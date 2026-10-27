/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.library

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.*
import com.metrolist.music.R
import com.metrolist.music.constants.*
import com.metrolist.music.db.entities.*
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.ui.component.*
import com.metrolist.music.ui.menu.*
import com.metrolist.music.ui.theme.sounPanel
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.safeDataStoreEdit
import com.metrolist.music.ui.component.sounCollectionRow
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.metrolist.music.viewmodels.LibraryMixViewModel

@Composable
fun LibraryScreen(viewModel: LibraryMixViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val nav = LocalNavController.current
    val database = LocalDatabase.current
    val player = LocalPlayerConnection.current
    val together = LocalListenTogetherManager.current
    val isGuest = together?.let { it.isInRoom && !it.isHost } ?: false
    val menus = LocalMenuState.current
    val scope = rememberCoroutineScope()
    var filter by rememberEnumPreference(ChipSortTypeKey, LibraryFilter.PLAYLISTS)
    var createPlaylist by rememberSaveable { mutableStateOf(false) }
    val likedColorSource by viewModel.likedColorSource.collectAsStateWithLifecycle()
    val downloadedColorSource by viewModel.downloadedColorSource.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val pinnedPlaylists by rememberPreference(PinnedLibraryPlaylistsKey, emptySet<String>())
    val orderedPlaylists = remember(playlists, pinnedPlaylists) { orderPinnedPlaylists(playlists, pinnedPlaylists) }
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val liked by database.likedSongsCount().collectAsStateWithLifecycle(0)
    val downloaded by database.downloadedSongs(SongSortType.CREATE_DATE, true).collectAsStateWithLifecycle(emptyList())
    val playing by player?.isEffectivelyPlaying?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }
    val metadata by player?.mediaMetadata?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }
    val selected = filter.takeIf { it in listOf(LibraryFilter.PLAYLISTS, LibraryFilter.SONGS, LibraryFilter.ALBUMS, LibraryFilter.ARTISTS) } ?: LibraryFilter.PLAYLISTS
    val content: List<LocalItem> = when (selected) {
        LibraryFilter.SONGS -> songs
        LibraryFilter.ALBUMS -> albums
        LibraryFilter.ARTISTS -> artists
        else -> orderedPlaylists
    }
    if (createPlaylist) CreatePlaylistDialog(
        onDismiss = { createPlaylist = false },
        onPlaylistCreated = { id -> createPlaylist = false; nav.navigate("local_playlist/$id") },
    )
    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            SounPageTitle(stringResource(R.string.filter_library)) {
                IconButton(onClick = { createPlaylist = true }, modifier = Modifier.sounPanel(CircleShape)) {
                    Icon(painterResource(R.drawable.add), stringResource(R.string.create_playlist))
                }
            }
            SounLibraryTabs(selected) { filter = it }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SounShortcut(stringResource(R.string.liked_songs), pluralStringResource(R.plurals.n_song, liked, liked),
                        R.drawable.favorite, Modifier.weight(1f), prominent = true, artwork = R.drawable.soun_liked_chrome, colorSource = likedColorSource) { nav.navigate("auto_playlist/liked") }
                    SounShortcut(stringResource(R.string.soun_downloads), pluralStringResource(R.plurals.n_song, downloaded.size, downloaded.size),
                        R.drawable.download, Modifier.weight(1f), artwork = R.drawable.soun_downloads_chrome, colorSource = downloadedColorSource) { nav.navigate("auto_playlist/downloaded") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SounShortcut(stringResource(R.string.soun_on_device), stringResource(R.string.soun_device_audio),
                        R.drawable.offline, Modifier.weight(1f)) { nav.navigate("library/device") }
                    SounShortcut(stringResource(R.string.history), stringResource(R.string.soun_recent_listening),
                        R.drawable.history, Modifier.weight(1f)) { nav.navigate("history") }
                }
            }
        }
        if (content.isEmpty()) item {
            Text(stringResource(R.string.soun_library_empty), color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp))
        }
        itemsIndexed(content, key = { _, it -> "${it::class.simpleName}_${it.id}" }) { index, entry ->
            val rowShape = RoundedCornerShape(
                topStart = if (index == 0) 24.dp else 0.dp, topEnd = if (index == 0) 24.dp else 0.dp,
                bottomStart = if (index == content.lastIndex) 24.dp else 0.dp, bottomEnd = if (index == content.lastIndex) 24.dp else 0.dp,
            )
            val rowFill = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
            val separator = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
            val isPinned = entry is Playlist && entry.id in pinnedPlaylists
            val showEntryMenu: () -> Unit = {
                menus.show {
                    when (entry) {
                        is Playlist -> PlaylistMenu(playlist = entry, coroutineScope = scope, onDismiss = menus::dismiss,
                            isLibraryPinned = isPinned, onToggleLibraryPin = {
                                scope.launch {
                                    context.safeDataStoreEdit { prefs ->
                                        val current = prefs[PinnedLibraryPlaylistsKey] ?: emptySet()
                                        prefs[PinnedLibraryPlaylistsKey] = if (entry.id in current) current - entry.id else current + entry.id
                                    }
                                }
                            })
                        is Album -> AlbumMenu(originalAlbum = entry, onDismiss = menus::dismiss)
                        is Artist -> ArtistMenu(originalArtist = entry, coroutineScope = scope, onDismiss = menus::dismiss)
                        is Song -> SongMenu(originalSong = entry, onDismiss = menus::dismiss)
                    }
                }
            }
            val rowModifier = if (entry is Playlist) {
                Modifier.padding(horizontal = 20.dp, vertical = 4.dp).sounCollectionRow(active = false, selected = isPinned)
            } else {
                Modifier.padding(horizontal = 20.dp).clip(rowShape).background(rowFill, rowShape).drawBehind {
                    if (index > 0) drawLine(separator, Offset(88.dp.toPx(), 0f), Offset(size.width - 16.dp.toPx(), 0f), 0.5.dp.toPx())
                }
            }
            val interactiveRow = rowModifier.combinedClickable(
                    onClick = {
                        when (entry) {
                            is Playlist -> nav.navigate("local_playlist/${entry.id}")
                            is Album -> nav.navigate("album/${entry.id}")
                            is Artist -> nav.navigate("artist/${entry.id}")
                            is Song -> if (!isGuest) {
                                if (metadata?.id == entry.id) player?.togglePlayPause()
                                else player?.playQueue(ListQueue(
                                    title = entry.title, items = content.filterIsInstance<Song>().map { it.toMediaItem() },
                                    startIndex = content.filterIsInstance<Song>().indexOfFirst { it.id == entry.id },
                                ))
                            }
                        }
                    },
                    onLongClick = showEntryMenu,
                )
            when (entry) {
                is Playlist -> PlaylistListItem(playlist = entry, modifier = interactiveRow, trailingContent = {
                    if (isPinned) Icon(painterResource(R.drawable.soun_pin), stringResource(R.string.soun_playlist_pinned), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = showEntryMenu) { Icon(painterResource(R.drawable.more_vert), stringResource(R.string.more_options)) }
                })
                is Album -> AlbumListItem(album = entry, modifier = interactiveRow)
                is Artist -> ArtistListItem(artist = entry, modifier = interactiveRow)
                is Song -> SongListItem(song = entry, isActive = metadata?.id == entry.id, isPlaying = playing,
                    isSwipeable = false, modifier = interactiveRow)
            }
        }
        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { nav.navigate("library/manage") }) { Text(stringResource(R.string.soun_manage_library)) }
                TextButton(onClick = { nav.navigate("library/podcasts") }) { Text(stringResource(R.string.filter_podcasts)) }
            }
        }
    }
}

@Composable
private fun SounLibraryTabs(selected: LibraryFilter, onSelect: (LibraryFilter) -> Unit) {
    val tabs = listOf(
        LibraryFilter.PLAYLISTS to R.string.filter_playlists,
        LibraryFilter.SONGS to R.string.filter_songs,
        LibraryFilter.ALBUMS to R.string.filter_albums,
        LibraryFilter.ARTISTS to R.string.filter_artists,
    )
    Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth().sounPanel().padding(4.dp)) {
        tabs.forEach { (filter, label) ->
            TextButton(onClick = { onSelect(filter) }, modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 2.dp),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (selected == filter) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
                        else androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )) {
                Text(stringResource(label), style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

internal fun orderPinnedPlaylists(playlists: List<Playlist>, pinned: Set<String>): List<Playlist> =
    playlists.sortedBy { it.id !in pinned }
