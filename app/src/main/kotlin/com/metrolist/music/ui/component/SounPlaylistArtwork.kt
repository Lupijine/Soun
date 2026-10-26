/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.metrolist.music.R
import com.metrolist.music.ui.theme.SounAccent
import com.metrolist.music.ui.theme.rememberArtworkColors
import com.metrolist.music.ui.theme.sounGlass
import com.metrolist.music.ui.theme.sounGlassColors

@Composable
fun SounPlaylistArtwork(
    @DrawableRes artwork: Int?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    colorSource: String? = null,
) {
    val context = LocalContext.current
    val image = remember(context, artwork, compact) {
        artwork?.let { ImageRequest.Builder(context).data(it).size(if (compact) 160 else 640).build() }
    }
    val palette = rememberArtworkColors(colorSource)
    val fallback = when (artwork) {
        R.drawable.soun_liked_chrome -> Color(0xFFFF7EB8)
        R.drawable.soun_downloads_chrome -> Color(0xFF83CEFF)
        else -> SounAccent
    }
    val primary = animateColorAsState(palette.firstOrNull() ?: fallback, tween(600), label = "collectionGlow")
    val secondary = animateColorAsState(palette.getOrNull(1) ?: fallback, tween(600), label = "collectionReflection")
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val pane = rememberLayerBackdrop()
    val shape = RoundedCornerShape(if (compact) 18.dp else 34.dp)
    Box(modifier.clip(shape), contentAlignment = Alignment.Center) {
        // Capture only this collection's colours, avoiding feedback and the currently playing song's palette.
        Box(Modifier.matchParentSize().layerBackdrop(pane).drawWithCache {
            val glow = Brush.radialGradient(listOf(primary.value.copy(alpha = 0.65f), Color.Transparent),
                center = Offset(size.width * 0.2f, size.height * 0.25f), radius = size.maxDimension.coerceAtLeast(1f))
            val reflection = Brush.radialGradient(listOf(secondary.value.copy(alpha = 0.40f), Color.Transparent),
                center = Offset(size.width, size.height), radius = size.maxDimension.coerceAtLeast(1f))
            onDrawBehind { drawRect(base); drawRect(glow); drawRect(reflection) }
        })
        Box(Modifier.matchParentSize().sounGlass(shape, sounGlassColors(strong = false), elevation = 0f,
            backdrop = pane, fullPanel = !compact))
        if (image != null) {
            AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.matchParentSize().padding(if (compact) 4.dp else 16.dp))
        } else {
            Icon(painterResource(R.drawable.queue_music), null, Modifier.size(if (compact) 24.dp else 72.dp),
                tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}
