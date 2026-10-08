package com.mickstarify.zooforzotero.search

import com.mickstarify.zooforzotero.search.SearchProgress.Phase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SearchProgressTest {

    @Test
    fun `asking the server shows a busy bar`() {
        val p = SearchProgress(Phase.ASKING_SERVER)
        assertNull(p.percent())
        assertEquals("Searching inside your PDFs on zotero.org…", p.message())
        assertNull(p.matchesLine())
    }

    @ParameterizedTest(name = "{0} of {1} -> {2}%")
    @CsvSource(
        "0, 22, 0",
        "7, 22, 31",    // rounds down
        "21, 22, 95",
        "22, 22, 100",
        "30, 22, 100",  // never above 100
        "-1, 22, 0",    // never below 0
    )
    fun `reading text fills the bar`(done: Int, total: Int, expected: Int) =
        assertEquals(expected, SearchProgress(Phase.READING_TEXT, done, total).percent())

    @Test
    fun `reading with nothing to read shows a busy bar, not a division by zero`() =
        assertNull(SearchProgress(Phase.READING_TEXT, 0, 0).percent())

    @Test
    fun `reading message shows the count, clamped`() {
        assertEquals(
            "Reading the matching PDFs… 7 of 22",
            SearchProgress(Phase.READING_TEXT, 7, 22).message()
        )
        assertEquals(
            "Reading the matching PDFs… 22 of 22",
            SearchProgress(Phase.READING_TEXT, 25, 22).message()
        )
    }

    @ParameterizedTest(name = "{0} PDFs -> \"{1}\"")
    @CsvSource(
        "0, No matches inside PDFs",
        "1, Matches in 1 PDF",
        "2, Matches in 2 PDFs",
        "12, Matches in 12 PDFs",
    )
    fun `match count wording`(pdfs: Int, expected: String) {
        assertEquals(expected, SearchSummary.pdfCount(pdfs))
        assertEquals(expected, SearchProgress(Phase.READING_TEXT, 0, 1, pdfs).matchesLine())
    }

    @ParameterizedTest(name = "{0} results, {1} PDFs -> \"{2}\"")
    @CsvSource(
        nullValues = ["null"],
        value = [
            "23, 12, 23 results · matches in 12 PDFs",
            "1, 1, 1 result · matches in 1 PDF",
            "5, 0, 5 results · no matches inside PDFs",
            "0, 0, 0 results · no matches inside PDFs",
            "7, null, 7 results",   // didn't look inside PDFs
            "1, null, 1 result",
        ]
    )
    fun `result line`(results: Int, pdfs: Int?, expected: String) =
        assertEquals(expected, SearchSummary.resultLine(results, pdfs))

    @Test
    fun `matches line says how many PDFs can't be read`() {
        assertEquals(
            "Matches in 300 PDFs · 12 without saved text",
            SearchProgress(Phase.READING_TEXT, 0, 288, 300, notRead = 12).matchesLine()
        )
        assertEquals("Matches in 300 PDFs", SearchProgress(Phase.READING_TEXT, 0, 300, 300, notRead = 0).matchesLine())
    }

    @Test
    fun `before pressing Search, the result line says how to look inside PDFs`() {
        assertEquals(
            "7 results · press Search on the keyboard to look inside PDFs",
            SearchSummary.resultLine(7, null, canSearchInsidePdfs = true)
        )
        assertEquals("7 results", SearchSummary.resultLine(7, null, canSearchInsidePdfs = false))
        // once the PDF search has run, the hint is replaced by the count
        assertEquals("7 results · matches in 2 PDFs", SearchSummary.resultLine(7, 2, canSearchInsidePdfs = true))
    }

    @Test
    fun `PDF count ignores non-PDF matches and duplicates`() {
        val pdfs = setOf("PDF1", "PDF2")
        assertEquals(2, SearchSummary.countPdfs(listOf("PDF1", "NOTE1", "PDF2", "PDF1", "BOOK1"), { it in pdfs }))
        assertEquals(0, SearchSummary.countPdfs(emptyList(), { true }))
    }
}
