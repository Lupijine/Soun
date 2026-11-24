package com.metrolist.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterParserTest {
    private val hour = 60L * 60 * 1000

    @Test
    fun `reads a plain tracklist`() {
        val description =
            """
            A Glimpse Of Entropy - full album

            Tracklist:
            0:00 Intro
            4:12 - The Visionary
            11:05 – Glimpse | Part II
            1:02:03 Outro

            Follow us on Instagram
            """.trimIndent()
        val chapters = ChapterParser.parse(description, 2 * hour)
        assertEquals(listOf(0L, 252_000L, 665_000L, 3_723_000L), chapters.map { it.startMs })
        assertEquals(listOf("Intro", "The Visionary", "Glimpse | Part II", "Outro"), chapters.map { it.title })
    }

    @Test
    fun `strips track numbers, brackets and ranges`() {
        val description =
            """
            1. Sunrise (0:00)
            2. Rain [5:30]
            03 - 22 Acacia Avenue (10:15)
            00:15:00 - 00:20:00 Last One
            """.trimIndent()
        val chapters = ChapterParser.parse(description, hour)
        assertEquals(listOf("Sunrise", "Rain", "22 Acacia Avenue", "Last One"), chapters.map { it.title })
        assertEquals(listOf(0L, 330_000L, 615_000L, 900_000L), chapters.map { it.startMs })
    }

    @Test
    fun `song lengths are not mistaken for start times`() {
        val description =
            """
            Sunrise 4:12
            Rain 3:55
            Night 5:01
            """.trimIndent()
        assertTrue(ChapterParser.parse(description, hour).isEmpty())
    }

    @Test
    fun `picks the tracklist over other times in the description`() {
        val description =
            """
            Recorded live 21:30 on stage
            0:00 One
            3:00 Two
            6:00 Three
            Lyrics:
            [0:12] first line
            [0:20] second line
            """.trimIndent()
        assertEquals(listOf("One", "Two", "Three"), ChapterParser.parse(description, hour).map { it.title })
    }

    @Test
    fun `needs three parts that fit in the track`() {
        assertTrue(ChapterParser.parse("0:00 One\n3:00 Two", hour).isEmpty())
        val chapters = ChapterParser.parse("0:00 One\n3:00 Two\n6:00 Three\n2:00:00 Too far", 10 * 60 * 1000L)
        assertEquals(3, chapters.size)
    }

    @Test
    fun `a list starting a few seconds in starts at zero`() {
        val chapters = ChapterParser.parse("0:05 One\n3:00 Two\n6:00 Three", hour)
        assertEquals(0L, chapters.first().startMs)
    }
}
