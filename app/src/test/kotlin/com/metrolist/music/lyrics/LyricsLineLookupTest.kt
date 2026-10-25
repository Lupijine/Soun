package com.metrolist.music.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsLineLookupTest {
    @Test
    fun `lookup preserves boundaries duplicate timestamps and backward seeks`() {
        val lines = listOf(0L, 1000L, 1000L, 2500L, 9000L).map { LyricsEntry(it, "line") }
        for (position in listOf(-101L, -100L, -99L, 899L, 900L, 901L, 2400L, 2401L, 20000L, 0L)) {
            val firstUpcoming = lines.indexOfFirst { it.time >= position + 100L }
            val expected = if (firstUpcoming == -1) lines.lastIndex else firstUpcoming - 1
            assertEquals("position=$position", expected, LyricsUtils.findCurrentLineIndex(lines, position))
        }
        assertEquals(-1, LyricsUtils.findCurrentLineIndex(emptyList(), 1000L))
    }

    @Test
    fun `long transcripts require logarithmic line reads`() {
        var reads = 0
        val lines = object : AbstractList<LyricsEntry>() {
            override val size = 100_000
            override fun get(index: Int): LyricsEntry {
                reads++
                return LyricsEntry(index * 1000L, "line")
            }
        }
        assertEquals(99_999, LyricsUtils.findCurrentLineIndex(lines, 100_000_000L))
        assertTrue("Expected logarithmic lookup, got $reads line reads", reads <= 17)
    }
}
