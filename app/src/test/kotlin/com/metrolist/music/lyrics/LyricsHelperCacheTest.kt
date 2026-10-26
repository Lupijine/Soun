package com.metrolist.music.lyrics

import android.app.Application
import com.metrolist.music.db.entities.LyricsEntity
import android.content.Context
import android.net.ConnectivityManager
import android.util.LruCache
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.NetworkConnectivityObserver
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class LyricsHelperCacheTest {
    private lateinit var observer: NetworkConnectivityObserver
    private lateinit var helper: LyricsHelper
    private lateinit var cache: LruCache<LyricsRequest, List<LyricsResult>>
    private val metadata = MediaMetadata("id", "Song", listOf(MediaMetadata.Artist(null, "Artist")), 180)
    private val request = LyricsRequest("id", "Song", "Artist", 180, null)
    private val saved = listOf(LyricsResult("LrcLib", "[00:01.00]Cached lyrics"))

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).setActiveNetworkInfo(null)
        observer = NetworkConnectivityObserver(context)
        helper = LyricsHelper(context, observer)
        cache = ReflectionHelpers.getField(helper, "cache")
        cache.put(request, saved)
        cache.put(request.copy(search = true), saved)
    }

    @After fun close() { observer.unregister() }

    @Test fun `verification persists across helpers but is invalidated by changed lyrics or metadata`() {
        val entry = LyricsEntity("id", "Verified lyrics", "LrcLib")
        helper.markStoredVerified(metadata, entry)
        assertTrue(helper.isStoredVerified(metadata, entry))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val reopened = LyricsHelper(context, observer)
        assertTrue(reopened.isStoredVerified(metadata, entry))
        assertFalse(reopened.isStoredVerified(metadata.copy(title = "Everywhere"), entry))
        assertFalse(reopened.isStoredVerified(metadata, entry.copy(lyrics = "Different lyrics")))
        assertFalse(reopened.isStoredVerified(metadata, entry.copy(provider = "BetterLyrics")))
    }

    @Test fun `refetch bypasses cached lyrics and invalidates cached search results`() = runBlocking {
        assertEquals(LyricsLoadStatus.FOUND, helper.getLyrics(metadata).status)
        assertEquals(LyricsLoadStatus.OFFLINE, helper.getLyrics(metadata, forceRefresh = true).status)
        val results = mutableListOf<LyricsResult>()
        helper.getAllLyrics("id", "Song", "Artist", 180) { results += it }
        assertTrue(results.isEmpty())
    }

    @Test fun `editing search metadata does not reuse the previous query for the same media ID`() = runBlocking {
        val results = mutableListOf<LyricsResult>()
        helper.getAllLyrics("id", "Song", "Artist", 180) { results += it }
        assertEquals(saved, results)
        results.clear()
        helper.getAllLyrics("id", "Different song", "Artist", 180) { results += it }
        helper.getAllLyrics("id", "Song", "Different artist", 180) { results += it }
        helper.getAllLyrics("id", "Song", "Artist", 360) { results += it }
        assertTrue(results.isEmpty())
        assertEquals(LyricsLoadStatus.OFFLINE, helper.getLyrics(metadata.copy(title = "Different song")).status)
    }
}
