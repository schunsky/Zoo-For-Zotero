package com.mickstarify.zooforzotero.search

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Keeps the text of PDFs downloaded from zotero.org on the phone (compressed),
 * so each PDF's text is downloaded once, not on every search.
 * Text that isn't used for the chosen number of days is removed, and the least
 * recently used text goes first when the space limit is reached. Settings are
 * read on every use, so a changed setting applies straight away.
 * Call from a background thread.
 */
class FullTextCache(
    context: Context,
    private val maxAgeDays: () -> Int,
    private val maxBytes: () -> Long
) {
    private val dir = File(context.cacheDir, "fulltext").apply { mkdirs() }

    private fun fileFor(attachmentKey: String) =
        File(dir, attachmentKey.filter { it.isLetterOrDigit() } + ".txt.gz")

    fun read(attachmentKey: String): String? {
        val file = fileFor(attachmentKey)
        if (!file.isFile) return null
        if (CacheEviction.isExpired(file.lastModified(), System.currentTimeMillis(), maxAgeDays())) {
            file.delete()
            return null
        }
        return try {
            val text = GZIPInputStream(file.inputStream()).bufferedReader().use { it.readText() }
            file.setLastModified(System.currentTimeMillis())   // "used now": counts from last use
            text
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

    /** Is this PDF's text saved on the phone (and not due for removal)? */
    fun isSaved(attachmentKey: String): Boolean {
        val file = fileFor(attachmentKey)
        return file.isFile &&
            !CacheEviction.isExpired(file.lastModified(), System.currentTimeMillis(), maxAgeDays())
    }

    /** Space used by saved text, in bytes. */
    fun usedBytes(): Long =
        dir.listFiles { f -> f.name.endsWith(".txt.gz") }?.sumOf { it.length() } ?: 0L

    /** Removes text not used for too long and, if still over the space limit, the least recently used. */
    fun trim() {
        val files = dir.listFiles { f -> f.name.endsWith(".txt.gz") } ?: return
        val entries = files.map { CacheEntry(it.name, it.length(), it.lastModified()) }
        val toDelete = CacheEviction.filesToDelete(entries, System.currentTimeMillis(), maxAgeDays(), maxBytes())
        files.filter { it.name in toDelete }.forEach { it.delete() }
    }
}
