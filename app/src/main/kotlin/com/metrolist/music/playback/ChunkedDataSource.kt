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

/**
 * Downloads a stream as a series of small ranged requests instead of one long one.
 *
 * YouTube slows down or drops a single open-ended request for a large file, so a
 * download of a long track (over an hour) would get stuck partway. This source asks
 * for [chunkSizeBytes] at a time (`Range: bytes=a-b`) and joins the pieces into one
 * continuous stream for the caller, so the cache and downloader see no difference.
 *
 * The end of the file is detected either by a chunk coming back shorter than asked,
 * or by the server answering 416 (range not satisfiable) for the next chunk.
 */
class ChunkedDataSource(
    private val upstream: DataSource,
    private val chunkSizeBytes: Long = DEFAULT_CHUNK_SIZE_BYTES,
) : DataSource {
    private var spec: DataSpec? = null

    /** Bytes still wanted by the caller, or [C.LENGTH_UNSET] if open-ended. */
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
        bytesRemaining = dataSpec.length
        chunkStart = 0L
        ended = false
        openChunk()
        return dataSpec.length
    }

    /** Opens the next chunk; returns false if the server says the file has ended. */
    private fun openChunk(): Boolean {
        val dataSpec = spec ?: return false
        val wanted =
            if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
                chunkSizeBytes
            } else {
                minOf(chunkSizeBytes, bytesRemaining)
            }
        if (wanted <= 0L) return false
        val chunkSpec = dataSpec.subrange(chunkStart, wanted)
        try {
            upstream.open(chunkSpec)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            // 416 past the last byte of an open-ended download means we're done.
            if (e.responseCode == 416 && chunkStart > 0L && dataSpec.length == C.LENGTH_UNSET.toLong()) {
                runCatching { upstream.close() }
                return false
            }
            throw e
        }
        upstreamOpen = true
        chunkLength = wanted
        chunkRead = 0L
        return true
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
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
            if (shortChunk || bytesRemaining == 0L) {
                ended = true
                return C.RESULT_END_OF_INPUT
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
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            ChunkedDataSource(upstreamFactory.createDataSource(), chunkSizeBytes)
    }

    companion object {
        /** 4 MB: small enough that YouTube serves each piece at full speed. */
        const val DEFAULT_CHUNK_SIZE_BYTES = 4L * 1024 * 1024
    }
}
