package com.metrolist.music.ui.component

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.R
import com.metrolist.music.playback.DownloadOverview
import com.metrolist.music.playback.downloadOverview
import com.metrolist.music.ui.theme.sounSurface
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
    val paused = state.paused
    val canResume = paused || overview.stopped > 0
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().sounSurface().padding(16.dp)) {
        Text(stringResource(R.string.soun_offline_library), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.soun_download_summary, overview.completed, overview.pending, overview.failed.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (overview.pending > 0) {
            Spacer(Modifier.height(12.dp))
            val progress = overview.progress
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            } else if (!canResume) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(
                stringResource(if (canResume) R.string.soun_downloads_paused else R.string.soun_downloads_waiting),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { util.setDownloadsPaused(!canResume) }) {
                Text(stringResource(if (canResume) R.string.soun_resume_downloads else R.string.soun_pause_downloads))
            }
        }
        overview.failed.take(5).forEach { failed ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(failed.title, Modifier.weight(1f), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                TextButton(onClick = { util.retryFailedDownloads(failed.id) }) { Text(stringResource(R.string.retry)) }
            }
        }
        if (overview.failed.size > 5) {
            Text(
                stringResource(R.string.soun_more_failed_downloads, overview.failed.size - 5),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (overview.failed.isNotEmpty()) {
            TextButton(onClick = { util.retryFailedDownloads() }) { Text(stringResource(R.string.soun_retry_failed_downloads)) }
        }
        TextButton(onClick = onStorageClick) {
            Text(stringResource(R.string.soun_download_storage, Formatter.formatShortFileSize(context, state.storage)))
        }
    }
}

private data class DownloadPanelState(val overview: DownloadOverview, val paused: Boolean, val storage: Long)
