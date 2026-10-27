/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.metrolist.music.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.outlineVariant
    val volumeLabel = stringResource(R.string.soun_volume)
    val capsule = RoundedCornerShape(22.dp)
    Row(
        modifier = modifier
            .clip(capsule)
            .background(Brush.verticalGradient(listOf(ink.copy(alpha = 0.12f), ink.copy(alpha = 0.035f))))
            .border(0.5.dp, outline.copy(alpha = 0.5f), capsule)
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(if (value <= 0f) R.drawable.volume_off else R.drawable.volume_up),
            contentDescription = null, modifier = Modifier.size(20.dp), tint = ink.copy(alpha = 0.8f))
        Slider(
            value = value.coerceIn(0f, 1f), onValueChange = onValueChange,
            enabled = enabled, valueRange = 0f..1f, onValueChangeFinished = onValueChangeFinished,
            modifier = Modifier.weight(1f).semantics { contentDescription = volumeLabel },
            thumb = {
                Box(Modifier.size(20.dp)
                    .background(Brush.verticalGradient(listOf(ink.copy(alpha = 0.95f), accentColor.copy(alpha = 0.8f))), CircleShape)
                    .border(1.dp, ink.copy(alpha = 0.55f), CircleShape))
            },
            track = { state ->
                val trackShape = RoundedCornerShape(50)
                Box(Modifier.fillMaxWidth().height(10.dp).clip(trackShape)
                    .background(ink.copy(alpha = 0.13f)).border(0.5.dp, ink.copy(alpha = 0.12f), trackShape)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(state.value.coerceIn(0f, 1f))
                        .background(Brush.verticalGradient(listOf(ink.copy(alpha = 0.85f), accentColor.copy(alpha = 0.65f))), trackShape))
                }
            },
        )
    }
}
