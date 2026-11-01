package dev.korbi.fjell

import org.junit.Assert.assertEquals
import org.junit.Test

class RelevanceTest {
    private val now = 10 * Relevance.NEW_FOR_MS
    private val day = Relevance.NEW_FOR_MS / 3

    private fun pick(
        installed: Map<String, Long>,
        launches: Map<String, Int> = emptyMap(),
        recent: List<String> = emptyList(),
        limit: Int = 4,
    ) = Relevance.pick(installed.keys.toList(), { installed.getValue(it) }, { launches[it] ?: 0 }, recent, now, limit)

    @Test
    fun `new unopened apps come first, newest first`() {
        val installed = mapOf("old" to 0L, "yesterday" to now - day, "today" to now - 1000)
        assertEquals(listOf("today", "yesterday", "old"), pick(installed, launches = mapOf("old" to 5)))
    }

    @Test
    fun `an opened app is no longer new`() {
        val installed = mapOf("fresh" to now - 1000, "used" to 0L)
        assertEquals(listOf("used", "fresh"), pick(installed, launches = mapOf("fresh" to 1, "used" to 9)))
    }

    @Test
    fun `apps older than three days are not new`() {
        val installed = mapOf("week" to now - 7 * day)
        assertEquals(emptyList<String>(), pick(installed))
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
