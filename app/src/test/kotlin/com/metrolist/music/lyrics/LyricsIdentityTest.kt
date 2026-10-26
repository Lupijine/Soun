package com.metrolist.music.lyrics

import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.lrclib.models.Track
import com.metrolist.lrclib.models.matchingTracks
import org.junit.Assert.*
import org.junit.Test

class LyricsIdentityTest {
    @Test fun `old lyrics cannot follow a song switch even while a translation is active`() {
        val old = LyricsEntity("old-song", "Old song lyrics", "BetterLyrics")
        val current = LyricsEntity("new-song", "New song lyrics", "LrcLib")
        assertNull(lyricsForSong("new-song", old, old, translating = true))
        assertNull(lyricsForSong(null, old, old, translating = true))
        assertEquals(current, lyricsForSong("new-song", current, old, translating = true))
        assertEquals(current, lyricsForSong("new-song", null, current, translating = true))
        assertNull(lyricsForSong("new-song", null, current, translating = false))
    }

    @Test fun `Everywhere by Cranes cannot select another artist with the same title`() {
        val candidates = listOf(
            Track(1, "Everywhere", "Fleetwood Mac", 215.0, "Other lyrics", null),
            Track(2, "Everywhere", "Michelle Branch", 215.0, null, "Different lyrics"),
            Track(3, "Everywhere", "Cranes", 215.0, "Cranes fixture lyrics", null),
        )
        assertEquals(listOf(3), candidates.matchingTracks(215, "Everywhere", "Cranes").map { it.id })
    }

    @Test fun `unidentified word synced lyrics must match the verified text rather than only timing`() {
        val verified = "Amber rivers cross the valley slowly. Paper lanterns light the garden path.\nMorning carries distant silver echoes. Quiet windows catch the fading stars."
        val synced = "[00:01.00]{agent:v1}Amber rivers cross the valley slowly. Paper lanterns light the garden path.\n<Amber:1:2|rivers:2:3>\n[00:05.00]Morning carries distant silver echoes. Quiet windows catch the fading stars."
        assertTrue(sameLyricsBody(synced, verified))
        assertFalse(sameLyricsBody("[00:01.00]A completely different song about sailing over an endless ocean and following the sun on a summer afternoon.", verified))
        assertFalse(sameLyricsBody("", verified))
    }
}
