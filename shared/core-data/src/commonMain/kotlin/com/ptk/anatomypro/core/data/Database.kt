package com.ptk.anatomypro.core.data

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
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
/**
 * Adds the preference table.
 *
 * Written out rather than falling back to a destructive migration: nothing has shipped
 * yet, but the habit of dropping user data to add a column is one that only gets noticed
 * after it has cost someone their verification work (spec §7).
 */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `preference` " +
                "(`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))"
        )
    }
}

fun RoomDatabase.Builder<AnatomyDatabase>.openAnatomyDatabase(): AnatomyDatabase =
    addMigrations(MIGRATION_1_2)
        .setDriver(BundledSQLiteDriver())
        // Dispatchers.IO is not in the common source set; Default is the portable
        // equivalent here, and queries are short.
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
