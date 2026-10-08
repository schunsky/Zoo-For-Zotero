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
    /** How many times the term appears, capped at [SnippetLogic.MAX_COUNT] + 1. */
    val matchCount: Int
)

object SnippetLogic {
    const val MAX_COUNT = 999
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
                matchCount = count
            )
        }
        return null
    }

    /** "PDF · 3 matches · " — the label shown before a PDF snippet. */
    fun pdfLabel(snippet: TextSnippet): String {
        val count = when {
            snippet.matchCount > MAX_COUNT -> " · $MAX_COUNT+ matches"
            snippet.matchCount > 1 -> " · ${snippet.matchCount} matches"
            else -> ""
        }
        return "PDF$count · "
    }

    /**
     * Orders [keys] by their match count, most first. Equal counts keep their
     * original order; keys with no known count (e.g. beyond the per-search limit,
     * or text not available) go last, also in their original order.
     */
    fun sortByMatchCount(keys: List<String>, counts: Map<String, Int>): List<String> =
        keys.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<String>> { counts[it.value] ?: -1 }
                    .thenBy { it.index }
            )
            .map { it.value }

    /** May the text of a PDF be downloaded to make a snippet? (setting: OFF / WIFI / ALL) */
    fun mayDownloadText(mode: String?, unmeteredNetwork: Boolean): Boolean = when (mode) {
        "ALL" -> true
        "WIFI" -> unmeteredNetwork
        else -> false
    }

    /** Line breaks and runs of spaces become single spaces (PDF text is full of them). */
    private fun collapse(s: String): String = s.replace(Regex("\\s+"), " ")
}

/**
 * The user's search settings, read from their stored text values.
 * Anything missing or unreadable falls back to the default.
 */
object SearchSettings {
    const val DEFAULT_CACHE_DAYS = 30
    const val DEFAULT_CACHE_MB = 100
    const val DEFAULT_SNIPPETS_PER_SEARCH = 15

    /** Days to keep downloaded PDF text; 0 means keep until the size limit removes it. */
    fun cacheDays(stored: String?): Int = parseInRange(stored, 0..3650, DEFAULT_CACHE_DAYS)

    /** Most space downloaded PDF text may use, in MB. */
    fun cacheMegabytes(stored: String?): Int = parseInRange(stored, 1..10_000, DEFAULT_CACHE_MB)

    /** How many PDFs per search may get a context line. */
    fun snippetsPerSearch(stored: String?): Int = parseInRange(stored, 1..1000, DEFAULT_SNIPPETS_PER_SEARCH)

    private fun parseInRange(stored: String?, range: IntRange, default: Int): Int {
        val value = stored?.trim()?.toIntOrNull() ?: return default
        return if (value in range) value else default
    }
}

/** One file in the PDF-text cache, as seen by [CacheEviction]. */
data class CacheEntry(val name: String, val sizeBytes: Long, val modifiedMs: Long)

object CacheEviction {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * Which cache files to delete: first everything older than [maxAgeDays]
     * (0 = no age limit), then the oldest remaining ones until the total is
     * within [maxBytes].
     */
    fun filesToDelete(entries: List<CacheEntry>, nowMs: Long, maxAgeDays: Int, maxBytes: Long): Set<String> {
        val delete = LinkedHashSet<String>()
        val kept = ArrayList<CacheEntry>()
        for (entry in entries.sortedBy { it.modifiedMs }) {
            val tooOld = maxAgeDays > 0 && nowMs - entry.modifiedMs > maxAgeDays * DAY_MS
            if (tooOld) delete.add(entry.name) else kept.add(entry)
        }
        var total = kept.sumOf { it.sizeBytes }
        for (entry in kept) {
            if (total <= maxBytes) break
            total -= entry.sizeBytes
            delete.add(entry.name)
        }
        return delete
    }

    fun isExpired(modifiedMs: Long, nowMs: Long, maxAgeDays: Int): Boolean =
        maxAgeDays > 0 && nowMs - modifiedMs > maxAgeDays * DAY_MS
}

/** Which PDF results get a context line and match count during one search. */
data class SnippetPlan<T>(
    /** Text already saved on the phone: always used, no download needed. */
    val fromPhone: List<T>,
    /** Text to download now: at most the per-search download limit. */
    val toDownload: List<T>
)

object SnippetPlanning {
    /**
     * Every result whose text is saved on the phone is used. Of the others, the
     * first [downloadLimit] (in result order) are downloaded, if downloads are
     * allowed right now. The rest get no context line this time.
     */
    fun <T> plan(hits: List<T>, isSaved: (T) -> Boolean, mayDownload: Boolean, downloadLimit: Int): SnippetPlan<T> {
        val (saved, notSaved) = hits.partition(isSaved)
        val download = if (mayDownload) notSaved.take(downloadLimit.coerceAtLeast(0)) else emptyList()
        return SnippetPlan(saved, download)
    }
}

/** Rules and messages for "Download text of all PDFs". */
object DownloadAllLogic {
    enum class Stop { NONE, CANCELLED, NOT_ON_WIFI, SPACE_FULL }

    /** Keep some room below the limit so text saved during normal searches still fits. */
    const val HEADROOM_PERCENT = 5

    /**
     * Item keys from zotero.org's full-text list (`{"ABCD1234": 512, ...}`).
     * Only 8-character Zotero keys are accepted.
     */
    fun parseFullTextKeys(json: String): List<String> =
        Regex("\"([A-Z0-9]{8})\"\\s*:\\s*\\d+").findAll(json).map { it.groupValues[1] }.distinct().toList()

    fun keysToDownload(allKeys: List<String>, isSaved: (String) -> Boolean): List<String> =
        allKeys.filterNot(isSaved)

    /** Checked before every download. Cancelling wins, then Wi-Fi, then space. */
    fun stopReason(cancelled: Boolean, onWifi: Boolean, usedBytes: Long, maxBytes: Long): Stop = when {
        cancelled -> Stop.CANCELLED
        !onWifi -> Stop.NOT_ON_WIFI
        usedBytes >= maxBytes * (100 - HEADROOM_PERCENT) / 100 -> Stop.SPACE_FULL
        else -> Stop.NONE
    }

    fun megabytes(bytes: Long): String = "${(bytes + 512 * 1024) / (1024 * 1024)} MB"

    // ---- Messages shown under the button ----

    const val IDLE = "Saves the text of every PDF on this phone (Wi-Fi only), so every search " +
        "result gets its context line and match count. Tap to start."

    fun checking() = "Checking which PDFs have text on zotero.org…"

    fun progress(saved: Int, total: Int, usedBytes: Long) =
        "Downloading… $saved of $total PDFs saved (${megabytes(usedBytes)}). Tap to stop."

    fun stopped(reason: Stop, saved: Int, total: Int, maxBytes: Long): String = when (reason) {
        Stop.CANCELLED -> "Stopped. $saved of $total PDFs saved. Tap to continue."
        Stop.NOT_ON_WIFI -> "Paused: not on Wi-Fi. $saved of $total PDFs saved. Tap to continue on Wi-Fi."
        Stop.SPACE_FULL -> "Stopped: 'Space for saved text' (${megabytes(maxBytes)}) is full. " +
            "$saved of $total PDFs saved. Raise the limit, then tap to continue."
        Stop.NONE -> "$saved of $total PDFs saved."
    }

    fun finished(total: Int, failed: Int, usedBytes: Long, forgetAfterDays: Int): String {
        val base = if (failed == 0) {
            "Done: text of all $total PDFs saved (${megabytes(usedBytes)})."
        } else {
            "Done: ${total - failed} of $total PDFs saved (${megabytes(usedBytes)}); " +
                "$failed could not be downloaded. Tap to retry them."
        }
        val tip = if (forgetAfterDays > 0) {
            " Text not used for $forgetAfterDays days is removed; choose 'Never' under " +
                "'Remove saved text not used for' to keep it all."
        } else ""
        return base + tip
    }

    fun failedToStart(reason: String) = "Couldn't reach zotero.org ($reason). Tap to try again."

    const val NOT_LOGGED_IN = "Log in to your Zotero account first."
}
