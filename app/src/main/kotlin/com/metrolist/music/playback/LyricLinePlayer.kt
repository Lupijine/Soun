/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import androidx.media3.common.FlagSet
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import java.util.concurrent.CopyOnWriteArraySet

/**
 * The player as the media session sees it: the same player, except that while a lyric line is
 * set it is shown in place of the artist, so the lock screen, the notification and connected
 * car displays follow the lyrics. The app itself keeps using the real player.
 *
 * [line] must be set on the player's application (main) thread.
 */
@UnstableApi
class LyricLinePlayer(player: Player) : ForwardingPlayer(player) {
    private val sessionListeners = CopyOnWriteArraySet<Player.Listener>()

    var line: String? = null
        set(value) {
            val clean = value?.trim()?.takeIf { it.isNotEmpty() }
            if (field == clean) return
            field = clean
            notifyMetadataChanged()
        }

    override fun addListener(listener: Player.Listener) {
        sessionListeners.add(listener)
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        sessionListeners.remove(listener)
        super.removeListener(listener)
    }

    override fun getMediaMetadata(): MediaMetadata {
        val base = super.getMediaMetadata()
        val current = line ?: return base
        return base.buildUpon()
            .setArtist(current)
            .setSubtitle(current)
            .build()
    }

    private fun notifyMetadataChanged() {
        val metadata = mediaMetadata
        val events = Player.Events(FlagSet.Builder().add(Player.EVENT_MEDIA_METADATA_CHANGED).build())
        sessionListeners.forEach { listener ->
            listener.onMediaMetadataChanged(metadata)
            listener.onEvents(this, events)
        }
    }
}
