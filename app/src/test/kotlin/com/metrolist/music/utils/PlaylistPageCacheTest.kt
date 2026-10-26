package com.metrolist.music.utils

import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.YouTubeLocale
import com.metrolist.innertube.pages.PlaylistPage
import org.junit.Assert.*
import org.junit.Test

class PlaylistPageCacheTest {
    private val key = PlaylistCacheKey("id", "account-a", YouTubeLocale("US", "en"), false)
    private fun page(id: String = "id") = PlaylistPage(PlaylistItem(id, "Playlist", null, null, null, null, null, null), emptyList(), "next-page", null)

    @Test fun `reentering a playlist restores its header and continuation immediately`() {
        val cache = PlaylistPageCache()
        val snapshot = CachedPlaylistPage(page(), 1000)
        cache.put(key, snapshot)
        assertEquals(snapshot, cache.get(key, 2000))
        assertTrue(cache.get(key, 2000)!!.isFresh(2000))
        assertEquals("next-page", cache.get(key, 2000)!!.page.songsContinuation)
    }

    @Test fun `cached pages cannot cross accounts languages or content filters`() {
        val cache = PlaylistPageCache()
        cache.put(key, CachedPlaylistPage(page(), 1000))
        assertNull(cache.get(key.copy(account = "account-b"), 2000))
        assertNull(cache.get(key.copy(authUser = "other-profile"), 2000))
        assertNull(cache.get(key.copy(dataSyncId = "other-channel"), 2000))
        assertNull(cache.get(key.copy(locale = YouTubeLocale("FR", "fr")), 2000))
        assertNull(cache.get(key.copy(hideVideos = true), 2000))
    }

    @Test fun `stale data remains available for background refresh but expired data is discarded`() {
        val cache = PlaylistPageCache()
        cache.put(key, CachedPlaylistPage(page(), 1000))
        assertFalse(cache.get(key, 62_000)!!.isFresh(62_000))
        assertNull(cache.get(key, 902_000))
    }

    @Test fun `recently used playlists survive eviction of older ones`() {
        val cache = PlaylistPageCache()
        for (id in 0..5) cache.put(key.copy(id = "$id"), CachedPlaylistPage(page("$id"), 1000))
        cache.get(key.copy(id = "0"), 2000)
        cache.put(key.copy(id = "6"), CachedPlaylistPage(page("6"), 1000))
        assertNotNull(cache.get(key.copy(id = "0"), 2000))
        assertNull(cache.get(key.copy(id = "1"), 2000))
    }
}
