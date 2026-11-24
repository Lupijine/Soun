/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A personal tag ("Workout", "Rain", "Focus") and the songs that carry it, newest first. */
data class SongTag(
    val name: String,
    val songIds: List<String>,
    val createdAt: Long,
)

/**
 * Personal tags for songs. Unlike playlists, one song can carry many tags, and a tag is played
 * or browsed like a playlist. Kept on the device.
 */
object SounTags {
    private const val PREFS = "soun_tags"
    private const val KEY = "tags"
    const val MAX_NAME = 32

    private val state = MutableStateFlow<List<SongTag>?>(null)

    fun tags(context: Context): StateFlow<List<SongTag>?> {
        if (state.value == null) state.value = load(context)
        return state.asStateFlow()
    }

    fun current(context: Context): List<SongTag> = state.value ?: load(context).also { state.value = it }

    /** Tag names, tidied: trimmed, single spaces, capped. Blank names are refused. */
    fun cleanName(name: String): String? =
        name.trim().replace(Regex("""\s+"""), " ").take(MAX_NAME).takeIf { it.isNotBlank() }

    /** Adds [songIds] to [name] (creating the tag) or, when they all carry it already, takes it off them. */
    fun toggle(context: Context, name: String, songIds: List<String>) {
        update(context) { tags -> toggled(tags, name, songIds, System.currentTimeMillis()) }
    }

    fun create(context: Context, name: String, songIds: List<String> = emptyList()) {
        val clean = cleanName(name) ?: return
        update(context) { tags ->
            val existing = tags.firstOrNull { it.name.equals(clean, ignoreCase = true) }
            if (existing == null) {
                tags + SongTag(clean, songIds.distinct(), System.currentTimeMillis())
            } else {
                tags.map { if (it === existing) it.copy(songIds = (songIds + it.songIds).distinct()) else it }
            }
        }
    }

    fun rename(context: Context, from: String, to: String) {
        val clean = cleanName(to) ?: return
        update(context) { tags ->
            if (tags.any { it.name.equals(clean, ignoreCase = true) && it.name != from }) return@update tags
            tags.map { if (it.name == from) it.copy(name = clean) else it }
        }
    }

    fun delete(context: Context, name: String) {
        update(context) { tags -> tags.filterNot { it.name == name } }
    }

    fun removeSong(context: Context, name: String, songId: String) {
        update(context) { tags -> tags.map { if (it.name == name) it.copy(songIds = it.songIds - songId) else it } }
    }

    private fun update(context: Context, change: (List<SongTag>) -> List<SongTag>) {
        val next = change(current(context))
        state.value = next
        store(context, next)
    }

    private fun load(context: Context): List<SongTag> =
        runCatching {
            val raw = prefs(context).getString(KEY, null) ?: return emptyList()
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val songs = o.getJSONArray("songs")
                SongTag(
                    name = o.getString("name"),
                    songIds = (0 until songs.length()).map { songs.getString(it) },
                    createdAt = o.optLong("at"),
                )
            }
        }.getOrDefault(emptyList())

    private fun store(context: Context, tags: List<SongTag>) {
        val array = JSONArray()
        tags.forEach { tag ->
            array.put(
                JSONObject()
                    .put("name", tag.name)
                    .put("songs", JSONArray(tag.songIds))
                    .put("at", tag.createdAt),
            )
        }
        prefs(context).edit().putString(KEY, array.toString()).apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** [tags] after toggling [name] on [songIds] (pure, for tests). */
internal fun toggled(tags: List<SongTag>, name: String, songIds: List<String>, now: Long): List<SongTag> {
    val clean = SounTags.cleanName(name) ?: return tags
    val existing = tags.firstOrNull { it.name.equals(clean, ignoreCase = true) }
        ?: return tags + SongTag(clean, songIds.distinct(), now)
    val allTagged = songIds.all { it in existing.songIds }
    val updated =
        if (allTagged) {
            existing.copy(songIds = existing.songIds - songIds.toSet())
        } else {
            existing.copy(songIds = (songIds.filterNot { it in existing.songIds } + existing.songIds).distinct())
        }
    return tags.map { if (it === existing) updated else it }
}
