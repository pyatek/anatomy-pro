package com.ptk.anatomypro.core.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Opens a database written by an older version of the schema.
 *
 * The old file is built by hand from the exported schema rather than by Room, because Room
 * can only create the current version. Opening it afterwards runs the real migration and
 * Room's own check of the result against the entities.
 */
class MigrationTest {

    @Test
    fun a_version_2_database_opens_with_definitions_moved_out_and_names_rehashed() = runTest {
        val path = NSTemporaryDirectory() + "anatomy-migration-${NSUUID().UUIDString}.db"
        BundledSQLiteDriver().open(path).use { old ->
            VERSION_2_SCHEMA.forEach(old::execSQL)
            old.execSQL(
                "INSERT INTO structure VALUES " +
                    "('1168-clavicula-left', '1168', NULL, 'skeletal-system', 'trunk', 'L', 'skeletal-trunk', 0)"
            )
            // Version 2 copied the English definition onto every locale's row and hashed
            // it together with the name.
            old.execSQL(
                "INSERT INTO structure_text VALUES " +
                    "('1168-clavicula-left', 'en', 'Clavicle', 'The collarbone.', 'aaaaaaaa'), " +
                    "('1168-clavicula-left', 'la', 'Clavicula', 'The collarbone.', 'bbbbbbbb')"
            )
            old.execSQL(
                "INSERT INTO structure_verification VALUES " +
                    "('1168-clavicula-left', 'en', 'VERIFIED', 1000, 'aaaaaaaa')"
            )
            old.execSQL("PRAGMA user_version = 2")
        }

        val database = Room.databaseBuilder<AnatomyDatabase>(name = path).openAnatomyDatabase()
        try {
            val dao = database.structures()

            assertEquals("The collarbone.", assertNotNull(dao.definition("1168-clavicula-left", "en")).text)
            assertNull(dao.definition("1168-clavicula-left", "la"), "English text was kept as a Latin definition")

            val english = assertNotNull(dao.text("1168-clavicula-left", "en"))
            assertEquals("Clavicle", english.name)
            assertEquals(PackIngest.contentHash("Clavicle"), english.contentHash)
            assertEquals("Clavicula", assertNotNull(dao.text("1168-clavicula-left", "la")).name)

            // Approved against the old hash, which covered the definition: stale, not lost.
            assertNull(dao.currentVerification("1168-clavicula-left", "en"))
            assertNotNull(dao.anyVerification("1168-clavicula-left", "en"))
        } finally {
            database.close()
        }
    }
}

/** `schemas/…/2.json`, as the statements Room would have run. */
private val VERSION_2_SCHEMA = listOf(
    "CREATE TABLE IF NOT EXISTS `structure` (`id` TEXT NOT NULL, `taCode` TEXT, `parentId` TEXT, `systemId` TEXT, `regionId` TEXT, `laterality` TEXT NOT NULL, `packId` TEXT NOT NULL, `isGroup` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_structure_parentId` ON `structure` (`parentId`)",
    "CREATE INDEX IF NOT EXISTS `index_structure_systemId` ON `structure` (`systemId`)",
    "CREATE INDEX IF NOT EXISTS `index_structure_regionId` ON `structure` (`regionId`)",
    "CREATE INDEX IF NOT EXISTS `index_structure_packId` ON `structure` (`packId`)",
    "CREATE TABLE IF NOT EXISTS `structure_text` (`structureId` TEXT NOT NULL, `locale` TEXT NOT NULL, `name` TEXT NOT NULL, `definition` TEXT, `contentHash` TEXT NOT NULL, PRIMARY KEY(`structureId`, `locale`))",
    "CREATE TABLE IF NOT EXISTS `structure_synonym` (`structureId` TEXT NOT NULL, `locale` TEXT NOT NULL, `synonym` TEXT NOT NULL, PRIMARY KEY(`structureId`, `locale`, `synonym`))",
    "CREATE TABLE IF NOT EXISTS `mesh_ref` (`structureId` TEXT NOT NULL, `packId` TEXT NOT NULL, `nodeName` TEXT NOT NULL, PRIMARY KEY(`structureId`, `packId`, `nodeName`))",
    "CREATE INDEX IF NOT EXISTS `index_mesh_ref_packId` ON `mesh_ref` (`packId`)",
    "CREATE TABLE IF NOT EXISTS `structure_verification` (`structureId` TEXT NOT NULL, `locale` TEXT NOT NULL, `state` TEXT NOT NULL, `verifiedAt` INTEGER NOT NULL, `verifiedTextHash` TEXT NOT NULL, PRIMARY KEY(`structureId`, `locale`))",
    "CREATE TABLE IF NOT EXISTS `pack` (`id` TEXT NOT NULL, `version` INTEGER NOT NULL, `installedAt` INTEGER, `meshUri` TEXT, PRIMARY KEY(`id`))",
    "CREATE TABLE IF NOT EXISTS `structure_search` (`structureId` TEXT NOT NULL, `locale` TEXT NOT NULL, `normalised` TEXT NOT NULL, PRIMARY KEY(`structureId`, `locale`, `normalised`))",
    "CREATE INDEX IF NOT EXISTS `index_structure_search_normalised` ON `structure_search` (`normalised`)",
    "CREATE INDEX IF NOT EXISTS `index_structure_search_locale` ON `structure_search` (`locale`)",
    "CREATE TABLE IF NOT EXISTS `preference` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))",
    "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
    "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '64a96561178f3f72621352a8671a5109')",
)
