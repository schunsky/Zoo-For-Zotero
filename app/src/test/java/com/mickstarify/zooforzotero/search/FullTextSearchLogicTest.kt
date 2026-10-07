package com.mickstarify.zooforzotero.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class FullTextSearchLogicTest {

    @Nested
    inner class ShouldSearch {
        @ParameterizedTest(name = "\"{0}\" enabled={1} -> {2}")
        @CsvSource(
            "compiler, true, true",
            "abc, true, true",          // exactly the minimum length
            "ab, true, false",          // one below the minimum
            "'  ab  ', true, false",    // spaces don't count
            "'', true, false",
            "compiler, false, false",   // setting off
            "tag:python, true, false",  // tag browsing is not a text search
        )
        fun `decides when to search inside PDFs`(query: String, enabled: Boolean, expected: Boolean) {
            assertEquals(expected, FullTextSearchLogic.shouldSearch(query, enabled))
        }
    }

    @Nested
    inner class ParseKeys {
        @Test
        fun `one key per line, blanks and duplicates removed`() {
            assertEquals(
                listOf("8GYXWK7S", "ABCD1234"),
                FullTextSearchLogic.parseKeys("8GYXWK7S\n\n ABCD1234 \r\n8GYXWK7S\n")
            )
        }

        @Test
        fun `empty answer means no matches`() {
            assertEquals(emptyList<String>(), FullTextSearchLogic.parseKeys(""))
        }
    }

    @Nested
    inner class ResolveHits {
        private inner class Fake(val key: String, val parent: String? = null)

        private val library = listOf(
            Fake("BOOK0001"),                       // parent item
            Fake("PDF00001", parent = "BOOK0001"),  // its PDF
            Fake("NOTE0001", parent = "BOOK0001"),  // its note
            Fake("SOLOPDF1"),                       // standalone PDF
            Fake("ORPHAN01", parent = "GONE0001"),  // parent not on this phone
        ).associateBy { it.key }

        private fun resolve(keys: List<String>, shown: Set<String> = emptySet()) =
            FullTextSearchLogic.resolveHits(
                keys, shown,
                lookup = { library[it] }, keyOf = { it.key }, parentKeyOf = { it.parent }
            ).map { it.key }

        @Test
        fun `match inside a PDF shows its parent item`() =
            assertEquals(listOf("BOOK0001"), resolve(listOf("PDF00001")))

        @Test
        fun `standalone PDF shows itself`() =
            assertEquals(listOf("SOLOPDF1"), resolve(listOf("SOLOPDF1")))

        @Test
        fun `PDF and note of the same item appear once`() =
            assertEquals(listOf("BOOK0001"), resolve(listOf("PDF00001", "NOTE0001", "BOOK0001")))

        @Test
        fun `items already found by the normal search are not repeated`() =
            assertEquals(listOf("SOLOPDF1"), resolve(listOf("PDF00001", "SOLOPDF1"), shown = setOf("BOOK0001")))

        @Test
        fun `keys not synced to this phone are skipped`() =
            assertEquals(listOf("SOLOPDF1"), resolve(listOf("NEWKEY99", "SOLOPDF1")))

        @Test
        fun `PDF whose parent is missing shows the PDF itself`() =
            assertEquals(listOf("ORPHAN01"), resolve(listOf("ORPHAN01")))

        @Test
        fun `server order is kept`() =
            assertEquals(listOf("SOLOPDF1", "BOOK0001"), resolve(listOf("SOLOPDF1", "PDF00001")))
    }
}
