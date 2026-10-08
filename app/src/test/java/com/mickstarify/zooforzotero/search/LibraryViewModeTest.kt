package com.mickstarify.zooforzotero.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class LibraryViewModeTest {

    // Decision table: chosen view x search box state.
    @ParameterizedTest(name = "gallery chosen={0}, search open={1} -> gallery={2}, switch={3}, button={4}")
    @CsvSource(
        "true,  false, true,  true,  true",   // browsing in gallery
        "true,  true,  false, false, false",  // searching from gallery: list for results
        "false, false, false, true,  true",   // browsing in list
        "false, true,  false, false, false",  // searching from list
    )
    fun `screen layout follows the search box`(
        galleryChosen: Boolean, searchOpen: Boolean,
        gallery: Boolean, viewSwitch: Boolean, searchButton: Boolean
    ) {
        assertEquals(gallery, LibraryViewMode.showGallery(galleryChosen, searchOpen))
        assertEquals(viewSwitch, LibraryViewMode.showViewSwitch(searchOpen))
        assertEquals(searchButton, LibraryViewMode.showSearchButton(searchOpen, plusMenuOpen = false))
    }

    @ParameterizedTest(name = "search open={0}, plus menu open={1} -> search button={2}")
    @CsvSource(
        "false, false, true",   // normal: visible above the + button
        "false, true,  false",  // + menu open: its buttons need that spot
        "true,  false, false",  // search box already open
        "true,  true,  false",
    )
    fun `search button steps aside for the plus menu`(searchOpen: Boolean, plusOpen: Boolean, expected: Boolean) =
        assertEquals(expected, LibraryViewMode.showSearchButton(searchOpen, plusOpen))

    @ParameterizedTest(name = "plus at ({0},{1}) w={2}, search {3}x{4}, gap={5} -> ({6},{7})")
    @CsvSource(
        nullValues = ["null"],
        value = [
            // same size as +: same left edge, one button-height plus gap above
            "900, 1700, 160, 160, 160, 30, 900.0, 1510.0",
            // smaller search button: still centred on the + button
            "900, 1700, 160, 120, 120, 30, 920.0, 1550.0",
            // gap couldn't be measured: fallback gap (20) is used
            "900, 1700, 160, 160, 160, null, 900.0, 1520.0",
            // a negative measured gap (overlap) is ignored in favour of the fallback
            "900, 1700, 160, 160, 160, -5, 900.0, 1520.0",
            // zero gap is a valid measurement
            "900, 1700, 160, 160, 160, 0, 900.0, 1540.0",
        ]
    )
    fun `search button sits centred directly above the plus button`(
        plusLeft: Int, plusTop: Int, plusWidth: Int, searchW: Int, searchH: Int, gap: Int?,
        expectedX: String, expectedY: String
    ) {
        val (x, y) = LibraryViewMode.placeSearchButton(
            plusLeft.toFloat(), plusTop.toFloat(), plusWidth, searchW, searchH, gap, fallbackGap = 20
        )
        assertEquals(expectedX.toFloat(), x)
        assertEquals(expectedY.toFloat(), y)
    }

    @Test
    fun `centres line up exactly`() {
        val (x, _) = LibraryViewMode.placeSearchButton(903f, 0f, 161, 117, 117, 10, 20)
        assertEquals(903f + 161 / 2f, x + 117 / 2f)
    }
}
