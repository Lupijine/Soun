/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Composable
fun Modifier.sounAmbientBackground(pureBlack: Boolean = false, artwork: List<Color> = emptyList()): Modifier {
    val scheme = MaterialTheme.colorScheme
    if (pureBlack) return background(Color.Black)
    val dark = scheme.surface.luminance() < 0.5f
    val primary by animateColorAsState(artwork.firstOrNull() ?: scheme.primary, tween(900), label = "ambientPrimary")
    val secondary by animateColorAsState(artwork.getOrNull(1) ?: scheme.tertiary, tween(900), label = "ambientSecondary")
    return background(if (dark) Color(0xFF07070B) else Color(0xFFF4F2F9)).drawBehind {
        if (size.minDimension <= 0f) return@drawBehind
        drawRect(
            Brush.radialGradient(
                listOf(primary.copy(alpha = if (dark) 0.60f else 0.30f), Color.Transparent),
                center = Offset(size.width * 0.1f, size.height * 0.12f),
                radius = size.width * 1.1f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                listOf(secondary.copy(alpha = if (dark) 0.40f else 0.22f), Color.Transparent),
                center = Offset(size.width, size.height * 0.65f),
                radius = size.width,
            ),
        )
    }
}
