package com.metrolist.music.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Static once settled: artwork changes animate without a perpetual background animation. */
@Composable
fun AlbumGlow(colors: List<Color>, dark: Boolean, modifier: Modifier = Modifier) {
    val primary by animateColorAsState(colors.firstOrNull() ?: Color.Transparent, tween(900), label = "albumGlowPrimary")
    val secondary by animateColorAsState(colors.getOrNull(1) ?: Color.Transparent, tween(900), label = "albumGlowSecondary")
    Canvas(modifier) {
        if (size.minDimension <= 0f) return@Canvas
        drawRect(Brush.radialGradient(
            listOf(primary.copy(alpha = primary.alpha * if (dark) 0.38f else 0.16f), Color.Transparent),
            center = Offset(size.width * 0.15f, size.height * 0.22f),
            radius = size.maxDimension * 0.75f,
        ))
        drawRect(Brush.radialGradient(
            listOf(secondary.copy(alpha = secondary.alpha * if (dark) 0.28f else 0.12f), Color.Transparent),
            center = Offset(size.width * 0.95f, size.height * 0.72f),
            radius = size.maxDimension * 0.65f,
        ))
    }
}
