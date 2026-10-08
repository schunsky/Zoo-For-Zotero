package com.mickstarify.zooforzotero.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class LibraryViewModeTest {

    // ---- Which view is shown: scenarios, step by step ----

    @Test
    fun `starting a search from gallery shows the list`() {
        val browsing = LibraryViewState(galleryChosen = true)
        assertTrue(browsing.showsGallery)
        assertFalse(browsing.searchOpened().showsGallery)
    }

    @Test
    fun `gallery is still possible during a search`() {
        val searching = LibraryViewState(galleryChosen = true).searchOpened()
        assertTrue(searching.toggled().showsGallery)
    }

    @Test
    fun `gallery is possible during a search started from list view too`() {
        val searching = LibraryViewState(galleryChosen = false).searchOpened()
        val switched = searching.toggled()
        assertTrue(switched.showsGallery)
        assertTrue(switched.galleryChosen, "the switch is remembered as the chosen view")
    }

    @Test
    fun `closing the search returns to the chosen view`() {
        val fromGallery = LibraryViewState(galleryChosen = true).searchOpened().searchClosed()
        assertTrue(fromGallery.showsGallery)
        val fromList = LibraryViewState(galleryChosen = false).searchOpened().searchClosed()
        assertFalse(fromList.showsGallery)
    }

    @Test
    fun `a choice made during a search is kept after it`() {
        // gallery -> search (list) -> button: gallery -> button: list -> close search
        val end = LibraryViewState(galleryChosen = true)
            .searchOpened().toggled().toggled().searchClosed()
        assertFalse(end.showsGallery, "last choice was list, so list stays")
        assertFalse(end.galleryChosen)
    }

    @Test
    fun `switching to gallery during a search survives the search closing`() {
        val end = LibraryViewState(galleryChosen = true).searchOpened().toggled().searchClosed()
        assertTrue(end.showsGallery)
    }

    @Test
    fun `the button always flips what is shown, searching or not`() {
        val states = listOf(
            LibraryViewState(galleryChosen = true),
            LibraryViewState(galleryChosen = false),
            LibraryViewState(galleryChosen = true).searchOpened(),
            LibraryViewState(galleryChosen = false).searchOpened(),
        )
        for (state in states) {
            assertEquals(!state.showsGallery, state.toggled().showsGallery, "from $state")
        }
    }

    @Test
    fun `opening the search box again changes nothing more`() {
        val once = LibraryViewState(galleryChosen = true).searchOpened()
        assertEquals(once, once.searchOpened())
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
