/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * The beat of what is playing, for the player background to move with. Values are written by
 * [BeatAudioProcessor] as the audio is decoded and read back by the UI at the moment that audio
 * is heard (the sound reaches the speaker about [latencyNanos] after it was processed).
 */
object BeatMeter {
    private const val SIZE = 512
    private val times = LongArray(SIZE)
    private val values = FloatArray(SIZE)
    private var head = 0
    private var count = 0

    /** Only analyse while something shows the beat; costs nothing otherwise. */
    @Volatile
    var listening = false

    /** Audio buffered between the processor and the speaker (more over Bluetooth). */
    @Volatile
    var latencyNanos = 280_000_000L

    @Synchronized
    fun push(timeNanos: Long, value: Float) {
        times[head] = timeNanos
        values[head] = value
        head = (head + 1) % SIZE
        if (count < SIZE) count++
    }

    @Synchronized
    fun clear() {
        count = 0
    }

    /** The beat heard at [nowNanos]: the value processed [latencyNanos] earlier. */
    @Synchronized
    fun levelAt(nowNanos: Long): Float {
        if (count == 0) return 0f
        val target = nowNanos - latencyNanos
        // Newest first; the first entry at or before the target is the one being heard.
        for (i in 1..count) {
            val index = (head - i + SIZE) % SIZE
            if (times[index] <= target) {
                // Nothing for a while (paused, buffering): no beat.
                return if (target - times[index] > 200_000_000L) 0f else values[index]
            }
        }
        return 0f
    }
}

/**
 * A pass-through step in the audio chain that measures the beat: the bass is filtered out,
 * its energy taken every 20 ms and compared with the recent average, so a kick or a strong
 * downbeat reads as a short pulse from 0 to 1. Needs no permission (unlike Android's
 * Visualizer) and only does the maths while [BeatMeter.listening].
 */
@UnstableApi
class BeatAudioProcessor : BaseAudioProcessor() {
    private var sampleRate = 0
    private var channelCount = 0

    private var lowAlpha = 0f
    private var low = 0f
    private var blockFrames = 0
    private var blockSize = 0
    private var blockEnergy = 0f
    private var average = 0f
    private var peak = 0.05f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount <= 0) {
            // Leave other formats alone; the effect just stays still.
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        // One-pole low-pass around 150 Hz: kick drum and bass.
        lowAlpha = (1.0 - exp(-2.0 * Math.PI * 150.0 / sampleRate)).toFloat()
        blockSize = (sampleRate / 50).coerceAtLeast(1)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        if (BeatMeter.listening && sampleRate > 0) analyse(inputBuffer)
        replaceOutputBuffer(inputBuffer.remaining()).apply {
            put(inputBuffer)
            flip()
        }
    }

    private fun analyse(buffer: ByteBuffer) {
        val data = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val start = data.position()
        val frames = data.remaining() / (2 * channelCount)
        val now = System.nanoTime()
        val blockNanos = 1_000_000_000L * blockSize / sampleRate
        var emitted = 0
        for (frame in 0 until frames) {
            var sum = 0f
            val base = start + frame * channelCount * 2
            for (channel in 0 until channelCount) sum += data.getShort(base + channel * 2)
            val sample = sum / (channelCount * 32768f)
            low += lowAlpha * (sample - low)
            blockEnergy += low * low
            if (++blockFrames >= blockSize) {
                val energy = sqrt(blockEnergy / blockFrames)
                blockFrames = 0
                blockEnergy = 0f
                // Slow average (about a second) and a slowly falling peak keep it relative to
                // the song: quiet tracks pulse as clearly as loud ones.
                average += (energy - average) * 0.04f
                peak = maxOf(energy, peak * 0.995f, 0.02f)
                val pulse = ((energy - average * 1.15f) / (peak - average * 1.15f + 1e-4f)).coerceIn(0f, 1f)
                val presence = (energy / peak).coerceIn(0f, 1f)
                // Blocks in this buffer are played one after another; spread their times.
                val time = now + emitted * blockNanos
                BeatMeter.push(time, (pulse * 0.75f + presence * 0.25f).coerceIn(0f, 1f))
                emitted++
            }
        }
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        low = 0f
        blockFrames = 0
        blockEnergy = 0f
        BeatMeter.clear()
    }

    override fun onReset() {
        sampleRate = 0
        channelCount = 0
        average = 0f
        peak = 0.05f
        BeatMeter.clear()
    }
}
