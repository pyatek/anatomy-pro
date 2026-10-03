package com.ptk.anatomypro.core.data.repository

import com.ptk.anatomypro.core.data.model.SearchHit
import com.ptk.anatomypro.core.data.model.StructureDetail
import com.ptk.anatomypro.core.data.model.StructureSummary
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.SystemId

/**
 * Reading the atlas: the taxonomy, and the structures in it.
 *
 * An interface so screens can be driven by a fake without a database, which is the same
 * reason `FakeAnatomyRenderer` exists for the renderer (spec §15).
 */
interface AtlasRepository {

    /** Top of the taxonomy — the structures with no parent. */
    suspend fun roots(locale: String): List<StructureSummary>

    suspend fun children(parent: StructureId, locale: String): List<StructureSummary>

    suspend fun summary(id: StructureId, locale: String): StructureSummary?

    /** Names in every language, the definition, and the chain of groups above it. */
    suspend fun detail(id: StructureId, locale: String): StructureDetail?

    /** Searches every language at once; each hit reports which one matched. */
    suspend fun search(query: String, limit: Int = 50): List<SearchHit>

    /** The systems the installed atlas has structures in, by id (screen 07). */
    suspend fun systems(): List<SystemId>

    /** Every structure in [system], groups included: hiding a group hides nothing extra. */
    suspend fun structuresIn(system: SystemId): Set<StructureId>

    /** Every structure in the atlas: what isolation hides everything else of. */
    suspend fun allStructures(): Set<StructureId>
}
