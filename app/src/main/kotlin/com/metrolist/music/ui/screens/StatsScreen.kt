/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import com.metrolist.music.ui.theme.LocalSounSurfaceBackdrop
import com.metrolist.music.ui.theme.sounGlassColors
import com.metrolist.music.ui.theme.sounGlass
import com.metrolist.music.ui.theme.sounPanel
import com.metrolist.music.ui.component.StatsListeningClock
import com.metrolist.music.ui.component.StatsAlbumTile
import com.metrolist.music.ui.component.StatsRankedRow
import com.metrolist.music.ui.component.StatsSectionHeader
import com.metrolist.music.ui.component.StatsTopArtistCard
import com.metrolist.music.ui.component.StatsFactsRow
import com.metrolist.music.ui.component.StatsHeroCard
import com.metrolist.music.ui.component.StatsRangeHeader
import com.metrolist.music.utils.StatsRange
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.CONTENT_TYPE_ARTIST
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.StatPeriod
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.ArtistListItem
import com.metrolist.music.ui.component.ChoiceChipsRow
import com.metrolist.music.ui.component.EmptyPlaceholder
import com.metrolist.music.ui.component.HideOnScrollFAB
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.LocalAlbumsGrid
import com.metrolist.music.ui.component.LocalArtistsGrid
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.LocalSongsGrid
import com.metrolist.music.ui.component.NavigationTitle
import com.metrolist.music.ui.component.PlaylistGridItem
import com.metrolist.music.ui.component.TimeTransfer
import com.metrolist.music.ui.menu.AlbumMenu
import com.metrolist.music.ui.menu.ArtistMenu
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.joinByBullet
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.StatsViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val sArtists = viewModel.selectedArtists // SnapshotStateList<Artist>

// Helper actions:
    val toggleArtistSelection: (Artist) -> Unit = { artist ->
        if (sArtists.any { it.id == artist.id }) {
            sArtists.removeAll { it.id == artist.id }
        } else {
            sArtists.add(artist)
        }
    }

    val clearArtistSelection: () -> Unit = {
        sArtists.clear()
    }
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val context = LocalContext.current

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

    val indexChips by viewModel.indexChips.collectAsStateWithLifecycle()
    val mostPlayedSongs by viewModel.mostPlayedSongs.collectAsStateWithLifecycle()
    val mostPlayedSongsStats by viewModel.filteredSongs.collectAsStateWithLifecycle()
    val mostPlayedArtists by viewModel.filteredArtists.collectAsStateWithLifecycle()
    val mostPlayedAlbums by viewModel.filteredAlbums.collectAsStateWithLifecycle()
    val allArtists by viewModel.mostPlayedArtists.collectAsStateWithLifecycle()
    val firstEvent by viewModel.firstEvent.collectAsStateWithLifecycle()
    val weeklyMostPlaylist by viewModel.weeklyMostPlaylist.collectAsStateWithLifecycle()
    val monthlyMostPlaylist by viewModel.monthlyMostPlaylist.collectAsStateWithLifecycle()
    val recapPlaylists by viewModel.recapPlaylists.collectAsStateWithLifecycle()
    val currentDate = LocalDateTime.now()
    val orderedMostPlayedSongs =
        remember(mostPlayedSongsStats, mostPlayedSongs) {
            val songsById = mostPlayedSongs.associateBy { it.song.id }
            mostPlayedSongsStats.mapNotNull { statsSong -> songsById[statsSong.id] }
        }
    val mostPeriodPlaylists = listOfNotNull(weeklyMostPlaylist, monthlyMostPlaylist)
    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn =
        remember(innerTubeCookie) {
            "SAPISID" in parseCookieString(innerTubeCookie)
        }
    val visibleStatsPlaylists =
        remember(mostPeriodPlaylists, recapPlaylists, isLoggedIn) {
            if (isLoggedIn) {
                (mostPeriodPlaylists + recapPlaylists).distinctBy { it.id }
            } else {
                mostPeriodPlaylists
            }
        }

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val selectedOption by viewModel.selectedOption.collectAsStateWithLifecycle()

    var showTimeTransfer by rememberSaveable { mutableStateOf(false) }
    val range by viewModel.range.collectAsStateWithLifecycle()
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    var rangeBeforeTransfer by remember { mutableStateOf<StatsRange?>(null) }

    LaunchedEffect(showTimeTransfer) {
        if (showTimeTransfer) {
            // The transfer dialog needs every song ever played.
            if (rangeBeforeTransfer == null) rangeBeforeTransfer = viewModel.range.value
            viewModel.range.value = StatsRange(StatsRange.Kind.ALL)
        }
    }

    if (showTimeTransfer) {
        TimeTransfer(
            onDismiss = {
                showTimeTransfer = false
                rangeBeforeTransfer?.let { viewModel.range.value = it }
                rangeBeforeTransfer = null
            },
        )
    }

    LaunchedEffect(Unit) {
        viewModel.syncMostPlaylistsIfNeeded()
    }

    // Soun: period label and whether there is older listening to step back to.
    val bounds = remember(range) { range.bounds(LocalDateTime.now()) }
    val firstListen = firstEvent?.event?.timestamp
    val dayMonth = remember { DateTimeFormatter.ofPattern("d MMM") }
    val periodLabel =
        when (range.kind) {
            StatsRange.Kind.WEEK ->
                if (range.offset == 0) {
                    stringResource(R.string.soun_stats_last_7_days)
                } else {
                    "${dayMonth.format(bounds.first)} – ${dayMonth.format(bounds.second.minusDays(1))}"
                }
            StatsRange.Kind.MONTH -> DateTimeFormatter.ofPattern("LLLL yyyy").format(bounds.first)
            StatsRange.Kind.YEAR -> "${bounds.first.year}"
            StatsRange.Kind.ALL ->
                firstListen?.let { stringResource(R.string.soun_stats_since, DateTimeFormatter.ofPattern("d MMM yyyy").format(it)) }
                    ?: stringResource(R.string.soun_stats_all)
        }
    val canGoBack = firstListen != null && firstListen.isBefore(bounds.first)
    var showAllSongs by rememberSaveable(range) { mutableStateOf(false) }
    var showAllArtists by rememberSaveable(range) { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            contentPadding =
                LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                    .asPaddingValues(),
            modifier =
                Modifier.windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top),
                ),
        ) {
            val filteredArtists =
                allArtists
                    .map { artistWrapper ->
                        Artist(
                            id = artistWrapper.artist.id,
                            name = artistWrapper.artist.name,
                        )
                    }.filter { artist ->
                        artist.name.contains(query.text, ignoreCase = true)
                    }

            if (!isSearching) {
                item(key = "stats_title") {
                    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 14.dp)) {
                        Text(
                            stringResource(R.string.soun_stats_title),
                            fontSize = 36.sp,
                            lineHeight = 40.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.8).sp,
                        )
                        if (sArtists.isNotEmpty()) {
                            Text(
                                sArtists.joinToString { it.name },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                            )
                        }
                    }
                }

                item(key = "stats_range") {
                    StatsRangeHeader(
                        range = range,
                        periodLabel = periodLabel,
                        canGoBack = canGoBack,
                        onRangeChange = { viewModel.range.value = it },
                    )
                }

                if (overview.totalPlayTimeMs <= 0L) {
                    item(key = "stats_empty") {
                        Text(
                            stringResource(R.string.soun_stats_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                                .fillMaxWidth()
                                .sounPanel(RoundedCornerShape(28.dp))
                                .padding(32.dp),
                        )
                    }
                } else {
                    item(key = "stats_hero") {
                        StatsHeroCard(overview, range.kind, Modifier.padding(top = 6.dp).animateItem())
                    }
                    item(key = "stats_facts") {
                        StatsFactsRow(
                            plays = overview.plays,
                            songs = overview.uniqueSongs,
                            artists = overview.uniqueArtists,
                            modifier = Modifier.padding(top = 10.dp).animateItem(),
                        )
                    }
                }

                mostPlayedArtists.firstOrNull()?.let { top ->
                    item(key = "stats_top_artist") {
                        Column(Modifier.animateItem()) {
                            Spacer(Modifier.height(10.dp))
                            StatsTopArtistCard(
                                name = top.artist.name,
                                imageUrl = top.artist.thumbnailUrl,
                                playTimeMs = top.timeListened?.toLong() ?: 0L,
                                plays = top.songCount,
                                onClick = { navController.navigate("artist/${top.id}") },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        ArtistMenu(originalArtist = top, coroutineScope = coroutineScope, onDismiss = menuState::dismiss)
                                    }
                                },
                            )
                        }
                    }
                }

                if (mostPlayedSongsStats.isNotEmpty()) {
                    item(key = "stats_songs_header") {
                        StatsSectionHeader(
                            title = stringResource(R.string.soun_stats_top_songs),
                            modifier = Modifier.animateItem(),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatsHeaderButton(R.drawable.shuffle, stringResource(R.string.shuffle)) {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = context.getString(R.string.most_played_songs),
                                            items = orderedMostPlayedSongs.map { it.toMediaMetadata().toMediaItem() }.shuffled(),
                                        ),
                                    )
                                }
                                StatsHeaderButton(R.drawable.play, stringResource(R.string.play_all)) {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = context.getString(R.string.most_played_songs),
                                            items = orderedMostPlayedSongs.map { it.toMediaMetadata().toMediaItem() },
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    val shownSongs = if (showAllSongs) mostPlayedSongsStats else mostPlayedSongsStats.take(10)
                    itemsIndexed(
                        items = shownSongs,
                        key = { index, song -> "stats_song_${song.id}_$index" },
                    ) { index, song ->
                        StatsRankedRow(
                            rank = index + 1,
                            imageUrl = song.thumbnailUrl,
                            round = false,
                            title = song.title,
                            subtitle = song.artists.joinToString { it.name }.ifBlank { song.artistName.orEmpty() },
                            detail = "${pluralStringResource(R.plurals.n_time, song.songCountListened, song.songCountListened)}\n${makeTimeString(song.timeListened)}",
                            isActive = song.id == mediaMetadata?.id,
                            onClick = {
                                if (song.id == mediaMetadata?.id) {
                                    playerConnection.togglePlayPause()
                                } else {
                                    mostPlayedSongs.find { it.id == song.id }?.let { targetSong ->
                                        playerConnection.playQueue(
                                            YouTubeQueue(
                                                endpoint = WatchEndpoint(song.id),
                                                preloadItem = targetSong.toMediaMetadata(),
                                            ),
                                        )
                                    }
                                }
                            },
                            onLongClick = {
                                mostPlayedSongs.find { it.id == song.id }?.let { targetSong ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show { SongMenu(originalSong = targetSong, onDismiss = menuState::dismiss) }
                                }
                            },
                            modifier = Modifier.padding(horizontal = 6.dp).animateItem(),
                        )
                    }
                    if (mostPlayedSongsStats.size > 10) {
                        item(key = "stats_songs_more") {
                            StatsShowMore(
                                expanded = showAllSongs,
                                count = mostPlayedSongsStats.size,
                                onToggle = { showAllSongs = !showAllSongs },
                            )
                        }
                    }
                }

                if (mostPlayedArtists.size > 1) {
                    item(key = "stats_artists_header") {
                        StatsSectionHeader(stringResource(R.string.soun_stats_top_artists), Modifier.animateItem())
                    }
                    val shownArtists = mostPlayedArtists.drop(1).let { if (showAllArtists) it else it.take(9) }
                    itemsIndexed(
                        items = shownArtists,
                        key = { _, artist -> "stats_artist_row_${artist.id}" },
                    ) { index, artist ->
                        StatsRankedRow(
                            rank = index + 2,
                            imageUrl = artist.artist.thumbnailUrl,
                            round = true,
                            title = artist.artist.name,
                            subtitle = pluralStringResource(R.plurals.n_time, artist.songCount, artist.songCount),
                            detail = makeTimeString(artist.timeListened?.toLong()),
                            isActive = false,
                            onClick = { navController.navigate("artist/${artist.id}") },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    ArtistMenu(originalArtist = artist, coroutineScope = coroutineScope, onDismiss = menuState::dismiss)
                                }
                            },
                            modifier = Modifier.padding(horizontal = 6.dp).animateItem(),
                        )
                    }
                    if (mostPlayedArtists.size > 10) {
                        item(key = "stats_artists_more") {
                            StatsShowMore(
                                expanded = showAllArtists,
                                count = mostPlayedArtists.size,
                                onToggle = { showAllArtists = !showAllArtists },
                            )
                        }
                    }
                }

                if (mostPlayedAlbums.isNotEmpty()) {
                    item(key = "stats_albums") {
                        Column(Modifier.animateItem()) {
                            StatsSectionHeader(stringResource(R.string.soun_stats_top_albums))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                itemsIndexed(items = mostPlayedAlbums, key = { _, album -> album.id }) { index, album ->
                                    StatsAlbumTile(
                                        rank = index + 1,
                                        imageUrl = album.album.thumbnailUrl,
                                        title = album.album.title,
                                        subtitle = makeTimeString(album.timeListened),
                                        onClick = { navController.navigate("album/${album.id}") },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show { AlbumMenu(originalAlbum = album, onDismiss = menuState::dismiss) }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                if (overview.peakHour != null) {
                    item(key = "stats_clock") {
                        Column(Modifier.animateItem()) {
                            StatsSectionHeader(stringResource(R.string.soun_stats_when))
                            StatsListeningClock(overview)
                        }
                    }
                }

                if (visibleStatsPlaylists.isNotEmpty() && sArtists.isEmpty()) {
                    item(key = "stats_playlists") {
                        Column(Modifier.animateItem()) {
                            StatsSectionHeader(stringResource(R.string.soun_stats_playlists))
                            LazyRow(contentPadding = PaddingValues(horizontal = 4.dp)) {
                                itemsIndexed(items = visibleStatsPlaylists, key = { _, playlist -> playlist.id }) { _, playlist ->
                                    PlaylistGridItem(
                                        playlist = playlist,
                                        autoPlaylist = true,
                                        modifier = Modifier.combinedClickable(
                                            onClick = { navController.navigate("local_playlist/${playlist.id}") },
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "stats_bottom_space") { Spacer(Modifier.height(24.dp)) }
            }

            if (isSearching) {
                items(
                    items =
                        allArtists.filter { artist ->
                            artist.artist.name.contains(query.text, ignoreCase = true)
                        },
                    key = { "stats_artist_${it.id}" },
                    contentType = { CONTENT_TYPE_ARTIST },
                ) { artist ->
                    val uiArtist = Artist(name = artist.artist.name, id = artist.id)
                    val isChecked = sArtists.any { it.id == uiArtist.id }
                    Row( // Use a row to arrange the checkbox and ArtistListItem horizontally
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    toggleArtistSelection(uiArtist)
                                }.padding(8.dp),
                    ) {
                        ArtistListItem(
                            artist = artist,
                            modifier = Modifier.weight(1f), // Allow ArtistListItem to take remaining space
                        )

                        Checkbox(
                            checked = sArtists.contains(Artist(name = artist.artist.name, id = artist.id)), // Get the current checked state
                            onCheckedChange = {
                                toggleArtistSelection(uiArtist)
                            },
                        )
                    }
                }
            }
            if (query.text.isNotEmpty() && filteredArtists.isEmpty()) {
                item(key = "no_result") {
                    EmptyPlaceholder(
                        icon = R.drawable.search,
                        text = stringResource(R.string.no_results_found),
                    )
                }
            }
        }
    }

    // A soft fade behind the transparent top bar so its glass buttons stay readable over the list.
    val scrimColor = MaterialTheme.colorScheme.surface
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
            .height(64.dp)
            .drawBehind {
                drawRect(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(scrimColor.copy(alpha = 0.85f), scrimColor.copy(alpha = 0f)),
                        startY = -size.height,
                        endY = size.height,
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, -size.height),
                    size = androidx.compose.ui.geometry.Size(size.width, size.height * 2),
                )
            },
    )

    TopAppBar(
        title = {
            if (inSelectMode) {
                Text(pluralStringResource(R.plurals.n_selected, selection.size, selection.size))
            } else if (isSearching) {
                Row {
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
                                .weight(1f)
                                .focusRequester(focusRequester),
                    )

                    if (sArtists.isNotEmpty()) {
                        androidx.compose.material3.IconButton(onClick = clearArtistSelection) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = "Clear Artists",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        navigationIcon = {
            if (inSelectMode) {
                androidx.compose.material3.IconButton(onClick = onExitSelectionMode) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = "Select Button",
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
                    modifier = Modifier.sounGlass(CircleShape, sounGlassColors(), backdrop = LocalSounSurfaceBackdrop.current),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = "Back Button",
                    )
                }
            }
        },
        actions = {
            if (inSelectMode) {
                Checkbox(
                    checked = true,
                    onCheckedChange = {
                    },
                )
                androidx.compose.material3.IconButton(
                    enabled = selection.isNotEmpty(),
                    onClick = {
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = "More Button",
                    )
                }
            } else if (!isSearching) {
                androidx.compose.material3.IconButton(
                    onClick = { isSearching = true },
                    modifier = Modifier.sounGlass(CircleShape, sounGlassColors(), backdrop = LocalSounSurfaceBackdrop.current),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.search),
                        contentDescription = "Search Button",
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { showTimeTransfer = true },
                    onLongClick = { showTimeTransfer = true },
                    modifier = Modifier.sounGlass(CircleShape, sounGlassColors(), backdrop = LocalSounSurfaceBackdrop.current),
                ) {
                    Icon(
                        painterResource(R.drawable.sync),
                        contentDescription = "Time Transfer",
                    )
                }
            }
        },
    )
}

enum class OptionStats { WEEKS, MONTHS, YEARS, CONTINUOUS }

@Composable
private fun StatsHeaderButton(icon: Int, description: String, onClick: () -> Unit) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .sounGlass(CircleShape, sounGlassColors(), backdrop = LocalSounSurfaceBackdrop.current),
    ) {
        Icon(painterResource(icon), description, Modifier.size(20.dp))
    }
}

@Composable
private fun StatsShowMore(expanded: Boolean, count: Int, onToggle: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        androidx.compose.material3.TextButton(onClick = onToggle) {
            Text(
                if (expanded) stringResource(R.string.soun_stats_show_less)
                else stringResource(R.string.soun_stats_show_all, count),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
