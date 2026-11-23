/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException

/**
 * How the [ChunkedDataSource] further down the chain should read a resolved stream. Carried in
 * [DataSpec.customData], which the cache layers copy onto the requests they send on.
 *
 * [maxChunkBytes] is the largest range the server answers for this stream (null: the source's
 * own size), and [contentLength] the size of the whole stream when the resolver knows it.
 */
data class RangeChunkHint(
    val maxChunkBytes: Long? = null,
    val contentLength: Long? = null,
)

/**
 * Reads a stream as a series of small ranged requests instead of one long one.
 *
 * YouTube slows down, cuts short or drops a single open-ended request for a large file, and
 * some of its clients only answer bounded ranges at all, so a long track (an hour or more)
 * would get stuck partway. This source asks for one range at a time (`Range: bytes=a-b`) and
 * joins the pieces into one continuous stream for the caller, so the player, the cache and the
 * downloader see no difference.
 *
 * Requests carrying a [RangeChunkHint] are always chunked; with [onlyWhenHinted] set, anything
 * else (not a resolved YouTube stream) passes straight through to [upstream].
 *
 * The total size comes from the first range's `Content-Range`, or else from the hint, so the
 * caller learns the real length up front. A range that comes back shorter than asked is not
 * taken as the end: reading carries on from where it stopped, and only an empty range or a
 * 416 (range not satisfiable) past the last byte ends a stream of unknown size.
 */
class ChunkedDataSource(
    private val upstream: DataSource,
    private val chunkSizeBytes: Long = DEFAULT_CHUNK_SIZE_BYTES,
    private val onlyWhenHinted: Boolean = false,
) : DataSource {
    private var spec: DataSpec? = null
    private var passThrough = false
    private var currentChunkSize = chunkSizeBytes

    /** True once the remaining size is known from the caller or the server, not only guessed. */
    private var lengthConfirmed = false

    /** Bytes still wanted by the caller, or [C.LENGTH_UNSET] if open-ended and still unknown. */
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()

    /** Offset from the start of [spec] where the current chunk begins. */
    private var chunkStart = 0L
    private var chunkLength = 0L
    private var chunkRead = 0L
    private var upstreamOpen = false
    private var ended = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec
        val hint = dataSpec.customData as? RangeChunkHint
        if (hint == null && onlyWhenHinted) {
            passThrough = true
            upstreamOpen = true
            return upstream.open(dataSpec)
        }
        passThrough = false
        currentChunkSize =
            hint?.maxChunkBytes?.takeIf { it > 0L }?.let { minOf(it, chunkSizeBytes) } ?: chunkSizeBytes
        bytesRemaining = dataSpec.length
        lengthConfirmed = dataSpec.length != C.LENGTH_UNSET.toLong()
        chunkStart = 0L
        ended = false
        if (!openChunk()) {
            ended = true
            return 0L
        }
        if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            val serverTotal = totalLength(upstream.responseHeaders)
            if (serverTotal != null) {
                bytesRemaining = (serverTotal - dataSpec.position).coerceAtLeast(0L)
                lengthConfirmed = true
            } else {
                hint?.contentLength?.takeIf { it > dataSpec.position }?.let { bytesRemaining = it - dataSpec.position }
            }
        }
        return bytesRemaining
    }

    /** Opens the next chunk; returns false if there is nothing left to read. */
    private fun openChunk(): Boolean {
        val dataSpec = spec ?: return false
        val wanted =
            if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
                currentChunkSize
            } else {
                minOf(currentChunkSize, bytesRemaining)
            }
        if (wanted <= 0L) return false
        val chunkSpec = dataSpec.subrange(chunkStart, wanted)
        try {
            upstream.open(chunkSpec)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            runCatching { upstream.close() }
            // 416 past the last byte of a stream of unknown size means we're done.
            if (e.responseCode == 416 && chunkStart > 0L && !lengthConfirmed) {
                return false
            }
            throw e
        } catch (e: IOException) {
            runCatching { upstream.close() }
            throw e
        }
        upstreamOpen = true
        chunkLength = wanted
        chunkRead = 0L
        return true
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (passThrough) return upstream.read(buffer, offset, length)
        if (ended) return C.RESULT_END_OF_INPUT
        while (true) {
            if (!upstreamOpen) {
                if (!openChunk()) {
                    ended = true
                    return C.RESULT_END_OF_INPUT
                }
            }
            val read = upstream.read(buffer, offset, length)
            if (read != C.RESULT_END_OF_INPUT) {
                chunkRead += read
                if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
                return read
            }
            // This chunk is finished.
            upstream.close()
            upstreamOpen = false
            val got = chunkRead
            chunkStart += got
            when {
                bytesRemaining == 0L -> {
                    ended = true
                    return C.RESULT_END_OF_INPUT
                }
                got == 0L -> {
                    // The server has nothing more. That is only wrong if it told us otherwise:
                    // then fail, and the player retries from here.
                    if (lengthConfirmed) throw IOException("Range request ended $bytesRemaining bytes early")
                    ended = true
                    return C.RESULT_END_OF_INPUT
                }
                // Otherwise carry on with the next range, also after a short one: some servers
                // cap how much one response carries.
            }
        }
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        spec = null
        if (upstreamOpen) {
            upstreamOpen = false
            upstream.close()
        }
    }

    class Factory(
        private val upstreamFactory: DataSource.Factory,
        private val chunkSizeBytes: Long = DEFAULT_CHUNK_SIZE_BYTES,
        private val onlyWhenHinted: Boolean = false,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            ChunkedDataSource(upstreamFactory.createDataSource(), chunkSizeBytes, onlyWhenHinted)
    }

    companion object {
        /** 4 MB: small enough that YouTube serves each piece at full speed. */
        const val DEFAULT_CHUNK_SIZE_BYTES = 4L * 1024 * 1024

        /** 1 MB for playback: about a minute of audio per request, like the official clients. */
        const val PLAYBACK_CHUNK_SIZE_BYTES = 1L * 1024 * 1024

        /** The total from a `Content-Range: bytes a-b/total` header, if the server sent one. */
        internal fun totalLength(headers: Map<String, List<String>>): Long? {
            val value =
                headers.entries
                    .firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }
                    ?.value?.firstOrNull() ?: return null
            return value.substringAfterLast('/', "").trim().toLongOrNull()?.takeIf { it > 0L }
        }
    }
}
