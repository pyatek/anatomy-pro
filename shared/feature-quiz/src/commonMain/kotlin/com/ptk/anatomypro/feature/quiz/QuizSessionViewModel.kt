package com.ptk.anatomypro.feature.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptk.anatomypro.core.data.model.AnswerResult
import com.ptk.anatomypro.core.data.model.QuizAnswer
import com.ptk.anatomypro.core.data.model.QuizFormat
import com.ptk.anatomypro.core.data.model.QuizQuestion
import com.ptk.anatomypro.core.data.model.QuizSession
import com.ptk.anatomypro.core.data.model.QuizSummary
import com.ptk.anatomypro.core.data.repository.ProgressRepository
import com.ptk.anatomypro.core.data.repository.QuizRepository
import com.ptk.anatomypro.core.model.QuizTopicId
import com.ptk.anatomypro.core.model.StructureId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.TimeSource

/** A session's length. Fewer when the topic has fewer structures to ask about. */
const val QUESTIONS_PER_SESSION = 10

/** How long a timed question allows. The timer can be turned off in settings (§12). */
const val QUESTION_MILLIS = 30_000L

/** How often a running countdown is redrawn. Elapsed time is read from the clock, not counted in ticks. */
const val TICK_MILLIS = 100L

/** A clock that only goes forwards, in milliseconds from when it was made. */
internal fun monotonicMillis(): () -> Long {
    val origin = TimeSource.Monotonic.markNow()
    return { origin.elapsedNow().inWholeMilliseconds }
}

enum class FailedDuring { START, FINISH }

/** Where a quiz session is. Every quiz screen is a picture of one of these. */
sealed interface QuizStage {

    data object Idle : QuizStage

    data object Starting : QuizStage

    /** Screens 09 and 10. */
    data class Asking(
        val session: QuizSession,
        val index: Int,
        /** Null when the timer is off: untimed, with no limit (§12). */
        val remainingMillis: Long?,
        val submitting: Boolean = false,
        /** The last answer could not be checked; the question is waiting to be answered again. */
        val submitFailed: Boolean = false,
    ) : QuizStage {
        val question: QuizQuestion get() = session.questions[index]
        val total: Int get() = session.questions.size
    }

    /** Screens 11 and 12. */
    data class Feedback(val session: QuizSession, val index: Int, val result: AnswerResult) : QuizStage {
        val isLast: Boolean get() = index == session.questions.lastIndex
    }

    /** Screen 13. [topic] and [format] are kept so the same test can be taken again. */
    data class Finished(val summary: QuizSummary, val topic: QuizTopicId, val format: QuizFormat) : QuizStage

    data class Failed(val during: FailedDuring) : QuizStage
}

/**
 * One quiz session, from its first question to its summary.
 *
 * App-scoped, so leaving the Test tab and returning finds the same question (all-screens
 * spec §7). Time counts only between [onQuestionShown] and [onQuestionHidden]: a question
 * that is not on screen is not being answered, and must not time out.
 *
 * Elapsed time is read from [now], not counted. An untimed question therefore runs nothing
 * in the background; a timed one runs a countdown that ends by itself when the time is up.
 */
class QuizSessionViewModel(
    private val quiz: QuizRepository,
    private val progress: ProgressRepository,
    private val nextSeed: () -> Long = { Random.nextLong() },
    private val now: () -> Long = monotonicMillis(),
) : ViewModel() {

    private val _stage = MutableStateFlow<QuizStage>(QuizStage.Idle)
    val stage: StateFlow<QuizStage> = _stage.asStateFlow()

    private var timed = false
    private var shown = false

    /** Time the current question had been on screen before it was last hidden. */
    private var elapsedBefore = 0L

    /** When the current question last came on screen, or null while it is off it. */
    private var shownAt: Long? = null
    private var countdown: Job? = null

    /**
     * The one piece of session work in flight: starting, checking an answer, or finishing.
     * Cancelled by [abandon] and by a new [start], so a result that arrives late cannot
     * undo either.
     */
    private var work: Job? = null

    /** Starts a session. A session already starting or under way is dropped: the latest start wins. */
    fun start(topic: QuizTopicId, format: QuizFormat, locale: String, timed: Boolean) {
        work?.cancel()
        stopCountdown()
        shownAt = null
        this.timed = timed
        _stage.value = QuizStage.Starting
        work = viewModelScope.launch {
            try {
                val session = quiz.startSession(topic, format, QUESTIONS_PER_SESSION, nextSeed(), locale)
                ensureActive()
                if (session.questions.isEmpty()) finish(session) else ask(session, index = 0)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ensureActive()
                // A topic too small to ask about, or anything else: the grid says so.
                _stage.value = QuizStage.Failed(FailedDuring.START)
            }
        }
    }

    /** The question is on screen: its time counts. */
    fun onQuestionShown() {
        shown = true
        resumeTiming()
    }

    /** The question has left the screen: its time stops where it is. */
    fun onQuestionHidden() {
        shown = false
        pauseTiming()
    }

    /** The student's answer. Ignored unless a question is waiting for one. */
    fun answer(chosen: StructureId) = submit(chosen)

    fun next() {
        val feedback = _stage.value as? QuizStage.Feedback ?: return
        if (!feedback.isLast) {
            ask(feedback.session, feedback.index + 1)
            return
        }
        // Already finishing: a second tap must not finish, or record, twice.
        if (work?.isActive == true) return
        work = viewModelScope.launch { finish(feedback.session) }
    }

    /** Ends the session without a summary. Nothing is recorded, and nothing in flight can undo it. */
    fun abandon() {
        work?.cancel()
        work = null
        stopCountdown()
        shownAt = null
        _stage.value = QuizStage.Idle
    }

    private fun ask(session: QuizSession, index: Int) {
        elapsedBefore = 0
        shownAt = null
        _stage.value = QuizStage.Asking(session, index, remainingMillis = if (timed) QUESTION_MILLIS else null)
        if (shown) resumeTiming()
    }

    private fun submit(chosen: StructureId?) {
        val asking = _stage.value as? QuizStage.Asking ?: return
        if (asking.submitting) return
        val elapsed = elapsed()
        pauseTiming()
        _stage.value = asking.copy(submitting = true, submitFailed = false)
        work = viewModelScope.launch {
            try {
                val result = quiz.submit(asking.session.id, QuizAnswer(asking.question.id, chosen, elapsed))
                ensureActive()
                _stage.value = QuizStage.Feedback(asking.session, asking.index, result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ensureActive()
                // The question is still there. A timed question gets its time back rather
                // than expiring on a failure the student did not cause.
                elapsedBefore = 0
                shownAt = null
                _stage.value = asking.copy(
                    submitting = false,
                    submitFailed = true,
                    remainingMillis = if (timed) QUESTION_MILLIS else null,
                )
                if (shown) resumeTiming()
            }
        }
    }

    private suspend fun finish(session: QuizSession) {
        val summary = try {
            quiz.finish(session.id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            _stage.value = QuizStage.Failed(FailedDuring.FINISH)
            return
        }
        currentCoroutineContext().ensureActive()
        _stage.value = QuizStage.Finished(summary, session.topic, session.format)

        // On its own, not part of the session's work: once the summary is showing the
        // session is finished, and leaving the summary must not cancel its record.
        viewModelScope.launch {
            try {
                progress.recordSession(summary)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A store that cannot be written is not something to interrupt the summary with.
            }
        }
    }

    /** How long the current question has been on screen in all. */
    private fun elapsed(): Long = elapsedBefore + (shownAt?.let { now() - it } ?: 0L)

    private fun resumeTiming() {
        val asking = _stage.value as? QuizStage.Asking ?: return
        if (asking.submitting) return
        if (shownAt == null) shownAt = now()
        if (timed) startCountdown()
    }

    private fun pauseTiming() {
        shownAt?.let { elapsedBefore += now() - it }
        shownAt = null
        stopCountdown()
    }

    /**
     * Redraws the time left, and answers with nothing when there is none. Only for a timed
     * question, and it always ends: at the latest when the time runs out.
     */
    private fun startCountdown() {
        if (countdown?.isActive == true) return
        countdown = viewModelScope.launch {
            while (true) {
                delay(TICK_MILLIS)
                val current = _stage.value as? QuizStage.Asking ?: return@launch
                if (current.submitting) return@launch
                val left = QUESTION_MILLIS - elapsed()
                if (left <= 0) {
                    // Out of time: answered with nothing. Cleared first, because submit()
                    // stops the countdown and this coroutine is the countdown.
                    countdown = null
                    submit(chosen = null)
                    return@launch
                }
                _stage.value = current.copy(remainingMillis = left)
            }
        }
    }

    private fun stopCountdown() {
        countdown?.cancel()
        countdown = null
    }
}
