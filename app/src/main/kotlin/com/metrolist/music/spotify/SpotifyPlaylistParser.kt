/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.spotify

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

data class SpotifyTrack(
    val title: String,
    val artists: List<String>,
    val durationMs: Long?,
)

data class SpotifyPlaylist(
    val name: String?,
    val coverUrl: String?,
    val tracks: List<SpotifyTrack>,
    /** Track count Spotify reports for the whole playlist, when the page says so. */
    val reportedTrackCount: Int?,
)

/**
 * Reads a public Spotify playlist from its embed page
 * (`https://open.spotify.com/embed/playlist/<id>`), which needs no API key.
 */
object SpotifyPlaylistParser {
    /** The embed lists at most this many tracks, however long the playlist is. */
    const val EMBED_TRACK_LIMIT = 100

    private val linkPatterns = listOf(
        Regex("""open\.spotify\.com/(?:intl-[a-z_-]+/)?(?:embed/)?(?:user/[^/\s?#]+/)?playlist/([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE),
        Regex("""spotify:(?:user:[^:\s]+:)?playlist:([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE),
    )
    private val bareId = Regex("[A-Za-z0-9]{22}")
    private val nextDataScript = Regex(
        """<script[^>]*\bid=["']__NEXT_DATA__["'][^>]*>(.*?)</script>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val jsonScripts = Regex(
        """<script[^>]*\btype=["']application/json["'][^>]*>(.*?)</script>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** The playlist id in a Spotify link, URI or bare id, or null if [input] holds none. */
    fun extractPlaylistId(input: String): String? {
        val text = input.trim()
        linkPatterns.forEach { pattern ->
            pattern.find(text)?.groupValues?.get(1)?.takeIf { it.length >= 10 }?.let { return it }
        }
        return text.takeIf { bareId.matches(it) }
    }

    fun embedUrl(playlistId: String) = "https://open.spotify.com/embed/playlist/$playlistId"

    /**
     * Parses the embed page. Returns null when the page carries no playlist data at all;
     * a playlist with no tracks means Spotify showed the playlist but listed nothing.
     */
    fun parseEmbedHtml(html: String): SpotifyPlaylist? {
        val candidates = buildList {
            nextDataScript.find(html)?.let { add(it.groupValues[1]) }
            jsonScripts.findAll(html).forEach { add(it.groupValues[1]) }
        }
        var fallback: SpotifyPlaylist? = null
        for (candidate in candidates.distinct()) {
            val playlist = parseJson(candidate) ?: continue
            if (playlist.tracks.isNotEmpty()) return playlist
            if (fallback == null) fallback = playlist
        }
        return fallback
    }

    fun parseJson(text: String): SpotifyPlaylist? {
        val root = runCatching { json.parseToJsonElement(text.trim()) }.getOrNull() ?: return null
        val entity = root.at("props", "pageProps", "state", "data", "entity") as? JsonObject
        val listOwner = entity?.takeIf { it["trackList"] is JsonArray } ?: findTrackListOwner(root)
        val source = listOwner ?: entity ?: return null
        val tracks = (source["trackList"] as? JsonArray).orEmpty().mapNotNull(::parseTrack)
        return SpotifyPlaylist(
            name = source.string("name") ?: source.string("title") ?: entity?.string("name") ?: entity?.string("title"),
            coverUrl = coverUrl(source) ?: entity?.let(::coverUrl),
            tracks = tracks,
            reportedTrackCount = reportedCount(source) ?: entity?.let(::reportedCount),
        )
    }

    private fun parseTrack(element: JsonElement): SpotifyTrack? {
        val obj = element as? JsonObject ?: return null
        val nested = obj["track"] as? JsonObject
        val title = (obj.string("title") ?: obj.string("name") ?: nested?.string("name"))
            ?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val artistObjects = (obj["artists"] as? JsonArray ?: nested?.get("artists") as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.string("name") ?: (it as? JsonPrimitive)?.contentOrNull }
            .orEmpty()
        val artists = artistObjects.ifEmpty {
            obj.string("subtitle").orEmpty().replace(' ', ' ').split(',')
        }.map { it.trim() }.filter { it.isNotEmpty() }
        val duration = obj["duration"] ?: nested?.get("duration_ms") ?: obj["duration_ms"]
        val durationMs = when (duration) {
            is JsonPrimitive -> duration.longOrNull ?: duration.doubleOrNull?.toLong()
            is JsonObject -> (duration["totalMilliseconds"] as? JsonPrimitive)?.longOrNull
            else -> null
        }?.takeIf { it > 0 }
        return SpotifyTrack(title = title, artists = artists, durationMs = durationMs)
    }

    private fun coverUrl(entity: JsonObject): String? {
        val sources = (entity.at("coverArt", "sources") as? JsonArray)
            ?: (entity.at("visualIdentity", "image") as? JsonArray)
            ?: (entity["images"] as? JsonArray)
            ?: return null
        return sources.mapNotNull { it as? JsonObject }
            .maxByOrNull { (it.number("width") ?: it.number("maxWidth") ?: 0L) }
            ?.string("url")
    }

    private fun reportedCount(entity: JsonObject): Int? =
        (entity.number("trackCount") ?: entity.number("totalTracks") ?: entity.number("totalCount")
            ?: (entity["tracks"] as? JsonObject)?.number("total"))?.toInt()?.takeIf { it > 0 }

    private fun findTrackListOwner(element: JsonElement): JsonObject? = when (element) {
        is JsonObject -> element.takeIf { (it["trackList"] as? JsonArray)?.isNotEmpty() == true }
            ?: element.values.firstNotNullOfOrNull(::findTrackListOwner)
        is JsonArray -> element.firstNotNullOfOrNull(::findTrackListOwner)
        else -> null
    }

    private fun JsonElement.at(vararg path: String): JsonElement? =
        path.fold(this as JsonElement?) { node, key -> (node as? JsonObject)?.get(key) }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.number(key: String): Long? =
        (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
}
