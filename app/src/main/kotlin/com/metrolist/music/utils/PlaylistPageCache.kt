package com.metrolist.music.utils

import android.os.SystemClock
import com.metrolist.innertube.models.YouTubeLocale
import com.metrolist.innertube.pages.PlaylistPage
import javax.inject.Inject
import javax.inject.Singleton

internal data class PlaylistCacheKey(
    val id: String,
    val account: String?,
    val locale: YouTubeLocale,
    val hideVideos: Boolean,
    val authUser: String = "",
    val dataSyncId: String? = null,
)
internal data class CachedPlaylistPage(val page: PlaylistPage, val fetchedAt: Long) {
    fun isFresh(now: Long = SystemClock.elapsedRealtime()) = now - fetchedAt in 0..60_000L
}

/** Recent navigation snapshots, scoped to the account, language and content filter. */
@Singleton
class PlaylistPageCache @Inject constructor() {
    private val pages = LinkedHashMap<PlaylistCacheKey, CachedPlaylistPage>(8, 0.75f, true)

    @Synchronized
    internal fun get(key: PlaylistCacheKey, now: Long = SystemClock.elapsedRealtime()): CachedPlaylistPage? {
        val entry = pages[key] ?: return null
        if (now - entry.fetchedAt !in 0..900_000L) {
            pages.remove(key)
            return null
        }
        return entry
    }

    @Synchronized
    internal fun put(key: PlaylistCacheKey, entry: CachedPlaylistPage) {
        pages[key] = entry.copy(page = entry.page.copy(songs = entry.page.songs.toList()))
        while (pages.size > 6 || pages.values.sumOf { it.page.songs.size } > 4_000) {
            pages.remove(pages.keys.first())
        }
    }
}
