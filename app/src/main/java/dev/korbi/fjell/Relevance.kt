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
