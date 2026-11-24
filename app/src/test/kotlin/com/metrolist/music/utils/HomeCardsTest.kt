package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HomeCardsTest {
    private val today = LocalDate.of(2026, 11, 24)

    private fun played(vararg daysAgo: Long, ms: Long = 5 * 60_000L) =
        daysAgo.map { DailyPlayTime(today.minusDays(it).toEpochDay(), ms) }

    @Test
    fun `streak counts days in a row up to today`() {
        val streak = listeningStreak(played(0, 1, 2, 3, 5, 6), today)
        assertEquals(4, streak.current)
        assertTrue(streak.listenedToday)
        assertEquals(4, streak.best)
        assertEquals(STREAK_DAYS_SHOWN, streak.lastDays.size)
        assertTrue(streak.lastDays.last())
        assertFalse(streak.lastDays[STREAK_DAYS_SHOWN - 1 - 4])
    }

    @Test
    fun `a streak is not broken before today's first song`() {
        val streak = listeningStreak(played(1, 2, 3), today)
        assertEquals(3, streak.current)
        assertFalse(streak.listenedToday)
    }

    @Test
    fun `the best streak can be an older one, and short plays do not count`() {
        val rows = played(0, 1, 10, 11, 12, 13, 14) + played(2, ms = 20_000L)
        val streak = listeningStreak(rows, today)
        assertEquals(2, streak.current)
        assertEquals(5, streak.best)
    }

    @Test
    fun `day periods cover every hour once`() {
        (0..23).forEach { hour -> assertEquals(1, DayPeriod.entries.count { hour in it.hours }) }
        assertEquals(DayPeriod.NIGHT, DayPeriod.of(23))
        assertEquals(DayPeriod.NIGHT, DayPeriod.of(2))
        assertEquals(DayPeriod.MORNING, DayPeriod.of(7))
        assertEquals(DayPeriod.EVENING, DayPeriod.of(19))
    }

    @Test
    fun `on this day looks at the week around that date`() {
        val (from, to) = onThisDayWindow(today, OnThisDayRange.YEAR)
        assertEquals(LocalDate.of(2025, 11, 21).atStartOfDay(), from)
        assertEquals(LocalDate.of(2025, 11, 28).atStartOfDay(), to)
    }
}
