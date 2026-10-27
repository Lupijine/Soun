package com.metrolist.music.ui.menu

import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.room.withTransaction
import coil3.compose.AsyncImage
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.db.entities.Event
import com.metrolist.music.db.entities.PlaylistSong
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.ui.component.Material3MenuGroup
import com.metrolist.music.ui.component.Material3MenuItemData
import com.metrolist.music.ui.component.NewAction
import com.metrolist.music.ui.component.NewActionGrid
import com.metrolist.music.ui.component.VolumeSlider
import com.metrolist.music.utils.makeTimeString

@Composable
fun DeviceAudioMenu(
    metadata: MediaMetadata,
    onDismiss: () -> Unit,
    showVolume: Boolean = false,
    showCloseButton: Boolean = false,
    event: Event? = null,
    playlistSong: PlaylistSong? = null,
) {
    val player = LocalPlayerConnection.current ?: return
    val database = LocalDatabase.current
    val nav = LocalNavController.current
    val context = LocalContext.current
    val together = LocalListenTogetherManager.current
    val guest = together?.let { it.isInRoom && !it.isHost } == true
    val volume by player.service.playerVolume.collectAsState()
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val systemEq = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    AddToPlaylistDialog(isVisible = showAdd, allowSyncing = false, localOnly = true,
        onGetSong = { database.withTransaction { insert(metadata) }; listOf(metadata.id) },
        onGetSongIds = { listOf(metadata.id) }, onDismiss = { showAdd = false })
    if (showDetails) AlertDialog(
        onDismissRequest = { showDetails = false },
        title = { Text(metadata.title) },
        text = { Text(listOfNotNull(metadata.artists.joinToString { it.name }.takeIf { it.isNotBlank() },
            makeTimeString(metadata.duration * 1000L).takeIf { metadata.duration > 0 }).joinToString(" · ")) },
        confirmButton = { TextButton(onClick = { showDetails = false }) { Text(stringResource(R.string.dismiss)) } },
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center) {
            if (metadata.thumbnailUrl != null) AsyncImage(metadata.thumbnailUrl, contentDescription = null,
                contentScale = ContentScale.Crop, error = painterResource(R.drawable.music_note), modifier = Modifier.fillMaxSize())
            else Icon(painterResource(R.drawable.music_note), null, Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(metadata.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(metadata.artists.joinToString { it.name }, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (showCloseButton) IconButton(onClick = onDismiss) { Icon(painterResource(R.drawable.close), stringResource(R.string.dismiss)) }
    }
    if (showVolume) VolumeSlider(volume, { player.service.playerVolume.value = it }, modifier = Modifier.fillMaxWidth(),
        accentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
    LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)) {
        item {
            NewActionGrid(compact = true, columns = if (guest) 1 else 3, actions = buildList {
                add(NewAction(icon = { Icon(painterResource(R.drawable.playlist_add), null) },
                    text = stringResource(R.string.add_to_playlist), onClick = { showAdd = true }))
                if (!guest) {
                    add(NewAction(icon = { Icon(painterResource(R.drawable.playlist_play), null) },
                        text = stringResource(R.string.play_next), onClick = { player.playNext(metadata.toMediaItem()); onDismiss() }))
                    add(NewAction(icon = { Icon(painterResource(R.drawable.queue_music), null) },
                        text = stringResource(R.string.add_to_queue), onClick = { player.addToQueue(metadata.toMediaItem()); onDismiss() }))
                }
            })
        }
        item { Spacer(Modifier.height(12.dp)) }
        item {
            Material3MenuGroup(compact = true, items = buildList {
                add(Material3MenuItemData(title = { Text(stringResource(R.string.details)) },
                    icon = { Icon(painterResource(R.drawable.info), null) }, onClick = { showDetails = true }))
                if (event != null) add(Material3MenuItemData(title = { Text(stringResource(R.string.remove_from_history)) },
                    icon = { Icon(painterResource(R.drawable.delete), null) }, onClick = { database.query { delete(event) }; onDismiss() }))
                if (playlistSong != null) add(Material3MenuItemData(title = { Text(stringResource(R.string.remove_from_playlist)) },
                    icon = { Icon(painterResource(R.drawable.delete), null) }, onClick = {
                        database.transaction { move(playlistSong.map.playlistId, playlistSong.map.position, Int.MAX_VALUE); delete(playlistSong.map.copy(position = Int.MAX_VALUE)) }
                        onDismiss()
                    }))
                add(Material3MenuItemData(title = { Text(stringResource(R.string.equalizer)) },
                    icon = { Icon(painterResource(R.drawable.equalizer), null) }, onClick = { nav.navigate("equalizer"); onDismiss() }))
                if (showVolume) add(Material3MenuItemData(title = { Text(stringResource(R.string.system_equalizer)) },
                    icon = { Icon(painterResource(R.drawable.graphic_eq), null) }, onClick = {
                        val session = player.player.audioSessionId
                        if (session != C.AUDIO_SESSION_ID_UNSET && session > 0) {
                            val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, session)
                                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                            }
                            if (intent.resolveActivity(context.packageManager) != null) systemEq.launch(intent)
                        }
                        onDismiss()
                    }))
            })
        }
    }
}
