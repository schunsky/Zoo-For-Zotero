package com.mickstarify.zooforzotero.search

/**
 * Where a search inside PDFs has got to, for the progress window.
 * Pure logic, unit-tested on the JVM.
 */
data class SearchProgress(
    val phase: Phase,
    /** PDFs whose text has been read so far (READING_TEXT only). */
    val done: Int = 0,
    /** PDFs whose text will be read in total (READING_TEXT only). */
    val total: Int = 0,
    /** PDFs whose text contains the search term; null until zotero.org has answered. */
    val pdfsWithMatches: Int? = null,
    /** Matching PDFs that won't be read: text not on the phone and no download allowed. */
    val notRead: Int = 0
) {
    enum class Phase { ASKING_SERVER, READING_TEXT }

    /** Main line of the progress window. */
    fun message(): String = when (phase) {
        Phase.ASKING_SERVER -> "Searching inside your PDFs on zotero.org…"
        Phase.READING_TEXT -> "Reading the matching PDFs… ${done.coerceIn(0, total)} of $total"
    }

    /** 0-100 for a filling bar, or null for a "busy" bar when the amount of work isn't known yet. */
    fun percent(): Int? = when {
        phase == Phase.ASKING_SERVER -> null
        total <= 0 -> null
        else -> (done.coerceIn(0, total) * 100 / total)
    }

    /** Second line: how many PDFs contain the term, once known, and how many can't be read. */
    fun matchesLine(): String? = pdfsWithMatches?.let { pdfs ->
        if (notRead > 0) "${SearchSummary.pdfCount(pdfs)} · $notRead without saved text" else SearchSummary.pdfCount(pdfs)
    }
}

object SearchSummary {
    /** "Matches in 12 PDFs", "Matches in 1 PDF", "No matches inside PDFs". */
    fun pdfCount(pdfs: Int): String = when {
        pdfs <= 0 -> "No matches inside PDFs"
        pdfs == 1 -> "Matches in 1 PDF"
        else -> "Matches in $pdfs PDFs"
    }

    /**
     * The line shown under the title after a search, e.g.
     * "23 results · matches in 12 PDFs". [pdfsWithMatches] is null when the
     * search didn't look inside PDFs (switched off, offline, or term too short).
     */
    fun resultLine(results: Int, pdfsWithMatches: Int?, canSearchInsidePdfs: Boolean = false): String {
        val resultsText = if (results == 1) "1 result" else "$results results"
        if (pdfsWithMatches == null) {
            return if (canSearchInsidePdfs) "$resultsText · press Search on the keyboard to look inside PDFs"
            else resultsText
        }
        return "$resultsText · ${pdfCount(pdfsWithMatches).replaceFirstChar { it.lowercase() }}"
    }

    /** How many of the server's matching keys are PDF files on this phone's list. */
    fun countPdfs(matchingKeys: List<String>, isPdf: (String) -> Boolean): Int =
        matchingKeys.distinct().count(isPdf)
}

/** What the library screen shows, depending on the chosen view and whether the search box is open. */
object LibraryViewMode {
    /**
     * The floating search button sits just above the "+" button. It's hidden while the
     * search box is already open, and while the "+" menu is open (its buttons fan out
     * upwards, into the same spot).
     */
    fun showSearchButton(searchBoxOpen: Boolean, plusMenuOpen: Boolean): Boolean =
        !searchBoxOpen && !plusMenuOpen

    /**
     * Where to put the search button (its top-left corner, in the screen layout's pixels)
     * so it sits directly above the "+" button, centred on it.
     *
     * [plusLeft]/[plusTop] are the "+" button's position in that layout. [menuGap] is the
     * gap the "+" menu leaves between its own buttons, measured on screen, so the search
     * button looks like part of the same column; [fallbackGap] is used if it can't be measured.
     */
    fun placeSearchButton(
        plusLeft: Float, plusTop: Float, plusWidth: Int,
        searchWidth: Int, searchHeight: Int,
        menuGap: Int?, fallbackGap: Int
    ): Pair<Float, Float> {
        val gap = menuGap?.takeIf { it >= 0 } ?: fallbackGap
        val x = plusLeft + plusWidth / 2f - searchWidth / 2f
        val y = plusTop - gap - searchHeight
        return Pair(x, y)
    }
}

/**
 * Which view the library shows: the view the user chose, plus a temporary switch that
 * shows search results as a list (where context lines and match counts fit).
 */
data class LibraryViewState(
    /** The user's chosen view, saved in the settings. */
    val galleryChosen: Boolean,
    /** Search results are being shown as a list, regardless of the chosen view. */
    val listForSearch: Boolean = false
) {
    val showsGallery: Boolean get() = galleryChosen && !listForSearch

    /** Opening the search box: results appear as a list. The chosen view is kept. */
    fun searchOpened() = copy(listForSearch = true)

    /** Closing the search box: back to the chosen view. */
    fun searchClosed() = copy(listForSearch = false)

    /**
     * The list/gallery button: always works, even while searching. It switches what's
     * shown and remembers that as the chosen view.
     */
    fun toggled() = LibraryViewState(galleryChosen = !showsGallery, listForSearch = false)
}

/** How much work runs side by side while reading matching PDFs. */
object SearchParallelism {
    /** Most PDFs read at once: a large book's text takes several MB while being read. */
    const val MAX_READING_THREADS = 4

    /** Downloads from zotero.org at once (to stay polite to the server). */
    const val DOWNLOAD_THREADS = 3

    /** Saved PDF texts read at once: one per core, leaving one free for the screen. */
    fun readingThreads(cores: Int): Int = (cores - 1).coerceIn(1, MAX_READING_THREADS)
}

/** Limits how often the progress window is redrawn while many PDFs finish quickly. */
object ProgressThrottle {
    const val INTERVAL_MS = 100L

    /** Redraw if enough time has passed, and always for the first and the last PDF. */
    fun shouldPublish(nowMs: Long, lastPublishedMs: Long?, done: Int, total: Int): Boolean =
        lastPublishedMs == null || done >= total || nowMs - lastPublishedMs >= INTERVAL_MS
}
