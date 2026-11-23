/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import androidx.core.net.toUri
import androidx.media3.datasource.DataSpec

internal data class CachedStreamUrl(
    val url: String,
    val requestHeaders: Map<String, String>,
    val clientName: String,
    val requireBoundedRange: Boolean = false,
    val rangeChunkSizeBytes: Long = 0L,
    val useRangeChunks: Boolean = false,
    val contentLength: Long? = null,
)

/**
 * Points [this] request at the resolved stream. The request keeps its full length and carries a
 * [RangeChunkHint] instead: the [ChunkedDataSource] in the chain reads it in ranges no larger
 * than the client allows, and knows the stream's size up front. (Cutting the request itself
 * down to one range made playback stop or stall after the first megabyte.)
 */
internal fun DataSpec.withResolvedStream(stream: CachedStreamUrl): DataSpec {
    val maxChunk =
        stream.rangeChunkSizeBytes.takeIf {
            it > 0L && (stream.requireBoundedRange || stream.useRangeChunks)
        }
    return buildUpon()
        .setUri(stream.url.toUri())
        .setHttpRequestHeaders(httpRequestHeaders + stream.requestHeaders)
        .setCustomData(RangeChunkHint(maxChunkBytes = maxChunk, contentLength = stream.contentLength?.takeIf { it > 0L }))
        .build()
}

internal class StreamUrlCache(
    private val maxEntries: Int = 500,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(
        val stream: CachedStreamUrl,
        val expiresAtMillis: Long,
    )

    private val entries =
        object : LinkedHashMap<String, Entry>(0, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
                size > maxEntries
        }
    private val generations = HashMap<String, Long>()

    init {
        require(maxEntries > 0) { "maxEntries must be greater than zero" }
    }

    operator fun get(mediaId: String): CachedStreamUrl? =
        synchronized(entries) {
            val entry = entries[mediaId] ?: return@synchronized null
            if (entry.expiresAtMillis <= currentTimeMillis()) {
                entries.remove(mediaId)
                advanceGeneration(mediaId)
                null
            } else {
                entry.stream
            }
        }

    fun clientName(mediaId: String): String? =
        synchronized(entries) { entries[mediaId]?.stream?.clientName }

    fun generation(mediaId: String): Long =
        synchronized(entries) { generations[mediaId] ?: 0L }

    fun put(
        mediaId: String,
        url: String,
        requestHeaders: Map<String, String>,
        clientName: String,
        expiresInSeconds: Int,
        requireBoundedRange: Boolean = false,
        rangeChunkSizeBytes: Long = 0L,
        useRangeChunks: Boolean = false,
        contentLength: Long? = null,
        expectedGeneration: Long = generation(mediaId),
    ): Boolean {
        val now = currentTimeMillis()
        val ttlMillis = expiresInSeconds.coerceAtLeast(0).toLong() * 1_000L
        val expiresAtMillis =
            runCatching { Math.addExact(now, ttlMillis) }
                .getOrDefault(Long.MAX_VALUE)

        synchronized(entries) {
            if ((generations[mediaId] ?: 0L) != expectedGeneration) return false
            entries[mediaId] =
                Entry(
                    stream =
                        CachedStreamUrl(
                            url = url,
                            requestHeaders = requestHeaders.toMap(),
                            clientName = clientName,
                            requireBoundedRange = requireBoundedRange,
                            rangeChunkSizeBytes = rangeChunkSizeBytes,
                            useRangeChunks = useRangeChunks,
                            contentLength = contentLength,
                        ),
                    expiresAtMillis = expiresAtMillis,
                )
            return true
        }
    }

    fun invalidate(mediaId: String) {
        synchronized(entries) {
            entries.remove(mediaId)
            advanceGeneration(mediaId)
        }
    }

    private fun advanceGeneration(mediaId: String) {
        generations[mediaId] = (generations[mediaId] ?: 0L) + 1L
    }
}
