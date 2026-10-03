package com.ptk.anatomypro.core.data.repository

import com.ptk.anatomypro.core.data.AnatomyDatabase
import com.ptk.anatomypro.core.data.PackIngest
import com.ptk.anatomypro.core.data.entity.StructureEntity
import com.ptk.anatomypro.core.data.model.SearchHit
import com.ptk.anatomypro.core.data.model.StructureDetail
import com.ptk.anatomypro.core.data.model.StructureSummary
import com.ptk.anatomypro.core.model.Laterality
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.SystemId

class RoomAtlasRepository(private val database: AnatomyDatabase) : AtlasRepository {

    private val dao get() = database.structures()

    override suspend fun roots(locale: String): List<StructureSummary> =
        dao.roots().toSummaries(locale)

    override suspend fun children(parent: StructureId, locale: String): List<StructureSummary> =
        dao.children(parent.value).toSummaries(locale)

    override suspend fun summary(id: StructureId, locale: String): StructureSummary? =
        dao.structure(id.value)?.let { listOf(it).toSummaries(locale).firstOrNull() }

    override suspend fun detail(id: StructureId, locale: String): StructureDetail? {
        val entity = dao.structure(id.value) ?: return null
        val texts = dao.texts(id.value)
        val preferred = texts.firstOrNull { it.locale == locale }
            ?: texts.firstOrNull { it.locale == PackIngest.LOCALE_LATIN }

        return StructureDetail(
            id = StructureId(entity.id),
            names = texts.associate { it.locale to it.name },
            definition = preferred?.definition,
            definitionLocale = preferred?.locale,
            systemId = entity.systemId,
            regionId = entity.regionId,
            laterality = entity.laterality.toLaterality(),
            isGroup = entity.isGroup,
            ancestors = ancestorsOf(entity, locale),
        )
    }

    override suspend fun search(query: String, limit: Int): List<SearchHit> {
        val normalised = PackIngest.normalise(query)
        if (normalised.isEmpty()) return emptyList()

        // One row per (structure, locale) matched; the best hit per structure wins so a
        // term present in three languages does not fill the list with itself.
        val hits = dao.searchHits(normalised, limit * 3)
        val bestByStructure = LinkedHashMap<String, String>()
        for (hit in hits) if (hit.structureId !in bestByStructure) bestByStructure[hit.structureId] = hit.locale

        val summaries = bestByStructure.keys
            .mapNotNull { dao.structure(it) }
            .toSummaries(PackIngest.LOCALE_LATIN)
            .associateBy { it.id.value }

        return bestByStructure.entries.take(limit).mapNotNull { (id, matched) ->
            summaries[id]?.let { SearchHit(it, matched) }
        }
    }

    /**
     * The chain of groups above a structure, root first.
     *
     * Bounded rather than trusting the data: a cycle in `parentId` would otherwise hang
     * the screen, and the taxonomy is built by a pipeline, not by a constraint.
     */
    private suspend fun ancestorsOf(entity: StructureEntity, locale: String): List<StructureSummary> {
        val chain = mutableListOf<StructureEntity>()
        val seen = mutableSetOf(entity.id)
        var current = entity.parentId
        while (current != null && chain.size < MAX_DEPTH && seen.add(current)) {
            val parent = dao.structure(current) ?: break
            chain += parent
            current = parent.parentId
        }
        return chain.reversed().toSummaries(locale)
    }

    private fun String.toLaterality() = when (this) {
        "L" -> Laterality.LEFT
        "R" -> Laterality.RIGHT
        else -> Laterality.MEDIAN
    }

    private companion object {
        const val MAX_DEPTH = 16
    }

    /**
     * Names come from the requested locale, falling back to Latin.
     *
     * Latin is the canonical key (spec §13), so it is the one locale guaranteed present;
     * a display locale with no translation yet would otherwise render a blank row.
     */
    private suspend fun List<StructureEntity>.toSummaries(locale: String): List<StructureSummary> {
        if (isEmpty()) return emptyList()
        val parents = dao.parentsWithChildren().toSet()
        return map { entity ->
            val preferred = dao.text(entity.id, locale)
            val latin = dao.text(entity.id, PackIngest.LOCALE_LATIN)
            StructureSummary(
                id = StructureId(entity.id),
                name = preferred?.name ?: latin?.name ?: entity.id,
                latinName = latin?.name,
                laterality = entity.laterality.toLaterality(),
                isGroup = entity.isGroup,
                hasChildren = entity.id in parents,
            )
        }
    }

    override suspend fun systems(): List<SystemId> = dao.systems().map(::SystemId)

    override suspend fun structuresIn(system: SystemId): Set<StructureId> =
        dao.idsInSystem(system.value).mapTo(mutableSetOf(), ::StructureId)

    override suspend fun allStructures(): Set<StructureId> =
        dao.allIds().mapTo(mutableSetOf(), ::StructureId)
}
