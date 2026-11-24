/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import android.content.Intent
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.queues.Queue
import com.metrolist.music.playback.queues.YouTubeQueue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A second of a song worth coming back to. */
data class SavedMoment(
    val id: String,
    val songId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val positionMs: Long,
    val savedAt: Long,
)

/** Saved moments, kept on the device (newest first). */
object SounMoments {
    private const val PREFS = "soun_moments"
    private const val KEY = "moments"
    private const val MAX_MOMENTS = 500

    private val state = MutableStateFlow<List<SavedMoment>?>(null)

    fun moments(context: Context): StateFlow<List<SavedMoment>?> {
        if (state.value == null) state.value = load(context)
        return state.asStateFlow()
    }

    fun save(context: Context, metadata: MediaMetadata, positionMs: Long): SavedMoment {
        val moment =
            SavedMoment(
                id = "${metadata.id}@${System.currentTimeMillis()}",
                songId = metadata.id,
                title = metadata.title,
                artist = metadata.artists.joinToString { it.name },
                thumbnailUrl = metadata.thumbnailUrl,
                positionMs = positionMs.coerceAtLeast(0L),
                savedAt = System.currentTimeMillis(),
            )
        update(context) { (listOf(moment) + it).take(MAX_MOMENTS) }
        return moment
    }

    fun remove(context: Context, moment: SavedMoment) {
        update(context) { list -> list.filterNot { it.id == moment.id } }
    }

    /** The song's link that starts at the moment, e.g. `https://music.youtube.com/watch?v=ID&t=754`. */
    fun shareLink(songId: String, positionMs: Long): String =
        "https://music.youtube.com/watch?v=$songId&t=${positionMs / 1000}"

    fun share(context: Context, title: String, songId: String, positionMs: Long) {
        val text = "$title · ${makeTimeString(positionMs)}\n${shareLink(songId, positionMs)}"
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** A queue that plays [songId] (and its radio after it) from [positionMs]. */
    fun queueFrom(songId: String, positionMs: Long, preload: MediaMetadata? = null): Queue =
        StartAtQueue(YouTubeQueue(WatchEndpoint(videoId = songId), preload), positionMs)

    /** Reads a link's start time: `t=754`, `t=754s`, `t=12m34s`, `t=1h2m3s`. */
    fun parseStartTime(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        value.toLongOrNull()?.let { return it * 1000 }
        val match = Regex("""^(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?$""").matchEntire(value.trim()) ?: return null
        val (h, m, s) = match.destructured
        if (h.isEmpty() && m.isEmpty() && s.isEmpty()) return null
        return ((h.toLongOrNull() ?: 0) * 3600 + (m.toLongOrNull() ?: 0) * 60 + (s.toLongOrNull() ?: 0)) * 1000
    }

    private fun update(context: Context, change: (List<SavedMoment>) -> List<SavedMoment>) {
        val current = state.value ?: load(context)
        val next = change(current)
        state.value = next
        store(context, next)
    }

    private fun load(context: Context): List<SavedMoment> =
        runCatching {
            val raw = prefs(context).getString(KEY, null) ?: return emptyList()
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                SavedMoment(
                    id = o.getString("id"),
                    songId = o.getString("song"),
                    title = o.optString("title"),
                    artist = o.optString("artist"),
                    thumbnailUrl = o.optString("thumb").takeIf { it.isNotBlank() },
                    positionMs = o.getLong("pos"),
                    savedAt = o.optLong("at"),
                )
            }
        }.getOrDefault(emptyList())

    private fun store(context: Context, moments: List<SavedMoment>) {
        val array = JSONArray()
        moments.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("song", it.songId)
                    .put("title", it.title)
                    .put("artist", it.artist)
                    .put("thumb", it.thumbnailUrl ?: "")
                    .put("pos", it.positionMs)
                    .put("at", it.savedAt),
            )
        }
        prefs(context).edit().putString(KEY, array.toString()).apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** [base], but starting [positionMs] into its first song. */
class StartAtQueue(
    private val base: Queue,
    private val positionMs: Long,
) : Queue by base {
    override suspend fun getInitialStatus(): Queue.Status = base.getInitialStatus().copy(position = positionMs)
}
