package dev.korbi.fjell

/**
 * Picks the stack's "relevant" row, in four tiers:
 * 1. just installed (under half a day) and not opened yet, newest first
 * 2. the most used apps (ties go to the most recently used)
 * 3. installed half a day to two days ago and still not opened, newest first
 * 4. everything else, newest install first
 */
object Relevance {
    const val JUST_INSTALLED_MS = 12 * 60 * 60 * 1000L
    const val NEW_FOR_MS = 2 * 24 * 60 * 60 * 1000L

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
        val (used, unopened) = candidates.partition { launches(it) > 0 }
        val recency = recent.withIndex().associate { (i, key) -> key to i }
        val mostUsed = used.sortedWith(compareByDescending<String>(launches).thenBy { recency[it] ?: Int.MAX_VALUE })
        val byAge = unopened.sortedByDescending(installedAt).groupBy { key ->
            val age = now - installedAt(key)
            when {
                age in 0 until JUST_INSTALLED_MS -> 0
                age in 0 until NEW_FOR_MS -> 1
                else -> 2
            }
        }
        val justInstalled = byAge[0].orEmpty()
        val recentlyInstalled = byAge[1].orEmpty()
        val rest = byAge[2].orEmpty()
        return (justInstalled + mostUsed + recentlyInstalled + rest).take(limit)
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
