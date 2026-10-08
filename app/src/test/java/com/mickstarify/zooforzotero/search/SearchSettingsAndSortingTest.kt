package com.mickstarify.zooforzotero.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SearchSettingsAndSortingTest {

    @Nested
    inner class SortByMatchCount {
        private fun sort(keys: String, counts: Map<String, Int>) =
            SnippetLogic.sortByMatchCount(keys.split(","), counts).joinToString(",")

        @Test
        fun `most matches first`() =
            assertEquals("B,C,A", sort("A,B,C", mapOf("A" to 1, "B" to 40, "C" to 3)))

        @Test
        fun `equal counts keep their original order`() =
            assertEquals("A,C,B", sort("A,B,C", mapOf("A" to 5, "B" to 2, "C" to 5)))

        @Test
        fun `results without a count go last, in original order`() =
            assertEquals("C,A,B,D", sort("A,B,C,D", mapOf("C" to 1)))

        @Test
        fun `a single match still ranks above an unknown count`() =
            assertEquals("B,A", sort("A,B", mapOf("B" to 1)))

        @Test
        fun `nothing counted leaves the order unchanged`() =
            assertEquals("A,B,C", sort("A,B,C", emptyMap()))

        @Test
        fun `empty list stays empty`() =
            assertEquals(emptyList<String>(), SnippetLogic.sortByMatchCount(emptyList(), mapOf("A" to 1)))
    }

    @Nested
    inner class Settings {
        @ParameterizedTest(name = "days \"{0}\" -> {1}")
        @CsvSource(
            nullValues = ["null"],
            value = ["7, 7", "0, 0", "3650, 3650", "3651, 30", "-1, 30", "abc, 30", "'', 30", "null, 30", "' 90 ', 90"]
        )
        fun `cache days`(stored: String?, expected: Int) =
            assertEquals(expected, SearchSettings.cacheDays(stored))

        @ParameterizedTest(name = "MB \"{0}\" -> {1}")
        @CsvSource(nullValues = ["null"], value = ["250, 250", "1, 1", "0, 100", "10000, 10000", "10001, 100", "null, 100"])
        fun `cache size`(stored: String?, expected: Int) =
            assertEquals(expected, SearchSettings.cacheMegabytes(stored))

        @ParameterizedTest(name = "per search \"{0}\" -> {1}")
        @CsvSource(nullValues = ["null"], value = ["5, 5", "1, 1", "0, 15", "1000, 1000", "1001, 15", "x, 15", "null, 15"])
        fun `PDFs per search`(stored: String?, expected: Int) =
            assertEquals(expected, SearchSettings.snippetsPerSearch(stored))
    }

    @Nested
    inner class Eviction {
        private val day = 24L * 60 * 60 * 1000
        private val now = 100 * day
        private val mb = 1024L * 1024

        private fun entry(name: String, sizeMb: Long, ageDays: Long) =
            CacheEntry(name, sizeMb * mb, now - ageDays * day)

        @Test
        fun `files older than the limit are deleted`() {
            val entries = listOf(entry("old", 1, 31), entry("new", 1, 29))
            assertEquals(setOf("old"), CacheEviction.filesToDelete(entries, now, 30, 100 * mb))
        }

        @Test
        fun `exactly at the age limit is kept`() {
            val entries = listOf(CacheEntry("edge", mb, now - 30 * day))
            assertEquals(emptySet<String>(), CacheEviction.filesToDelete(entries, now, 30, 100 * mb))
        }

        @Test
        fun `age limit 0 keeps old files`() {
            val entries = listOf(entry("ancient", 1, 3000))
            assertEquals(emptySet<String>(), CacheEviction.filesToDelete(entries, now, 0, 100 * mb))
        }

        @Test
        fun `oldest files go first when over the size limit`() {
            val entries = listOf(entry("c", 4, 1), entry("a", 4, 3), entry("b", 4, 2))
            // 12 MB total, limit 8 MB: removing the oldest (a) is enough
            assertEquals(setOf("a"), CacheEviction.filesToDelete(entries, now, 30, 8 * mb))
        }

        @Test
        fun `exactly at the size limit deletes nothing`() {
            val entries = listOf(entry("a", 4, 1), entry("b", 4, 2))
            assertEquals(emptySet<String>(), CacheEviction.filesToDelete(entries, now, 30, 8 * mb))
        }

        @Test
        fun `expired files count toward freeing space`() {
            val entries = listOf(entry("expired", 50, 40), entry("a", 4, 2), entry("b", 4, 1))
            assertEquals(setOf("expired"), CacheEviction.filesToDelete(entries, now, 30, 8 * mb))
        }

        @Test
        fun `a lowered size limit removes several files`() {
            val entries = listOf(entry("a", 4, 3), entry("b", 4, 2), entry("c", 4, 1))
            assertEquals(setOf("a", "b"), CacheEviction.filesToDelete(entries, now, 30, 5 * mb))
        }

        @Test
        fun `expiry check`() {
            assertTrue(CacheEviction.isExpired(now - 31 * day, now, 30))
            assertFalse(CacheEviction.isExpired(now - 29 * day, now, 30))
            assertFalse(CacheEviction.isExpired(0, now, 0))
        }
    }
}
