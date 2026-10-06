package com.ptk.anatomypro.core.data

/**
 * Writes a downloaded pack's metadata into the database.
 *
 * One transaction per pack: a half-installed pack would leave structures whose meshes the
 * renderer cannot resolve, which surfaces to a user as a structure that cannot be tapped
 * rather than as an error.
 */
class PackInstaller(private val database: AnatomyDatabase) {

    suspend fun install(manifestJson: String, version: Long, meshUri: String?) {
        val rows = PackIngest.parse(manifestJson, version, meshUri)
        database.structures().replacePackContent(
            pack = rows.pack,
            structures = rows.structures,
            text = rows.text,
            definitions = rows.definitions,
            synonyms = rows.synonyms,
            meshRefs = rows.meshRefs,
            searchRows = rows.search,
        )
    }
}
