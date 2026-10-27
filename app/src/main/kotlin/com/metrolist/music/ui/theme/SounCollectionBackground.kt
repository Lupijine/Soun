package com.metrolist.music.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
fun SounCollectionBackground(
    colors: List<Color>,
    pureBlack: Boolean = false,
    modifier: Modifier = Modifier,
    lightField: LayerBackdrop = rememberLayerBackdrop(),
    motionPaused: () -> Boolean = { false },
) {
    // Share the home light field without filtering an animated full-screen glass layer.
    Box(modifier.fillMaxSize().layerBackdrop(lightField).sounAmbientBackground(
        pureBlack = pureBlack && MaterialTheme.colorScheme.surface.luminance() < 0.5f,
        artwork = if (LocalSounArtworkAccent.current) colors else emptyList(),
        motionPaused = motionPaused,
    ))
}
