package com.metrolist.music.lyrics

import org.junit.Assert.*
import org.junit.Test

class LyricsTimingTest {
    @Test
    fun `line-only lyrics never acquire invented word timestamps`() {
        assertNull(LyricsEntry(1000, "A whole line").timedWordsForDisplay(false))
        assertNull(LyricsEntry(1000, "A whole line", emptyList()).timedWordsForDisplay(false))
    }

    @Test
    fun `real timestamps are preserved but never applied to romanized text`() {
        val words = listOf(WordTimestamp("Hello", 1.0, 1.5), WordTimestamp("world", 1.6, 2.0))
        val entry = LyricsEntry(1000, "Hello world", words)
        assertSame(words, entry.timedWordsForDisplay(false))
        assertNull(entry.timedWordsForDisplay(true))
    }

    @Test
    fun `invalid timestamps fall back to line highlighting`() {
        for (word in listOf(
            WordTimestamp("x", Double.NaN, 2.0),
            WordTimestamp("x", 1.0, Double.POSITIVE_INFINITY),
            WordTimestamp("x", 2.0, 1.0),
            WordTimestamp("x", -1.0, 1.0),
            WordTimestamp("x", 1.0, 1.0),
        )) assertNull(LyricsEntry(1000, "x", listOf(word)).timedWordsForDisplay(false))
    }
}
