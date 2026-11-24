package com.metrolist.music.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AlbumGlow(
    colors: List<Color>,
    dark: Boolean,
    modifier: Modifier = Modifier,
    motionPaused: () -> Boolean = { false },
    /** Soun: the beat (0..1); the glows swell and brighten with it. */
    beat: () -> Float = { 0f },
) {
    val artworkAccent = LocalSounArtworkAccent.current
    val scheme = MaterialTheme.colorScheme
    val primary by animateColorAsState(if (artworkAccent) colors.firstOrNull() ?: Color.Transparent else scheme.primary, tween(900), label = "albumGlowPrimary")
    val secondary by animateColorAsState(if (artworkAccent) colors.getOrNull(1) ?: Color.Transparent else scheme.tertiary, tween(900), label = "albumGlowSecondary")
    val drift = rememberAmbientDrift()
    val motion = remember { AmbientPhase() }
    Box(modifier.drawWithCache {
        val firstGlow = Brush.radialGradient(
            listOf(primary.copy(alpha = primary.alpha * if (dark) 0.38f else 0.16f), Color.Transparent),
            center = Offset(size.width * 0.15f, size.height * 0.22f),
            radius = (size.maxDimension * 0.75f).coerceAtLeast(1f),
        )
        val secondGlow = Brush.radialGradient(
            listOf(secondary.copy(alpha = secondary.alpha * if (dark) 0.28f else 0.12f), Color.Transparent),
            center = Offset(size.width * 0.95f, size.height * 0.72f),
            radius = (size.maxDimension * 0.65f).coerceAtLeast(1f),
        )
        onDrawBehind {
            val phase = motion.advance(if (motionPaused()) null else drift.value) * (2 * Math.PI).toFloat()
            val dx = sin(phase) * size.width * 0.24f
            val dy = (cos(phase) - 1f) * size.height * 0.09f
            val pulse = beat().coerceIn(0f, 1f)
            val glowSize = androidx.compose.ui.geometry.Size(size.width * 1.6f, size.height * 1.4f)
            val glowOrigin = Offset(-size.width * 0.3f, -size.height * 0.2f)
            translate(dx, dy) {
                // The main glow swells a little on each beat and lights up the glass above it.
                scale(1f + pulse * 0.10f, pivot = Offset(size.width * 0.15f, size.height * 0.22f)) {
                    drawRect(firstGlow, topLeft = glowOrigin, size = glowSize)
                    if (pulse > 0.01f) drawRect(firstGlow, topLeft = glowOrigin, size = glowSize, alpha = pulse * 0.9f)
                }
            }
            translate(-dx, -dy) {
                scale(1f + pulse * 0.06f, pivot = Offset(size.width * 0.95f, size.height * 0.72f)) {
                    drawRect(secondGlow, topLeft = glowOrigin, size = glowSize)
                    if (pulse > 0.01f) drawRect(secondGlow, topLeft = glowOrigin, size = glowSize, alpha = pulse * 0.6f)
                }
            }
        }
    })
}
