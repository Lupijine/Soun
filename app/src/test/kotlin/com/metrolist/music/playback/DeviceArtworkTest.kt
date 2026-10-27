package com.metrolist.music.playback

import android.net.Uri
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class DeviceArtworkTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `embedded artwork is reused and cache stays bounded`() {
        val root = temporary.newFolder()
        val bytes = byteArrayOf(1, 2, 3)
        val first = saveDeviceArtwork(root, bytes)
        assertEquals(first, saveDeviceArtwork(root, bytes))
        assertArrayEquals(bytes, File(Uri.parse(first).path!!).readBytes())
        repeat(70) { saveDeviceArtwork(root, byteArrayOf(it.toByte(), 42)) }
        assertEquals(64, File(root, "device_album_art").listFiles { file -> file.extension == "img" }!!.size)
    }
}
