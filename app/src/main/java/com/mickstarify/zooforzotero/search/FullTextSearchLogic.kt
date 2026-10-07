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
    ): List<Hit<T>> {
        val shownByKey = LinkedHashMap<String, T>()
        val matchedByKey = HashMap<String, MutableList<String>>()
        for (key in keys) {
            val hit = lookup(key) ?: continue
            val shown = parentKeyOf(hit)?.let { lookup(it) } ?: hit
            val shownKey = keyOf(shown)
            if (shownKey in alreadyShown) continue
            shownByKey.getOrPut(shownKey) { shown }
            val matched = matchedByKey.getOrPut(shownKey) { mutableListOf() }
            if (key !in matched) matched.add(key)
        }
        return shownByKey.map { (shownKey, shown) -> Hit(shown, matchedByKey[shownKey].orEmpty()) }
    }

    /** One entry for the results: what to show, and which server keys matched for it. */
    data class Hit<T>(val shown: T, val matchedKeys: List<String>)
}

/**
 * The text around a search match, ready to show under a result.
 * [before] and [after] already start/end with "…" when the text was cut there.
 */
data class TextSnippet(
    val before: String,
    val match: String,
    val after: String,
    /** Where the first match is, as a percentage of the whole text (0-100). */
    val positionPercent: Int,
    /** How many times the term appears, capped at [SnippetLogic.MAX_COUNT] + 1. */
    val matchCount: Int
)

object SnippetLogic {
    const val MAX_COUNT = 99
    const val DEFAULT_RADIUS = 70

    /**
     * Finds [query] in [text] and returns the words around the first match.
     * Tries the whole query first, then its words from longest to shortest
     * (the server matches words anywhere, not necessarily the exact phrase).
     * Case-insensitive. Returns null if nothing is found.
     */
    fun makeSnippet(text: String, query: String, radius: Int = DEFAULT_RADIUS): TextSnippet? {
        if (text.isEmpty()) return null
        val trimmed = query.trim()
        val candidates = (listOf(trimmed) + trimmed.split(Regex("\\s+")).sortedByDescending { it.length })
            .filter { it.length >= FullTextSearchLogic.MIN_QUERY_LENGTH }
            .distinct()

        for (term in candidates) {
            val first = text.indexOf(term, ignoreCase = true)
            if (first < 0) continue

            var count = 0
            var at = first
            while (at >= 0 && count <= MAX_COUNT) {
                count++
                at = text.indexOf(term, at + term.length, ignoreCase = true)
            }

            val end = first + term.length
            val startCut = (first - radius).coerceAtLeast(0)
            val endCut = (end + radius).coerceAtMost(text.length)

            var before = collapse(text.substring(startCut, first))
            var after = collapse(text.substring(end, endCut))
            // Don't start or end in the middle of a word.
            if (startCut > 0) before = "…" + before.substringAfter(' ', before).trimStart()
            if (endCut < text.length) after = after.substringBeforeLast(' ', after).trimEnd() + "…"

            return TextSnippet(
                before = before,
                match = collapse(text.substring(first, end)),
                after = after,
                positionPercent = (first.toLong() * 100 / text.length).toInt().coerceIn(0, 100),
                matchCount = count
            )
        }
        return null
    }

    /** "PDF 48% · 3 matches · " — the label shown before a PDF snippet. */
    fun pdfLabel(snippet: TextSnippet): String {
        val count = when {
            snippet.matchCount > MAX_COUNT -> " · $MAX_COUNT+ matches"
            snippet.matchCount > 1 -> " · ${snippet.matchCount} matches"
            else -> ""
        }
        return "PDF ${snippet.positionPercent}%$count · "
    }

    /** May the text of a PDF be downloaded to make a snippet? (setting: OFF / WIFI / ALL) */
    fun mayDownloadText(mode: String?, unmeteredNetwork: Boolean): Boolean = when (mode) {
        "ALL" -> true
        "WIFI" -> unmeteredNetwork
        else -> false
    }

    /** Line breaks and runs of spaces become single spaces (PDF text is full of them). */
    private fun collapse(s: String): String = s.replace(Regex("\\s+"), " ")
}
