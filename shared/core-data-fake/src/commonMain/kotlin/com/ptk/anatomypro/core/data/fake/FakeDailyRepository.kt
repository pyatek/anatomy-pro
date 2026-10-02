package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.model.DailyQuiz
import com.ptk.anatomypro.core.data.model.DailyResult
import com.ptk.anatomypro.core.data.model.QuizAnswer
import com.ptk.anatomypro.core.data.model.QuizFormat
import com.ptk.anatomypro.core.data.model.QuizQuestion
import com.ptk.anatomypro.core.data.repository.DailyRepository
import com.ptk.anatomypro.core.model.QuizTopicId
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * The daily quiz, played against a server that does not exist.
 *
 * [available] drives §14's offline row: the daily requires a connection to start and to
 * submit, and offline play is practice mode and never ranked. Keeping that as a
 * constructor flag rather than an injected exception means screen 15's offline state is
 * something you switch on, not something you have to break the app to see.
 */
class FakeDailyRepository(
    private val behaviour: FakeBehaviour = FakeBehaviour(),
    private val available: Boolean = true,
    alreadyAttempted: Boolean = false,
    private val date: LocalDate = LocalDate(2026, 9, 24),
    private val quiz: FakeQuizRepository = FakeQuizRepository(),
) : DailyRepository {

    private var state: DailyQuiz =
        if (alreadyAttempted) {
            DailyQuiz.Completed(date, DailyResult(date, correct = 7, total = 10, elapsedMillis = 64_000, rank = 412, totalPlayers = 5_180))
        } else {
            DailyQuiz.NotStarted(date, questionCount = QUESTION_COUNT)
        }

    override suspend fun today(): DailyQuiz = behaviour.respond {
        if (!available) DailyQuiz.Unavailable else state
    }

    override suspend fun start(): DailyQuiz.InProgress = behaviour.respond {
        check(available) { "the daily quiz requires a connection (§14)" }
        check(state is DailyQuiz.NotStarted) { "one attempt per day (§9.1)" }

        val session = quiz.startSession(
            topic = QuizTopicId("costae"),
            format = QuizFormat.NAME_THE_HIGHLIGHTED,
            questionCount = QUESTION_COUNT,
            // Seeded on the date: everyone gets the same set today (§9.1).
            seed = date.toEpochDays(),
        )
        val started = DailyQuiz.InProgress(date, session.questions, startedAt = CLOCK_START)
        state = started
        started
    }

    override suspend fun submit(answers: List<QuizAnswer>): DailyResult = behaviour.respond {
        check(available) { "the daily quiz requires a connection to submit (§14)" }
        val inProgress = state as? DailyQuiz.InProgress ?: error("the daily quiz has not been started")

        val correct = answers.count { answer ->
            val question = inProgress.questions.first { it.id == answer.questionId }
            val expected = when (question) {
                is QuizQuestion.NameTheHighlighted -> question.correct.id
                is QuizQuestion.TapTheStructure -> question.target
            }
            answer.chosen == expected
        }

        val result = DailyResult(
            date = date,
            correct = correct,
            total = answers.size,
            elapsedMillis = answers.sumOf { it.elapsedMillis },
            // Better scores rank higher; the arithmetic only has to be plausible.
            rank = (TOTAL_PLAYERS - correct * 400).coerceAtLeast(1),
            totalPlayers = TOTAL_PLAYERS,
        )
        state = DailyQuiz.Completed(date, result)
        result
    }

    private companion object {
        const val QUESTION_COUNT = 10
        const val TOTAL_PLAYERS = 5_180
        val CLOCK_START = Instant.fromEpochSeconds(1_790_000_000)
    }
}
