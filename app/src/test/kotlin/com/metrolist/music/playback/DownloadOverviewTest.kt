package com.metrolist.music.playback

import android.net.Uri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadProgress
import androidx.media3.exoplayer.offline.DownloadRequest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class DownloadOverviewTest {
    private fun download(id: String, state: Int, length: Long = 100, bytes: Long = 0): Download = Download(
        DownloadRequest.Builder(id, Uri.parse("https://example.com/$id")).setData(id.toByteArray()).build(),
        state, 0, 0, length, 0,
        if (state == Download.STATE_FAILED) Download.FAILURE_REASON_UNKNOWN else Download.FAILURE_REASON_NONE,
        DownloadProgress().apply { bytesDownloaded = bytes },
    )

    @Test
    fun `progress weights bytes and excludes failed completed and removing requests`() {
        val result = downloadOverview(listOf(
            download("ready", Download.STATE_COMPLETED, 200, 200),
            download("small", Download.STATE_DOWNLOADING, 100, 50),
            download("large", Download.STATE_QUEUED, 300, 0),
            download("failed", Download.STATE_FAILED),
            download("removing", Download.STATE_REMOVING),
        ))
        assertEquals(1, result.completed)
        assertEquals(2, result.pending)
        assertEquals(listOf(FailedDownload("failed", "failed")), result.failed)
        assertEquals(0.125f, result.progress!!, 0.001f)
    }

    @Test
    fun `unknown lengths never report misleading completion`() {
        val result = downloadOverview(listOf(download("queued", Download.STATE_QUEUED, -1)))
        assertNull(result.progress)
        assertNull(downloadOverview(emptyList()).progress)
    }

    @Test
    fun `stopped downloads remain visible as pending`() {
        val result = downloadOverview(listOf(download("paused", Download.STATE_STOPPED, 100, 40)))
        assertEquals(1, result.pending)
        assertEquals(1, result.stopped)
        assertEquals(0.4f, result.progress!!, 0.001f)
    }
}
