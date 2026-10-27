package com.metrolist.music.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackNotificationGateTest {
    @Test fun `browsing and restoring a paused queue do not publish playback controls`() {
        val gate = PlaybackNotificationGate()
        repeat(3) { assertFalse(gate.shouldShow(false, false)) }
        assertTrue(gate.shouldShow(true, false))
        assertTrue(gate.shouldShow(false, false))
    }

    @Test fun `foreground playback requests are never suppressed`() {
        assertTrue(PlaybackNotificationGate().shouldShow(false, true))
    }
}
