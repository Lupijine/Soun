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
import androidx.compose.runtime.remember
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
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

// A convex pane magnifies the centre and curves the image across the entire surface.
internal const val SounPanelLensShader = """
uniform shader content;
uniform float2 size;
half4 main(float2 coord) {
    float2 centre = size * 0.5;
    float2 local = coord - centre;
    float2 normalized = local / max(centre, float2(1.0));
    float scale = 0.78 + 0.12 * min(dot(normalized, normalized), 2.0);
    float2 samplePoint = centre + local * scale;
    half4 colour = content.eval(samplePoint);
    colour.r = content.eval(samplePoint + local * 0.002).r;
    colour.b = content.eval(samplePoint - local * 0.002).b;
    return colour;
}
"""

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
            rimBright = Color.White.copy(alpha = if (strong) 0.40f else 0.28f),
            rimDim = Color.White.copy(alpha = 0.06f),
            shadow = Color.Black.copy(alpha = 0.30f),
            selectedPill = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
            surfaceTint = (if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface).copy(alpha = if (strong) 0.24f else 0.04f),
            fallback = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    } else {
        SounGlassColors(
            // Small controls (chips, round buttons) stay almost clear so they read as
            // glass, not as white pills; the bright rim carries the shape.
            fillTop = Color.White.copy(alpha = if (strong) 0.18f else 0.08f),
            fillBottom = Color.White.copy(alpha = if (strong) 0.08f else 0f),
            // On light backgrounds a white rim reads as a white outline; a faint dark
            // edge defines the glass the way it looks on a white surface.
            rimBright = Color(0xFF1E143C).copy(alpha = if (strong) 0.16f else 0.12f),
            rimDim = Color(0xFF1E143C).copy(alpha = 0.07f),
            shadow = Color(0xFF281E5A).copy(alpha = 0.16f),
            selectedPill = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            surfaceTint = MaterialTheme.colorScheme.surface.copy(alpha = if (strong) 0.12f else 0f),
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
    fullPanel: Boolean = false,
): Modifier {
    val fill = remember(colors.fillTop, colors.fillBottom) {
        Brush.verticalGradient(listOf(colors.fillTop, colors.fillBottom))
    }
    if (backdropEnabled && backdrop != null && isRenderEffectSupported()) {
        return this
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    if (fullPanel) {
                        runtimeShaderEffect("SounPanelLens", SounPanelLensShader, "content") {
                            setFloatUniform("size", size.width, size.height)
                        }
                    }
                    if (Build.VERSION.SDK_INT < 33) blur(5.dp.toPx())
                    lens(18.dp.toPx(), 32.dp.toPx(), depthEffect = true, chromaticAberration = true)
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
                    drawRect(fill)
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
        .background(fill, shape)
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

/**
 * Full-screen Liquid Glass for the Now Playing sheet: the live app behind it is
 * refracted by a thick glass slab with rounded corners (bent at the edges, with a
 * light-catching rim), lightly diffused and tinted so the controls stay readable.
 * Falls back to a solid [tint] where the live backdrop isn't available.
 */
@Composable
fun Modifier.sounSheetGlass(tint: Color, enabled: Boolean = true): Modifier {
    val backdrop = LocalSounBackdrop.current
    if (!enabled || backdrop == null || !isRenderEffectSupported()) return this.background(tint)
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(36.dp)
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(6.dp.toPx())
            // A tall refraction band makes the whole slab act as a lens, not just its rim.
            lens(140.dp.toPx(), 96.dp.toPx(), depthEffect = true, chromaticAberration = true)
        },
        highlight = {
            Highlight(
                width = 1.2.dp,
                blurRadius = 0.5.dp,
                style = HighlightStyle.Default(color = Color.White.copy(alpha = 0.55f), angle = 35f, falloff = 1.5f),
            )
        },
        shadow = { Shadow(radius = 0.dp, color = Color.Transparent) },
        onDrawSurface = {
            drawRect(tint.copy(alpha = tint.alpha * 0.45f))
        },
    )
}
