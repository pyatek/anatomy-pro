package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.model.QuizTopic
import com.ptk.anatomypro.core.data.model.StructureSummary
import com.ptk.anatomypro.core.data.repository.AtlasRepository
import com.ptk.anatomypro.core.model.QuizTopicId
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.SystemId
import com.ptk.anatomypro.core.model.VerificationState

/** How many options screen 10 offers (spec §8), and so the fewest answers a topic needs. */
internal const val OPTION_COUNT = 4

/**
 * Where [FakeQuizRepository] gets its structures.
 *
 * Two sources, for two jobs. Tests want a small atlas they know by heart. A person running
 * the app wants questions about the model actually on screen, or nothing they tap can be
 * right and nothing the quiz highlights can be seen.
 */
interface QuizSource {
    suspend fun topics(locale: String): List<QuizTopic>

    /** Structures that may be an expected answer in [topic], named in [locale]. */
    suspend fun answerable(topic: QuizTopicId, locale: String): List<StructureSummary>

    suspend fun summary(id: StructureId, locale: String): StructureSummary?

    /** The groups above [id], root first. */
    suspend fun ancestors(id: StructureId, locale: String): List<StructureSummary>

    /** The locale [id]'s name is really in when asked for [locale]: Latin when untranslated. */
    suspend fun nameLocale(id: StructureId, locale: String): String

    /**
     * Whether [chosen] answers a question that expects [expected].
     *
     * The same structure always does. A source whose atlas draws a left and a right
     * structure under one name says its mirror does too: the question names no side, so
     * neither side can be the wrong one.
     */
    suspend fun sameAnswer(expected: StructureId, chosen: StructureId, locale: String): Boolean = expected == chosen
}

/**
 * The shared fixture, with §7's gate: an UNVERIFIED structure is never an answer.
 *
 * This is what every test uses, so the screens never learn a habit the real generator
 * cannot keep.
 */
object FixtureQuizSource : QuizSource {

    override suspend fun topics(locale: String): List<QuizTopic> =
        AtlasFixture.all.filter { it.isGroup && it.parent != null }.map { group ->
            QuizTopic(
                id = QuizTopicId(group.id.value),
                title = AtlasFixture.nameOf(group, locale),
                system = SystemId(group.system),
                structureCount = AtlasFixture.childrenOf(group.id).size,
                // A fixed spread so screen 08 shows untouched, partial and near-complete
                // cells without a progress store existing yet.
                mastery = when (group.id.value) {
                    "costae" -> 0.4f
                    "vertebrae-cervicales" -> 0f
                    else -> 0.8f
                },
            )
        }

    /** §7: only VERIFIED structures may be answers. Groups are not answers either. */
    override suspend fun answerable(topic: QuizTopicId, locale: String): List<StructureSummary> =
        AtlasFixture.childrenOf(StructureId(topic.value))
            .filter { !it.isGroup && it.verification["la"] == VerificationState.VERIFIED }
            .map { AtlasFixture.toSummary(it, locale) }

    override suspend fun summary(id: StructureId, locale: String): StructureSummary? =
        AtlasFixture.summary(id, locale)

    override suspend fun ancestors(id: StructureId, locale: String): List<StructureSummary> =
        AtlasFixture.ancestorsOf(id, locale)

    override suspend fun nameLocale(id: StructureId, locale: String): String =
        if (AtlasFixture.byId(id)?.names?.containsKey(locale) == true) locale else "la"
}

/**
 * A harness: questions about whatever atlas is installed, so a debug build's quiz matches
 * the model it draws.
 *
 * **It does not apply §7.** An installed atlas has no verified structures yet, so the gate
 * would leave nothing to ask. Everything drawable is treated as answerable, which is wrong
 * for a product and exactly right for looking at screens. It lives in the fake module and
 * cannot reach a release build.
 *
 * It is not §8.1's generator either: no difficulty, no distractor distance. A topic is any
 * group with enough drawable children to fill a question.
 */
class AtlasQuizSource(private val atlas: AtlasRepository) : QuizSource {

    override suspend fun topics(locale: String): List<QuizTopic> {
        val topics = mutableListOf<QuizTopic>()
        val seen = mutableSetOf<StructureId>()
        var level = atlas.roots(locale)
        var depth = 0
        // Bounded rather than trusting the data: the taxonomy comes from a pipeline, not a
        // constraint, and a cycle would otherwise hang the topic grid.
        while (level.isNotEmpty() && depth < MAX_DEPTH) {
            val next = mutableListOf<StructureSummary>()
            for (group in level) {
                if (!group.isGroup || !seen.add(group.id)) continue
                val children = atlas.children(group.id, locale)
                next += children.filter { it.isGroup }

                val answers = answersAmong(children)
                if (answers.size < OPTION_COUNT) continue
                // A group's own system is empty in real packs; its children carry it.
                val system = atlas.detail(answers.first().id, locale)?.systemId ?: continue
                val id = runCatching { QuizTopicId(group.id.value) }.getOrNull() ?: continue
                topics += QuizTopic(id, group.name, SystemId(system), structureCount = answers.size, mastery = 0f)
            }
            level = next
            depth++
        }
        return topics
    }

    override suspend fun answerable(topic: QuizTopicId, locale: String): List<StructureSummary> =
        answersAmong(atlas.children(StructureId(topic.value), locale))

    /**
     * Drawable children, one per name. A left and a right structure share a name, and two
     * options reading the same cannot both be offered.
     */
    private fun answersAmong(children: List<StructureSummary>): List<StructureSummary> =
        children.filter { !it.isGroup }.distinctBy { it.name }

    override suspend fun summary(id: StructureId, locale: String): StructureSummary? =
        atlas.summary(id, locale)

    override suspend fun ancestors(id: StructureId, locale: String): List<StructureSummary> =
        atlas.detail(id, locale)?.ancestors.orEmpty()

    override suspend fun nameLocale(id: StructureId, locale: String): String =
        if (atlas.detail(id, locale)?.names?.containsKey(locale) == true) locale else "la"

    /**
     * [answersAmong] offers one structure per name, but the model draws both sides, so the
     * mirror counts: the same name in [locale] under the same parent. The same name under
     * another group is a different structure.
     */
    override suspend fun sameAnswer(expected: StructureId, chosen: StructureId, locale: String): Boolean {
        if (expected == chosen) return true
        val one = atlas.detail(expected, locale) ?: return false
        val other = atlas.detail(chosen, locale) ?: return false
        if (one.isGroup || other.isGroup) return false
        return atlas.summary(expected, locale)?.name == atlas.summary(chosen, locale)?.name &&
            one.ancestors.lastOrNull()?.id == other.ancestors.lastOrNull()?.id
    }

    private companion object {
        const val MAX_DEPTH = 8
    }
}
