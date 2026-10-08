package com.mickstarify.zooforzotero.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PdfMatchesTest {

    // A small library: two books with PDFs, one with two PDFs, a standalone PDF, a note.
    private val parentOf = mapOf(
        "PDFTITLE" to "BOOKTITL",  // book already found by its title
        "PDFOTHER" to "BOOKOTHR",
        "PDFPART1" to "BOOKTWO1",  // a book split into two PDFs
        "PDFPART2" to "BOOKTWO1",
    )
    private val standalone = setOf("SOLOPDF1")
    private val pdfs = parentOf.keys + standalone
    private val onPhone = pdfs + parentOf.values + setOf("NOTE0001")

    private fun rowKeyOf(key: String): String? =
        if (key !in onPhone) null else parentOf[key] ?: key

    private fun matches(vararg keys: String) =
        FullTextSearchLogic.pdfMatches(keys.toList(), { it in pdfs }, ::rowKeyOf)

    @Test
    fun `regression - a PDF whose item was already found by title is still read`() {
        // The bug: "Matches in 300 PDFs" but "of 299" read, because PDFs of items the
        // title search had already listed were skipped. They must be included.
        assertEquals(listOf("BOOKTITL" to "PDFTITLE"), matches("PDFTITLE"))
    }

    @Test
    fun `every matching PDF is read once, so the counts agree`() {
        val m = matches("PDFTITLE", "PDFOTHER", "PDFPART1", "PDFPART2", "SOLOPDF1", "PDFOTHER")
        assertEquals(5, m.size)
        assertEquals(m.size, SearchSummary.countPdfs(
            listOf("PDFTITLE", "PDFOTHER", "PDFPART1", "PDFPART2", "SOLOPDF1", "PDFOTHER"), { it in pdfs }
        ))
    }

    @Test
    fun `both PDFs of a split book are read, under the same row`() =
        assertEquals(listOf("BOOKTWO1" to "PDFPART1", "BOOKTWO1" to "PDFPART2"), matches("PDFPART1", "PDFPART2"))

    @Test
    fun `a standalone PDF is its own row`() =
        assertEquals(listOf("SOLOPDF1" to "SOLOPDF1"), matches("SOLOPDF1"))

    @Test
    fun `notes, parent items and unknown keys are not PDFs to read`() =
        assertEquals(emptyList<Pair<String, String>>(), matches("NOTE0001", "BOOKOTHR", "UNKNOWN1"))

    // ---- combining two PDFs of the same item ----

    private fun snippet(match: String, count: Int) = TextSnippet("…before ", match, " after…", count)

    @Test
    fun `counts of an item's PDFs add up, context from the PDF with more matches`() {
        val combined = SnippetLogic.combineForItem(snippet("one", 3), snippet("two", 10))
        assertEquals(13, combined.matchCount)
        assertEquals("two", combined.match)
    }

    @Test
    fun `on a tie the first PDF's context is kept`() =
        assertEquals("one", SnippetLogic.combineForItem(snippet("one", 4), snippet("two", 4)).match)

    @Test
    fun `the first PDF of an item is used as it is`() =
        assertEquals(snippet("one", 3), SnippetLogic.combineForItem(null, snippet("one", 3)))

    @Test
    fun `combined counts stay within the cap`() =
        assertEquals(
            SnippetLogic.MAX_COUNT + 1,
            SnippetLogic.combineForItem(snippet("a", 900), snippet("b", 900)).matchCount
        )
}
