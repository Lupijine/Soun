package com.metrolist.music.ui.component

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.R
import com.metrolist.music.playback.DownloadOverview
import com.metrolist.music.playback.downloadOverview
import kotlinx.coroutines.delay

@Composable
fun DownloadOverviewCard(onStorageClick: () -> Unit) {
    val util = LocalDownloadUtil.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by produceState(
        DownloadPanelState(downloadOverview(util.downloads.value.values), util.downloadManager.downloadsPaused, util.downloadCache.cacheSpace),
        util, lifecycleOwner,
    ) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = DownloadPanelState(downloadOverview(util.downloads.value.values),
                    util.downloadManager.downloadsPaused, util.downloadCache.cacheSpace)
                delay(1000)
            }
        }
    }
    DownloadStatusStrip(state, onStorageClick,
        onPauseChange = util::setDownloadsPaused,
        onRetry = { util.retryFailedDownloads(it) })
}

@Composable
internal fun DownloadStatusStrip(
    state: DownloadPanelState,
    onStorageClick: () -> Unit,
    onPauseChange: (Boolean) -> Unit,
    onRetry: (String?) -> Unit,
) {
    val context = LocalContext.current
    val overview = state.overview
    val canResume = state.paused || overview.stopped > 0
    var expanded by rememberSaveable { mutableStateOf(false) }
    val hasIssues = overview.pending > 0 || overview.failed.isNotEmpty()
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onStorageClick)
                .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.download), stringResource(R.string.soun_manage_storage), Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Formatter.formatShortFileSize(context, state.storage), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (overview.pending > 0) {
                Text(stringResource(R.string.soun_download_pending_count, overview.pending),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { onPauseChange(!canResume) }) {
                    Icon(painterResource(if (canResume) R.drawable.play else R.drawable.pause),
                        stringResource(if (canResume) R.string.soun_resume_downloads else R.string.soun_pause_downloads), Modifier.size(18.dp))
                }
            }
            if (overview.failed.isNotEmpty()) {
                TextButton(onClick = { onRetry(null) }) {
                    Text(stringResource(R.string.soun_retry_count, overview.failed.size), style = MaterialTheme.typography.labelMedium)
                }
            }
            if (!hasIssues) TextButton(onClick = onStorageClick) {
                Text(stringResource(R.string.soun_manage_storage), style = MaterialTheme.typography.labelMedium)
            }
            if (hasIssues) IconButton(onClick = { expanded = !expanded }) {
                Icon(painterResource(if (expanded) R.drawable.expand_less else R.drawable.expand_more),
                    stringResource(if (expanded) R.string.soun_hide_download_details else R.string.soun_show_download_details), Modifier.size(18.dp))
            }
        }
        if (overview.pending > 0) {
            val progress = overview.progress
            if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp))
            else if (!canResume) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
        }
        if (expanded && hasIssues) {
            if (overview.pending > 0) Text(
                stringResource(if (canResume) R.string.soun_downloads_paused else R.string.soun_downloads_waiting),
                Modifier.padding(horizontal = 8.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            overview.failed.take(5).forEach { failed ->
                Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(failed.title, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { onRetry(failed.id) }) { Text(stringResource(R.string.retry)) }
                }
            }
            if (overview.failed.size > 5) Text(stringResource(R.string.soun_more_failed_downloads, overview.failed.size - 5),
                Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

internal data class DownloadPanelState(val overview: DownloadOverview, val paused: Boolean, val storage: Long)
