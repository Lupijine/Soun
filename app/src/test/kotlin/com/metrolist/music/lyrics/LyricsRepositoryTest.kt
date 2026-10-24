package com.metrolist.music.lyrics

import android.content.Context
import android.net.ConnectivityManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.db.InternalDatabase
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.NetworkConnectivityObserver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class LyricsRepositoryTest {
    private lateinit var room: InternalDatabase
    private lateinit var database: MusicDatabase
    private lateinit var repository: LyricsRepository
    private lateinit var observer: NetworkConnectivityObserver
    private val metadata = MediaMetadata("offline-song", "Song", emptyList(), 120)

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).setActiveNetworkInfo(null)
        observer = NetworkConnectivityObserver(context)
        assertFalse(observer.isCurrentlyConnected())
        room = Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java).allowMainThreadQueries().build()
        database = MusicDatabase(room)
        repository = LyricsRepository(database, LyricsHelper(context, observer))
    }

    @After
    fun close() { observer.unregister(); room.close() }

    @Test
    fun `provider lyrics persist and can be read offline`() = runBlocking {
        val fetched = LyricsWithProvider("[00:01.00]Saved line", "LrcLib")
        repository.retain(metadata.id, fetched)
        assertEquals(LyricsLoadStatus.FOUND, repository.load(metadata))
        assertEquals(fetched.lyrics, database.lyrics(metadata.id).first()?.lyrics)
        assertEquals("LrcLib", database.lyrics(metadata.id).first()?.provider)
    }

    @Test
    fun `late provider responses cannot overwrite manual edits or translations`() = runBlocking {
        val manual = LyricsEntity(metadata.id, "My lyrics", "Manual", "Translation", "fr", "Literal")
        database.upsert(manual)
        repository.retain(metadata.id, LyricsWithProvider("Different provider lyrics", "LrcLib"))
        repository.retain(metadata.id, LyricsWithProvider(LyricsEntity.LYRICS_NOT_FOUND, ""))
        assertEquals(manual, database.lyrics(metadata.id).first())
    }

    @Test
    fun `saved lyrics and translations remain available offline`() = runBlocking {
        val saved = LyricsEntity(metadata.id, "[00:01.00]Hello", "Manual", "Bonjour", "fr", "Literal")
        database.upsert(saved)
        assertEquals(LyricsLoadStatus.FOUND, repository.load(metadata))
        assertEquals(saved, database.lyrics(metadata.id).first())
    }

    @Test
    fun `offline misses do not create permanent negative cache entries`() = runBlocking {
        assertEquals(LyricsLoadStatus.OFFLINE, repository.load(metadata))
        assertNull(database.lyrics(metadata.id).first())
        assertEquals(LyricsLoadStatus.OFFLINE, repository.load(metadata))
    }

    @Test
    fun `legacy not-found entries remain retryable`() = runBlocking {
        database.upsert(LyricsEntity(metadata.id, LyricsEntity.LYRICS_NOT_FOUND))
        assertEquals(LyricsLoadStatus.OFFLINE, repository.load(metadata))
        assertFalse(usableLyrics(database.lyrics(metadata.id).first()?.lyrics))
    }
}
