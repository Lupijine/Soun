package com.metrolist.music.playback

import android.content.Context
import android.util.AtomicFile
import com.metrolist.music.di.ApplicationScope
import com.metrolist.music.models.PersistPlayerState
import com.metrolist.music.models.PersistQueue
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import javax.inject.Inject
import javax.inject.Singleton

internal data class SavedPlaybackState(val queue: PersistQueue, val automix: PersistQueue?, val player: PersistPlayerState?)

@Singleton
class QueueStateWriter internal constructor(directory: File, private val scope: CoroutineScope) {
    @Inject constructor(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope) : this(context.filesDir, scope)

    private val queueFile = AtomicFile(File(directory, MusicService.PERSISTENT_QUEUE_FILE))
    private val automixFile = AtomicFile(File(directory, MusicService.PERSISTENT_AUTOMIX_FILE))
    private val playerFile = AtomicFile(File(directory, MusicService.PERSISTENT_PLAYER_STATE_FILE))
    private val mutex = Mutex()

    // Acquire the lock in submission order; only immutable snapshots cross to the I/O dispatcher.
    internal fun save(state: SavedPlaybackState) = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    write(queueFile, state.queue)
                    state.automix?.let { write(automixFile, it) }
                    state.player?.let { write(playerFile, it) }
                } catch (error: Exception) {
                    Timber.e(error, "Failed to persist playback state")
                }
            }
        }
    }

    internal suspend fun read(): SavedPlaybackState? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val queue = read<PersistQueue>(queueFile) ?: return@withContext null
            SavedPlaybackState(queue, read<PersistQueue>(automixFile), read<PersistPlayerState>(playerFile))
        }
    }

    private inline fun <reified T> read(file: AtomicFile): T? = try {
        file.openRead().use { stream ->
            ObjectInputStream(stream).use { input ->
                input.readObject() as? T ?: throw java.io.InvalidObjectException("Unexpected playback state type")
            }
        }
    } catch (_: java.io.FileNotFoundException) {
        null
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Timber.w(error, "Discarding unreadable playback file %s", file.baseFile.name)
        file.delete()
        null
    }

    private fun write(file: AtomicFile, value: Serializable) {
        val stream = file.startWrite()
        try {
            // finishWrite must sync and close the underlying stream itself.
            ObjectOutputStream(stream).apply { writeObject(value); flush() }
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }
}
