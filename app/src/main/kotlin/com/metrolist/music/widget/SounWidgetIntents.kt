/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.metrolist.music.MainActivity

/** Click actions shared by the Now Playing and Turntable widgets. */
internal object SounWidgetIntents {
    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), FLAGS)

    fun playPause(context: Context): PendingIntent =
        musicBroadcast(context, MusicWidgetReceiver.ACTION_PLAY_PAUSE, 1)

    fun like(context: Context): PendingIntent =
        musicBroadcast(context, MusicWidgetReceiver.ACTION_LIKE, 2)

    fun next(context: Context): PendingIntent =
        musicBroadcast(context, MusicWidgetReceiver.ACTION_NEXT, 6)

    fun previous(context: Context): PendingIntent =
        musicBroadcast(context, MusicWidgetReceiver.ACTION_PREVIOUS, 7)

    fun turntablePlayPause(context: Context): PendingIntent =
        turntableBroadcast(context, TurntableWidgetReceiver.ACTION_TURNTABLE_PLAY_PAUSE, 3)

    fun turntableNext(context: Context): PendingIntent =
        turntableBroadcast(context, TurntableWidgetReceiver.ACTION_TURNTABLE_NEXT, 4)

    fun turntablePrevious(context: Context): PendingIntent =
        turntableBroadcast(context, TurntableWidgetReceiver.ACTION_TURNTABLE_PREVIOUS, 5)

    /** There is no shuffle-play action yet, so "Shuffle liked" opens Liked songs where shuffle is one tap away. */
    fun openLikedSongs(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_WIDGET_TARGET
            putExtra(MainActivity.EXTRA_WIDGET_TARGET_TYPE, PlaylistWidgetReceiver.TARGET_TYPE_LIKED)
            putExtra(MainActivity.EXTRA_WIDGET_TARGET_ID, PlaylistWidgetReceiver.TARGET_TYPE_LIKED)
        }
        return PendingIntent.getActivity(context, 8, intent, FLAGS)
    }

    private fun musicBroadcast(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MusicWidgetReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(context, requestCode, intent, FLAGS)
    }

    private fun turntableBroadcast(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, TurntableWidgetReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(context, requestCode, intent, FLAGS)
    }
}
