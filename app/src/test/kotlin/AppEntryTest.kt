package dev.korbi.fjell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppEntryTest {
    private val whatsapp = "com.whatsapp/com.whatsapp.Main#0"

    @Test
    fun `matches its own package and user`() {
        assertTrue(AppEntry.isOf(whatsapp, "com.whatsapp", 0))
    }

    @Test
    fun `ignores the same app in another profile`() {
        assertFalse(AppEntry.isOf(whatsapp, "com.whatsapp", 10))
    }

    @Test
    fun `ignores packages that merely share a prefix`() {
        assertFalse(AppEntry.isOf("com.whatsapp.w4b/com.whatsapp.Main#0", "com.whatsapp", 0))
    }
}
