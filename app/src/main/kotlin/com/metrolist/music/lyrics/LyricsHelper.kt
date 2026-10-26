/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

import android.content.Context
import android.util.LruCache
import android.util.Base64
import com.metrolist.music.db.entities.LyricsEntity
import java.security.MessageDigest
import com.metrolist.music.constants.LyricsProviderOrderKey
import com.metrolist.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.NetworkConnectivityObserver
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

private const val MAX_LYRICS_FETCH_MS = 25000L
private const val PER_PROVIDER_TIMEOUT_MS = 8000L
private const val PROVIDER_NONE = ""

class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val networkConnectivity: NetworkConnectivityObserver,
) {
    val preferred =
        context.dataStore.data
            .map { preferences ->
                resolveLyricsProviders(preferences)
            }.distinctUntilChanged()

    private val cache = LruCache<LyricsRequest, List<LyricsResult>>(MAX_CACHE_SIZE)

    private val verification by lazy { context.getSharedPreferences("lyrics_identity_v2", Context.MODE_PRIVATE) }

    private fun fingerprint(metadata: MediaMetadata, entry: LyricsEntity): String {
        val identity = listOf(metadata.id, metadata.title, metadata.artists.joinToString { it.name },
            metadata.duration.toString(), metadata.album?.title.orEmpty(), entry.provider, entry.lyrics).joinToString("\u0000")
        return Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()), Base64.NO_WRAP)
    }

    internal fun isStoredVerified(metadata: MediaMetadata, entry: LyricsEntity): Boolean =
        verification.getString(metadata.id, null) == fingerprint(metadata, entry)

    internal fun markStoredVerified(metadata: MediaMetadata, entry: LyricsEntity) {
        verification.edit().putString(metadata.id, fingerprint(metadata, entry)).apply()
    }

    fun invalidate(mediaId: String) {
        cache.snapshot().keys.filter { it.mediaId == mediaId }.forEach(cache::remove)
    }

    suspend fun getLyrics(mediaMetadata: MediaMetadata, forceRefresh: Boolean = false): LyricsWithProvider {
        if (forceRefresh) invalidate(mediaMetadata.id)
        val request = LyricsRequest(mediaMetadata.id, mediaMetadata.title, mediaMetadata.artists.joinToString { it.name },
            mediaMetadata.duration, mediaMetadata.album?.title)
        val cached = cache.get(request)?.firstOrNull()
        if (cached != null) {
            return LyricsWithProvider(cached.lyrics, cached.providerName)
        }

        val orderedProviders = context.dataStore.data
            .map { preferences -> resolveLyricsProviders(preferences) }
            .first()

        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true
        }

        if (!isNetworkAvailable) {
            return LyricsWithProvider(LYRICS_NOT_FOUND, PROVIDER_NONE, LyricsLoadStatus.OFFLINE)
        }

        val result = withTimeoutOrNull(MAX_LYRICS_FETCH_MS) {
            val cleanedTitle = LyricsUtils.cleanTitleForSearch(mediaMetadata.title)
            val enabledProviders = orderedProviders.filter { it.isEnabled(context) }

            Timber.tag("LyricsHelper").d("Starting sequential fetch for: $cleanedTitle by ${mediaMetadata.artists.joinToString { it.name }}")
            Timber.tag("LyricsHelper").d("Enabled providers in order: ${enabledProviders.joinToString { it.name }}")

            var hadProviderError = false
            for (provider in enabledProviders) {
                Timber.tag("LyricsHelper").d("Trying provider: ${provider.name}")
                val providerResult = try {
                    withTimeoutOrNull(PER_PROVIDER_TIMEOUT_MS) {
                        provider.getLyrics(
                            context,
                            mediaMetadata.id,
                            cleanedTitle,
                            mediaMetadata.artists.joinToString { it.name },
                            mediaMetadata.duration,
                            mediaMetadata.album?.title,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag("LyricsHelper").w("${provider.name} threw: ${e.message}")
                    null
                }

                if (providerResult != null && providerResult.isSuccess) {
                    Timber.tag("LyricsHelper").i("Got lyrics from ${provider.name}")
                    val filtered = LyricsUtils.filterLyricsCreditLines(providerResult.getOrNull()!!)
                    if (!usableLyrics(filtered)) continue
                    cache.put(request, listOf(LyricsResult(provider.name, filtered)))
                    return@withTimeoutOrNull LyricsWithProvider(filtered, provider.name)
                } else {
                    if (providerResult == null || providerResult.exceptionOrNull() is java.io.IOException) hadProviderError = true
                    val errorMsg = providerResult?.exceptionOrNull()?.message ?: "timeout or exception"
                    Timber.tag("LyricsHelper").w("${provider.name} failed: $errorMsg")
                }
            }

            Timber.tag("LyricsHelper").w("No lyrics found after checking all providers")
            LyricsWithProvider(LYRICS_NOT_FOUND, PROVIDER_NONE, if (hadProviderError) LyricsLoadStatus.ERROR else LyricsLoadStatus.NOT_FOUND)
        }

        return result ?: LyricsWithProvider(LYRICS_NOT_FOUND, PROVIDER_NONE, LyricsLoadStatus.ERROR)
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        album: String? = null,
        callback: (LyricsResult) -> Unit,
    ) {
        val cacheKey = LyricsRequest(mediaId, songTitle, songArtists, duration, album, search = true)
        cache.get(cacheKey)?.let { results ->
            results.forEach { currentCoroutineContext().ensureActive(); callback(it) }
            return
        }

        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true
        }

        if (!isNetworkAvailable) return

        val allResult = mutableListOf<LyricsResult>()
        coroutineScope {
            val cleanedTitle = LyricsUtils.cleanTitleForSearch(songTitle)
            val allProviders = context.dataStore.data
                .map { preferences -> resolveLyricsProviders(preferences) }
                .first()
            val enabledProviders = allProviders.filter { it.isEnabled(context) }

            val otherProviders = enabledProviders.filter { it.name != "LyricsPlus" }
            val lyricsPlusProvider = enabledProviders.find { it.name == "LyricsPlus" }

            val callbackMutex = Any()

            val otherJobs = otherProviders.map { provider ->
                launch {
                    val requestContext = currentCoroutineContext()
                    try {
                        provider.getAllLyrics(context, mediaId, cleanedTitle, songArtists, duration, album) { lyrics ->
                            val filteredLyrics = LyricsUtils.filterLyricsCreditLines(lyrics)
                            val result = LyricsResult(provider.name, filteredLyrics)
                            requestContext.ensureActive()
                            if (!usableLyrics(filteredLyrics)) return@getAllLyrics
                            synchronized(callbackMutex) {
                                allResult += result
                                callback(result)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }
            }
            otherJobs.forEach { it.join() }

            val otherLyricsCount = allResult.count { it.providerName != "LyricsPlus" }
            if (lyricsPlusProvider != null && otherLyricsCount <= 2) {
                launch {
                    val requestContext = currentCoroutineContext()
                    try {
                        lyricsPlusProvider.getAllLyrics(context, mediaId, cleanedTitle, songArtists, duration, album) { lyrics ->
                            val filteredLyrics = LyricsUtils.filterLyricsCreditLines(lyrics)
                            val result = LyricsResult(lyricsPlusProvider.name, filteredLyrics)
                            requestContext.ensureActive()
                            if (!usableLyrics(filteredLyrics)) return@getAllLyrics
                            synchronized(callbackMutex) {
                                allResult += result
                                callback(result)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }.join()
            }

            if (allResult.isNotEmpty()) cache.put(cacheKey, allResult)
        }

    }

    private fun resolveLyricsProviders(preferences: androidx.datastore.preferences.core.Preferences): List<LyricsProvider> {
        val providerOrder = preferences[LyricsProviderOrderKey].orEmpty()
        if (providerOrder.isNotBlank()) {
            return LyricsProviderRegistry.getOrderedProviders(providerOrder)
        }

        return LyricsProviderRegistry.getDefaultProviderOrder()
            .mapNotNull { LyricsProviderRegistry.getProviderByName(it) }
    }

    companion object {
        private const val MAX_CACHE_SIZE = 3
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)

data class LyricsWithProvider(
    val lyrics: String,
    val provider: String,
    val status: LyricsLoadStatus = if (usableLyrics(lyrics)) LyricsLoadStatus.FOUND else LyricsLoadStatus.NOT_FOUND,
)

internal data class LyricsRequest(
    val mediaId: String,
    val title: String,
    val artist: String,
    val duration: Int,
    val album: String?,
    val search: Boolean = false,
)
