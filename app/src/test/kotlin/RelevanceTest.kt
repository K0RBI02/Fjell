package dev.korbi.fjell

import org.junit.Assert.assertEquals
import org.junit.Test

class RelevanceTest {
    private val hour = 60 * 60 * 1000L
    private val now = 1000 * hour

    private fun pick(
        installed: Map<String, Long>,
        launches: Map<String, Int> = emptyMap(),
        recent: List<String> = emptyList(),
        limit: Int = 8,
    ) = Relevance.pick(installed.keys.toList(), { installed.getValue(it) }, { launches[it] ?: 0 }, recent, now, limit)

    @Test
    fun `just installed, then most used, then recently installed, then the rest`() {
        val installed = mapOf(
            "old" to 0L,
            "used" to 0L,
            "yesterday" to now - 30 * hour,
            "anHourAgo" to now - hour,
            "lastWeek" to now - 7 * 24 * hour,
        )
        assertEquals(
            listOf("anHourAgo", "used", "yesterday", "lastWeek", "old"),
            pick(installed, launches = mapOf("used" to 5)),
        )
    }

    @Test
    fun `half a day old is no longer just installed`() {
        val installed = mapOf("thisMorning" to now - 13 * hour, "used" to 0L)
        assertEquals(listOf("used", "thisMorning"), pick(installed, launches = mapOf("used" to 1)))
    }

    @Test
    fun `an opened app ranks by use, not by age`() {
        val installed = mapOf("fresh" to now - hour, "used" to 0L)
        assertEquals(listOf("used", "fresh"), pick(installed, launches = mapOf("fresh" to 1, "used" to 9)))
    }

    @Test
    fun `most used first, ties go to the most recent`() {
        val installed = mapOf("a" to 0L, "b" to 0L, "c" to 0L)
        val launches = mapOf("a" to 3, "b" to 3, "c" to 7)
        assertEquals(listOf("c", "b", "a"), pick(installed, launches, recent = listOf("b", "a")))
    }

    @Test
    fun `never more than the limit`() {
        val installed = (1..9).associate { "app$it" to 0L }
        val launches = (1..9).associate { "app$it" to it }
        assertEquals(listOf("app9", "app8"), pick(installed, launches, limit = 2))
    }

    @Test
    fun `search ranks name starts, then word starts, then the rest`() {
        assertEquals(0, SearchRank.tier("Maps", "ma"))
        assertEquals(1, SearchRank.tier("Proton Mail", "ma"))
        assertEquals(1, SearchRank.tier("MediaMarkt", "ma"))
        assertEquals(2, SearchRank.tier("Amazon Shopping", "ma"))
        assertEquals(null, SearchRank.tier("Discord", "ma"))
    }

    @Test
    fun `a later word start beats an earlier inner match`() {
        assertEquals(1, SearchRank.tier("Gmail Mail", "mai"))
    }
}
