package dev.korbi.fjell

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarTest {
    private val hour = 60 * 60 * 1000L
    private val today = LocalDate.of(2026, 11, 1)
    private val birthday = CalendarEvent(1, "Mum's birthday", 0L, day = today)

    @Test
    fun `a timed event within four hours wins`() {
        val dentist = CalendarEvent(2, "Dentist", 10 * hour)
        assertEquals(dentist, Calendar.next(listOf(birthday, dentist), now = 7 * hour))
        assertEquals(null, Calendar.next(listOf(dentist), now = 5 * hour))
        assertEquals(null, Calendar.next(listOf(dentist), now = 11 * hour))
    }

    @Test
    fun `all-day events show until the evening of their day`() {
        assertEquals(birthday, Calendar.allDayNow(listOf(birthday), today.atTime(9, 0)))
        assertEquals(null, Calendar.allDayNow(listOf(birthday), today.atTime(18, 0)))
        assertEquals(null, Calendar.allDayNow(listOf(birthday), LocalDateTime.of(2026, 11, 2, 9, 0)))
    }
}
