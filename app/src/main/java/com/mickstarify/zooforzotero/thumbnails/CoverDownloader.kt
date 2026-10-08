package com.mickstarify.zooforzotero.thumbnails

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.mickstarify.zooforzotero.PreferenceManager
import com.mickstarify.zooforzotero.ZoteroStorage.AttachmentStorageManager
import com.mickstarify.zooforzotero.ZoteroStorage.Database.GroupInfo
import com.mickstarify.zooforzotero.ZoteroStorage.ZoteroDB.ZoteroDB
import com.mickstarify.zooforzotero.search.LastRun
import com.mickstarify.zooforzotero.search.NetworkUtil
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Make covers for all PDFs": makes the gallery covers of every PDF in the personal
 * library, one after another, without having to scroll the gallery. Covers already made
 * are skipped, so tapping again after a stop continues where it left off. Runs while the
 * app is open; progress is reported through [status], and the final result is kept with
 * its time, so it's still shown after a restart.
 */
@Singleton
class CoverDownloader @Inject constructor(
    private val context: Context,
    private val preferences: PreferenceManager,
    private val attachmentStorageManager: AttachmentStorageManager,
    private val thumbnailLoader: PdfThumbnailLoader
) {
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile private var cancelled = false
    @Volatile var isRunning = false
        private set

    private val statusData = MutableLiveData(
        preferences.getLastRun(LAST_RUN_NAME)?.let { (message, time) -> LastRun.describe(message, formatTime(time)) }
            ?: CoverDownloadLogic.IDLE
    )

    /** The message to show under the button. */
    val status: LiveData<String> = statusData

    /** Starts, or stops if already running. */
    fun toggle() {
        if (isRunning) {
            cancelled = true
        } else {
            cancelled = false
            isRunning = true
            executor.execute {
                try {
                    run()
                } finally {
                    isRunning = false
                }
            }
        }
    }

    private fun run() {
        statusData.postValue(CoverDownloadLogic.loading())
        val allItems = try {
            val db = ZoteroDB(context, GroupInfo.NO_GROUP_ID)
            db.loadItemsFromDatabase().blockingAwait()
            db.items
        } catch (e: Exception) {
            Log.d("zotero", "make covers: couldn't load the library: ${e.message}")
            null
        }
        // No items at all: the library hasn't been synced to this phone yet.
        if (allItems.isNullOrEmpty()) {
            end(CoverDownloadLogic.NO_LIBRARY)
            return
        }
        val pdfs = allItems.filter { it.isDownloadable() && it.getFileExtension() == "pdf" }

        val folderOnDevice = attachmentStorageManager.isAttachmentFolderOnDevice()
        val counts = HashMap<CoverDownloadLogic.Result, Int>()
        pdfs.forEachIndexed { index, pdf ->
            val stop = CoverDownloadLogic.stopReason(cancelled, folderOnDevice, NetworkUtil.isUnmetered(context))
            if (stop != CoverDownloadLogic.Stop.NONE) {
                end(CoverDownloadLogic.stopped(stop, index, pdfs.size))
                return
            }
            statusData.postValue(CoverDownloadLogic.progress(index, pdfs.size))
            val result = try {
                thumbnailLoader.makeCoversNow(pdf)
            } catch (e: Exception) {
                Log.d("zotero", "make covers: ${pdf.itemKey} failed: ${e.message}")
                CoverDownloadLogic.Result.UNREADABLE
            }
            counts[result] = (counts[result] ?: 0) + 1
        }
        end(CoverDownloadLogic.finished(counts))
    }

    /** A run ended (finished, stopped or failed): show and keep the message with the time. */
    private fun end(message: String) {
        val now = System.currentTimeMillis()
        preferences.setLastRun(LAST_RUN_NAME, message, now)
        statusData.postValue(LastRun.describe(message, formatTime(now)))
    }

    private fun formatTime(timeMs: Long): String =
        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
            .format(java.util.Date(timeMs))

    private companion object {
        const val LAST_RUN_NAME = "cover_download"
    }
}
