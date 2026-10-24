/**
 * Prism (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/** Prism's default accent, used when the user hasn't picked a theme color. */
val PrismAccent = Color(0xFFA99BFF)

/**
 * Liquid Glass surface colors for one theme. A strong surface (nav bar, mini player)
 * is more opaque and has a brighter rim than a regular one (cards, chips).
 */
data class PrismGlassColors(
    val fillTop: Color,
    val fillBottom: Color,
    val rimBright: Color,
    val rimDim: Color,
    val shadow: Color,
    val selectedPill: Color,
)

@Composable
@ReadOnlyComposable
fun prismGlassColors(strong: Boolean = true, pureBlack: Boolean = false): PrismGlassColors {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (dark) {
        // Glass needs something behind it to read as glass; on pure black we
        // tint it a little more so the edge still shows.
        val boost = if (pureBlack) 0.04f else 0f
        PrismGlassColors(
            fillTop = Color.White.copy(alpha = (if (strong) 0.14f else 0.09f) + boost),
            fillBottom = Color.White.copy(alpha = (if (strong) 0.06f else 0.035f) + boost),
            rimBright = Color.White.copy(alpha = if (strong) 0.62f else 0.45f),
            rimDim = Color.White.copy(alpha = 0.06f),
            shadow = Color.Black.copy(alpha = 0.55f),
            selectedPill = Color.White.copy(alpha = 0.16f),
        )
    } else {
        PrismGlassColors(
            fillTop = Color.White.copy(alpha = if (strong) 0.82f else 0.66f),
            fillBottom = Color.White.copy(alpha = if (strong) 0.58f else 0.42f),
            rimBright = Color.White,
            rimDim = Color(0xFF1E143C).copy(alpha = 0.07f),
            shadow = Color(0xFF281E5A).copy(alpha = 0.28f),
            selectedPill = Color.White.copy(alpha = 0.92f),
        )
    }
}

/**
 * Draws a Liquid Glass surface: a soft vertical sheen, a light-catching rim that is
 * brightest at the top-left and bottom-right corners, and a floating shadow.
 * Content drawn after this modifier is clipped to [shape].
 */
fun Modifier.prismGlass(
    shape: Shape,
    colors: PrismGlassColors,
    elevation: Float = 18f,
): Modifier =
    this
        .shadow(
            elevation = elevation.dp,
            shape = shape,
            clip = false,
            ambientColor = colors.shadow,
            spotColor = colors.shadow,
        )
        .clip(shape)
        .background(Brush.verticalGradient(listOf(colors.fillTop, colors.fillBottom)), shape)
        .border(
            BorderStroke(
                width = 1.dp,
                brush =
                    Brush.linearGradient(
                        0f to colors.rimBright,
                        0.28f to colors.rimDim,
                        0.5f to Color.Transparent,
                        0.74f to colors.rimDim,
                        1f to colors.rimBright.copy(alpha = colors.rimBright.alpha * 0.7f),
                        start = Offset.Zero,
                        end = Offset.Infinite,
                    ),
            ),
            shape,
        )
