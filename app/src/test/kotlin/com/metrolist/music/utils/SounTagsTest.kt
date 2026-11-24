package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SounTagsTest {
    @Test
    fun `tidies tag names`() {
        assertEquals("Night drive", SounTags.cleanName("  Night    drive "))
        assertEquals(SounTags.MAX_NAME, SounTags.cleanName("x".repeat(80))!!.length)
        assertNull(SounTags.cleanName("   "))
        assertNull(SounTags.cleanName(""))
    }

    @Test
    fun `a new tag is created with the songs`() {
        val tags = toggled(emptyList(), " Rain ", listOf("a", "b", "a"), now = 5L)
        assertEquals(listOf(SongTag("Rain", listOf("a", "b"), 5L)), tags)
    }

    @Test
    fun `adding to an existing tag puts new songs first and matches names without case`() {
        val start = listOf(SongTag("Rain", listOf("a"), 1L))
        val tags = toggled(start, "rain", listOf("b", "a"), now = 9L)
        assertEquals(listOf(SongTag("Rain", listOf("b", "a"), 1L)), tags)
    }

    @Test
    fun `toggling songs that all carry the tag takes it off`() {
        val start = listOf(SongTag("Focus", listOf("a", "b", "c"), 1L), SongTag("Rain", listOf("a"), 2L))
        val tags = toggled(start, "Focus", listOf("a", "c"), now = 9L)
        assertEquals(listOf(SongTag("Focus", listOf("b"), 1L), SongTag("Rain", listOf("a"), 2L)), tags)
    }

    @Test
    fun `blank names change nothing`() {
        val start = listOf(SongTag("Rain", listOf("a"), 1L))
        assertEquals(start, toggled(start, "  ", listOf("b"), now = 9L))
    }
}
