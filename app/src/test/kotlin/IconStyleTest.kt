package dev.korbi.fjell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconStyleTest {
    private val styles = listOf(
        IconStyle.Original,
        IconStyle.Grayscale,
        IconStyle.Mono,
        IconStyle.Custom,
        IconStyle.Pack("com.nothing.icon.free"),
        IconStyle.PackIcon("com.nothing.icon.free", "google_home"),
    )

    @Test
    fun `every style survives a save and load`() {
        styles.forEach { assertEquals(it, IconStyle.parse(it.id)) }
    }

    @Test
    fun `nothing stored means no style`() {
        assertNull(IconStyle.parse(null))
    }

    @Test
    fun `early builds stored the bare pack name`() {
        assertEquals(IconStyle.Pack("com.nothing.icon.free"), IconStyle.parse("com.nothing.icon.free"))
    }

    @Test
    fun `a broken pack icon entry is ignored`() {
        assertNull(IconStyle.parse("packicon:com.nothing.icon.free"))
    }
}
