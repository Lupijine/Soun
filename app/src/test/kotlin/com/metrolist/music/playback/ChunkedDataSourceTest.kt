package com.metrolist.music.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChunkedDataSourceTest {
    /** A server that answers byte ranges of [content], like googlevideo does; one connection per request. */
    private class RangeServer(
        private val content: ByteArray,
        private val sendContentRange: Boolean = true,
        /** Most bytes one response carries, like a server that cuts long responses short. */
        private val maxResponseBytes: Long = Long.MAX_VALUE,
        /** For each new connection in turn: drop it after this many bytes (0: before any data). */
        failAfterBytes: List<Int> = emptyList(),
    ) : DataSource.Factory {
        val requests = mutableListOf<DataSpec>()
        private val failures = ArrayDeque(failAfterBytes)

        override fun createDataSource(): DataSource = Connection(failures.removeFirstOrNull())

        inner class Connection(private val failAfter: Int?) : DataSource {
            private var current: DataSpec? = null
            private var pos = 0L
            private var end = 0L
            private var sent = 0

            override fun addTransferListener(transferListener: TransferListener) = Unit

            override fun open(dataSpec: DataSpec): Long {
                requests += dataSpec
                if (dataSpec.position >= content.size && dataSpec.position > 0) {
                    throw HttpDataSource.InvalidResponseCodeException(416, null, null, emptyMap(), dataSpec, ByteArray(0))
                }
                current = dataSpec
                pos = dataSpec.position
                end =
                    if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
                        content.size.toLong()
                    } else {
                        minOf(content.size.toLong(), dataSpec.position + dataSpec.length)
                    }
                if (end - pos > maxResponseBytes) end = pos + maxResponseBytes
                return end - pos
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (failAfter != null && sent >= failAfter) throw IOException("connection dropped")
                if (pos >= end) return C.RESULT_END_OF_INPUT
                var n = minOf(length.toLong(), end - pos).toInt()
                if (failAfter != null) n = minOf(n, failAfter - sent)
                System.arraycopy(content, pos.toInt(), buffer, offset, n)
                pos += n
                sent += n
                return n
            }

            override fun getUri(): Uri? = current?.uri

            override fun getResponseHeaders(): Map<String, List<String>> {
                val spec = current ?: return emptyMap()
                if (!sendContentRange) return emptyMap()
                return mapOf("content-range" to listOf("bytes ${spec.position}-${end - 1}/${content.size}"))
            }

            override fun close() {
                current = null
            }
        }
    }

    private val content = ByteArray(2_500) { (it % 251).toByte() }

    private fun spec(position: Long = 0L, hint: RangeChunkHint? = RangeChunkHint(maxChunkBytes = 1_000L)) =
        DataSpec.Builder()
            .setUri(Uri.parse("https://rr1.googlevideo.com/videoplayback"))
            .setPosition(position)
            .setCustomData(hint)
            .build()

    private fun DataSource.readAll(): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(333)
        while (true) {
            val n = read(buffer, 0, buffer.size)
            if (n == C.RESULT_END_OF_INPUT) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    @Test
    fun `reads a long stream in hinted ranges and learns its length`() {
        val server = RangeServer(content)
        val source = ChunkedDataSource(server, onlyWhenHinted = true)

        assertEquals(2_500L, source.open(spec()))
        assertArrayEquals(content, source.readAll())
        source.close()

        assertEquals(listOf(0L, 1_000L, 2_000L), server.requests.map { it.position })
        assertEquals(listOf(1_000L, 1_000L, 500L), server.requests.map { it.length })
    }

    @Test
    fun `starts from the requested position`() {
        val server = RangeServer(content)
        val source = ChunkedDataSource(server, onlyWhenHinted = true)

        assertEquals(1_900L, source.open(spec(position = 600L)))
        assertArrayEquals(content.copyOfRange(600, 2_500), source.readAll())
        assertEquals(listOf(600L, 1_600L), server.requests.map { it.position })
    }

    @Test
    fun `without a content range it reads on until the server has nothing more`() {
        val server = RangeServer(content, sendContentRange = false)
        val source = ChunkedDataSource(server, onlyWhenHinted = true)

        assertEquals(C.LENGTH_UNSET.toLong(), source.open(spec()))
        assertArrayEquals(content, source.readAll())
        // Three ranges with data, then one past the end that the server refuses.
        assertEquals(listOf(0L, 1_000L, 2_000L, 2_500L), server.requests.map { it.position })
    }

    @Test
    fun `a server that cuts responses short is read to the end`() {
        val server = RangeServer(content, sendContentRange = false, maxResponseBytes = 300L)
        val source = ChunkedDataSource(server, onlyWhenHinted = true)

        source.open(spec())
        assertArrayEquals(content, source.readAll())
    }

    @Test
    fun `the resolver's size is used when the server sends none`() {
        val server = RangeServer(content, sendContentRange = false, maxResponseBytes = 300L)
        val source = ChunkedDataSource(server, onlyWhenHinted = true)

        assertEquals(2_100L, source.open(spec(position = 400L, hint = RangeChunkHint(1_000L, contentLength = 2_500L))))
        assertArrayEquals(content.copyOfRange(400, 2_500), source.readAll())
    }

    @Test
    fun `the source's own range size applies when the client sets no limit`() {
        val server = RangeServer(content)
        val source = ChunkedDataSource(server, chunkSizeBytes = 800L, onlyWhenHinted = true)

        source.open(spec(hint = RangeChunkHint()))
        assertArrayEquals(content, source.readAll())
        assertEquals(listOf(800L, 800L, 800L, 100L), server.requests.map { it.length })
    }

    @Test
    fun `a dropped connection keeps what arrived and carries on`() {
        val server = RangeServer(content, failAfterBytes = listOf(400))
        val source = ChunkedDataSource(server, onlyWhenHinted = true)

        assertEquals(2_500L, source.open(spec()))
        assertArrayEquals(content, source.readAll())
        assertEquals(400L, server.requests[1].position)
    }

    @Test
    fun `a range that fails before any data is retried on a fresh connection`() {
        val server = RangeServer(content, failAfterBytes = listOf(1_000_000, 0, 0))
        val source = ChunkedDataSource(server, onlyWhenHinted = true)

        source.open(spec())
        assertArrayEquals(content, source.readAll())
        // The second range failed twice before it came through.
        assertEquals(listOf(0L, 1_000L, 1_000L, 1_000L, 2_000L), server.requests.map { it.position })
    }

    @Test
    fun `unhinted requests pass straight through when only hinted ones are chunked`() {
        val server = RangeServer(content)
        val source = ChunkedDataSource(server, onlyWhenHinted = true)

        source.open(spec(hint = null))
        assertArrayEquals(content, source.readAll())
        assertEquals(1, server.requests.size)
        assertEquals(C.LENGTH_UNSET.toLong(), server.requests.single().length)
    }

    @Test
    fun `resolved streams keep their full length and carry the range size`() {
        val request = DataSpec.Builder().setUri(Uri.parse("song://id")).setPosition(42L).setKey("id").build()
        val bounded =
            request.withResolvedStream(
                CachedStreamUrl(
                    url = "https://rr1.googlevideo.com/videoplayback?id=1",
                    requestHeaders = mapOf("User-Agent" to "vr"),
                    clientName = "ANDROID_VR",
                    requireBoundedRange = true,
                    rangeChunkSizeBytes = 512L * 1024,
                    useRangeChunks = true,
                    contentLength = 99_000_000L,
                ),
            )
        assertEquals(C.LENGTH_UNSET.toLong(), bounded.length)
        assertEquals(42L, bounded.position)
        assertEquals("id", bounded.key)
        assertEquals("https://rr1.googlevideo.com/videoplayback?id=1", bounded.uri.toString())
        assertEquals("vr", bounded.httpRequestHeaders["User-Agent"])
        assertEquals(RangeChunkHint(512L * 1024, 99_000_000L), bounded.customData)

        val open =
            request.withResolvedStream(
                CachedStreamUrl("https://example.com/a", emptyMap(), "VISIONOS", rangeChunkSizeBytes = 1_048_576L),
            )
        assertEquals(RangeChunkHint(maxChunkBytes = null, contentLength = null), open.customData)
        assertNull(request.customData)
    }
}
