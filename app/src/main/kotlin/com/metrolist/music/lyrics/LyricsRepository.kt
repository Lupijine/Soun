package com.metrolist.music.lyrics

import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.models.MediaMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

enum class LyricsLoadStatus { LOADING, FOUND, NOT_FOUND, OFFLINE, ERROR }

data class LyricsLoadState(val mediaId: String, val status: LyricsLoadStatus)

internal fun usableLyrics(text: String?): Boolean =
    !text.isNullOrBlank() && text.trim() != LyricsEntity.LYRICS_NOT_FOUND

@Singleton
class LyricsRepository @Inject constructor(private val database: MusicDatabase, private val helper: LyricsHelper) {

    suspend fun load(metadata: MediaMetadata): LyricsLoadStatus {
        val previous = database.lyrics(metadata.id).first()
        val automaticSource = previous != null && LyricsProviderRegistry.providerNames.any { name ->
            name.equals(previous.provider, ignoreCase = true) ||
                LyricsProviderRegistry.getProviderByName(name)?.name.equals(previous.provider, ignoreCase = true)
        }
        val recheck = usableLyrics(previous?.lyrics) && previous != null && automaticSource &&
            previous.translatedLyrics.isBlank() && !helper.isStoredVerified(metadata, previous)
        if (usableLyrics(previous?.lyrics) && !recheck) return LyricsLoadStatus.FOUND
        val result = try {
            helper.getLyrics(metadata, forceRefresh = recheck)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return LyricsLoadStatus.ERROR
        }
        if (!usableLyrics(result.lyrics)) {
            // Keep downloaded lyrics available offline; an online mismatch must not be displayed.
            return if (recheck && result.status == LyricsLoadStatus.OFFLINE) LyricsLoadStatus.FOUND else result.status
        }
        if (recheck) {
            database.withTransaction {
                if (database.lyrics(metadata.id).first() == previous) {
                    database.upsert(LyricsEntity(metadata.id, result.lyrics, result.provider))
                }
            }
        } else {
            retain(metadata.id, result)
        }
        val retained = database.lyrics(metadata.id).first()
        if (retained?.lyrics == result.lyrics && retained.provider == result.provider) helper.markStoredVerified(metadata, retained)
        return LyricsLoadStatus.FOUND
    }

    internal suspend fun retain(mediaId: String, result: LyricsWithProvider) {
        if (!usableLyrics(result.lyrics)) return
        database.withTransaction {
            val current = database.lyrics(mediaId).first()
            // Preserve manual edits and translations made while a provider request was running.
            if (!usableLyrics(current?.lyrics)) {
                database.upsert(LyricsEntity(mediaId, result.lyrics, result.provider))
            }
        }
    }
}
