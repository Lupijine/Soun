package com.metrolist.music.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AmbientPhaseTest {
    @Test fun `scroll pause freezes movement and resumes without catching up`() {
        val phase = AmbientPhase()
        phase.advance(0f)
        assertEquals(0.1f, phase.advance(0.1f), 0.0001f)
        assertEquals(0.1f, phase.advance(null), 0.0001f)
        assertEquals(0.1f, phase.advance(0.8f), 0.0001f)
        assertEquals(0.2f, phase.advance(0.9f), 0.0001f)
        assertEquals(0.35f, phase.advance(0.05f), 0.0001f)
    }
}
