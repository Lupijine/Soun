/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.spotify

import com.metrolist.innertube.models.SongItem
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/** Picks the YouTube Music song that best stands for a Spotify track. */
object SpotifyTrackMatcher {
    private const val MIN_SCORE = 0.6
    private const val MIN_TITLE = 0.5
    private const val DURATION_TOLERANCE_S = 10.0
    private const val DURATION_CUTOFF_S = 40.0

    // A result with one of these words is a different recording unless the Spotify title has it too.
    private val versionWords = setOf(
        "live", "remix", "acoustic", "instrumental", "karaoke", "cover", "slowed", "sped",
        "nightcore", "reverb", "demo", "8d",
    )
    private val marks = Regex("\\p{M}+")
    private val separators = Regex("[^\\p{L}\\p{N}]+")
    private val bracketed = Regex("""[(\[][^)\]]*[)\]]""")
    private val dashSuffix = Regex("""\s[-–—]\s.*$""")

    fun query(track: SpotifyTrack): String =
        listOfNotNull(track.title, track.artists.firstOrNull()).joinToString(" ")

    /** A second, plainer query ("Song - 2011 Remaster" → "Song Artist"), or null if it would repeat [query]. */
    fun fallbackQuery(track: SpotifyTrack): String? {
        val core = coreTitle(track.title).takeIf { it.isNotBlank() } ?: return null
        val plain = listOfNotNull(core, track.artists.firstOrNull()).joinToString(" ")
        return plain.takeIf { it != query(track) }
    }

    fun bestMatch(track: SpotifyTrack, results: List<SongItem>): SongItem? =
        results.asSequence()
            .filter { !it.isEpisode }
            .mapIndexedNotNull { rank, song -> accepted(track, song, rank)?.let { song to it } }
            .maxByOrNull { it.second }
            ?.first

    /** The match score of [song] at search position [rank], or null if it is not the same song. */
    fun accepted(track: SpotifyTrack, song: SongItem, rank: Int): Double? {
        val title = titleScore(track.title, song.title)
        val artist = artistScore(track.artists, song.artists.map { it.name })
        val durationDiff = durationDiff(track, song)
        val score = 0.5 * title + 0.3 * artist + 0.2 * durationScore(durationDiff) + max(0.0, 0.08 - 0.02 * rank)
        if (title >= MIN_TITLE && score >= MIN_SCORE) return score
        // Same artist, same length, top hit: usually the same recording with its title in another script.
        if (rank == 0 && artist >= 1.0 && durationDiff != null && durationDiff <= 3.0) return score
        return null
    }

    internal fun titleScore(spotifyTitle: String, candidateTitle: String): Double {
        val fullTokens = tokens(spotifyTitle)
        val core = tokens(coreTitle(spotifyTitle)).ifEmpty { fullTokens }
        val candidateTokens = tokens(candidateTitle)
        val candidateCore = tokens(coreTitle(candidateTitle)).ifEmpty { candidateTokens }
        if (core.isEmpty() || candidateCore.isEmpty()) return 0.0
        val candidateSet = candidateTokens.toSet()
        val fullSet = fullTokens.toSet()
        val covered = core.count { it in candidateSet }.toDouble() / core.size
        val extra = candidateCore.count { it in fullSet }.toDouble() / candidateCore.size
        val version = if (candidateTokens.any { it in versionWords && it !in fullSet }) 0.5 else 1.0
        return (0.7 * covered + 0.3 * extra) * version
    }

    internal fun artistScore(spotifyArtists: List<String>, candidateArtists: List<String>): Double {
        if (spotifyArtists.isEmpty()) return 0.5
        val candidates = candidateArtists.map(::tokens).filter { it.isNotEmpty() }
        if (candidates.isEmpty()) return 0.0
        fun matches(name: String): Boolean {
            val wanted = tokens(name)
            if (wanted.isEmpty()) return false
            return candidates.any { have -> have.containsAll(wanted) || wanted.containsAll(have) }
        }
        return when {
            matches(spotifyArtists.first()) -> 1.0
            spotifyArtists.drop(1).any(::matches) -> 0.6
            else -> 0.0
        }
    }

    private fun durationDiff(track: SpotifyTrack, song: SongItem): Double? {
        val spotify = track.durationMs ?: return null
        val youtube = song.duration?.takeIf { it > 0 } ?: return null
        return abs(spotify / 1000.0 - youtube)
    }

    private fun durationScore(diff: Double?): Double = when {
        diff == null -> 0.5
        diff <= DURATION_TOLERANCE_S -> 1.0
        diff >= DURATION_CUTOFF_S -> 0.0
        else -> 1.0 - (diff - DURATION_TOLERANCE_S) / (DURATION_CUTOFF_S - DURATION_TOLERANCE_S)
    }

    private fun coreTitle(title: String): String =
        title.replace(bracketed, " ").replace(dashSuffix, "").trim()

    internal fun tokens(text: String): List<String> =
        Normalizer.normalize(text, Normalizer.Form.NFKD)
            .replace(marks, "")
            .lowercase(Locale.ROOT)
            .replace("&", " and ")
            .split(separators)
            .filter { it.isNotEmpty() }
}
