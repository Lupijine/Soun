package com.metrolist.music.lyrics

import com.metrolist.lrclib.models.Track
import com.metrolist.lrclib.models.matchingTracks
import com.metrolist.kugou.KuGou
import com.metrolist.kugou.models.Keyword
import com.metrolist.kugou.models.SearchLyricsResponse
import com.metrolist.paxsenix.Paxsenix
import com.metrolist.paxsenix.models.SearchResult
import org.junit.Assert.*
import org.junit.Test

class LyricsMatchTest {
    @Test fun `same duration and title cannot compensate for a different artist`() {
        assertNull(LyricsMatch.score("Hello", "Adele", 295, "Hello", "Lionel Richie", 295.0))
    }

    @Test fun `same artist and duration cannot compensate for a different song`() {
        assertNull(LyricsMatch.score("Moon, Moon", "Sofia Rotaru", 276, "Moon", "Sofia Rotaru", 276.0))
        assertNull(LyricsMatch.score("Love", "Artist", 200, "Love Again", "Artist", 200.0))
    }

    @Test fun `unknown duration still requires both identity fields`() {
        assertNotNull(LyricsMatch.score("Space Song", "Beach House", -1, "Space Song", "Beach House", null))
        assertNull(LyricsMatch.score("Space Song", "Beach House", -1, "Another Song", "Beach House", null))
        assertNull(LyricsMatch.score("Space Song", "", -1, "Space Song", "Beach House", null))
    }

    @Test fun `only promotional annotations are stripped`() {
        assertEquals("Song", LyricsMatch.cleanTitle("Song (Official Music Video) [4K]"))
        for (title in listOf("Song (Live)", "Song (Radio Edit)", "Song (Remix)", "Song (Acoustic)", "Song (Part II)", "Song (When We Meet)")) {
            assertEquals(title, LyricsMatch.cleanTitle(title))
            assertFalse(LyricsMatch.titlesMatch(title, "Song"))
        }
    }

    @Test fun `unicode punctuation accents and topic suffix do not hide the right song`() {
        assertTrue(LyricsMatch.titlesMatch("Don’t Stop (Official Audio)", "Don't Stop"))
        assertTrue(LyricsMatch.artistsMatch("Beyoncé - Topic", "Beyonce"))
        assertTrue(LyricsMatch.titlesMatch("Серые глаза", "СЕРЫЕ ГЛАЗА"))
        assertTrue(LyricsMatch.titlesMatch("قَلْبِي", "قلبي"))
    }

    @Test fun `a shared guest artist is not a lead artist match`() {
        assertTrue(LyricsMatch.artistsMatch("Artist, Guest", "Artist feat. Guest"))
        assertFalse(LyricsMatch.artistsMatch("Artist, Guest", "Other Artist, Guest"))
        assertFalse(LyricsMatch.artistsMatch("Joji", "Joji Tribute Band"))
        assertFalse(LyricsMatch.artistsMatch("Artist, Guest", "Artist, Other guest"))
    }

    @Test fun `duration tolerance uses seconds without truncation`() {
        assertNotNull(LyricsMatch.score("Song", "Artist", 200, "Song", "Artist", 205.0, 5.0))
        assertNull(LyricsMatch.score("Song", "Artist", 200, "Song", "Artist", 205.1, 5.0))
        assertNull(LyricsMatch.score("Song", "Artist", 200, "Song", "Artist", 400.0))
    }

    @Test fun `LRCLIB rejects wrong synced result even when its duration is closer`() {
        val wrong = Track(1, "Hello", "Lionel Richie", 295.0, null, "wrong")
        val right = Track(2, "Hello", "Adele", 297.0, "right", null)
        assertEquals(listOf(right), listOf(wrong, right).matchingTracks(295, "Hello", "Adele"))
        assertTrue(listOf(wrong).matchingTracks(-1, "Hello", "Adele").isEmpty())
    }

    @Test fun `KuGou validates keyword fallback candidates and converts milliseconds once`() {
        fun candidate(id: Long, artist: String, duration: Long) = SearchLyricsResponse.Candidate(id, "", duration, "key", "Hello", artist)
        val right = candidate(2, "Adele", 295000)
        val missingIdentity = SearchLyricsResponse.Candidate(4, "", 295000, "key")
        assertEquals(listOf(right), KuGou.matchingCandidates(listOf(candidate(1, "Other", 295000), right,
            candidate(3, "Adele", 200000), missingIdentity), Keyword("Hello", "Adele"), 295))
    }

    @Test fun `Paxsenix ranks seconds and rejects unrelated search results`() {
        val exact = SearchResult("right", trackName = "Hello", artistName = "Adele", duration = 295)
        val close = exact.copy(id = "close", duration = 299)
        val wrong = exact.copy(id = "wrong", artistName = "Other")
        assertEquals(listOf(exact, close), Paxsenix.scoreAndFilterResults(listOf(wrong, close, exact), "Hello", "Adele", 295).map { it.first })
    }
}
