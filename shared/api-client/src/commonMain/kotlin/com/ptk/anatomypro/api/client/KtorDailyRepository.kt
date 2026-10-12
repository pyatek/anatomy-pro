package com.ptk.anatomypro.api.client

import com.ptk.anatomypro.api.ApiErrorCode
import com.ptk.anatomypro.api.ApiPaths
import com.ptk.anatomypro.api.DailyAnswer
import com.ptk.anatomypro.api.DailyAttemptResponse
import com.ptk.anatomypro.api.DailyResultResponse
import com.ptk.anatomypro.api.DailyStartRequest
import com.ptk.anatomypro.api.DailyState
import com.ptk.anatomypro.api.DailyStateResponse
import com.ptk.anatomypro.api.DailySubmitRequest
import com.ptk.anatomypro.core.data.model.DailyQuiz
import com.ptk.anatomypro.core.data.model.DailyResult
import com.ptk.anatomypro.core.data.model.Difficulty
import com.ptk.anatomypro.core.data.model.QuizAnswer
import com.ptk.anatomypro.core.data.model.QuizQuestion
import com.ptk.anatomypro.core.data.model.StructureSummary
import com.ptk.anatomypro.core.data.repository.DailyRepository
import com.ptk.anatomypro.core.model.Laterality
import com.ptk.anatomypro.core.model.QuizQuestionId
import com.ptk.anatomypro.core.model.StructureId
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * The daily, from the server (backend spec §6).
 *
 * [timed] is the user's timer setting, read when a round starts: with the timer off the
 * attempt is scored and not ranked (§6.3).
 *
 * Failure, as §6.4 has it: a server that cannot be reached makes [today] `Unavailable`,
 * which is a designed state with practice on offer. [start] and [submit] throw instead —
 * their return types have no such state, and the screen keeps what it holds and offers the
 * request again. A refusal by the server ([ApiFailure]) is always thrown.
 */
class KtorDailyRepository(private val api: ApiClient, private val timed: suspend () -> Boolean) : DailyRepository {

    /** The server's day as last heard, and the day of the attempt in hand. */
    private var day: String? = null
    private var attemptDay: String? = null

    override suspend fun today(): DailyQuiz = try {
        state().toModel()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (refused: ApiFailure) {
        throw refused
    } catch (_: Exception) {
        DailyQuiz.Unavailable
    }

    private suspend fun state(): DailyStateResponse = api.get(ApiPaths.DAILY_TODAY, DailyStateResponse.serializer()).also {
        day = it.date
        if (it.attempt != null || it.result != null) attemptDay = it.date
    }

    override suspend fun start(locale: String): DailyQuiz.InProgress {
        val request = DailyStartRequest(locale, timed())
        suspend fun start(date: String) =
            api.post(ApiPaths.dailyStart(date), DailyStartRequest.serializer(), request, DailyAttemptResponse.serializer())

        val attempt = try {
            start(day ?: state().date)
        } catch (refused: ApiFailure) {
            // The day turned while the lobby was open. Start is what was asked for, so the
            // new day is started rather than the old one mourned.
            if (refused.code != ApiErrorCode.WRONG_DATE) throw refused
            start(state().date)
        }
        attemptDay = attempt.date
        return attempt.toModel()
    }

    override suspend fun submit(answers: List<QuizAnswer>): DailyResult {
        // The attempt's own day, not today: a submit that was retried across midnight is
        // still for the day it was played on.
        val date = attemptDay ?: state().date
        return api.post(
            ApiPaths.dailySubmit(date),
            DailySubmitRequest.serializer(),
            DailySubmitRequest(answers.map { DailyAnswer(it.questionId.value, it.chosen?.value) }),
            DailyResultResponse.serializer(),
        ).toModel()
    }

    private fun DailyStateResponse.toModel(): DailyQuiz = when (state) {
        DailyState.UNAVAILABLE -> DailyQuiz.Unavailable
        DailyState.NOT_STARTED -> DailyQuiz.NotStarted(LocalDate.parse(date), questionCount)
        DailyState.IN_PROGRESS -> attempt?.toModel() ?: DailyQuiz.Unavailable
        DailyState.COMPLETED -> result?.let { DailyQuiz.Completed(LocalDate.parse(date), it.toModel()) } ?: DailyQuiz.Unavailable
    }

    private fun DailyAttemptResponse.toModel() = DailyQuiz.InProgress(
        date = LocalDate.parse(date),
        startedAt = Instant.fromEpochMilliseconds(startedAtEpochMillis),
        questions = questions.map { question ->
            QuizQuestion.NameTheHighlighted(
                id = QuizQuestionId(question.id),
                // Not sent, and nothing in the daily shows it: every player gets the same set.
                difficulty = Difficulty.MEDIUM,
                highlighted = StructureId(question.highlighted),
                options = question.options.map { option ->
                    StructureSummary(
                        id = StructureId(option.id),
                        name = option.name,
                        latinName = option.latinName,
                        // The question names no side, so none is sent.
                        laterality = Laterality.MEDIAN,
                        isGroup = false,
                        hasChildren = false,
                    )
                },
                // The right option is the highlighted structure. The server does not say so
                // because the atlas on this device already does (backend spec §4.1).
                correctIndex = question.options.indexOfFirst { it.id == question.highlighted },
            )
        },
    )
}

internal fun DailyResultResponse.toModel() = DailyResult(
    date = LocalDate.parse(date),
    correct = correct,
    total = total,
    elapsedMillis = elapsedMillis,
    rank = rank,
    totalPlayers = totalPlayers,
)
