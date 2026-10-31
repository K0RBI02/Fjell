package dev.korbi.helgeland

import org.junit.Assert.assertEquals
import org.junit.Test

class SectionLetterTest {
    @Test
    fun `plain letters are upper-cased`() {
        assertEquals("W", sectionLetter("whatsapp"))
    }

    @Test
    fun `umlauts sort under their base letter`() {
        assertEquals("A", sectionLetter("Ärzte"))
        assertEquals("O", sectionLetter("Öffi"))
        assertEquals("U", sectionLetter("Übersetzer"))
    }

    @Test
    fun `digits and symbols go under the hash`() {
        assertEquals("#", sectionLetter("2 Player Games"))
        assertEquals("#", sectionLetter("@home"))
    }

    @Test
    fun `an empty label doesn't crash`() {
        assertEquals("#", sectionLetter(""))
    }
}
