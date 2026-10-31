package dev.korbi.fjell

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherTest {
    @Test
    fun `clear sky depends on day and night`() {
        assertEquals(Sky.Clear, Weather.skyFor(0, isDay = true))
        assertEquals(Sky.ClearNight, Weather.skyFor(0, isDay = false))
    }

    @Test
    fun `codes map to their glyph`() {
        assertEquals(Sky.PartlyCloudy, Weather.skyFor(2, isDay = true))
        assertEquals(Sky.Cloudy, Weather.skyFor(3, isDay = true))
        assertEquals(Sky.Fog, Weather.skyFor(45, isDay = true))
        assertEquals(Sky.Rain, Weather.skyFor(61, isDay = true))
        assertEquals(Sky.Rain, Weather.skyFor(81, isDay = false))
        assertEquals(Sky.Snow, Weather.skyFor(73, isDay = true))
        assertEquals(Sky.Snow, Weather.skyFor(86, isDay = true))
        assertEquals(Sky.Thunder, Weather.skyFor(95, isDay = true))
    }

    @Test
    fun `unknown codes fall back to clouds`() {
        assertEquals(Sky.Cloudy, Weather.skyFor(42, isDay = true))
    }
}
