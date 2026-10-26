package com.metrolist.music.utils

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class PlaylistCoverStorageTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `saved cover survives removal of the temporary crop`() {
        val crop = File(context.cacheDir, "crop.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val stored = persistPlaylistCover(context, Uri.fromFile(crop))
        crop.delete()
        val cover = File(Uri.parse(stored).path!!)
        assertEquals(File(context.filesDir, "playlist_covers"), cover.parentFile)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), cover.readBytes())
        deletePlaylistCover(context, stored)
    }

    @Test
    fun `cleanup only removes managed covers`() {
        val original = File(context.filesDir, "original.jpg").apply { writeText("user image") }
        val stored = persistPlaylistCover(context, Uri.fromFile(original))
        deletePlaylistCover(context, Uri.fromFile(original).toString())
        assertTrue(original.exists())
        deletePlaylistCover(context, stored)
        assertFalse(File(Uri.parse(stored).path!!).exists())
        assertTrue(original.exists())
    }

    @Test
    fun `empty crop does not leave a broken cover file`() {
        val directory = File(context.filesDir, "playlist_covers")
        val before = directory.listFiles()?.size ?: 0
        val crop = File(context.cacheDir, "empty.jpg").apply { writeBytes(byteArrayOf()) }
        assertThrows(IOException::class.java) { persistPlaylistCover(context, Uri.fromFile(crop)) }
        assertEquals(before, directory.listFiles()?.size ?: 0)
    }
}
