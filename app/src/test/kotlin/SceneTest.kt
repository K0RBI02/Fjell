package dev.korbi.fjell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneTest {
    @Test
    fun `media line sits above the grid and never overlaps it`() {
        Scene.BUILT_IN.forEach { scene ->
            assertTrue("${scene.id}: media band is empty", scene.mediaTop < scene.mediaBottom)
            assertTrue("${scene.id}: media reaches into the grid", scene.mediaBottom <= scene.gridTop)
        }
    }

    @Test
    fun `grid leaves room for three rows`() {
        // Below ~18% of the screen height the icons would get too small to hit.
        Scene.BUILT_IN.forEach { scene ->
            assertTrue("${scene.id}: grid area too small", 1f - scene.gridTop >= 0.18f)
        }
    }

    @Test
    fun `custom scenes survive a save round trip`() {
        val scene = Scene("custom:1", 0.7f, 0.657f, 0.7f, androidx.compose.ui.graphics.Color.White, androidx.compose.ui.graphics.Color(0xFF2E3550), true, name = "Fjell", file = "1.jpg", width = 1080, height = 2400)
        assertEquals(listOf(scene), Scene.listFromJson(Scene.listToJson(listOf(scene))))
    }

    @Test
    fun `a missing scene falls back to the first`() {
        assertEquals(Scene.DEFAULT_ID, Scene.BUILT_IN.resolve("custom:gone").id)
    }
}
