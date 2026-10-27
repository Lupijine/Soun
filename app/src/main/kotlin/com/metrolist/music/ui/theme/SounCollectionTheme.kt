package com.metrolist.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import com.kyant.backdrop.Backdrop
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.materialkolor.rememberDynamicColorScheme
import androidx.compose.runtime.staticCompositionLocalOf

val LocalSounCollectionRows = staticCompositionLocalOf { false }

val LocalSounArtworkAccent = staticCompositionLocalOf { true }

@Composable
fun SounCollectionTheme(color: Color, backdrop: Backdrop? = LocalSounSurfaceBackdrop.current, content: @Composable () -> Unit) {
    if (!LocalSounArtworkAccent.current) {
        CompositionLocalProvider(LocalSounSurfaceBackdrop provides backdrop, LocalSounCollectionRows provides true, content = content)
        return
    }
    val base = MaterialTheme.colorScheme
    val accents = rememberDynamicColorScheme(seedColor = color, isDark = base.surface.luminance() < 0.5f)
    MaterialTheme(colorScheme = base.copy(
        primary = accents.primary, onPrimary = accents.onPrimary,
        primaryContainer = accents.primaryContainer, onPrimaryContainer = accents.onPrimaryContainer,
        secondary = accents.secondary, onSecondary = accents.onSecondary,
        secondaryContainer = accents.secondaryContainer, onSecondaryContainer = accents.onSecondaryContainer,
        tertiary = accents.tertiary, onTertiary = accents.onTertiary,
    )) {
        CompositionLocalProvider(LocalSounSurfaceBackdrop provides backdrop, LocalSounCollectionRows provides true, content = content)
    }
}
