package com.metrolist.music.ui.theme

import androidx.compose.foundation.background
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
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

/**
 * A dialog with real Liquid Glass (refraction, not just blur).
 *
 * A normal dialog is a small separate window, so the glass can't see the app behind it.
 * Here the dialog window covers the whole screen at the same origin as the app, which
 * lets [sounGlass] sample the app's live backdrop at the right position. The scrim and
 * tap-outside-to-dismiss are drawn here instead of by the window.
 */
@Composable
fun SounGlassDialog(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                window.setDimAmount(0f)
                window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
            }
            onDispose {}
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.28f))
                    .clickable(interactionSource = null, indication = null, onClick = onDismiss),
        ) {
            Box(
                modifier =
                    Modifier
                        .padding(24.dp)
                        .sounGlass(RoundedCornerShape(32.dp), sounGlassColors(strong = true), elevation = 24f)
                        .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.35f))
                        // Swallow taps so touching the dialog doesn't dismiss it.
                        .clickable(interactionSource = null, indication = null, onClick = {}),
            ) {
                content()
            }
        }
    }
}
