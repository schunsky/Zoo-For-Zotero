package com.mickstarify.zooforzotero.thumbnails

import com.mickstarify.zooforzotero.search.LastRun
import com.mickstarify.zooforzotero.thumbnails.CoverDownloadLogic.Result
import com.mickstarify.zooforzotero.thumbnails.CoverDownloadLogic.Stop
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class CoverDownloadLogicTest {

    @ParameterizedTest(name = "cancelled={0} onPhone={1} wifi={2} -> {3}")
    @CsvSource(
        "false, true,  false, NONE",        // PDFs on the phone: no network needed
        "false, true,  true,  NONE",
        "false, false, true,  NONE",        // cloud folder on Wi-Fi: fine
        "false, false, false, NOT_ON_WIFI", // cloud folder on mobile data: pause
        "true,  false, false, CANCELLED",   // cancelling always wins
        "true,  true,  true,  CANCELLED",
    )
    fun `decides when to stop`(cancelled: Boolean, onPhone: Boolean, wifi: Boolean, expected: String) =
        assertEquals(Stop.valueOf(expected), CoverDownloadLogic.stopReason(cancelled, onPhone, wifi))

    @Test
    fun `everything done`() =
        assertEquals("Done: all 883 PDFs have covers (12 new).",
            CoverDownloadLogic.finished(mapOf(Result.MADE to 12, Result.ALREADY_MADE to 871)))

    @Test
    fun `some PDFs not here or unreadable`() =
        assertEquals(
            "Done: covers for 850 of 883 PDFs (850 new). 30 PDFs are not on this phone or in the " +
                "attachment folder. 3 PDFs couldn't be read (protected or damaged).",
            CoverDownloadLogic.finished(mapOf(Result.MADE to 850, Result.NOT_ON_PHONE to 30, Result.UNREADABLE to 3))
        )

    @Test
    fun `singular wording`() {
        val msg = CoverDownloadLogic.finished(mapOf(Result.MADE to 5, Result.NOT_ON_PHONE to 1, Result.UNREADABLE to 1))
        assertTrue(msg.contains("1 PDF is not on this phone"), msg)
        assertTrue(msg.contains("1 PDF couldn't be read"), msg)
    }

    @Test
    fun `empty library`() =
        assertEquals("Done: there are no PDFs in your library.", CoverDownloadLogic.finished(emptyMap()))

    @Test
    fun `stop messages say how to continue`() {
        assertTrue(CoverDownloadLogic.stopped(Stop.CANCELLED, 120, 883).contains("Tap to continue"))
        assertTrue(CoverDownloadLogic.stopped(Stop.NOT_ON_WIFI, 120, 883).contains("not on Wi-Fi"))
        assertTrue(CoverDownloadLogic.stopped(Stop.NOT_ON_WIFI, 120, 883).contains("120 of 883"))
    }

    @Test
    fun `kept result shows when it ran`() =
        assertEquals(
            "Last run 8 Oct 2026, 14:05 · Done: text of all 883 PDFs saved (85 MB).",
            LastRun.describe("Done: text of all 883 PDFs saved (85 MB).", "8 Oct 2026, 14:05")
        )
}
