/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.music.MainActivity
import com.metrolist.music.R
import com.metrolist.music.constants.NewReleaseNotificationsKey
import com.metrolist.music.db.DatabaseDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ReleaseWatcherEntryPoint {
    fun databaseDao(): DatabaseDao
}

/**
 * Tells you when an artist you follow puts out a new album, EP or single. A few times a day it
 * looks at the artists' pages; the first look at an artist only learns what is already out, so
 * only releases that appear after that are announced.
 */
object SounReleaseWatcher {
    private const val TAG = "SounReleases"
    private const val PREFS = "soun_release_watcher"
    private const val KEY_CURSOR = "cursor"
    private const val CHANNEL = "new_releases"
    private const val ARTISTS_PER_RUN = 40
    private const val MAX_NOTIFICATIONS_PER_RUN = 3
    private const val SUMMARY_ID = 0x52454C

    suspend fun check(context: Context) {
        val app = context.applicationContext
        if (app.dataStore.data.first()[NewReleaseNotificationsKey] == false) return
        val dao = EntryPointAccessors.fromApplication(app, ReleaseWatcherEntryPoint::class.java).databaseDao()
        val artists = dao.bookmarkedArtistEntitiesByNameAsc().filter { it.id.startsWith("UC") }
        if (artists.isEmpty()) return
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // Large libraries are covered a slice at a time.
        val start = prefs.getInt(KEY_CURSOR, 0).let { if (it >= artists.size) 0 else it }
        val slice = (artists.drop(start) + artists.take(start)).take(ARTISTS_PER_RUN)
        prefs.edit().putInt(KEY_CURSOR, (start + slice.size) % artists.size).apply()

        val thisYear = LocalDate.now().year
        val fresh = mutableListOf<Pair<String, AlbumItem>>()
        for (artist in slice) {
            val page = YouTube.artist(artist.id).getOrNull() ?: continue
            val releases = page.sections.flatMap { it.items }.filterIsInstance<AlbumItem>().distinctBy { it.browseId }
            if (releases.isEmpty()) continue
            val key = "seen_${artist.id}"
            val seen = prefs.getStringSet(key, null)
            if (seen != null) {
                // Only recent releases: an old album that shows up in the list later is not news.
                releases
                    .filter { it.browseId !in seen && (it.year ?: thisYear) >= thisYear - 1 }
                    .forEach { fresh += artist.name to it }
            }
            prefs.edit().putStringSet(key, (seen.orEmpty() + releases.map { it.browseId }).toSet()).apply()
            delay(600)
        }
        if (fresh.isNotEmpty()) notify(app, fresh)
    }

    private fun notify(context: Context, releases: List<Pair<String, AlbumItem>>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createChannel(context)
        val manager = NotificationManagerCompat.from(context)
        releases.take(MAX_NOTIFICATIONS_PER_RUN).forEach { (artistName, album) ->
            val open =
                PendingIntent.getActivity(
                    context,
                    album.browseId.hashCode(),
                    Intent(context, MainActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .setData("https://music.youtube.com/browse/${album.browseId}".toUri())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val notification =
                NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.small_icon)
                    .setContentTitle(context.getString(R.string.soun_new_release_title, artistName))
                    .setContentText(album.title)
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .setGroup(CHANNEL)
                    .build()
            runCatching { manager.notify(album.browseId.hashCode(), notification) }
        }
        if (releases.size > MAX_NOTIFICATIONS_PER_RUN) {
            val more = releases.size - MAX_NOTIFICATIONS_PER_RUN
            val summary =
                NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.small_icon)
                    .setContentTitle(context.resources.getQuantityString(R.plurals.soun_new_releases_more, more, more))
                    .setGroup(CHANNEL)
                    .setGroupSummary(true)
                    .setAutoCancel(true)
                    .build()
            runCatching { manager.notify(SUMMARY_ID, summary) }
        }
    }

    private fun createChannel(context: Context) {
        val channel =
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.soun_new_releases_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.soun_new_releases_channel_desc) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}

/** Runs [SounReleaseWatcher] a few times a day, also while the app is closed. */
class SounReleaseJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            runCatching { SounReleaseWatcher.check(applicationContext) }
                .onFailure { Timber.tag("SounReleases").w(it, "New release check failed") }
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
        private const val JOB_ID = 0x52454C53 // "RELS"

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (scheduler.getPendingJob(JOB_ID) != null) return
            val job =
                JobInfo.Builder(JOB_ID, ComponentName(context, SounReleaseJobService::class.java))
                    .setPeriodic(TimeUnit.HOURS.toMillis(8), TimeUnit.HOURS.toMillis(2))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build()
            runCatching { context.getSystemService(JobScheduler::class.java)?.schedule(job) }
                .onFailure { Timber.tag("SounReleases").w(it, "Could not schedule new release checks") }
        }
    }
}
