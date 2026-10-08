package com.mickstarify.zooforzotero.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SearchHistoryTest {

    @Nested
    inner class Adding {
        @Test
        fun `newest search goes on top`() =
            assertEquals(listOf("kotlin", "python"), SearchHistory.add(listOf("python"), "kotlin"))

        @Test
        fun `repeating a search moves it to the top instead of duplicating it`() =
            assertEquals(
                listOf("python", "kotlin", "rust"),
                SearchHistory.add(listOf("kotlin", "python", "rust"), "python")
            )

        @Test
        fun `different capitals and spaces count as the same search, newest spelling kept`() =
            assertEquals(
                listOf("Test Driven", "rust"),
                SearchHistory.add(listOf("test   driven", "rust"), "  Test Driven ")
            )

        @ParameterizedTest(name = "\"{0}\" is not remembered")
        @CsvSource("''", "'   '", "tag:python", "'  tag:python'")
        fun `blank searches and tag browsing are not remembered`(query: String) =
            assertEquals(listOf("rust"), SearchHistory.add(listOf("rust"), query))

        @Test
        fun `only the newest are kept`() {
            val full = (1..SearchHistory.MAX_ENTRIES).map { "q$it" }
            val after = SearchHistory.add(full, "new")
            assertEquals(SearchHistory.MAX_ENTRIES, after.size)
            assertEquals("new", after.first())
            assertEquals("q${SearchHistory.MAX_ENTRIES - 1}", after.last())   // the oldest dropped
        }

        @Test
        fun `line breaks never get into the history`() =
            assertEquals(listOf("two lines"), SearchHistory.add(emptyList(), "two\nlines"))
    }

    @Nested
    inner class Suggesting {
        private val history = listOf("design patterns", "python testing", "Test driven", "pytest", "rust")

        @Test
        fun `nothing typed shows the most recent`() =
            assertEquals(listOf("design patterns", "python testing", "Test driven"),
                SearchHistory.suggestions(history, "", limit = 3))

        @Test
        fun `starts-with matches come before contains matches, newest first in each`() =
            assertEquals(
                listOf("Test driven", "python testing", "pytest"),
                SearchHistory.suggestions(history, "test")
            )

        @Test
        fun `typing is matched ignoring capitals and extra spaces`() =
            assertEquals(listOf("Test driven"), SearchHistory.suggestions(history, "  TEST  dr"))

        @Test
        fun `what is already typed exactly is not offered again`() =
            assertEquals(emptyList<String>(), SearchHistory.suggestions(listOf("rust"), "Rust"))

        @Test
        fun `no match gives nothing`() =
            assertEquals(emptyList<String>(), SearchHistory.suggestions(history, "haskell"))

        @Test
        fun `empty history gives nothing`() =
            assertEquals(emptyList<String>(), SearchHistory.suggestions(emptyList(), ""))

        @Test
        fun `suggestions are capped`() =
            assertEquals(SearchHistory.MAX_SUGGESTIONS,
                SearchHistory.suggestions((1..30).map { "q$it" }, "").size)
    }

    @Nested
    inner class Storing {
        @Test
        fun `round trip keeps order and text`() {
            val h = listOf("design patterns", "C++ & Rust: 2nd ed.", "über")
            assertEquals(h, SearchHistory.decode(SearchHistory.encode(h)))
        }

        @Test
        fun `nothing stored means empty history`() {
            assertEquals(emptyList<String>(), SearchHistory.decode(null))
            assertEquals(emptyList<String>(), SearchHistory.decode(""))
        }

        @Test
        fun `stray blank lines are ignored`() =
            assertEquals(listOf("a", "b"), SearchHistory.decode("a\n\n  \nb\n"))

        @ParameterizedTest(name = "{0} -> \"{1}\"")
        @CsvSource("0, No searches saved", "1, 1 search saved", "12, 12 searches saved")
        fun `count label`(size: Int, expected: String) =
            assertEquals(expected, SearchHistory.countLabel(size))
    }
}
