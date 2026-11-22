package com.metrolist.music.playback

/** Restoring a paused queue is not a request to publish media controls. */
internal class PlaybackNotificationGate {
    private var playbackRequested = false

    fun shouldShow(playWhenReady: Boolean, foregroundRequired: Boolean): Boolean {
        playbackRequested = playbackRequested || playWhenReady || foregroundRequired
        return playbackRequested
    }

    /**
     * The service was promoted to the foreground with a placeholder notification (a widget or
     * other start command): let Media3 replace it with the real controls from now on, or the
     * blank placeholder would stay pinned until playback starts.
     */
    fun open() {
        playbackRequested = true
    }
}
