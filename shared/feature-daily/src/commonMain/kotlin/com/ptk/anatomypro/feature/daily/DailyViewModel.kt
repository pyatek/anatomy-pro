package com.ptk.anatomypro.feature.daily

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptk.anatomypro.core.data.model.DailyQuiz
import com.ptk.anatomypro.core.data.model.DailyResult
import com.ptk.anatomypro.core.data.model.Entitlements
import com.ptk.anatomypro.core.data.model.QuizAnswer
import com.ptk.anatomypro.core.data.model.QuizFormat
import com.ptk.anatomypro.core.data.model.QuizQuestion
import com.ptk.anatomypro.core.data.model.QuizSession
import com.ptk.anatomypro.core.data.model.QuizTopic
import com.ptk.anatomypro.core.data.repository.DailyRepository
import com.ptk.anatomypro.core.data.repository.EntitlementRepository
import com.ptk.anatomypro.core.data.repository.QuizRepository
import com.ptk.anatomypro.core.data.repository.SettingsRepository
import com.ptk.anatomypro.core.model.QuizSessionId
import com.ptk.anatomypro.core.model.QuizTopicId
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.feature.quiz.QuizStage
import com.ptk.anatomypro.feature.quiz.monotonicMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * How long a question must have been on screen before an answer counts.
 *
 * The daily shows nothing between questions, so the tap that answers one lands on the next
 * a moment later. A second tap inside this window is that tap again, not an answer.
 */
const val MIN_ANSWER_MILLIS = 300L

enum class DailyFailure { LOAD, START, SUBMIT }

/** Where today's quiz is. Screens 14 and 15 are both pictures of this. */
sealed interface DailyStage {
    data object Loading : DailyStage

    /** A free user. Designed, not an error: it has its own copy and a way to the paywall. */
    data object NotSubscribed : DailyStage

    data class Ready(val date: LocalDate, val questionCount: Int) : DailyStage

    /**
     * [asking] is what the quiz's question screen draws. It never carries a time limit:
     * the server times the whole attempt (design spec §9.1).
     */
    data class Playing(val asking: QuizStage.Asking) : DailyStage

    data object Submitting : DailyStage

    data class Done(val result: DailyResult) : DailyStage

    /** §14. [practice] is the topic practice mode would use, or null when there is none. */
    data class Offline(val practice: QuizTopic?) : DailyStage

    data class Failed(val during: DailyFailure) : DailyStage
}

/**
 * Today's quiz, from the lobby to its result.
 *
 * App-scoped: the home screen and the lobby show the same day, and a question left for
 * another tab is still there on the way back.
 *
 * There is no feedback between questions because there is nothing to give: answer keys
 * never reach the device (§9.1). Answers are collected and sent together.
 */
class DailyViewModel(
    private val daily: DailyRepository,
    private val entitlements: EntitlementRepository,
    private val quiz: QuizRepository,
    private val settings: SettingsRepository,
    private val now: () -> Long = monotonicMillis(),
) : ViewModel() {

    private val _stage = MutableStateFlow<DailyStage>(DailyStage.Loading)
    val stage: StateFlow<DailyStage> = _stage.asStateFlow()

    private var held = Entitlements(subscribed = false, ownedSystems = emptySet())
    private var locale = "la"
    private val answers = mutableListOf<QuizAnswer>()
    /** Time the current question had been on screen before it was last hidden. */
    private var elapsedBefore = 0L

    /** When the current question last came on screen, or null while it is off it. */
    private var shownAt: Long? = null
    private var shown = false

    init {
        viewModelScope.launch {
            entitlements.entitlements.collect { current ->
                val was = held.subscribed
                held = current
                when {
                    !current.subscribed -> {
                        stopClock()
                        _stage.value = DailyStage.NotSubscribed
                    }
                    // The first reading, or a subscription bought while the screen was open.
                    !was || _stage.value == DailyStage.Loading -> refresh()
                }
            }
        }
    }

    /** Reads the day again. A free user's device never asks. */
    fun refresh() {
        if (!held.subscribed) return
        if (_stage.value is DailyStage.Playing || _stage.value == DailyStage.Submitting) return
        _stage.value = DailyStage.Loading
        viewModelScope.launch {
            try {
                when (val today = daily.today()) {
                    is DailyQuiz.NotStarted -> _stage.value = DailyStage.Ready(today.date, today.questionCount)
                    is DailyQuiz.InProgress -> play(today)
                    is DailyQuiz.Completed -> _stage.value = DailyStage.Done(today.result)
                    DailyQuiz.Unavailable -> _stage.value = DailyStage.Offline(findPractice())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _stage.value = DailyStage.Failed(DailyFailure.LOAD)
            }
        }
    }

    /** [locale] is the examination locale the questions are named in (§13). */
    fun start(locale: String) {
        if (_stage.value !is DailyStage.Ready) return
        this.locale = locale
        _stage.value = DailyStage.Loading
        viewModelScope.launch {
            try {
                play(daily.start(locale))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _stage.value = DailyStage.Failed(DailyFailure.START)
            }
        }
    }

    fun onQuestionShown() {
        shown = true
        startClock()
    }

    fun onQuestionHidden() {
        shown = false
        stopClock()
    }

    fun answer(chosen: StructureId) {
        val playing = _stage.value as? DailyStage.Playing ?: return
        val elapsed = elapsed()
        if (elapsed < MIN_ANSWER_MILLIS) return
        val asking = playing.asking
        answers += QuizAnswer(asking.question.id, chosen, elapsed)
        elapsedBefore = 0
        shownAt = shownAt?.let { now() }
        if (asking.index == asking.session.questions.lastIndex) {
            submit()
        } else {
            _stage.value = DailyStage.Playing(asking.copy(index = asking.index + 1))
        }
    }

    /** After a failure: read the day again, or send the answers again. */
    fun retry() {
        when ((_stage.value as? DailyStage.Failed)?.during) {
            DailyFailure.SUBMIT -> submit()
            DailyFailure.LOAD, DailyFailure.START -> {
                _stage.value = DailyStage.Loading
                refresh()
            }
            null -> Unit
        }
    }

    private fun play(started: DailyQuiz.InProgress) {
        answers.clear()
        elapsedBefore = 0
        shownAt = null
        if (started.questions.isEmpty()) {
            submit()
            return
        }
        // Dressed as a quiz session so the quiz's question screen and canvas can draw it.
        val session = QuizSession(
            id = QuizSessionId("daily-${started.date}"),
            topic = QuizTopicId("daily"),
            format = when (started.questions.first()) {
                is QuizQuestion.NameTheHighlighted -> QuizFormat.NAME_THE_HIGHLIGHTED
                is QuizQuestion.TapTheStructure -> QuizFormat.TAP_THE_STRUCTURE
            },
            questions = started.questions,
            seed = 0,
            locale = locale,
        )
        _stage.value = DailyStage.Playing(QuizStage.Asking(session, index = 0, remainingMillis = null))
        if (shown) startClock()
    }

    private fun submit() {
        stopClock()
        _stage.value = DailyStage.Submitting
        viewModelScope.launch {
            try {
                _stage.value = DailyStage.Done(daily.submit(answers.toList()))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The answers are kept. §14: retry, do not replay.
                _stage.value = DailyStage.Failed(DailyFailure.SUBMIT)
            }
        }
    }

    private suspend fun findPractice(): QuizTopic? = try {
        val current = settings.settings.first()
        practiceTopic(quiz.topics(current.interfaceLocale), current.studiedSystems, held)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    // Elapsed time is read from the clock, as the quiz reads it (§34.3), not counted in
    // ticks: a loop that never ends is one a test can never run to idle.
    private fun elapsed(): Long = elapsedBefore + (shownAt?.let { now() - it } ?: 0L)

    private fun startClock() {
        if (shownAt != null || _stage.value !is DailyStage.Playing) return
        shownAt = now()
    }

    private fun stopClock() {
        shownAt?.let { elapsedBefore += now() - it }
        shownAt = null
    }
}
