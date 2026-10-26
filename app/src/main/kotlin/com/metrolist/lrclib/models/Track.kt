package com.metrolist.lrclib.models

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: Int,
    val trackName: String,
    val artistName: String,
    val duration: Double,
    val plainLyrics: String?,
    val syncedLyrics: String?,
)

internal fun List<Track>.matchingTracks(duration: Int, title: String, artist: String): List<Track> =
    mapNotNull { track ->
        if (track.syncedLyrics.isNullOrBlank() && track.plainLyrics.isNullOrBlank()) return@mapNotNull null
        com.metrolist.music.lyrics.LyricsMatch.score(title, artist, duration, track.trackName,
            track.artistName, track.duration, tolerance = 5.0)?.let { score -> track to score }
    }.sortedWith(compareByDescending<Pair<Track, Double>> { it.second }
        .thenByDescending { !it.first.syncedLyrics.isNullOrBlank() })
        .map { it.first }
