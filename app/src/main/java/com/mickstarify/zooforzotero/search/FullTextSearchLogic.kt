package com.mickstarify.zooforzotero.search

/**
 * Pure logic for "search inside PDFs" (full-text search on zotero.org).
 * Free of Android classes so it can be unit-tested on a plain JVM.
 */
object FullTextSearchLogic {

    /** Shortest query worth sending to the server; shorter ones match almost everything. */
    const val MIN_QUERY_LENGTH = 3

    /** Should this query also be searched inside PDFs? */
    fun shouldSearch(query: String, enabled: Boolean): Boolean {
        if (!enabled) return false
        val trimmed = query.trim()
        if (trimmed.startsWith("tag:")) return false   // tag browsing, not a text search
        return trimmed.length >= MIN_QUERY_LENGTH
    }

    /** Parses the server's `format=keys` answer: one item key per line. */
    fun parseKeys(body: String): List<String> =
        body.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.distinct().toList()

    /**
     * Turns the server's matching keys into the entries to add to the results.
     *
     * - A match inside a PDF (or a child note) is shown as its parent item, which
     *   carries the cover and PDF button; a standalone PDF is shown as itself.
     * - Keys this phone doesn't know yet (not synced) are skipped.
     * - Entries already shown by the normal search ([alreadyShown]) aren't repeated,
     *   and no entry appears twice. The server's order is kept.
     */
    fun <T> resolveHits(
        keys: List<String>,
        alreadyShown: Set<String>,
        lookup: (String) -> T?,
        keyOf: (T) -> String,
        parentKeyOf: (T) -> String?
    ): List<T> {
        val result = LinkedHashMap<String, T>()
        for (key in keys) {
            val hit = lookup(key) ?: continue
            val shown = parentKeyOf(hit)?.let { lookup(it) } ?: hit
            val shownKey = keyOf(shown)
            if (shownKey !in alreadyShown && shownKey !in result) {
                result[shownKey] = shown
            }
        }
        return result.values.toList()
    }
}
