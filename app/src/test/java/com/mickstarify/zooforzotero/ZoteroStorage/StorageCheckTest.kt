package com.mickstarify.zooforzotero.ZoteroStorage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class StorageCheckTest {

    // Decision table. Columns: custom folder, folder chosen, permission granted,
    // can read, can write -> expected status, must reconfigure?
    @ParameterizedTest(name = "custom={0} chosen={1} granted={2} read={3} write={4} -> {5}")
    @CsvSource(
        // Not using a custom folder: nothing to check, whatever else is true
        "false, false, false, false, false, OK, false",
        "false, true,  true,  true,  true,  OK, false",
        // Custom folder fully usable
        "true,  true,  true,  true,  true,  OK, false",
        // Readable only (linked PDFs still open): keep the setting
        "true,  true,  true,  true,  false, READ_ONLY, false",
        // The pCloud case: permission intact, folder didn't answer: keep the setting
        "true,  true,  true,  false, false, UNAVAILABLE, false",
        "true,  true,  true,  false, true,  UNAVAILABLE, false",
        // Permission really gone: reconfigure, even if a stale read seemed to work
        "true,  true,  false, false, false, PERMISSION_LOST, true",
        "true,  true,  false, true,  true,  PERMISSION_LOST, true",
        // No folder ever chosen
        "true,  false, false, false, false, NOT_SET, true",
        "true,  false, true,  true,  true,  NOT_SET, true",
    )
    fun `classifies the attachments folder`(
        custom: Boolean, chosen: Boolean, granted: Boolean, read: Boolean, write: Boolean,
        expected: String, reconfigure: Boolean
    ) {
        val status = StorageCheck.classify(custom, chosen, granted, read, write)
        assertEquals(StorageStatus.valueOf(expected), status)
        assertEquals(reconfigure, status.needsReconfiguring)
    }
}
