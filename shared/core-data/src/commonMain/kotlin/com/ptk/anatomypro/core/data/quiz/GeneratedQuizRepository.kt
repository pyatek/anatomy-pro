package com.ptk.anatomypro.core.data.quiz

import com.ptk.anatomypro.core.data.model.AnswerResult
import com.ptk.anatomypro.core.data.model.QuizAnswer
import com.ptk.anatomypro.core.data.model.QuizFormat
import com.ptk.anatomypro.core.data.model.QuizQuestion
import com.ptk.anatomypro.core.data.model.QuizSession
import com.ptk.anatomypro.core.data.model.QuizSummary
import com.ptk.anatomypro.core.data.model.QuizTopic
import com.ptk.anatomypro.core.data.repository.QuizRepository
import com.ptk.anatomypro.core.model.QuizSessionId
import com.ptk.anatomypro.core.model.QuizTopicId

/** Where the generator reads the atlas from: every named structure, as [locale] names it. */
fun interface QuizGraphSource {
    suspend fun nodes(locale: String): List<QuizNode>
}

/**
 * The real quiz: sessions generated on the device from the installed atlas (spec §8.2).
 *
 * The atlas is read once per call that needs it, so a pack installed or a name verified
 * since the last session is in the next one. A running session keeps the graph it was
 * generated from: its answers are judged against the atlas they were asked about.
 *
 * Sessions are held in memory. One that is lost with the process is started again, as the
 * session's ViewModel already assumes.
 */
class GeneratedQuizRepository(private val source: QuizGraphSource) : QuizRepository {

    private class Running(val session: QuizSession, val graph: QuizGraph) {
        val results = mutableListOf<AnswerResult>()
    }

    private val running = mutableMapOf<QuizSessionId, Running>()
    private var nextSession = 0

    override suspend fun topics(locale: String): List<QuizTopic> = QuizGraph(source.nodes(locale)).topics()

    override suspend fun startSession(
        topic: QuizTopicId,
        format: QuizFormat,
        questionCount: Int,
        seed: Long,
        locale: String,
    ): QuizSession {
        val graph = QuizGraph(source.nodes(locale))
        val session = QuizSession(
            id = QuizSessionId("session-${nextSession++}"),
            topic = topic,
            format = format,
            questions = graph.questions(topic, format, questionCount, seed),
            seed = seed,
            locale = locale,
        )
        running[session.id] = Running(session, graph)
        return session
    }

    override suspend fun submit(session: QuizSessionId, answer: QuizAnswer): AnswerResult {
        val held = requireNotNull(running[session]) { "unknown session ${session.value}" }
        val question = held.session.questions.first { it.id == answer.questionId }
        val expected = when (question) {
            is QuizQuestion.NameTheHighlighted -> question.correct.id
            is QuizQuestion.TapTheStructure -> question.target
        }

        val chosen = answer.chosen
        val correct = chosen != null && held.graph.sameAnswer(expected, chosen)
        // Right through the mirror: the model must mark what the student tapped as right,
        // not its twin, so that is the structure reported as expected.
        val shown = if (correct && chosen != null) chosen else expected

        val result = AnswerResult(
            questionId = answer.questionId,
            correct = correct,
            expected = requireNotNull(held.graph.summary(shown)),
            // A tap can land on a structure the atlas no longer names; then there is a
            // wrong answer and nothing to call it.
            chosen = chosen?.let(held.graph::summary),
            sharedAncestor = chosen?.let { held.graph.sharedAncestor(expected, it) },
            elapsedMillis = answer.elapsedMillis,
        )
        held.results += result
        return result
    }

    override suspend fun finish(session: QuizSessionId): QuizSummary {
        val results = requireNotNull(running.remove(session)) { "unknown session ${session.value}" }.results
        return QuizSummary(
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
}
