package com.ptk.anatomypro.core.data.quiz

import com.ptk.anatomypro.core.data.AnatomyDatabase
import com.ptk.anatomypro.core.data.entity.StructureEntity
import com.ptk.anatomypro.core.data.entity.StructureTextEntity
import com.ptk.anatomypro.core.model.Laterality
import com.ptk.anatomypro.core.model.StructureId

/** The canonical language, and the one a name falls back to (spec §13). */
private const val LATIN = "la"

/** The rows of this database, read by the rule both ends share ([quizNodesOf]). */
fun quizNodes(
    structures: List<StructureEntity>,
    texts: List<StructureTextEntity>,
    verified: Set<Pair<String, String>>,
    locale: String,
): List<QuizNode> = quizNodesOf(
    structures = structures.map { QuizStructureRow(it.id, it.parentId, it.systemId, it.regionId, it.laterality, it.isGroup) },
    names = texts.map { QuizNameRow(it.structureId, it.locale, it.name) },
    verified = verified,
    locale = locale,
)

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
