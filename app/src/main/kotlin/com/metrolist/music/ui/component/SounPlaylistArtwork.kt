/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.metrolist.music.R
import com.metrolist.music.ui.theme.LocalSounSurfaceBackdrop
import com.metrolist.music.ui.theme.sounGlass
import com.metrolist.music.ui.theme.sounGlassColors

@Composable
fun SounPlaylistArtwork(
    @DrawableRes artwork: Int,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val context = LocalContext.current
    val image = remember(context, artwork, compact) {
        ImageRequest.Builder(context).data(artwork).size(if (compact) 160 else 640).build()
    }
    val accent = if (artwork == R.drawable.soun_liked_chrome) Color(0xFFFF7EB8) else Color(0xFF83CEFF)
    val shape = RoundedCornerShape(if (compact) 18.dp else 34.dp)
    Box(
        modifier.sounGlass(shape, sounGlassColors(strong = false), elevation = if (compact) 0f else 16f,
            backdrop = LocalSounSurfaceBackdrop.current, fullPanel = !compact)
            .drawWithCache {
                val glow = Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(size.width * 0.25f, size.height * 0.3f),
                    radius = size.maxDimension.coerceAtLeast(1f),
                )
                val reflection = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent))
                onDrawBehind { drawRect(glow); drawRect(reflection) }
            }.padding(if (compact) 4.dp else 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Decode to the display size; retain the supplied PNG's original transparency and colours.
        AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    }
}
