package com.metrolist.music.playback

import androidx.media3.exoplayer.offline.Download

data class FailedDownload(val id: String, val title: String)

data class DownloadOverview(
    val completed: Int,
    val pending: Int,
    val stopped: Int,
    val failed: List<FailedDownload>,
    val progress: Float?,
)

fun downloadOverview(downloads: Collection<Download>): DownloadOverview {
    val pending = downloads.filter {
        it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED || it.state == Download.STATE_STOPPED
    }
    val knownSize = pending.isNotEmpty() && pending.all { it.contentLength > 0 }
    return DownloadOverview(
        completed = downloads.count { it.state == Download.STATE_COMPLETED },
        pending = pending.size,
        stopped = pending.count { it.state == Download.STATE_STOPPED },
        failed = downloads.filter { it.state == Download.STATE_FAILED }.map {
            FailedDownload(it.request.id, it.request.data.toString(Charsets.UTF_8).ifBlank { it.request.id })
        },
        progress = if (knownSize) {
            (pending.sumOf { it.bytesDownloaded.coerceAtLeast(0).toDouble() } /
                pending.sumOf { it.contentLength.toDouble() }).toFloat().coerceIn(0f, 1f)
        } else null,
    )
}
