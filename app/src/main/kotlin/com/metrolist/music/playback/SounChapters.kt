/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import android.content.Context
import android.util.LruCache
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/** One part of a long track (a song inside a full-album upload, a section of a mix). */
data class Chapter(
    val startMs: Long,
    val title: String,
)

/** The chapters of the track [mediaId]; empty when it has none. */
data class ChapterState(
    val mediaId: String? = null,
    val chapters: List<Chapter> = emptyList(),
)

/**
 * Reads a tracklist out of a video description: lines with a start time such as
 * `0:00 Intro`, `12:40 - Song`, `1. Song (1:02:03)` or `00:00 - 04:12 Song`.
 */
object ChapterParser {
    private val TIME = Regex("""(?<![\d:])(?:(\d{1,2}):)?(\d{1,2}):(\d{2})(?![\d:])""")
    private val LEADING_NUMBER = Regex("""^(?:#|No\.?\s*)?\d{1,3}\s*[.)\-–—:]\s+""", RegexOption.IGNORE_CASE)
    private val EMPTY_BRACKETS = Regex("""[(\[{]\s*[)\]}]""")
    private const val EDGE_CHARS = " \t-–—|:•·*~>/,=+"

    /** A list counts only if it starts near the beginning; YouTube's own rule is three or more parts. */
    private const val FIRST_START_MAX_MS = 15_000L
    private const val MIN_CHAPTERS = 3

    fun parse(description: String?, durationMs: Long): List<Chapter> {
        if (description.isNullOrBlank()) return emptyList()
        val candidates =
            description.lineSequence().mapNotNull { line ->
                val match = TIME.find(line) ?: return@mapNotNull null
                val start = match.toMs() ?: return@mapNotNull null
                Chapter(start, cleanTitle(line))
            }.toList()

        // Take the longest run that starts near 0:00 and keeps going forward: descriptions can
        // hold more than one list (a tracklist, then lyrics or credits with times in them).
        var best = emptyList<Chapter>()
        var run = mutableListOf<Chapter>()
        for (candidate in candidates) {
            val last = run.lastOrNull()
            when {
                candidate.startMs <= FIRST_START_MAX_MS && (last == null || candidate.startMs <= last.startMs) -> {
                    if (run.size > best.size) best = run
                    run = mutableListOf(candidate)
                }
                last != null && candidate.startMs > last.startMs -> run += candidate
                else -> {
                    if (run.size > best.size) best = run
                    run = mutableListOf()
                }
            }
        }
        if (run.size > best.size) best = run

        val chapters =
            best
                .filter { durationMs <= 0L || it.startMs < durationMs }
                .mapIndexed { index, chapter -> if (index == 0) chapter.copy(startMs = 0L) else chapter }
        return if (chapters.size >= MIN_CHAPTERS) chapters else emptyList()
    }

    private fun MatchResult.toMs(): Long? {
        val hours = groupValues[1].takeIf { it.isNotEmpty() }?.toLong() ?: 0L
        val minutes = groupValues[2].toLong()
        val seconds = groupValues[3].toLong()
        if (seconds >= 60 || (groupValues[1].isNotEmpty() && minutes >= 60)) return null
        return ((hours * 60 + minutes) * 60 + seconds) * 1000
    }

    private fun cleanTitle(line: String): String {
        var title = TIME.replace(line, " ")
        title = EMPTY_BRACKETS.replace(title, " ")
        title = title.trim { it in EDGE_CHARS }
        title = LEADING_NUMBER.replace(title, "")
        title = title.trim { it in EDGE_CHARS }
        title = title.removeSurrounding("\"").removeSurrounding("“", "”").trim()
        return title.replace(Regex("""\s{2,}"""), " ")
    }
}

/**
 * Finds and remembers the chapters of long tracks. The description is fetched once per track;
 * the result (also "no chapters") is kept on disk so the next play needs no network.
 */
class ChapterRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val memory = LruCache<String, List<Chapter>>(64)

    suspend fun chapters(videoId: String, durationMs: Long): List<Chapter> {
        memory.get(videoId)?.let { return it }
        return withContext(Dispatchers.IO) {
            prefs.getString(videoId, null)?.let { stored ->
                decode(stored)?.let { cached ->
                    memory.put(videoId, cached)
                    return@withContext cached
                }
            }
            // Don't remember failed lookups: the next play tries again.
            val description = fetchDescription(videoId) ?: return@withContext emptyList()
            val chapters = ChapterParser.parse(description, durationMs)
            memory.put(videoId, chapters)
            store(videoId, chapters)
            chapters
        }
    }

    /** Chapters already found for [videoId], without going to the network; null if never looked up. */
    fun cached(videoId: String): List<Chapter>? =
        memory.get(videoId) ?: prefs.getString(videoId, null)?.let(::decode)

    private suspend fun fetchDescription(videoId: String): String? {
        for (client in listOf(YouTubeClient.ANDROID_VR_NO_AUTH, YouTubeClient.WEB_REMIX, YouTubeClient.IOS)) {
            val description =
                YouTube.player(videoId, client = client)
                    .onFailure { Timber.tag(TAG).d(it, "No description from ${client.clientName}") }
                    .getOrNull()
                    ?.videoDetails
                    ?.shortDescription
            if (description != null) return description
        }
        return null
    }

    private fun store(videoId: String, chapters: List<Chapter>) {
        val array = JSONArray()
        chapters.forEach { array.put(JSONObject().put("s", it.startMs).put("t", it.title)) }
        val editor = prefs.edit()
        if (prefs.all.size >= MAX_STORED) prefs.all.keys.take(prefs.all.size - MAX_STORED + 1).forEach(editor::remove)
        editor.putString(videoId, array.toString()).apply()
    }

    private fun decode(stored: String): List<Chapter>? =
        runCatching {
            val array = JSONArray(stored)
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                Chapter(item.getLong("s"), item.optString("t"))
            }
        }.getOrNull()

    companion object {
        private const val TAG = "SounChapters"
        private const val PREFS = "soun_chapters"
        private const val MAX_STORED = 400

        /** Only tracks at least this long are looked up. */
        const val MIN_TRACK_MS = 8L * 60 * 1000
    }
}
