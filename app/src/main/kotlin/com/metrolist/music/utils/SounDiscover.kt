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
import kotlinx.coroutines.flow.asStateFlow
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

@Serializable
data class DiscoverSection(
    val title: String,
    val reason: String = "",
    val picks: List<DiscoverPick>,
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

data class CuratedSection(val title: String, val reason: String, val songs: List<CuratedSong>)

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
            val songs =
                ((section["songs"] ?: section["picks"]) as? JsonArray).orEmpty().mapNotNull { songElement ->
                    val song = songElement as? JsonObject ?: return@mapNotNull null
                    val title = song.string("title")
                    val artist = song.string("artist")
                    if (title.isEmpty() || artist.isEmpty()) null else CuratedSong(title, artist, song.string("why"))
                }
            val title = section.string("title")
            if (title.isEmpty() || songs.isEmpty()) null else CuratedSection(title, section.string("reason"), songs)
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

/**
 * The index of the result that is the picked song, or -1. The title has to match; a matching
 * artist and an audio track (not a video) rank higher. Taking whatever came first would play
 * wrong songs for picks YouTube does not have.
 */
internal fun bestMatch(title: String, artist: String, candidates: List<SongCandidate>): Int {
    val wantedTitle = normalizeForMatch(title)
    val wantedArtists = artist.split(Regex("""\s*(?:,|&|\bfeat\.?|\bft\.?|\bx\b|\band\b)\s*""", RegexOption.IGNORE_CASE)).map(::normalizeForMatch).filter { it.isNotEmpty() }
    var best = -1
    var bestScore = 0
    candidates.take(6).forEachIndexed { index, candidate ->
        if (!looselyEqual(normalizeForMatch(candidate.title), wantedTitle)) return@forEachIndexed
        val names = candidate.artists.map(::normalizeForMatch)
        val artistMatches = wantedArtists.any { wanted -> names.any { looselyEqual(it, wanted) } }
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
    val periodSongs = dao.getSongsByIds(periodStats.map { it.songId }).associateBy { it.id }
    return TasteProfile(
        artistsMonth = artistsMonth,
        artistsYear = artistsYear,
        songsMonth = songsMonth.map { it.line() },
        recentLikes = likes.map { it.line() },
        songsThisTimeOfDay = periodStats.mapNotNull { periodSongs[it.songId]?.line() },
        knownSongIds = (songsMonth + likes).map { it.id }.toSet() + periodStats.map { it.songId },
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
        appendLine()
        if (webSearch) {
            appendLine("Use Google Search to find what these artists and artists close to them released in recent months, and to make sure every song you pick really exists.")
        }
        appendLine("Build 4 or 5 sections of 6 to 8 songs each. One section suits this $period. ${if (webSearch) "One section is recent releases. " else ""}The rest each follow one clear idea drawn from the profile. Do not pick any song listed above, and use each song once.")
        appendLine("Reply with this JSON only, no other text:")
        append("""{"intro": "one short sentence about today's page", "sections": [{"title": "...", "reason": "one sentence on why this fits the listener", "songs": [{"title": "...", "artist": "...", "why": "a few words"}]}]}""")
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
    private const val SEARCH_PARALLELISM = 4

    private val state = MutableStateFlow(DiscoverState())
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var loaded = false

    fun state(context: Context): StateFlow<DiscoverState> {
        ensureLoaded(context.applicationContext)
        return state.asStateFlow()
    }

    fun hasKey(context: Context): Boolean = context.dataStore.get(DiscoverGeminiApiKey, "").isNotBlank()

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

    private suspend fun build(context: Context): DiscoverPage {
        val prefs = context.dataStore.data.first()
        val apiKey = prefs[DiscoverGeminiApiKey].orEmpty()
        if (apiKey.isBlank()) throw DiscoverException(DiscoverError.NO_KEY)
        val model = prefs[DiscoverModelKey]?.takeIf { it.isNotBlank() } ?: DiscoverDefaultModel
        val webSearch = prefs[DiscoverWebSearchKey] ?: true

        state.update { it.copy(step = DiscoverStep.PROFILE, error = null, errorDetail = null) }
        val dao = EntryPointAccessors.fromApplication(context, ReleaseWatcherEntryPoint::class.java).databaseDao()
        val now = LocalDateTime.now()
        val profile = buildTasteProfile(dao, now)
        val language = Locale.getDefault().getDisplayLanguage(Locale.ENGLISH).ifBlank { "English" }

        state.update { it.copy(step = DiscoverStep.ASKING) }
        val asked = ask(context, apiKey, model, webSearch) { search -> discoverPrompt(profile, now, language, search) }
        val reply = asked.reply
        val curated = parseCuratedPage(reply.text) ?: throw DiscoverException(DiscoverError.BAD_REPLY, reply.text.take(200))

        state.update { it.copy(step = DiscoverStep.FINDING) }
        val sections = findSongs(curated, profile.knownSongIds)
        if (sections.isEmpty()) throw DiscoverException(DiscoverError.NOTHING_FOUND)
        return DiscoverPage(
            generatedAt = System.currentTimeMillis(),
            period = DayPeriod.of(now.hour).name,
            intro = curated.intro,
            sections = sections,
            model = asked.model,
            searchSuggestionsHtml = reply.searchSuggestionsHtml,
            madeWithoutSearch = webSearch && !asked.searched,
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

    private suspend fun findSongs(curated: CuratedPage, known: Set<String>): List<DiscoverSection> {
        val wanted = curated.sections.flatMap { section -> section.songs }
        val found = HashMap<CuratedSong, DiscoverSong?>()
        coroutineScope {
            wanted.distinct().chunked(SEARCH_PARALLELISM).forEach { batch ->
                batch.map { song -> async { song to find(song) } }.awaitAll().forEach { (song, match) -> found[song] = match }
            }
        }
        val used = HashSet<String>(known)
        return curated.sections.mapNotNull { section ->
            val picks =
                section.songs.mapNotNull { pick ->
                    val song = found[pick] ?: return@mapNotNull null
                    if (!used.add(song.id)) null else DiscoverPick(song, pick.why)
                }
            if (picks.size < 2) null else DiscoverSection(section.title, section.reason, picks)
        }
    }

    private suspend fun find(song: CuratedSong): DiscoverSong? {
        val results =
            YouTube.search("${song.title} ${song.artist}", YouTube.SearchFilter.FILTER_SONG)
                .getOrNull()
                ?.items
                ?.filterIsInstance<SongItem>()
                .orEmpty()
        val index =
            bestMatch(
                song.title,
                song.artist,
                results.map { item -> SongCandidate(item.title, item.artists.map { it.name }, item.isVideoSong) },
            )
        return results.getOrNull(index)?.let(DiscoverSong::from)
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
