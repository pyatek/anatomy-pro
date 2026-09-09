package com.ptk.anatomypro.core.data

import androidx.room.Room
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/** Opens the atlas database in the app's documents directory. */
@OptIn(ExperimentalForeignApi::class)
fun anatomyDatabase(name: String = "anatomy.db"): AnatomyDatabase {
    val documents: NSURL = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    ) ?: error("no documents directory")
    return Room.databaseBuilder<AnatomyDatabase>(
        name = requireNotNull(documents.URLByAppendingPathComponent(name)?.path) { "bad database path" },
    ).openAnatomyDatabase()
}
