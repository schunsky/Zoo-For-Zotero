package com.mickstarify.zooforzotero.thumbnails

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class CoverPolicyTest {

    // Decision table: every combination of folder location x setting x network.
    @ParameterizedTest(name = "onDevice={0}, mode={1}, unmetered={2} -> {3}")
    @CsvSource(
        nullValues = ["null"],
        value = [
            // Phone storage: always allowed, whatever the setting or network
            "true,  OPENED, false, true",
            "true,  WIFI,   false, true",
            "true,  ALL,    false, true",
            "true,  null,   false, true",
            // Cloud folder
            "false, OPENED, true,  false",
            "false, OPENED, false, false",
            "false, WIFI,   true,  true",
            "false, WIFI,   false, false",
            "false, ALL,    true,  true",
            "false, ALL,    false, true",
            // Missing or unknown setting falls back to the safe choice
            "false, null,   true,  false",
            "false, BOGUS,  true,  false",
        ]
    )
    fun `decides when browsing may make a cover`(
        onDevice: Boolean, mode: String?, unmetered: Boolean, expected: Boolean
    ) {
        assertEquals(expected, CoverPolicy.mayRenderWhileBrowsing(onDevice, mode, unmetered))
    }
}
