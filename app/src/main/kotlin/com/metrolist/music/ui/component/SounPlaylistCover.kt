/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.R
import com.metrolist.music.ui.theme.rememberArtworkColors
import kotlin.math.absoluteValue

/** Soun's own tints for playlists with no songs (or covers) to take colours from. */
private val CoverTints =
    listOf(
        Color(0xFFA99BFF) to Color(0xFF3B2A7A),
        Color(0xFFFF8FB8) to Color(0xFF6A1E46),
        Color(0xFF7FD8C8) to Color(0xFF14524C),
        Color(0xFFFFC27A) to Color(0xFF6B3B10),
        Color(0xFF8FC8FF) to Color(0xFF1C3D6E),
    )

/**
 * A glass cover for a playlist that has no cover of its own: the colours of its songs' artwork
 * (or a Soun tint picked from its name) under a sheet of glass, with the playlist's name when
 * there is room for it. List-sized covers show only the colours and a note.
 */
@Composable
fun SounPlaylistCover(
    name: String,
    thumbnails: List<String>,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val first = rememberArtworkColors(thumbnails.getOrNull(0))
    val second = rememberArtworkColors(thumbnails.getOrNull(1))
    val tint = CoverTints[name.hashCode().absoluteValue % CoverTints.size]
    val light by animateColorAsState(first.getOrNull(0) ?: tint.first, tween(500), label = "coverLight")
    val deep by animateColorAsState(
        second.getOrNull(1) ?: first.getOrNull(1) ?: tint.second,
        tween(500),
        label = "coverDeep",
    )
    val accent by animateColorAsState(second.getOrNull(0) ?: first.getOrNull(2) ?: lerp(tint.first, Color.White, 0.25f), tween(500), label = "coverAccent")

    BoxWithConstraints(
        modifier =
            modifier
                .clip(shape)
                .drawWithCache {
                    val base = Brush.linearGradient(listOf(lerp(light, Color.Black, 0.15f), lerp(deep, Color.Black, 0.35f)), Offset.Zero, Offset(size.width, size.height))
                    val glowA = Brush.radialGradient(listOf(light.copy(alpha = 0.9f), Color.Transparent), Offset(size.width * 0.22f, size.height * 0.25f), size.minDimension * 0.75f)
                    val glowB = Brush.radialGradient(listOf(accent.copy(alpha = 0.75f), Color.Transparent), Offset(size.width * 0.85f, size.height * 0.8f), size.minDimension * 0.7f)
                    // The glass: a soft sheen from the top left and a darker foot for the name.
                    val sheen = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent, Color.Transparent), Offset.Zero, Offset(size.width, size.height))
                    val foot = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.38f)), size.height * 0.45f, size.height)
                    onDrawBehind {
                        drawRect(base)
                        drawRect(glowA)
                        drawRect(glowB)
                        drawRect(sheen)
                        drawRect(foot)
                    }
                }
                .border(1.dp, Color.White.copy(alpha = 0.22f), shape),
    ) {
        val side = minOf(maxWidth, maxHeight)
        if (side < 90.dp) {
            Icon(
                painterResource(R.drawable.queue_music),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.align(Alignment.Center).size(side * 0.42f),
            )
        } else {
            Text(
                text = name,
                maxLines = if (side < 180.dp) 2 else 3,
                overflow = TextOverflow.Ellipsis,
                style =
                    TextStyle(
                        color = Color.White,
                        fontSize = (side.value * 0.12f).sp,
                        lineHeight = (side.value * 0.135f).sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.02f * side.value * 0.12f).sp,
                        shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 2f), 8f),
                    ),
                modifier = Modifier.align(Alignment.BottomStart).padding(side * 0.08f),
            )
        }
    }
}
