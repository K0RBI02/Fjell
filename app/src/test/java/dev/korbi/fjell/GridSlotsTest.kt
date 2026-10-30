package dev.korbi.fjell

import dev.korbi.fjell.GridSlots.COVERED
import dev.korbi.fjell.GridSlots.SEARCH_KEY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GridSlotsTest {
    private val columns = 4

    private fun grid(vararg slots: String?) = slots.toList()

    @Test
    fun `places search in the first row with two free neighbours`() {
        val slots = grid("a", null, "b", null, null, null, "c", "d")
        assertEquals(grid("a", null, "b", null, SEARCH_KEY, COVERED, "c", "d"), GridSlots.placeSearch(slots, 2, columns))
    }

    @Test
    fun `search never wraps across rows`() {
        val slots = grid("a", "b", "c", null, null, "d", "e", "f")
        assertNull(GridSlots.placeSearch(slots, 2, columns))
    }

    @Test
    fun `finds the span of a placed search bar`() {
        assertEquals(1..3, GridSlots.search(grid("a", SEARCH_KEY, COVERED, COVERED)))
    }

    @Test
    fun `moving the bar swaps its span with the apps it lands on`() {
        val slots = grid(SEARCH_KEY, COVERED, "a", "b", "c", "d", "e", "f")
        assertEquals(grid("c", "d", "a", "b", SEARCH_KEY, COVERED, "e", "f"), GridSlots.move(slots, 0, 4, columns))
    }

    @Test
    fun `moving the bar one slot over shifts the displaced app back`() {
        val slots = grid(SEARCH_KEY, COVERED, "a", "b")
        assertEquals(grid("a", SEARCH_KEY, COVERED, "b"), GridSlots.move(slots, 0, 1, columns))
    }

    @Test
    fun `a bar dropped near the row end is pulled back so it fits`() {
        val slots = grid(SEARCH_KEY, COVERED, "a", "b")
        assertEquals(grid("a", "b", SEARCH_KEY, COVERED), GridSlots.move(slots, 0, 3, columns))
    }

    @Test
    fun `apps dropped on the bar stay put`() {
        val slots = grid("a", SEARCH_KEY, COVERED, "b")
        assertEquals(slots, GridSlots.move(slots, 0, 2, columns))
    }

    @Test
    fun `apps swap as before`() {
        assertEquals(grid("b", "a"), GridSlots.move(grid("a", "b"), 0, 1, columns))
    }

    @Test
    fun `widening keeps the start when the row has room`() {
        val slots = grid(SEARCH_KEY, COVERED, null, null)
        assertEquals(grid(SEARCH_KEY, COVERED, COVERED, COVERED), GridSlots.resizeSearch(slots, 4, columns))
    }

    @Test
    fun `widening fails when no row has room`() {
        val slots = grid(SEARCH_KEY, COVERED, "a", null, "b", null, "c", null)
        assertNull(GridSlots.resizeSearch(slots, 4, columns))
    }
}
