package com.metrolist.music.lyrics

import android.os.Looper
import com.metrolist.music.viewmodels.LyricsViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class LyricsViewModelIdentityTest {
    @Test fun `switching songs clears parsed lyrics before the next parse finishes`() {
        val model = LyricsViewModel()
        fun awaitSong(id: String) {
            val deadline = System.nanoTime() + 3_000_000_000L
            while (model.mediaId.value != id && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(5)
            }
            assertEquals(id, model.mediaId.value)
        }
        model.processLyrics("Old song text", emptyList(), false, false, "old")
        awaitSong("old")
        assertTrue(model.lines.value.any { it.text == "Old song text" })
        model.processLyrics("New song text", emptyList(), false, false, "new")
        assertNotEquals("old", model.mediaId.value)
        assertFalse(model.lines.value.any { it.text == "Old song text" })
        awaitSong("new")
        assertTrue(model.lines.value.any { it.text == "New song text" })
    }
}
