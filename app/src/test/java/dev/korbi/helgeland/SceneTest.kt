package dev.korbi.helgeland

import org.junit.Assert.assertTrue
import org.junit.Test

class SceneTest {
    @Test
    fun `media line sits above the grid and never overlaps it`() {
        Scene.entries.forEach { scene ->
            assertTrue("${scene.name}: media band is empty", scene.mediaTop < scene.mediaBottom)
            assertTrue("${scene.name}: media reaches into the grid", scene.mediaBottom <= scene.gridTop)
        }
    }

    @Test
    fun `grid leaves room for three rows`() {
        // Below ~18% of the screen height the icons would get too small to hit.
        Scene.entries.forEach { scene ->
            assertTrue("${scene.name}: grid area too small", 1f - scene.gridTop >= 0.18f)
        }
    }
}
