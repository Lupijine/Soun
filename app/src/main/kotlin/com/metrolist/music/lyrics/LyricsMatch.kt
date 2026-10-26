package com.metrolist.music.lyrics

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/** Identity is mandatory; timing and sync quality only rank candidates after that check. */
internal object LyricsMatch {
    private val brackets = Regex("""\([^()]*\)|\[[^\[\]]*\]|【[^】]*】""")
    private val promotional = Regex("""(?i)(?:(?:official|music|lyric|lyrics|audio|video|visualizer|hd|hq|4k)\s*)+""")
    private val suffix = Regex("""(?i)\s+[-|–—]\s*((?:(?:official|music|lyric|lyrics|audio|video|visualizer|hd|hq|4k)\s*)+)$""")
    private val featuring = Regex("""(?i)\s+(?:feat\.?|ft\.?|featuring)\s+""")
    private val contributors = Regex("""(?i)\s*(?:,|;|、|\s&\s|\sfeat\.?\s|\sft\.?\s|\sfeaturing\s)\s*""")
    private val marks = Regex("\\p{M}+")
    private val punctuation = Regex("[^\\p{L}\\p{N}]+")

    fun cleanTitle(title: String): String = title.replace(brackets) { match ->
        if (promotional.matches(match.value.drop(1).dropLast(1).trim())) "" else match.value
    }.replace(suffix, "").trim()

    private fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(marks, "").lowercase(Locale.ROOT).replace(punctuation, "")

    private fun titleKey(title: String): String = normalized(
        cleanTitle(title).replace(Regex("""(?i)[(\[]\s*(?:feat\.?|ft\.?|featuring)\s+[^)\]]+[)\]]"""), "")
            .split(featuring, limit = 2).first(),
    )

    fun titlesMatch(expected: String, actual: String): Boolean =
        titleKey(expected).let { it.isNotEmpty() && it == titleKey(actual) }

    private fun artistKey(artist: String) = normalized(artist.replace(Regex("(?i)\\s+-\\s+Topic$"), ""))

    fun artistsMatch(expected: String, actual: String): Boolean {
        val requested = artistKey(expected)
        val candidate = artistKey(actual)
        if (requested.isEmpty() || candidate.isEmpty()) return false
        if (requested == candidate) return true
        // Require the lead artist; a shared guest or a substring is not enough.
        val requestedArtists = expected.split(contributors).map(::artistKey).filter(String::isNotEmpty)
        val candidateArtists = actual.split(contributors).map(::artistKey).filter(String::isNotEmpty)
        if (requestedArtists.firstOrNull() != candidateArtists.firstOrNull()) return false
        return requestedArtists.all { it in candidateArtists } || candidateArtists.all { it in requestedArtists }
    }

    fun score(title: String, artist: String, duration: Int, candidateTitle: String,
              candidateArtist: String, candidateDuration: Double?, tolerance: Double = 8.0): Double? {
        if (!titlesMatch(title, candidateTitle) || !artistsMatch(artist, candidateArtist)) return null
        val difference = if (duration > 0 && candidateDuration != null && candidateDuration.isFinite() && candidateDuration > 0) {
            abs(duration - candidateDuration)
        } else null
        if (difference != null && difference > tolerance) return null
        return 100.0 + (if (artistKey(artist) == artistKey(candidateArtist)) 10.0 else 0.0) +
            (if (difference != null) tolerance - difference else 0.0)
    }
}
