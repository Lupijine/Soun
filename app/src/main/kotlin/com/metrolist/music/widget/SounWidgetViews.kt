/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.metrolist.music.R
import com.metrolist.music.utils.makeTimeString

internal data class NowPlayingState(
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val isLiked: Boolean,
    val duration: Long,
    val position: Long,
)

/** Builds the Liquid Glass RemoteViews for the Now Playing and Turntable widgets. */
internal object SounWidgetViews {

    enum class NowPlayingSize { SQUARE, WIDE, FULL }

    fun sizeOf(options: Bundle): NowPlayingSize {
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        return when {
            minHeight < 100 -> if (minWidth >= 180) NowPlayingSize.WIDE else NowPlayingSize.SQUARE
            // Two columns are too narrow for the cover + controls layout, show the cover-filled square instead
            minWidth < 240 -> NowPlayingSize.SQUARE
            else -> NowPlayingSize.FULL
        }
    }

    /** MusicService reports an empty player with these titles and no artwork. */
    fun isIdle(context: Context, title: String, artworkUri: String?): Boolean =
        artworkUri == null &&
            (title == context.getString(R.string.no_song_playing) || title == context.getString(R.string.not_playing))

    fun nowPlaying(
        context: Context,
        options: Bundle,
        state: NowPlayingState,
        covers: SounWidgetCovers,
    ): RemoteViews = when (sizeOf(options)) {
        NowPlayingSize.SQUARE -> compactSquare(context, state, covers)
        NowPlayingSize.WIDE -> compactWide(context, state, covers)
        NowPlayingSize.FULL -> full(context, state, covers)
    }

    fun idle(context: Context, options: Bundle): RemoteViews {
        val size = sizeOf(options)
        if (size == NowPlayingSize.FULL) return empty(context)
        val state = NowPlayingState(
            title = context.getString(R.string.soun_widget_nothing_playing),
            artist = context.getString(R.string.soun_widget_pick_up),
            isPlaying = false,
            isLiked = false,
            duration = 0,
            position = 0,
        )
        val covers = SounWidgetArt.placeholderCovers(context)
        return if (size == NowPlayingSize.WIDE) compactWide(context, state, covers) else compactSquare(context, state, covers)
    }

    fun turntable(context: Context, covers: SounWidgetCovers, isPlaying: Boolean): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_turntable).apply {
            setImageViewBitmap(R.id.widget_turntable_album_art, covers.circular)
            setImageViewResource(R.id.widget_turntable_play_pause, playPauseIcon(isPlaying))
            setOnClickPendingIntent(R.id.widget_turntable_album_art, SounWidgetIntents.openApp(context))
            setOnClickPendingIntent(R.id.widget_turntable_play_container, SounWidgetIntents.turntablePlayPause(context))
            setOnClickPendingIntent(R.id.widget_turntable_prev_button, SounWidgetIntents.turntablePrevious(context))
            setOnClickPendingIntent(R.id.widget_turntable_next_button, SounWidgetIntents.turntableNext(context))
        }

    private fun full(context: Context, state: NowPlayingState, covers: SounWidgetCovers): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_music_player).apply {
            setTextViewText(R.id.widget_song_title, state.title)
            setTextViewText(R.id.widget_artist_name, state.artist)
            setImageViewBitmap(R.id.widget_album_art, covers.rounded)

            // The blurred backdrop relies on the root clipping to its rounded outline, which needs API 31
            val blurred = covers.blurred
            if (blurred != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setImageViewBitmap(R.id.widget_album_glass_art, blurred)
                setViewVisibility(R.id.widget_album_glass, View.VISIBLE)
            } else {
                setViewVisibility(R.id.widget_album_glass, View.GONE)
            }

            setImageViewResource(R.id.widget_play_pause, playPauseIcon(state.isPlaying))
            setImageViewResource(
                R.id.widget_like_button,
                if (state.isLiked) R.drawable.soun_widget_ic_heart_filled else R.drawable.soun_widget_ic_heart,
            )

            val duration = state.duration.coerceAtLeast(0)
            val position = state.position.coerceIn(0, duration)
            val level = if (duration > 0) (position * 10_000 / duration).toInt() else 0
            setInt(R.id.widget_progress_fill, "setImageLevel", level)
            setTextViewText(R.id.widget_progress_elapsed, makeTimeString(position))
            setTextViewText(R.id.widget_progress_duration, makeTimeString(duration))

            val openApp = SounWidgetIntents.openApp(context)
            setOnClickPendingIntent(R.id.widget_album_art, openApp)
            setOnClickPendingIntent(R.id.widget_info_area, openApp)
            setOnClickPendingIntent(R.id.widget_play_pause_container, SounWidgetIntents.playPause(context))
            setOnClickPendingIntent(R.id.widget_like_container, SounWidgetIntents.like(context))
            setOnClickPendingIntent(R.id.widget_prev_container, SounWidgetIntents.previous(context))
            setOnClickPendingIntent(R.id.widget_next_container, SounWidgetIntents.next(context))
        }

    private fun compactWide(context: Context, state: NowPlayingState, covers: SounWidgetCovers): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_compact_wide).apply {
            setTextViewText(R.id.widget_wide_song_title, state.title)
            setTextViewText(R.id.widget_wide_artist_name, state.artist)
            setImageViewBitmap(R.id.widget_wide_album_art, covers.circular)
            setImageViewResource(R.id.widget_wide_play_pause, playPauseIcon(state.isPlaying))

            val openApp = SounWidgetIntents.openApp(context)
            setOnClickPendingIntent(R.id.widget_wide_album_art, openApp)
            setOnClickPendingIntent(R.id.widget_wide_info_area, openApp)
            setOnClickPendingIntent(R.id.widget_wide_play_container, SounWidgetIntents.playPause(context))
            setOnClickPendingIntent(R.id.widget_wide_next_container, SounWidgetIntents.next(context))
        }

    private fun compactSquare(context: Context, state: NowPlayingState, covers: SounWidgetCovers): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_compact_square).apply {
            setTextViewText(R.id.widget_compact_song_title, state.title)
            setTextViewText(R.id.widget_compact_artist_name, state.artist)
            setImageViewBitmap(R.id.widget_compact_album_art, covers.fill)
            setImageViewResource(R.id.widget_compact_play_pause, playPauseIcon(state.isPlaying))

            val openApp = SounWidgetIntents.openApp(context)
            setOnClickPendingIntent(R.id.widget_compact_album_art, openApp)
            setOnClickPendingIntent(R.id.widget_compact_strip, openApp)
            setOnClickPendingIntent(R.id.widget_compact_play_container, SounWidgetIntents.playPause(context))
        }

    private fun empty(context: Context): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_music_empty).apply {
            setOnClickPendingIntent(R.id.widget_empty_content, SounWidgetIntents.openApp(context))
            setOnClickPendingIntent(R.id.widget_empty_resume, SounWidgetIntents.playPause(context))
            setOnClickPendingIntent(R.id.widget_empty_shuffle, SounWidgetIntents.openLikedSongs(context))
        }

    private fun playPauseIcon(isPlaying: Boolean): Int =
        if (isPlaying) R.drawable.soun_widget_ic_pause else R.drawable.soun_widget_ic_play
}
