/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import android.util.LruCache
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val artworkColors = LruCache<String, List<Color>>(24)

@Composable
fun rememberArtworkColors(url: String?, enabled: Boolean = true): List<Color> {
    val context = LocalContext.current
    val fallback = MaterialTheme.colorScheme.surface.toArgb()
    val colors by produceState<List<Color>>(emptyList(), url, enabled, fallback) {
        value = emptyList()
        if (url == null || !enabled) return@produceState
        artworkColors.get(url)?.let { value = it; return@produceState }
        try {
            val bitmap = withContext(Dispatchers.IO) {
                context.imageLoader.execute(
                    ImageRequest.Builder(context).data(url).size(100, 100).allowHardware(false).build(),
                ).image?.toBitmap()
            }
            if (bitmap != null) {
                val result = withContext(Dispatchers.Default) {
                    PlayerColorExtractor.extractGradientColors(
                        Palette.from(bitmap).maximumColorCount(8).resizeBitmapArea(10000).generate(), fallback,
                    )
                }
                artworkColors.put(url, result)
                value = result
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            value = emptyList()
        }
    }
    return colors
}
