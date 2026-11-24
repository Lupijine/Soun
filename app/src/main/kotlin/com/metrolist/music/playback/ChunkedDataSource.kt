/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import timber.log.Timber

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
 * YouTube slows down, stalls or drops a single long-running request for a large file, and some
 * of its clients only answer bounded ranges at all, so a long track (an hour or more) would get
 * stuck partway. This source asks for one range at a time (`Range: bytes=a-b`), downloads each
 * range completely as soon as it is needed, and joins the pieces into one continuous stream,
 * so the player, the cache and the downloader see no difference.
 *
 * Downloading a whole range at once means no connection is ever left half-read while the
 * player's buffer is full (servers drop or stall those). A range that stalls (no data for the
 * HTTP read timeout) or runs past [rangeTimeoutMs] is retried on a fresh connection, keeping
 * whatever already arrived, and later ranges shrink to suit a slow network and grow back on a
 * fast one. Playback starts after a small first range, so it does not wait for a whole one.
 *
 * Requests carrying a [RangeChunkHint] are always chunked; with [onlyWhenHinted] set, anything
 * else (not a resolved YouTube stream) passes straight through to the upstream.
 *
 * The total size comes from the first range's `Content-Range`, or else from the hint, so the
 * caller learns the real length up front. A range that comes back shorter than asked is not
 * taken as the end: reading carries on from where it stopped, and only an empty range or a
 * 416 (range not satisfiable) past the last byte ends a stream of unknown size.
 */
class ChunkedDataSource(
    private val upstreamFactory: DataSource.Factory,
    private val chunkSizeBytes: Long = DEFAULT_CHUNK_SIZE_BYTES,
    private val onlyWhenHinted: Boolean = false,
    private val rangeTimeoutMs: Long = DEFAULT_RANGE_TIMEOUT_MS,
) : DataSource {
    private val transferListeners = mutableListOf<TransferListener>()

    private var spec: DataSpec? = null
    private var passThroughSource: DataSource? = null

    /** Range size used for the next request; adapts to how fast ranges arrive. */
    private var currentChunkSize = chunkSizeBytes
    private var maxChunkSize = chunkSizeBytes

    /** Bytes still wanted by the caller, or [C.LENGTH_UNSET] if open-ended and still unknown. */
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()

    /** True once the remaining size is known from the caller or the server, not only guessed. */
    private var lengthConfirmed = false

    /** Offset from the start of [spec] of the first byte not yet requested. */
    private var nextRangeStart = 0L
    private var current: ByteArray? = null
    private var currentOffset = 0
    private var ended = false

    private var uri: Uri? = null
    private var responseHeaders: Map<String, List<String>> = emptyMap()

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners += transferListener
    }

    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec
        val hint = dataSpec.customData as? RangeChunkHint
        if (hint == null && onlyWhenHinted) {
            val source = createUpstream()
            passThroughSource = source
            return source.open(dataSpec)
        }
        maxChunkSize = hint?.maxChunkBytes?.takeIf { it > 0L }?.let { minOf(it, chunkSizeBytes) } ?: chunkSizeBytes
        currentChunkSize = maxChunkSize
        bytesRemaining = dataSpec.length
        lengthConfirmed = dataSpec.length != C.LENGTH_UNSET.toLong()
        nextRangeStart = 0L
        current = null
        currentOffset = 0
        ended = false
        uri = null
        responseHeaders = emptyMap()
        if (dataSpec.length == 0L) {
            ended = true
            return 0L
        }

        // A small first range, so playback can start without waiting for a whole one.
        val first = fetchRange(minOf(maxChunkSize, STARTUP_RANGE_BYTES), first = true)
        if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            val serverTotal = totalLength(responseHeaders)
            if (serverTotal != null) {
                bytesRemaining = (serverTotal - dataSpec.position).coerceAtLeast(0L)
                lengthConfirmed = true
            } else {
                hint?.contentLength?.takeIf { it > dataSpec.position }?.let { bytesRemaining = it - dataSpec.position }
            }
        }
        Timber.tag(LOG_TAG).d(
            "open %s at %d: asked %d, remaining %d (confirmed=%s), max range %d, hinted length %s",
            dataSpec.key, dataSpec.position, dataSpec.length, bytesRemaining, lengthConfirmed, maxChunkSize, hint?.contentLength,
        )
        if (first == null || first.isEmpty()) {
            ended = true
            return if (bytesRemaining == C.LENGTH_UNSET.toLong()) 0L else bytesRemaining
        }
        current = first
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        passThroughSource?.let { return it.read(buffer, offset, length) }
        while (true) {
            val range = current
            if (range != null && currentOffset < range.size) {
                val n = minOf(length, range.size - currentOffset)
                System.arraycopy(range, currentOffset, buffer, offset, n)
                currentOffset += n
                if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= n
                return n
            }
            current = null
            if (ended || bytesRemaining == 0L) {
                ended = true
                return C.RESULT_END_OF_INPUT
            }
            val wanted =
                if (bytesRemaining == C.LENGTH_UNSET.toLong()) currentChunkSize else minOf(currentChunkSize, bytesRemaining)
            val next = fetchRange(wanted, first = false)
            if (next == null || next.isEmpty()) {
                // The server has nothing more. That is only wrong if it told us otherwise: then
                // fail, and the player retries from here.
                if (lengthConfirmed) {
                    Timber.tag(LOG_TAG).w("%s: server sent nothing at %d with %d bytes still due", spec?.key, position(), bytesRemaining)
                    throw IOException("Range request ended $bytesRemaining bytes early")
                }
                Timber.tag(LOG_TAG).d("%s: end of stream at %d", spec?.key, position())
                ended = true
                return C.RESULT_END_OF_INPUT
            }
            current = next
            currentOffset = 0
        }
    }

    /**
     * Downloads the range starting at [nextRangeStart], up to [size] bytes, and advances past
     * what arrived. Returns null past the end of a stream of unknown size.
     */
    private fun fetchRange(size: Long, first: Boolean): ByteArray? {
        val dataSpec = checkNotNull(spec)
        var wanted = size
        var lastError: IOException? = null
        for (attempt in 0 until MAX_RANGE_ATTEMPTS) {
            if (attempt > 0) pause(RETRY_DELAY_MS * attempt)
            val source = createUpstream()
            val received = ByteArrayOutputStream(minOf(wanted, MAX_INITIAL_BUFFER).toInt())
            val startedAt = SystemClock.elapsedRealtime()
            var timedOut = false
            try {
                source.open(dataSpec.subrange(nextRangeStart, wanted))
                if (first) {
                    uri = source.uri
                    responseHeaders = source.responseHeaders
                }
                val chunk = ByteArray(READ_BUFFER_BYTES)
                while (received.size() < wanted) {
                    val n = source.read(chunk, 0, minOf(chunk.size.toLong(), wanted - received.size()).toInt())
                    if (n == C.RESULT_END_OF_INPUT) break
                    received.write(chunk, 0, n)
                    if (SystemClock.elapsedRealtime() - startedAt > rangeTimeoutMs) {
                        // Too slow: keep what came, and ask for less next time.
                        timedOut = true
                        break
                    }
                }
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                adapt(received.size().toLong(), elapsed, timedOut)
                logRange(dataSpec, wanted, received.size(), elapsed, attempt, if (timedOut) "slow, cut short" else null)
                return received.toByteArray().also { nextRangeStart += it.size }
            } catch (e: HttpDataSource.InvalidResponseCodeException) {
                Timber.tag(LOG_TAG).w("%s: HTTP %d at %d (attempt %d)", dataSpec.key, e.responseCode, dataSpec.position + nextRangeStart, attempt + 1)
                when {
                    // 416 past the last byte of a stream of unknown size means we're done.
                    e.responseCode == 416 && !lengthConfirmed && !first -> return null
                    e.responseCode == 408 || e.responseCode == 429 || e.responseCode >= 500 -> lastError = e
                    // Expired or refused: retrying the same address will not help.
                    else -> throw e
                }
            } catch (e: IOException) {
                // The player cancelled the load (a seek, a skip): stop at once, don't retry.
                if (isCancellation(e)) throw e
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                Timber.tag(LOG_TAG).w(
                    "%s: range at %d failed after %d ms with %d bytes (attempt %d): %s",
                    dataSpec.key, dataSpec.position + nextRangeStart, elapsed, received.size(), attempt + 1, describe(e),
                )
                // Keep whatever arrived; the next range picks up from there.
                if (received.size() > 0) {
                    adapt(received.size().toLong(), elapsed, timedOut = true)
                    return received.toByteArray().also { nextRangeStart += it.size }
                }
                lastError = e
                wanted = maxOf(minRange(), wanted / 2)
                currentChunkSize = maxOf(minRange(), minOf(currentChunkSize, wanted))
            } finally {
                runCatching { source.close() }
            }
        }
        Timber.tag(LOG_TAG).e("%s: giving up on the range at %d after %d attempts", dataSpec.key, dataSpec.position + nextRangeStart, MAX_RANGE_ATTEMPTS)
        throw lastError ?: IOException("Range request failed")
    }

    private fun position(): Long = (spec?.position ?: 0L) + nextRangeStart

    private fun logRange(dataSpec: DataSpec, wanted: Long, got: Int, elapsedMs: Long, attempt: Int, note: String?) {
        Timber.tag(LOG_TAG).d(
            "%s: range at %d got %d of %d bytes in %d ms%s%s",
            dataSpec.key, dataSpec.position + nextRangeStart, got, wanted, elapsedMs,
            if (attempt > 0) " (attempt ${attempt + 1})" else "",
            note?.let { " ($it)" } ?: "",
        )
    }

    /** Halves the range size after a slow range, doubles it back after a quick full one. */
    private fun adapt(bytes: Long, elapsedMs: Long, timedOut: Boolean) {
        currentChunkSize =
            when {
                timedOut -> maxOf(minRange(), minOf(currentChunkSize, bytes) / 2)
                bytes >= currentChunkSize && elapsedMs < FAST_RANGE_MS -> minOf(maxChunkSize, currentChunkSize * 2)
                else -> currentChunkSize
            }
    }

    private fun minRange() = minOf(MIN_RANGE_BYTES, maxChunkSize)

    private fun pause(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Interrupted while retrying a range")
        }
    }

    private fun createUpstream(): DataSource =
        upstreamFactory.createDataSource().also { source -> transferListeners.forEach(source::addTransferListener) }

    override fun getUri(): Uri? = passThroughSource?.uri ?: uri

    override fun getResponseHeaders(): Map<String, List<String>> = passThroughSource?.responseHeaders ?: responseHeaders

    override fun close() {
        spec = null
        current = null
        passThroughSource?.let { source ->
            passThroughSource = null
            source.close()
        }
    }

    class Factory(
        private val upstreamFactory: DataSource.Factory,
        private val chunkSizeBytes: Long = DEFAULT_CHUNK_SIZE_BYTES,
        private val onlyWhenHinted: Boolean = false,
        private val rangeTimeoutMs: Long = DEFAULT_RANGE_TIMEOUT_MS,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            ChunkedDataSource(upstreamFactory, chunkSizeBytes, onlyWhenHinted, rangeTimeoutMs)
    }

    companion object {
        private const val LOG_TAG = "SounStream"

        /**
         * A stalled connection (no data for the HTTP read timeout) also surfaces as an
         * [InterruptedIOException]: a [SocketTimeoutException]. That one is retried; only a real
         * interruption, the player cancelling the load, is passed on at once.
         */
        internal fun isCancellation(e: IOException): Boolean {
            if (Thread.currentThread().isInterrupted) return true
            var cause: Throwable? = e
            while (cause != null) {
                if (cause is SocketTimeoutException) return false
                if (cause is InterruptedIOException) return true
                cause = cause.cause?.takeIf { it !== cause }
            }
            return false
        }

        private fun describe(e: Throwable): String {
            val root = generateSequence(e) { it.cause?.takeIf { next -> next !== it } }.last()
            return if (root === e) "${e::class.simpleName}: ${e.message}" else "${e::class.simpleName} (${root::class.simpleName}: ${root.message})"
        }

        /** 4 MB: small enough that YouTube serves each piece at full speed. */
        const val DEFAULT_CHUNK_SIZE_BYTES = 4L * 1024 * 1024

        /** 1 MB for playback: about a minute of audio per request, like the official clients. */
        const val PLAYBACK_CHUNK_SIZE_BYTES = 1L * 1024 * 1024

        /** The first range after opening or seeking, so sound starts quickly. */
        const val STARTUP_RANGE_BYTES = 256L * 1024

        const val DEFAULT_RANGE_TIMEOUT_MS = 20_000L

        private const val MIN_RANGE_BYTES = 64L * 1024
        private const val FAST_RANGE_MS = 4_000L
        private const val MAX_RANGE_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 400L
        private const val READ_BUFFER_BYTES = 16 * 1024
        private const val MAX_INITIAL_BUFFER = 1L * 1024 * 1024

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
