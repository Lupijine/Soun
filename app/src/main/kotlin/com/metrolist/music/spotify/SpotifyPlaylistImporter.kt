/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.spotify

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

enum class SpotifyImportError { INVALID_LINK, NETWORK, NOT_FOUND, EMPTY_OR_PRIVATE, UNREADABLE, SEARCH_FAILED, NO_MATCHES }

class SpotifyImportException(val error: SpotifyImportError, cause: Throwable? = null) : Exception(error.name, cause)

/** Fetches a public Spotify playlist and looks its tracks up on YouTube Music. */
class SpotifyPlaylistImporter(
    private val client: OkHttpClient = defaultClient,
) {
    sealed interface Match {
        data class Found(val song: SongItem) : Match
        data object NotFound : Match
        data object Failed : Match
    }

    suspend fun fetchPlaylist(playlistId: String): SpotifyPlaylist = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(SpotifyPlaylistParser.embedUrl(playlistId))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en")
            .build()
        val html = try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 400 || response.code == 404 -> throw SpotifyImportException(SpotifyImportError.NOT_FOUND)
                    response.code == 401 || response.code == 403 -> throw SpotifyImportException(SpotifyImportError.EMPTY_OR_PRIVATE)
                    !response.isSuccessful -> throw SpotifyImportException(SpotifyImportError.NETWORK)
                }
                response.body.string()
            }
        } catch (e: IOException) {
            throw SpotifyImportException(SpotifyImportError.NETWORK, e)
        }
        currentCoroutineContext().ensureActive()
        val playlist = SpotifyPlaylistParser.parseEmbedHtml(html)
            ?: throw SpotifyImportException(
                if (html.contains("__NEXT_DATA__")) SpotifyImportError.EMPTY_OR_PRIVATE else SpotifyImportError.UNREADABLE,
            )
        if (playlist.tracks.isEmpty()) throw SpotifyImportException(SpotifyImportError.EMPTY_OR_PRIVATE)
        playlist
    }

    suspend fun match(track: SpotifyTrack): Match {
        var failed = false
        for (query in listOfNotNull(SpotifyTrackMatcher.query(track), SpotifyTrackMatcher.fallbackQuery(track))) {
            val songs = search(query)
            if (songs == null) {
                failed = true
                continue
            }
            SpotifyTrackMatcher.bestMatch(track, songs)?.let { return Match.Found(it) }
        }
        return if (failed) Match.Failed else Match.NotFound
    }

    private suspend fun search(query: String): List<SongItem>? {
        repeat(2) { attempt ->
            val result = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
            currentCoroutineContext().ensureActive()
            result.exceptionOrNull()?.let { error ->
                if (error is CancellationException) throw error
                Timber.tag(TAG).w(error, "Search failed (attempt ${attempt + 1}): %s", query)
            }
            result.getOrNull()?.let { page -> return page.items.filterIsInstance<SongItem>() }
        }
        return null
    }

    companion object {
        private const val TAG = "SpotifyImport"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val defaultClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        }
    }
}
