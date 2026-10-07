package com.mickstarify.zooforzotero.thumbnails

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ThumbnailSizingTest {

    // ---- fitInBox: equivalence partitions + boundaries ----

    @ParameterizedTest(name = "{0}x{1} in {2}x{3} -> {4}x{5}")
    @CsvSource(
        // portrait A4 (595x842 pt) is limited by height
        "595, 842, 96, 96, 68, 96",
        // landscape slide is limited by width
        "842, 595, 96, 96, 96, 68",
        // square page fills the box
        "100, 100, 96, 96, 96, 96",
        // tiny page is scaled UP to the box
        "10, 20, 96, 96, 48, 96",
        // extreme ratio never collapses to 0 px
        "1, 100000, 96, 96, 1, 96",
        // smallest valid box
        "595, 842, 1, 1, 1, 1",
    )
    fun `fits page inside box keeping aspect ratio`(
        pageW: Int, pageH: Int, boxW: Int, boxH: Int, expectedW: Int, expectedH: Int
    ) {
        assertEquals(Pair(expectedW, expectedH), ThumbnailSizing.fitInBox(pageW, pageH, boxW, boxH))
    }

    @ParameterizedTest(name = "invalid page {0}x{1} gets whole box")
    @CsvSource("0, 842", "595, 0", "-1, 842", "0, 0")
    fun `invalid page size falls back to the box`(pageW: Int, pageH: Int) {
        assertEquals(Pair(96, 96), ThumbnailSizing.fitInBox(pageW, pageH, 96, 96))
    }

    @ParameterizedTest(name = "box {0}x{1} is rejected")
    @CsvSource("0, 96", "96, 0", "-5, 96")
    fun `non-positive box is rejected`(boxW: Int, boxH: Int) {
        assertThrows<IllegalArgumentException> { ThumbnailSizing.fitInBox(595, 842, boxW, boxH) }
    }

    // ---- cacheKey ----

    @Test
    fun `key without md5 is the uppercased item key`() {
        assertEquals("ABCD1234", ThumbnailSizing.cacheKey("abcd1234", null))
        assertEquals("ABCD1234", ThumbnailSizing.cacheKey("abcd1234", ""))
    }

    @Test
    fun `key includes first 12 md5 characters so replaced PDFs get a new cover`() {
        assertEquals(
            "ABCD1234_9e107d9d372b",
            ThumbnailSizing.cacheKey("ABCD1234", "9E107D9D372BB6826BD81D3542A419D6")
        )
    }

    @Test
    fun `unsafe characters are stripped so the key is a safe file name`() {
        assertEquals("ABCD_ef", ThumbnailSizing.cacheKey("../ab/cd", "e/f"))
    }

    @Test
    fun `key with no usable characters is rejected`() {
        assertThrows<IllegalArgumentException> { ThumbnailSizing.cacheKey("../", null) }
    }
}
