package com.metrolist.music.playback

import android.net.Uri
import androidx.media3.datasource.DataSpec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StreamOrDownloadDataSourceTest {
    private fun spec(customData: Any?) =
        DataSpec.Builder()
            .setUri(Uri.parse("Xgxzdt-rzPg"))
            .setKey("Xgxzdt-rzPg")
            .setCustomData(customData)
            .build()

    @Test
    fun `streams and whole player-cache copies skip the downloads`() {
        assertTrue(StreamOrDownloadDataSource.isStream(spec(RangeChunkHint(contentLength = 1_000L))))
        assertTrue(StreamOrDownloadDataSource.isStream(spec(StreamOrDownloadDataSource.FromPlayerCache)))
    }

    @Test
    fun `finished downloads are read from the downloads`() {
        assertFalse(StreamOrDownloadDataSource.isStream(spec(null)))
    }
}
