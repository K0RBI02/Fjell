package dev.korbi.fjell

/**
 * Pure helpers for the home grid, a row-major list of slot values: an app key,
 * [STAPEL_KEY], [SEARCH_KEY] followed by [COVERED] for each extra slot the search
 * bar spans, or null for an empty slot.
 */
object GridSlots {
    const val STAPEL_KEY = "@stapel"
    const val SEARCH_KEY = "@search"

    /** Marks a slot taken up by the search bar to its left. */
    const val COVERED = "@covered"

    /** Where the search bar starts and how many slots it spans, or null if it isn't placed. */
    fun search(slots: List<String?>): IntRange? {
        val start = slots.indexOf(SEARCH_KEY).takeIf { it >= 0 } ?: return null
        var end = start
        while (end + 1 < slots.size && slots[end + 1] == COVERED) end++
        return start..end
    }

    fun withoutSearch(slots: List<String?>): List<String?> = slots.map { if (it == SEARCH_KEY || it == COVERED) null else it }

    /** Puts a search bar of [span] slots into the first row with room, top row first. Null if no row has room. */
    fun placeSearch(slots: List<String?>, span: Int, columns: Int): List<String?>? {
        val base = withoutSearch(slots)
        for (start in base.indices) {
            if (start % columns + span > columns) continue
            if ((start until start + span).all { base[it] == null }) return base.fill(start, span)
        }
        return null
    }

    /** Widens or narrows the bar in place, keeping its start if it fits there. Null if there's no room. */
    fun resizeSearch(slots: List<String?>, span: Int, columns: Int): List<String?>? {
        val current = search(slots) ?: return null
        val base = withoutSearch(slots)
        val rowStart = current.first - current.first % columns
        // Prefer the current start, then any start in the same row, then anywhere.
        val starts = listOf(current.first) + (rowStart until rowStart + columns)
        for (start in starts) {
            if (start % columns + span > columns) continue
            if ((start until start + span).all { base[it] == null }) return base.fill(start, span)
        }
        return placeSearch(base, span, columns)
    }

    /**
     * Moves whatever sits at [from] so it starts at [to]. Single items swap with the
     * target; the search bar swaps its whole span with the slots it lands on.
     * Dropping a single item onto the search bar does nothing.
     */
    fun move(slots: List<String?>, from: Int, to: Int, columns: Int): List<String?> {
        val bar = search(slots)
        if (bar != null && from == bar.first) {
            val span = bar.count()
            val row = to / columns
            val start = minOf(to, row * columns + columns - span)
            val target = start until start + span
            val result = slots.toMutableList()
            val vacated = bar.filter { it !in target }
            val displaced = target.filter { it !in bar }.map { slots[it] }
            vacated.forEachIndexed { i, slot -> result[slot] = displaced[i] }
            target.forEachIndexed { i, slot -> result[slot] = if (i == 0) SEARCH_KEY else COVERED }
            return result
        }
        if (bar != null && to in bar) return slots
        val result = slots.toMutableList()
        result[from] = slots[to]
        result[to] = slots[from]
        return result
    }

    /**
     * How many rows (in halves) to move the grid down so its occupied rows sit centred,
     * ignoring fully empty rows at the top and bottom. Negative moves it up.
     */
    fun centeringShift(occupied: List<Boolean>, columns: Int): Float {
        val rows = occupied.size / columns
        val used = (0 until rows).filter { row -> (0 until columns).any { occupied[row * columns + it] } }
        if (used.isEmpty()) return 0f
        return (rows - 1 - used.last() - used.first()) / 2f
    }

    /** How many rows lie between the first and last occupied row, inclusive; 0 for an empty grid. */
    fun usedRowSpan(occupied: List<Boolean>, columns: Int): Int {
        val rows = occupied.size / columns
        val used = (0 until rows).filter { row -> (0 until columns).any { occupied[row * columns + it] } }
        return if (used.isEmpty()) 0 else used.last() - used.first() + 1
    }

    private fun List<String?>.fill(start: Int, span: Int): List<String?> = toMutableList().also {
        it[start] = SEARCH_KEY
        for (i in start + 1 until start + span) it[i] = COVERED
    }
}
