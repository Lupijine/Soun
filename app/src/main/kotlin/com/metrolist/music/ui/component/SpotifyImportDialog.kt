/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import android.content.ClipboardManager
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.spotify.SpotifyImportError
import com.metrolist.music.spotify.SpotifyPlaylistParser
import com.metrolist.music.ui.theme.SounGlassDialog
import com.metrolist.music.viewmodels.SpotifyImportState
import com.metrolist.music.viewmodels.SpotifyImportViewModel
import com.metrolist.music.viewmodels.SpotifyTruncation

/**
 * Imports a public Spotify playlist: paste its link, each track is matched on YouTube Music,
 * and the matches are saved, in Spotify's order, as a new local playlist.
 */
@Composable
fun SpotifyImportDialog(
    onDismiss: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    viewModel: SpotifyImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var link by rememberSaveable { mutableStateOf("") }
    val fallbackName = stringResource(R.string.soun_spotify_default_name)
    val busy = state is SpotifyImportState.Loading || state is SpotifyImportState.Matching || state is SpotifyImportState.Saving
    val close = {
        viewModel.reset()
        onDismiss()
    }
    val start = { if (link.isNotBlank()) viewModel.start(link, fallbackName) }

    SounGlassDialog(onDismiss = { if (!busy) close() }) {
        Column(
            modifier = Modifier.padding(22.dp).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ImportHeader()
            when (val current = state) {
                SpotifyImportState.Idle, is SpotifyImportState.Failed -> LinkInput(
                    link = link,
                    onLinkChange = { link = it },
                    error = (current as? SpotifyImportState.Failed)?.error,
                    onStart = start,
                    onCancel = close,
                )
                SpotifyImportState.Loading -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
                    StatusText(stringResource(R.string.soun_spotify_loading))
                    CancelRow(viewModel::cancel)
                }
                is SpotifyImportState.Matching -> {
                    PlaylistSummary(current.name ?: fallbackName, current.coverUrl,
                        pluralStringResource(R.plurals.n_song, current.total, current.total))
                    LinearProgressIndicator(
                        progress = { if (current.total == 0) 0f else current.done.toFloat() / current.total },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    )
                    StatusText(stringResource(R.string.soun_spotify_matching, current.done, current.total))
                    current.truncation?.let { TruncationNote(it) }
                    CancelRow(viewModel::cancel)
                }
                is SpotifyImportState.Saving -> {
                    PlaylistSummary(current.name ?: fallbackName, current.coverUrl, null)
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
                    StatusText(stringResource(R.string.soun_spotify_saving))
                }
                is SpotifyImportState.Done -> ImportSummary(
                    done = current,
                    onClose = close,
                    onOpen = {
                        viewModel.reset()
                        onOpenPlaylist(current.playlistId)
                    },
                )
            }
        }
    }
}

@Composable
private fun ImportHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
        ) {
            Icon(
                painter = painterResource(R.drawable.soun_import),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            stringResource(R.string.soun_spotify_import),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun LinkInput(
    link: String,
    onLinkChange: (String) -> Unit,
    error: SpotifyImportError?,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    Text(
        stringResource(R.string.soun_spotify_import_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
    )
    // Like TextFieldDialog: swallow double taps, which crash the TextClassifier inside dialogs.
    Box(Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures(onDoubleTap = {}) }) {
        OutlinedTextField(
            value = link,
            onValueChange = onLinkChange,
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            placeholder = { Text(stringResource(R.string.soun_spotify_link_placeholder), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(painterResource(R.drawable.link), null, Modifier.size(20.dp)) },
            trailingIcon = {
                if (link.isEmpty()) {
                    TextButton(onClick = { clipboardText(context)?.let(onLinkChange) }) {
                        Text(stringResource(R.string.soun_spotify_paste))
                    }
                } else {
                    IconButton(onClick = { onLinkChange("") }) {
                        Icon(painterResource(R.drawable.close), stringResource(R.string.soun_spotify_clear), Modifier.size(20.dp))
                    }
                }
            },
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onStart() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (error != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.error), null, Modifier.padding(top = 1.dp).size(18.dp), tint = MaterialTheme.colorScheme.error)
            Text(stringResource(error.message()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) }
        Button(onClick = onStart, enabled = link.isNotBlank(), shape = RoundedCornerShape(50)) {
            Text(
                stringResource(if (error != null && error != SpotifyImportError.INVALID_LINK) R.string.soun_spotify_try_again else R.string.soun_spotify_import_action),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ImportSummary(
    done: SpotifyImportState.Done,
    onClose: () -> Unit,
    onOpen: () -> Unit,
) {
    PlaylistSummary(done.name, done.coverUrl, pluralStringResource(R.plurals.n_song, done.added, done.added))
    Text(
        pluralStringResource(R.plurals.soun_spotify_added, done.total, done.added, done.total),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    done.truncation?.let { TruncationNote(it) }
    if (done.missing.isNotEmpty()) {
        Text(
            stringResource(R.string.soun_spotify_not_found, done.missing.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
        val divider = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        ) {
            itemsIndexed(done.missing) { index, track ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 14.dp), 0.5.dp, divider)
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(track.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (track.artists.isNotEmpty()) Text(track.artists.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onClose) { Text(stringResource(R.string.soun_spotify_done)) }
        Button(onClick = onOpen, shape = RoundedCornerShape(50)) {
            Text(stringResource(R.string.soun_spotify_open_playlist), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PlaylistSummary(name: String, coverUrl: String?, subtitle: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        val shape = RoundedCornerShape(12.dp)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(56.dp).clip(shape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        ) {
            Icon(painterResource(R.drawable.music_note), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (coverUrl != null) {
                AsyncImage(model = coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun TruncationNote(truncation: SpotifyTruncation) {
    val text = if (truncation.total != null) {
        stringResource(R.string.soun_spotify_truncated_known, truncation.shown, truncation.total)
    } else {
        stringResource(R.string.soun_spotify_truncated_limit, SpotifyPlaylistParser.EMBED_TRACK_LIMIT)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(R.drawable.info), null, Modifier.padding(top = 1.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
}

@Composable
private fun CancelRow(onCancel: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) }
    }
}

@StringRes
private fun SpotifyImportError.message(): Int = when (this) {
    SpotifyImportError.INVALID_LINK -> R.string.soun_spotify_error_invalid_link
    SpotifyImportError.NETWORK -> R.string.soun_spotify_error_network
    SpotifyImportError.NOT_FOUND -> R.string.soun_spotify_error_not_found
    SpotifyImportError.EMPTY_OR_PRIVATE -> R.string.soun_spotify_error_empty
    SpotifyImportError.UNREADABLE -> R.string.soun_spotify_error_unreadable
    SpotifyImportError.SEARCH_FAILED -> R.string.soun_spotify_error_search
    SpotifyImportError.NO_MATCHES -> R.string.soun_spotify_error_no_matches
}

private fun clipboardText(context: Context): String? =
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
        ?.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
