/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.metrolist.music.BuildConfig
import com.metrolist.music.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Updates straight from the Soun GitHub releases. Release builds carry their commit in the
 * version name ("13.7.0+abc1234"); the newest `soun-<commit>` release is offered when GitHub
 * says it is ahead of the installed commit. Debug builds have no commit and are never offered one.
 */
object SounUpdater {
    private const val REPO_API = "https://api.github.com/repos/Lupijine/Soun"
    private const val PREFS = "soun_updater"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_NOTIFIED_TAG = "notified_tag"

    /** Re-check when the app comes back to the screen at most this often (always on launch). */
    private const val FOREGROUND_CHECK_INTERVAL_MS = 20L * 60 * 1000
    private const val MAX_CHANGES = 8
    private const val NOTIFICATION_CHANNEL = "updates"
    private const val NOTIFICATION_ID = 0x534F55

    data class Update(
        val tag: String,
        val title: String,
        val changes: List<String>,
        val apkUrl: String,
        val sizeBytes: Long,
    )

    /** The update waiting to be shown; set by the automatic check or by "Check for updates". */
    val pending = MutableStateFlow<Update?>(null)

    /** True while the app is on screen; a background check then shows the prompt, not a notification. */
    @Volatile
    var inForeground = false

    /** Release put off with "Later": not offered again until the app is launched anew. */
    @Volatile
    private var dismissedTag: String? = null

    @Volatile
    private var checkedThisLaunch = false

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val installedCommit: String? =
        BuildConfig.VERSION_NAME.substringAfter('+', "").lowercase().takeIf { it.matches(Regex("[0-9a-f]{7,40}")) }

    /** True on every launch, and when the app returns to the screen after a while. */
    fun isCheckDue(context: Context): Boolean {
        if (installedCommit == null) return false
        if (!checkedThisLaunch) return true
        val last = prefs(context).getLong(KEY_LAST_CHECK, 0L)
        return System.currentTimeMillis() - last > FOREGROUND_CHECK_INTERVAL_MS
    }

    /** "Later": hides this release until the next launch. */
    fun dismiss(update: Update) {
        dismissedTag = update.tag
        if (pending.value?.tag == update.tag) pending.value = null
    }

    /**
     * Looks for a newer release. [ignoreSkipped] is for a manual check, which should also show a
     * release put off with "Later".
     */
    suspend fun check(context: Context, ignoreSkipped: Boolean = false): Update? = withContext(Dispatchers.IO) {
        val installed = installedCommit ?: return@withContext null
        checkedThisLaunch = true
        prefs(context).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        runCatching {
            val release = JSONObject(get("$REPO_API/releases/latest") ?: return@runCatching null)
            val tag = release.optString("tag_name")
            val latestCommit = tag.removePrefix("soun-").lowercase()
            if (!tag.startsWith("soun-") || installed.startsWith(latestCommit) || latestCommit.startsWith(installed)) {
                return@runCatching null
            }
            if (!ignoreSkipped && dismissedTag == tag) return@runCatching null

            val assets = release.optJSONArray("assets") ?: return@runCatching null
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") } ?: return@runCatching null

            // Only offer releases that are really ahead of what is installed, and list what changed.
            val compare = JSONObject(get("$REPO_API/compare/$installed...$latestCommit") ?: return@runCatching null)
            if (compare.optString("status") != "ahead") return@runCatching null
            val commits = compare.optJSONArray("commits")
            val changes = if (commits == null) emptyList() else {
                (0 until commits.length())
                    .map { commits.getJSONObject(it).getJSONObject("commit").optString("message").lineSequence().first().trim() }
                    .filter { it.isNotBlank() && !it.startsWith("Merge ") }
                    .map { it.substringAfter("): ").substringAfter(": ").replaceFirstChar(Char::uppercaseChar) }
                    .reversed()
                    .take(MAX_CHANGES)
            }

            Update(
                tag = tag,
                title = release.optString("name").ifBlank { tag },
                changes = changes,
                apkUrl = apk.optString("browser_download_url"),
                sizeBytes = apk.optLong("size"),
            )
        }.getOrNull()
    }

    /** Downloads the APK into the cache, reporting progress from 0 to 1. */
    suspend fun download(context: Context, update: Update, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "soun-update.apk")
        val response = client.newCall(Request.Builder().url(update.apkUrl).build()).execute()
        response.use {
            check(it.isSuccessful) { "Download failed: HTTP ${it.code}" }
            val body = checkNotNull(it.body) { "Empty download" }
            val total = body.contentLength().takeIf { len -> len > 0 } ?: update.sizeBytes
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        target
    }

    /** Hands the APK to the system installer (it asks to allow installs from Soun the first time). */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Shows a system notification for [update] once per release, so a new version is noticed
     * even while Soun is closed. Tapping it opens the app, which shows the update window.
     */
    fun notifyIfNew(context: Context, update: Update) {
        val prefs = prefs(context)
        if (prefs.getString(KEY_NOTIFIED_TAG, null) == update.tag) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val launch =
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                ?: return
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val summary = update.changes.firstOrNull() ?: update.title
        val details = update.changes.joinToString("\n") { "• $it" }.ifBlank { summary }
        val notification =
            NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.update)
                .setContentTitle(context.getString(R.string.soun_update_title))
                .setContentText(summary)
                .setStyle(NotificationCompat.BigTextStyle().bigText(details))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
            .onSuccess { prefs.edit().putString(KEY_NOTIFIED_TAG, update.tag).apply() }
    }

    /** Removes the update notification (the window is showing it, or it was installed). */
    fun cancelNotification(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }

    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .build()
        return client.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
