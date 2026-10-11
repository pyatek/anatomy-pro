package com.ptk.anatomypro.core.data.repository

import com.ptk.anatomypro.core.data.AnatomyDatabase
import com.ptk.anatomypro.core.data.PackInstaller
import com.ptk.anatomypro.core.model.PackId

/** A pack whose geometry is on the device. */
data class InstalledPack(val id: PackId, val version: Long, val meshUri: String)

/**
 * What is installed, as the database has it.
 *
 * The database is where a pack becomes visible: the renderer is only ever handed a mesh
 * that a row here points at, so writing that row is the last step of an install and
 * clearing it the first step of a removal.
 */
interface InstalledPacks {
    suspend fun all(): List<InstalledPack>

    /** Reads [manifestJson] into the atlas and records that the pack's mesh is at [meshUri]. */
    suspend fun install(manifestJson: String, version: Long, meshUri: String)

    /** Forgets where the mesh is. The pack's structures stay (design §24.3). */
    suspend fun uninstall(id: PackId)
}

class RoomInstalledPacks(
    database: AnatomyDatabase,
    private val installer: PackInstaller = PackInstaller(database),
) : InstalledPacks {
    private val dao = database.structures()

    override suspend fun all(): List<InstalledPack> = dao.packs().mapNotNull { row ->
        row.meshUri?.let { InstalledPack(PackId(row.id), row.version, it) }
    }

    override suspend fun install(manifestJson: String, version: Long, meshUri: String) =
        installer.install(manifestJson, version, meshUri)

    override suspend fun uninstall(id: PackId) = dao.forgetMeshOf(id.value)
}
