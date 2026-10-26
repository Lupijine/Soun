package com.metrolist.lrclib

import com.metrolist.lrclib.models.Track
import com.metrolist.lrclib.models.matchingTracks
import com.metrolist.music.lyrics.LyricsMatch
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json

object LrcLib {
    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(
                    Json {
                        isLenient = true
                        ignoreUnknownKeys = true
                    },
                )
            }

            defaultRequest {
                url("https://lrclib.net")
            }

            expectSuccess = true
        }
    }

    private suspend fun queryLyricsWithParams(
        trackName: String? = null,
        artistName: String? = null,
        albumName: String? = null,
        query: String? = null,
    ): List<Track> = runCatching {
        client.get("/api/search") {
            if (query != null) parameter("q", query)
            if (trackName != null) parameter("track_name", trackName)
            if (artistName != null) parameter("artist_name", artistName)
            if (albumName != null) parameter("album_name", albumName)
        }.body<List<Track>>()
    }.getOrDefault(emptyList())

    private suspend fun queryLyrics(artist: String, title: String, album: String? = null, duration: Int = -1): List<Track> {
        val cleanedTitle = LyricsMatch.cleanTitle(title)
        val searches: List<suspend () -> List<Track>> = listOf(
            { queryLyricsWithParams(trackName = cleanedTitle, artistName = artist, albumName = album) },
            { queryLyricsWithParams(trackName = cleanedTitle, artistName = artist) },
            { queryLyricsWithParams(query = "$artist $cleanedTitle") },
            { queryLyricsWithParams(trackName = cleanedTitle) },
        )
        for (search in searches) {
            currentCoroutineContext().ensureActive()
            val matches = search().matchingTracks(duration, title, artist)
            if (matches.isNotEmpty()) return matches
        }
        return emptyList()
    }

    suspend fun getLyrics(title: String, artist: String, duration: Int, album: String? = null) = runCatching {
        val track = queryLyrics(artist, title, album, duration).firstOrNull()
            ?: throw IllegalStateException("Lyrics unavailable")
        track.syncedLyrics?.takeIf { it.isNotBlank() } ?: track.plainLyrics!!
    }

    suspend fun getAllLyrics(title: String, artist: String, duration: Int, album: String? = null, callback: (String) -> Unit) {
        val tracks = queryLyrics(artist, title, album, duration)
        val results = tracks.flatMap { listOfNotNull(it.syncedLyrics?.takeIf(String::isNotBlank), it.plainLyrics?.takeIf(String::isNotBlank)) }
            .distinct().take(5)
        for (lyrics in results) {
            currentCoroutineContext().ensureActive()
            callback(lyrics)
        }
    }

    suspend fun lyrics(artist: String, title: String) = runCatching { queryLyrics(artist, title) }

    @JvmInline
    value class Lyrics(
        val text: String,
    ) {
        val sentences
            get() =
                runCatching {
                    buildMap {
                        put(0L, "")
                        text.trim().lines().filter { it.length >= 10 }.forEach {
                            put(
                                it[8].digitToInt() * 10L +
                                    it[7].digitToInt() * 100 +
                                    it[5].digitToInt() * 1000 +
                                    it[4].digitToInt() * 10000 +
                                    it[2].digitToInt() * 60 * 1000 +
                                    it[1].digitToInt() * 600 * 1000,
                                it.substring(10),
                            )
                        }
                    }
                }.getOrNull()
    }
}


