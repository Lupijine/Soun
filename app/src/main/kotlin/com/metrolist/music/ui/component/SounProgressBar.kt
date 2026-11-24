/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import com.metrolist.music.ui.utils.SounHaptics
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp

/**
 * The Now Playing progress bar: a thin capsule with no thumb that thickens under the finger.
 * Tap anywhere to jump there, or drag to scrub; [onValueChangeFinished] seeks when the finger lifts.
 */
@Composable
fun SounProgressBar(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Soun: chapter starts as fractions of the bar (0..1), drawn as small gaps. */
    markers: List<Float> = emptyList(),
) {
    var dragging by remember { mutableStateOf(false) }
    val view = LocalView.current
    val thickness by animateDpAsState(
        targetValue = if (dragging) 12.dp else 6.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 700f),
        label = "progressThickness",
    )
    val stretch by animateFloatAsState(
        targetValue = if (dragging) 1.025f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "progressStretch",
    )
    val span = valueRange.endInclusive - valueRange.start
    val fraction = if (span > 0f) ((value - valueRange.start) / span).coerceIn(0f, 1f) else 0f

    val latestRange by rememberUpdatedState(valueRange)
    val latestChange by rememberUpdatedState(onValueChange)
    val latestFinished by rememberUpdatedState(onValueChangeFinished)
    val latestMarkers by rememberUpdatedState(markers)

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(valueRange), valueRange)
                if (enabled) {
                    setProgress { target ->
                        latestChange(target.coerceIn(latestRange))
                        latestFinished()
                        true
                    }
                } else {
                    disabled()
                }
            }
            .then(
                if (!enabled) Modifier else Modifier.pointerInput(Unit) {
                    fun valueAt(x: Float): Float {
                        val range = latestRange
                        val f = if (size.width > 0) (x / size.width).coerceIn(0f, 1f) else 0f
                        return range.start + f * (range.endInclusive - range.start)
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        dragging = true
                        SounHaptics.touch(view)
                        down.consume()
                        latestChange(valueAt(down.position.x))
                        var lastX = down.position.x
                        try {
                            drag(down.id) { change ->
                                change.consume()
                                val x = change.position.x
                                // A light tick when the finger passes a chapter start.
                                if (size.width > 0 && latestMarkers.any { m ->
                                        val mx = m * size.width
                                        (lastX < mx && x >= mx) || (lastX > mx && x <= mx)
                                    }
                                ) {
                                    SounHaptics.tick(view)
                                }
                                lastX = x
                                latestChange(valueAt(x))
                            }
                        } finally {
                            dragging = false
                            SounHaptics.tick(view)
                            latestFinished()
                        }
                    }
                },
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(thickness)
                .graphicsLayer {
                    scaleX = stretch
                    if (markers.isNotEmpty()) compositingStrategy = CompositingStrategy.Offscreen
                }
                .clip(CircleShape)
                .background(color.copy(alpha = 0.22f))
                .drawWithContent {
                    drawRect(
                        color = color.copy(alpha = if (dragging) 1f else 0.92f),
                        size = Size(size.width * fraction, size.height),
                    )
                    // Chapter starts cut small gaps through the capsule.
                    val gap = 2.dp.toPx()
                    markers.forEach { m ->
                        if (m <= 0.002f || m >= 0.998f) return@forEach
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(size.width * m - gap / 2f, 0f),
                            size = Size(gap, size.height),
                            blendMode = BlendMode.Clear,
                        )
                    }
                },
        )
    }
}
