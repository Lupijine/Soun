package com.metrolist.music.playback

/** Restoring a paused queue is not a request to publish media controls. */
internal class PlaybackNotificationGate {
    private var playbackRequested = false

    fun shouldShow(playWhenReady: Boolean, foregroundRequired: Boolean): Boolean {
        playbackRequested = playbackRequested || playWhenReady || foregroundRequired
        return playbackRequested
    }
}
