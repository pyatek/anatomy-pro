package com.ptk.anatomypro.core.data.quiz

import com.ptk.anatomypro.core.data.model.Difficulty
import com.ptk.anatomypro.core.data.model.QuizFormat
import com.ptk.anatomypro.core.data.model.QuizQuestion
import com.ptk.anatomypro.core.data.model.QuizTopic
import com.ptk.anatomypro.core.data.model.StructureSummary
import com.ptk.anatomypro.core.model.Laterality
import com.ptk.anatomypro.core.model.QuizQuestionId
import com.ptk.anatomypro.core.model.QuizTopicId
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.SystemId
import kotlin.random.Random

/** How many options a "name the highlighted structure" question offers (spec §8). */
const val QUIZ_OPTION_COUNT = 4

/**
 * One structure as the quiz sees it: where it sits in the graph, what it is called in the
 * examination locale, and whether that name has been reviewed.
 *
 * [nameLocale] is the language [name] is really in, which is Latin when the examination
 * locale has no translation. [verified] is about that name in that language: it is what
 * the student is shown, so it is what §7 has to have checked.
 */
data class QuizNode(
    val id: StructureId,
    val parentId: StructureId?,
    val systemId: String?,
    val regionId: String?,
    val laterality: Laterality,
    val isGroup: Boolean,
    val name: String,
    val nameLocale: String,
    val latinName: String?,
    val verified: Boolean,
)

/**
 * §8's generator: questions from the structure graph, with nothing authored.
 *
 * Pure, and built from a snapshot of the atlas, so everything §15 asks to be tested — tiers,
 * sides, the verified-only gate, a seeded set — is tested without a database.
 *
 * **§7 is applied here and nowhere else can undo it.** A structure whose name is not
 * verified is never an expected answer and never an option: a wrong name among the options
 * teaches as surely as a wrong answer.
 *
 * A left and a right structure share a name. The question names no side, so they are one
 * answer: a session asks about each name once, no two options read the same, and either
 * side answers a question about the other.
 */
class QuizGraph(nodes: List<QuizNode>) {

    // Sorted, so a seeded set does not depend on the order the atlas happened to list in.
    private val sorted = nodes.sortedBy { it.id.value }
    private val byId = sorted.associateBy { it.id }
    private val childrenOf = sorted.filter { it.parentId != null }.groupBy { it.parentId!! }
    private val askable = sorted.filter { !it.isGroup && it.verified }

    /** Every group with enough verified answers under it to fill a question. */
    fun topics(): List<QuizTopic> = sorted.filter { it.isGroup }.mapNotNull { group ->
        val answers = answersIn(group.id)
        val names = answers.distinctBy { it.name }.size
        if (names < QUIZ_OPTION_COUNT) return@mapNotNull null
        // A group's own system is empty in real packs; its children carry it.
        val system = answers.firstNotNullOfOrNull { it.systemId } ?: return@mapNotNull null
        // A structure id is not always a slug, and a topic id must be one.
        val id = runCatching { QuizTopicId(group.id.value) }.getOrNull() ?: return@mapNotNull null
        // Mastery is the progress store's to report; until one exists a topic is untouched.
        QuizTopic(id, group.name, SystemId(system), structureCount = names, mastery = 0f)
    }

    private fun answersIn(group: StructureId): List<QuizNode> =
        childrenOf[group].orEmpty().filter { !it.isGroup && it.verified }

    /**
     * [questionCount] questions about [topic], or as many as it has names for.
     *
     * Seeded and reproducible (§8.2): the same graph, topic, format, count and seed give the
     * same questions on every platform.
     */
    fun questions(topic: QuizTopicId, format: QuizFormat, questionCount: Int, seed: Long): List<QuizQuestion> {
        val sides = answersIn(StructureId(topic.value)).groupBy { it.name }
        require(sides.size >= QUIZ_OPTION_COUNT) {
            "topic ${topic.value} has ${sides.size} verified answers, needs $QUIZ_OPTION_COUNT"
        }

        val random = Random(seed)
        val names = sides.keys.sorted().shuffled(random).take(questionCount.coerceIn(0, sides.size))
        // One side per name, chosen by the seed: which rib VII is drawn is not the question.
        val targets = names.map { name -> sides.getValue(name).let { it[random.nextInt(it.size)] } }

        return targets.mapIndexed { index, target ->
            // The seed is printed unsigned: ids are slugs, and a negative seed would put "--" in one.
            val id = QuizQuestionId("q-${seed.toULong()}-$index")
            when (format) {
                // Nothing is offered, so nothing is near or far: finding it is recall.
                QuizFormat.TAP_THE_STRUCTURE -> QuizQuestion.TapTheStructure(
                    id = id,
                    difficulty = Difficulty.HARD,
                    prompt = target.name,
                    promptLocale = target.nameLocale,
                    target = target.id,
                )

                QuizFormat.NAME_THE_HIGHLIGHTED -> {
                    // A session climbs: the first third easy, the last third hard.
                    val wanted = Difficulty.entries[index * Difficulty.entries.size / targets.size]
                    val (difficulty, distractors) = distractorsFor(target, wanted, random)
                    val options = (distractors + target).shuffled(random).map(::summaryOf)
                    QuizQuestion.NameTheHighlighted(
                        id = id,
                        difficulty = difficulty,
                        highlighted = target.id,
                        options = options,
                        correctIndex = options.indexOfFirst { it.id == target.id },
                    )
                }
            }
        }
    }

    /**
     * §8.1: difficulty is how close the wrong options sit to the right one.
     *
     * A tier that cannot fill a question gives way to the nearest one that can, and the
     * question is labelled with the tier it was really drawn from. Siblings always can: a
     * topic is a group with at least [QUIZ_OPTION_COUNT] answers.
     */
    private fun distractorsFor(target: QuizNode, wanted: Difficulty, random: Random): Pair<Difficulty, List<QuizNode>> {
        val order = when (wanted) {
            Difficulty.EASY -> listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)
            Difficulty.MEDIUM -> listOf(Difficulty.MEDIUM, Difficulty.HARD, Difficulty.EASY)
            Difficulty.HARD -> listOf(Difficulty.HARD, Difficulty.MEDIUM, Difficulty.EASY)
        }
        for (tier in order) {
            val candidates = tierOf(target, tier)
            if (candidates.size >= QUIZ_OPTION_COUNT - 1) {
                return tier to candidates.shuffled(random).take(QUIZ_OPTION_COUNT - 1)
            }
        }
        error("no tier can fill a question about ${target.id.value}; its group should not be a topic")
    }

    /** Wrong answers at one distance from [target], one per name and never its own. */
    private fun tierOf(target: QuizNode, tier: Difficulty): List<QuizNode> = askable
        .filter { it.id != target.id && it.name != target.name }
        .filter {
            when (tier) {
                Difficulty.EASY -> it.systemId != target.systemId
                // The same system as well as the same region: a muscle of the trunk among
                // ribs is told apart at a glance, which is what easy means.
                Difficulty.MEDIUM ->
                    it.systemId == target.systemId && it.regionId != null && it.regionId == target.regionId &&
                        it.parentId != target.parentId
                Difficulty.HARD -> it.parentId == target.parentId
            }
        }
        .distinctBy { it.name }

    /**
     * Whether [chosen] answers a question that expects [expected].
     *
     * The same structure does, and so does its mirror: the same name under the same parent.
     * The same name under another group is a different structure.
     */
    fun sameAnswer(expected: StructureId, chosen: StructureId): Boolean {
        val one = byId[expected] ?: return false
        if (one.isGroup) return false
        if (expected == chosen) return true
        val other = byId[chosen] ?: return false
        return !other.isGroup && one.name == other.name && one.parentId == other.parentId
    }

    fun summary(id: StructureId): StructureSummary? = byId[id]?.let(::summaryOf)

    /** The lowest group both structures sit under — what screen 12 explains. */
    fun sharedAncestor(a: StructureId, b: StructureId): StructureSummary? {
        val aboveA = ancestorsOf(a).toSet()
        return ancestorsOf(b).firstOrNull { it in aboveA }?.let(::summary)
    }

    /** The groups above [id], nearest first. Bounded: the taxonomy is data, not a constraint. */
    private fun ancestorsOf(id: StructureId): List<StructureId> {
        val chain = mutableListOf<StructureId>()
        var current = byId[id]?.parentId
        while (current != null && current !in chain && chain.size < MAX_DEPTH) {
            chain += current
            current = byId[current]?.parentId
        }
        return chain
    }

    private fun summaryOf(node: QuizNode) = StructureSummary(
        id = node.id,
        name = node.name,
        latinName = node.latinName,
        laterality = node.laterality,
        isGroup = node.isGroup,
        hasChildren = node.id in childrenOf,
    )

    private companion object {
        const val MAX_DEPTH = 32
    }
}
