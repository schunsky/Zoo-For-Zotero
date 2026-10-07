package com.mickstarify.zooforzotero.search

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Keeps the text of PDFs downloaded from zotero.org on the phone (compressed),
 * so each PDF's text is downloaded once, not on every search.
 * Entries expire after [MAX_AGE_MS]; the oldest are removed above [MAX_TOTAL_BYTES].
 * Call from a background thread.
 */
class FullTextCache(context: Context) {
    companion object {
        private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000   // 30 days
        private const val MAX_TOTAL_BYTES = 100L * 1024 * 1024     // 100 MB
    }

    private val dir = File(context.cacheDir, "fulltext").apply { mkdirs() }

    private fun fileFor(attachmentKey: String) =
        File(dir, attachmentKey.filter { it.isLetterOrDigit() } + ".txt.gz")

    fun read(attachmentKey: String): String? {
        val file = fileFor(attachmentKey)
        if (!file.isFile) return null
        if (System.currentTimeMillis() - file.lastModified() > MAX_AGE_MS) {
            file.delete()
            return null
        }
        return try {
            GZIPInputStream(file.inputStream()).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            file.delete()
            null
        }
    }

    fun write(attachmentKey: String, text: String) {
        val target = fileFor(attachmentKey)
        val temp = File(dir, target.name + ".tmp")
        try {
            GZIPOutputStream(temp.outputStream()).bufferedWriter().use { it.write(text) }
            if (!temp.renameTo(target)) temp.delete()
        } catch (e: IOException) {
            temp.delete()
        }
        trim()
    }

    private fun trim() {
        val files = dir.listFiles { f -> f.name.endsWith(".txt.gz") }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= MAX_TOTAL_BYTES) break
            total -= file.length()
            file.delete()
        }
    }
}
