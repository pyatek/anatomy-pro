package com.ptk.anatomypro.core.data

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * The settings both platforms share.
 *
 * `BundledSQLiteDriver` ships SQLite with the app rather than using the system's, so the
 * two platforms run identical SQL. That matters more here than the binary size: iOS and
 * Android otherwise differ in SQLite version, and eventually in whether a feature such as
 * FTS is even compiled in.
 */
fun RoomDatabase.Builder<AnatomyDatabase>.openAnatomyDatabase(): AnatomyDatabase =
    setDriver(BundledSQLiteDriver())
        // Dispatchers.IO is not in the common source set; Default is the portable
        // equivalent here, and queries are short.
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
