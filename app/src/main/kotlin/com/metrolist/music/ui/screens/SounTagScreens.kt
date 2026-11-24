/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.component.SounPlaylistCover
import com.metrolist.music.ui.component.sounLavenderInk
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.ui.theme.SounAccent
import com.metrolist.music.ui.theme.SounGlassDialog
import com.metrolist.music.utils.SongTag
import com.metrolist.music.utils.SounTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val OnAccent = Color(0xFF17121F)

fun tagRoute(name: String): String = "tag/${Uri.encode(name)}"

/* The tag editor ---------------------------------------------------------------------------- */

/**
 * Puts tags on [songIds]: every tag as a chip (lit when all of the songs carry it), tap to add or
 * remove; type a new one below. [beforeTagging] saves songs that are not in the library yet.
 */
@Composable
fun SounTagsDialog(
    songIds: List<String>,
    onDismiss: () -> Unit,
    beforeTagging: suspend () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tags by remember { SounTags.tags(context) }.collectAsStateWithLifecycle()
    var newTag by remember { mutableStateOf("") }
    val suggestions = listOf(
        stringResource(R.string.soun_tag_suggest_workout),
        stringResource(R.string.soun_tag_suggest_focus),
        stringResource(R.string.soun_tag_suggest_rain),
        stringResource(R.string.soun_tag_suggest_night_drive),
        stringResource(R.string.soun_tag_suggest_chill),
    )

    fun tagWith(name: String) {
        scope.launch {
            withContext(Dispatchers.IO) { beforeTagging() }
            SounTags.toggle(context, name, songIds)
        }
    }

    SounGlassDialog(onDismiss = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.widthIn(max = 420.dp).padding(22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(SounAccent.copy(alpha = 0.16f)),
                ) {
                    Icon(painterResource(R.drawable.soun_tag), contentDescription = null, tint = sounLavenderInk(), modifier = Modifier.size(24.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.soun_tags), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Text(
                        stringResource(R.string.soun_tags_dialog_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val existing = tags.orEmpty()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                existing.forEach { tag ->
                    TagChip(tag.name, selected = songIds.isNotEmpty() && songIds.all { it in tag.songIds }) { tagWith(tag.name) }
                }
                // Ideas while there are few tags of your own.
                suggestions
                    .filter { idea -> existing.none { it.name.equals(idea, ignoreCase = true) } }
                    .take((6 - existing.size).coerceAtLeast(0))
                    .forEach { idea -> TagChip(idea, selected = false, suggestion = true) { tagWith(idea) } }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it.take(SounTags.MAX_NAME) },
                    placeholder = { Text(stringResource(R.string.soun_tag_new)) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        SounTags.cleanName(newTag)?.let { tagWith(it) }
                        newTag = ""
                    }),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(SounAccent)
                            .clickable(onClickLabel = stringResource(R.string.soun_tag_add)) {
                                SounTags.cleanName(newTag)?.let { tagWith(it) }
                                newTag = ""
                            },
                ) {
                    Icon(painterResource(R.drawable.add), stringResource(R.string.soun_tag_add), tint = OnAccent)
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f), RoundedCornerShape(50))
                        .clickable(onClick = onDismiss),
            ) {
                Text(stringResource(R.string.soun_sleep_done), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TagChip(
    name: String,
    selected: Boolean,
    suggestion: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            Modifier
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    when {
                        selected -> SounAccent
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
                    },
                )
                .then(
                    if (suggestion) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f), RoundedCornerShape(50))
                    } else {
                        Modifier
                    },
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Icon(
            painterResource(if (selected) R.drawable.check else if (suggestion) R.drawable.add else R.drawable.soun_tag),
            contentDescription = null,
            tint = if (selected) OnAccent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            name,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = if (selected) OnAccent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/* All tags ---------------------------------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SounTagsScreen() {
    val nav = LocalNavController.current
    val context = LocalContext.current
    val database = LocalDatabase.current
    val tags by remember { SounTags.tags(context) }.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .padding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal).asPaddingValues()),
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.soun_tags)) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            navigationIcon = {
                IconButton(onClick = { nav.navigateUp() }) {
                    Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.back))
                }
            },
        )
        val list = tags.orEmpty().filter { it.songIds.isNotEmpty() }.sortedByDescending { it.songIds.size }
        if (tags != null && list.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(32.dp),
            ) {
                Icon(painterResource(R.drawable.soun_tag), contentDescription = null, tint = SounAccent, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.soun_tags_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        ) {
            items(list, key = { it.name }) { tag ->
                val thumbnails by produceState(emptyList<String>(), tag.songIds.take(2)) {
                    value = withContext(Dispatchers.IO) {
                        database.getSongsByIds(tag.songIds.take(2)).mapNotNull { it.thumbnailUrl }
                    }
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { nav.navigate(tagRoute(tag.name)) },
                ) {
                    SounPlaylistCover(
                        name = tag.name,
                        thumbnails = thumbnails,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    )
                    Text(
                        pluralStringResource(R.plurals.soun_card_songs, tag.songIds.size, tag.songIds.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

/* One tag ----------------------------------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SounTagScreen(name: String) {
    val nav = LocalNavController.current
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val tags by remember { SounTags.tags(context) }.collectAsStateWithLifecycle()
    val tag: SongTag? = tags?.firstOrNull { it.name == name }
    val songs by produceState(emptyList<Song>(), tag?.songIds) {
        val ids = tag?.songIds.orEmpty()
        value = withContext(Dispatchers.IO) {
            val byId = database.getSongsByIds(ids).associateBy { it.id }
            ids.mapNotNull { byId[it] }
        }
    }
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .padding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal).asPaddingValues()),
    ) {
        TopAppBar(
            title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            navigationIcon = {
                IconButton(onClick = { nav.navigateUp() }) {
                    Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.back))
                }
            },
            actions = {
                IconButton(onClick = {
                    SounTags.delete(context, name)
                    nav.navigateUp()
                }) {
                    Icon(painterResource(R.drawable.delete), stringResource(R.string.soun_tag_delete))
                }
            },
        )
        LazyColumn(
            contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    SounPlaylistCover(
                        name = name,
                        thumbnails = songs.take(2).mapNotNull { it.thumbnailUrl },
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.size(200.dp),
                    )
                    Text(
                        pluralStringResource(R.plurals.soun_card_songs, songs.size, songs.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        TagAction(stringResource(R.string.play), R.drawable.play, primary = true, modifier = Modifier.weight(1f)) {
                            if (songs.isNotEmpty()) playerConnection.playQueue(ListQueue(title = name, items = songs.map { it.toMediaItem() }))
                        }
                        TagAction(stringResource(R.string.shuffle), R.drawable.shuffle, primary = false, modifier = Modifier.weight(1f)) {
                            if (songs.isNotEmpty()) playerConnection.playQueue(ListQueue(title = name, items = songs.shuffled().map { it.toMediaItem() }))
                        }
                    }
                }
            }
            items(songs, key = { it.id }) { song ->
                SongListItem(
                    song = song,
                    isActive = mediaMetadata?.id == song.id,
                    isPlaying = isPlaying,
                    isSwipeable = false,
                    trailingContent = {
                        IconButton(onClick = { SounTags.removeSong(context, name, song.id) }) {
                            Icon(painterResource(R.drawable.close), stringResource(R.string.soun_tag_remove_song))
                        }
                    },
                    modifier =
                        Modifier
                            .padding(horizontal = 8.dp)
                            .combinedClickable(
                                onClick = {
                                    playerConnection.playQueue(
                                        ListQueue(title = name, items = songs.map { it.toMediaItem() }, startIndex = songs.indexOf(song)),
                                    )
                                },
                                onLongClick = { menuState.show { SongMenu(originalSong = song, onDismiss = menuState::dismiss) } },
                            )
                            .animateItem(),
                )
            }
        }
    }
}

@Composable
private fun TagAction(
    text: String,
    icon: Int,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(50))
                .background(if (primary) SounAccent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = if (primary) OnAccent else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
        Text(text, color = if (primary) OnAccent else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    }
}
