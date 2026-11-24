package com.metrolist.music.playback.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class BeatMeterTest {
    @Test
    fun `reads the beat that is heard now, not the one just processed`() {
        BeatMeter.clear()
        BeatMeter.latencyNanos = 300_000_000L
        BeatMeter.push(1_000_000_000L, 0.2f)
        BeatMeter.push(1_020_000_000L, 0.9f)
        BeatMeter.push(1_300_000_000L, 0.5f)
        // Heard at 1.33 s: processed at 1.03 s, the 0.9 block.
        assertEquals(0.9f, BeatMeter.levelAt(1_330_000_000L), 0f)
        assertEquals(0.5f, BeatMeter.levelAt(1_610_000_000L), 0f)
        // Long after the last block (paused): no beat.
        assertEquals(0f, BeatMeter.levelAt(3_000_000_000L), 0f)
        // Before anything was processed.
        assertEquals(0f, BeatMeter.levelAt(1_100_000_000L), 0f)
    }
}

class BeatAudioProcessorTest {
    @Test
    fun `passes the audio through unchanged and pulses on a bass hit`() {
        val processor = BeatAudioProcessor()
        processor.configure(
            androidx.media3.common.audio.AudioProcessor.AudioFormat(48_000, 2, androidx.media3.common.C.ENCODING_PCM_16BIT),
        )
        processor.flush(androidx.media3.common.audio.AudioProcessor.StreamMetadata.DEFAULT)
        BeatMeter.clear()
        BeatMeter.latencyNanos = 0L
        BeatMeter.listening = true
        try {
            // Half a second of quiet, then a loud 60 Hz thump.
            val frames = 48_000
            val input = java.nio.ByteBuffer.allocateDirect(frames * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until frames) {
                val amplitude = if (i < frames / 2) 300.0 else 20_000.0
                val value = (amplitude * kotlin.math.sin(2 * Math.PI * 60 * i / 48_000.0)).toInt().toShort()
                input.putShort(value)
                input.putShort(value)
            }
            input.flip()
            val copy = ByteArray(input.remaining()).also { input.duplicate().get(it) }

            processor.queueInput(input)
            val output = processor.output
            val out = ByteArray(output.remaining()).also { output.get(it) }
            org.junit.Assert.assertArrayEquals(copy, out)

            // 0.2 s in it is quiet; 0.9 s in, the thump reads as a strong beat.
            val quiet = BeatMeter.levelAt(System.nanoTime() + 200_000_000L)
            val loud = BeatMeter.levelAt(System.nanoTime() + 900_000_000L)
            org.junit.Assert.assertTrue("beat was $loud", loud > 0.5f)
            org.junit.Assert.assertTrue("quiet was $quiet", quiet < loud)
        } finally {
            BeatMeter.listening = false
        }
    }
}
