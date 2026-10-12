package com.ptk.anatomypro.core.data.quiz

import com.ptk.anatomypro.core.model.Laterality
import com.ptk.anatomypro.core.model.StructureId

/** The canonical language, and the one a name falls back to (spec §13). */
private const val LATIN = "la"

/** A structure as either end stores it. */
data class QuizStructureRow(
    val id: String,
    val parentId: String?,
    val systemId: String?,
    val regionId: String?,
    /** "L", "R", or anything else for a structure on the midline. */
    val laterality: String,
    val isGroup: Boolean,
)

data class QuizNameRow(val structureId: String, val locale: String, val name: String)

/**
 * The atlas as the quiz sees it, from rows.
 *
 * A structure is named in [locale], or in Latin where it has no such name, and one with
 * neither is left out: it cannot be asked about. [verified] holds the (structure, locale)
 * pairs whose review is current. A node is verified only by the pair for the language its
 * name is shown in — a reviewed Latin name says nothing about a Polish one (§7).
 */
fun quizNodesOf(
    structures: List<QuizStructureRow>,
    names: List<QuizNameRow>,
    verified: Set<Pair<String, String>>,
    locale: String,
): List<QuizNode> {
    val byStructure = names.groupBy { it.structureId }
    return structures.mapNotNull { structure ->
        val own = byStructure[structure.id].orEmpty()
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
