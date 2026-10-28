package com.metrolist.music.db

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.db.entities.Event
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
    @Test fun `sync preserves a locally chosen cover and still updates remote covers`() = runBlocking {
        val local = PlaylistEntity(id = "local-cover", name = "Before", browseId = "remote", thumbnailUrl = "file:///private/cover.jpg")
        val remote = PlaylistEntity(id = "remote-cover", name = "Before", browseId = "remote", thumbnailUrl = "https://old")
        database.dao.insert(local)
        database.dao.insert(remote)
        val update = com.metrolist.innertube.models.PlaylistItem("remote", "After", null, null, "https://new", null, null, null)
        database.dao.update(local, update)
        database.dao.update(remote, update)
        assertEquals("file:///private/cover.jpg", database.dao.playlist(local.id).first()!!.playlist.thumbnailUrl)
        assertEquals("After", database.dao.playlist(local.id).first()!!.playlist.name)
        assertEquals("https://new", database.dao.playlist(remote.id).first()!!.playlist.thumbnailUrl)
    }

    @Test fun `sync uses the latest cover when its playlist snapshot is stale`() = runBlocking {
        val stale = PlaylistEntity(id = "saved", name = "Before", browseId = "remote", thumbnailUrl = "https://old")
        database.dao.insert(stale)
        database.dao.update(stale.copy(thumbnailUrl = "file:///private/new.png"))
        val remote = com.metrolist.innertube.models.PlaylistItem("remote", "Updated", null, null, "https://remote", null, null, null)
        database.dao.update(stale, remote)
        val saved = database.dao.playlist(stale.id).first()!!.playlist
        assertEquals("file:///private/new.png", saved.thumbnailUrl)
        assertEquals("Updated", saved.name)
    }

    @Test fun `sync keeps a custom YouTube cover while the remote listing catches up`() = runBlocking {
        val saved = PlaylistEntity(id = "custom", name = "Before", browseId = "remote", thumbnailUrl = "https://example/studio_square_thumbnail/cover")
        database.dao.insert(saved)
        val remote = com.metrolist.innertube.models.PlaylistItem("remote", "After", null, null, "https://older", null, null, null)
        database.dao.update(saved, remote)
        assertEquals(saved.thumbnailUrl, database.dao.playlist(saved.id).first()!!.playlist.thumbnailUrl)
    }

    @Test fun `statistics include a song without cover art`() = runBlocking {
        database.dao.insert(SongEntity(id = "content://media/external/audio/media/42", title = "Local song", thumbnailUrl = null))
        database.dao.insert(Event(songId = "content://media/external/audio/media/42", timestamp = firstDate, playTime = 12000))
        val songs = database.dao.mostPlayedSongsStats(firstDate.minusDays(1), limit = -1, toTimeStamp = firstDate.plusDays(1)).first()
        assertEquals(1, songs.size)
        assertNull(songs.single().thumbnailUrl)
    }

}
