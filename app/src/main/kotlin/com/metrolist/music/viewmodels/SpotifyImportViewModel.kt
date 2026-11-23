/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.spotify.SpotifyImportError
import com.metrolist.music.spotify.SpotifyImportException
import com.metrolist.music.spotify.SpotifyPlaylist
import com.metrolist.music.spotify.SpotifyPlaylistImporter
import com.metrolist.music.spotify.SpotifyPlaylistParser
import com.metrolist.music.spotify.SpotifyTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/** What Spotify left out: [shown] tracks came back, out of [total] when Spotify said how many. */
data class SpotifyTruncation(val shown: Int, val total: Int?)

sealed interface SpotifyImportState {
    data object Idle : SpotifyImportState
    data object Loading : SpotifyImportState
    data class Matching(
        val name: String?,
        val coverUrl: String?,
        val done: Int,
        val total: Int,
        val truncation: SpotifyTruncation?,
    ) : SpotifyImportState
    data class Saving(val name: String?, val coverUrl: String?) : SpotifyImportState
    data class Done(
        val playlistId: String,
        val name: String,
        val coverUrl: String?,
        val added: Int,
        val total: Int,
        val missing: List<SpotifyTrack>,
        val truncation: SpotifyTruncation?,
    ) : SpotifyImportState
    data class Failed(val error: SpotifyImportError) : SpotifyImportState
}

@HiltViewModel
class SpotifyImportViewModel @Inject constructor(
    private val database: MusicDatabase,
) : ViewModel() {
    private val importer = SpotifyPlaylistImporter()
    private val _state = MutableStateFlow<SpotifyImportState>(SpotifyImportState.Idle)
    val state: StateFlow<SpotifyImportState> = _state.asStateFlow()
    private var job: Job? = null

    val isRunning: Boolean
        get() = job?.isActive == true

    fun start(link: String, fallbackName: String) {
        if (isRunning) return
        val playlistId = SpotifyPlaylistParser.extractPlaylistId(link)
        if (playlistId == null) {
            _state.value = SpotifyImportState.Failed(SpotifyImportError.INVALID_LINK)
            return
        }
        _state.value = SpotifyImportState.Loading
        job = viewModelScope.launch {
            try {
                val playlist = importer.fetchPlaylist(playlistId)
                _state.value = import(playlist, fallbackName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SpotifyImportException) {
                _state.value = SpotifyImportState.Failed(e.error)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Spotify import failed")
                _state.value = SpotifyImportState.Failed(SpotifyImportError.UNREADABLE)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = SpotifyImportState.Idle
    }

    fun reset() {
        if (!isRunning) _state.value = SpotifyImportState.Idle
    }

    private suspend fun import(playlist: SpotifyPlaylist, fallbackName: String): SpotifyImportState {
        val tracks = playlist.tracks
        val truncation = truncation(playlist)
        _state.value = SpotifyImportState.Matching(playlist.name, playlist.coverUrl, 0, tracks.size, truncation)

        val matches = arrayOfNulls<SongItem>(tracks.size)
        val completed = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val permits = Semaphore(SEARCH_CONCURRENCY)
        coroutineScope {
            tracks.forEachIndexed { index, track ->
                launch(Dispatchers.IO) {
                    val match = permits.withPermit { importer.match(track) }
                    when (match) {
                        is SpotifyPlaylistImporter.Match.Found -> matches[index] = match.song
                        SpotifyPlaylistImporter.Match.Failed -> failed.incrementAndGet()
                        SpotifyPlaylistImporter.Match.NotFound -> Unit
                    }
                    val done = completed.incrementAndGet()
                    _state.update { (it as? SpotifyImportState.Matching)?.copy(done = maxOf(it.done, done)) ?: it }
                }
            }
        }

        val found = matches.filterNotNull()
        if (found.isEmpty()) {
            throw SpotifyImportException(
                if (failed.get() == tracks.size) SpotifyImportError.SEARCH_FAILED else SpotifyImportError.NO_MATCHES,
            )
        }

        _state.value = SpotifyImportState.Saving(playlist.name, playlist.coverUrl)
        val name = playlist.name?.takeIf { it.isNotBlank() } ?: fallbackName
        val playlistId = savePlaylist(name, found)
        return SpotifyImportState.Done(
            playlistId = playlistId,
            name = name,
            coverUrl = playlist.coverUrl,
            added = found.size,
            total = tracks.size,
            missing = tracks.filterIndexed { index, _ -> matches[index] == null },
            truncation = truncation,
        )
    }

    private suspend fun savePlaylist(name: String, songs: List<SongItem>): String = withContext(Dispatchers.IO) {
        val entity = PlaylistEntity(name = name, bookmarkedAt = LocalDateTime.now(), isEditable = true)
        database.withTransaction {
            songs.forEach { insert(it.toMediaMetadata()) }
            insert(entity)
        }
        val playlist = database.playlist(entity.id).first()
            ?: throw SpotifyImportException(SpotifyImportError.UNREADABLE)
        database.addSongsToPlaylist(playlist, songs.map { it.id to null })
        entity.id
    }

    private fun truncation(playlist: SpotifyPlaylist): SpotifyTruncation? {
        val shown = playlist.tracks.size
        val total = playlist.reportedTrackCount
        return when {
            total != null && total > shown -> SpotifyTruncation(shown, total)
            total == null && shown >= SpotifyPlaylistParser.EMBED_TRACK_LIMIT -> SpotifyTruncation(shown, null)
            else -> null
        }
    }

    private companion object {
        const val TAG = "SpotifyImport"
        const val SEARCH_CONCURRENCY = 3
    }
}
