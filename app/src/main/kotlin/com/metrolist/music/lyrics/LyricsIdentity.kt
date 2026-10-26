package com.metrolist.music.lyrics

import com.metrolist.music.db.entities.LyricsEntity
import java.text.Normalizer
import java.util.Locale

internal fun lyricsForSong(mediaId: String?, current: LyricsEntity?, previous: LyricsEntity? = null, translating: Boolean = false): LyricsEntity? =
    current?.takeIf { mediaId != null && it.id == mediaId }
        ?: previous?.takeIf { translating && mediaId != null && it.id == mediaId }

/** Providers without track identity must agree with lyrics from a metadata-validated source. */
internal fun sameLyricsBody(candidate: String, reference: String): Boolean {
    fun body(text: String): String = Normalizer.normalize(
        text.replace(Regex("""\[[^\]]*\]|<[^>]*>|\{[^}]*\}"""), ""), Normalizer.Form.NFKD,
    ).lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")
    val actual = body(candidate)
    val expected = body(reference)
    if (actual.isEmpty() || expected.isEmpty()) return false
    if (minOf(actual.length, expected.length) < 40) return actual == expected
    val actualParts = actual.windowed(12).toSet()
    val expectedParts = expected.windowed(12).toSet()
    val overlap = actualParts.intersect(expectedParts).size.toDouble()
    return overlap / minOf(actualParts.size, expectedParts.size) >= 0.85 &&
        overlap / maxOf(actualParts.size, expectedParts.size) >= 0.65
}
