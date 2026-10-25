/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.*
import com.metrolist.music.R
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.*
import com.metrolist.music.ui.theme.sounPanel
import com.metrolist.music.viewmodels.ChartsViewModel
import com.metrolist.music.viewmodels.HomeViewModel

@Composable
fun ExploreScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    chartsViewModel: ChartsViewModel = hiltViewModel(),
) {
    val nav = LocalNavController.current
    val player = LocalPlayerConnection.current
    val together = LocalListenTogetherManager.current
    val isGuest = together?.let { it.isInRoom && !it.isHost } ?: false
    val page by viewModel.explorePage.collectAsStateWithLifecycle()
    val loading by viewModel.isLoading.collectAsStateWithLifecycle()
    val charts by chartsViewModel.chartsPage.collectAsStateWithLifecycle()
    val playing by player?.isEffectivelyPlaying?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }
    val metadata by player?.mediaMetadata?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }
    LaunchedEffect(Unit) { viewModel.loadHomeData(); if (charts == null) chartsViewModel.loadCharts() }
    LazyColumn(contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(), modifier = Modifier.fillMaxSize()) {
        item {
            SounPageTitle(stringResource(R.string.soun_explore))
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    Triple(R.string.soun_new_releases, R.drawable.trending_up, "new_release"),
                    Triple(R.string.charts, R.drawable.stats, "charts_screen"),
                    Triple(R.string.mood_and_genres, R.drawable.grid_view, "mood_and_genres"),
                ).forEach { (title, icon, route) ->
                    Column(Modifier.weight(1f).heightIn(min = 100.dp).sounPanel(RoundedCornerShape(22.dp))
                        .clip(RoundedCornerShape(22.dp)).clickable { nav.navigate(route) }.padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween) {
                        Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Text(stringResource(title), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        val albums = page?.newReleaseAlbums.orEmpty()
        if (albums.isNotEmpty()) {
            item { NavigationTitle(stringResource(R.string.soun_new_albums), onClick = { nav.navigate("new_release") }) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                    items(albums.distinctBy { it.id }, key = { it.id }) { album ->
                        YouTubeGridItem(album, roundedArtwork = true, modifier = Modifier.clickable { nav.navigate("album/${album.id}") })
                    }
                }
            }
        }
        val moods = page?.moodAndGenres.orEmpty().distinctBy { it.endpoint.browseId to it.title }
        if (moods.isNotEmpty()) {
            item { NavigationTitle(stringResource(R.string.mood_and_genres), onClick = { nav.navigate("mood_and_genres") }) }
            items(moods.chunked(2), key = { it.first().title }) { pair ->
                Row(Modifier.padding(horizontal = 20.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { mood ->
                        val tint = Color(mood.stripeColor.toInt())
                        Box(Modifier.weight(1f).heightIn(min = 64.dp).sounPanel(RoundedCornerShape(20.dp))
                            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.24f), tint.copy(alpha = 0.05f))), RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp)).clickable {
                                nav.navigate("youtube_browse/${mood.endpoint.browseId}?params=${android.net.Uri.encode(mood.endpoint.params)}")
                            }.padding(16.dp)) {
                            Text(mood.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        val topSongs = charts?.sections.orEmpty().flatMap { it.items }.filterIsInstance<SongItem>().distinctBy { it.id }.take(4)
        if (topSongs.isNotEmpty()) {
            item { NavigationTitle(stringResource(R.string.soun_top_songs), onClick = { nav.navigate("charts_screen") }) }
            item {
                Column(Modifier.padding(horizontal = 20.dp).sounPanel().padding(vertical = 6.dp)) {
                    topSongs.forEachIndexed { index, song ->
                        Row {
                            Text("${index + 1}", color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(32.dp).padding(top = 20.dp, start = 14.dp))
                            YouTubeListItem(song, isActive = song.id == metadata?.id, isPlaying = playing,
                                modifier = Modifier.weight(1f).clickable {
                                    if (!isGuest) player?.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
                                })
                        }
                    }
                }
            }
        }
        if (loading && albums.isEmpty()) item {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(24.dp))
        }
        if (!loading && page == null) item {
            Column(Modifier.padding(24.dp)) {
                Text(stringResource(R.string.soun_explore_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { viewModel.refresh(); chartsViewModel.loadCharts() }) { Text(stringResource(R.string.retry)) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
