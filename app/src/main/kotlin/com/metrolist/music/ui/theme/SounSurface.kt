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
        .background(Brush.verticalGradient(listOf(colors.surfaceContainerHigh.copy(alpha = 0.62f), colors.surfaceContainer.copy(alpha = 0.5f))), shape)
        .border(0.5.dp, colors.outlineVariant.copy(alpha = 0.5f), shape)
}

/**
 * Real glass for dialogs and sheets. They live in their own window, so the in-app
 * backdrop can't reach behind them; instead Android 12+ blurs whatever is behind
 * the window itself. Call from inside the dialog or sheet content.
 */
@Composable
fun SounWindowBlur(radius: androidx.compose.ui.unit.Dp = 36.dp) {
    val view = androidx.compose.ui.platform.LocalView.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.DisposableEffect(view) {
        val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        if (window != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply {
                blurBehindRadius = with(density) { radius.roundToPx() }
            }
            window.setDimAmount(0.18f)
        }
        onDispose {}
    }
}

/** Translucent glass fill for windows blurred by [SounWindowBlur]. */
@Composable
fun Modifier.sounWindowGlass(shape: Shape = RoundedCornerShape(32.dp)): Modifier {
    val colors = MaterialTheme.colorScheme
    val glass = sounGlassColors(strong = true)
    return this
        .sounGlass(shape, glass, elevation = 24f, backdropEnabled = false)
        .background(colors.surfaceContainer.copy(alpha = 0.55f), shape)
}
