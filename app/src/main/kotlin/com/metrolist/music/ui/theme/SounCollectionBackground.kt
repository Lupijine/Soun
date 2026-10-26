package com.metrolist.music.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SounCollectionBackground(colors: List<Color>, pureBlack: Boolean = false, modifier: Modifier = Modifier, lightField: LayerBackdrop = rememberLayerBackdrop()) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    if (pureBlack && dark) {
        Box(modifier.fillMaxSize().layerBackdrop(lightField).background(Color.Black))
        return
    }
    val drift = rememberAmbientDrift()
    val artworkAccent = LocalSounArtworkAccent.current
    val targetPrimary = if (artworkAccent) colors.firstOrNull() ?: SounAccent else scheme.primary
    val targetSecondary = if (artworkAccent) colors.getOrNull(1) ?: targetPrimary else scheme.tertiary
    val primary by animateColorAsState(targetPrimary, tween(800), label = "collectionLight")
    val secondary by animateColorAsState(targetSecondary, tween(800), label = "collectionLightSecondary")
    val base = if (dark) Color(0xFF0C0C12) else Color(0xFFF3F1F7)
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(lightField).background(base).drawWithCache {
            val glow = Brush.radialGradient(listOf(primary.copy(alpha = if (dark) 0.54f else 0.26f), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.18f), radius = size.width * 1.1f)
            val lowerGlow = Brush.radialGradient(listOf(secondary.copy(alpha = if (dark) 0.32f else 0.18f), Color.Transparent),
                center = Offset(size.width * 0.9f, size.height * 0.72f), radius = size.width)
            val upper = Path().apply {
                moveTo(-size.width * 0.2f, size.height * 0.38f)
                cubicTo(size.width * 0.75f, -size.height * 0.08f, size.width * 1.2f, size.height * 0.25f, size.width * 0.72f, size.height * 0.57f)
            }
            val lower = Path().apply {
                moveTo(size.width * 1.2f, size.height * 0.54f)
                cubicTo(size.width * 0.2f, size.height * 0.34f, -size.width * 0.25f, size.height * 0.75f, size.width * 0.3f, size.height * 1.12f)
            }
            val beam = Brush.linearGradient(listOf(Color.Transparent, primary.copy(alpha = 0.20f), secondary.copy(alpha = 0.16f), Color.Transparent),
                start = Offset.Zero, end = Offset(size.width, size.height))
            val glint = Brush.linearGradient(listOf(Color.Transparent, scheme.onSurface.copy(alpha = 0.14f), Color.Transparent))
            val ribbons = listOf(upper, lower)
            val outerStroke = Stroke(100.dp.toPx(), cap = StrokeCap.Round)
            val softStroke = Stroke(48.dp.toPx(), cap = StrokeCap.Round)
            val lightStroke = Stroke(14.dp.toPx(), cap = StrokeCap.Round)
            val edgeStroke = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round)
            onDrawBehind {
                val phase = drift.value * (2 * Math.PI).toFloat()
                val dx = sin(phase) * size.width * 0.20f
                val dy = (cos(phase) - 1f) * size.height * 0.07f
                translate(dx, dy) { drawRect(glow, topLeft = Offset(-size.width * 0.3f, -size.height * 0.2f), size = androidx.compose.ui.geometry.Size(size.width * 1.6f, size.height * 1.4f)) }
                translate(-dx, -dy) { drawRect(lowerGlow, topLeft = Offset(-size.width * 0.3f, -size.height * 0.2f), size = androidx.compose.ui.geometry.Size(size.width * 1.6f, size.height * 1.4f)) }
                translate(dx * 0.5f, dy * 0.5f) {
                for (ribbon in ribbons) {
                    drawPath(ribbon, beam, alpha = 0.2f, style = outerStroke)
                    drawPath(ribbon, beam, alpha = 0.35f, style = softStroke)
                    drawPath(ribbon, beam, style = lightStroke)
                    drawPath(ribbon, glint, style = edgeStroke)
                }
                }
            }
        })
        Box(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp).sounGlass(
            RoundedCornerShape(30.dp),
            sounGlassColors(strong = false).copy(
                surfaceTint = base.copy(alpha = 0.16f),
                fillTop = Color.White.copy(alpha = if (dark) 0.04f else 0.14f),
                rimBright = scheme.onSurface.copy(alpha = 0.16f),
            ), elevation = 0f, backdrop = lightField, fullPanel = true,
        ))
    }
}
