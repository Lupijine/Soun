/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import androidx.compose.runtime.Immutable
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.db.entities.Song
import java.time.LocalDate

/** Room row: total play time (ms) for one local day ([day] is that day's epoch day). */
data class DailyPlayTime(
    val day: Long,
    val playTime: Long,
)

/** The "Your week" card's data for the 7 days ending today. */
@Immutable
data class WeekSummary(
    val today: LocalDate,
    /** Play time per day in ms, oldest first; the last entry is today. */
    val dailyPlayTimeMs: List<Long>,
    val topArtist: Artist?,
    val topSong: Song?,
) {
    val totalPlayTimeMs: Long get() = dailyPlayTimeMs.sum()
    val totalMinutes: Long get() = totalPlayTimeMs / 60_000L
}

const val WEEK_DAYS = 7

/** The card stays hidden below this much listening in the week. */
const val WEEK_SUMMARY_MIN_PLAY_MS = 10 * 60_000L

/** First day of the 7-day window that ends with [today]. */
fun weekStart(today: LocalDate): LocalDate = today.minusDays((WEEK_DAYS - 1).toLong())

/** Spreads per-day rows over the 7 days ending [today], filling days without listening with 0. */
fun weekDailyPlayTime(today: LocalDate, rows: List<DailyPlayTime>): List<Long> {
    val byDay = rows.groupingBy { it.day }.fold(0L) { total, row -> total + row.playTime }
    val first = weekStart(today).toEpochDay()
    return List(WEEK_DAYS) { offset -> (byDay[first + offset] ?: 0L).coerceAtLeast(0L) }
}

fun hasEnoughListeningForWeek(dailyPlayTimeMs: List<Long>): Boolean =
    dailyPlayTimeMs.sum() >= WEEK_SUMMARY_MIN_PLAY_MS
