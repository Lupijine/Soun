/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import androidx.compose.runtime.Immutable
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Song
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Room row: how much one song was played. [lastPlayed] is a local wall-clock time in epoch ms. */
data class SongPlayStats(
    val songId: String,
    val plays: Int,
    val playTime: Long,
    val lastPlayed: Long,
)

/* ---------------------------------------------------------------------------------------- */
/* Listening streak                                                                          */

@Immutable
data class StreakSummary(
    /** Days in a row with listening, up to today (or yesterday while today has none yet). */
    val current: Int,
    val best: Int,
    val listenedToday: Boolean,
    /** The last [STREAK_DAYS_SHOWN] days, oldest first; the last one is today. */
    val lastDays: List<Boolean>,
)

const val STREAK_DAYS_SHOWN = 14

/** A day counts once a minute of music was played. */
const val STREAK_MIN_PLAY_MS = 60_000L

/** Streak from per-day play time rows ([DailyPlayTime.day] = epoch day). */
fun listeningStreak(rows: List<DailyPlayTime>, today: LocalDate): StreakSummary {
    val days =
        rows.groupingBy { it.day }.fold(0L) { total, row -> total + row.playTime }
            .filterValues { it >= STREAK_MIN_PLAY_MS }
            .keys
    val todayDay = today.toEpochDay()
    val listenedToday = todayDay in days
    var current = 0
    var day = if (listenedToday) todayDay else todayDay - 1
    while (day in days) {
        current++
        day--
    }
    var best = 0
    var run = 0
    var previous = Long.MIN_VALUE
    for (d in days.sorted()) {
        run = if (d == previous + 1) run + 1 else 1
        best = maxOf(best, run)
        previous = d
    }
    val lastDays = List(STREAK_DAYS_SHOWN) { offset -> (todayDay - (STREAK_DAYS_SHOWN - 1 - offset)) in days }
    return StreakSummary(current, maxOf(best, current), listenedToday, lastDays)
}

/* ---------------------------------------------------------------------------------------- */
/* Time of day                                                                               */

enum class DayPeriod(val hours: List<Int>) {
    MORNING((5..11).toList()),
    AFTERNOON((12..16).toList()),
    EVENING((17..21).toList()),
    NIGHT(listOf(22, 23, 0, 1, 2, 3, 4)),
    ;

    companion object {
        fun of(hour: Int): DayPeriod = entries.first { hour in it.hours }
    }
}

/* ---------------------------------------------------------------------------------------- */
/* On this day                                                                               */

enum class OnThisDayRange(val months: Long) {
    MONTH(1),
    HALF_YEAR(6),
    YEAR(12),
}

/** The week around the same day [range] ago: three days before it to three days after. */
fun onThisDayWindow(today: LocalDate, range: OnThisDayRange): Pair<LocalDateTime, LocalDateTime> {
    val day = today.minusMonths(range.months)
    return day.minusDays(3).atStartOfDay() to day.plusDays(4).atStartOfDay()
}

/* ---------------------------------------------------------------------------------------- */
/* What the home screen shows                                                                */

@Immutable
data class OnThisDayWindow(
    val range: OnThisDayRange,
    val topSong: Song,
    val topSongPlays: Int,
    val songs: List<Song>,
)

@Immutable
data class DriftedSongs(
    val songs: List<Song>,
    val minPlays: Int,
    /** When the most recent of them was last played. */
    val lastPlayed: LocalDate,
)

@Immutable
data class TimeOfDayMix(
    val period: DayPeriod,
    /** The hour (0..23) inside [period] with the most listening. */
    val peakHour: Int,
    val songs: List<Song>,
)

@Immutable
data class HomeCards(
    val streak: StreakSummary? = null,
    val onThisDay: List<OnThisDayWindow> = emptyList(),
    val drifted: DriftedSongs? = null,
    val timeOfDay: TimeOfDayMix? = null,
)

private const val DRIFT_AFTER_DAYS = 60L
private const val DRIFT_MIN_PLAYS = 8
private const val DRIFT_MIN_SONGS = 5
private const val MIX_MIN_SONGS = 8

/** Builds the listening cards from the play history (runs on a background thread). */
suspend fun loadHomeCards(database: MusicDatabase, now: LocalDateTime = LocalDateTime.now()): HomeCards {
    val today = now.toLocalDate()

    val streak =
        listeningStreak(
            database.dailyPlayTimeInRange(today.minusDays(400).atStartOfDay(), now).first(),
            today,
        ).takeIf { it.current >= 2 }

    val onThisDay =
        OnThisDayRange.entries.mapNotNull { range ->
            val (from, to) = onThisDayWindow(today, range)
            val stats = database.songStatsInRange(from, to, limit = 40)
            if (stats.size < 3) return@mapNotNull null
            val songs = database.getSongsByIds(stats.map { it.songId }).associateBy { it.id }
            val ordered = stats.mapNotNull { songs[it.songId] }
            val top = stats.firstNotNullOfOrNull { stat -> songs[stat.songId]?.let { it to stat.plays } } ?: return@mapNotNull null
            OnThisDayWindow(range, top.first, top.second, ordered)
        }

    val drifted =
        database.driftedSongStats(now.minusDays(DRIFT_AFTER_DAYS), DRIFT_MIN_PLAYS, limit = 50)
            .takeIf { it.size >= DRIFT_MIN_SONGS }
            ?.let { stats ->
                val songs = database.getSongsByIds(stats.map { it.songId }).associateBy { it.id }
                val ordered = stats.mapNotNull { songs[it.songId] }
                if (ordered.size < DRIFT_MIN_SONGS) return@let null
                DriftedSongs(
                    songs = ordered,
                    minPlays = stats.minOf { it.plays },
                    lastPlayed = localDateOf(stats.maxOf { it.lastPlayed }),
                )
            }

    val period = DayPeriod.of(now.hour)
    val since = now.minusDays(60)
    val timeOfDay =
        database.songStatsAtHours(since, period.hours, limit = 30)
            .takeIf { it.size >= MIX_MIN_SONGS }
            ?.let { stats ->
                val songs = database.getSongsByIds(stats.map { it.songId }).associateBy { it.id }
                val ordered = stats.mapNotNull { songs[it.songId] }
                if (ordered.size < MIX_MIN_SONGS) return@let null
                val hourly = database.hourlyPlayTimeInRange(since, now).first()
                val peak = hourly.filter { it.hour in period.hours }.maxByOrNull { it.playTime }?.hour ?: now.hour
                TimeOfDayMix(period, peak, ordered)
            }

    return HomeCards(streak, onThisDay, drifted, timeOfDay)
}

/** Event timestamps are local wall-clock times stored as if they were UTC. */
private fun localDateOf(epochMs: Long): LocalDate =
    LocalDateTime.ofEpochSecond(epochMs / 1000, 0, ZoneOffset.UTC).toLocalDate()
