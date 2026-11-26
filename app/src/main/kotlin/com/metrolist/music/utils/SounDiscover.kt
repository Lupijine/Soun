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
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.Album as YTAlbum
import com.metrolist.innertube.models.Artist as YTArtist
import com.metrolist.music.constants.DiscoverDefaultModel
import com.metrolist.music.constants.DiscoverEveningRefreshKey
import com.metrolist.music.constants.DiscoverGeminiApiKey
import com.metrolist.music.constants.DiscoverModelKey
import com.metrolist.music.constants.DiscoverMorningRefreshKey
import com.metrolist.music.constants.DiscoverWebSearchKey
import com.metrolist.music.db.DatabaseDao
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.toMediaMetadata
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.text.Normalizer
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

/* The page ---------------------------------------------------------------------------------- */

@Serializable
data class DiscoverArtist(
    val id: String? = null,
    val name: String,
)

/** A song found on YouTube Music for a pick. */
@Serializable
data class DiscoverSong(
    val id: String,
    val title: String,
    val artists: List<DiscoverArtist>,
    val duration: Int = -1,
    val thumbnailUrl: String? = null,
    val albumId: String? = null,
    val albumTitle: String? = null,
    val explicit: Boolean = false,
    val musicVideoType: String? = null,
) {
    val artistNames: String get() = artists.joinToString { it.name }

    fun toMediaMetadata() =
        MediaMetadata(
            id = id,
            title = title,
            artists = artists.map { MediaMetadata.Artist(it.id, it.name) },
            duration = duration,
            thumbnailUrl = thumbnailUrl,
            album = albumId?.let { MediaMetadata.Album(it, albumTitle.orEmpty()) },
            explicit = explicit,
            musicVideoType = musicVideoType,
        )

    /** For the song menu, which works with YouTube Music items. */
    fun toSongItem() =
        SongItem(
            id = id,
            title = title,
            artists = artists.map { YTArtist(it.name, it.id) },
            album = albumId?.let { YTAlbum(albumTitle.orEmpty(), it) },
            duration = duration.takeIf { it > 0 },
            musicVideoType = musicVideoType,
            thumbnail = thumbnailUrl.orEmpty(),
            explicit = explicit,
        )

    companion object {
        fun from(metadata: MediaMetadata) =
            DiscoverSong(
                id = metadata.id,
                title = metadata.title,
                artists = metadata.artists.map { DiscoverArtist(it.id, it.name) },
                duration = metadata.duration,
                thumbnailUrl = metadata.thumbnailUrl,
                albumId = metadata.album?.id,
                albumTitle = metadata.album?.title,
                explicit = metadata.explicit,
                musicVideoType = metadata.musicVideoType,
            )

        fun from(song: com.metrolist.music.db.entities.Song) =
            DiscoverSong(
                id = song.id,
                title = song.song.title,
                artists = song.artists.map { DiscoverArtist(it.id, it.name) },
                duration = song.song.duration,
                thumbnailUrl = song.song.thumbnailUrl,
                albumId = song.song.albumId,
                albumTitle = song.song.albumName,
                explicit = song.song.explicit,
            )

        fun from(item: SongItem): DiscoverSong {
            val metadata = item.toMediaMetadata()
            return DiscoverSong(
                id = metadata.id,
                title = metadata.title,
                artists = metadata.artists.map { DiscoverArtist(it.id, it.name) },
                duration = metadata.duration,
                thumbnailUrl = metadata.thumbnailUrl,
                albumId = metadata.album?.id,
                albumTitle = metadata.album?.title,
                explicit = metadata.explicit,
                musicVideoType = metadata.musicVideoType,
            )
        }
    }
}

/** One song the curator picked, and why. */
@Serializable
data class DiscoverPick(
    val song: DiscoverSong,
    val why: String = "",
)

/** An album found on YouTube Music for a pick. */
@Serializable
data class DiscoverAlbum(
    val browseId: String,
    val playlistId: String? = null,
    val title: String,
    val artists: String,
    val year: Int? = null,
    val thumbnailUrl: String? = null,
)

@Serializable
data class DiscoverAlbumPick(
    val album: DiscoverAlbum,
    val why: String = "",
)

/** What a section is built from, which also decides how it looks. */
@Serializable
enum class DiscoverKind {
    SONGS,

    /** For this part of the day. */
    TIME,

    /** Recent releases. */
    NEW,

    /** A pattern in the listening history. */
    HISTORY,

    /** Songs that go with one song you played ([DiscoverSection.seed]). */
    BECAUSE,

    /** A flowing blend of a few of your artists and new ones. */
    MIX,

    /** Whole albums ([DiscoverSection.albums]). */
    ALBUMS,
}

@Serializable
data class DiscoverSection(
    val title: String,
    val reason: String = "",
    val picks: List<DiscoverPick> = emptyList(),
    val kind: DiscoverKind = DiscoverKind.SONGS,
    val seed: DiscoverSong? = null,
    val albums: List<DiscoverAlbumPick> = emptyList(),
)

@Serializable
data class DiscoverPage(
    val generatedAt: Long,
    val period: String,
    val intro: String = "",
    val sections: List<DiscoverSection>,
    val model: String,
    /** Google's search suggestions (HTML), shown with any page that used Google Search. */
    val searchSuggestionsHtml: String? = null,
    /** Web search was on but this key could not use it (newer models search only with billing). */
    val madeWithoutSearch: Boolean = false,
    /** The song a "More like this" page was made around; null for the main page. */
    val seed: DiscoverSong? = null,
) {
    val songs: List<DiscoverSong> get() = sections.flatMap { section -> section.picks.map { it.song } }
}

enum class DiscoverStep { PROFILE, ASKING, FINDING }

enum class DiscoverError { NO_KEY, BAD_KEY, BAD_MODEL, QUOTA, SERVICE, NETWORK, BAD_REPLY, NOTHING_FOUND, SEARCH_UNAVAILABLE }

data class DiscoverState(
    val page: DiscoverPage? = null,
    val step: DiscoverStep? = null,
    val error: DiscoverError? = null,
    val errorDetail: String? = null,
) {
    val refreshing: Boolean get() = step != null
}

/* What the curator writes, before the songs are looked up ----------------------------------- */

data class CuratedSong(val title: String, val artist: String, val why: String)

data class CuratedSection(
    val title: String,
    val reason: String,
    val songs: List<CuratedSong>,
    val kind: DiscoverKind = DiscoverKind.SONGS,
    val seed: CuratedSong? = null,
    val albums: List<CuratedSong> = emptyList(),
)

internal fun discoverKindOf(type: String): DiscoverKind =
    when (type.trim().lowercase(Locale.ROOT)) {
        "time" -> DiscoverKind.TIME
        "new", "new releases", "releases" -> DiscoverKind.NEW
        "history" -> DiscoverKind.HISTORY
        "because", "seed", "song" -> DiscoverKind.BECAUSE
        "mix" -> DiscoverKind.MIX
        "albums", "album" -> DiscoverKind.ALBUMS
        else -> DiscoverKind.SONGS
    }

data class CuratedPage(val intro: String, val sections: List<CuratedSection>)

/** Reads the curator's JSON, also when it comes wrapped in prose or a code fence. Null if there is none. */
internal fun parseCuratedPage(text: String): CuratedPage? {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    val root = runCatching { DiscoverJson.parseToJsonElement(text.substring(start, end + 1)).jsonObject }.getOrNull() ?: return null
    fun JsonObject.string(key: String): String = this[key]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.trim().orEmpty()
    val sections =
        (root["sections"] as? JsonArray).orEmpty().mapNotNull { element ->
            val section = element as? JsonObject ?: return@mapNotNull null
            fun JsonObject.item(): CuratedSong? {
                val title = string("title")
                val artist = string("artist")
                return if (title.isEmpty() || artist.isEmpty()) null else CuratedSong(title, artist, string("why"))
            }
            fun list(key: String) = (section[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.item() }
            val kind = discoverKindOf(section.string("type"))
            val songs = list("songs").ifEmpty { list("picks") }
            val albums = list("albums")
            val title = section.string("title")
            val usable = if (kind == DiscoverKind.ALBUMS) albums.isNotEmpty() else songs.isNotEmpty()
            if (title.isEmpty() || !usable) {
                null
            } else {
                CuratedSection(
                    title = title,
                    reason = section.string("reason"),
                    songs = if (kind == DiscoverKind.ALBUMS) emptyList() else songs,
                    kind = kind,
                    seed = (section["seed"] as? JsonObject)?.item(),
                    albums = if (kind == DiscoverKind.ALBUMS) albums else emptyList(),
                )
            }
        }
    if (sections.isEmpty()) return null
    return CuratedPage(root.string("intro"), sections)
}

/* Matching a pick to a YouTube Music result -------------------------------------------------- */

data class SongCandidate(val title: String, val artists: List<String>, val isVideo: Boolean)

private val Parenthetical = Regex("""\s*[(\[][^)\]]*[)\]]""")
private val Dashed = Regex("""\s+[-–—]\s+.*$""")
private val NonWord = Regex("""[^\p{L}\p{N}]+""")

internal fun normalizeForMatch(text: String): String {
    val plain = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "")
    return plain
        .lowercase(Locale.ROOT)
        .replace(Parenthetical, "")
        .replace(Dashed, "")
        .replace(NonWord, " ")
        .trim()
}

private fun looselyEqual(a: String, b: String): Boolean {
    if (a.isEmpty() || b.isEmpty()) return false
    return a == b || a.startsWith(b) || b.startsWith(a)
}

/** Most of the words in common, for titles written a little differently. */
private fun closeEnough(a: String, b: String): Boolean {
    val wordsA = a.split(' ').filter { it.isNotEmpty() }.toSet()
    val wordsB = b.split(' ').filter { it.isNotEmpty() }.toSet()
    if (wordsA.isEmpty() || wordsB.isEmpty()) return false
    return (wordsA intersect wordsB).size.toDouble() / (wordsA union wordsB).size >= 0.6
}

private val TitleDash = Regex("""\s+[-–—]\s+""")

/** A title as written, and for "Artist - Title" uploads also the part after the dash. */
private fun titleVariants(raw: String): List<String> {
    val dash = TitleDash.find(raw)
    return listOfNotNull(normalizeForMatch(raw), dash?.let { normalizeForMatch(raw.substring(it.range.last + 1)) })
        .filter { it.isNotEmpty() }
}

/**
 * The index of the result that is the picked song, or -1. The title has to match; a matching
 * artist and an audio track (not a video) rank higher. Taking whatever came first would play
 * wrong songs for picks YouTube does not have.
 */
internal fun bestMatch(
    title: String,
    artist: String,
    candidates: List<SongCandidate>,
    requireArtist: Boolean = false,
): Int {
    val wantedTitle = normalizeForMatch(title)
    val wantedArtists = artist.split(Regex("""\s*(?:,|&|\bfeat\.?|\bft\.?|\bx\b|\band\b)\s*""", RegexOption.IGNORE_CASE)).map(::normalizeForMatch).filter { it.isNotEmpty() }
    var best = -1
    var bestScore = 0
    candidates.take(6).forEachIndexed { index, candidate ->
        val names = candidate.artists.map(::normalizeForMatch)
        // A video's channel is often not the artist, so its title ("Artist - Title") counts too.
        val uploaderInTitle = TitleDash.find(candidate.title)?.let { normalizeForMatch(candidate.title.substring(0, it.range.first)) }
        val artistMatches =
            wantedArtists.any { wanted -> names.any { looselyEqual(it, wanted) } || (uploaderInTitle != null && looselyEqual(uploaderInTitle, wanted)) }
        if (requireArtist && !artistMatches) return@forEachIndexed
        val variants = titleVariants(candidate.title)
        val titleMatches =
            variants.any { looselyEqual(it, wantedTitle) } || (artistMatches && variants.any { closeEnough(it, wantedTitle) })
        if (!titleMatches) return@forEachIndexed
        val score = 2 + (if (artistMatches) 4 else 0) + (if (candidate.isVideo) 0 else 1) - index.coerceAtMost(3) / 2
        if (score > bestScore) {
            best = index
            bestScore = score
        }
    }
    return best
}

/* Refresh times ----------------------------------------------------------------------------- */

/** Models Google offers new keys, tried in turn when the chosen one is not available. */
val DiscoverFallbackModels = listOf("gemini-3.8-flash", "gemini-3.5-flash-lite")

val DiscoverMorningTime: LocalTime = LocalTime.of(7, 30)
val DiscoverEveningTime: LocalTime = LocalTime.of(19, 30)

/** The next of [times] after [now], or null when there are none. */
internal fun nextRefreshAt(now: LocalDateTime, times: List<LocalTime>): LocalDateTime? =
    times
        .map { time -> now.toLocalDate().atTime(time).let { if (it.isAfter(now)) it else it.plusDays(1) } }
        .minOrNull()

/* Taste profile ----------------------------------------------------------------------------- */

data class TasteProfile(
    val artistsMonth: List<String>,
    val artistsYear: List<String>,
    val songsMonth: List<String>,
    val recentLikes: List<String>,
    val songsThisTimeOfDay: List<String>,
    val knownSongIds: Set<String>,
    /** Played most in the last few days: the songs "Because you played" sections start from. */
    val recentSongs: List<String> = emptyList(),
    /** Every song named above, to show the one a section starts from. */
    val songsByLine: Map<String, DiscoverSong> = emptyMap(),
) {
    val isEmpty: Boolean get() = artistsYear.isEmpty() && recentLikes.isEmpty()
}

private fun com.metrolist.music.db.entities.Song.line(): String =
    "${song.title} — ${artists.joinToString { it.name }.ifEmpty { "?" }}"

suspend fun buildTasteProfile(dao: DatabaseDao, now: LocalDateTime): TasteProfile {
    val month = now.minusDays(30)
    val artistsMonth = dao.mostPlayedArtists(month, limit = 15, toTimeStamp = now).first().map { it.artist.name }
    val artistsYear = dao.mostPlayedArtists(now.minusDays(365), limit = 20, toTimeStamp = now).first().map { it.artist.name }
    val songsMonth = dao.mostPlayedSongs(month, limit = 25, toTimeStamp = now).first()
    val likes = dao.likedSongsByCreateDateDesc(limit = 25, offset = 0)
    val period = DayPeriod.of(now.hour)
    val periodStats = dao.songStatsAtHours(now.minusDays(90), period.hours, 15)
    val recentStats = dao.songStatsInRange(now.minusDays(4), now, 12)
    val byId = dao.getSongsByIds((periodStats + recentStats).map { it.songId }.distinct()).associateBy { it.id }
    val recent = recentStats.mapNotNull { byId[it.songId] }
    val periodSongs = periodStats.mapNotNull { byId[it.songId] }
    return TasteProfile(
        artistsMonth = artistsMonth,
        artistsYear = artistsYear,
        songsMonth = songsMonth.map { it.line() },
        recentLikes = likes.map { it.line() },
        songsThisTimeOfDay = periodSongs.map { it.line() },
        knownSongIds = (songsMonth + likes + periodSongs + recent).map { it.id }.toSet(),
        recentSongs = recent.map { it.line() },
        songsByLine = (recent + songsMonth + likes + periodSongs).associate { it.line() to DiscoverSong.from(it) },
    )
}

internal fun discoverPrompt(
    profile: TasteProfile,
    now: LocalDateTime,
    language: String,
    webSearch: Boolean,
): String {
    val period = DayPeriod.of(now.hour).name.lowercase(Locale.ROOT)
    val day = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    fun list(items: List<String>) = if (items.isEmpty()) "(none yet)" else items.joinToString("; ")
    return buildString {
        appendLine("Local time: $day ${"%02d:%02d".format(now.hour, now.minute)} ($period).")
        appendLine("Write the intro, section titles, reasons and \"why\" notes in $language. Keep song titles and artist names exactly as officially released.")
        appendLine()
        appendLine("The listener's profile:")
        appendLine("- Most played artists, last 30 days: ${list(profile.artistsMonth)}")
        appendLine("- Most played artists, last year: ${list(profile.artistsYear)}")
        appendLine("- Most played songs, last 30 days: ${list(profile.songsMonth)}")
        appendLine("- Recently liked: ${list(profile.recentLikes)}")
        appendLine("- Played most in the $period: ${list(profile.songsThisTimeOfDay)}")
        appendLine("- Played most in the last few days: ${list(profile.recentSongs)}")
        appendLine()
        if (webSearch) {
            appendLine("Use Google Search to find what these artists and artists close to them released in recent months, and to make sure every song and album you pick really exists.")
        }
        appendLine("Build these sections, in this order:")
        appendLine("- type \"time\": 8 songs that suit this $period.")
        if (webSearch) appendLine("- type \"new\": 8 songs released in recent months by these artists or artists close to them.")
        if (profile.recentSongs.isNotEmpty()) {
            appendLine("- Two sections of type \"because\", each starting from a different song of \"Played most in the last few days\": put that song in \"seed\" exactly as listed (title and artist) and pick 8 songs that go with it. The title names the seed song.")
        }
        appendLine("- type \"history\": 8 songs that follow a pattern in their listening (an artist they keep coming back to, a sound they play again and again); the reason names the pattern.")
        appendLine("- Two sections of type \"mix\": each blends two or three of their most played artists with new names into one flowing mix of 10 songs, in playing order. The title is a short mix name such as \"Artist A × Artist B\".")
        appendLine("- type \"albums\": 6 full albums (no singles) they have not played, listed in \"albums\" instead of \"songs\".")
        appendLine("Do not pick any song listed in the profile, and use each song once across the page.")
        appendLine("Reply with this JSON only, no other text:")
        append(DiscoverReplyFormat)
    }
}

private const val DiscoverReplyFormat =
    """{"intro": "one short, vivid sentence about the page", "sections": [{"type": "...", "title": "...", "reason": "one sentence on why this fits the listener", "seed": {"title": "...", "artist": "..."}, "songs": [{"title": "...", "artist": "...", "why": "a few words"}], "albums": [{"title": "...", "artist": "...", "why": "a few words"}]}]}"""

/** For "More like this": a page built around one song. */
internal fun seedPrompt(
    seed: DiscoverSong,
    profile: TasteProfile,
    language: String,
    webSearch: Boolean,
): String {
    fun list(items: List<String>) = if (items.isEmpty()) "(none yet)" else items.joinToString("; ")
    return buildString {
        appendLine("The listener asked for more music like \"${seed.title}\" by ${seed.artistNames.ifBlank { "an unknown artist" }}.")
        appendLine("Write the intro, section titles, reasons and \"why\" notes in $language. Keep song titles and artist names exactly as officially released.")
        appendLine()
        appendLine("Artists they play most: ${list(profile.artistsMonth.ifEmpty { profile.artistsYear })}")
        appendLine("Songs they already know (do not pick these): ${list(profile.songsMonth + profile.recentLikes)}")
        appendLine()
        if (webSearch) appendLine("Use Google Search to learn about this song and its artist, and to make sure every song and album you pick really exists.")
        appendLine("Build these sections, in this order:")
        appendLine("- type \"because\": 10 songs that go with this song (mood, sound, era), with the song itself in \"seed\".")
        appendLine("- type \"mix\": a flowing mix of 10 songs, in playing order, that starts from this song's sound and drifts toward the artists they play most. The title is a short mix name.")
        appendLine("- type \"songs\": 8 more songs by this artist or the people they work with.")
        appendLine("- type \"albums\": 5 full albums for someone who loves this song, listed in \"albums\" instead of \"songs\".")
        appendLine("Do not pick the song itself, and use each song once.")
        appendLine("Reply with this JSON only, no other text:")
        append(DiscoverReplyFormat)
    }
}

private const val DiscoverSystemPrompt =
    "You are the music curator inside Soun, a personal music app that plays from YouTube Music. " +
        "You build a Discover page for one listener from their listening profile. Pick real, released recordings " +
        "that can be found on YouTube Music, and never invent a title. Favour music the listener has not played: " +
        "recent releases, deep cuts and artists close to their taste, with a few bolder steps outward. " +
        "Each reason refers to what this listener actually plays."

internal val DiscoverJson = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

/* Gemini ------------------------------------------------------------------------------------ */

class DiscoverException(val error: DiscoverError, message: String? = null, cause: Throwable? = null) : Exception(message, cause)

/** What an error answer from Gemini means for the page. */
internal fun discoverErrorFor(code: Int, message: String?, webSearch: Boolean): DiscoverError =
    when {
        // Search refused for this key or model: newer models search only on a paid plan.
        webSearch && code in 400..499 && code != 404 &&
            message?.contains(Regex("search|grounding|tool", RegexOption.IGNORE_CASE)) == true -> DiscoverError.SEARCH_UNAVAILABLE
        code == 429 -> DiscoverError.QUOTA
        code == 404 -> DiscoverError.BAD_MODEL
        code == 401 || code == 403 -> DiscoverError.BAD_KEY
        code == 400 && message?.contains("API key", ignoreCase = true) == true -> DiscoverError.BAD_KEY
        code == 400 && message?.contains("model", ignoreCase = true) == true -> DiscoverError.BAD_MODEL
        code >= 500 -> DiscoverError.SERVICE
        else -> DiscoverError.BAD_REPLY
    }

internal data class GeminiReply(val text: String, val searchSuggestionsHtml: String?)

internal object GeminiDiscover {
    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(150, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun generate(apiKey: String, model: String, prompt: String, webSearch: Boolean): GeminiReply {
        val body =
            buildJsonObject {
                putJsonObject("systemInstruction") { putJsonArray("parts") { addJsonObject { put("text", DiscoverSystemPrompt) } } }
                putJsonArray("contents") {
                    addJsonObject {
                        put("role", "user")
                        putJsonArray("parts") { addJsonObject { put("text", prompt) } }
                    }
                }
                if (webSearch) putJsonArray("tools") { addJsonObject { putJsonObject("google_search") {} } }
                putJsonObject("generationConfig") {
                    // Search grounding cannot be combined with a JSON response type, so JSON is asked for in the prompt.
                    if (!webSearch) put("responseMimeType", "application/json")
                }
            }
        val request =
            Request
                .Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/${model.trim()}:generateContent")
                .header("x-goog-api-key", apiKey.trim())
                .post(body.toString().toRequestBody(jsonType))
                .build()
        val (code, raw) =
            try {
                client.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
            } catch (e: IOException) {
                throw DiscoverException(DiscoverError.NETWORK, e.message, e)
            }
        if (code !in 200..299) {
            val message = runCatching { DiscoverJson.parseToJsonElement(raw).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull }.getOrNull()
            Timber.tag(TAG).w("Gemini answered %d: %s", code, message ?: raw.take(300))
            val error = discoverErrorFor(code, message, webSearch)
            throw DiscoverException(error, message)
        }
        return readReply(raw) ?: throw DiscoverException(DiscoverError.BAD_REPLY, "No text in the reply")
    }

    internal fun readReply(raw: String): GeminiReply? {
        val candidate =
            runCatching { DiscoverJson.parseToJsonElement(raw).jsonObject["candidates"]?.jsonArray?.firstOrNull()?.jsonObject }.getOrNull()
                ?: return null
        val text =
            (candidate["content"]?.jsonObject?.get("parts") as? JsonArray).orEmpty()
                .mapNotNull { it as? JsonObject }
                .filterNot { it["thought"]?.jsonPrimitive?.contentOrNull == "true" }
                .mapNotNull { it["text"]?.jsonPrimitive?.contentOrNull }
                .joinToString("")
        if (text.isBlank()) return null
        val html =
            runCatching {
                candidate["groundingMetadata"]?.jsonObject?.get("searchEntryPoint")?.jsonObject?.get("renderedContent")?.jsonPrimitive?.contentOrNull
            }.getOrNull()?.takeIf { it.isNotBlank() }
        return GeminiReply(text, html)
    }
}

private const val TAG = "SounDiscover"

/* The engine -------------------------------------------------------------------------------- */

/**
 * Discover: a page of songs picked for you by Gemini from what you listen to, with a reason for
 * every section. Refreshed in the morning and the evening (each time for that part of the day)
 * or on request; the songs are looked up on YouTube Music from the phone.
 */
object SounDiscover {
    private const val FILE = "soun_discover.json"
    private const val SEARCH_PARALLELISM = 6
    private const val MAX_SEED_PAGES = 6

    private val state = MutableStateFlow(DiscoverState())
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var loaded = false

    fun state(context: Context): StateFlow<DiscoverState> {
        ensureLoaded(context.applicationContext)
        return state.asStateFlow()
    }

    fun hasKey(context: Context): Boolean = context.dataStore.get(DiscoverGeminiApiKey, "").isNotBlank()

    /* "More like this": pages made around one song, kept for the session */

    private val seedStates = MutableStateFlow<Map<String, DiscoverState>>(emptyMap())
    private val seedSongs = java.util.concurrent.ConcurrentHashMap<String, DiscoverSong>()

    /** The song a "More like this" page was asked for in this session. */
    fun seedSong(songId: String): DiscoverSong? = seedSongs[songId]

    fun seedState(songId: String): Flow<DiscoverState> = seedStates.map { it[songId] ?: DiscoverState() }

    /** Makes a page around [seed], unless a recent one exists or one is on its way. */
    fun discoverFrom(
        context: Context,
        seed: DiscoverSong,
        force: Boolean = false,
    ) {
        val app = context.applicationContext
        seedSongs[seed.id] = seed
        val current = seedStates.value[seed.id]
        if (!force && current != null) {
            val fresh = current.page?.let { Duration.ofMillis(System.currentTimeMillis() - it.generatedAt) < Duration.ofHours(6) } == true
            if (current.refreshing || fresh) return
        }
        updateSeed(seed.id) { (it ?: DiscoverState()).copy(step = DiscoverStep.PROFILE, error = null, errorDetail = null) }
        scope.launch {
            runCatching { makePage(app, seed) { step -> updateSeed(seed.id) { state -> (state ?: DiscoverState()).copy(step = step) } } }
                .onSuccess { page -> updateSeed(seed.id) { DiscoverState(page = page) } }
                .onFailure { e ->
                    val error = (e as? DiscoverException)?.error ?: DiscoverError.SERVICE
                    Timber.tag(TAG).w(e, "More like %s failed: %s", seed.id, error)
                    updateSeed(seed.id) { state -> (state ?: DiscoverState()).copy(step = null, error = error, errorDetail = e.message) }
                }
        }
    }

    private fun updateSeed(
        songId: String,
        change: (DiscoverState?) -> DiscoverState,
    ) {
        seedStates.update { states ->
            val others = (states - songId).entries.toList().takeLast(MAX_SEED_PAGES - 1).associate { it.key to it.value }
            others + (songId to change(states[songId]))
        }
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            loaded = true
            val page = runCatching { DiscoverJson.decodeFromString(DiscoverPage.serializer(), file(context).readText()) }.getOrNull()
            state.update { it.copy(page = page) }
        }
    }

    private fun file(context: Context) = File(context.filesDir, FILE)

    /** Refreshes without tying the work to a screen: leaving the page does not cancel it. */
    fun refreshInBackground(context: Context) {
        val app = context.applicationContext
        scope.launch { refresh(app) }
    }

    /** Whether the page is old enough to be refreshed when you open it. */
    fun isStale(page: DiscoverPage?): Boolean =
        page == null || Duration.ofMillis(System.currentTimeMillis() - page.generatedAt) > Duration.ofHours(14)

    /** Makes a new page. Waits for a refresh already running instead of starting another. */
    suspend fun refresh(context: Context): Result<DiscoverPage> {
        val app = context.applicationContext
        ensureLoaded(app)
        if (lock.isLocked) {
            lock.withLock { }
            return state.value.page?.let { Result.success(it) } ?: Result.failure(DiscoverException(state.value.error ?: DiscoverError.SERVICE))
        }
        return lock.withLock {
            withContext(Dispatchers.IO) {
                runCatching { build(app) }
                    .onSuccess { page ->
                        runCatching { file(app).writeText(DiscoverJson.encodeToString(DiscoverPage.serializer(), page)) }
                        state.update { DiscoverState(page = page) }
                    }.onFailure { e ->
                        val error = (e as? DiscoverException)?.error ?: DiscoverError.SERVICE
                        Timber.tag(TAG).w(e, "Discover refresh failed: %s", error)
                        state.update { it.copy(step = null, error = error, errorDetail = e.message) }
                    }
            }
        }
    }

    private suspend fun build(context: Context): DiscoverPage =
        makePage(context, seed = null) { step -> state.update { it.copy(step = step, error = null, errorDetail = null) } }

    /** Makes the main page, or with a [seed] a "More like this" page. */
    private suspend fun makePage(
        context: Context,
        seed: DiscoverSong?,
        onStep: (DiscoverStep) -> Unit,
    ): DiscoverPage {
        val prefs = context.dataStore.data.first()
        val apiKey = prefs[DiscoverGeminiApiKey].orEmpty()
        if (apiKey.isBlank()) throw DiscoverException(DiscoverError.NO_KEY)
        val model = prefs[DiscoverModelKey]?.takeIf { it.isNotBlank() } ?: DiscoverDefaultModel
        val webSearch = prefs[DiscoverWebSearchKey] ?: true

        onStep(DiscoverStep.PROFILE)
        val dao = EntryPointAccessors.fromApplication(context, ReleaseWatcherEntryPoint::class.java).databaseDao()
        val now = LocalDateTime.now()
        val profile = buildTasteProfile(dao, now)
        val language = Locale.getDefault().getDisplayLanguage(Locale.ENGLISH).ifBlank { "English" }

        onStep(DiscoverStep.ASKING)
        val asked =
            ask(context, apiKey, model, webSearch) { search ->
                if (seed == null) discoverPrompt(profile, now, language, search) else seedPrompt(seed, profile, language, search)
            }
        val reply = asked.reply
        val curated = parseCuratedPage(reply.text) ?: throw DiscoverException(DiscoverError.BAD_REPLY, reply.text.take(200))

        onStep(DiscoverStep.FINDING)
        val sections = findAll(curated, profile, seed)
        if (sections.isEmpty()) throw DiscoverException(DiscoverError.NOTHING_FOUND)
        return DiscoverPage(
            generatedAt = System.currentTimeMillis(),
            period = DayPeriod.of(now.hour).name,
            intro = curated.intro,
            sections = sections,
            model = asked.model,
            searchSuggestionsHtml = reply.searchSuggestionsHtml,
            madeWithoutSearch = webSearch && !asked.searched,
            seed = seed,
        )
    }

    private class Asked(val reply: GeminiReply, val model: String, val searched: Boolean)

    /**
     * Asks [model], moving on to a model Google offers new keys when it is not available (the
     * 2.5 models answer 404 to projects that never used them), and without web search when the
     * key may not use it. A model that worked in place of the chosen one is kept for next time.
     */
    private suspend fun ask(
        context: Context,
        apiKey: String,
        model: String,
        webSearch: Boolean,
        prompt: (Boolean) -> String,
    ): Asked {
        var lastError: DiscoverException? = null
        for (candidate in (listOf(model) + DiscoverFallbackModels).distinct()) {
            try {
                val asked =
                    try {
                        Asked(GeminiDiscover.generate(apiKey, candidate, prompt(webSearch), webSearch), candidate, webSearch)
                    } catch (e: DiscoverException) {
                        if (!webSearch || e.error != DiscoverError.SEARCH_UNAVAILABLE) throw e
                        Timber.tag(TAG).i("Web search is not available to this key on %s; asking without it", candidate)
                        Asked(GeminiDiscover.generate(apiKey, candidate, prompt(false), false), candidate, false)
                    }
                if (candidate != model) {
                    Timber.tag(TAG).i("%s is not available to this key; using %s from now on", model, candidate)
                    context.dataStore.edit { it[DiscoverModelKey] = candidate }
                }
                return asked
            } catch (e: DiscoverException) {
                if (e.error != DiscoverError.BAD_MODEL) throw e
                lastError = e
            }
        }
        throw lastError ?: DiscoverException(DiscoverError.BAD_MODEL)
    }

    /** Looks every pick up on YouTube Music; sections left with too few found are dropped. */
    private suspend fun findAll(
        curated: CuratedPage,
        profile: TasteProfile,
        pageSeed: DiscoverSong?,
    ): List<DiscoverSection> {
        val songs = resolve(curated.sections.flatMap { it.songs }) { findSong(it) }
        val albums = resolve(curated.sections.flatMap { it.albums }) { findAlbum(it) }
        val usedSongs = HashSet(profile.knownSongIds).apply { pageSeed?.let { add(it.id) } }
        val usedAlbums = HashSet<String>()
        return curated.sections.mapNotNull { section ->
            if (section.kind == DiscoverKind.ALBUMS) {
                val picks =
                    section.albums.mapNotNull { pick ->
                        val album = albums[pick] ?: return@mapNotNull null
                        if (usedAlbums.add(album.browseId)) DiscoverAlbumPick(album, pick.why) else null
                    }
                if (picks.size < 2) null else DiscoverSection(section.title, section.reason, kind = DiscoverKind.ALBUMS, albums = picks)
            } else {
                val picks =
                    section.songs.mapNotNull { pick ->
                        val song = songs[pick] ?: return@mapNotNull null
                        if (usedSongs.add(song.id)) DiscoverPick(song, pick.why) else null
                    }
                val seed = if (section.kind == DiscoverKind.BECAUSE) pageSeed ?: section.seed?.let { seedFor(it, profile) } else null
                if (picks.size < 3) null else DiscoverSection(section.title, section.reason, picks, section.kind, seed)
            }
        }
    }

    private suspend fun <T> resolve(
        picks: List<CuratedSong>,
        find: suspend (CuratedSong) -> T?,
    ): Map<CuratedSong, T?> =
        coroutineScope {
            picks.distinct().chunked(SEARCH_PARALLELISM).flatMap { batch ->
                batch.map { pick -> async { pick to runCatching { find(pick) }.getOrNull() } }.awaitAll()
            }.toMap()
        }

    /** The song a "Because you played" section starts from: one of yours, else found online. */
    private suspend fun seedFor(
        wanted: CuratedSong,
        profile: TasteProfile,
    ): DiscoverSong? {
        val title = normalizeForMatch(wanted.title)
        val artist = normalizeForMatch(wanted.artist)
        val sameTitle = profile.songsByLine.values.filter { song -> titleVariants(song.title).any { looselyEqual(it, title) } }
        val yours =
            sameTitle.firstOrNull { song -> song.artists.any { normalizeForMatch(it.name).let { name -> name.isNotEmpty() && (looselyEqual(name, artist) || artist.contains(name)) } } }
                ?: sameTitle.firstOrNull()
        return yours ?: runCatching { findSong(wanted) }.getOrNull()
    }

    /** Songs first; music videos (titled "Artist - Title") only from the right artist. */
    private suspend fun findSong(song: CuratedSong): DiscoverSong? {
        val query = "${song.title} ${song.artist}"
        for (filter in listOf(YouTube.SearchFilter.FILTER_SONG, YouTube.SearchFilter.FILTER_VIDEO)) {
            val videos = filter == YouTube.SearchFilter.FILTER_VIDEO
            val results = YouTube.search(query, filter).getOrNull()?.items?.filterIsInstance<SongItem>().orEmpty()
            val index =
                bestMatch(
                    song.title,
                    song.artist,
                    results.map { item -> SongCandidate(item.title, item.artists.map { it.name }, videos || item.isVideoSong) },
                    requireArtist = videos,
                )
            results.getOrNull(index)?.let { return DiscoverSong.from(it) }
        }
        return null
    }

    private suspend fun findAlbum(pick: CuratedSong): DiscoverAlbum? {
        val results =
            YouTube.search("${pick.title} ${pick.artist}", YouTube.SearchFilter.FILTER_ALBUM)
                .getOrNull()
                ?.items
                ?.filterIsInstance<AlbumItem>()
                .orEmpty()
        val index =
            bestMatch(
                pick.title,
                pick.artist,
                results.map { item -> SongCandidate(item.title, item.artists.orEmpty().map { it.name }, isVideo = false) },
                requireArtist = true,
            )
        val album = results.getOrNull(index) ?: return null
        return DiscoverAlbum(
            browseId = album.browseId,
            playlistId = album.playlistId.takeIf { it.isNotBlank() },
            title = album.title,
            artists = album.artists.orEmpty().joinToString { it.name },
            year = album.year,
            thumbnailUrl = album.thumbnail,
        )
    }

    /* Automatic refreshes */

    fun refreshTimes(context: Context): List<LocalTime> =
        buildList {
            if (context.dataStore.get(DiscoverMorningRefreshKey, true)) add(DiscoverMorningTime)
            if (context.dataStore.get(DiscoverEveningRefreshKey, true)) add(DiscoverEveningTime)
        }

    /** Whether a scheduled refresh should run now: skipped right after a refresh you asked for. */
    internal fun isDue(context: Context): Boolean {
        ensureLoaded(context.applicationContext)
        val last = state.value.page?.generatedAt ?: return true
        return Duration.ofMillis(System.currentTimeMillis() - last) > Duration.ofHours(3)
    }
}

/** Refreshes Discover at the chosen times of day, also while the app is closed. */
class SounDiscoverJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            if (SounDiscover.hasKey(applicationContext) && SounDiscover.isDue(applicationContext)) {
                SounDiscover.refresh(applicationContext)
            }
            schedule(applicationContext)
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
        private const val JOB_ID = 0x44495343 // "DISC"

        /** Plans the next refresh, or cancels it when there is no key or no refresh time. */
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            val next = if (SounDiscover.hasKey(context)) nextRefreshAt(LocalDateTime.now(), SounDiscover.refreshTimes(context)) else null
            if (next == null) {
                scheduler.cancel(JOB_ID)
                return
            }
            val delay = Duration.between(LocalDateTime.now(), next).toMillis().coerceAtLeast(0L)
            val job =
                JobInfo.Builder(JOB_ID, ComponentName(context, SounDiscoverJobService::class.java))
                    .setMinimumLatency(delay)
                    .setOverrideDeadline(delay + TimeUnit.MINUTES.toMillis(45))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build()
            runCatching { scheduler.schedule(job) }
                .onFailure { Timber.tag(TAG).w(it, "Could not schedule Discover refreshes") }
        }
    }
}
