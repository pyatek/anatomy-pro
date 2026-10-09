package com.ptk.anatomypro.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Writes a downloaded pack's metadata into the database.
 *
 * One transaction per pack: a half-installed pack would leave structures whose meshes the
 * renderer cannot resolve, which surfaces to a user as a structure that cannot be tapped
 * rather than as an error.
 */
class PackInstaller(
    private val database: AnatomyDatabase,
    /**
     * Where the manifest is parsed. Not the caller's context: `install` is called from the
     * main thread at launch, and a megabyte of JSON for a thousand structures held the first
     * frame back by up to a second and a half there (design spec §40).
     */
    private val parseContext: CoroutineContext = Dispatchers.Default,
) {

    suspend fun install(manifestJson: String, version: Long, meshUri: String?) {
        val rows = withContext(parseContext) { PackIngest.parse(manifestJson, version, meshUri) }
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
