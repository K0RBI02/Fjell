package dev.korbi.fjell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorTest {
    private fun calc(input: String) = Calculator.evaluate(input)?.let(Calculator::format)

    @Test
    fun `basic arithmetic`() {
        assertEquals("48", calc("12*4"))
        assertEquals("7", calc("3 + 4"))
        assertEquals("2.5", calc("10/4"))
        assertEquals("-1", calc("3-4"))
    }

    @Test
    fun `precedence and parentheses`() {
        assertEquals("14", calc("2+3*4"))
        assertEquals("20", calc("(2+3)*4"))
        assertEquals("512", calc("2^3^2"))
    }

    @Test
    fun `typed symbols and decimal commas`() {
        assertEquals("12", calc("3×4"))
        assertEquals("2", calc("8÷4"))
        assertEquals("3.5", calc("1,5+2"))
        assertEquals("1", calc("3−2"))
    }

    @Test
    fun `percent divides by a hundred`() {
        assertEquals("25", calc("50%*50"))
    }

    @Test
    fun `long fractions are rounded`() {
        assertEquals("0.3333333333", calc("1/3"))
    }

    @Test
    fun `not a calculation`() {
        assertNull(calc("spotify"))
        assertNull(calc("42"))
        assertNull(calc("2+"))
        assertNull(calc("(1+2"))
        assertNull(calc("1/0"))
        assertNull(calc("+"))
    }
}
