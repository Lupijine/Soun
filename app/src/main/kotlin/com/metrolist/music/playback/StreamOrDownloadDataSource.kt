/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Sends requests for a resolved stream (they carry a [RangeChunkHint]) or for a song the player
 * cache holds whole ([FromPlayerCache]) past the downloads, to [stream]; songs found whole in
 * the downloads go to [downloaded].
 *
 * A song is streamed only when its download is missing or unfinished, and an unfinished
 * download may come from another encoding of the song than the stream playing now. Reading its
 * saved bytes and filling the gaps from the stream joined the two into audio that stopped after
 * a few seconds.
 */
class StreamOrDownloadDataSource(
    private val downloaded: DataSource,
    private val stream: DataSource,
) : DataSource {
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        downloaded.addTransferListener(transferListener)
        stream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val source = if (isStream(dataSpec)) stream else downloaded
        active = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(active) { "read() before open()" }.read(buffer, offset, length)

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

    override fun close() {
        val source = active ?: return
        active = null
        source.close()
    }

    class Factory(
        private val downloadedFactory: DataSource.Factory,
        private val streamFactory: DataSource.Factory,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            StreamOrDownloadDataSource(downloadedFactory.createDataSource(), streamFactory.createDataSource())
    }

    /** Marks a request the player cache serves whole, so an unfinished download is not read either. */
    object FromPlayerCache

    companion object {
        fun isStream(dataSpec: DataSpec): Boolean =
            dataSpec.customData is RangeChunkHint || dataSpec.customData === FromPlayerCache
    }
}
