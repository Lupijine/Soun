package com.metrolist.music.ui.component

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.R
import com.metrolist.music.playback.DownloadOverview
import com.metrolist.music.playback.downloadOverview
import com.metrolist.music.ui.theme.sounPanel
import kotlinx.coroutines.delay

@Composable
fun DownloadOverviewCard(onStorageClick: () -> Unit) {
    val util = LocalDownloadUtil.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val state by produceState(
        DownloadPanelState(downloadOverview(util.downloads.value.values), util.downloadManager.downloadsPaused, util.downloadCache.cacheSpace),
        util, lifecycleOwner,
    ) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = DownloadPanelState(
                    downloadOverview(util.downloads.value.values),
                    util.downloadManager.downloadsPaused,
                    util.downloadCache.cacheSpace,
                )
                delay(1000)
            }
        }
    }
    val overview = state.overview
    val canResume = state.paused || overview.stopped > 0
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth().sounPanel().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.soun_offline_library), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DownloadStat(overview.completed, stringResource(R.string.soun_download_ready), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            DownloadStat(overview.pending, stringResource(R.string.soun_download_pending), MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            DownloadStat(overview.failed.size, stringResource(R.string.soun_download_failed),
                if (overview.failed.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error, Modifier.weight(1f))
        }
        if (overview.pending > 0) {
            val progress = overview.progress
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            } else if (!canResume) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(stringResource(if (canResume) R.string.soun_downloads_paused else R.string.soun_downloads_waiting),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = { util.setDownloadsPaused(!canResume) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (canResume) R.string.soun_resume_downloads else R.string.soun_pause_downloads))
            }
        }
        overview.failed.take(5).forEach { failed ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(failed.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { util.retryFailedDownloads(failed.id) }) { Text(stringResource(R.string.retry)) }
            }
        }
        if (overview.failed.size > 5) Text(stringResource(R.string.soun_more_failed_downloads, overview.failed.size - 5),
            style = MaterialTheme.typography.bodySmall)
        if (overview.failed.isNotEmpty()) {
            OutlinedButton(onClick = { util.retryFailedDownloads() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.soun_retry_failed_downloads))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f))
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onStorageClick),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.storage), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.soun_storage_used), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Formatter.formatShortFileSize(context, state.storage), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
            }
            Text(stringResource(R.string.soun_manage_storage), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            Icon(painterResource(R.drawable.arrow_forward), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun DownloadStat(value: Int, label: String, accent: Color, modifier: Modifier) {
    Column(modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(value.toString(), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(5.dp).background(accent, CircleShape))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private data class DownloadPanelState(val overview: DownloadOverview, val paused: Boolean, val storage: Long)
