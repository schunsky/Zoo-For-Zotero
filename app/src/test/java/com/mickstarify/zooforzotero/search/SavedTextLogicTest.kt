package com.mickstarify.zooforzotero.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SavedTextLogicTest {

    @Nested
    inner class Planning {
        private val hits = listOf("A", "B", "C", "D", "E", "F")
        private val saved = setOf("B", "E", "F")

        private fun plan(mayDownload: Boolean, limit: Int) =
            SnippetPlanning.plan(hits, { it in saved }, mayDownload, limit)

        @Test
        fun `every saved PDF is used, even beyond the download limit`() =
            assertEquals(listOf("B", "E", "F"), plan(true, 1).fromPhone)

        @Test
        fun `downloads are capped at the limit, in result order`() =
            assertEquals(listOf("A", "C"), plan(true, 2).toDownload)

        @Test
        fun `a limit above the number of unsaved PDFs downloads them all`() =
            assertEquals(listOf("A", "C", "D"), plan(true, 100).toDownload)

        @Test
        fun `no downloads when not allowed, saved text still used`() {
            val p = plan(false, 15)
            assertEquals(emptyList<String>(), p.toDownload)
            assertEquals(listOf("B", "E", "F"), p.fromPhone)
        }

        @Test
        fun `zero or negative limit downloads nothing`() {
            assertEquals(emptyList<String>(), plan(true, 0).toDownload)
            assertEquals(emptyList<String>(), plan(true, -3).toDownload)
        }

        @Test
        fun `a PDF is never both used from the phone and downloaded`() {
            val p = plan(true, 100)
            assertTrue(p.fromPhone.intersect(p.toDownload.toSet()).isEmpty())
            assertEquals(hits.toSet(), (p.fromPhone + p.toDownload).toSet())
        }
    }

    @Nested
    inner class DownloadAll {
        private val mb = 1024L * 1024

        @Test
        fun `reads keys from zotero org's full-text list`() =
            assertEquals(
                listOf("8GYXWK7S", "ABCD1234"),
                DownloadAllLogic.parseFullTextKeys("""{"8GYXWK7S": 65, "ABCD1234":12,"8GYXWK7S": 70}""")
            )

        @Test
        fun `ignores anything that isn't an item key`() =
            assertEquals(
                emptyList<String>(),
                DownloadAllLogic.parseFullTextKeys("""{"short": 1, "lowercase": 2, "TOOLONGKEY1": 3, "ABCD1234": "x"}""")
            )

        @Test
        fun `empty library list means nothing to do`() =
            assertEquals(emptyList<String>(), DownloadAllLogic.parseFullTextKeys("{}"))

        @Test
        fun `already saved text is skipped`() =
            assertEquals(listOf("A", "C"), DownloadAllLogic.keysToDownload(listOf("A", "B", "C"), { it == "B" }))

        @ParameterizedTest(name = "cancelled={0} wifi={1} usedMb={2} -> {3}")
        @CsvSource(
            "false, true, 0, NONE",
            "false, true, 94, NONE",          // below 95% of 100 MB
            "false, true, 95, SPACE_FULL",    // at 95%: leave room for normal searches
            "false, true, 200, SPACE_FULL",
            "false, false, 0, NOT_ON_WIFI",
            "false, false, 200, NOT_ON_WIFI", // Wi-Fi is checked before space
            "true, false, 200, CANCELLED",    // cancelling always wins
            "true, true, 0, CANCELLED",
        )
        fun `decides when to stop`(cancelled: Boolean, onWifi: Boolean, usedMb: Int, expected: String) =
            assertEquals(
                DownloadAllLogic.Stop.valueOf(expected),
                DownloadAllLogic.stopReason(cancelled, onWifi, usedMb * mb, 100 * mb)
            )

        @ParameterizedTest(name = "{0} bytes -> {1}")
        @CsvSource("0, 0 MB", "524287, 0 MB", "524288, 1 MB", "1048576, 1 MB", "157286400, 150 MB")
        fun `sizes round to whole megabytes`(bytes: Long, expected: String) =
            assertEquals(expected, DownloadAllLogic.megabytes(bytes))

        @Test
        fun `finished message mentions failures and how to keep text`() {
            val ok = DownloadAllLogic.finished(480, 0, 210 * mb, 0)
            assertEquals("Done: text of all 480 PDFs saved (210 MB).", ok)

            val withFailures = DownloadAllLogic.finished(480, 3, 210 * mb, 0)
            assertTrue(withFailures.startsWith("Done: 477 of 480 PDFs saved"))
            assertTrue(withFailures.contains("3 could not be downloaded"))

            val withExpiry = DownloadAllLogic.finished(480, 0, 210 * mb, 30)
            assertTrue(withExpiry.contains("not used for 30 days"))
            assertFalse(ok.contains("Never"))
        }

        @Test
        fun `each stop reason explains what to do next`() {
            fun msg(r: DownloadAllLogic.Stop) = DownloadAllLogic.stopped(r, 120, 480, 100 * mb)
            assertTrue(msg(DownloadAllLogic.Stop.NOT_ON_WIFI).contains("not on Wi-Fi"))
            assertTrue(msg(DownloadAllLogic.Stop.SPACE_FULL).contains("Raise the limit"))
            assertTrue(msg(DownloadAllLogic.Stop.SPACE_FULL).contains("100 MB"))
            assertTrue(msg(DownloadAllLogic.Stop.CANCELLED).contains("120 of 480"))
        }
    }
}
