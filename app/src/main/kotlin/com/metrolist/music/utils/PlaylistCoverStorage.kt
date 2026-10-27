package com.metrolist.music.utils

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException

internal fun persistPlaylistCover(context: Context, source: Uri): String {
    val directory = File(context.filesDir, "playlist_covers")
    if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create playlist cover directory")
    val isPng = context.contentResolver.getType(source)?.equals("image/png", ignoreCase = true) == true ||
        source.lastPathSegment?.endsWith(".png", ignoreCase = true) == true
    val destination = File.createTempFile("cover_", if (isPng) ".png" else ".jpg", directory)
    try {
        val copied = context.contentResolver.openInputStream(source)?.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("Cannot read playlist cover")
        if (copied == 0L) throw IOException("Empty playlist cover")
        return Uri.fromFile(destination).toString()
    } catch (error: Exception) {
        destination.delete()
        throw error
    }
}

internal fun deletePlaylistCover(context: Context, uri: String?) {
    if (uri == null) return
    runCatching {
        val parsed = Uri.parse(uri)
        if (parsed.scheme != "file") return
        val file = File(parsed.path ?: return).canonicalFile
        // Never delete gallery files or any files outside our managed cover directory.
        if (file.parentFile == File(context.filesDir, "playlist_covers").canonicalFile) file.delete()
    }
}
