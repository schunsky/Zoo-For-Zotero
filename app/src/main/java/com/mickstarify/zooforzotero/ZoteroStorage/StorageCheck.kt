package com.mickstarify.zooforzotero.ZoteroStorage

/**
 * What the start-up / return-to-app check found about the attachments folder.
 * Pure logic, unit-tested on the JVM.
 */
enum class StorageStatus {
    /** Folder usable (or not using a custom folder at all). */
    OK,
    /** Readable but not writable: linked PDFs open fine; downloads into it may fail. */
    READ_ONLY,
    /** Permission is still granted, but the folder's app (e.g. pCloud) didn't answer just now. */
    UNAVAILABLE,
    /** Android no longer grants Zoo access to the folder: it must be chosen again. */
    PERMISSION_LOST,
    /** "Custom" location selected, but no folder was ever chosen. */
    NOT_SET;

    /** Only these mean the setting really can't work and must be reconfigured. */
    val needsReconfiguring: Boolean get() = this == PERMISSION_LOST || this == NOT_SET
}

object StorageCheck {
    fun classify(
        usingCustomFolder: Boolean,
        folderChosen: Boolean,
        permissionGranted: Boolean,
        canRead: Boolean,
        canWrite: Boolean
    ): StorageStatus = when {
        !usingCustomFolder -> StorageStatus.OK
        !folderChosen -> StorageStatus.NOT_SET
        !permissionGranted -> StorageStatus.PERMISSION_LOST
        !canRead -> StorageStatus.UNAVAILABLE
        !canWrite -> StorageStatus.READ_ONLY
        else -> StorageStatus.OK
    }
}
