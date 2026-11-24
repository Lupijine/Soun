/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.metrolist.music.R
import com.metrolist.music.ui.theme.SounGlassDialog
import com.metrolist.music.utils.SounUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Checks the Soun releases now and then and, when a newer build exists, offers it in a glass
 * window with the list of changes; "Update" downloads it and opens the system installer.
 */
@Composable
fun SounUpdatePrompt() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val update by SounUpdater.pending.collectAsState()
    var progress by remember { mutableStateOf<Float?>(null) }
    var failed by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    // Check on every launch and whenever Soun comes back to the screen after a while.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            SounUpdater.inForeground = true
            try {
                if (SounUpdater.isCheckDue(context)) {
                    SounUpdater.check(context)?.let { SounUpdater.pending.value = it }
                }
                awaitCancellation()
            } finally {
                SounUpdater.inForeground = false
            }
        }
    }

    val current = update ?: return
    LaunchedEffect(current.tag) { SounUpdater.cancelNotification(context) }
    val later = {
        SounUpdater.dismiss(current)
        failed = false
    }

    SounGlassDialog(onDismiss = { if (progress == null) later() }) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.small_icon),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Column {
                    Text(
                        stringResource(R.string.soun_update_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        current.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                    )
                }
            }

            if (current.changes.isNotEmpty()) {
                Text(
                    stringResource(R.string.soun_update_whats_new),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    current.changes.forEach { change ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                Modifier
                                    .padding(top = 8.dp)
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                            Text(change, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            progress?.let { value ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = { value },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    )
                    Text(
                        stringResource(R.string.soun_update_downloading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                    )
                }
            }

            if (failed) {
                Text(
                    stringResource(R.string.soun_update_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = later, enabled = progress == null) {
                    Text(stringResource(R.string.soun_update_later))
                }
                Button(
                    enabled = progress == null,
                    shape = RoundedCornerShape(50),
                    onClick = {
                        failed = false
                        progress = 0f
                        scope.launch {
                            try {
                                val apk = SounUpdater.download(context, current) { progress = it }
                                SounUpdater.install(context, apk)
                                SounUpdater.pending.value = null
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Timber.tag("SounUpdater").w(e, "Update download failed")
                                failed = true
                            } finally {
                                progress = null
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.soun_update_install), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
