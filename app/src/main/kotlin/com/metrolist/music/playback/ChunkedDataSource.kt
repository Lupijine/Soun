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
 * Asks the next [ChunkedDataSource] in the chain to read a stream in ranges of [chunkSizeBytes].
 * Carried in [DataSpec.customData], which the cache layers copy onto the requests they send on.
 */
data class RangeChunkHint(val chunkSizeBytes: Long)

/**
 * Reads a stream as a series of small ranged requests instead of one long one.
 *
 * YouTube slows down or drops a single open-ended request for a large file, and some of its
 * clients only answer bounded ranges at all, so a long track (an hour or more) would get stuck
 * partway. This source asks for one chunk at a time (`Range: bytes=a-b`) and joins the pieces
 * into one continuous stream for the caller, so the player, the cache and the downloader see
 * no difference.
 *
 * The chunk size comes from a [RangeChunkHint] on the request when there is one, otherwise
 * [chunkSizeBytes]. With [onlyWhenHinted] set, requests without a hint (anything that is not a
 * resolved YouTube stream) pass straight through to [upstream].
 *
 * The total size is taken from the first chunk's `Content-Range`, so the caller learns the
 * real length up front. Without it, the end of the file is detected by a chunk coming back
 * shorter than asked, or by the server answering 416 (range not satisfiable) for the next one.
 */
class ChunkedDataSource(
    private val upstream: DataSource,
    private val chunkSizeBytes: Long = DEFAULT_CHUNK_SIZE_BYTES,
    private val onlyWhenHinted: Boolean = false,
) : DataSource {
    private var spec: DataSpec? = null
    private var passThrough = false
    private var currentChunkSize = chunkSizeBytes

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
        val size = chunkSizeFor(dataSpec)
        if (size == null) {
            passThrough = true
            upstreamOpen = true
            return upstream.open(dataSpec)
        }
        passThrough = false
        currentChunkSize = size
        bytesRemaining = dataSpec.length
        chunkStart = 0L
        ended = false
        if (!openChunk()) {
            ended = true
            return 0L
        }
        if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            totalLength(upstream.responseHeaders)?.let { total ->
                bytesRemaining = (total - dataSpec.position).coerceAtLeast(0L)
            }
        }
        return bytesRemaining
    }

    private fun chunkSizeFor(dataSpec: DataSpec): Long? {
        val hinted = (dataSpec.customData as? RangeChunkHint)?.chunkSizeBytes?.takeIf { it > 0L }
        return when {
            hinted != null -> hinted
            onlyWhenHinted -> null
            else -> chunkSizeBytes
        }
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
            // 416 past the last byte of an open-ended read means we're done.
            if (e.responseCode == 416 && chunkStart > 0L && bytesRemaining == C.LENGTH_UNSET.toLong()) {
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
            val shortChunk = chunkRead < chunkLength
            chunkStart += chunkRead
            when {
                bytesRemaining == 0L -> {
                    ended = true
                    return C.RESULT_END_OF_INPUT
                }
                // Size unknown: a short chunk is the end of the file.
                bytesRemaining == C.LENGTH_UNSET.toLong() && shortChunk -> {
                    ended = true
                    return C.RESULT_END_OF_INPUT
                }
                // Size known but the server sent nothing: let the player retry from here.
                shortChunk && chunkRead == 0L ->
                    throw IOException("Range request ended $bytesRemaining bytes early")
                // Otherwise carry on with the next chunk (also after a short one with bytes left).
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
