package com.mickstarify.zooforzotero.search

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.mickstarify.zooforzotero.PreferenceManager
import com.mickstarify.zooforzotero.SyncSetup.domain.AuthenticationStorage
import com.mickstarify.zooforzotero.ZoteroAPI.ZoteroAPI
import com.mickstarify.zooforzotero.ZoteroStorage.AttachmentStorageManager
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Download text of all PDFs": saves the text of every PDF in the personal library
 * on the phone, one at a time, on Wi-Fi only. Text already saved is skipped, so
 * tapping again after a stop continues where it left off. Runs while the app is
 * open; progress is reported through [status].
 */
@Singleton
class FullTextDownloader @Inject constructor(
    private val context: Context,
    private val preferences: PreferenceManager,
    private val auth: AuthenticationStorage,
    private val attachmentStorageManager: AttachmentStorageManager
) {
    private val executor = Executors.newSingleThreadExecutor()

    // After a restart, show how the last run ended, and when.
    private val statusData = MutableLiveData(
        preferences.getLastRun(LAST_RUN_NAME)?.let { (message, time) -> LastRun.describe(message, formatTime(time)) }
            ?: DownloadAllLogic.IDLE
    )

    @Volatile private var cancelled = false
    @Volatile var isRunning = false
        private set

    /** The message to show under the button. */
    val status: LiveData<String> = statusData

    private val cache by lazy {
        FullTextCache(
            context,
            maxAgeDays = { preferences.getFullTextCacheDays() },
            maxBytes = { maxBytes() }
        )
    }

    private fun maxBytes() = preferences.getFullTextCacheMegabytes() * 1024L * 1024L

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
        const val LAST_RUN_NAME = "fulltext_download"
    }

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
        if (!auth.hasCredentials()) {
            end(DownloadAllLogic.NOT_LOGGED_IN)
            return
        }
        statusData.postValue(DownloadAllLogic.checking())
        val api = ZoteroAPI(
            auth.getUserKey(), auth.getUserID(), auth.getUsername(),
            attachmentStorageManager, preferences
        )
        val allKeys = try {
            api.getFullTextKeys().blockingGet()
        } catch (e: Exception) {
            end(DownloadAllLogic.failedToStart(e.message ?: "no connection"))
            return
        }

        val todo = DownloadAllLogic.keysToDownload(allKeys) { cache.isSaved(it) }
        val total = allKeys.size
        var saved = total - todo.size
        var failed = 0

        for (key in todo) {
            val stop = DownloadAllLogic.stopReason(
                cancelled, NetworkUtil.isUnmetered(context), cache.usedBytes(), maxBytes()
            )
            if (stop != DownloadAllLogic.Stop.NONE) {
                end(DownloadAllLogic.stopped(stop, saved, total, maxBytes()))
                return
            }
            statusData.postValue(DownloadAllLogic.progress(saved, total, cache.usedBytes()))
            try {
                val text = api.getFullText(key).blockingGet()
                if (text.isNotEmpty()) cache.write(key, text)
                saved++
            } catch (e: Exception) {
                Log.d("zotero", "download all: no text for $key: ${e.message}")
                failed++
            }
        }
        end(DownloadAllLogic.finished(total, failed, cache.usedBytes(), preferences.getFullTextCacheDays()))
    }
}
