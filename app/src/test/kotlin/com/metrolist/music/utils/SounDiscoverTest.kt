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
}
