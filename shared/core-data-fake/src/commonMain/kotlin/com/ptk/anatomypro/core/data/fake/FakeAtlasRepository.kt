package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.PackIngest
import com.ptk.anatomypro.core.data.model.SearchHit
import com.ptk.anatomypro.core.data.model.StructureDetail
import com.ptk.anatomypro.core.data.model.StructureSummary
import com.ptk.anatomypro.core.data.repository.AtlasRepository
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.SystemId

/**
 * The atlas, in memory, over [AtlasFixture].
 *
 * [queries] replaces the recording stub each feature test used to carry: proving a
 * debounce needs to know what was asked, and every caller wanted the same list.
 * [childrenCalls] does the same for the atlas tree, which must not re-query on every toggle.
 */
class FakeAtlasRepository(
    private val behaviour: FakeBehaviour = FakeBehaviour(),
) : AtlasRepository {

    private val _queries = mutableListOf<String>()
    val queries: List<String> get() = _queries.toList()

    var childrenCalls: Int = 0
        private set

    override suspend fun roots(locale: String): List<StructureSummary> = behaviour.respond {
        AtlasFixture.roots().map { AtlasFixture.toSummary(it, locale) }
    }

    override suspend fun children(parent: StructureId, locale: String): List<StructureSummary> {
        childrenCalls++
        return behaviour.respond {
            AtlasFixture.childrenOf(parent).map { AtlasFixture.toSummary(it, locale) }
        }
    }

    override suspend fun summary(id: StructureId, locale: String): StructureSummary? =
        behaviour.respond { AtlasFixture.summary(id, locale) }

    override suspend fun detail(id: StructureId, locale: String): StructureDetail? =
        behaviour.respond { AtlasFixture.detail(id, locale) }

    /**
     * Mirrors RoomAtlasRepository.search rather than approximating it, so screen 06 is not
     * designed around results the real atlas cannot produce: a prefix match over lowercased,
     * accent-folded names in every language, the shortest match first, one hit per
     * structure, and the summary in Latin with the matched language reported beside it.
     */
    override suspend fun search(query: String, limit: Int): List<SearchHit> {
        _queries += query
        return behaviour.respond {
            val normalised = PackIngest.normalise(query)
            if (normalised.isEmpty()) return@respond emptyList()

            AtlasFixture.all
                .flatMap { structure ->
                    structure.names.mapNotNull { (locale, name) ->
                        val term = PackIngest.normalise(name)
                        if (term.startsWith(normalised)) Triple(structure, locale, term) else null
                    }
                }
                .sortedWith(compareBy({ it.third.length }, { it.first.id.value }))
                .distinctBy { it.first.id }
                .take(limit)
                .map { (structure, locale, _) ->
                    SearchHit(AtlasFixture.toSummary(structure, LATIN), locale)
                }
        }
    }

    private companion object {
        const val LATIN = "la"
    }

    override suspend fun systems(): List<SystemId> = behaviour.respond {
        AtlasFixture.all.map { it.system }.distinct().map(::SystemId)
    }

    override suspend fun structuresIn(system: SystemId): Set<StructureId> = behaviour.respond {
        AtlasFixture.all.filter { it.system == system.value }.mapTo(mutableSetOf()) { it.id }
    }

    override suspend fun allStructures(): Set<StructureId> = behaviour.respond {
        AtlasFixture.all.mapTo(mutableSetOf()) { it.id }
    }
}
