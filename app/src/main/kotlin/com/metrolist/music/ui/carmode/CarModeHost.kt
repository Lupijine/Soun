/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.carmode

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.constants.CarModeAutoOpenKey
import com.metrolist.music.utils.rememberPreference

/** Whether car mode is showing. Lives outside the activity so it survives rotation. */
object CarMode {
    var isOpen by mutableStateOf(false)
        private set

    // Car connections auto-open has already answered; closing car mode must not be undone by a rotation
    // or a return to the app while the same connection lasts. Audio device ids are new on every connection.
    internal val answeredDevices = mutableSetOf<Int>()

    fun open() {
        isOpen = true
    }

    fun close() {
        isOpen = false
    }
}

/** Shows car mode over the whole app when it is open, and opens it on its own in the car if allowed. */
@Composable
fun CarModeHost() {
    CarModeAutoOpen()
    val playerConnection = LocalPlayerConnection.current ?: return
    val playerReady by playerConnection.service.isPlayerReady.collectAsStateWithLifecycle()
    if (CarMode.isOpen && playerReady) {
        CarModeScreen(playerConnection = playerConnection, onClose = CarMode::close)
    }
}

@Composable
private fun CarModeAutoOpen() {
    val enabled by rememberPreference(CarModeAutoOpenKey, defaultValue = false)
    if (!enabled) return
    val context = LocalContext.current
    val connected = remember { mutableStateMapOf<Int, AudioDeviceInfo>() }

    DisposableEffect(context) {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val callback =
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    addedDevices?.filter(CarAudioDevices::isBluetoothAudio)?.forEach { connected[it.id] = it }
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    removedDevices?.forEach {
                        connected.remove(it.id)
                        CarMode.answeredDevices.remove(it.id)
                    }
                }
            }
        // Registering also reports the devices already connected, so a car that connected while
        // Soun was in the background is noticed once the app is back in front.
        audioManager?.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose {
            audioManager?.unregisterAudioDeviceCallback(callback)
            connected.clear()
        }
    }

    // Android does not let apps open screens from the background, so act only while Soun is in front.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val inFront = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val deviceIds = connected.keys.toSet()
    LaunchedEffect(inFront, deviceIds) {
        if (inFront) openForNewCar(context, connected.values.toList())
    }
}

private fun openForNewCar(context: Context, devices: List<AudioDeviceInfo>) {
    val cars = devices.filter { it.id !in CarMode.answeredDevices && CarAudioDevices.isCar(context, it) }
    if (cars.isEmpty()) return
    cars.forEach { CarMode.answeredDevices += it.id }
    CarMode.open()
}
