package com.mickstarify.zooforzotero.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SearchSpeedTest {

    @ParameterizedTest(name = "{0} cores -> {1} reading threads")
    @CsvSource(
        "1, 1",    // single core: still works, one at a time
        "2, 1",    // keep one core for the screen
        "3, 2",
        "4, 3",
        "5, 4",
        "8, 4",    // typical phone: capped for memory
        "16, 4",
        "0, 1",    // nonsense report from the system: still at least one
    )
    fun `reading threads follow the cores, within limits`(cores: Int, expected: Int) =
        assertEquals(expected, SearchParallelism.readingThreads(cores))

    @ParameterizedTest(name = "now={0} last={1} done={2}/{3} -> {4}")
    @CsvSource(
        nullValues = ["null"],
        value = [
            "1000, null, 1, 300, true",   // first update always shown
            "1050, 1000, 2, 300, false",  // 50 ms later: skipped
            "1099, 1000, 3, 300, false",  // just under the interval
            "1100, 1000, 4, 300, true",   // exactly the interval
            "1500, 1000, 9, 300, true",
            "1001, 1000, 300, 300, true", // the last PDF is always shown
            "1001, 1000, 0, 0, true",     // nothing to read: shown at once
        ]
    )
    fun `progress redraws are limited, first and last always shown`(
        now: Long, last: Long?, done: Int, total: Int, expected: Boolean
    ) = assertEquals(expected, ProgressThrottle.shouldPublish(now, last, done, total))
}
