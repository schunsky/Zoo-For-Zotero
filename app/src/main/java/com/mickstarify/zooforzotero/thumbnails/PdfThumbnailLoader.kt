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
 * Renders the first page of a locally available PDF as a small cover image.
 *
 * - Only uses files already on the device (downloaded or linked); never downloads.
 * - Results are cached in memory and on disk (app cache dir), so each PDF is
 *   rendered once.
 * - Rendering happens on one background thread; callbacks run on the main thread.
 */
@Singleton
class PdfThumbnailLoader @Inject constructor(
    private val context: Context,
    private val attachmentStorageManager: AttachmentStorageManager,
    private val preferenceManager: PreferenceManager
) {
    companion object {
        private const val TAG = "PdfThumbnailLoader"
        private const val THUMBNAIL_BOX_DP = 48          // matches the list icon size
        private const val JPEG_QUALITY = 85
    }

    private val memoryCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 16).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    // Attachments with no local file, so scrolling doesn't repeat slow folder
    // lookups for every row. Cleared by [forgetMissing] when the list is shown
    // again, so a PDF downloaded in the meantime gets its cover.
    private val knownMissing: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // PdfRenderer isn't thread-safe, so render one PDF at a time.
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val diskDir: File by lazy {
        File(context.cacheDir, "pdf_thumbnails").apply { mkdirs() }
    }

    private val boxPx: Int by lazy {
        (THUMBNAIL_BOX_DP * context.resources.displayMetrics.density).roundToInt()
    }

    /**
     * Requests the cover for [attachment]. [onLoaded] is called on the main thread
     * only if a cover could be produced and [isStillWanted] is still true
     * (list rows get recycled while scrolling).
     */
    fun load(attachment: Item, isStillWanted: () -> Boolean, onLoaded: (Bitmap) -> Unit) {
        val key = try {
            ThumbnailSizing.cacheKey(attachment.itemKey, attachment.data["md5"])
        } catch (e: IllegalArgumentException) {
            return
        }

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
                ?: if (renderAllowed) renderAndStore(attachment, key) else null
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
     * Makes the cover for a PDF the user just opened, whatever the cloud setting:
     * the file is being downloaded for reading anyway. It shows up when the user
     * returns to the list.
     */
    fun makeCoverForOpenedPdf(attachment: Item) {
        val key = try {
            ThumbnailSizing.cacheKey(attachment.itemKey, attachment.data["md5"])
        } catch (e: IllegalArgumentException) {
            return
        }
        executor.execute {
            if (memoryCache.get(key) != null || File(diskDir, "$key.jpg").isFile) return@execute
            renderAndStore(attachment, key)?.let { bitmap ->
                memoryCache.put(key, bitmap)
                knownMissing.remove(key)
            }
        }
    }

    private fun isOnUnmeteredNetwork(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /** Re-check attachments that had no local file (e.g. after returning to the list). */
    fun forgetMissing() {
        knownMissing.clear()
    }

    private fun readFromDisk(key: String): Bitmap? {
        val file = File(diskDir, "$key.jpg")
        if (!file.isFile) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    private fun renderAndStore(attachment: Item, key: String): Bitmap? {
        val uri = attachmentStorageManager.findLocalAttachmentUri(attachment) ?: return null

        val bitmap = renderFirstPage(uri, attachment.itemKey) ?: return null
        writeToDisk(key, bitmap)
        return bitmap
    }

    private fun renderFirstPage(uri: Uri, itemKey: String): Bitmap? {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount == 0) {
                        null
                    } else {
                        renderer.openPage(0).use { page ->
                            val (width, height) =
                                ThumbnailSizing.fitInBox(page.width, page.height, boxPx, boxPx)
                            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE) // PDF pages are transparent by default
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Encrypted, damaged, or unreadable PDFs: keep the normal icon.
            Log.d(TAG, "Could not render cover for $itemKey: ${e.message}")
            null
        }
    }

    private fun writeToDisk(key: String, bitmap: Bitmap) {
        // Write to a temp file first so a crash never leaves a half-written thumbnail.
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
