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

/**
 * Moves definitions out of the name rows, and narrows the content hash to the name.
 *
 * The hash is FNV-1a computed in Kotlin, which SQL cannot reproduce, so every name row is
 * read back and rehashed here. Verifications recorded against the old hash stop matching
 * and read as stale: that is the safe direction, and none exist outside tests.
 *
 * Only the English row's definition is kept. The Latin row carried a copy of the same
 * English text.
 */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `structure_definition` " +
                "(`structureId` TEXT NOT NULL, `locale` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                "`sourceUrl` TEXT, `licence` TEXT, PRIMARY KEY(`structureId`, `locale`))"
        )
        connection.execSQL(
            "INSERT INTO `structure_definition` (`structureId`, `locale`, `text`, `sourceUrl`, `licence`) " +
                "SELECT `structureId`, `locale`, `definition`, NULL, NULL FROM `structure_text` " +
                "WHERE `locale` = '${PackIngest.LOCALE_ENGLISH}' AND `definition` IS NOT NULL"
        )
        connection.execSQL("ALTER TABLE `structure_text` DROP COLUMN `definition`")

        val names = mutableListOf<Triple<String, String, String>>()
        connection.prepare("SELECT `structureId`, `locale`, `name` FROM `structure_text`").use { rows ->
            while (rows.step()) names += Triple(rows.getText(0), rows.getText(1), rows.getText(2))
        }
        connection.prepare(
            "UPDATE `structure_text` SET `contentHash` = ? WHERE `structureId` = ? AND `locale` = ?"
        ).use { update ->
            for ((structureId, locale, name) in names) {
                update.bindText(1, PackIngest.contentHash(name))
                update.bindText(2, structureId)
                update.bindText(3, locale)
                update.step()
                update.reset()
            }
        }
    }
}

fun RoomDatabase.Builder<AnatomyDatabase>.openAnatomyDatabase(): AnatomyDatabase =
    addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .setDriver(BundledSQLiteDriver())
        // Dispatchers.IO is not in the common source set; Default is the portable
        // equivalent here, and queries are short.
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
