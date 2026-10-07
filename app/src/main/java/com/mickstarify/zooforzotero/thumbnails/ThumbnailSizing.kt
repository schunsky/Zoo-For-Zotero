package com.mickstarify.zooforzotero.thumbnails

import kotlin.math.roundToInt

/** The cover sizes the app uses. Each gets its own cached image. */
enum class CoverSize(val boxWidthDp: Int, val boxHeightDp: Int, val keySuffix: String) {
    /** Small cover in the list view, same size as the item-type icon. */
    LIST(48, 48, ""),
    /** Large cover in the gallery view (portrait book-cover shape). */
    GALLERY(128, 170, "g"),
}

/**
 * Pure helpers for PDF cover thumbnails.
 * Kept free of Android classes so they can be unit-tested on a plain JVM.
 */
object ThumbnailSizing {

    /**
     * Scales a page of [pageWidth] x [pageHeight] to fit inside a
     * [boxWidth] x [boxHeight] box, keeping the page's aspect ratio.
     * Returns (width, height) in pixels, each at least 1.
     * A page with an invalid size is given the whole box.
     */
    fun fitInBox(pageWidth: Int, pageHeight: Int, boxWidth: Int, boxHeight: Int): Pair<Int, Int> {
        require(boxWidth > 0 && boxHeight > 0) { "box must be positive, was ${boxWidth}x$boxHeight" }
        if (pageWidth <= 0 || pageHeight <= 0) return Pair(boxWidth, boxHeight)
        val scale = minOf(
            boxWidth.toDouble() / pageWidth,
            boxHeight.toDouble() / pageHeight
        )
        val width = (pageWidth * scale).roundToInt().coerceIn(1, boxWidth)
        val height = (pageHeight * scale).roundToInt().coerceIn(1, boxHeight)
        return Pair(width, height)
    }

    /**
     * File-safe cache key for an attachment's cover in a given [size].
     * Includes part of the md5 when Zotero knows it, so a replaced PDF
     * gets a fresh cover instead of the old cached one.
     * The LIST size keeps the original key format, so covers cached by
     * earlier versions stay valid.
     */
    fun cacheKey(attachmentKey: String, md5: String?, size: CoverSize = CoverSize.LIST): String {
        val safeKey = attachmentKey.uppercase().filter { it.isLetterOrDigit() }
        require(safeKey.isNotEmpty()) { "attachmentKey has no usable characters: '$attachmentKey'" }
        val safeMd5 = md5.orEmpty().filter { it.isLetterOrDigit() }.take(12).lowercase()
        val base = if (safeMd5.isEmpty()) safeKey else "${safeKey}_$safeMd5"
        return if (size.keySuffix.isEmpty()) base else "${base}_${size.keySuffix}"
    }

    /** Number of gallery columns for a screen [screenWidthDp] wide: about 130dp each, at least 2. */
    fun galleryColumns(screenWidthDp: Int): Int = (screenWidthDp / 130).coerceAtLeast(2)
}
