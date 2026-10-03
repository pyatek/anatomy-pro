package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.model.AnswerResult
import com.ptk.anatomypro.core.data.model.Difficulty
import com.ptk.anatomypro.core.data.model.QuizAnswer
import com.ptk.anatomypro.core.data.model.QuizFormat
import com.ptk.anatomypro.core.data.model.QuizQuestion
import com.ptk.anatomypro.core.data.model.QuizSession
import com.ptk.anatomypro.core.data.model.QuizSummary
import com.ptk.anatomypro.core.data.model.QuizTopic
import com.ptk.anatomypro.core.data.model.StructureSummary
import com.ptk.anatomypro.core.data.repository.QuizRepository
import com.ptk.anatomypro.core.model.QuizQuestionId
import com.ptk.anatomypro.core.model.QuizSessionId
import com.ptk.anatomypro.core.model.QuizTopicId
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.SystemId
import com.ptk.anatomypro.core.model.VerificationState
import kotlin.random.Random

/**
 * Canned questions over [AtlasFixture].
 *
 * This is deliberately not §8.1's generator: difficulty here is a label on a question, not
 * a computed distractor distance. Building the real generator behind a screen would hide a
 * piece of work that deserves its own tests. What this fake does honour is §7's gate —
 * an UNVERIFIED structure is never an expected answer — because the screens must never
 * learn a habit the real generator cannot keep.
 */
class FakeQuizRepository(
    private val behaviour: FakeBehaviour = FakeBehaviour(),
) : QuizRepository {

    private val sessions = mutableMapOf<QuizSessionId, QuizSession>()
    private val submitted = mutableMapOf<QuizSessionId, MutableList<AnswerResult>>()
    private var nextSession = 0

    /** §7: only VERIFIED structures may be answers. Groups are not answers either. */
    private fun answerable(topic: QuizTopicId): List<FixtureStructure> =
        AtlasFixture.childrenOf(StructureId(topic.value))
            .filter { !it.isGroup && it.verification["la"] == VerificationState.VERIFIED }

    override suspend fun topics(locale: String): List<QuizTopic> = behaviour.respond {
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
    }

    override suspend fun startSession(
        topic: QuizTopicId,
        format: QuizFormat,
        questionCount: Int,
        seed: Long,
        locale: String,
    ): QuizSession = behaviour.respond {
        val pool = answerable(topic)
        require(pool.size >= OPTION_COUNT) {
            "topic ${topic.value} has ${pool.size} verified answerable structures, needs $OPTION_COUNT"
        }

        val random = Random(seed)
        val targets = pool.shuffled(random).take(questionCount.coerceAtMost(pool.size))

        val questions = targets.mapIndexed { index, target ->
            val distractors = pool.filter { it.id != target.id }.shuffled(random).take(OPTION_COUNT - 1)
            val options = (distractors + target).shuffled(random).map { AtlasFixture.toSummary(it, locale) }
            // The seed is printed unsigned: ids are slugs, and a negative seed would put "--" in one.
            val id = QuizQuestionId("q-${seed.toULong()}-$index")

            when (format) {
                QuizFormat.NAME_THE_HIGHLIGHTED -> QuizQuestion.NameTheHighlighted(
                    id = id,
                    difficulty = Difficulty.HARD,
                    highlighted = target.id,
                    options = options,
                    correctIndex = options.indexOfFirst { it.id == target.id },
                )
                QuizFormat.TAP_THE_STRUCTURE -> QuizQuestion.TapTheStructure(
                    id = id,
                    difficulty = Difficulty.HARD,
                    prompt = AtlasFixture.nameOf(target, locale),
                    promptLocale = if (locale in target.names) locale else "la",
                    target = target.id,
                )
            }
        }

        val session = QuizSession(
            id = QuizSessionId("session-${nextSession++}"),
            topic = topic,
            format = format,
            questions = questions,
            seed = seed,
            locale = locale,
        )
        sessions[session.id] = session
        submitted[session.id] = mutableListOf()
        session
    }

    override suspend fun submit(session: QuizSessionId, answer: QuizAnswer): AnswerResult =
        behaviour.respond {
            val held = requireNotNull(sessions[session]) { "unknown session ${session.value}" }
            val question = held.questions.first { it.id == answer.questionId }
            val expectedId = when (question) {
                is QuizQuestion.NameTheHighlighted -> question.correct.id
                is QuizQuestion.TapTheStructure -> question.target
            }

            val result = AnswerResult(
                questionId = answer.questionId,
                correct = answer.chosen == expectedId,
                expected = requireNotNull(AtlasFixture.summary(expectedId, held.locale)),
                chosen = answer.chosen?.let { AtlasFixture.summary(it, held.locale) },
                sharedAncestor = answer.chosen?.let { sharedAncestorOf(expectedId, it, held.locale) },
                elapsedMillis = answer.elapsedMillis,
            )
            submitted.getValue(session) += result
            result
        }

    override suspend fun finish(session: QuizSessionId): QuizSummary = behaviour.respond {
        val results = submitted[session].orEmpty()
        QuizSummary(
            session = session,
            correct = results.count { it.correct },
            total = results.size,
            elapsedMillis = results.sumOf { it.elapsedMillis },
            // Slowest miss first: the one that cost the most time is the one to revisit.
            needsReview = results.filterNot { it.correct }
                .sortedByDescending { it.elapsedMillis }
                .map { it.expected },
        )
    }

    /** The lowest group both structures sit under — what screen 12 explains. */
    private fun sharedAncestorOf(a: StructureId, b: StructureId, locale: String): StructureSummary? {
        val chainOfA = AtlasFixture.ancestorsOf(a, locale).map { it.id }.toSet()
        return AtlasFixture.ancestorsOf(b, locale).lastOrNull { it.id in chainOfA }
    }

    private companion object {
        /** Screen 10 offers four options (spec §8). */
        const val OPTION_COUNT = 4
    }
}
