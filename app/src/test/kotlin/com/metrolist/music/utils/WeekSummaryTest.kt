package com.metrolist.music.utils

import com.metrolist.music.db.Converters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeekSummaryTest {
    private val today = LocalDate.of(2026, 11, 23)

    @Test fun `days are laid out oldest first and gaps are zero`() {
        val rows = listOf(
            DailyPlayTime(today.toEpochDay(), 5_000),
            DailyPlayTime(today.minusDays(6).toEpochDay(), 1_000),
            DailyPlayTime(today.minusDays(2).toEpochDay(), 3_000),
            // Outside the window: ignored.
            DailyPlayTime(today.minusDays(7).toEpochDay(), 99_000),
        )
        assertEquals(listOf(1_000L, 0L, 0L, 0L, 3_000L, 0L, 5_000L), weekDailyPlayTime(today, rows))
        assertEquals(LocalDate.of(2026, 11, 17), weekStart(today))
    }

    @Test fun `stored event timestamps divide into their local calendar day`() {
        val converters = Converters()
        val lateEvening = today.atTime(23, 59, 30)
        val millis = converters.dateToTimestamp(lateEvening)!!
        assertEquals(today.toEpochDay(), millis / 86_400_000L)
        val midnight = converters.dateToTimestamp(today.atStartOfDay())!!
        assertEquals(today.toEpochDay(), midnight / 86_400_000L)
    }

    @Test fun `card needs ten minutes in the week`() {
        assertFalse(hasEnoughListeningForWeek(List(7) { 0L }))
        assertFalse(hasEnoughListeningForWeek(listOf(9 * 60_000L + 59_999L, 0L, 0L, 0L, 0L, 0L, 0L)))
        assertTrue(hasEnoughListeningForWeek(listOf(5 * 60_000L, 0L, 0L, 0L, 0L, 0L, 5 * 60_000L)))
        val summary = WeekSummary(today, listOf(60_000L, 0L, 0L, 0L, 0L, 0L, 125_000L), topArtist = null, topSong = null)
        assertEquals(3L, summary.totalMinutes)
    }
}
