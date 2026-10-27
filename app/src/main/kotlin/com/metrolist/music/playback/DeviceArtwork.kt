package com.metrolist.music.playback

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.security.MessageDigest

/** Runs off the main thread. A missing or unreadable embedded image is not a playback error. */
internal fun extractDeviceArtwork(context: Context, mediaId: String): String? {
    val retriever = try { MediaMetadataRetriever() } catch (_: Exception) { return null }
    return try {
        retriever.setDataSource(context, Uri.parse(mediaId))
        retriever.embeddedPicture?.takeIf { it.isNotEmpty() }?.let { saveDeviceArtwork(context.cacheDir, it) }
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

internal fun saveDeviceArtwork(cacheDir: File, bytes: ByteArray): String {
    val directory = File(cacheDir, "device_album_art")
    check(directory.isDirectory || directory.mkdirs())
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    val target = File(directory, "$digest.img")
    if (!target.isFile) {
        val temporary = File.createTempFile("art_", ".tmp", directory)
        try {
            temporary.writeBytes(bytes)
            check(temporary.renameTo(target) || target.isFile)
        } finally {
            temporary.delete()
        }
    }
    target.setLastModified(System.currentTimeMillis())
    directory.listFiles { file -> file.extension == "img" }
        ?.filter { it != target }
        ?.sortedByDescending { it.lastModified() }
        ?.drop(63)
        ?.forEach { it.delete() }
    return Uri.fromFile(target).toString()
}
