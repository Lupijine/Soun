/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Reads songs that already live on the phone (`content://` MediaStore audio) straight from
 * [local], and sends everything else (YouTube streams) through [remote], the
 * download/player cache chain. Without this, device songs were copied whole into the player
 * cache, pushing out cached YouTube audio and showing up in the "Cached" playlist.
 */
@UnstableApi
class LocalFirstDataSource(
    private val local: DataSource,
    private val remote: DataSource,
) : DataSource {
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        local.addTransferListener(transferListener)
        remote.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val source = if (isLocal(dataSpec.uri)) local else remote
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
        private val localFactory: DataSource.Factory,
        private val remoteFactory: DataSource.Factory,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            LocalFirstDataSource(localFactory.createDataSource(), remoteFactory.createDataSource())
    }

    companion object {
        fun isLocal(uri: Uri): Boolean = uri.scheme.equals("content", ignoreCase = true)
    }
}
