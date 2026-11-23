/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import com.metrolist.music.db.MusicDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetrolistWidgetManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val playlistWidgetManager: PlaylistWidgetManager,
) {
    private val imageLoader by lazy {
        ImageLoader.Builder(context)
            .crossfade(false)
            .build()
    }

    // Artwork variants are cached per URI so the once-a-second progress updates don't redo bitmap work
    @Volatile
    private var cachedArtworkUri: String? = null
    @Volatile
    private var cachedCovers: SounWidgetCovers? = null

    suspend fun updateWidgets(
        title: String,
        artist: String,
        artworkUri: String?,
        isPlaying: Boolean,
        isLiked: Boolean,
        duration: Long = 0,
        currentPosition: Long = 0
    ) = withContext(Dispatchers.IO) {
        val appWidgetManager = AppWidgetManager.getInstance(context)

        val widgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, MusicWidgetReceiver::class.java))
        val turntableWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, TurntableWidgetReceiver::class.java))
        if (widgetIds.isEmpty() && turntableWidgetIds.isEmpty()) {
            playlistWidgetManager.updateWidgets(title, artist, artworkUri, isPlaying, isLiked, duration, currentPosition)
            return@withContext
        }

        val covers = coversFor(artworkUri)
        val isIdle = SounWidgetViews.isIdle(context, title, artworkUri)
        val state = NowPlayingState(
            title = title,
            artist = artist,
            isPlaying = isPlaying,
            isLiked = isLiked,
            duration = duration,
            position = currentPosition,
        )

        widgetIds.forEach { widgetId ->
            val options = appWidgetManager.getAppWidgetOptions(widgetId)
            val views = if (isIdle) {
                SounWidgetViews.idle(context, options)
            } else {
                SounWidgetViews.nowPlaying(context, options, state, covers)
            }
            appWidgetManager.updateAppWidget(widgetId, views)
        }

        if (turntableWidgetIds.isNotEmpty()) {
            val turntableViews = SounWidgetViews.turntable(context, covers, isPlaying)
            turntableWidgetIds.forEach { widgetId ->
                appWidgetManager.updateAppWidget(widgetId, turntableViews)
            }
        }

        playlistWidgetManager.updateWidgets(
            title = title,
            artist = artist,
            artworkUri = artworkUri,
            isPlaying = isPlaying,
            isLiked = isLiked,
            duration = duration,
            currentPosition = currentPosition,
        )
    }

    private suspend fun coversFor(artworkUri: String?): SounWidgetCovers {
        if (artworkUri == null) return SounWidgetArt.placeholderCovers(context)
        if (artworkUri == cachedArtworkUri) cachedCovers?.let { return it }

        val art = loadAlbumArt(artworkUri, 300) ?: return SounWidgetArt.placeholderCovers(context)
        return SounWidgetArt.coversFor(art).also {
            cachedArtworkUri = artworkUri
            cachedCovers = it
        }
    }

    private suspend fun loadAlbumArt(artworkUri: String, size: Int = 200): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(artworkUri)
                    .size(size, size)
                    .allowHardware(false)
                    .crossfade(300)
                    .build()
                val result = imageLoader.execute(request)
                result.image?.toBitmap()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }
}
