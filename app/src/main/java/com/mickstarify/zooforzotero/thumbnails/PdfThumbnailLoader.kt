package com.mickstarify.zooforzotero.thumbnails

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import com.mickstarify.zooforzotero.PreferenceManager
import com.mickstarify.zooforzotero.ZoteroStorage.AttachmentStorageManager
import com.mickstarify.zooforzotero.ZoteroStorage.Database.Item
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Renders the first page of a PDF as a cover image, in every [CoverSize] at once
 * (so switching between list and gallery never reads a cloud PDF twice).
 *
 * - Covers are cached in memory and on disk (app cache dir), so each PDF is
 *   rendered once.
 * - Rendering happens on one background thread; callbacks run on the main thread.
 * - For cloud folders, whether browsing may make new covers follows [CoverPolicy].
 */
@Singleton
class PdfThumbnailLoader @Inject constructor(
    private val context: Context,
    private val attachmentStorageManager: AttachmentStorageManager,
    private val preferenceManager: PreferenceManager
) {
    companion object {
        private const val TAG = "PdfThumbnailLoader"
        private const val JPEG_QUALITY = 85
    }

    private val memoryCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 12).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    // Attachments with no cover available, so scrolling doesn't repeat slow folder
    // lookups for every row. Cleared by [forgetMissing] when the list is shown
    // again, so a PDF downloaded in the meantime gets its cover.
    private val knownMissing: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // PdfRenderer isn't thread-safe, so render one PDF at a time.
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val diskDir: File by lazy {
        File(context.cacheDir, "pdf_thumbnails").apply { mkdirs() }
    }

    private fun boxPx(dp: Int): Int = (dp * context.resources.displayMetrics.density).roundToInt()

    private fun keyFor(attachment: Item, size: CoverSize): String? = try {
        ThumbnailSizing.cacheKey(attachment.itemKey, attachment.data["md5"], size)
    } catch (e: IllegalArgumentException) {
        null
    }

    /**
     * Requests the [size] cover for [attachment]. [onLoaded] is called on the main thread
     * only if a cover could be produced and [isStillWanted] is still true
     * (rows get recycled while scrolling).
     */
    fun load(
        attachment: Item,
        size: CoverSize,
        isStillWanted: () -> Boolean,
        onLoaded: (Bitmap) -> Unit
    ) {
        val key = keyFor(attachment, size) ?: return

        memoryCache.get(key)?.let {
            onLoaded(it)
            return
        }

        if (key in knownMissing) return

        // Covers already made are always shown (from the caches). Making a new one
        // from a cloud folder downloads the PDF, so that follows the user's setting.
        val renderAllowed = CoverPolicy.mayRenderWhileBrowsing(
            folderOnDevice = attachmentStorageManager.isAttachmentFolderOnDevice(),
            cloudMode = preferenceManager.getCloudCoverMode(),
            unmeteredNetwork = isOnUnmeteredNetwork()
        )

        executor.execute {
            if (!isStillWanted()) return@execute

            val bitmap = readFromDisk(key)
                ?: if (renderAllowed) renderAllSizes(attachment)[size] else null
            if (bitmap == null) {
                knownMissing.add(key)
                return@execute
            }
            knownMissing.remove(key)
            memoryCache.put(key, bitmap)
            mainHandler.post {
                if (isStillWanted()) onLoaded(bitmap)
            }
        }
    }

    /**
     * Makes the covers for a PDF the user just opened, whatever the cloud setting:
     * the file is being downloaded for reading anyway. They show up when the user
     * returns to the list.
     */
    fun makeCoverForOpenedPdf(attachment: Item) {
        val keys = CoverSize.values().mapNotNull { keyFor(attachment, it) }
        if (keys.size < CoverSize.values().size) return
        executor.execute {
            val allCached = keys.all { File(diskDir, "$it.jpg").isFile }
            if (!allCached) renderAllSizes(attachment)
        }
    }

    /** Re-check attachments that had no cover (e.g. after returning to the list). */
    fun forgetMissing() {
        knownMissing.clear()
    }

    private fun isOnUnmeteredNetwork(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun readFromDisk(key: String): Bitmap? {
        val file = File(diskDir, "$key.jpg")
        if (!file.isFile) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    /**
     * Opens the PDF once and renders its first page in every size.
     * Each result is saved to disk and to the memory cache.
     * Returns an empty map if the PDF isn't available or can't be read.
     */
    private fun renderAllSizes(attachment: Item): Map<CoverSize, Bitmap> {
        val uri = attachmentStorageManager.findLocalAttachmentUri(attachment) ?: return emptyMap()
        val rendered = renderFirstPage(uri, attachment.itemKey)
        for ((size, bitmap) in rendered) {
            val key = keyFor(attachment, size) ?: continue
            writeToDisk(key, bitmap)
            memoryCache.put(key, bitmap)
            knownMissing.remove(key)
        }
        return rendered
    }

    private fun renderFirstPage(uri: Uri, itemKey: String): Map<CoverSize, Bitmap> {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount == 0) {
                        emptyMap()
                    } else {
                        renderer.openPage(0).use { page ->
                            CoverSize.values().associateWith { size ->
                                val (width, height) = ThumbnailSizing.fitInBox(
                                    page.width, page.height,
                                    boxPx(size.boxWidthDp), boxPx(size.boxHeightDp)
                                )
                                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(Color.WHITE) // PDF pages are transparent by default
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bmp
                            }
                        }
                    }
                }
            } ?: emptyMap()
        } catch (e: Exception) {
            // Encrypted, damaged, or unreadable PDFs: keep the normal icon.
            Log.d(TAG, "Could not render cover for $itemKey: ${e.message}")
            emptyMap()
        }
    }

    private fun writeToDisk(key: String, bitmap: Bitmap) {
        // Write to a temp file first so a crash never leaves a half-written cover.
        val target = File(diskDir, "$key.jpg")
        val temp = File(diskDir, "$key.jpg.tmp")
        try {
            FileOutputStream(temp).use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            if (!temp.renameTo(target)) temp.delete()
        } catch (e: IOException) {
            Log.d(TAG, "Could not cache cover for $key: ${e.message}")
            temp.delete()
        }
    }
}
