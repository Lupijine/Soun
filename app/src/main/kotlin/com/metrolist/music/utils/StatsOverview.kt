/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Room row: total play time (ms) that started in one hour of the local day (0–23). */
data class HourlyPlayTime(
    val hour: Int,
    val playTime: Long,
)

/** The stats page's time range: a kind, and how many of those to step back from now. */
@Immutable
data class StatsRange(
    val kind: Kind = Kind.WEEK,
    val offset: Int = 0,
) {
    enum class Kind { WEEK, MONTH, YEAR, ALL }

    /** Start (exclusive) and end (inclusive) of the range, matching the stats queries. */
    fun bounds(now: LocalDateTime): Pair<LocalDateTime, LocalDateTime> {
        val today = now.toLocalDate()
        return when (kind) {
            Kind.WEEK -> {
                val end = today.plusDays(1).atStartOfDay().minusWeeks(offset.toLong())
                end.minusDays(7) to minOf(end, now)
            }
            Kind.MONTH -> {
                val start = YearMonth.from(today).minusMonths(offset.toLong()).atDay(1).atStartOfDay()
                start to minOf(start.plusMonths(1), now)
            }
            Kind.YEAR -> {
                val start = LocalDate.of(today.year - offset, 1, 1).atStartOfDay()
                start to minOf(start.plusYears(1), now)
            }
            Kind.ALL -> LocalDateTime.of(1970, 1, 1, 0, 0) to now
        }
    }

    /** The same-length range just before this one, for the comparison; none for all time. */
    fun previous(): StatsRange? = if (kind == Kind.ALL) null else copy(offset = offset + 1)
}

@Immutable
data class StatsBar(val label: String, val playTimeMs: Long, val highlighted: Boolean)

@Immutable
data class StatsOverview(
    val totalPlayTimeMs: Long,
    val previousPlayTimeMs: Long?,
    val plays: Int,
    val uniqueSongs: Int,
    val uniqueArtists: Int,
    val bars: List<StatsBar>,
    /** Play time per hour of the day, 24 entries. */
    val hourly: List<Long>,
) {
    val peakHour: Int? get() = hourly.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }?.index

    /** Change against the previous range in percent, or null when there is nothing to compare. */
    val changePercent: Int?
        get() {
            val previous = previousPlayTimeMs ?: return null
            if (previous < 60_000L) return null
            return (((totalPlayTimeMs - previous).toDouble() / previous) * 100).toInt()
        }

    companion object {
        val Empty = StatsOverview(0, null, 0, 0, 0, emptyList(), List(24) { 0L })
    }
}

/**
 * Turns per-day rows into the chart's bars: days for a week or a month, months for a year,
 * and months (or years when that would be too many) for all time.
 */
fun statsBars(
    range: StatsRange,
    from: LocalDateTime,
    to: LocalDateTime,
    rows: List<DailyPlayTime>,
    firstListen: LocalDate?,
    locale: Locale = Locale.getDefault(),
): List<StatsBar> {
    val byDay = rows.groupingBy { it.day }.fold(0L) { total, row -> total + row.playTime.coerceAtLeast(0L) }
    val today = to.minusNanos(1).toLocalDate()
    fun dayTotal(date: LocalDate) = byDay[date.toEpochDay()] ?: 0L

    return when (range.kind) {
        StatsRange.Kind.WEEK -> {
            val first = from.toLocalDate()
            List(7) { i ->
                val date = first.plusDays(i.toLong())
                StatsBar(
                    label = date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                    playTimeMs = dayTotal(date),
                    highlighted = date == LocalDate.now(),
                )
            }
        }
        StatsRange.Kind.MONTH -> {
            val month = YearMonth.from(from.toLocalDate())
            List(month.lengthOfMonth()) { i ->
                val date = month.atDay(i + 1)
                StatsBar(
                    label = if ((i + 1) % 7 == 1) "${i + 1}" else "",
                    playTimeMs = dayTotal(date),
                    highlighted = date == LocalDate.now(),
                )
            }
        }
        StatsRange.Kind.YEAR -> {
            val year = from.toLocalDate().year
            List(12) { i ->
                val month = YearMonth.of(year, i + 1)
                StatsBar(
                    label = month.month.getDisplayName(TextStyle.NARROW, locale),
                    playTimeMs = (1..month.lengthOfMonth()).sumOf { d -> dayTotal(month.atDay(d)) },
                    highlighted = month == YearMonth.now(),
                )
            }
        }
        StatsRange.Kind.ALL -> {
            val start = YearMonth.from(firstListen ?: today)
            val end = YearMonth.from(today)
            val months = generateSequence(start) { it.plusMonths(1) }.takeWhile { !it.isAfter(end) }.toList()
            if (months.size <= 24) {
                months.map { month ->
                    StatsBar(
                        label = if (month.monthValue == 1 || month == start) "’%02d".format(month.year % 100) else "",
                        playTimeMs = (1..month.lengthOfMonth()).sumOf { d -> dayTotal(month.atDay(d)) },
                        highlighted = month == end,
                    )
                }
            } else {
                (start.year..end.year).map { year ->
                    StatsBar(
                        label = "$year",
                        playTimeMs = byDay.entries
                            .filter { LocalDate.ofEpochDay(it.key).year == year }
                            .sumOf { it.value },
                        highlighted = year == end.year,
                    )
                }
            }
        }
    }
}

/** 24 hourly totals from the grouped rows. */
fun hourlyTotals(rows: List<HourlyPlayTime>): List<Long> {
    val byHour = rows.groupingBy { it.hour }.fold(0L) { total, row -> total + row.playTime.coerceAtLeast(0L) }
    return List(24) { byHour[it] ?: 0L }
}
