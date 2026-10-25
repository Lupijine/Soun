/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(if (value <= 0f) R.drawable.volume_off else R.drawable.volume_up),
            contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = value.coerceIn(0f, 1f), onValueChange = onValueChange,
            enabled = enabled, valueRange = 0f..1f, onValueChangeFinished = onValueChangeFinished,
            modifier = Modifier.weight(1f),
            thumb = { Box(Modifier.size(14.dp).background(if (enabled) accentColor else MaterialTheme.colorScheme.outline, CircleShape)) },
            track = { state -> SliderDefaults.Track(state, modifier = Modifier.height(6.dp),
                colors = SliderDefaults.colors(activeTrackColor = accentColor,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                thumbTrackGapSize = 2.dp, drawStopIndicator = null) },
        )
    }
}
