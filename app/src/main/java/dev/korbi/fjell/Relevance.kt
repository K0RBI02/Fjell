package dev.korbi.fjell

/**
 * Picks the stack's "relevant" row: apps installed recently and never opened come
 * first (newest first), then the most used ones (ties go to the most recently used).
 */
object Relevance {
    const val NEW_FOR_MS = 3 * 24 * 60 * 60 * 1000L

    /**
     * [installedAt] and [launches] are looked up by key; [recent] lists keys, most
     * recently launched first. Returns at most [limit] of [candidates].
     */
    fun pick(
        candidates: List<String>,
        installedAt: (String) -> Long,
        launches: (String) -> Int,
        recent: List<String>,
        now: Long,
        limit: Int,
    ): List<String> {
        val fresh = candidates
            .filter { launches(it) == 0 && now - installedAt(it) in 0 until NEW_FOR_MS }
            .sortedByDescending(installedAt)
        val recency = recent.withIndex().associate { (i, key) -> key to i }
        val used = candidates
            .filter { launches(it) > 0 }
            .sortedWith(compareByDescending<String>(launches).thenBy { recency[it] ?: Int.MAX_VALUE })
        return (fresh + used).take(limit)
    }
}

/** Orders search hits: names starting with the query, then words starting with it, then the rest. */
object SearchRank {
    /** 0 = the name starts with [query], 1 = a word in it does, 2 = it's somewhere inside, null = no match. */
    fun tier(label: String, query: String): Int? {
        if (query.isEmpty()) return null
        var best: Int? = null
        var from = 0
        while (true) {
            val i = label.indexOf(query, from, ignoreCase = true)
            if (i < 0) return best
            val tier = when {
                i == 0 -> 0
                isWordStart(label, i) -> 1
                else -> 2
            }
            if (tier == 0) return 0
            best = minOf(best ?: tier, tier)
            from = i + 1
        }
    }

    /** After a space or symbol, or a capital inside a word ("Media|Markt"). */
    private fun isWordStart(label: String, i: Int): Boolean {
        val before = label[i - 1]
        return !before.isLetterOrDigit() || (label[i].isUpperCase() && before.isLowerCase())
    }
}
