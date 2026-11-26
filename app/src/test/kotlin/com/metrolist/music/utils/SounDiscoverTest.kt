package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class SounDiscoverTest {
    @Test
    fun `reads the page from a fenced reply with prose around it`() {
        val reply =
            """
            Here is your page:
            ```json
            {"intro": "Warm electronics for tonight",
             "sections": [
               {"title": "After dark", "reason": "You play Rone late.", "songs": [
                 {"title": "Bye Bye Macadam", "artist": "Rone", "why": "the start of it all"},
                 {"title": "", "artist": "Nobody"},
                 {"title": "Kerala", "artist": "Bonobo"}
               ]},
               {"title": "Empty", "songs": []}
             ]}
            ```
            Enjoy!
            """.trimIndent()
        val page = parseCuratedPage(reply)
        assertNotNull(page)
        assertEquals("Warm electronics for tonight", page!!.intro)
        assertEquals(1, page.sections.size)
        assertEquals(listOf("Bye Bye Macadam", "Kerala"), page.sections[0].songs.map { it.title })
        assertEquals("", page.sections[0].songs[1].why)
    }

    @Test
    fun `no JSON, no page`() {
        assertNull(parseCuratedPage("Sorry, I can't help with that."))
        assertNull(parseCuratedPage("""{"sections": []}"""))
    }

    @Test
    fun `matches the song, not just the first result`() {
        val candidates =
            listOf(
                SongCandidate("Kerala (Live)", listOf("Some Cover Band"), isVideo = true),
                SongCandidate("Something Else", listOf("Bonobo"), isVideo = false),
                SongCandidate("Kerala", listOf("Bonobo"), isVideo = false),
            )
        assertEquals(2, bestMatch("Kerala", "Bonobo", candidates))
    }

    @Test
    fun `ignores accents, case and extras in titles and splits collaborations`() {
        val candidates = listOf(SongCandidate("Friend of All That Lives - Original Mix", listOf("Dominik Eulberg", "Gabriel Ananda"), isVideo = false))
        assertEquals(0, bestMatch("friend of all that lives", "Dominik Eulberg & Gabriel Ananda", candidates))
        assertEquals("cafe del mar", normalizeForMatch("Café del Mar (Energy 52 Remix)"))
    }

    @Test
    fun `a pick YouTube does not have finds nothing`() {
        val candidates = listOf(SongCandidate("Another Song", listOf("Rone"), isVideo = false))
        assertEquals(-1, bestMatch("Made Up Title", "Rone", candidates))
    }

    @Test
    fun `next refresh is the next chosen time, tomorrow once both have passed`() {
        val times = listOf(LocalTime.of(7, 30), LocalTime.of(19, 30))
        assertEquals(LocalDateTime.of(2026, 11, 25, 19, 30), nextRefreshAt(LocalDateTime.of(2026, 11, 25, 18, 42), times))
        assertEquals(LocalDateTime.of(2026, 11, 26, 7, 30), nextRefreshAt(LocalDateTime.of(2026, 11, 25, 20, 0), times))
        assertEquals(LocalDateTime.of(2026, 11, 26, 7, 30), nextRefreshAt(LocalDateTime.of(2026, 11, 26, 6, 0), times.take(1)))
        assertNull(nextRefreshAt(LocalDateTime.of(2026, 11, 25, 18, 42), emptyList()))
    }

    @Test
    fun `reads the text and Google's search suggestions from a Gemini reply`() {
        val raw =
            """
            {"candidates": [{"content": {"role": "model", "parts": [
                {"text": "{\"intro\": \"x\", "},
                {"text": "\"sections\": []}"}
              ]},
              "groundingMetadata": {"searchEntryPoint": {"renderedContent": "<div>chips</div>"}}}]}
            """.trimIndent()
        val reply = GeminiDiscover.readReply(raw)
        assertNotNull(reply)
        assertEquals("{\"intro\": \"x\", \"sections\": []}", reply!!.text)
        assertEquals("<div>chips</div>", reply.searchSuggestionsHtml)
        assertNull(GeminiDiscover.readReply("""{"candidates": []}"""))
    }

    @Test
    fun `reads what Gemini's error answers mean`() {
        assertEquals(DiscoverError.BAD_MODEL, discoverErrorFor(404, "This model models/gemini-2.5-flash is no longer available to new users.", webSearch = true))
        assertEquals(DiscoverError.SEARCH_UNAVAILABLE, discoverErrorFor(400, "Search Grounding is not supported on the free tier.", webSearch = true))
        assertEquals(DiscoverError.SEARCH_UNAVAILABLE, discoverErrorFor(429, "Quota exceeded for metric: search_grounding_requests, limit: 0", webSearch = true))
        assertEquals(DiscoverError.QUOTA, discoverErrorFor(429, "Quota exceeded for metric: search_grounding_requests, limit: 0", webSearch = false))
        assertEquals(DiscoverError.BAD_KEY, discoverErrorFor(400, "API key not valid. Please pass a valid API key.", webSearch = true))
        assertEquals(DiscoverError.SERVICE, discoverErrorFor(503, "The model is overloaded.", webSearch = true))
    }

    @Test
    fun `reads typed sections with a seed song and albums`() {
        val reply =
            """
            {"intro": "x", "sections": [
              {"type": "because", "title": "Because you played Watermark", "seed": {"title": "Watermark", "artist": "Djrum"},
               "songs": [{"title": "A", "artist": "B"}]},
              {"type": "albums", "title": "Albums", "albums": [{"title": "Tohu Bohu", "artist": "Rone", "why": "w"}]},
              {"type": "mix", "title": "Rone × Bonobo", "songs": [{"title": "C", "artist": "D"}]},
              {"type": "albums", "title": "No albums in it", "songs": [{"title": "E", "artist": "F"}]}
            ]}
            """.trimIndent()
        val page = parseCuratedPage(reply)!!
        assertEquals(listOf(DiscoverKind.BECAUSE, DiscoverKind.ALBUMS, DiscoverKind.MIX), page.sections.map { it.kind })
        assertEquals("Watermark", page.sections[0].seed?.title)
        assertEquals(listOf("Tohu Bohu"), page.sections[1].albums.map { it.title })
        assertEquals(emptyList<CuratedSong>(), page.sections[1].songs)
        assertEquals(DiscoverKind.SONGS, discoverKindOf("something else"))
    }

    @Test
    fun `finds a music video titled Artist - Title from another channel`() {
        val candidates = listOf(SongCandidate("Bonobo - Kerala (Official Video)", listOf("Ninja Tune"), isVideo = true))
        assertEquals(0, bestMatch("Kerala", "Bonobo", candidates, requireArtist = true))
    }

    @Test
    fun `accepts a title written a little differently when the artist matches`() {
        assertEquals(0, bestMatch("The Golden Age", "Beck", listOf(SongCandidate("Golden Age, The", listOf("Beck"), isVideo = false))))
        assertEquals(-1, bestMatch("The Golden Age", "Beck", listOf(SongCandidate("Golden Age, The", listOf("Someone"), isVideo = false))))
    }

    @Test
    fun `albums need the right artist`() {
        assertEquals(-1, bestMatch("Kerala", "Bonobo", listOf(SongCandidate("Kerala", listOf("Someone Else"), isVideo = false)), requireArtist = true))
    }
}
