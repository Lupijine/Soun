package com.metrolist.music.ui.theme

import android.animation.ValueAnimator
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Slow light movement, sampled at 20 fps and read only during drawing. */
@Composable
internal fun rememberAmbientDrift(): State<Float> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val power = LocalContext.current.getSystemService(PowerManager::class.java)
    return produceState(0f, lifecycle, power) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = SystemClock.uptimeMillis()
            var nextPolicyCheck = 0L
            var moving = false
            while (isActive) {
                val now = SystemClock.uptimeMillis()
                if (now >= nextPolicyCheck) {
                    moving = ValueAnimator.areAnimatorsEnabled() && power?.isPowerSaveMode != true
                    nextPolicyCheck = now + 1_000L
                }
                if (moving) {
                    value = (value + (now - previous).coerceAtMost(100L) / 36_000f) % 1f
                }
                previous = now
                delay(if (moving) 50L else 1_000L)
            }
        }
    }
}
