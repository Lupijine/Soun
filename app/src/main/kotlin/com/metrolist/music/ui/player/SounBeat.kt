/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import com.metrolist.music.playback.audio.BeatMeter

/**
 * The beat of what is playing right now, 0..1, smoothed for drawing: it rises at once on a hit
 * and falls back gently. Measured only while [active] (the player open and playing, the effect
 * on); otherwise it stays at 0 and nothing is analysed.
 */
@Composable
fun rememberBeatLevel(active: Boolean): State<Float> {
    val context = LocalContext.current
    val level = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active) {
        if (!active) {
            level.floatValue = 0f
            return@LaunchedEffect
        }
        BeatMeter.latencyNanos = outputLatencyNanos(context)
        BeatMeter.listening = true
        try {
            var smoothed = 0f
            while (true) {
                withFrameNanos {
                    val target = BeatMeter.levelAt(System.nanoTime())
                    smoothed += (target - smoothed) * if (target > smoothed) 0.55f else 0.10f
                    level.floatValue = smoothed
                }
            }
        } finally {
            BeatMeter.listening = false
            level.floatValue = 0f
        }
    }
    return level
}

/** The audio buffer plus the output: wireless headphones add a lot. */
private fun outputLatencyNanos(context: Context): Long {
    val audio = context.getSystemService(AudioManager::class.java) ?: return 280_000_000L
    val wireless =
        runCatching {
            audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }
        }.getOrDefault(false)
    return if (wireless) 480_000_000L else 280_000_000L
}
