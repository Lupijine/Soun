/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import com.metrolist.music.ui.theme.LocalSounCollectionRows
import com.metrolist.music.ui.theme.sounSurface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.util.fastForEachReversed
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.HistorySource
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.extensions.metadata
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.HideOnScrollFAB
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.ui.menu.SelectionMediaMetadataMenu
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.DateAgo
import com.metrolist.music.viewmodels.HistoryViewModel
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    navController: NavController,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection =
        rememberSaveable(
            saver =
                listSaver<MutableList<Long>, Long>(
                    save = { it.toList() },
                    restore = { it.toMutableStateList() },
                ),
        ) { mutableStateListOf() }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
    }

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
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

    val historySource by viewModel.historySource.collectAsStateWithLifecycle()

    val historyPage by viewModel.historyPage.collectAsStateWithLifecycle()

    val events by viewModel.events.collectAsStateWithLifecycle()

    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn =
        remember(innerTubeCookie) {
            "SAPISID" in parseCookieString(innerTubeCookie)
        }

    @Composable
    fun dateAgoToString(dateAgo: DateAgo): String =
        when (dateAgo) {
            DateAgo.Today -> stringResource(R.string.today)
            DateAgo.Yesterday -> stringResource(R.string.yesterday)
            DateAgo.ThisWeek -> stringResource(R.string.this_week)
            DateAgo.LastWeek -> stringResource(R.string.last_week)
            is DateAgo.Other -> dateAgo.date.format(DateTimeFormatter.ofPattern("yyyy/MM"))
        }

    val filteredEvents =
        remember(events, query) {
            if (query.text.isEmpty()) {
                events
            } else {
                events
                    .mapValues { (_, songs) ->
                        songs.filter { event ->
                            event.song.song.title
                                .contains(query.text, ignoreCase = true) ||
                                event.song.artists.any {
                                    it.name.contains(
                                        query.text,
                                        ignoreCase = true,
                                    )
                                }
                        }
                    }.filterValues { it.isNotEmpty() }
            }
        }

    val filteredRemoteContent =
        remember(historyPage, query) {
            if (query.text.isEmpty()) {
                historyPage?.sections
            } else {
                historyPage
                    ?.sections
                    ?.map { section ->
                        section.copy(
                            songs =
                                section.songs.filter { song ->
                                    song.title.contains(query.text, ignoreCase = true) ||
                                        song.artists.any { it.name.contains(query.text, ignoreCase = true) }
                                },
                        )
                    }?.filter { it.songs.isNotEmpty() }
            }
        }

    val allEvents =
        remember(filteredEvents) {
            filteredEvents.values.flatten()
        }

    LaunchedEffect(allEvents) {
        selection.fastForEachReversed { eventId ->
            if (allEvents.find { it.event.id == eventId } == null) {
                selection.remove(eventId)
            }
        }
    }

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn && viewModel.historySource.value == HistorySource.REMOTE) {
            onExitSelectionMode()
            viewModel.historySource.value = HistorySource.LOCAL
        }
    }

    val lazyListState = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalSounCollectionRows provides true) {
            LazyColumn(
                state = lazyListState,
                contentPadding =
                    LocalPlayerAwareWindowInsets.current
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                        .asPaddingValues(),
                modifier =
                    Modifier.windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Top,
                        ),
                    ),
            ) {
                if (isLoggedIn) item(key = "history_source") {
                    HistorySourceTabs(historySource) { source ->
                        onExitSelectionMode()
                        viewModel.historySource.value = source
                        if (source == HistorySource.REMOTE) viewModel.fetchRemoteHistory()
                    }
                }

                val hasNoResults = if (historySource == HistorySource.REMOTE && isLoggedIn) {
                    filteredRemoteContent != null && filteredRemoteContent.all { it.songs.isEmpty() }
                } else filteredEvents.isEmpty()
                if (hasNoResults) item(key = "history_empty") {
                    HistoryEmptyState(isSearching = query.text.isNotEmpty())
                }

                if (historySource == HistorySource.REMOTE && isLoggedIn) {
                    filteredRemoteContent?.forEachIndexed { sectionIndex, section ->
                        item {
                            HistorySectionHeading(section.title, section.songs.size)
                        }

                        itemsIndexed(
                            items = section.songs,
                            key = { index, song -> "remote_${sectionIndex}_${song.id}_$index" },
                        ) { _, song ->
                            YouTubeListItem(
                                item = song,
                                isActive = song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            menuState.show {
                                                YouTubeSongMenu(
                                                    song = song,
                                                    onDismiss = menuState::dismiss,
                                                    onHistoryRemoved = {
                                                        viewModel.fetchRemoteHistory()
                                                    },
                                                )
                                            }
                                        },
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.more_vert),
                                            contentDescription = null,
                                        )
                                    }
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                if (song.id == mediaMetadata?.id) {
                                                    playerConnection.togglePlayPause()
                                                } else {
                                                    playerConnection.playQueue(
                                                        YouTubeQueue.radio(song.toMediaMetadata()),
                                                    )
                                                }
                                            },
                                            onLongClick = {
                                                menuState.show {
                                                    YouTubeSongMenu(
                                                        song = song,
                                                        onDismiss = menuState::dismiss,
                                                        onHistoryRemoved = {
                                                            viewModel.fetchRemoteHistory()
                                                        },
                                                    )
                                                }
                                            },
                                        ).animateItem(),
                            )
                        }
                    }
                } else {
                    filteredEvents.forEach { (dateAgo, dateEvents) ->
                        item {
                            HistorySectionHeading(dateAgoToString(dateAgo), dateEvents.size)
                        }

                        itemsIndexed(
                            items = dateEvents,
                            key = { index, event -> "${dateAgo}_${event.event.id}_$index" },
                        ) { index, event ->
                            val onCheckedChange: (Boolean) -> Unit = {
                                if (it) {
                                    selection.add(event.event.id)
                                } else {
                                    selection.remove(event.event.id)
                                }
                            }
                            val dateTitle = dateAgoToString(dateAgo)

                            SongListItem(
                                song = event.song,
                                isSelected = inSelectMode && event.event.id in selection,
                                isActive = event.song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                showInLibraryIcon = true,
                                trailingContent = {
                                    if (inSelectMode) {
                                        Checkbox(
                                            checked = event.event.id in selection,
                                            onCheckedChange = onCheckedChange,
                                        )
                                    } else {
                                        IconButton(
                                            onClick = {
                                                menuState.show {
                                                    SongMenu(
                                                        originalSong = event.song,
                                                        event = event.event,
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
                                    }
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                if (inSelectMode) {
                                                    onCheckedChange(event.event.id !in selection)
                                                } else if (event.song.id == mediaMetadata?.id) {
                                                    playerConnection.togglePlayPause()
                                                } else {
                                                    playerConnection.playQueue(
                                                        ListQueue(
                                                            title = dateTitle,
                                                            items = dateEvents.map { it.song.toMediaItem() },
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
                                                }
                                            },
                                        ).animateItem(),
                            )
                        }
                    }
                }
            }

        }

        val historyTitle = stringResource(R.string.history)

        HideOnScrollFAB(
            visible =
                if (historySource == HistorySource.REMOTE) {
                    filteredRemoteContent?.any { it.songs.isNotEmpty() } == true
                } else {
                    allEvents.isNotEmpty()
                },
            lazyListState = lazyListState,
            icon = R.drawable.shuffle,
            onClick = {
                if (historySource == HistorySource.REMOTE && historyPage != null) {
                    val songs = filteredRemoteContent?.flatMap { it.songs } ?: emptyList()
                    if (songs.isNotEmpty()) {
                        playerConnection.playQueue(
                            ListQueue(
                                title = historyTitle,
                                items = songs.map { it.toMediaItem() }.shuffled(),
                            ),
                        )
                    }
                } else {
                    playerConnection.playQueue(
                        ListQueue(
                            title = historyTitle,
                            items = allEvents.map { it.song.toMediaItem() }.shuffled(),
                        ),
                    )
                }
            },
        )
    }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
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
            } else {
                Text(stringResource(R.string.history), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
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
                    checked = selection.size == allEvents.size && selection.isNotEmpty(),
                    onCheckedChange = {
                        if (selection.size == allEvents.size) {
                            selection.clear()
                        } else {
                            selection.clear()
                            selection.addAll(allEvents.map { it.event.id })
                        }
                    },
                )
                IconButton(
                    enabled = selection.isNotEmpty(),
                    onClick = {
                        menuState.show {
                            SelectionMediaMetadataMenu(
                                songSelection =
                                    selection.mapNotNull { eventId ->
                                        allEvents
                                            .find { it.event.id == eventId }
                                            ?.song
                                            ?.toMediaItem()
                                            ?.metadata
                                    },
                                onDismiss = menuState::dismiss,
                                clearAction = onExitSelectionMode,
                                currentItems = emptyList(),
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
}

@Composable
internal fun HistorySourceTabs(current: HistorySource, onSelect: (HistorySource) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)
            .sounSurface(RoundedCornerShape(24.dp)).selectableGroup().padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(HistorySource.LOCAL to R.string.local_history, HistorySource.REMOTE to R.string.remote_history).forEach { (source, label) ->
            TextButton(
                onClick = { onSelect(source) }, modifier = Modifier.weight(1f).height(44.dp).semantics {
                    selected = source == current
                    role = Role.Tab
                },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (source == current) MaterialTheme.colorScheme.primary.copy(alpha = 0.24f) else Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) { Text(stringResource(label), style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
internal fun HistorySectionHeading(title: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface)
        Text(pluralStringResource(R.plurals.n_song, count, count), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun HistoryEmptyState(isSearching: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(R.drawable.history), contentDescription = null,
            modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(if (isSearching) R.string.soun_history_no_matches else R.string.soun_history_empty),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
