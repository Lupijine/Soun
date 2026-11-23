/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.carmode

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.metrolist.music.R
import com.metrolist.music.constants.CarModeAutoOpenKey
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.utils.rememberPreference

/** The "Car mode" section of the player settings. */
@Composable
fun CarModeSettingsGroup() {
    val context = LocalContext.current
    val (autoOpen, onAutoOpenChange) = rememberPreference(CarModeAutoOpenKey, defaultValue = false)
    var canReadDeviceClass by remember { mutableStateOf(CarAudioDevices.canReadDeviceClass(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canReadDeviceClass = CarAudioDevices.canReadDeviceClass(context)
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            canReadDeviceClass = granted
        }

    val setAutoOpen: (Boolean) -> Unit = { enabled ->
        onAutoOpenChange(enabled)
        // Without the permission the switch still works, by the device name only.
        if (enabled && !canReadDeviceClass && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    Material3SettingsGroup(
        title = stringResource(R.string.soun_car_mode),
        items =
            listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.soun_car),
                    title = { Text(stringResource(R.string.soun_car_mode_open)) },
                    description = { Text(stringResource(R.string.soun_car_mode_open_desc)) },
                    onClick = CarMode::open,
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.bluetooth),
                    title = { Text(stringResource(R.string.soun_car_mode_auto_open)) },
                    description = {
                        Text(
                            stringResource(
                                if (autoOpen && !canReadDeviceClass) {
                                    R.string.soun_car_mode_auto_open_by_name
                                } else {
                                    R.string.soun_car_mode_auto_open_desc
                                },
                            ),
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = autoOpen,
                            onCheckedChange = setAutoOpen,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(if (autoOpen) R.drawable.check else R.drawable.close),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize),
                                )
                            },
                        )
                    },
                    onClick = { setAutoOpen(!autoOpen) },
                ),
            ),
    )
}
