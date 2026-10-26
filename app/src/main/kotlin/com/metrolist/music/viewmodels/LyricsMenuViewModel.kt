/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.lyrics.LyricsHelper
import com.metrolist.music.lyrics.LyricsResult
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.NetworkConnectivityObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.metrolist.music.lyrics.usableLyrics
import com.metrolist.music.lyrics.LyricsLoadStatus
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class LyricsMenuViewModel
@Inject
constructor(
    private val lyricsHelper: LyricsHelper,
    val database: MusicDatabase,
    private val networkConnectivity: NetworkConnectivityObserver,
) : ViewModel() {
    private var job: Job? = null
    @Volatile private var searchGeneration = 0L
    val results = MutableStateFlow(emptyList<LyricsResult>())
    val isLoading = MutableStateFlow(false)

    private val _isNetworkAvailable = MutableStateFlow(false)
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    init {
        viewModelScope.launch {
            networkConnectivity.networkStatus.collect { isConnected ->
                _isNetworkAvailable.value = isConnected
            }
        }

        _isNetworkAvailable.value = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true // Assume connected as fallback
        }
    }

    fun search(
        mediaId: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ) {
        val generation = ++searchGeneration
        job?.cancel()
        isLoading.value = true
        results.value = emptyList()
        job = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    lyricsHelper.getAllLyrics(mediaId, title, artist, duration, album) { result ->
                        launch(Dispatchers.Main.immediate) {
                            if (generation == searchGeneration) results.update { it + result }
                        }
                    }
                }
            } finally {
                if (generation == searchGeneration) isLoading.value = false
            }
        }
    }

    fun cancelSearch() {
        ++searchGeneration
        job?.cancel()
        job = null
        isLoading.value = false
    }

    fun refetchLyrics(
        mediaMetadata: MediaMetadata,
        lyricsEntity: LyricsEntity?,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = lyricsHelper.getLyrics(mediaMetadata, forceRefresh = true)
            if (!usableLyrics(result.lyrics) && result.status != LyricsLoadStatus.NOT_FOUND) return@launch
            database.withTransaction {
                // A fresh response must not overwrite edits made while the request was running.
                if (database.lyrics(mediaMetadata.id).first() == lyricsEntity) {
                    database.upsert(LyricsEntity(mediaMetadata.id, result.lyrics, result.provider))
                }
            }
        }
    }
}
