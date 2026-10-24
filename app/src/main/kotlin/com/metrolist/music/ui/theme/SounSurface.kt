package com.metrolist.music.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Shared solid surface for content and modal windows that cannot sample the screen backdrop. */
@Composable
fun Modifier.sounSurface(shape: Shape = RoundedCornerShape(24.dp)): Modifier {
    val colors = MaterialTheme.colorScheme
    return clip(shape)
        .background(Brush.verticalGradient(listOf(colors.surfaceContainerHigh, colors.surfaceContainer)), shape)
        .border(0.5.dp, colors.outlineVariant.copy(alpha = 0.5f), shape)
}
