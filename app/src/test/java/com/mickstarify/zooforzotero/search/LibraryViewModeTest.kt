package com.mickstarify.zooforzotero.search

import org.junit.jupiter.api.Assertions.assertEquals
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
        assertEquals(searchButton, LibraryViewMode.showSearchButton(searchOpen))
    }
}
