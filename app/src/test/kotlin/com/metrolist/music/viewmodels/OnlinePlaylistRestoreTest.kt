package com.metrolist.music.viewmodels

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.PlaylistPage
import com.metrolist.music.db.InternalDatabase
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.utils.CachedPlaylistPage
import com.metrolist.music.utils.PlaylistCacheKey
import com.metrolist.music.utils.PlaylistPageCache
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class OnlinePlaylistRestoreTest {
    @Test fun `a recreated playlist screen has its songs and header before any asynchronous loading`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val room = Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java).allowMainThreadQueries().build()
        try {
            val header = PlaylistItem("cached-list", "CHEYLL VIBES", null, null, null, null, null, null)
            val song = SongItem("cached-song", "Song", emptyList(), thumbnail = "")
            val cache = PlaylistPageCache()
            cache.put(PlaylistCacheKey("cached-list", YouTube.cookie, YouTube.locale, false),
                CachedPlaylistPage(PlaylistPage(header, listOf(song), null, null), SystemClock.elapsedRealtime()))
            val model = OnlinePlaylistViewModel(context, SavedStateHandle(mapOf("playlistId" to "cached-list")), MusicDatabase(room), cache)
            assertFalse(model.isLoading.value)
            assertEquals(header, model.playlist.value)
            assertEquals(listOf(song), model.playlistSongs.value)
        } finally {
            room.close()
        }
    }
}
