/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Looks for a new Soun release every few hours, also while the app is closed, and posts a
 * notification when one is out. While the app is on screen the update window shows instead.
 */
class SounUpdateJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            runCatching {
                SounUpdater.check(applicationContext)?.let { update ->
                    SounUpdater.pending.value = update
                    if (!SounUpdater.inForeground) SounUpdater.notifyIfNew(applicationContext, update)
                }
            }.onFailure { Timber.tag("SounUpdater").w(it, "Background update check failed") }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        scope.coroutineContext.cancelChildren()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val JOB_ID = 0x534F554E // "SOUN"
        private val INTERVAL_MS = TimeUnit.HOURS.toMillis(3)
        private val FLEX_MS = TimeUnit.HOURS.toMillis(1)

        /** Schedules the periodic check once; release builds only. */
        fun schedule(context: Context) {
            if (SounUpdater.installedCommit == null) return
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (scheduler.getPendingJob(JOB_ID) != null) return
            val job =
                JobInfo.Builder(JOB_ID, ComponentName(context, SounUpdateJobService::class.java))
                    .setPeriodic(INTERVAL_MS, FLEX_MS)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build()
            runCatching { scheduler.schedule(job) }
                .onFailure { Timber.tag("SounUpdater").w(it, "Could not schedule update checks") }
        }
    }
}
