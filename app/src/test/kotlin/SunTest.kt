package dev.korbi.fjell

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SunTest {
    private val munich = Place(48.14, 11.58)
    private val berlinTime = ZoneId.of("Europe/Berlin")

    private fun assertNear(expected: String, minute: Int?) {
        val (h, m) = expected.split(':').map(String::toInt)
        assertTrue("expected $expected, got ${minute?.let { "%02d:%02d".format(it / 60, it % 60) }}", minute != null && abs(minute - (h * 60 + m)) <= 3)
    }

    @Test
    fun `munich at midsummer`() {
        val sun = Sun.times(LocalDate.of(2026, 6, 21), munich, berlinTime)
        assertNear("05:12", sun.sunrise)
        assertNear("21:17", sun.sunset)
    }

    @Test
    fun `munich at midwinter`() {
        val sun = Sun.times(LocalDate.of(2026, 12, 21), munich, berlinTime)
        assertNear("08:01", sun.sunrise)
        assertNear("16:22", sun.sunset)
    }

    @Test
    fun `no sunrise in the polar night`() {
        assertEquals(SunTimes(null, null), Sun.times(LocalDate.of(2026, 12, 21), Place(78.2, 15.6), ZoneId.of("Arctic/Longyearbyen")))
    }
}
