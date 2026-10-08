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
    val pdfsWithMatches: Int? = null
) {
    enum class Phase { ASKING_SERVER, READING_TEXT }

    /** Main line of the progress window. */
    fun message(): String = when (phase) {
        Phase.ASKING_SERVER -> "Searching inside your PDFs on zotero.org…"
        Phase.READING_TEXT -> "Finding the text around each match… ${done.coerceIn(0, total)} of $total PDFs"
    }

    /** 0-100 for a filling bar, or null for a "busy" bar when the amount of work isn't known yet. */
    fun percent(): Int? = when {
        phase == Phase.ASKING_SERVER -> null
        total <= 0 -> null
        else -> (done.coerceIn(0, total) * 100 / total)
    }

    /** Second line: how many PDFs contain the term, once known. */
    fun matchesLine(): String? = pdfsWithMatches?.let { SearchSummary.pdfCount(it) }
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
    fun resultLine(results: Int, pdfsWithMatches: Int?): String {
        val resultsText = if (results == 1) "1 result" else "$results results"
        if (pdfsWithMatches == null) return resultsText
        return "$resultsText · ${pdfCount(pdfsWithMatches).replaceFirstChar { it.lowercase() }}"
    }

    /** How many of the server's matching keys are PDF files on this phone's list. */
    fun countPdfs(matchingKeys: List<String>, isPdf: (String) -> Boolean): Int =
        matchingKeys.distinct().count(isPdf)
}

/** What the library screen shows, depending on the chosen view and whether the search box is open. */
object LibraryViewMode {
    /** Search results always use the list: context lines and match counts only fit there. */
    fun showGallery(galleryChosen: Boolean, searchBoxOpen: Boolean): Boolean =
        galleryChosen && !searchBoxOpen

    /** The list/gallery switch is hidden while searching (it couldn't take effect anyway). */
    fun showViewSwitch(searchBoxOpen: Boolean): Boolean = !searchBoxOpen

    /** The floating search button is hidden while the search box is already open. */
    fun showSearchButton(searchBoxOpen: Boolean): Boolean = !searchBoxOpen
}
