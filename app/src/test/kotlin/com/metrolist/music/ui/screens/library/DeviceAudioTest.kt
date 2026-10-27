package com.metrolist.music.ui.screens.library

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.extensions.isDeviceAudioId
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class DeviceAudioTest {
    @Test fun `device audio keeps playable content URIs and duration without a network lookup`() {
        val provider = AudioProvider()
        ShadowContentResolver.registerProviderInternal("media", provider)
        val songs = readDeviceAudio(ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver)
        assertEquals(1, songs.size)
        assertEquals("Local title", songs.single().title)
        assertEquals("Local artist", songs.single().artists.single().name)
        assertEquals(182, songs.single().duration)
        assertEquals("content://media/external/audio/albumart/7", songs.single().thumbnailUrl)
        assertEquals("content://media/external/audio/media/42", songs.single().toMediaItem().localConfiguration!!.uri.toString())
        assertTrue(provider.lastCursor!!.isClosed)
    }

    @Test fun `missing album art is represented without inventing a cover`() {
        ShadowContentResolver.registerProviderInternal("media", AudioProvider(albumId = null))
        val song = readDeviceAudio(ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver).single()
        assertNull(song.thumbnailUrl)
        assertTrue(isDeviceAudioId(song.id))
        assertFalse(isDeviceAudioId("https://music.youtube.com/watch?v=abc"))
        assertFalse(isDeviceAudioId("abc"))
    }

    @Test(expected = SecurityException::class)
    fun `access failures are surfaced so the page can offer settings instead of an empty list`() {
        ShadowContentResolver.registerProviderInternal("media", AudioProvider(denied = true))
        readDeviceAudio(ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver)
    }

    @Test(expected = java.io.IOException::class)
    fun `unavailable audio provider is an error rather than an empty library`() {
        ShadowContentResolver.registerProviderInternal("media", AudioProvider(unavailable = true))
        readDeviceAudio(ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver)
    }

    private class AudioProvider(private val denied: Boolean = false, private val unavailable: Boolean = false, private val albumId: Long? = 7L) : ContentProvider() {
        var lastCursor: Cursor? = null
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
            if (unavailable) return null
            if (denied) throw SecurityException("Audio access denied")
            assertEquals("duration > 0", selection)
            assertTrue(sortOrder!!.contains("COLLATE NOCASE"))
            return MatrixCursor(projection!!).apply {
                addRow(arrayOf<Any?>(42L, "Local title", "Local artist", 182_900L, albumId))
                lastCursor = this
            }
        }
        override fun getType(uri: Uri) = "audio/mpeg"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
