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
        if (usableLyrics(previous?.lyrics)) return LyricsLoadStatus.FOUND
        val result = try {
            helper.getLyrics(metadata)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return LyricsLoadStatus.ERROR
        }
        if (!usableLyrics(result.lyrics)) return result.status
        retain(metadata.id, result)
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
