/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.carmode

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import java.text.Normalizer

/** Guesses from a Bluetooth name alone whether the device is a car. */
object CarDeviceNames {
    // Matched as whole words only: "audi" would otherwise match "audio" and "car" would match "card".
    private val words = setOf(
        "car", "cars", "auto", "vehicle", "carkit", "carplay", "carstereo", "caraudio",
        "vw", "bmw", "audi", "kia", "ford", "fiat", "jeep", "seat", "opel",
        "sync", "mmi", "idrive", "comand", "entune", "byd", "dodge", "buick", "gmc", "saab",
    )

    // Distinctive enough to match inside a longer name, such as "MyToyota" or "PEUGEOT208".
    private val parts = listOf(
        "vehicle", "carkit", "carplay", "carstereo", "caraudio", "medianav", "rlink", "uconnect",
        "volkswagen", "mercedes", "toyota", "lexus", "honda", "mazda", "nissan", "subaru", "suzuki", "mitsubishi",
        "hyundai", "peugeot", "citroen", "renault", "dacia", "skoda", "cupra", "volvo", "tesla",
        "porsche", "chevrolet", "cadillac", "chrysler", "infiniti", "landrover", "rangerover",
        "jaguar", "alfaromeo", "lancia", "vauxhall", "polestar", "geely", "chery", "haval",
        "kenwood", "alpine",
    )

    private val camelBoundary = Regex("(?<=[a-z])(?=[A-Z])")
    private val diacritics = Regex("\\p{Mn}+")
    private val wordPattern = Regex("[a-z]+")
    private val notAlphanumeric = Regex("[^a-z0-9]+")

    fun looksLikeCar(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val plain = Normalizer.normalize(name.replace(camelBoundary, " "), Normalizer.Form.NFD)
            .replace(diacritics, "")
            .lowercase()
        if (wordPattern.findAll(plain).any { it.value in words }) return true
        val compact = plain.replace(notAlphanumeric, "")
        return parts.any { it in compact }
    }
}

/** Tells car audio apart from other Bluetooth audio devices. */
internal object CarAudioDevices {
    /** Whether the Bluetooth device class can be read; without it only the device name is used. */
    fun canReadDeviceClass(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    fun isBluetoothAudio(device: AudioDeviceInfo): Boolean =
        device.isSink && device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP

    fun isCar(context: Context, device: AudioDeviceInfo): Boolean {
        if (!isBluetoothAudio(device)) return false
        return CarDeviceNames.looksLikeCar(device.productName?.toString()) || hasCarAudioClass(context, device)
    }

    @SuppressLint("MissingPermission")
    private fun hasCarAudioClass(context: Context, device: AudioDeviceInfo): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !canReadDeviceClass(context)) return false
        // Without the permission the address can come back anonymised, which this check rejects.
        val address = device.address.takeIf { BluetoothAdapter.checkBluetoothAddress(it) } ?: return false
        return try {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return false
            adapter.getRemoteDevice(address).bluetoothClass?.deviceClass ==
                BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}
