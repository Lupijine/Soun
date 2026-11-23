/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.PinnedLibraryPlaylistsKey
import com.metrolist.music.constants.PlaylistSortType
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.PlaylistFolderMenu
import com.metrolist.music.ui.component.PlaylistListItem
import com.metrolist.music.ui.component.sounCollectionRow
import com.metrolist.music.ui.menu.PlaylistMenu
import com.metrolist.music.utils.PlaylistFolder
import com.metrolist.music.utils.rememberPlaylistFolderRepository
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.safeDataStoreEdit
import kotlinx.coroutines.launch

/** Playlists filed in one library folder, in the same rows as the library itself. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistFolderScreen(folderId: String) {
    val nav = LocalNavController.current
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menus = LocalMenuState.current
    val scope = rememberCoroutineScope()
    val repository = rememberPlaylistFolderRepository()
    val folders by repository.folders.collectAsStateWithLifecycle(initialValue = null)
    val folder: PlaylistFolder? = folders?.firstOrNull { it.id == folderId }
    val playlists by remember(database) { database.playlists(PlaylistSortType.CREATE_DATE, true) }
        .collectAsStateWithLifecycle(initialValue = null)
    val pinned by rememberPreference(PinnedLibraryPlaylistsKey, emptySet<String>())
    val members = remember(folder, playlists, pinned) {
        val ids = folder?.playlistIds?.toSet().orEmpty()
        orderPinnedPlaylists(playlists.orEmpty().filter { it.id in ids }, pinned)
    }

    // Deleting the folder (here or from another screen) closes it; guard so we only pop once.
    var left by remember { mutableStateOf(false) }
    LaunchedEffect(folders, folder) {
        if (folders != null && folder == null && !left) {
            left = true
            nav.navigateUp()
        }
    }

    val showFolderMenu: () -> Unit = {
        folder?.let { current ->
            menus.show {
                PlaylistFolderMenu(folder = current, playlistCount = members.size, onDismiss = menus::dismiss)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(
                LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Horizontal)
                    .asPaddingValues(),
            ),
    ) {
        TopAppBar(
            title = { Text(folder?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            navigationIcon = {
                IconButton(onClick = { nav.navigateUp() }) {
                    Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.back))
                }
            },
            actions = {
                IconButton(onClick = showFolderMenu, enabled = folder != null) {
                    Icon(painterResource(R.drawable.more_vert), stringResource(R.string.more_options))
                }
            },
        )
        LazyColumn(
            contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (folder != null && playlists != null && members.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.soun_folder_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            items(members, key = { it.id }) { playlist ->
                val isPinned = playlist.id in pinned
                val showMenu: () -> Unit = {
                    menus.show {
                        PlaylistMenu(
                            playlist = playlist,
                            coroutineScope = scope,
                            onDismiss = menus::dismiss,
                            isLibraryPinned = isPinned,
                            onToggleLibraryPin = {
                                scope.launch {
                                    context.safeDataStoreEdit { prefs ->
                                        val current = prefs[PinnedLibraryPlaylistsKey] ?: emptySet()
                                        prefs[PinnedLibraryPlaylistsKey] =
                                            if (playlist.id in current) current - playlist.id else current + playlist.id
                                    }
                                }
                            },
                        )
                    }
                }
                FolderPlaylistRow(
                    playlist = playlist,
                    isPinned = isPinned,
                    onClick = { nav.navigate("local_playlist/${playlist.id}") },
                    onMenu = showMenu,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderPlaylistRow(
    playlist: Playlist,
    isPinned: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaylistListItem(
        playlist = playlist,
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .sounCollectionRow(active = false, selected = isPinned)
            .combinedClickable(onClick = onClick, onLongClick = onMenu),
        trailingContent = {
            if (isPinned) {
                Icon(
                    painterResource(R.drawable.soun_pin),
                    stringResource(R.string.soun_playlist_pinned),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onMenu) {
                Icon(painterResource(R.drawable.more_vert), stringResource(R.string.more_options))
            }
        },
    )
}
