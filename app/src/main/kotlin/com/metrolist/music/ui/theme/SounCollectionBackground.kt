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

@Composable
fun SounCollectionBackground(colors: List<Color>, pureBlack: Boolean = false, modifier: Modifier = Modifier, lightField: LayerBackdrop = rememberLayerBackdrop()) {
    val scheme = MaterialTheme.colorScheme
    val primary by animateColorAsState(colors.firstOrNull() ?: scheme.primary, tween(800), label = "collectionLight")
    val secondary by animateColorAsState(colors.getOrNull(1) ?: primary, tween(800), label = "collectionLightSecondary")
    val dark = scheme.surface.luminance() < 0.5f
    val base = if (pureBlack) Color.Black else if (dark) Color(0xFF0C0C12) else Color(0xFFF3F1F7)
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
            onDrawBehind {
                drawRect(glow)
                drawRect(lowerGlow)
                if (!pureBlack) for (ribbon in listOf(upper, lower)) {
                    drawPath(ribbon, beam, alpha = 0.2f, style = Stroke(100.dp.toPx(), cap = StrokeCap.Round))
                    drawPath(ribbon, beam, alpha = 0.35f, style = Stroke(48.dp.toPx(), cap = StrokeCap.Round))
                    drawPath(ribbon, beam, style = Stroke(14.dp.toPx(), cap = StrokeCap.Round))
                    drawPath(ribbon, glint, style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        })
        Box(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp).sounGlass(
            RoundedCornerShape(30.dp),
            sounGlassColors(strong = false).copy(
                surfaceTint = base.copy(alpha = if (pureBlack) 0.85f else 0.16f),
                fillTop = Color.White.copy(alpha = if (dark) 0.04f else 0.14f),
                rimBright = scheme.onSurface.copy(alpha = 0.16f),
            ), elevation = 0f, backdrop = lightField, fullPanel = true,
        ))
    }
}
