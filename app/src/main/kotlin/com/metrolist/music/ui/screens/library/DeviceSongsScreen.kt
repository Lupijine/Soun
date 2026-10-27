package com.metrolist.music.ui.screens.library

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextOverflow
import com.metrolist.music.LocalListenTogetherManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.menu.DeviceAudioMenu
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSongsScreen() {
    val context = LocalContext.current
    val nav = LocalNavController.current
    val player = LocalPlayerConnection.current
    val menu = LocalMenuState.current
    val together = LocalListenTogetherManager.current
    val isGuest = together?.let { it.isInRoom && !it.isHost } == true
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    fun hasAccess() = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    var allowed by remember { mutableStateOf(hasAccess()) }
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var songs by remember { mutableStateOf(emptyList<MediaMetadata>()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = hasAccess(); refresh++ }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!allowed && !permissionRequested) {
            permissionRequested = true
            launcher.launch(permission)
        }
    }
    LaunchedEffect(allowed, refresh) {
        loading = allowed
        failed = false
        if (allowed) {
            try {
                songs = withContext(Dispatchers.IO) {
                    readDeviceAudio(context.contentResolver)
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { failed = true }
            finally { loading = false }
        }
    }
    Column(Modifier.fillMaxSize().padding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom).asPaddingValues())) {
        TopAppBar(
            title = { Text(stringResource(R.string.soun_on_device)) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            navigationIcon = { IconButton(onClick = { nav.navigateUp() }) { Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.back)) } },
        )
        when {
            !allowed || failed -> Column(Modifier.padding(24.dp)) {
                Text(stringResource(if (failed) R.string.soun_device_error else R.string.soun_device_permission))
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) }) {
                    Text(stringResource(R.string.soun_device_settings))
                }
            }
            loading -> CircularProgressIndicator(Modifier.padding(24.dp))
            songs.isEmpty() -> Text(stringResource(R.string.soun_device_empty), Modifier.padding(24.dp))
            else -> LazyColumn {
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    ListItem(
                        headlineContent = { Text(song.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(song.artists.joinToString { it.name }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = {
                            if (song.thumbnailUrl == null) Icon(painterResource(R.drawable.music_note), null, Modifier.size(48.dp))
                            else AsyncImage(song.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop,
                                error = painterResource(R.drawable.music_note), modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
                        },
                        trailingContent = {
                            IconButton(onClick = { menu.show { DeviceAudioMenu(song, onDismiss = menu::dismiss) } }) {
                                Icon(painterResource(R.drawable.more_vert), stringResource(R.string.more_options))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(enabled = player != null && !isGuest) {
                            player?.playQueue(ListQueue(title = context.getString(R.string.soun_on_device), items = songs.map { it.toMediaItem() }, startIndex = index))
                        },
                    )
                }
            }
        }
    }
}

internal fun readDeviceAudio(resolver: android.content.ContentResolver): List<MediaMetadata> {
    val audio = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val columns = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM_ID)
    val result = resolver.query(audio, columns, "${MediaStore.Audio.Media.DURATION} > 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")
        ?: throw java.io.IOException("Audio provider returned no result")
    return buildList {
        result.use { cursor ->
            while (cursor.moveToNext()) add(MediaMetadata(
                id = ContentUris.withAppendedId(audio, cursor.getLong(0)).toString(),
                title = cursor.getString(1).orEmpty(),
                artists = listOf(MediaMetadata.Artist(null, cursor.getString(2).orEmpty())),
                duration = (cursor.getLong(3) / 1000).toInt(),
                thumbnailUrl = if (!cursor.isNull(4) && cursor.getLong(4) > 0) {
                    ContentUris.withAppendedId("content://media/external/audio/albumart".toUri(), cursor.getLong(4)).toString()
                } else null,
            ))
        }
    }
}
