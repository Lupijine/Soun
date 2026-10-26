/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.playlist

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import kotlinx.coroutines.flow.first
import com.metrolist.music.utils.persistPlaylistCover
import com.metrolist.music.utils.deletePlaylistCover
import com.metrolist.music.ui.component.SounCollectionHeader
import androidx.compose.runtime.CompositionLocalProvider
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.metrolist.music.ui.theme.LocalSounSurfaceBackdrop
import com.metrolist.music.ui.theme.SounCollectionBackground
import com.metrolist.music.ui.theme.rememberArtworkColors
import com.metrolist.music.constants.PureBlackKey

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastForEachReversed
import androidx.compose.ui.util.fastSumBy
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.LocalSyncUtils
import com.metrolist.music.R
import com.metrolist.music.constants.DarkModeKey
import com.metrolist.music.constants.PlaylistEditLockKey
import com.metrolist.music.constants.PlaylistSongSortDescendingKey
import com.metrolist.music.constants.PlaylistSongSortType
import com.metrolist.music.constants.PlaylistSongSortTypeKey
import com.metrolist.music.constants.SwipeToRemoveSongKey
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.PlaylistSong
import com.metrolist.music.db.entities.PlaylistSongMap
import com.metrolist.music.extensions.move
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.ExoDownloadService
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.ui.component.ActionPromptDialog
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.DraggableScrollbar
import com.metrolist.music.ui.component.EmptyPlaceholder
import com.metrolist.music.ui.component.ExpandableText
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.component.SortHeader
import com.metrolist.music.ui.component.TextFieldDialog
import com.metrolist.music.ui.menu.LocalPlaylistMenu
import com.metrolist.music.ui.menu.SelectionSongMenu
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.ui.screens.settings.DarkMode
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.reportException
import com.metrolist.music.viewmodels.LocalPlaylistViewModel
import com.yalantis.ucrop.UCrop
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDateTime

@SuppressLint("RememberReturnType")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LocalPlaylistScreen(
    navController: NavController,
    viewModel: LocalPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val songs by viewModel.playlistSongs.collectAsStateWithLifecycle()
    val onlinePlaylist by viewModel.onlinePlaylist.collectAsStateWithLifecycle()
    val mutableSongs = remember { mutableStateListOf<PlaylistSong>() }
    val playlistLength =
        remember(songs) {
            songs.fastSumBy { it.song.song.duration }
        }
    val (sortType, onSortTypeChange) =
        rememberEnumPreference(
            PlaylistSongSortTypeKey,
            PlaylistSongSortType.CUSTOM,
        )
    val (sortDescending, onSortDescendingChange) =
        rememberPreference(
            PlaylistSongSortDescendingKey,
            true,
        )
    var locked by rememberPreference(PlaylistEditLockKey, defaultValue = true)

    val coroutineScope = rememberCoroutineScope()
    val syncUtils = LocalSyncUtils.current
    val snackbarHostState = remember { SnackbarHostState() }

    var isSearching by rememberSaveable { mutableStateOf(false) }

    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }

    val filteredSongs =
        remember(songs, query) {
            if (query.text.isEmpty()) {
                songs
            } else {
                songs.filter { song ->
                    song.song.song.title
                        .contains(query.text, ignoreCase = true) ||
                        song.song.artists
                            .fastAny { it.name.contains(query.text, ignoreCase = true) }
                }
            }
        }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    var inSelectMode by remember { mutableStateOf(false) }
    val selection =
        remember {
            mutableStateListOf<Int>()
        }
    var selectionAnchorMapId by remember { mutableStateOf<Int?>(null) }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
        selectionAnchorMapId = null
    }

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    val downloadUtil = LocalDownloadUtil.current
    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }

    val editable: Boolean = playlist?.playlist?.isEditable == true

    LaunchedEffect(songs) {
        selection.fastForEachReversed { mapId ->
            if (songs.find { it.map.id == mapId } == null) {
                selection.remove(Integer.valueOf(mapId))
            }
        }

        if (selectionAnchorMapId != null && songs.none { it.map.id == selectionAnchorMapId }) {
            selectionAnchorMapId = songs.firstOrNull { it.map.id in selection }?.map?.id
        }
    }

    LaunchedEffect(songs) {
        mutableSongs.apply {
            clear()
            addAll(songs)
        }
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it.song.id]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (songs.all {
                        downloads[it.song.id]?.state == Download.STATE_QUEUED ||
                            downloads[it.song.id]?.state == Download.STATE_DOWNLOADING ||
                            downloads[it.song.id]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    var showEditDialog by remember {
        mutableStateOf(false)
    }

    if (showEditDialog) {
        playlist?.playlist?.let { playlistEntity ->
            TextFieldDialog(
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.edit),
                        contentDescription = null,
                    )
                },
                title = { Text(text = stringResource(R.string.edit_playlist)) },
                onDismiss = { showEditDialog = false },
                initialTextFieldValue =
                    TextFieldValue(
                        playlistEntity.name,
                        TextRange(playlistEntity.name.length),
                    ),
                onDone = { name ->
                    database.query {
                        update(
                            playlistEntity.copy(
                                name = name,
                                lastUpdateTime = LocalDateTime.now(),
                            ),
                        )
                    }
                    viewModel.viewModelScope.launch(Dispatchers.IO) {
                        playlistEntity.browseId?.let { YouTube.renamePlaylist(it, name) }
                    }
                },
            )
        }
    }

    var showRemoveDownloadDialog by remember {
        mutableStateOf(false)
    }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text =
                        stringResource(
                            R.string.remove_download_playlist_confirm,
                            playlist?.playlist!!.name,
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = { showRemoveDownloadDialog = false },
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        if (!editable) {
                            database.transaction {
                                playlist?.id?.let { clearPlaylist(it) }
                            }
                        }
                        songs.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.song.id,
                                false,
                            )
                        }
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    var showDeletePlaylistDialog by remember {
        mutableStateOf(false)
    }
    if (showDeletePlaylistDialog) {
        DefaultDialog(
            onDismiss = { showDeletePlaylistDialog = false },
            content = {
                Text(
                    text =
                        stringResource(
                            R.string.delete_playlist_confirm,
                            playlist?.playlist!!.name,
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                    },
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                        database.query {
                            playlist?.let { delete(it.playlist) }
                        }
                        viewModel.viewModelScope.launch(Dispatchers.IO) {
                            playlist?.playlist?.browseId?.let { YouTube.deletePlaylist(it) }
                        }
                        navController.popBackStack()
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    val headerItems = 2
    val lazyListState = rememberLazyListState()
    var dragInfo by remember {
        mutableStateOf<Pair<Int, Int>?>(null)
    }
    val reorderableState =
        rememberReorderableLazyListState(
            lazyListState = lazyListState,
            scrollThresholdPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) { from, to ->
            if (to.index >= headerItems && from.index >= headerItems) {
                val currentDragInfo = dragInfo
                dragInfo =
                    if (currentDragInfo == null) {
                        (from.index - headerItems) to (to.index - headerItems)
                    } else {
                        currentDragInfo.first to (to.index - headerItems)
                    }

                mutableSongs.move(from.index - headerItems, to.index - headerItems)
            }
        }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            dragInfo?.let { (from, to) ->
                viewModel.viewModelScope.launch(Dispatchers.IO) {
                    database.withTransaction {
                        move(viewModel.playlistId, from, to)
                    }

                    // Sync order with YT Music
                    val browseId = viewModel.playlist.value?.playlist?.browseId
                    if (browseId != null) {
                        val playlistSongMap = database.playlistSongMaps(viewModel.playlistId, 0)
                        val setVideoId = playlistSongMap.getOrNull(to)?.setVideoId
                        val successorSetVideoId = playlistSongMap.getOrNull(to + 1)?.setVideoId

                        if (setVideoId != null) {
                            YouTube.moveSongPlaylist(
                                browseId,
                                setVideoId,
                                successorSetVideoId,
                            )
                        }
                    }
                }

                dragInfo = null
            }
        }
    }

    val showTopBarTitle by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0
        }
    }

    val collectionSource by remember(database, viewModel.playlistId) { database.playlistColorSource(viewModel.playlistId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val palette = rememberArtworkColors(collectionSource)
    val pureBlack by rememberPreference(PureBlackKey, false)
    val collectionBackdrop = rememberLayerBackdrop()
    CompositionLocalProvider(LocalSounSurfaceBackdrop provides collectionBackdrop) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            SounCollectionBackground(palette.ifEmpty { listOf(com.metrolist.music.ui.theme.SounAccent) }, pureBlack = pureBlack, lightField = collectionBackdrop)
            LazyColumn(
                state = lazyListState,
                contentPadding = LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues(),
            ) {
                playlist?.let { playlist ->
                    if (!isSearching) {
                        item(key = "playlist_header") {
                            LocalPlaylistHeader(
                                playlist = playlist,
                                songs = songs,
                                onlinePlaylist = onlinePlaylist,
                                onShowEditDialog = { showEditDialog = true },
                                onShowRemoveDownloadDialog = { showRemoveDownloadDialog = true },
                                onshowDeletePlaylistDialog = { showDeletePlaylistDialog = true },
                                onStartSearch = { isSearching = true },
                                snackbarHostState = snackbarHostState,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    if (playlist.songCount == 0 && (playlist.playlist.remoteSongCount ?: 0) == 0) {
                        item(key = "empty_placeholder") {
                            EmptyPlaceholder(
                                icon = R.drawable.music_note,
                                text = stringResource(R.string.playlist_is_empty),
                                modifier = Modifier.animateItem(),
                            )
                        }
                    } else {
                        item(key = "controls_row") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    Modifier
                                        .padding(start = 16.dp)
                                        .animateItem(),
                            ) {
                                SortHeader(
                                    sortType = sortType,
                                    sortDescending = sortDescending,
                                    onSortTypeChange = onSortTypeChange,
                                    onSortDescendingChange = onSortDescendingChange,
                                    sortTypeText = { sortType ->
                                        when (sortType) {
                                            PlaylistSongSortType.CUSTOM -> R.string.sort_by_custom
                                            PlaylistSongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                            PlaylistSongSortType.NAME -> R.string.sort_by_name
                                            PlaylistSongSortType.ARTIST -> R.string.sort_by_artist
                                            PlaylistSongSortType.PLAY_TIME -> R.string.sort_by_play_time
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                if (editable) {
                                    IconButton(
                                        onClick = { locked = !locked },
                                        modifier = Modifier.padding(horizontal = 6.dp),
                                    ) {
                                        Icon(
                                            painter = painterResource(if (locked) R.drawable.lock else R.drawable.lock_open),
                                            contentDescription = null,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                val displayedSongs = if (isSearching) filteredSongs else mutableSongs

                itemsIndexed(
                    items = displayedSongs,
                    key = { _, song -> song.map.id },
                ) { index, song ->
                    ReorderableItem(
                        state = reorderableState,
                        key = song.map.id,
                    ) {
                        val currentItem by rememberUpdatedState(song)

                        fun deleteFromPlaylist() {
                            // Capture values before deletion — DB entry will be gone afterwards
                            val browseId = playlist?.playlist?.browseId
                            val setVideoId = currentItem.map.setVideoId
                            val songId = currentItem.map.songId
                            val playlistId = currentItem.map.playlistId

                            database.transaction {
                                move(playlistId, currentItem.map.position, Int.MAX_VALUE)
                                delete(currentItem.map.copy(position = Int.MAX_VALUE))
                            }

                            if (browseId != null) {
                                syncUtils.scheduleRemoveFromPlaylist(
                                    browseId,
                                    songId,
                                    playlistId
                                ) {
                                    setVideoId
                                }
                            }
                        }

                        val swipeRemoveEnabled by rememberPreference(SwipeToRemoveSongKey, defaultValue = false)
                        val dismissBoxState =
                            rememberSwipeToDismissBoxState(
                                positionalThreshold = { totalDistance -> totalDistance },
                            )
                        var processedDismiss by remember { mutableStateOf(false) }
                        LaunchedEffect(dismissBoxState.currentValue) {
                            val dv = dismissBoxState.currentValue
                            if (swipeRemoveEnabled && !processedDismiss && (
                                    dv == SwipeToDismissBoxValue.StartToEnd ||
                                        dv == SwipeToDismissBoxValue.EndToStart
                                )
                            ) {
                                processedDismiss = true
                                deleteFromPlaylist()
                            }
                            if (dv == SwipeToDismissBoxValue.Settled) {
                                processedDismiss = false
                            }
                        }

                        val onCheckedChange: (Boolean) -> Unit = {
                            if (it) {
                                selection.add(song.map.id)
                            } else {
                                selection.remove(Integer.valueOf(song.map.id))
                            }
                        }

                        val content: @Composable () -> Unit = {
                            SongListItem(
                                song = song.song,
                                isActive = song.song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                showInLibraryIcon = true,
                                trailingContent = {
                                    if (inSelectMode) {
                                        Checkbox(
                                            checked = selection.contains(song.map.id),
                                            onCheckedChange = onCheckedChange,
                                        )
                                    } else {
                                        IconButton(
                                            onClick = {
                                                menuState.show {
                                                    SongMenu(
                                                        originalSong = song.song,
                                                        playlistSong = song,
                                                        playlistBrowseId = playlist?.playlist?.browseId,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.more_vert),
                                                contentDescription = null,
                                            )
                                        }

                                        if (sortType == PlaylistSongSortType.CUSTOM && !locked && !inSelectMode && !isSearching && editable) {
                                            IconButton(
                                                onClick = { },
                                                modifier = Modifier.draggableHandle(),
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.drag_handle),
                                                    contentDescription = null,
                                                )
                                            }
                                        }
                                    }
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                if (inSelectMode) {
                                                    onCheckedChange(!selection.contains(song.map.id))
                                                } else if (song.song.id == mediaMetadata?.id) {
                                                    playerConnection.togglePlayPause()
                                                } else {
                                                    playerConnection.playQueue(
                                                        ListQueue(
                                                            title = playlist!!.playlist.name,
                                                            items = songs.map { it.song.toMediaItem() },
                                                            startIndex = songs.indexOfFirst { it.map.id == song.map.id },
                                                        ),
                                                    )
                                                }
                                            },
                                            onLongClick = {
                                                if (!inSelectMode) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    inSelectMode = true
                                                    onCheckedChange(true)
                                                    selectionAnchorMapId = song.map.id
                                                } else {
                                                    val anchorIndex =
                                                        selectionAnchorMapId?.let { anchorMapId ->
                                                            displayedSongs.indexOfFirst { it.map.id == anchorMapId }
                                                        } ?: -1

                                                    if (anchorIndex == -1) {
                                                        onCheckedChange(true)
                                                        selectionAnchorMapId = song.map.id
                                                    } else {
                                                        val range = if (anchorIndex <= index) anchorIndex..index else index..anchorIndex
                                                        for (rangeIndex in range) {
                                                            val rangeMapId = displayedSongs[rangeIndex].map.id
                                                            if (rangeMapId !in selection) {
                                                                selection.add(rangeMapId)
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                        ),
                            )
                        }

                        if (locked || inSelectMode || !swipeRemoveEnabled) {
                            Box(modifier = Modifier.animateItem()) {
                                content()
                            }
                        } else {
                            SwipeToDismissBox(
                                state = dismissBoxState,
                                backgroundContent = {},
                                modifier = Modifier.animateItem(),
                            ) {
                                content()
                            }
                        }
                    }
                }
            }

            DraggableScrollbar(
                modifier =
                    Modifier
                        .padding(
                            LocalPlayerAwareWindowInsets.current
                                .union(WindowInsets.ime)
                                .asPaddingValues(),
                        ).align(Alignment.CenterEnd),
                scrollState = lazyListState,
                headerItems = 2,
            )

            TopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                title = {
                    if (inSelectMode) {
                        Text(pluralStringResource(R.plurals.n_selected, selection.size, selection.size))
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
                    } else if (showTopBarTitle) {
                        Text(playlist?.playlist?.name.orEmpty())
                    }
                },
                navigationIcon = {
                    if (inSelectMode) {
                        IconButton(onClick = onExitSelectionMode) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = null,
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                if (isSearching) {
                                    isSearching = false
                                    query = TextFieldValue()
                                } else {
                                    navController.navigateUp()
                                }
                            },
                            onLongClick = {
                                if (!isSearching) {
                                    navController.backToMain()
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back),
                                contentDescription = null,
                            )
                        }
                    }
                },
                actions = {
                    if (inSelectMode) {
                        Checkbox(
                            checked = selection.size == songs.size && selection.isNotEmpty(),
                            onCheckedChange = {
                                if (selection.size == songs.size) {
                                    selection.clear()
                                } else {
                                    selection.clear()
                                    selection.addAll(songs.map { it.map.id })
                                }
                            },
                        )
                        IconButton(
                            enabled = selection.isNotEmpty(),
                            onClick = {
                                menuState.show {
                                    SelectionSongMenu(
                                        songSelection =
                                            selection.mapNotNull { mapId ->
                                                songs.find { it.map.id == mapId }?.song
                                            },
                                        songPosition =
                                            selection.mapNotNull { mapId ->
                                                songs.find { it.map.id == mapId }?.map
                                            },
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
                        // Only search button remains in TopAppBar
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
                modifier =
                    Modifier
                        .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime))
                        .align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
fun LocalPlaylistHeader(
    playlist: Playlist,
    songs: List<PlaylistSong>,
    onlinePlaylist: PlaylistItem?,
    onShowEditDialog: () -> Unit,
    onShowRemoveDownloadDialog: () -> Unit,
    onshowDeletePlaylistDialog: () -> Unit,
    onStartSearch: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier,
) {
    val navController = LocalNavController.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val syncUtils = LocalSyncUtils.current
    val scope = rememberCoroutineScope()
    val editPlaylistCoverStr = stringResource(R.string.edit_playlist_cover)
    val playlistSyncedStr = stringResource(R.string.playlist_synced)

    val playlistLength =
        remember(songs) {
            songs.fastSumBy { it.song.song.duration }
        }

    val downloadUtil = LocalDownloadUtil.current
    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }

    val liked = playlist.playlist.bookmarkedAt != null
    val editable: Boolean = playlist.playlist.isEditable

    val colorSource by remember(database, playlist.id) { database.playlistColorSource(playlist.id) }
        .collectAsStateWithLifecycle(initialValue = null)
    val isCustomThumbnail = playlist.playlist.thumbnailUrl?.let {
        if (playlist.playlist.browseId == null) true
        else it.contains("studio_square_thumbnail") || it.startsWith("content://${context.packageName}")
    } ?: false
    var coverBusy by remember { mutableStateOf(false) }

    val result = rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingCropDestUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var showEditNoteDialog by remember { mutableStateOf(false) }

    val cropLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == android.app.Activity.RESULT_OK) {
                val output = res.data?.let { UCrop.getOutput(it) } ?: pendingCropDestUri
                if (output != null) result.value = output
            }
        }

    val (darkMode, _) =
        rememberEnumPreference(
            DarkModeKey,
            defaultValue = DarkMode.AUTO,
        )

    val cropColor = MaterialTheme.colorScheme
    val darkTheme = darkMode == DarkMode.ON || (darkMode == DarkMode.AUTO && isSystemInDarkTheme())

    val pickLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            uri?.let { sourceUri ->
                val destFile = java.io.File(context.cacheDir, "playlist_cover_crop_${System.currentTimeMillis()}.jpg")
                val destUri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", destFile)
                pendingCropDestUri = destUri

                val options =
                    UCrop.Options().apply {
                        setCompressionFormat(Bitmap.CompressFormat.JPEG)
                        setCompressionQuality(90)
                        setHideBottomControls(true)
                        setToolbarTitle(editPlaylistCoverStr)

                        setStatusBarLight(!darkTheme)

                        setToolbarColor(cropColor.surface.toArgb())
                        setToolbarWidgetColor(cropColor.inverseSurface.toArgb())
                        setRootViewBackgroundColor(cropColor.surface.toArgb())
                        setLogoColor(cropColor.surface.toArgb())
                    }

                val intent =
                    UCrop
                        .of(sourceUri, destUri)
                        .withAspectRatio(1f, 1f)
                        .withMaxResultSize(1200, 1200)
                        .withOptions(options)
                        .getIntent(context)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                cropLauncher.launch(intent)
            }
        }

    suspend fun updateCover(value: String?) {
        withContext(Dispatchers.IO) {
            var old: String? = null
            database.withTransaction {
                val latest = database.playlist(playlist.id).first()?.playlist
                    ?: throw java.io.IOException("Playlist no longer exists")
                old = latest.thumbnailUrl
                database.update(latest.copy(thumbnailUrl = value))
            }
            if (old != value) deletePlaylistCover(context, old)
        }
    }

    val coverErrorText = stringResource(R.string.soun_cover_update_failed)
    LaunchedEffect(result.value) {
        val uri = result.value ?: return@LaunchedEffect
        coverBusy = true
        var stored: String? = null
        try {
            val cover = withContext(Dispatchers.IO) {
                val browseId = playlist.playlist.browseId
                if (browseId == null) {
                    persistPlaylistCover(context, uri).also { stored = it }
                } else {
                    val bytes = uriToByteArray(context, uri) ?: throw java.io.IOException("Cannot read playlist cover")
                    YouTube.uploadCustomThumbnailLink(browseId, bytes).getOrThrow()
                }
            }
            updateCover(cover)
            stored = null
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            reportException(error)
            snackbarHostState.showSnackbar(coverErrorText)
        } finally {
            withContext(kotlinx.coroutines.NonCancellable) {
                stored?.let { candidate ->
                    withContext(Dispatchers.IO) {
                        val retained = runCatching { database.playlist(playlist.id).first()?.playlist?.thumbnailUrl == candidate }.getOrDefault(true)
                        if (!retained) deletePlaylistCover(context, candidate)
                    }
                }
                result.value = null
                coverBusy = false
            }
        }
    }

    val chooseCover: () -> Unit = {
        if (playlist.playlist.browseId == null) {
            pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else {
            showEditNoteDialog = true
        }
    }
    val removeCover: () -> Unit = {
        scope.launch {
            coverBusy = true
            try {
                val restored = withContext(Dispatchers.IO) {
                    playlist.playlist.browseId?.let { YouTube.removeThumbnailPlaylist(it).getOrThrow() }
                }
                updateCover(restored)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                reportException(error)
                snackbarHostState.showSnackbar(coverErrorText)
            } finally {
                coverBusy = false
            }
        }
    }

    LaunchedEffect(songs) {
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it.song.id]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (songs.all {
                        downloads[it.song.id]?.state == Download.STATE_QUEUED ||
                            downloads[it.song.id]?.state == Download.STATE_DOWNLOADING ||
                            downloads[it.song.id]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    val openPlaylistMenu: () -> Unit = {
        menuState.show {
            LocalPlaylistMenu(
                playlist = playlist,
                songs = songs,
                context = context,
                downloadState = downloadState,
                onEdit = onShowEditDialog,
                onEditCover = chooseCover.takeIf { !coverBusy },
                onSync = {
                    scope.launch(Dispatchers.IO) {
                        syncUtils.syncPlaylistSuspend(
                            playlist.playlist.browseId!!,
                            playlist.id,
                        )
                        withContext(Dispatchers.Main) {
                            snackbarHostState.showSnackbar(playlistSyncedStr)
                        }
                    }
                },
                onDelete = onshowDeletePlaylistDialog,
                onDownload = {
                    when (downloadState) {
                        Download.STATE_COMPLETED -> {
                            onShowRemoveDownloadDialog()
                        }

                        Download.STATE_DOWNLOADING -> {
                            songs.forEach { song ->
                                DownloadService.sendRemoveDownload(
                                    context,
                                    ExoDownloadService::class.java,
                                    song.song.id,
                                    false,
                                )
                            }
                        }

                        else -> {
                            songs.forEach { song ->
                                downloadUtil.download(song.song)
                            }
                        }
                    }
                },
                onQueue = {
                    playerConnection.addToQueue(
                        items = songs.map { it.song.toMediaItem() },
                    )
                },
                onDismiss = { menuState.dismiss() },
            )
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showEditNoteDialog) {
            ActionPromptDialog(
                title = stringResource(R.string.edit_playlist_cover),
                onDismiss = { showEditNoteDialog = false },
                onConfirm = {
                    showEditNoteDialog = false
                    pickLauncher.launch(
                        PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onCancel = { showEditNoteDialog = false },
            ) {
                if (playlist.playlist.browseId != null) {
                    Text(
                        text = stringResource(R.string.edit_playlist_cover_note),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    text = stringResource(R.string.edit_playlist_cover_note_wait),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
        val songCount = if (playlist.songCount == 0) playlist.playlist.remoteSongCount ?: 0 else playlist.songCount
        SounCollectionHeader(
            name = playlist.playlist.name,
            subtitle = listOfNotNull(
                pluralStringResource(R.plurals.n_song, songCount, songCount),
                makeTimeString(playlistLength * 1000L).takeIf { playlistLength > 0 },
            ).joinToString(" · "),
            artwork = null,
            colorSource = colorSource,
            thumbnail = playlist.playlist.thumbnailUrl,
            enabled = songs.isNotEmpty(),
            onPlay = { playerConnection.playQueue(ListQueue(title = playlist.playlist.name, items = songs.map { it.song.toMediaItem() })) },
            onShuffle = { playerConnection.playQueue(ListQueue(title = playlist.playlist.name, items = songs.shuffled().map { it.song.toMediaItem() })) },
            onMore = openPlaylistMenu,
            moreEnabled = true,
        )
        if (editable) {
            FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = chooseCover, enabled = !coverBusy) {
                    Icon(painterResource(R.drawable.insert_photo), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (isCustomThumbnail) R.string.soun_change_playlist_photo else R.string.soun_add_playlist_photo))
                }
                if (isCustomThumbnail) TextButton(onClick = removeCover, enabled = !coverBusy) {
                    Text(stringResource(R.string.remove_custom_image))
                }
            }
            if (coverBusy) LinearProgressIndicator(modifier = Modifier.width(160.dp).padding(top = 4.dp))
            Spacer(Modifier.height(12.dp))
        }

        val onlineAuthor = onlinePlaylist?.author
        if (onlineAuthor != null) {
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier.combinedClickable(
                        onClick = {
                            if (onlineAuthor.id != null) {
                                navController.navigate("artist/${onlineAuthor.id}")
                            }
                        },
                    ),
            ) {
                if (onlinePlaylist.authorAvatarUrl != null) {
                    AsyncImage(
                        model = onlinePlaylist.authorAvatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape),
                    )
                }
                Text(
                    text = onlineAuthor.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        val description = onlinePlaylist?.description
        if (!description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))

            ExpandableText(
                text = description,
                modifier = Modifier.padding(horizontal = 32.dp),
                collapsedMaxLines = 3,
            )
        }

    }
}

fun uriToByteArray(
    context: Context,
    uri: Uri,
): ByteArray? =
    try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (_: SecurityException) {
        null
    }
