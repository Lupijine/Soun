package com.metrolist.music.playback

import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.PersistPlayerState
import com.metrolist.music.models.PersistQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.io.ObjectOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class QueueStateWriterTest {
    @get:Rule val directory = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @After fun close() { scope.cancel() }

    private fun snapshot(title: String, items: List<MediaMetadata> = emptyList()) = SavedPlaybackState(
        PersistQueue(title, items, 0, 1234L),
        PersistQueue("automix", emptyList(), 0, 0L),
        PersistPlayerState(false, 0, false, 0.75f, 1234L, 0, 3),
    )

    @Test fun `saving never serializes on caller and later snapshots cannot be overwritten by older writes`() = runBlocking {
        val writer = QueueStateWriter(directory.root, scope)
        val caller = Thread.currentThread()
        val worker = AtomicReference<Thread>()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = writer.save(snapshot("old", ProbeItems {
            worker.set(Thread.currentThread())
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }))
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertNotSame(caller, worker.get())
            val expected = snapshot("new")
            val second = writer.save(expected)
            assertFalse(second.isCompleted)
            release.countDown()
            first.join()
            second.join()
            assertSavedState(expected, writer.read())
        } finally { release.countDown() }
    }

    @Test fun `failed serialization keeps the previous queue readable`() = runBlocking {
        val writer = QueueStateWriter(directory.root, scope)
        val expected = snapshot("saved")
        writer.save(expected).join()
        writer.save(snapshot("broken", ProbeItems { throw IOException("disk serialization failure") })).join()
        assertSavedState(expected, writer.read())
    }

    @Test fun `corrupt optional player state does not discard a valid queue`() = runBlocking {
        val writer = QueueStateWriter(directory.root, scope)
        val expected = snapshot("keep queue")
        writer.save(expected).join()
        directory.root.resolve(MusicService.PERSISTENT_PLAYER_STATE_FILE).writeText("broken")
        val restored = writer.read()!!
        assertEquals(expected.queue.title, restored.queue.title)
        assertNotNull(restored.automix)
        assertNull(restored.player)
        assertTrue(directory.root.resolve(MusicService.PERSISTENT_QUEUE_FILE).exists())
        assertEquals(expected.queue.title, writer.read()!!.queue.title)
    }

    @Test fun `legacy atomic backup is recovered when the base file is missing`() = runBlocking {
        val writer = QueueStateWriter(directory.root, scope)
        val expected = snapshot("recover backup")
        writer.save(expected).join()
        val queue = directory.root.resolve(MusicService.PERSISTENT_QUEUE_FILE)
        assertTrue(queue.renameTo(java.io.File(queue.path + ".bak")))
        assertSavedState(expected, writer.read())
    }

    private fun assertSavedState(expected: SavedPlaybackState, actual: SavedPlaybackState?) {
        assertNotNull(actual)
        actual!!
        // Java serialization recreates Kotlin object instances; compare their type and stored fields.
        assertEquals(expected.queue.queueType.javaClass, actual.queue.queueType.javaClass)
        assertEquals(expected.queue, actual.queue.copy(queueType = expected.queue.queueType))
        assertEquals(expected.automix!!.queueType.javaClass, actual.automix!!.queueType.javaClass)
        assertEquals(expected.automix, actual.automix.copy(queueType = expected.automix.queueType))
        assertEquals(expected.player, actual.player)
    }

    private class ProbeItems(@Transient private val onWrite: () -> Unit) : ArrayList<MediaMetadata>() {
        private fun writeObject(output: ObjectOutputStream) {
            onWrite()
            output.defaultWriteObject()
        }
    }
}
