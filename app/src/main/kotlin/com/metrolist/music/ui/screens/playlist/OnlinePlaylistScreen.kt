/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEachReversed
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.PureBlackKey
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.YouTubePlaylistQueue
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.ExpandableText
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.SounCollectionHeader
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.ui.menu.YouTubePlaylistMenu
import com.metrolist.music.ui.menu.YouTubeSelectionSongMenu
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.ui.theme.SounAccent
import com.metrolist.music.ui.theme.SounCollectionBackground
import com.metrolist.music.ui.theme.SounCollectionTheme
import com.metrolist.music.ui.theme.rememberArtworkColors
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.OnlinePlaylistViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnlinePlaylistScreen(
    navController: NavController,
    viewModel: OnlinePlaylistViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val listenTogetherManager = LocalListenTogetherManager.current
    val isListenTogetherGuest = listenTogetherManager?.let { it.isInRoom && !it.isHost } ?: false
    val coroutineScope = rememberCoroutineScope()

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val songs by viewModel.playlistSongs.collectAsStateWithLifecycle()
    val dbPlaylist by viewModel.dbPlaylist.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isPodcastPlaylist = viewModel.isPodcastPlaylist

    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)

    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }

    val filteredSongs =
        remember(songs, query) {
            if (query.text.isEmpty()) {
                songs.mapIndexed { i, s -> i to s }
            } else {
                songs.mapIndexed { i, s -> i to s }.filter {
                    it.second.title.contains(query.text, true) ||
                        it.second.artists.fastAny { a -> a.name.contains(query.text, true) }
                }
            }
        }

    var inSelectMode by remember { mutableStateOf(false) }
    val selection =
        remember {
            mutableStateListOf<String>()
        }
    var selectionAnchorSongId by remember { mutableStateOf<String?>(null) }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
        selectionAnchorSongId = null
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) { if (isSearching) focusRequester.requestFocus() }

    LaunchedEffect(filteredSongs) {
        selection.fastForEachReversed { songId ->
            if (filteredSongs.find { it.second.id == songId } == null) {
                selection.remove(songId)
            }
        }

        if (selectionAnchorSongId != null && filteredSongs.none { it.second.id == selectionAnchorSongId }) {
            selectionAnchorSongId = filteredSongs.firstOrNull { it.second.id in selection }?.second?.id
        }
    }

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    val pureBlack by rememberPreference(PureBlackKey, false)
    val savedColorSource by remember(database, playlist?.id, dbPlaylist?.playlist?.id) {
        when {
            playlist?.id?.removePrefix("VL") == "LM" -> database.likedPlaylistColorSource()
            else -> dbPlaylist?.playlist?.id?.let(database::playlistColorSource) ?: flowOf(null)
        }
    }.collectAsStateWithLifecycle(initialValue = null)
    val colorSource = savedColorSource ?: songs.firstOrNull()?.thumbnail ?: playlist?.thumbnail
    val palette = rememberArtworkColors(colorSource)
    val collectionBackdrop = rememberLayerBackdrop()
    SounCollectionTheme(palette.firstOrNull() ?: SounAccent, collectionBackdrop) {
        Box(Modifier.fillMaxSize()) {
            SounCollectionBackground(palette, pureBlack, lightField = collectionBackdrop)
            LazyColumn(
                modifier = Modifier.graphicsLayer(),
                state = lazyListState,
                contentPadding = LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues(),
            ) {
                if (playlist == null || songs.isEmpty()) {
                    if (isLoading) {
                        item(key = "loading_placeholder") {
                            Box(
                                modifier =
                                    Modifier
                                        .fillParentMaxSize()
                                        .padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                ContainedLoadingIndicator()
                            }
                        }
                    } else if (error != null) {
                        item(key = "error_placeholder") {
                            Column(
                                modifier =
                                    Modifier
                                        .fillParentMaxSize()
                                        .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = error ?: stringResource(R.string.error_unknown),
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                androidx.compose.material3.TextButton(onClick = { viewModel.retry() }) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                        }
                    } else if (!isLoading && songs.isEmpty()) {
                        item(key = "empty_placeholder") {
                            Box(
                                modifier =
                                    Modifier
                                        .fillParentMaxSize()
                                        .padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.playlist_is_empty),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                } else {
                    playlist?.let { playlist ->
                        if (!isSearching) {
                            item(key = "playlist_header") {
                                OnlinePlaylistHeader(
                                    playlist = playlist,
                                    songs = songs,
                                    dbPlaylist = dbPlaylist,
                                    colorSource = colorSource,
                                    coroutineScope = coroutineScope,
                                    continuation = viewModel.continuation,
                                    isPodcastPlaylist = isPodcastPlaylist,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }

                        itemsIndexed(filteredSongs) { index, (_, songItem) ->
                            val onCheckedChange: (Boolean) -> Unit = {
                                if (it) {
                                    selection.add(songItem.id)
                                } else {
                                    selection.remove(songItem.id)
                                }
                            }

                            YouTubeListItem(
                                item = songItem,
                                isActive = mediaMetadata?.id == songItem.id,
                                isPlaying = isPlaying,
                                isSelected = inSelectMode && songItem.id in selection,
                                modifier =
                                    Modifier
                                        .combinedClickable(
                                            enabled = !hideExplicit || !songItem.explicit,
                                            onClick = {
                                                if (inSelectMode) {
                                                    onCheckedChange(songItem.id !in selection)
                                                } else if (songItem.id == mediaMetadata?.id) {
                                                    playerConnection.togglePlayPause()
                                                } else {
                                                    playerConnection.playQueue(
                                                        YouTubePlaylistQueue(
                                                            playlistId = playlist.id,
                                                            playlistTitle = playlist.title,
                                                            initialSongs = filteredSongs.map { it.second },
                                                            initialContinuation = viewModel.continuation,
                                                            startIndex = index,
                                                        ),
                                                    )
                                                }
                                            },
                                            onLongClick = {
                                                if (!inSelectMode) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    inSelectMode = true
                                                    onCheckedChange(true)
                                                    selectionAnchorSongId = songItem.id
                                                } else {
                                                    val anchorIndex =
                                                        selectionAnchorSongId?.let { anchorSongId ->
                                                            filteredSongs.indexOfFirst { it.second.id == anchorSongId }
                                                        } ?: -1

                                                    if (anchorIndex == -1) {
                                                        onCheckedChange(true)
                                                        selectionAnchorSongId = songItem.id
                                                    } else {
                                                        val range = if (anchorIndex <= index) anchorIndex..index else index..anchorIndex
                                                        for (rangeIndex in range) {
                                                            val rangeSongId = filteredSongs[rangeIndex].second.id
                                                            if (rangeSongId !in selection) {
                                                                selection.add(rangeSongId)
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                        ).animateItem(),
                                trailingContent = {
                                    if (inSelectMode) {
                                        Checkbox(
                                            checked = songItem.id in selection,
                                            onCheckedChange = onCheckedChange,
                                        )
                                    } else {
                                        IconButton(onClick = {
                                            menuState.show {
                                                YouTubeSongMenu(songItem, menuState::dismiss)
                                            }
                        }) {
                                            Icon(painterResource(R.drawable.more_vert), null)
                                        }
                                    }
                                },
                            )
                        }

                        if (isLoadingMore) {
                            item(key = "loading_more") {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    ContainedLoadingIndicator()
                                }
                            }
                        }
                    }
                }
            }

            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {
                    if (inSelectMode) {
                        Text(
                            text =
                                if (isPodcastPlaylist) {
                                    pluralStringResource(R.plurals.n_episode, selection.size, selection.size)
                                } else {
                                    pluralStringResource(R.plurals.n_song, selection.size, selection.size)
                                },
                            style = MaterialTheme.typography.titleLarge,
                        )
                    } else if (isSearching) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.search),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleLarge,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            colors =
                                TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                ),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                        )
                    } else if (lazyListState.firstVisibleItemIndex > 0) {
                        Text(playlist?.title ?: "")
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSearching) {
                                isSearching = false
                                query = TextFieldValue()
                            } else if (inSelectMode) {
                                onExitSelectionMode()
                            } else {
                                navController.navigateUp()
                            }
                        },
                        onLongClick = {
                            if (!isSearching && !inSelectMode) {
                                navController.backToMain()
                            }
                        },
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    if (inSelectMode) R.drawable.close else R.drawable.arrow_back,
                                ),
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    if (inSelectMode) {
                        Checkbox(
                            checked = selection.size == filteredSongs.size && selection.isNotEmpty(),
                            onCheckedChange = {
                                if (selection.size == filteredSongs.size) {
                                    selection.clear()
                                } else {
                                    selection.clear()
                                    selection.addAll(filteredSongs.map { it.second.id })
                                }
                            },
                        )
                        IconButton(
                            enabled = selection.isNotEmpty(),
                            onClick = {
                                menuState.show {
                                    YouTubeSelectionSongMenu(
                                        songSelection =
                                            filteredSongs
                                                .filter { it.second.id in selection }
                                                .map { it.second },
                                        onDismiss = menuState::dismiss,
                                        clearAction = onExitSelectionMode,
                                    )
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = null,
                            )
                        }
                    } else if (!isSearching) {
                        IconButton(
                            onClick = { isSearching = true },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.search),
                                contentDescription = null,
                            )
                        }
                    }
                },
            )

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun OnlinePlaylistHeader(
    playlist: PlaylistItem,
    songs: List<SongItem>,
    dbPlaylist: Playlist?,
    colorSource: String?,
    coroutineScope: CoroutineScope,
    continuation: String?,
    isPodcastPlaylist: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val navController = LocalNavController.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val listenTogetherManager = LocalListenTogetherManager.current
    val isListenTogetherGuest = listenTogetherManager?.let { it.isInRoom && !it.isHost } ?: false
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current

    val totalDuration = remember(songs) { songs.sumOf { it.duration ?: 0 } }
    val subtitle = pluralStringResource(if (isPodcastPlaylist) R.plurals.n_episode else R.plurals.n_song, songs.size, songs.size) +
        if (totalDuration > 0) " · " + makeTimeString(totalDuration * 1000L) else ""
    val liked = playlist.id.removePrefix("VL") == "LM"
    SounCollectionHeader(
        name = playlist.title,
        subtitle = subtitle,
        artwork = if (liked) R.drawable.soun_liked_chrome else null,
        colorSource = colorSource,
        thumbnail = playlist.thumbnail,
        enabled = songs.isNotEmpty() && !isListenTogetherGuest,
        moreEnabled = true,
        modifier = modifier,
        onPlay = {
            playerConnection.playQueue(YouTubePlaylistQueue(
                playlistId = playlist.id, playlistTitle = playlist.title,
                initialSongs = songs, initialContinuation = continuation,
            ))
        },
        onShuffle = {
            val endpoint = playlist.shuffleEndpoint
            if (endpoint != null) playerConnection.playQueue(YouTubeQueue(endpoint))
            else playerConnection.playQueue(YouTubePlaylistQueue(
                playlistId = playlist.id, playlistTitle = playlist.title,
                initialSongs = songs.shuffled(), initialContinuation = continuation,
            ))
        },
        onMore = {
            menuState.show {
                YouTubePlaylistMenu(playlist, songs, coroutineScope, menuState::dismiss)
            }
        },
        supportingContent = {
            Spacer(Modifier.height(8.dp))
            val author = playlist.author
            if (author != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier =
                        Modifier.combinedClickable(
                            onClick = {
                                if (author.id != null) {
                                    navController.navigate("artist/${author.id}")
                                }
                            },
                        ),
                ) {
                    if (playlist.authorAvatarUrl != null) {
                        AsyncImage(
                            model = playlist.authorAvatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .size(24.dp)
                                    .clip(CircleShape),
                        )
                    }
                    Text(
                        text = author.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            val description = playlist.description
            if (!description.isNullOrBlank()) {
                ExpandableText(
                    text = description,
                    modifier = Modifier.padding(horizontal = 32.dp),
                    collapsedMaxLines = 3,
                )
            }

            if (!liked) {
                val bookmarked = dbPlaylist?.playlist?.bookmarkedAt != null
                androidx.compose.material3.TextButton(onClick = {
                    if (dbPlaylist != null) {
                        database.transaction {
                            val currentPlaylist = dbPlaylist.playlist
                            update(currentPlaylist, playlist)
                            update(currentPlaylist.toggleLike())
                        }
                    } else {
                        coroutineScope.launch(Dispatchers.IO) {
                            val playlistEntity =
                                PlaylistEntity(
                                    name = playlist.title,
                                    browseId = playlist.id,
                                    thumbnailUrl = playlist.thumbnail,
                                    isEditable = playlist.isEditable,
                                    remoteSongCount =
                                        playlist.songCountText?.let {
                                            Regex("""\d+""").find(it)?.value?.toIntOrNull()
                                        },
                                    playEndpointParams = playlist.playEndpoint?.params,
                                    shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                                    radioEndpointParams = playlist.radioEndpoint?.params,
                                ).toggleLike()
                            val songMetadata = songs.map { it.toMediaMetadata() }
                            database.withTransaction {
                                insert(playlistEntity)
                                songMetadata.onEach { insert(it) }
                                val songIds = songMetadata.map { it.id to it.setVideoId }
                                val createdPlaylist =
                                    database.playlistBlocking(playlistEntity.id)
                                        ?: throw IllegalStateException("Failed to create playlist")
                                database.addSongsToPlaylist(createdPlaylist, songIds)
                            }
                        }
                    }
                }) {
                    Icon(painterResource(if (bookmarked) R.drawable.favorite else R.drawable.favorite_border), null, Modifier.size(18.dp))
                    Text(stringResource(if (bookmarked) R.string.remove_from_library else R.string.add_to_library), Modifier.padding(start = 8.dp))
                }
            }
        },
    )
}
