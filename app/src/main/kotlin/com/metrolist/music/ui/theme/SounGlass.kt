/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/** Soun's default accent, used when the user hasn't picked a theme color. */
val SounAccent = Color(0xFFA99BFF)

// Capture only screen content: including the floating controls would create a feedback loop.
val LocalSounBackdrop = staticCompositionLocalOf<Backdrop?> { null }
val LocalSounSurfaceBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun Modifier.sounPanel(shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)): Modifier =
    sounGlass(shape, sounGlassColors(strong = false), elevation = 0f, backdrop = LocalSounSurfaceBackdrop.current)

/**
 * Liquid Glass surface colors for one theme. A strong surface (nav bar, mini player)
 * is more opaque and has a brighter rim than a regular one (cards, chips).
 */
data class SounGlassColors(
    val fillTop: Color,
    val fillBottom: Color,
    val rimBright: Color,
    val rimDim: Color,
    val shadow: Color,
    val selectedPill: Color,
    val surfaceTint: Color,
    val fallback: Color,
)

@Composable
@ReadOnlyComposable
fun sounGlassColors(strong: Boolean = true, pureBlack: Boolean = false): SounGlassColors {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (dark) {
        // Glass needs something behind it to read as glass; on pure black we
        // tint it a little more so the edge still shows.
        val boost = if (pureBlack) 0.04f else 0f
        SounGlassColors(
            fillTop = Color.White.copy(alpha = (if (strong) 0.14f else 0.08f) + boost),
            fillBottom = Color.White.copy(alpha = 0.02f + boost),
            rimBright = Color.White.copy(alpha = if (strong) 0.62f else 0.45f),
            rimDim = Color.White.copy(alpha = 0.06f),
            shadow = Color.Black.copy(alpha = 0.30f),
            selectedPill = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
            surfaceTint = (if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface).copy(alpha = if (strong) 0.24f else 0.12f),
            fallback = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    } else {
        SounGlassColors(
            fillTop = Color.White.copy(alpha = if (strong) 0.32f else 0.22f),
            fillBottom = Color.White.copy(alpha = 0.08f),
            rimBright = Color.White,
            rimDim = Color(0xFF1E143C).copy(alpha = 0.07f),
            shadow = Color(0xFF281E5A).copy(alpha = 0.16f),
            selectedPill = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            surfaceTint = MaterialTheme.colorScheme.surface.copy(alpha = 0.30f),
            fallback = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

/**
 * Filters the live screen behind the surface, leaving its controls sharp.
 * The library gates lens refraction to API 33; blur is available from API 31.
 */
@Composable
fun Modifier.sounGlass(
    shape: Shape,
    colors: SounGlassColors,
    elevation: Float = 18f,
    backdropEnabled: Boolean = true,
    backdrop: Backdrop? = LocalSounBackdrop.current,
): Modifier {
    if (backdropEnabled && backdrop != null && isRenderEffectSupported()) {
        return this
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    // Keep detail in the centre; the curved edge does the optical work.
                    // A light blur keeps text behind the glass from showing through warped;
                    // a gentler lens keeps the curved edge without bending content too far.
                    blur(4.dp.toPx())
                    lens(10.dp.toPx(), 20.dp.toPx(), depthEffect = true)
                },
                highlight = {
                    Highlight(
                        width = 1.dp,
                        blurRadius = 0.35.dp,
                        style = HighlightStyle.Default(color = colors.rimBright, angle = 35f, falloff = 1.5f),
                    )
                },
                shadow = { Shadow(radius = elevation.dp, color = colors.shadow) },
                innerShadow = {
                    InnerShadow(radius = 3.dp, offset = DpOffset(0.dp, 1.dp), color = Color.Black.copy(alpha = 0.08f))
                },
                onDrawSurface = {
                    drawRect(colors.surfaceTint)
                    drawRect(Brush.verticalGradient(listOf(colors.fillTop, colors.fillBottom)))
                },
            )
            .clip(shape)
    }
    return this
        .shadow(
            elevation = elevation.dp,
            shape = shape,
            clip = false,
            ambientColor = colors.shadow,
            spotColor = colors.shadow,
        )
        .clip(shape)
        .then(if (backdropEnabled) Modifier.background(colors.fallback, shape) else Modifier)
        .background(Brush.verticalGradient(listOf(colors.fillTop, colors.fillBottom)), shape)
        .border(
            BorderStroke(
                width = 1.dp,
                brush =
                    Brush.linearGradient(
                        0f to colors.rimBright,
                        0.28f to colors.rimDim,
                        0.5f to Color.Transparent,
                        0.74f to colors.rimDim,
                        1f to colors.rimBright.copy(alpha = colors.rimBright.alpha * 0.7f),
                        start = Offset.Zero,
                        end = Offset.Infinite,
                    ),
            ),
            shape,
        )
}
