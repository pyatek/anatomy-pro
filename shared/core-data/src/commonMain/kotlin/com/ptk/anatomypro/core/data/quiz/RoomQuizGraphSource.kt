package com.ptk.anatomypro.core.data.quiz

import com.ptk.anatomypro.core.data.AnatomyDatabase
import com.ptk.anatomypro.core.data.entity.StructureEntity
import com.ptk.anatomypro.core.data.entity.StructureTextEntity
import com.ptk.anatomypro.core.model.Laterality
import com.ptk.anatomypro.core.model.StructureId

/** The canonical language, and the one a name falls back to (spec §13). */
private const val LATIN = "la"

/**
 * The atlas as the quiz sees it, from rows.
 *
 * A structure is named in [locale], or in Latin where it has no such name, and one with
 * neither is left out: it cannot be asked about. [verified] holds the (structure, locale)
 * pairs whose review is current. A node is verified only by the pair for the language its
 * name is shown in — a reviewed Latin name says nothing about a Polish one (§7).
 */
fun quizNodes(
    structures: List<StructureEntity>,
    texts: List<StructureTextEntity>,
    verified: Set<Pair<String, String>>,
    locale: String,
): List<QuizNode> {
    val names = texts.groupBy { it.structureId }
    return structures.mapNotNull { structure ->
        val own = names[structure.id].orEmpty()
        val latin = own.firstOrNull { it.locale == LATIN }
        val shown = own.firstOrNull { it.locale == locale } ?: latin ?: return@mapNotNull null
        QuizNode(
            id = StructureId(structure.id),
            parentId = structure.parentId?.let(::StructureId),
            systemId = structure.systemId,
            regionId = structure.regionId,
            laterality = when (structure.laterality) {
                "L" -> Laterality.LEFT
                "R" -> Laterality.RIGHT
                else -> Laterality.MEDIAN
            },
            isGroup = structure.isGroup,
            name = shown.name,
            nameLocale = shown.locale,
            latinName = latin?.name,
            verified = (structure.id to shown.locale) in verified,
        )
    }
}

/** Three reads for the whole atlas, rather than a query per structure. */
class RoomQuizGraphSource(database: AnatomyDatabase) : QuizGraphSource {

    private val dao = database.structures()

    override suspend fun nodes(locale: String): List<QuizNode> = quizNodes(
        structures = dao.allStructures(),
        texts = dao.textsIn(listOf(locale, LATIN).distinct()),
        verified = dao.currentlyVerified().map { it.structureId to it.locale }.toSet(),
        locale = locale,
    )
}
