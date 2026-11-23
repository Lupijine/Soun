/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import com.metrolist.music.constants.ListItemHeight
import com.metrolist.music.constants.ListThumbnailSize
import com.metrolist.music.constants.ThumbnailCornerRadius
import com.metrolist.music.ui.theme.SounAccent
import com.metrolist.music.ui.theme.sounPanel
import com.metrolist.music.utils.PlaylistFolder
import com.metrolist.music.utils.PlaylistFolderRepository
import com.metrolist.music.utils.PlaylistFolders
import com.metrolist.music.utils.rememberPlaylistFolderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Folder edits outlive the menu or screen that started them (a dismissed sheet cancels its own scope).
private val folderWriteScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

fun PlaylistFolderRepository.launchEdit(block: suspend PlaylistFolderRepository.() -> Unit) {
    folderWriteScope.launch { block() }
}

/** Soun lavender; a deeper shade on light surfaces so the glyph keeps its contrast. */
@Composable
fun sounLavenderInk(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) SounAccent else Color(0xFF6E5BD6)

@Composable
private fun FolderBadge(badgeSize: Dp, iconSize: Dp, shape: Shape) {
    val tint = sounLavenderInk()
    Box(Modifier.size(badgeSize).background(tint.copy(alpha = 0.16f), shape), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.soun_folder), null, Modifier.size(iconSize), tint = tint)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistFolderRow(
    folder: PlaylistFolder,
    playlistCount: Int,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(ListItemHeight)
            .sounPanel(shape)
            .combinedClickable(onClick = onClick, onLongClick = onMenu)
            .padding(start = 14.dp),
    ) {
        FolderBadge(ListThumbnailSize, 24.dp, RoundedCornerShape(ThumbnailCornerRadius))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(folder.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(pluralStringResource(R.plurals.soun_n_folder_playlists, playlistCount, playlistCount),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        IconButton(onClick = onMenu) {
            Icon(painterResource(R.drawable.more_vert), stringResource(R.string.more_options))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistFolderGridTile(
    folder: PlaylistFolder,
    playlistCount: Int,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ThumbnailCornerRadius + 8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onMenu)
            .padding(12.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).sounPanel(RoundedCornerShape(ThumbnailCornerRadius)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.soun_folder), null, Modifier.fillMaxSize(0.36f), tint = sounLavenderInk())
        }
        Spacer(Modifier.height(6.dp))
        Text(folder.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(pluralStringResource(R.plurals.soun_n_folder_playlists, playlistCount, playlistCount),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Glass pill that starts a new folder; sits above the folder rows in the library. */
@Composable
fun NewPlaylistFolderButton(modifier: Modifier = Modifier) {
    val repository = rememberPlaylistFolderRepository()
    var showCreate by remember { mutableStateOf(false) }
    if (showCreate) {
        PlaylistFolderNameDialog(
            title = stringResource(R.string.soun_new_folder),
            onDismiss = { showCreate = false },
            onDone = { name -> repository.launchEdit { create(name) } },
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .sounPanel(CircleShape)
            .clickable { showCreate = true }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Icon(painterResource(R.drawable.soun_folder_plus), null, Modifier.size(18.dp), tint = sounLavenderInk())
        Text(stringResource(R.string.soun_new_folder), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun PlaylistFolderNameDialog(
    title: String,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
    initialName: String = "",
) {
    TextFieldDialog(
        icon = { Icon(painterResource(R.drawable.soun_folder), contentDescription = null) },
        title = { Text(title) },
        placeholder = { Text(stringResource(R.string.soun_folder_name)) },
        initialTextFieldValue = TextFieldValue(initialName, TextRange(initialName.length)),
        isInputValid = { it.isNotBlank() },
        onDismiss = onDismiss,
        onDone = { name -> if (name.isNotBlank()) onDone(name.trim()) },
    )
}

/** Bottom-sheet menu for a folder: rename, or delete (its playlists return to the top level). */
@Composable
fun PlaylistFolderMenu(
    folder: PlaylistFolder,
    playlistCount: Int,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit = {},
) {
    val repository = rememberPlaylistFolderRepository()
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    if (showRename) {
        PlaylistFolderNameDialog(
            title = stringResource(R.string.soun_rename_folder),
            initialName = folder.name,
            onDismiss = { showRename = false },
            onDone = { name ->
                repository.launchEdit { rename(folder.id, name) }
                onDismiss()
            },
        )
    }

    if (showDelete) {
        DefaultDialog(
            onDismiss = { showDelete = false },
            title = { Text(stringResource(R.string.soun_delete_folder)) },
            buttons = {
                TextButton(onClick = { showDelete = false }) { Text(stringResource(android.R.string.cancel)) }
                TextButton(onClick = {
                    showDelete = false
                    repository.launchEdit { delete(folder.id) }
                    onDismiss()
                    onDeleted()
                }) { Text(stringResource(R.string.delete)) }
            },
        ) {
            Text(stringResource(R.string.soun_delete_folder_confirm, folder.name), style = MaterialTheme.typography.bodyLarge)
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FolderBadge(56.dp, 28.dp, RoundedCornerShape(18.dp))
        Column(Modifier.weight(1f)) {
            Text(folder.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(pluralStringResource(R.plurals.soun_n_folder_playlists, playlistCount, playlistCount),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider()
    Spacer(Modifier.height(12.dp))
    Material3MenuGroup(items = listOf(
        Material3MenuItemData(
            title = { Text(stringResource(R.string.soun_rename_folder)) },
            icon = { Icon(painterResource(R.drawable.edit), null) },
            onClick = { showRename = true },
        ),
        Material3MenuItemData(
            title = { Text(stringResource(R.string.soun_delete_folder)) },
            icon = { Icon(painterResource(R.drawable.delete), null) },
            onClick = { showDelete = true },
        ),
    ))
    Spacer(Modifier.height(16.dp))
}

/** Picks the folder for one playlist: an existing folder, a new one, or back to the top level. */
@Composable
fun MoveToFolderDialog(
    playlistId: String,
    onDismiss: () -> Unit,
) {
    val repository = rememberPlaylistFolderRepository()
    val folders by repository.folders.collectAsStateWithLifecycle(initialValue = emptyList())
    val current = remember(folders, playlistId) { PlaylistFolders.folderOf(folders, playlistId) }
    var showCreate by remember { mutableStateOf(false) }

    if (showCreate) {
        PlaylistFolderNameDialog(
            title = stringResource(R.string.soun_new_folder),
            onDismiss = { showCreate = false },
            onDone = { name ->
                repository.launchEdit { create(name, listOf(playlistId)) }
                onDismiss()
            },
        )
        return
    }

    ListDialog(onDismiss = onDismiss) {
        item {
            Text(
                stringResource(R.string.soun_move_to_folder),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 12.dp),
            )
        }
        items(folders, key = { it.id }) { folder ->
            val selected = folder.id == current?.id
            FolderChoice(
                icon = R.drawable.soun_folder,
                title = folder.name,
                subtitle = if (selected) stringResource(R.string.soun_folder_current) else null,
                selected = selected,
                onClick = {
                    if (!selected) repository.launchEdit { move(playlistId, folder.id) }
                    onDismiss()
                },
            )
        }
        item(key = "new_folder") {
            FolderChoice(
                icon = R.drawable.soun_folder_plus,
                title = stringResource(R.string.soun_new_folder_ellipsis),
                onClick = { showCreate = true },
            )
        }
        if (current != null) {
            item(key = "remove_from_folder") {
                FolderChoice(
                    icon = R.drawable.soun_folder_minus,
                    title = stringResource(R.string.soun_remove_from_folder),
                    onClick = {
                        repository.launchEdit { move(playlistId, null) }
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun FolderChoice(
    icon: Int,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    selected: Boolean = false,
) {
    val tint = sounLavenderInk()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) tint.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Icon(painterResource(icon), null, Modifier.size(22.dp), tint = if (selected) tint else MaterialTheme.colorScheme.onSurface)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selected) Icon(painterResource(R.drawable.check), null, Modifier.size(20.dp), tint = tint)
    }
}
