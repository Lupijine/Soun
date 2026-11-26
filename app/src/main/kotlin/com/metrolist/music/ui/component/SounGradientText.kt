/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import kotlinx.coroutines.delay
import java.util.Collections

/** Instagram's warm-to-cool gradient. */
private val InstagramGradient =
    listOf(
        Color(0xFFFEDA75),
        Color(0xFFFA7E1E),
        Color(0xFFD62976),
        Color(0xFF962FBF),
        Color(0xFF4F5BD5),
    )

/** Texts already typed out in this session, so they don't type again on every visit. */
private val typedTexts: MutableSet<String> = Collections.synchronizedSet(HashSet())

/**
 * Text painted with Instagram's gradient that types itself out the first time it is shown.
 * The layout is fixed from the start (untyped letters are only hidden), so nothing below
 * moves while it types. With [flowing] the colours drift slowly; the drift is drawn without
 * recomposing.
 */
@Composable
fun SounGradientText(
    text: String,
    typingKey: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    flowing: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
) {
    val key = "$typingKey|$text"
    var typed by remember(key) { mutableIntStateOf(if (key in typedTexts) text.length else 0) }
    LaunchedEffect(key) {
        if (typed < text.length) {
            val step = (2_400L / text.length.coerceAtLeast(1)).coerceIn(14L, 38L)
            while (typed < text.length) {
                delay(step)
                typed++
            }
        }
        typedTexts += key
    }

    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    // The pale yellow start does not read on light backgrounds.
    val colors = if (light) InstagramGradient.drop(1) else InstagramGradient
    val drift: State<Float>? =
        if (flowing) {
            rememberInfiniteTransition(label = "gradientDrift").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(7_000, easing = LinearEasing), RepeatMode.Reverse),
                label = "drift",
            )
        } else {
            null
        }

    Text(
        text =
            buildAnnotatedString {
                append(text.take(typed))
                if (typed < text.length) {
                    // A soft leading edge instead of a cursor: the next letters fade in.
                    val edge = text.substring(typed, (typed + 2).coerceAtMost(text.length))
                    edge.forEachIndexed { index, char ->
                        withStyle(SpanStyle(color = Color.White.copy(alpha = if (index == 0) 0.55f else 0.2f))) { append(char) }
                    }
                    withStyle(SpanStyle(color = Color.Transparent)) { append(text.substring((typed + 2).coerceAtMost(text.length))) }
                }
            },
        style = style.copy(color = Color.White),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithCache {
                    val width = size.width.coerceAtLeast(1f)
                    onDrawWithContent {
                        drawContent()
                        val shift = (drift?.value ?: 0f) * width
                        drawRect(
                            brush =
                                Brush.linearGradient(
                                    colors = colors,
                                    start = Offset(-shift, 0f),
                                    end = Offset(width - shift, size.height * 0.6f),
                                    tileMode = TileMode.Mirror,
                                ),
                            blendMode = BlendMode.SrcIn,
                        )
                    }
                },
    )
}
