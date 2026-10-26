package com.metrolist.music.db

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.db.entities.PlaylistSongMap
import com.metrolist.music.db.entities.SongEntity
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PlaylistColorSourceTest {
    private lateinit var database: InternalDatabase
    private val firstDate = LocalDateTime.of(2026, 1, 1, 0, 0)

    @Before fun open() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), InternalDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun close() { database.close() }

    @Test fun `liked colours follow membership age rather than row order or title`() = runBlocking {
        val newest = SongEntity("new", "AAA", thumbnailUrl = "new-cover", liked = true, likedDate = firstDate.plusDays(1))
        val oldest = SongEntity("old", "ZZZ", thumbnailUrl = "old-cover", liked = true, likedDate = firstDate)
        database.dao.insert(newest)
        database.dao.insert(oldest)
        assertEquals("old-cover", database.dao.likedPlaylistColorSource().first())
        database.dao.update(oldest.copy(liked = false))
        assertEquals("new-cover", database.dao.likedPlaylistColorSource().first())
    }

    @Test fun `download colours use the earliest dated music download`() = runBlocking {
        database.dao.insert(SongEntity("undated", "Legacy", thumbnailUrl = "unknown", isDownloaded = true))
        database.dao.insert(SongEntity("episode", "Podcast", thumbnailUrl = "podcast", isEpisode = true,
            isDownloaded = true, dateDownload = firstDate.minusDays(1)))
        database.dao.insert(SongEntity("new", "AAA", thumbnailUrl = "new-cover", isDownloaded = true, dateDownload = firstDate.plusDays(1)))
        database.dao.insert(SongEntity("old", "ZZZ", thumbnailUrl = "old-cover", isDownloaded = true, dateDownload = firstDate))
        assertEquals("old-cover", database.dao.downloadedPlaylistColorSource().first())
    }

    @Test fun `reordering a playlist does not change its first-added colour source`() = runBlocking {
        database.dao.insert(PlaylistEntity(id = "list", name = "List"))
        database.dao.insert(SongEntity("old", "Old", thumbnailUrl = "old-cover"))
        database.dao.insert(SongEntity("new", "New", thumbnailUrl = "new-cover"))
        val old = PlaylistSongMap(id = 10, playlistId = "list", songId = "old", position = 9)
        val new = PlaylistSongMap(id = 20, playlistId = "list", songId = "new", position = 0)
        database.dao.insert(old)
        database.dao.insert(new)
        assertEquals("old-cover", database.dao.playlistColorSource("list").first())
        database.dao.update(old.copy(position = 20))
        assertEquals("old-cover", database.dao.playlistColorSource("list").first())
        database.dao.delete(old)
        assertEquals("new-cover", database.dao.playlistColorSource("list").first())
    }

    @Test fun `missing artwork on the oldest song keeps the default palette`() = runBlocking {
        database.dao.insert(SongEntity("old", "Old", liked = true, likedDate = firstDate))
        database.dao.insert(SongEntity("new", "New", thumbnailUrl = "new-cover", liked = true, likedDate = firstDate.plusDays(1)))
        assertNull(database.dao.likedPlaylistColorSource().first())
    }
}
