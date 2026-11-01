package dev.korbi.fjell

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class AutoSceneTest {
    private fun at(month: Int, hour: Int) = Scene.automatic(LocalDateTime.of(2026, month, 15, hour, 0))

    @Test
    fun `winter is snow all day`() {
        assertEquals(Scene.Snow, at(12, 12))
        assertEquals(Scene.Snow, at(1, 3))
        assertEquals(Scene.Snow, at(2, 20))
    }

    @Test
    fun `otherwise mountain by day and pole at night`() {
        assertEquals(Scene.Mountain, at(6, 7))
        assertEquals(Scene.Mountain, at(6, 17))
        assertEquals(Scene.Pole, at(6, 18))
        assertEquals(Scene.Pole, at(10, 2))
    }

    @Test
    fun `the chosen scene wins while automatic is off`() {
        val noon = LocalDateTime.of(2026, 1, 15, 12, 0)
        assertEquals(Scene.Pole, LauncherSettings(scene = Scene.Pole).sceneAt(noon))
        assertEquals(Scene.Snow, LauncherSettings(scene = Scene.Pole, autoScene = true).sceneAt(noon))
    }
}
