/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

class StatsOverviewTest {
    private val now = LocalDateTime.of(2026, 11, 23, 18, 30)

    @Test
    fun `week ends with today and spans seven days`() {
        val (from, to) = StatsRange(StatsRange.Kind.WEEK).bounds(now)
        assertEquals(LocalDateTime.of(2026, 11, 17, 0, 0), from)
        assertEquals(now, to)
        val (pf, pt) = StatsRange(StatsRange.Kind.WEEK, 1).bounds(now)
        assertEquals(LocalDateTime.of(2026, 11, 10, 0, 0), pf)
        assertEquals(LocalDateTime.of(2026, 11, 17, 0, 0), pt)
    }

    @Test
    fun `month and year follow the calendar`() {
        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0), StatsRange(StatsRange.Kind.MONTH, 1).bounds(now).first)
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0), StatsRange(StatsRange.Kind.MONTH, 1).bounds(now).second)
        assertEquals(LocalDateTime.of(2025, 1, 1, 0, 0), StatsRange(StatsRange.Kind.YEAR, 1).bounds(now).first)
        assertNull(StatsRange(StatsRange.Kind.ALL).previous())
    }

    @Test
    fun `week bars put each day's minutes in its slot`() {
        val (from, to) = StatsRange(StatsRange.Kind.WEEK).bounds(now)
        val monday = LocalDate.of(2026, 11, 23).toEpochDay()
        val rows = listOf(DailyPlayTime(monday, 120_000L), DailyPlayTime(monday - 6, 60_000L))
        val bars = statsBars(StatsRange(StatsRange.Kind.WEEK), from, to, rows, null, Locale.ENGLISH)
        assertEquals(7, bars.size)
        assertEquals(60_000L, bars.first().playTimeMs)
        assertEquals(120_000L, bars.last().playTimeMs)
    }

    @Test
    fun `year bars sum days into months`() {
        val range = StatsRange(StatsRange.Kind.YEAR)
        val (from, to) = range.bounds(now)
        val rows = listOf(
            DailyPlayTime(LocalDate.of(2026, 3, 1).toEpochDay(), 1_000L),
            DailyPlayTime(LocalDate.of(2026, 3, 31).toEpochDay(), 2_000L),
        )
        val bars = statsBars(range, from, to, rows, null, Locale.ENGLISH)
        assertEquals(12, bars.size)
        assertEquals(3_000L, bars[2].playTimeMs)
    }

    @Test
    fun `change percent compares with the previous period`() {
        val overview = StatsOverview.Empty.copy(totalPlayTimeMs = 150 * 60_000L, previousPlayTimeMs = 100 * 60_000L)
        assertEquals(50, overview.changePercent)
        assertNull(StatsOverview.Empty.copy(totalPlayTimeMs = 10, previousPlayTimeMs = 0).changePercent)
    }

    @Test
    fun `peak hour is the busiest hour`() {
        val hourly = hourlyTotals(listOf(HourlyPlayTime(22, 500L), HourlyPlayTime(8, 100L)))
        assertEquals(22, StatsOverview.Empty.copy(hourly = hourly).peakHour)
    }
}
