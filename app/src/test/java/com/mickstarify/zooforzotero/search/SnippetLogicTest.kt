package com.mickstarify.zooforzotero.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SnippetLogicTest {

    private val text = "Intro words here. The hidden mysteries of template\nmetaprogramming are   revealed. " +
        "More mysteries later. End."

    private fun snippet(t: String, q: String, radius: Int = 20) =
        assertNotNull(SnippetLogic.makeSnippet(t, q, radius))!!

    @Test
    fun `finds the match case-insensitively and keeps the text's own case`() {
        assertEquals("mysteries", snippet(text, "MYSTERIES").match)
    }

    @Test
    fun `counts every occurrence`() {
        assertEquals(2, snippet(text, "mysteries").matchCount)
    }

    @Test
    fun `cut text starts and ends with an ellipsis, never mid-word`() {
        val s = snippet(text, "mysteries")
        assertEquals("…here. The hidden ", s.before)
        assertEquals(" of template…", s.after)
    }

    @Test
    fun `line breaks and repeated spaces are collapsed`() {
        val s = snippet(text, "metaprogramming", radius = 30)
        assertFalse(s.before.contains("\n") || s.after.contains("  "))
    }

    @Test
    fun `position is the share of text before the match`() {
        assertEquals(text.indexOf("mysteries") * 100 / text.length, snippet(text, "mysteries").positionPercent)
    }

    @Test
    fun `match at the very start has no leading ellipsis`() {
        val s = snippet("mysteries at the very start of a long sentence here", "mysteries", 10)
        assertEquals("", s.before)
        assertEquals(0, s.positionPercent)
    }

    @Test
    fun `match at the very end has no trailing ellipsis`() {
        assertEquals("", snippet("a sentence that ends with mysteries", "mysteries", 10).after)
    }

    @Test
    fun `whole phrase is preferred when present`() {
        assertEquals("hidden mysteries", snippet(text, "hidden mysteries").match)
    }

    @Test
    fun `when the phrase is absent the longest word is used`() {
        assertEquals("metaprogramming", snippet(text, "secret metaprogramming").match)
    }

    @ParameterizedTest(name = "\"{1}\" in \"{0}\" -> nothing")
    @CsvSource(
        "'$TEXT', zebra",   // not in the text
        "'', abc",          // empty text
        "'$TEXT', of",      // too short to search
    )
    fun `returns nothing when there is nothing to show`(t: String, q: String) {
        assertNull(SnippetLogic.makeSnippet(t, q, 10))
    }

    @Test
    fun `count stops just above the cap`() {
        assertEquals(SnippetLogic.MAX_COUNT + 1, snippet("abc ".repeat(500), "abc", 10).matchCount)
    }

    @Test
    fun `labels show position and count`() {
        val s = snippet(text, "mysteries")
        assertEquals("PDF 48% · ", SnippetLogic.pdfLabel(s.copy(positionPercent = 48, matchCount = 1)))
        assertEquals("PDF 48% · 3 matches · ", SnippetLogic.pdfLabel(s.copy(positionPercent = 48, matchCount = 3)))
        assertEquals("PDF 0% · 99+ matches · ", SnippetLogic.pdfLabel(s.copy(positionPercent = 0, matchCount = 100)))
    }

    @ParameterizedTest(name = "mode={0}, unmetered={1} -> {2}")
    @CsvSource(
        nullValues = ["null"],
        value = [
            "ALL, false, true",
            "ALL, true, true",
            "WIFI, true, true",
            "WIFI, false, false",
            "OFF, true, false",
            "null, true, false",
        ]
    )
    fun `text download follows the setting and network`(mode: String?, unmetered: Boolean, expected: Boolean) {
        assertEquals(expected, SnippetLogic.mayDownloadText(mode, unmetered))
    }

    @Test
    fun `no word of a multi-word query found means no snippet`() {
        assertTrue(SnippetLogic.makeSnippet(text, "zebra giraffe") == null)
    }

    companion object {
        const val TEXT = "Intro words here. The hidden mysteries of template metaprogramming."
    }
}
