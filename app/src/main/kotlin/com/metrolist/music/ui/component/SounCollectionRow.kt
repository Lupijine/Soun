package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.sounCollectionRow(active: Boolean, selected: Boolean): Modifier {
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val shape = RoundedCornerShape(22.dp)
    val tint = if (active || selected) colors.primary else colors.surfaceContainerHigh
    // Translucent frames share the ambient light field; no per-song backdrop capture or blur.
    return clip(shape)
        .background(Brush.linearGradient(listOf(
            tint.copy(alpha = if (selected) 0.48f else if (active) 0.32f else 0.42f),
            colors.surface.copy(alpha = if (dark) 0.18f else 0.38f),
        )))
        .border(0.75.dp, Brush.linearGradient(listOf(
            (if (dark) Color.White else colors.outline).copy(alpha = if (active || selected) 0.40f else 0.20f),
            colors.outline.copy(alpha = 0.06f),
            colors.primary.copy(alpha = if (active || selected) 0.32f else 0.12f),
        )), shape)
}

@Composable
fun Modifier.sounCatalogCard(): Modifier {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    return clip(shape)
        .background(Brush.verticalGradient(listOf(
            colors.primary.copy(alpha = 0.12f),
            colors.surfaceContainerHigh.copy(alpha = 0.22f),
            colors.surface.copy(alpha = 0.26f),
        )))
        .border(0.75.dp, Color.White.copy(alpha = 0.14f), shape)
}
