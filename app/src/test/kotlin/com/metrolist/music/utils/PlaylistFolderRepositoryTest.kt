package com.metrolist.music.utils

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class PlaylistFolderRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var repository: PlaylistFolderRepository

    @Before fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        store = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "folders.preferences_pb") }
        repository = PlaylistFolderRepository(store)
    }

    @After fun tearDown() {
        scope.cancel()
    }

    @Test fun `create rename move and delete persist through the store`() = runBlocking {
        assertTrue(repository.current().isEmpty())

        val chill = repository.create("Chill")
        val gym = repository.create("Gym", listOf("p1"))
        repository.move("p2", chill)
        repository.move("p1", chill)
        repository.rename(gym, "Workout")

        val stored = PlaylistFolders.decode(store.data.first()[PlaylistFoldersKey])
        assertEquals(listOf("Chill", "Workout"), stored.map { it.name })
        assertEquals(listOf("p2", "p1"), stored.first { it.id == chill }.playlistIds)
        assertTrue(stored.first { it.id == gym }.playlistIds.isEmpty())
        assertEquals(stored, repository.folders.first())

        repository.move("p2", null)
        repository.delete(chill)
        val afterDelete = repository.current()
        assertEquals(listOf(gym), afterDelete.map { it.id })
        assertTrue(PlaylistFolders.filedPlaylistIds(afterDelete).isEmpty())

        repository.delete(gym)
        assertTrue(repository.current().isEmpty())
        assertEquals(null, store.data.first()[PlaylistFoldersKey])
    }
}
