package com.metrolist.music.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import com.kyant.backdrop.Backdrop
import com.metrolist.music.R
import com.metrolist.music.lyrics.LyricsLoadStatus
import com.metrolist.music.ui.theme.LocalSounBackdrop
import com.metrolist.music.ui.theme.sounGlass
import com.metrolist.music.ui.theme.sounGlassColors
import com.metrolist.music.ui.theme.sounSurface

@Composable
internal fun LyricsSourceLabel(provider: String, synced: Boolean, wordSynced: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.lyrics_from_provider, provider), style = MaterialTheme.typography.labelMedium)
        Text(
            stringResource(when {
                wordSynced -> R.string.soun_lyrics_words
                synced -> R.string.soun_lyrics_lines
                else -> R.string.soun_lyrics_plain
            }),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun LyricsLoadPanel(status: LyricsLoadStatus, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp).sounSurface().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (status == LyricsLoadStatus.LOADING) CircularProgressIndicator()
            Text(
                stringResource(when (status) {
                    LyricsLoadStatus.LOADING -> R.string.soun_lyrics_loading
                    LyricsLoadStatus.OFFLINE -> R.string.soun_lyrics_offline
                    LyricsLoadStatus.ERROR -> R.string.soun_lyrics_error
                    else -> R.string.lyrics_not_found
                }),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (status != LyricsLoadStatus.LOADING) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}

@Composable
internal fun LyricsFollowButton(backdrop: Backdrop?, onClick: () -> Unit) {
    // Soun: a small round glass button that sits at the side instead of covering the lyrics.
    CompositionLocalProvider(LocalSounBackdrop provides backdrop) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp).sounGlass(CircleShape, sounGlassColors()),
            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        ) {
            Icon(painterResource(R.drawable.soun_locate), stringResource(R.string.soun_lyrics_follow), Modifier.size(22.dp))
        }
    }
}

/**
 * Tap and long-press for a lyric line that still fire while the list is scrolling.
 *
 * The lyrics scroll themselves almost constantly. With a normal clickable, a tap
 * during that animation is first used to stop the scroll and never becomes a click,
 * so tapping a line to jump there only worked sometimes (or on the second tap).
 * Listening with requireUnconsumed = false lets the tap through; a real drag still
 * cancels it, so scrolling the lyrics by hand doesn't seek.
 */
@Composable
internal fun Modifier.lyricsTap(onClick: () -> Unit, onLongClick: () -> Unit): Modifier {
    val latestClick by rememberUpdatedState(onClick)
    val latestLongClick by rememberUpdatedState(onLongClick)
    // Playback recomposes every frame; callback keys would cancel a finger already down.
    return semantics(mergeDescendants = true) {
        onClick { latestClick(); true }
        onLongClick { latestLongClick(); true }
    }.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var timedOut = true
            val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                waitForUpOrCancellation().also { timedOut = false }
            }
            when {
                up != null -> {
                    up.consume()
                    latestClick()
                }
                timedOut -> {
                    latestLongClick()
                    waitForUpOrCancellation()?.consume()
                }
            }
        }
    }
}
