package com.metrolist.music.playback

import android.net.Uri
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.ogg.OggExtractor
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class PlaybackExtractorsTest {
    @Test fun `opus in an ogg container is recognized by the player extractor factory`() {
        val bytes = javaClass.getResourceAsStream("/audio/silence.opus")!!.readBytes()
        val extractor = playbackExtractorsFactory().createExtractors().filterIsInstance<OggExtractor>().single()
        val source = ByteArrayDataSource(bytes)
        val length = source.open(DataSpec(Uri.parse("https://example.org/audio.opus")))
        try { assertTrue(extractor.sniff(DefaultExtractorInput(source, 0, length))) }
        finally { source.close() }
    }
}
