/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import android.os.SystemClock
import com.metrolist.music.utils.PlaylistPageCache
import com.metrolist.music.utils.PlaylistCacheKey
import com.metrolist.music.utils.CachedPlaylistPage
import com.metrolist.innertube.pages.PlaylistPage
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.filterVideoSongs
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.metrolist.music.constants.SongSortType
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.Album
import javax.inject.Inject

@HiltViewModel
class OnlinePlaylistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val database: MusicDatabase,
    private val pageCache: PlaylistPageCache,
) : ViewModel() {
    private val playlistId = savedStateHandle.get<String>("playlistId")!!

    // Check if this is a special podcast playlist (with or without VL prefix)
    private val normalizedPlaylistId = playlistId.removePrefix("VL")
    val isPodcastPlaylist = normalizedPlaylistId == "RDPN" || normalizedPlaylistId == "SE"

    private val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
    private val cacheKey = PlaylistCacheKey(normalizedPlaylistId, YouTube.cookie, YouTube.locale, hideVideoSongs, YouTube.authUser, YouTube.dataSyncId)
    private val cachedPage = if (isPodcastPlaylist) null else pageCache.get(cacheKey)
    private var fetchedAt = cachedPage?.fetchedAt ?: 0L
    val playlist = MutableStateFlow<PlaylistItem?>(cachedPage?.page?.playlist)
    val playlistSongs = MutableStateFlow(cachedPage?.page?.songs.orEmpty())

    private val _isLoading = MutableStateFlow(cachedPage == null)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    val dbPlaylist = database.playlistByBrowseId(playlistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    var continuation: String? = cachedPage?.page?.songsContinuation
        private set

    private var proactiveLoadJob: Job? = null
    private var initialLoadJob: Job? = null
    private var manualLoadJob: Job? = null
    private val pagination = Mutex()

    init {
        if (cachedPage?.isFresh() == true) {
            if (continuation != null) startProactiveBackgroundLoading()
        } else {
            fetchInitialPlaylistData()
        }
    }

    private fun fetchInitialPlaylistData() {
        initialLoadJob?.cancel()
        manualLoadJob?.cancel()
        initialLoadJob = viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = playlist.value == null
            _error.value = null
            if (playlist.value == null) continuation = null
            proactiveLoadJob?.cancel() // Cancel any ongoing proactive load

            if (isPodcastPlaylist) {
                // Use special podcast playlist APIs
                fetchPodcastPlaylist()
            } else {
                // Use regular playlist API
                pagination.withLock { fetchRegularPlaylist() }
            }
        }
    }

    private suspend fun fetchPodcastPlaylist() {
        when (normalizedPlaylistId) {
            "RDPN" -> {
                YouTube.newEpisodes()
                    .onSuccess { episodes ->
                        playlist.value = PlaylistItem(
                            id = playlistId,
                            title = "New Episodes",
                            author = null,
                            songCountText = "${episodes.size} episodes",
                            thumbnail = episodes.firstOrNull()?.thumbnail ?: "",
                            playEndpoint = null,
                            shuffleEndpoint = null,
                            radioEndpoint = null,
                        )
                        playlistSongs.value = applySongFilters(episodes)
                        _isLoading.value = false
                    }.onFailure { throwable ->
                        currentCoroutineContext().ensureActive()
                        _error.value = throwable.message ?: "Failed to load new episodes"
                        _isLoading.value = false
                        reportException(throwable)
                    }
            }
            "SE" -> {
                timber.log.Timber.d("[SE_LOCAL] Fetching SE playlist...")
                val result = YouTube.episodesForLater()
                val episodes = result.getOrNull() ?: emptyList()
                timber.log.Timber.d("[SE_LOCAL] YouTube API result: ${if (result.isSuccess) "success" else "failed"}, ${episodes.size} episodes")

                if (result.isSuccess && episodes.isNotEmpty()) {
                    // Use YouTube episodes
                    playlist.value = PlaylistItem(
                        id = playlistId,
                        title = "Episodes for Later",
                        author = null,
                        songCountText = "${episodes.size} episodes",
                        thumbnail = episodes.firstOrNull()?.thumbnail ?: "",
                        playEndpoint = null,
                        shuffleEndpoint = null,
                        radioEndpoint = null,
                    )
                    playlistSongs.value = applySongFilters(episodes)
                    _isLoading.value = false
                } else {
                    // Fall back to local saved episodes when API fails or returns empty
                    timber.log.Timber.d("[SE_LOCAL] Falling back to local saved episodes")
                    loadLocalSavedEpisodes()
                }
            }
            else -> {
                _error.value = "Unknown podcast playlist"
                _isLoading.value = false
            }
        }
    }

    private suspend fun fetchRegularPlaylist() {
        YouTube.playlist(playlistId)
            .onSuccess { playlistPage ->
                currentCoroutineContext().ensureActive()
                fetchedAt = SystemClock.elapsedRealtime()
                playlist.value = playlistPage.playlist
                playlistSongs.value = applySongFilters(playlistPage.songs)
                continuation = playlistPage.songsContinuation
                cacheCurrentPage()
                _isLoading.value = false
                if (continuation != null) {
                    startProactiveBackgroundLoading()
                }
            }.onFailure { throwable ->
                currentCoroutineContext().ensureActive()
                _error.value = throwable.message ?: "Failed to load playlist"
                _isLoading.value = false
                reportException(throwable)
            }
    }

    private suspend fun loadLocalSavedEpisodes() {
        timber.log.Timber.d("[SE_LOCAL] loadLocalSavedEpisodes called")
        val savedEpisodes = database.savedPodcastEpisodes(SongSortType.CREATE_DATE, true).firstOrNull() ?: emptyList()
        timber.log.Timber.d("[SE_LOCAL] Found ${savedEpisodes.size} saved episodes")
        savedEpisodes.forEachIndexed { index, ep ->
            timber.log.Timber.d("[SE_LOCAL] Episode $index: id=${ep.song.id}, title=${ep.song.title}, isEpisode=${ep.song.isEpisode}, inLibrary=${ep.song.inLibrary}")
        }
        if (savedEpisodes.isNotEmpty()) {
            // Convert local Song entities to SongItem format
            val songItems = savedEpisodes.map { song ->
                SongItem(
                    id = song.song.id,
                    title = song.song.title,
                    artists = song.artists.map { Artist(it.id, it.name) },
                    album = song.album?.let { com.metrolist.innertube.models.Album(it.id, it.title) },
                    duration = song.song.duration,
                    thumbnail = song.song.thumbnailUrl ?: "",
                    explicit = song.song.explicit,
                    endpoint = null,
                )
            }
            timber.log.Timber.d("[SE_LOCAL] Converted to ${songItems.size} SongItems")
            playlist.value = PlaylistItem(
                id = playlistId,
                title = "Episodes for Later",
                author = null,
                songCountText = "${songItems.size} episodes",
                thumbnail = songItems.firstOrNull()?.thumbnail ?: "",
                playEndpoint = null,
                shuffleEndpoint = null,
                radioEndpoint = null,
            )
            val filtered = applySongFilters(songItems)
            timber.log.Timber.d("[SE_LOCAL] After filter: ${filtered.size} episodes, setting playlistSongs")
            playlistSongs.value = filtered
            _isLoading.value = false
            timber.log.Timber.d("[SE_LOCAL] Done, isLoading=false")
        } else {
            timber.log.Timber.d("[SE_LOCAL] No saved episodes found")
            _error.value = "No saved episodes"
            _isLoading.value = false
        }
    }

    private fun cacheCurrentPage() {
        val header = playlist.value ?: return
        pageCache.put(cacheKey, CachedPlaylistPage(PlaylistPage(header, playlistSongs.value, continuation, null), fetchedAt))
    }

    private suspend fun appendNextPage(): Boolean = pagination.withLock {
        val token = continuation ?: return@withLock false
        val result = YouTube.playlistContinuation(token)
        currentCoroutineContext().ensureActive()
        result.onSuccess { page ->
            playlistSongs.value = applySongFilters(playlistSongs.value + page.songs)
            continuation = page.continuation
            cacheCurrentPage()
        }.onFailure { reportException(it) }
        result.isSuccess && continuation != null
    }

    private fun startProactiveBackgroundLoading() {
        proactiveLoadJob?.cancel()
        proactiveLoadJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive && appendNextPage()) { }
        }
    }

    fun loadMoreSongs() {
        if (_isLoadingMore.value || continuation == null) return
        proactiveLoadJob?.cancel()
        _isLoadingMore.value = true
        manualLoadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                appendNextPage()
            } finally {
                _isLoadingMore.value = false
                if (isActive && continuation != null) startProactiveBackgroundLoading()
            }
        }
    }

    fun retry() {
        proactiveLoadJob?.cancel()
        fetchInitialPlaylistData() // This will also restart proactive loading if applicable
    }

    private fun applySongFilters(songs: List<SongItem>): List<SongItem> {
        return songs
            .distinctBy { it.id }
            .filterVideoSongs(hideVideoSongs)
    }

    override fun onCleared() {
        super.onCleared()
        proactiveLoadJob?.cancel()
    }
}
