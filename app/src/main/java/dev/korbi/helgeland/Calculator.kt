package dev.korbi.helgeland

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.pow

/**
 * Evaluates simple arithmetic typed into the stack search: + − × ÷ (also * and /),
 * ^ for powers, % as "divide by 100", parentheses, and decimal points or commas.
 */
object Calculator {
    /** The result, or null if [input] isn't a calculation (plain numbers don't count). */
    fun evaluate(input: String): Double? {
        val text = input.replace(" ", "")
        if (text.none { it in OPERATORS } || text.none(Char::isDigit)) return null
        return runCatching { Parser(text).parse() }.getOrNull()?.takeIf { it.isFinite() }
    }

    /** "48", "0.3333333333", "1.5E+20": at most 10 significant digits, no trailing zeros. */
    fun format(value: Double): String =
        BigDecimal(value).round(MathContext(10)).stripTrailingZeros().let {
            if (it.abs() < BigDecimal("1E15")) it.toPlainString() else it.toString()
        }

    private const val OPERATORS = "+-−*×/÷^%"

    /** Recursive descent: sum → product → power → unary → atom. */
    private class Parser(private val s: String) {
        private var pos = 0

        fun parse(): Double = sum().also { require(pos == s.length) }

        private fun sum(): Double {
            var value = product()
            while (true) {
                value = when (peek()) {
                    '+' -> { pos++; value + product() }
                    '-', '−' -> { pos++; value - product() }
                    else -> return value
                }
            }
        }

        private fun product(): Double {
            var value = power()
            while (true) {
                value = when (peek()) {
                    '*', '×' -> { pos++; value * power() }
                    '/', '÷' -> { pos++; value / power() }
                    else -> return value
                }
            }
        }

        private fun power(): Double {
            val base = unary()
            if (peek() == '^') {
                pos++
                return base.pow(power()) // right-associative
            }
            return base
        }

        private fun unary(): Double = when (peek()) {
            '-', '−' -> { pos++; -unary() }
            '+' -> { pos++; unary() }
            else -> percent(atom())
        }

        private fun percent(value: Double): Double {
            var v = value
            while (peek() == '%') {
                pos++
                v /= 100
            }
            return v
        }

        private fun atom(): Double {
            if (peek() == '(') {
                pos++
                val value = sum()
                require(peek() == ')')
                pos++
                return value
            }
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.' || s[pos] == ',')) pos++
            require(pos > start)
            return s.substring(start, pos).replace(',', '.').toDouble()
        }

        private fun peek(): Char? = s.getOrNull(pos)
    }
}
