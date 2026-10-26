/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.metrolist.music.R
import com.metrolist.music.ui.theme.SounAccent
import com.metrolist.music.ui.theme.rememberArtworkColors
import com.metrolist.music.ui.theme.LocalSounArtworkAccent

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
    val artworkAccent = LocalSounArtworkAccent.current
    val palette = rememberArtworkColors(colorSource, enabled = artworkAccent)
    val fallback = when (artwork) {
        R.drawable.soun_liked_chrome -> Color(0xFFFF7EB8)
        R.drawable.soun_downloads_chrome -> Color(0xFF83CEFF)
        else -> SounAccent
    }
    val primary by animateColorAsState(if (artworkAccent) palette.firstOrNull() ?: fallback else MaterialTheme.colorScheme.primary, tween(600), label = "collectionGlow")
    val secondary by animateColorAsState(if (artworkAccent) palette.getOrNull(1) ?: fallback else MaterialTheme.colorScheme.tertiary, tween(600), label = "collectionReflection")
    val light = MaterialTheme.colorScheme.onSurface
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().drawWithCache {
            val glow = Brush.radialGradient(listOf(primary.copy(alpha = 0.48f), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.5f), radius = size.minDimension * 0.5f)
            val reflection = Brush.radialGradient(listOf(secondary.copy(alpha = 0.4f), Color.Transparent),
                center = Offset(size.width * 0.62f, size.height * 0.55f), radius = size.minDimension * 0.32f)
            onDrawBehind {
                drawRect(glow)
                drawRect(reflection)
                if (!compact) {
                    drawArc(Brush.linearGradient(listOf(Color.Transparent, light.copy(alpha = 0.3f), secondary.copy(alpha = 0.2f), Color.Transparent)),
                        12f, 150f, false, Offset(size.width * 0.08f, size.height * 0.62f),
                        Size(size.width * 0.84f, size.height * 0.24f), style = Stroke(1.dp.toPx()))
                }
            }
        })
        if (image != null) {
            AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.matchParentSize().padding(if (compact) 2.dp else 10.dp))
        } else {
            Icon(painterResource(R.drawable.queue_music), null, Modifier.size(if (compact) 24.dp else 64.dp), tint = light)
        }
    }
}
