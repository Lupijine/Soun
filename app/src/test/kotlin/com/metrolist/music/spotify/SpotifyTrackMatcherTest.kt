package com.metrolist.music.spotify

import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyTrackMatcherTest {
    private fun song(id: String, title: String, artist: String, seconds: Int?) = SongItem(
        id = id,
        title = title,
        artists = listOf(Artist(name = artist, id = null)),
        duration = seconds,
        thumbnail = "",
    )

    private fun track(title: String, vararg artists: String, seconds: Int? = null) =
        SpotifyTrack(title = title, artists = artists.toList(), durationMs = seconds?.let { it * 1000L })

    @Test
    fun `prefers the right artist over a song with the same title`() {
        val spotify = track("Hello", "Adele", seconds = 295)
        val results = listOf(
            song("richie", "Hello", "Lionel Richie", 251),
            song("adele", "Hello", "Adele", 296),
        )
        assertEquals("adele", SpotifyTrackMatcher.bestMatch(spotify, results)?.id)
    }

    @Test
    fun `skips live and remix versions the Spotify track is not`() {
        val spotify = track("Blinding Lights", "The Weeknd", seconds = 200)
        val results = listOf(
            song("live", "Blinding Lights (Live)", "The Weeknd", 210),
            song("studio", "Blinding Lights", "The Weeknd", 201),
        )
        assertEquals("studio", SpotifyTrackMatcher.bestMatch(spotify, results)?.id)
    }

    @Test
    fun `keeps a version the Spotify title asks for`() {
        val spotify = track("Despacito - Remix", "Luis Fonsi", "Daddy Yankee", "Justin Bieber", seconds = 229)
        val results = listOf(
            song("original", "Despacito", "Luis Fonsi", 282),
            song("remix", "Despacito (Remix)", "Luis Fonsi", 229),
        )
        assertEquals("remix", SpotifyTrackMatcher.bestMatch(spotify, results)?.id)
    }

    @Test
    fun `ignores remaster suffixes and prefers the closest length`() {
        val spotify = track("Bohemian Rhapsody - Remastered 2011", "Queen", seconds = 354)
        val results = listOf(
            song("long", "Bohemian Rhapsody", "Queen", 420),
            song("right", "Bohemian Rhapsody (Remastered 2011)", "Queen", 355),
        )
        assertEquals("right", SpotifyTrackMatcher.bestMatch(spotify, results)?.id)
    }

    @Test
    fun `finds nothing when no result is the same song`() {
        val spotify = track("Blinding Lights", "The Weeknd", seconds = 200)
        val results = listOf(
            song("a", "Save Your Tears", "The Weeknd", 215),
            song("b", "Blinding Lights", "Tribute Band", 260),
        )
        assertNull(SpotifyTrackMatcher.bestMatch(spotify, results))
    }

    @Test
    fun `accepts a top hit by the same artist with the same length when the title is in another script`() {
        val spotify = track("Ya Bent El Sultan", "Ahmed Adaweyah", seconds = 241)
        val top = song("arabic", "يا بنت السلطان", "Ahmed Adaweyah", 242)
        assertEquals("arabic", SpotifyTrackMatcher.bestMatch(spotify, listOf(top))?.id)
        val lower = listOf(song("other", "Something Else", "Someone", 100), top)
        assertNull(SpotifyTrackMatcher.bestMatch(spotify, lower))
    }

    @Test
    fun `builds queries from title and main artist`() {
        val spotify = track("Bohemian Rhapsody - Remastered 2011", "Queen")
        assertEquals("Bohemian Rhapsody - Remastered 2011 Queen", SpotifyTrackMatcher.query(spotify))
        assertEquals("Bohemian Rhapsody Queen", SpotifyTrackMatcher.fallbackQuery(spotify))
        assertNull(SpotifyTrackMatcher.fallbackQuery(track("Hello", "Adele")))
    }

    @Test
    fun `normalizes accents and symbols`() {
        assertEquals(listOf("beyonce", "and", "jay", "z"), SpotifyTrackMatcher.tokens("Beyoncé & JAY-Z"))
    }
}
