/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.ListItemHeight
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.NavigationTitle
import com.metrolist.music.ui.component.YouTubeGridItem
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.ui.component.shimmer.GridItemPlaceHolder
import com.metrolist.music.ui.component.shimmer.ShimmerHost
import com.metrolist.music.ui.component.shimmer.TextPlaceholder
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.ui.theme.sounPanel
import com.metrolist.music.ui.utils.SnapLayoutInfoProvider
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.viewmodels.ChartsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChartsScreen(
    navController: NavController,
    viewModel: ChartsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val chartsPage by viewModel.chartsPage.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (chartsPage == null) {
            viewModel.loadCharts()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.charts)) },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.navigateUp() },
                        onLongClick = { navController.backToMain() },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            if (isLoading) {
                ShimmerHost(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                    ) {
                        TextPlaceholder(
                            height = 36.dp,
                            modifier =
                                Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(0.5f),
                        )
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val horizontalLazyGridItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
                            val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor

                            LazyHorizontalGrid(
                                rows = GridCells.Fixed(4),
                                contentPadding = PaddingValues(start = 4.dp),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(ListItemHeight * 4),
                            ) {
                                items(4) {
                                    Row(
                                        modifier =
                                            Modifier
                                                .width(horizontalLazyGridItemWidth)
                                                .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .size(ListItemHeight - 16.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(MaterialTheme.colorScheme.onSurface),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(
                                            modifier = Modifier.fillMaxHeight(),
                                            verticalArrangement = Arrangement.Center,
                                        ) {
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .height(16.dp)
                                                        .width(120.dp)
                                                        .background(MaterialTheme.colorScheme.onSurface),
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .height(12.dp)
                                                        .width(80.dp)
                                                        .background(MaterialTheme.colorScheme.onSurface),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        TextPlaceholder(
                            height = 36.dp,
                            modifier =
                                Modifier
                                    .padding(vertical = 12.dp, horizontal = 12.dp)
                                    .width(250.dp),
                        )
                        Row {
                            repeat(2) {
                                GridItemPlaceHolder()
                            }
                        }
                    }
                }
            } else if (chartsPage?.sections.isNullOrEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(stringResource(R.string.charts_unavailable))
                    Button(onClick = viewModel::loadCharts) {
                        Text(stringResource(R.string.retry))
                    }
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    contentPadding =
                        LocalPlayerAwareWindowInsets.current
                            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                            .asPaddingValues(),
                ) {
                    chartsPage?.sections?.forEach { section ->
                        val chartItems = section.items
                            .filter { it is SongItem || it is AlbumItem || it is ArtistItem || it is PlaylistItem }
                            .distinctBy { it.id }
                        if (chartItems.isEmpty()) return@forEach
                        val rows = minOf(chartItems.size, 4)
                        val rankedArtists = chartItems.all { it is ArtistItem }
                        item(key = "section_title_${section.title}") {
                            NavigationTitle(
                                title =
                                    when (section.title) {
                                        "Trending" -> stringResource(R.string.trending)
                                        else -> section.title.ifEmpty { stringResource(R.string.charts) }
                                    },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item(key = "section_content_${section.title}") {
                            if (chartItems.all { it is PlaylistItem }) {
                                LazyRow(
                                    contentPadding = WindowInsets.systemBars
                                        .only(WindowInsetsSides.Horizontal)
                                        .asPaddingValues(),
                                ) {
                                    items(chartItems, key = { it.id }) { item ->
                                        YouTubeGridItem(
                                            item = item,
                                            fillMaxWidth = true,
                                            catalogCard = true,
                                            modifier = Modifier
                                                .width(190.dp)
                                                .padding(horizontal = 5.dp)
                                                .sounPanel(RoundedCornerShape(22.dp))
                                                .clickable { navController.navigate("online_playlist/${item.id}") },
                                        )
                                    }
                                }
                            } else {
                                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                    val horizontalLazyGridItemWidthFactor =
                                        if (rankedArtists) {
                                            if (maxWidth >= 640.dp) 0.5f else 1f
                                        } else {
                                            if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
                                        }
                                    val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor

                                    val lazyGridState = rememberLazyGridState()
                                    val snapLayoutInfoProvider =
                                        remember(lazyGridState) {
                                            SnapLayoutInfoProvider(
                                                lazyGridState = lazyGridState,
                                                positionInLayout = { layoutSize, itemSize ->
                                                    (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                                                },
                                            )
                                        }

                                    LazyHorizontalGrid(
                                        state = lazyGridState,
                                        rows = GridCells.Fixed(rows),
                                        flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
                                        contentPadding =
                                            WindowInsets.systemBars
                                                .only(WindowInsetsSides.Horizontal)
                                                .asPaddingValues(),
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .height(ListItemHeight * rows)
                                                .animateItem(),
                                    ) {
                                        itemsIndexed(
                                            items = chartItems,
                                            key = { _, item -> "charts_item_${item.id}" },
                                        ) { index, item ->
                                            Row(
                                                modifier = Modifier
                                                    .width(horizontalLazyGridItemWidth)
                                                    .then(
                                                        if (rankedArtists) Modifier
                                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                                            .sounPanel(RoundedCornerShape(18.dp))
                                                        else Modifier,
                                                    ),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                if (rankedArtists) {
                                                    Text(
                                                        text = "${index + 1}",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(start = 16.dp, end = 8.dp).width(32.dp),
                                                    )
                                                }
                                                YouTubeListItem(
                                                    item = item,
                                                    isActive = item is SongItem && item.id == mediaMetadata?.id,
                                                    isPlaying = isPlaying,
                                                    isSwipeable = false,
                                                    trailingContent = {
                                                        if (item is SongItem) IconButton(
                                                            onClick = {
                                                                menuState.show {
                                                                    YouTubeSongMenu(
                                                                        song = item,
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
                                                    },
                                                    modifier =
                                                        Modifier
                                                            .weight(1f)
                                                            .combinedClickable(
                                                                onClick = {
                                                                    when (item) {
                                                                        is SongItem -> if (item.id == mediaMetadata?.id) {
                                                                            playerConnection.togglePlayPause()
                                                                        } else {
                                                                            playerConnection.playQueue(
                                                                                YouTubeQueue(
                                                                                    endpoint = WatchEndpoint(videoId = item.id),
                                                                                    preloadItem = item.toMediaMetadata(),
                                                                                ),
                                                                            )
                                                                        }
                                                                        is AlbumItem -> navController.navigate("album/${item.id}")
                                                                        is ArtistItem -> navController.navigate("artist/${item.id}")
                                                                        is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                                                        else -> Unit
                                                                    }
                                                                },
                                                                onLongClick = {
                                                                    if (item is SongItem) {
                                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                        menuState.show {
                                                                            YouTubeSongMenu(
                                                                                song = item,
                                                                                onDismiss = menuState::dismiss,
                                                                            )
                                                                        }
                                                                    }
                                                                },
                                                            ),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                }
            }
        }
    }
}
