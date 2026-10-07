package com.mickstarify.zooforzotero.ZoteroStorage

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.mickstarify.zooforzotero.PreferenceManager
import com.mickstarify.zooforzotero.ZoteroStorage.Database.Item
import okhttp3.internal.toHexString
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

const val STORAGE_ACCESS_REQUEST = 1  // The request code

/* This classes handles the data storage for all attachments. */


@Singleton
class AttachmentStorageManager @Inject constructor(
    val context: Context,
    val preferenceManager: PreferenceManager
) {

    enum class StorageMode {
        CUSTOM,
        EXTERNAL_CACHE,
        NONE_SET
    }

    val storageMode: StorageMode
        get() = when (preferenceManager.getStorageMode()) {
            "CUSTOM" -> StorageMode.CUSTOM
            "EXTERNAL_CACHE" -> StorageMode.EXTERNAL_CACHE
            else -> StorageMode.EXTERNAL_CACHE
        }


    fun validateAccess() {
        if (storageMode == StorageMode.CUSTOM) {
            if (testStorage() == false) {
                throw (IOException("Cannot Read Attachment Directory"))
            }
        }
    }

    fun getFilenameForItem(item: Item): String {
        return item.data.get("filename") ?: "unknown.pdf"
    }

    fun validateMd5ForItem(
        item: Item,
        md5Key: String
    ): Boolean {
        if (item.itemType != Item.ATTACHMENT_TYPE) {
            throw (Exception("error invalid item ${item.itemKey}: ${item.itemType} cannot calculate md5."))
        }
        if (md5Key == "") {
            Log.d("zotero", "error cannot check MD5, no MD5 Available")
            return true
        }
        val calculatedMd5Key = calculateMd5(item)
        return (calculatedMd5Key == md5Key)
    }

    fun checkIfAttachmentExists(item: Item, checkMd5: Boolean = true): Boolean {
        val filename = getFilenameForItem(item)
        if (storageMode == StorageMode.EXTERNAL_CACHE) {
            val outputDir = File(context.externalCacheDir, item.itemKey.uppercase())
            if (!outputDir.exists() || outputDir.isDirectory == false) {
                return false
            }
            val file = File(outputDir, filename)
            if (file.length() == 0L) {
                file.delete()
                return false
            }
            if (checkMd5) {
                return file.exists() && calculateMd5(file.inputStream()) == item.data["md5"]
            } else {
                return file.exists()
            }

        } else if (storageMode == StorageMode.CUSTOM) {
            val location = preferenceManager.getCustomAttachmentStorageLocation()
            val rootDocFile = DocumentFile.fromTreeUri(context, Uri.parse(location))
            var directory = rootDocFile?.findFile(item.itemKey.uppercase())

            if (directory == null || directory.isDirectory == false) {
                return false
            }
            val file = directory?.findFile(filename)
//            val helper = SAFHelper(location)
//            val file = DocumentFile.fromSingleUri(context, helper.getUriForItem(item.itemKey.uppercase(), filename))

//            Log.d("zotero", "actual URI: ${file?.uri}")
//            Log.d("zotero", "my     URI: ${helper.getUriForItem(item.itemKey.uppercase(), filename)}")

            if (file == null) {
                return false
            }
            if (file.length() == 0L) {
                file.delete()
                return false
            }
            val exists = file.exists()
            if (exists && checkMd5) {
                return calculateMd5(context.contentResolver.openInputStream(file.uri)!!) == item.data["md5"]
            } else {
                return exists
            }
        }

        throw Exception("not implemented")
    }

    fun calculateMd5(attachment: Item): String {
        val attachmentInputStream = this.getItemInputStream(attachment)
        return calculateMd5(attachmentInputStream)
    }

    fun calculateMd5(inputStream: InputStream): String {
        val buffer = ByteArray(1024)
        val complete: MessageDigest = MessageDigest.getInstance("MD5")
        var numRead: Int

        do {
            numRead = inputStream.read(buffer)
            if (numRead > 0) {
                complete.update(buffer, 0, numRead)
            }
        } while (numRead != -1)

        inputStream.close()
        var result = ""
        val checksumByteArray = complete.digest()
        for (i in 0 until checksumByteArray.size) {
            result += ((checksumByteArray.get(i).toInt() and 0xff) + 0x100).toHexString()
                .substring(1)
        }
        return result
    }

    fun getItemOutputStream(item: Item): OutputStream {
        val filename = getFilenameForItem(item)
        val mimeType = item.data["contentType"] ?: "application/pdf"
        if (storageMode == StorageMode.EXTERNAL_CACHE) {
            val file = getAttachmentFile(item)
            return file.outputStream()
        } else if (storageMode == StorageMode.CUSTOM) {
            val documentTree = DocumentFile.fromTreeUri(context, getCustomStorageTreeURI())
            var directory = documentTree?.findFile(item.itemKey.uppercase())
            if (directory == null || directory.isDirectory == false) {
                directory = documentTree?.createDirectory(item.itemKey.uppercase())
            }
            var itemFile = directory!!.findFile(filename)
            if (itemFile == null || !itemFile.exists()) {
                itemFile = directory.createFile(mimeType, filename)
            }
            return context.contentResolver.openOutputStream(itemFile!!.uri)!!
        }
        throw Exception("not implemented")
    }

    fun getItemInputStream(item: Item): InputStream {
        val filename = getFilenameForItem(item)
        val mimeType = item.data["contentType"] ?: "application/pdf"
        if (storageMode == StorageMode.EXTERNAL_CACHE) {
            val file = getAttachmentFile(item)
            return file.inputStream()
        } else if (storageMode == StorageMode.CUSTOM) {
            return context.contentResolver.openInputStream(getAttachmentUri(item))!!
        }
        throw Exception("not implemented")
    }

    fun getAttachmentUri(item: Item): Uri {
        val filename = getFilenameForItem(item)
        if (storageMode == StorageMode.EXTERNAL_CACHE) {
            val file = getAttachmentFile(item)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                return FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider.app",
                    file
                )
            } else {
                return Uri.fromFile(file)
            }

        } else if (storageMode == StorageMode.CUSTOM) {
            val documentTree = DocumentFile.fromTreeUri(context, getCustomStorageTreeURI())
            val directory = documentTree?.findFile(item.itemKey.uppercase())
            val file = directory?.findFile(filename) ?: throw FileNotFoundException()
            if (file.exists()) {
                return file.uri
            }
            throw FileNotFoundException()
        }
        throw Exception("not implemented")
    }

    fun askUserForPath(activity: Activity) {
        /* Attempts to get access to a directory to store zotero storage. */
        val intent = Intent()
        intent.action = Intent.ACTION_OPEN_DOCUMENT_TREE
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.startActivityForResult(
            intent,
            STORAGE_ACCESS_REQUEST
        )
    }

    fun openAttachment(attachment: Item): Intent {
        var intent = Intent(Intent.ACTION_VIEW)
        var attachmentUri = getAttachmentUri(attachment)
        Log.d("zotero", "opening PDF with Uri $attachmentUri")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent = Intent(Intent.ACTION_VIEW)
            intent.data = attachmentUri
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } else {
            intent.setDataAndType(attachmentUri, attachment.data["contentType"])
            intent.flags = Intent.FLAG_ACTIVITY_NO_HISTORY
            intent = Intent.createChooser(intent, "Open File")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        }
        return intent
    }

    fun openAttachment(uri: Uri, contentType: String = ""): Intent {
        var intent = Intent(Intent.ACTION_VIEW)
        Log.d("zotero", "opening PDF with Uri $uri")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent = Intent(Intent.ACTION_VIEW)
            intent.data = uri
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } else {
            intent.setDataAndType(uri, contentType)
            intent.flags = Intent.FLAG_ACTIVITY_NO_HISTORY
            intent = Intent.createChooser(intent, "Open File")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        }
        return intent
    }


    fun testStorage(): Boolean {
        if (storageMode == StorageMode.EXTERNAL_CACHE) {
            return true //nothing to test.
        }

        val location = preferenceManager.getCustomAttachmentStorageLocation()
        if (location == "") {
            return false
        }
        val rootDocFile = DocumentFile.fromTreeUri(context, Uri.parse(location))
        Log.d(
            "zotero",
            "testing dir canWrite=${rootDocFile?.canWrite()} canRead=${rootDocFile?.canRead()}"
        )
        return rootDocFile?.canWrite() == true
    }

    fun createTempFile(filename: String): File {
        val zipFile = File(context.cacheDir, filename)
        if (zipFile.exists()) {
            zipFile.delete()
        }
        return zipFile
    }

    fun setStorage(location: String?) {
        if (location == null) {
            Log.e("zotero", "error got null for location in setStorage()")
            return
        }

        val uri = Uri.parse(location)

        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        preferenceManager.setCustomAttachmentStorage(location)
        testStorage()
    }

    fun getCustomStorageTreeURI(): Uri {
        return Uri.parse(preferenceManager.getCustomAttachmentStorageLocation())
    }

    fun getMtime(attachment: Item): Long {
        when (storageMode) {
            StorageMode.EXTERNAL_CACHE -> {
                val file = getAttachmentFile(attachment)
                return file.lastModified()
            }

            StorageMode.CUSTOM -> {
                val docFile = DocumentFile.fromSingleUri(context, getAttachmentUri(attachment))
                return docFile?.lastModified() ?: throw FileNotFoundException()
            }

            else -> throw Exception("Invalid storage mode")
        }

    }

    private fun getAttachmentFile(attachment: Item): File {
        val filename = getFilenameForItem(attachment)
        val directory = File(context.externalCacheDir, attachment.itemKey.uppercase())
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return File(directory, filename)
    }

    fun getFileSize(attachment: Item): Result<Long> {
        return try {
            val size = if (storageMode == StorageMode.EXTERNAL_CACHE) {
                getAttachmentFile(attachment).length()
            } else if (storageMode == StorageMode.CUSTOM) {
                val uri = getAttachmentUri(attachment)
                getFileSize(uri)
            } else {
                throw Exception("Invalid storage mode")
            }
            Result.success(size)
        } catch (e: FileNotFoundException) {
            Log.d("AttachmentStorageManager", "File not found for attachment ${attachment.itemKey}: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("AttachmentStorageManager", "Error getting file size for attachment ${attachment.itemKey}: ${e.message}")
            Result.failure(e)
        }
    }

    fun getFileSize(attachmentUri: Uri): Long {
        val docFile = DocumentFile.fromSingleUri(context, attachmentUri)
        return docFile?.length() ?: throw FileNotFoundException()
    }

    fun readBytes(attachment: Item): ByteArray {
        return getItemInputStream(attachment).readBytes()
    }

    fun writeAttachmentFromFile(file: File, attachment: Item): Uri {
        val outputStream = getItemOutputStream(attachment)
        val inputSource = file.inputStream().source()

        val outputSinkBuffer = outputStream.sink().buffer()
        outputSinkBuffer.writeAll(inputSource)
        outputSinkBuffer.close()
        inputSource.close()
        outputStream.close()
        return getAttachmentUri(attachment)
    }

    fun deleteAttachment(attachment: Item) {
        when (storageMode) {
            StorageMode.EXTERNAL_CACHE -> {
                try {
                    getAttachmentFile(attachment).delete()
                } catch (e: Exception) {
                    Log.d("zotero", "cannot delete file. Not found.")
                }
            }

            StorageMode.CUSTOM -> {
                try {
                    val docFile = DocumentFile.fromSingleUri(context, getAttachmentUri(attachment))
                    docFile?.delete()
                } catch (e: java.io.FileNotFoundException) {
                    Log.d("zotero", "cannot delete file. Not found.")
                }
            }

            else -> throw Exception("not implemented")
        }
    }

    fun openLinkedAttachment(item: Item): Intent? {
        val uri = findLinkedAttachmentUri(item) ?: return null
        return openAttachment(uri, item.data["contentType"] ?: "")
    }

    fun findLinkedAttachmentUri(item: Item): Uri? {
        /* This method searches for the linked attachment in the users storage directory.
        * This may fail and return nothing. */
        if (item.data["linkMode"] != "linked_file") {
            throw Exception("error attempting to open linked attachment on something this isn't elligible.")
        }

        // will work as followed:
        // /home/michael/Downloads/Alice in Wonderland.pdf -> [home, michael, Downloads. Alice in Wonderland.pdf]
        // for each path in list, check if folder exists in root dir
        // if it does, pursue this path.
        // if it doesn't, return to the root path, and repeat for the next item in the list.


        var itemPath = item.data["path"] ?: ""
        // do some transformations for windows style paths
        // e.g C:\\Users\\michael\\file.txt -> C:/Users/michael/file.txt
        itemPath = itemPath.replace("\\", File.separator)
        //we get rid of zotero reserved expression on storaged path "attachemnts:"
        itemPath = itemPath.replace(Regex("^attachments:"), "")
        val directories = itemPath.split("/")
        if (storageMode == StorageMode.CUSTOM) {
            for (index in directories.indices) {
                val location = preferenceManager.getCustomAttachmentStorageLocation()
                var documentFile = DocumentFile.fromTreeUri(context, Uri.parse(location))
                var i = index
                while (i < directories.size) {
                    val id: String =
                        DocumentsContract.getDocumentId(documentFile?.uri) + "/" + directories[i]
                    documentFile = DocumentFile.fromSingleUri(
                        context,
                        DocumentsContract.buildDocumentUriUsingTree(documentFile?.uri, id)
                    )
                    Log.d("zotero", "checking ${directories[i]}")
                    if (documentFile?.exists() ?: false) {
                        i++
                    } else {
                        break
                    }
                }
                if (documentFile?.isFile ?: false) {
                    Log.d("zotero", "found file ${documentFile?.name}")
                    return documentFile!!.uri
                }
            }
            // The fast search above only works when the folder comes from the phone's own
            // storage. Folders from cloud apps (e.g. pCloud) use internal IDs instead of
            // paths, so fall back to walking the folders by name.
            findByWalkingFolders(directories)?.let { return it }
        } else if (storageMode == StorageMode.EXTERNAL_CACHE) {
            // logic using File api is much simpler
            for (i in directories.indices) {
                var path = directories.slice(i..directories.size - 1).joinToString("/")
                if (path.first() == '/') {
                    path = path.slice(1..path.length - 1)
                }
                Log.d("zotero", "checking path $path")
                var document = File(context.externalCacheDir, path)
                if (document.exists()) {
                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            document
                        )
                    } else {
                        Uri.fromFile(document)
                    }
                    return uri
                }
            }


        } else {
            throw NotImplementedError()
        }

        return null
    }

    /**
     * Finds a file by opening each folder in [directories] by name, starting from the
     * chosen attachments folder. Works with any folder provider (including cloud apps),
     * but is slower, because every step lists a folder's contents.
     * Like the fast search, it also tries later parts of the path, so a full desktop path
     * (/home/me/pCloudDrive/Papers/x.pdf) still matches Papers/x.pdf.
     */
    private fun findByWalkingFolders(directories: List<String>): Uri? {
        val root = DocumentFile.fromTreeUri(context, getCustomStorageTreeURI()) ?: return null
        val parts = directories.filter { it.isNotEmpty() }
        for (start in parts.indices) {
            var current: DocumentFile? = root
            for (name in parts.subList(start, parts.size)) {
                current = current?.findFile(name)
                if (current == null) break
            }
            if (current != null && current.isFile) {
                Log.d("zotero", "found file by walking folders: ${parts.subList(start, parts.size).joinToString("/")}")
                return current.uri
            }
        }
        return null
    }

    /**
     * True when attachments are on the phone itself. False when the chosen
     * attachments folder belongs to a cloud app, where every read downloads the file.
     */
    fun isAttachmentFolderOnDevice(): Boolean {
        if (storageMode != StorageMode.CUSTOM) return true
        return getCustomStorageTreeURI().authority == "com.android.externalstorage.documents"
    }

    /**
     * Returns a readable Uri for an attachment that is already on this device
     * (a downloaded stored file or a linked file), or null if it isn't here.
     * Never downloads anything and never throws.
     */
    fun findLocalAttachmentUri(item: Item): Uri? {
        return try {
            if (item.data["linkMode"] == "linked_file") {
                findLinkedAttachmentUri(item)
            } else when (storageMode) {
                StorageMode.EXTERNAL_CACHE -> {
                    val directory = File(context.externalCacheDir, item.itemKey.uppercase())
                    val file = File(directory, getFilenameForItem(item))
                    if (file.isFile && file.length() > 0L) Uri.fromFile(file) else null
                }
                StorageMode.CUSTOM -> getAttachmentUri(item)
                else -> null
            }
        } catch (e: Exception) {
            null
        } catch (e: NotImplementedError) {
            null
        }
    }


}