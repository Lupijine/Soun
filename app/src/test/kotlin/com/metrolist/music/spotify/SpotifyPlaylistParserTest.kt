package com.metrolist.music.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyPlaylistParserTest {
    private fun fixture(): String =
        javaClass.getResourceAsStream("/spotify/embed_playlist.html")!!.bufferedReader().use { it.readText() }

    @Test
    fun `extracts playlist ids from links and uris`() {
        val id = "37i9dQZF1DXcBWIGoYBM5M"
        listOf(
            "https://open.spotify.com/playlist/$id",
            "https://open.spotify.com/playlist/$id?si=4f2a9c1e7b8d4e21&pi=a-xyz",
            "  https://open.spotify.com/intl-fr/playlist/$id?si=abc  ",
            "open.spotify.com/embed/playlist/$id",
            "https://open.spotify.com/user/someone/playlist/$id",
            "spotify:playlist:$id",
            "spotify:user:someone:playlist:$id",
            "Listen to this! https://open.spotify.com/playlist/$id?si=x",
            id,
        ).forEach { input -> assertEquals(input, id, SpotifyPlaylistParser.extractPlaylistId(input)) }
    }

    @Test
    fun `rejects links that are not playlists`() {
        listOf(
            "",
            "hello",
            "https://open.spotify.com/album/1DFixLWuPkv3KT3TnV35m3",
            "https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b",
            "https://music.youtube.com/playlist?list=PL123",
            "spotify:artist:1Xyo4u8uXC1ZmMpatF05PJ",
        ).forEach { input -> assertNull(input, SpotifyPlaylistParser.extractPlaylistId(input)) }
    }

    @Test
    fun `parses name, cover and tracks from the embed page`() {
        val playlist = SpotifyPlaylistParser.parseEmbedHtml(fixture())!!

        assertEquals("Soun Test Mix", playlist.name)
        assertEquals("https://image-cdn-ak.spotifycdn.com/image/large640", playlist.coverUrl)
        assertNull(playlist.reportedTrackCount)
        // The entry without a title is skipped.
        assertEquals(4, playlist.tracks.size)

        val first = playlist.tracks[0]
        assertEquals("Blinding Lights", first.title)
        assertEquals(listOf("The Weeknd"), first.artists)
        assertEquals(200040L, first.durationMs)

        assertEquals(
            listOf("Luis Fonsi", "Daddy Yankee", "Justin Bieber"),
            playlist.tracks[1].artists,
        )
        assertEquals("Bohemian Rhapsody - Remastered 2011", playlist.tracks[2].title)

        val local = playlist.tracks[3]
        assertTrue(local.artists.isEmpty())
        assertNull(local.durationMs)
    }

    @Test
    fun `finds a track list outside the usual path`() {
        val json = """
            {"props":{"pageProps":{"payload":{"playlistV2":{"name":"Moved","trackCount":250,
             "trackList":[{"title":"Song","subtitle":"Artist","duration":{"totalMilliseconds":181000}},
                          {"name":"Other","artists":[{"name":"First"},{"name":"Second"}],"duration":90000}]}}}}}
        """.trimIndent()
        val playlist = SpotifyPlaylistParser.parseJson(json)!!

        assertEquals("Moved", playlist.name)
        assertEquals(250, playlist.reportedTrackCount)
        assertEquals(181000L, playlist.tracks[0].durationMs)
        assertEquals("Other", playlist.tracks[1].title)
        assertEquals(listOf("First", "Second"), playlist.tracks[1].artists)
    }

    @Test
    fun `returns an empty playlist when Spotify lists no tracks`() {
        val html = """<script id="__NEXT_DATA__" type="application/json">
            {"props":{"pageProps":{"state":{"data":{"entity":{"name":"Empty","trackList":[]}}}}}}</script>"""
        val playlist = SpotifyPlaylistParser.parseEmbedHtml(html)!!

        assertEquals("Empty", playlist.name)
        assertTrue(playlist.tracks.isEmpty())
    }

    @Test
    fun `returns null when the page has no playlist data`() {
        assertNull(SpotifyPlaylistParser.parseEmbedHtml("<html><body>Page not found</body></html>"))
        assertNull(
            SpotifyPlaylistParser.parseEmbedHtml(
                """<script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"status":404}}}</script>""",
            ),
        )
        assertNull(SpotifyPlaylistParser.parseEmbedHtml("""<script id="__NEXT_DATA__" type="application/json">{broken</script>"""))
    }
}
