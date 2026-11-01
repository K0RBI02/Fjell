package dev.korbi.fjell

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoSceneTest {
    private fun at(month: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, month, 15, hour, minute)
    private val auto = LauncherSettings(autoScene = true)

    @Test
    fun `winter is snow all day`() {
        assertEquals(Scene.Snow, auto.sceneAt(at(12, 12)))
        assertEquals(Scene.Snow, auto.sceneAt(at(1, 3)))
        assertEquals(Scene.Snow, auto.sceneAt(at(2, 20)))
    }

    @Test
    fun `by default mountain by day and pole at night`() {
        assertEquals(Scene.Mountain, auto.sceneAt(at(6, 7)))
        assertEquals(Scene.Mountain, auto.sceneAt(at(6, 17, 59)))
        assertEquals(Scene.Pole, auto.sceneAt(at(6, 18)))
        assertEquals(Scene.Pole, auto.sceneAt(at(10, 2)))
    }

    @Test
    fun `a custom schedule wraps past midnight`() {
        val schedule = listOf(SceneSlot(22 * 60, Scene.Pole), SceneSlot(6 * 60 + 30, Scene.Snow), SceneSlot(12 * 60, Scene.Mountain))
        val settings = auto.copy(schedule = schedule, snowInWinter = false)
        assertEquals(Scene.Pole, settings.sceneAt(at(1, 3)))
        assertEquals(Scene.Snow, settings.sceneAt(at(1, 6, 30)))
        assertEquals(Scene.Mountain, settings.sceneAt(at(1, 21, 59)))
        assertEquals(Scene.Pole, settings.sceneAt(at(1, 23)))
    }

    @Test
    fun `an empty schedule keeps the chosen scene`() {
        assertEquals(Scene.Pole, auto.copy(scene = Scene.Pole, schedule = emptyList()).sceneAt(at(6, 12)))
    }

    @Test
    fun `the chosen scene wins while automatic is off`() {
        assertEquals(Scene.Pole, LauncherSettings(scene = Scene.Pole).sceneAt(at(1, 12)))
        assertEquals(Scene.Snow, LauncherSettings(scene = Scene.Pole, autoScene = true).sceneAt(at(1, 12)))
    }

    @Test
    fun `schedule survives a save round trip and skips junk`() {
        val slots = listOf(SceneSlot(0, Scene.Snow), SceneSlot(1439, Scene.Pole))
        assertEquals(slots, SceneSchedule.decode(SceneSchedule.encode(slots)))
        assertEquals(listOf(SceneSlot(60, Scene.Pole)), SceneSchedule.decode("60:Pole,99:Barn,abc,2000:Snow"))
    }

    @Test
    fun `new slots land on a free hour`() {
        assertEquals(19 * 60, SceneSchedule.nextFreeMinute(SceneSchedule.DEFAULT))
        assertEquals(12 * 60, SceneSchedule.nextFreeMinute(emptyList()))
        assertEquals(60, SceneSchedule.nextFreeMinute(listOf(SceneSlot(23 * 60, Scene.Pole), SceneSlot(0, Scene.Snow))))
    }

    @Test
    fun `sun slots follow the day's sunrise and sunset`() {
        val schedule = listOf(SceneSlot(7 * 60, Scene.Mountain, SunEvent.Sunrise), SceneSlot(18 * 60, Scene.Pole, SunEvent.Sunset))
        val settings = auto.copy(schedule = schedule, snowInWinter = false)
        val winterSun = SunTimes(sunrise = 8 * 60, sunset = 16 * 60 + 30)
        assertEquals(Scene.Pole, settings.sceneAt(at(11, 7, 30), winterSun))
        assertEquals(Scene.Mountain, settings.sceneAt(at(11, 12), winterSun))
        assertEquals(Scene.Pole, settings.sceneAt(at(11, 17), winterSun))
        // Unknown sun (no location yet): the stand-in times apply.
        assertEquals(Scene.Mountain, settings.sceneAt(at(11, 17)))
    }

    @Test
    fun `sun slots survive a save round trip`() {
        val slots = listOf(SceneSlot(420, Scene.Mountain, SunEvent.Sunrise), SceneSlot(1200, Scene.Pole))
        assertEquals(slots, SceneSchedule.decode(SceneSchedule.encode(slots)))
        assertEquals(emptyList<SceneSlot>(), SceneSchedule.decode("420:Pole:Noon"))
    }
}
