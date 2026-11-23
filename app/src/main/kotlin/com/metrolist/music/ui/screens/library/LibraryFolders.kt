/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.library

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.NewPlaylistFolderButton
import com.metrolist.music.ui.component.PlaylistFolderMenu
import com.metrolist.music.ui.component.PlaylistFolderRow
import com.metrolist.music.utils.PlaylistFolder
import com.metrolist.music.utils.PlaylistFolders

/** Playlists that are not filed in any folder; folder members only show inside their folder. */
internal fun topLevelPlaylists(playlists: List<Playlist>, folders: List<PlaylistFolder>): List<Playlist> {
    if (folders.isEmpty()) return playlists
    val filed = PlaylistFolders.filedPlaylistIds(folders)
    return playlists.filterNot { it.id in filed }
}

/** Number of a folder's playlists that still exist in the library. */
internal fun PlaylistFolder.liveCount(existingIds: Set<String>): Int = playlistIds.count { it in existingIds }

/** The "New folder" action and the folder rows that lead the library's playlist list. */
internal fun LazyListScope.libraryFolderItems(
    folders: List<PlaylistFolder>,
    playlistIds: Set<String>,
    onOpen: (PlaylistFolder) -> Unit,
) {
    item(key = "soun_folders_header") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 20.dp, bottom = 8.dp),
        ) {
            Text(
                text = if (folders.isEmpty()) "" else stringResource(R.string.soun_folders),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            NewPlaylistFolderButton()
        }
    }
    items(folders, key = { "soun_folder_${it.id}" }) { folder ->
        val menus = LocalMenuState.current
        val count = folder.liveCount(playlistIds)
        PlaylistFolderRow(
            folder = folder,
            playlistCount = count,
            onClick = { onOpen(folder) },
            onMenu = { menus.show { PlaylistFolderMenu(folder = folder, playlistCount = count, onDismiss = menus::dismiss) } },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).animateItem(),
        )
    }
}
