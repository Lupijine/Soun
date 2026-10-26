/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

import android.content.Context
import com.metrolist.lrclib.LrcLib
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.metrolist.music.betterlyrics.BetterLyrics
import com.metrolist.music.constants.EnableBetterLyricsKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get

object BetterLyricsProvider : LyricsProvider {
    override val name = "BetterLyrics"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableBetterLyricsKey] ?: true

    override suspend fun getLyrics(
        context: Context,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = runCatching {
        coroutineScope {
            val reference = async {
                if (LrcLibLyricsProvider.isEnabled(context)) {
                    val result = LrcLib.getLyrics(title, artist, duration, album)
                    (result.exceptionOrNull() as? CancellationException)?.let { throw it }
                    result.getOrNull()?.let { return@async it }
                }
                YouTubeLyricsProvider.getLyrics(context, id, title, artist, duration, album).getOrThrow()
            }
            val candidate = async { BetterLyrics.getLyrics(title, artist, duration, album).getOrThrow() }
            val lyrics = candidate.await()
            val verifiedLyrics = reference.await()
            check(withContext(Dispatchers.Default) { sameLyricsBody(lyrics, verifiedLyrics) }) { "Lyrics identity could not be verified" }
            lyrics
        }
    }
}
