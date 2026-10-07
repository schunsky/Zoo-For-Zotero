package com.mickstarify.zooforzotero.thumbnails

/**
 * Decides when a PDF cover may be made. Pure logic, unit-tested on the JVM.
 *
 * Files on the phone itself are always fine. For a cloud folder (e.g. pCloud),
 * making a cover downloads the PDF, so it follows the user's setting.
 */
object CoverPolicy {
    const val MODE_OPENED = "OPENED"   // only PDFs the user opens (they're downloaded anyway)
    const val MODE_WIFI = "WIFI"       // any PDF in view, but only on Wi-Fi / unmetered networks
    const val MODE_ALL = "ALL"         // any PDF in view, on any connection

    /** May a cover be made for a row that is merely scrolled into view? */
    fun mayRenderWhileBrowsing(folderOnDevice: Boolean, cloudMode: String?, unmeteredNetwork: Boolean): Boolean {
        if (folderOnDevice) return true
        return when (cloudMode) {
            MODE_ALL -> true
            MODE_WIFI -> unmeteredNetwork
            else -> false   // MODE_OPENED, or unknown/missing value: the safe choice
        }
    }
}
