package com.mickstarify.zooforzotero.thumbnails

/** Rules and messages for "Make covers for all PDFs". Pure logic, unit-tested on the JVM. */
object CoverDownloadLogic {
    enum class Stop { NONE, CANCELLED, NOT_ON_WIFI }

    /** What happened when making the covers of one PDF. */
    enum class Result { ALREADY_MADE, MADE, NOT_ON_PHONE, UNREADABLE }

    /**
     * Checked before every PDF. Opening a PDF in a cloud folder fetches it through the
     * cloud app, so that only happens on Wi-Fi; PDFs on the phone itself need no network.
     */
    fun stopReason(cancelled: Boolean, folderOnDevice: Boolean, onWifi: Boolean): Stop = when {
        cancelled -> Stop.CANCELLED
        !folderOnDevice && !onWifi -> Stop.NOT_ON_WIFI
        else -> Stop.NONE
    }

    const val IDLE = "Makes the cover of every PDF in your library that's on this phone or in your " +
        "attachment folder, so the gallery shows them straight away. Tap to start."

    const val NO_LIBRARY = "Your library isn't loaded yet. Open the library once, then try again."

    fun loading() = "Looking up your PDFs…"

    fun progress(processed: Int, total: Int) = "Making covers… $processed of $total PDFs. Tap to stop."

    fun stopped(reason: Stop, processed: Int, total: Int): String = when (reason) {
        Stop.CANCELLED -> "Stopped. $processed of $total PDFs done. Tap to continue."
        Stop.NOT_ON_WIFI -> "Paused: not on Wi-Fi (your PDFs are in a cloud folder). " +
            "$processed of $total PDFs done. Tap to continue on Wi-Fi."
        Stop.NONE -> "$processed of $total PDFs done."
    }

    /** Summary once every PDF has been tried. */
    fun finished(counts: Map<Result, Int>): String {
        val made = counts[Result.MADE] ?: 0
        val had = counts[Result.ALREADY_MADE] ?: 0
        val notHere = counts[Result.NOT_ON_PHONE] ?: 0
        val unreadable = counts[Result.UNREADABLE] ?: 0
        val total = made + had + notHere + unreadable
        if (total == 0) return "Done: there are no PDFs in your library."
        val withCovers = made + had
        val head = if (withCovers == total) "Done: all $total PDFs have covers" else "Done: covers for $withCovers of $total PDFs"
        val parts = mutableListOf("$head ($made new).")
        if (notHere > 0) parts.add("${pdfs(notHere)} ${if (notHere == 1) "is" else "are"} not on this phone or in the attachment folder.")
        if (unreadable > 0) parts.add("${pdfs(unreadable)} couldn't be read (protected or damaged).")
        return parts.joinToString(" ")
    }

    private fun pdfs(n: Int) = if (n == 1) "1 PDF" else "$n PDFs"
}
