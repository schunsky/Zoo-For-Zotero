package com.mickstarify.zooforzotero.search

/**
 * Recent searches, newest first. Pure logic (stored as text by the caller),
 * unit-tested on the JVM.
 */
object SearchHistory {
    const val MAX_ENTRIES = 20
    const val MAX_SUGGESTIONS = 8

    /**
     * Adds [query] at the top. Blank queries and tag browsing aren't remembered.
     * The same search typed with different capitals or spaces is kept once, at the top,
     * in its newest spelling. Only the newest [max] searches are kept.
     */
    fun add(history: List<String>, query: String, max: Int = MAX_ENTRIES): List<String> {
        val cleaned = clean(query)
        if (cleaned.isEmpty() || cleaned.startsWith("tag:")) return history
        val key = normalise(cleaned)
        return (listOf(cleaned) + history.filterNot { normalise(it) == key }).take(max)
    }

    /**
     * What to offer under the search box for what's typed so far: with nothing typed,
     * the most recent searches; otherwise past searches that start with the typed text,
     * then those that merely contain it, newest first within each group. The exact text
     * already typed isn't offered again.
     */
    fun suggestions(history: List<String>, typed: String, limit: Int = MAX_SUGGESTIONS): List<String> {
        val t = normalise(typed)
        if (t.isEmpty()) return history.take(limit)
        val candidates = history.filter { normalise(it) != t }
        val starts = candidates.filter { normalise(it).startsWith(t) }
        val contains = candidates.filter { !normalise(it).startsWith(t) && normalise(it).contains(t) }
        return (starts + contains).take(limit)
    }

    /** Stored as one search per line. */
    fun encode(history: List<String>): String = history.joinToString("\n") { clean(it) }

    fun decode(stored: String?): List<String> =
        stored.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_ENTRIES)

    /** "No searches saved", "1 search saved", "12 searches saved". */
    fun countLabel(size: Int): String = when {
        size <= 0 -> "No searches saved"
        size == 1 -> "1 search saved"
        else -> "$size searches saved"
    }

    /** One line, single spaces, trimmed. */
    private fun clean(s: String) = s.replace(Regex("\\s+"), " ").trim()

    private fun normalise(s: String) = clean(s).lowercase()
}
