package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SounMomentsTest {
    @Test
    fun `reads the start time of shared links`() {
        assertEquals(754_000L, SounMoments.parseStartTime("754"))
        assertEquals(754_000L, SounMoments.parseStartTime("754s"))
        assertEquals(754_000L, SounMoments.parseStartTime("12m34s"))
        assertEquals(3_723_000L, SounMoments.parseStartTime("1h2m3s"))
        assertEquals(120_000L, SounMoments.parseStartTime("2m"))
        assertNull(SounMoments.parseStartTime(""))
        assertNull(SounMoments.parseStartTime("abc"))
        assertNull(SounMoments.parseStartTime(null))
    }

    @Test
    fun `share links start at the saved second`() {
        assertEquals("https://music.youtube.com/watch?v=abc&t=754", SounMoments.shareLink("abc", 754_900L))
    }
}
