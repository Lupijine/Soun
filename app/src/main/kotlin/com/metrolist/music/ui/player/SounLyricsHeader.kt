/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import com.metrolist.music.ui.utils.SounHaptics
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kyant.backdrop.Backdrop
import com.metrolist.music.R
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.ui.theme.sounGlass
import com.metrolist.music.ui.theme.sounGlassColors

/**
 * Compact song header shown at the top of Now Playing while the lyrics are open, so the
 * lyrics get the middle of the screen instead of sharing it with a large title block.
 */
private const val SWIPE_THRESHOLD = 0.28f

@Composable
internal fun SounLyricsHeader(
    mediaMetadata: MediaMetadata,
    isFavorite: Boolean,
    showArtwork: Boolean,
    cropArtwork: Boolean,
    textColor: Color,
    backdrop: Backdrop?,
    onTitleClick: () -> Unit,
    onToggleLike: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    onSwipeNext: (() -> Unit)? = null,
    onSwipePrevious: (() -> Unit)? = null,
) {
    // Swipe the song left or right to change it, like the mini player: the title follows the
    // finger, slides out past the threshold, and the next song slides in from the other side.
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val swipeOffset = remember { Animatable(0f) }
    val latestNext by rememberUpdatedState(onSwipeNext)
    val latestPrevious by rememberUpdatedState(onSwipePrevious)
    var swipeWidth by remember { mutableFloatStateOf(1f) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Tapping the song (cover or title) closes the lyrics and returns to the artwork view.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .onSizeChanged { swipeWidth = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) {
                    var passed = false
                    var dragX = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            passed = false
                            dragX = swipeOffset.value
                        },
                        onHorizontalDrag = { change, delta ->
                            val next = dragX + delta
                            val allowed = (next < 0 && latestNext != null) || (next > 0 && latestPrevious != null)
                            if (allowed) {
                                change.consume()
                                dragX = next
                                scope.launch { swipeOffset.snapTo(next) }
                                val beyond = kotlin.math.abs(next) > swipeWidth * SWIPE_THRESHOLD
                                if (beyond != passed) {
                                    passed = beyond
                                    if (beyond) SounHaptics.tick(view)
                                }
                            }
                        },
                        onDragEnd = {
                            scope.launch {
                                val x = swipeOffset.value
                                val action = when {
                                    x < -swipeWidth * SWIPE_THRESHOLD -> latestNext
                                    x > swipeWidth * SWIPE_THRESHOLD -> latestPrevious
                                    else -> null
                                }
                                if (action == null) {
                                    swipeOffset.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 500f))
                                } else {
                                    val direction = if (x < 0) -1f else 1f
                                    swipeOffset.animateTo(direction * swipeWidth, tween(160))
                                    action()
                                    swipeOffset.snapTo(-direction * swipeWidth * 0.5f)
                                    swipeOffset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 400f))
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch { swipeOffset.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 500f)) }
                        },
                    )
                }
                .graphicsLayer {
                    translationX = swipeOffset.value
                    alpha = 1f - (kotlin.math.abs(swipeOffset.value) / swipeWidth).coerceIn(0f, 0.85f)
                }
                .clip(RoundedCornerShape(14.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = stringResource(R.string.close),
                    onClick = onTitleClick,
                ),
        ) {
            val artworkModifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
            if (showArtwork && mediaMetadata.thumbnailUrl != null) {
                AsyncImage(
                    model = mediaMetadata.thumbnailUrl,
                    contentDescription = null,
                    contentScale = if (cropArtwork) ContentScale.Crop else ContentScale.Fit,
                    modifier = artworkModifier,
                )
            } else {
                Box(artworkModifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.music_note),
                        contentDescription = null,
                        tint = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mediaMetadata.title,
                    color = textColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val artists = mediaMetadata.artists.map { it.name }.filter { it.isNotBlank() }
                if (artists.isNotEmpty()) {
                    Text(
                        text = artists.joinToString(),
                        color = textColor.copy(alpha = 0.72f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        IconButton(
            onClick = {
                SounHaptics.confirm(view)
                onToggleLike()
            },
            modifier = Modifier.size(44.dp).sounGlass(CircleShape, sounGlassColors(), backdrop = backdrop),
        ) {
            Icon(
                painter = painterResource(if (isFavorite) R.drawable.favorite else R.drawable.favorite_border),
                contentDescription = stringResource(R.string.like),
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else textColor,
                modifier = Modifier.size(22.dp),
            )
        }

        IconButton(
            onClick = onMore,
            modifier = Modifier.size(44.dp).sounGlass(CircleShape, sounGlassColors(), backdrop = backdrop),
        ) {
            Icon(
                painter = painterResource(R.drawable.more_horiz),
                contentDescription = stringResource(R.string.more_options),
                tint = textColor,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
