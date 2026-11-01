package dev.korbi.fjell

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoSceneTest {
    private fun at(month: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, month, 15, hour, minute)
    private val auto = LauncherSettings(autoScene = true)

    @Test
    fun `winter is snow all day`() {
        assertEquals("winter", auto.sceneAt(at(12, 12)))
        assertEquals("winter", auto.sceneAt(at(1, 3)))
        assertEquals("winter", auto.sceneAt(at(2, 20)))
    }

    @Test
    fun `by default fjord by day and dusk at night`() {
        assertEquals("fjord", auto.sceneAt(at(6, 7)))
        assertEquals("fjord", auto.sceneAt(at(6, 17, 59)))
        assertEquals("dusk", auto.sceneAt(at(6, 18)))
        assertEquals("dusk", auto.sceneAt(at(10, 2)))
    }

    @Test
    fun `a custom schedule wraps past midnight`() {
        val schedule = listOf(SceneSlot(22 * 60, "dusk"), SceneSlot(6 * 60 + 30, "winter"), SceneSlot(12 * 60, "fjord"))
        val settings = auto.copy(schedule = schedule, snowInWinter = false)
        assertEquals("dusk", settings.sceneAt(at(1, 3)))
        assertEquals("winter", settings.sceneAt(at(1, 6, 30)))
        assertEquals("fjord", settings.sceneAt(at(1, 21, 59)))
        assertEquals("dusk", settings.sceneAt(at(1, 23)))
    }

    @Test
    fun `an empty schedule keeps the chosen scene`() {
        assertEquals("dusk", auto.copy(scene = "dusk", schedule = emptyList()).sceneAt(at(6, 12)))
    }

    @Test
    fun `the chosen scene wins while automatic is off`() {
        assertEquals("dusk", LauncherSettings(scene = "dusk").sceneAt(at(1, 12)))
        assertEquals("winter", LauncherSettings(scene = "dusk", autoScene = true).sceneAt(at(1, 12)))
    }

    @Test
    fun `schedule survives a save round trip and skips junk`() {
        val slots = listOf(SceneSlot(0, "winter"), SceneSlot(1439, "dusk"))
        assertEquals(slots, SceneSchedule.decode(SceneSchedule.encode(slots)))
        assertEquals(listOf(SceneSlot(60, "dusk")), SceneSchedule.decode("60:dusk,abc,2000:winter"))
    }

    @Test
    fun `new slots land on a free hour`() {
        assertEquals(19 * 60, SceneSchedule.nextFreeMinute(SceneSchedule.DEFAULT))
        assertEquals(12 * 60, SceneSchedule.nextFreeMinute(emptyList()))
        assertEquals(60, SceneSchedule.nextFreeMinute(listOf(SceneSlot(23 * 60, "dusk"), SceneSlot(0, "winter"))))
    }

    @Test
    fun `sun slots follow the day's sunrise and sunset`() {
        val schedule = listOf(SceneSlot(7 * 60, "fjord", SunEvent.Sunrise), SceneSlot(18 * 60, "dusk", SunEvent.Sunset))
        val settings = auto.copy(schedule = schedule, snowInWinter = false)
        val winterSun = SunTimes(sunrise = 8 * 60, sunset = 16 * 60 + 30)
        assertEquals("dusk", settings.sceneAt(at(11, 7, 30), winterSun))
        assertEquals("fjord", settings.sceneAt(at(11, 12), winterSun))
        assertEquals("dusk", settings.sceneAt(at(11, 17), winterSun))
        // Unknown sun (no location yet): the stand-in times apply.
        assertEquals("fjord", settings.sceneAt(at(11, 17)))
    }

    @Test
    fun `sun slots survive a save round trip`() {
        val slots = listOf(SceneSlot(420, "fjord", SunEvent.Sunrise), SceneSlot(1200, "dusk"))
        assertEquals(slots, SceneSchedule.decode(SceneSchedule.encode(slots)))
        val custom = listOf(SceneSlot(400, "custom:abc-1", SunEvent.Sunset), SceneSlot(500, "custom:abc-2"))
        assertEquals(custom, SceneSchedule.decode(SceneSchedule.encode(custom)))
    }
}
