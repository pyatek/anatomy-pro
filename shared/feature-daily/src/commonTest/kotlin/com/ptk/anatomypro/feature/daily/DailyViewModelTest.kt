package com.ptk.anatomypro.feature.daily

import com.ptk.anatomypro.core.data.fake.FakeBehaviour
import com.ptk.anatomypro.core.data.fake.FakeDailyRepository
import com.ptk.anatomypro.core.data.fake.FakeEntitlementRepository
import com.ptk.anatomypro.core.data.fake.FakeQuizRepository
import com.ptk.anatomypro.core.data.fake.FakeSettingsRepository
import com.ptk.anatomypro.core.data.model.AppSettings
import com.ptk.anatomypro.core.data.model.DailyResult
import com.ptk.anatomypro.core.data.model.Entitlements
import com.ptk.anatomypro.core.data.model.QuizAnswer
import com.ptk.anatomypro.core.data.model.QuizQuestion
import com.ptk.anatomypro.core.data.model.SubscriptionPlan
import com.ptk.anatomypro.core.data.repository.DailyRepository
import com.ptk.anatomypro.core.model.StructureId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DailyViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val subscriber = Entitlements(subscribed = true, ownedSystems = emptySet())

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun model(
        daily: DailyRepository = FakeDailyRepository(),
        entitlements: FakeEntitlementRepository = FakeEntitlementRepository(initial = subscriber),
        settings: AppSettings = AppSettings(),
    ) = DailyViewModel(
        daily, entitlements, FakeQuizRepository(), FakeSettingsRepository(settings),
        now = { dispatcher.scheduler.currentTime },
    )

    private fun QuizQuestion.right(): StructureId = when (this) {
        is QuizQuestion.NameTheHighlighted -> correct.id
        is QuizQuestion.TapTheStructure -> target
    }

    /** Starts the daily with its first question on screen and old enough to be answered. */
    private fun TestScope.playing(model: DailyViewModel): DailyStage.Playing {
        runCurrent()
        model.start(locale = "la")
        runCurrent()
        model.onQuestionShown()
        advanceTimeBy(MIN_ANSWER_MILLIS)
        runCurrent()
        return assertIs<DailyStage.Playing>(model.stage.value)
    }

    /** Answers the question on screen rightly, then waits until the next may be answered. */
    private fun TestScope.answerRight(model: DailyViewModel) {
        val playing = assertIs<DailyStage.Playing>(model.stage.value)
        model.answer(playing.asking.question.right())
        advanceTimeBy(MIN_ANSWER_MILLIS)
        runCurrent()
    }

    // --- §15.3: not subscribed --------------------------------------------------------

    @Test
    fun a_free_user_is_told_they_are_not_subscribed_and_the_server_is_never_asked() = runTest(dispatcher) {
        var asked = 0
        val counting = object : DailyRepository by FakeDailyRepository() {
            override suspend fun today() = FakeDailyRepository().today().also { asked++ }
        }

        val model = model(daily = counting, entitlements = FakeEntitlementRepository())
        runCurrent()

        assertEquals(DailyStage.NotSubscribed, model.stage.value)
        assertEquals(0, asked)
    }

    @Test
    fun subscribing_while_the_screen_is_open_shows_the_daily() = runTest(dispatcher) {
        // Review Focus 4.
        val entitlements = FakeEntitlementRepository()
        val model = model(entitlements = entitlements)
        runCurrent()

        entitlements.purchase(SubscriptionPlan("monthly", "Miesięcznie", "29 zł", periodMonths = 1))
        runCurrent()

        assertIs<DailyStage.Ready>(model.stage.value)
    }

    // --- §9, screens 14 and 15 --------------------------------------------------------

    @Test
    fun a_day_not_yet_played_is_ready_and_says_how_many_questions() = runTest(dispatcher) {
        val model = model()
        runCurrent()

        assertEquals(10, assertIs<DailyStage.Ready>(model.stage.value).questionCount)
    }

    @Test
    fun a_day_already_played_shows_its_result() = runTest(dispatcher) {
        val model = model(daily = FakeDailyRepository(alreadyAttempted = true))
        runCurrent()

        val result = assertIs<DailyStage.Done>(model.stage.value).result
        assertEquals(7, result.correct)
        assertEquals(412, result.rank)
    }

    @Test
    fun offline_is_a_state_and_offers_practice_on_something_the_student_studies() = runTest(dispatcher) {
        // §14: block entry with a clear message; offer practice mode.
        val model = model(
            daily = FakeDailyRepository(available = false),
            settings = AppSettings(studiedSystems = setOf("muscular-system")),
        )
        runCurrent()

        val offline = assertIs<DailyStage.Offline>(model.stage.value)
        assertEquals("musculi-thoracis", offline.practice?.id?.value)
    }

    @Test
    fun a_day_that_cannot_be_read_is_a_failure_that_can_be_retried() = runTest(dispatcher) {
        var broken = true
        val real = FakeDailyRepository()
        val flaky = object : DailyRepository by real {
            override suspend fun today() = if (broken) throw IllegalStateException("no answer") else real.today()
        }
        val model = model(daily = flaky)
        runCurrent()
        assertEquals(DailyStage.Failed(DailyFailure.LOAD), model.stage.value)

        broken = false
        model.retry()
        runCurrent()

        assertIs<DailyStage.Ready>(model.stage.value)
    }

    // --- playing ----------------------------------------------------------------------

    @Test
    fun starting_shows_the_first_question_with_no_time_limit() = runTest(dispatcher) {
        // Decision 2: the server times the attempt; there is no second clock.
        val playing = playing(model())

        assertEquals(0, playing.asking.index)
        assertEquals(10, playing.asking.total)
        assertNull(playing.asking.remainingMillis)
    }

    @Test
    fun an_answer_moves_straight_to_the_next_question_with_no_verdict() = runTest(dispatcher) {
        // §9.1: answer keys never leave the server, so there is nothing to say yet.
        val model = model()
        playing(model)

        answerRight(model)

        assertEquals(1, assertIs<DailyStage.Playing>(model.stage.value).asking.index)
    }

    @Test
    fun a_second_tap_straight_after_an_answer_does_not_answer_the_next_question() = runTest(dispatcher) {
        // Review Focus 1.
        val model = model()
        val first = playing(model)

        model.answer(first.asking.question.right())
        model.answer(first.asking.question.right())
        runCurrent()

        assertEquals(1, assertIs<DailyStage.Playing>(model.stage.value).asking.index)
    }

    @Test
    fun the_last_answer_submits_them_all_and_shows_the_result() = runTest(dispatcher) {
        val model = model()
        playing(model)

        repeat(10) { answerRight(model) }
        runCurrent()

        val result = assertIs<DailyStage.Done>(model.stage.value).result
        assertEquals(10, result.correct)
        assertEquals(10, result.total)
    }

    @Test
    fun each_answer_carries_how_long_its_question_was_on_screen() = runTest(dispatcher) {
        var submitted: List<QuizAnswer> = emptyList()
        val real = FakeDailyRepository()
        val recording = object : DailyRepository by real {
            override suspend fun submit(answers: List<QuizAnswer>): DailyResult {
                submitted = answers
                return real.submit(answers)
            }
        }
        val model = model(daily = recording)
        playing(model)
        advanceTimeBy(2_000)
        runCurrent()

        repeat(10) { answerRight(model) }
        runCurrent()

        assertEquals(MIN_ANSWER_MILLIS + 2_000, submitted.first().elapsedMillis)
        assertEquals(MIN_ANSWER_MILLIS, submitted[1].elapsedMillis)
    }

    @Test
    fun the_clock_does_not_run_while_the_question_is_off_screen() = runTest(dispatcher) {
        var submitted: List<QuizAnswer> = emptyList()
        val real = FakeDailyRepository()
        val recording = object : DailyRepository by real {
            override suspend fun submit(answers: List<QuizAnswer>): DailyResult {
                submitted = answers
                return real.submit(answers)
            }
        }
        val model = model(daily = recording)
        playing(model)

        model.onQuestionHidden()
        advanceTimeBy(60_000)
        model.onQuestionShown()
        runCurrent()
        repeat(10) { answerRight(model) }
        runCurrent()

        assertEquals(MIN_ANSWER_MILLIS, submitted.first().elapsedMillis)
    }

    @Test
    fun a_submit_that_fails_keeps_the_answers_and_sends_them_again_on_retry() = runTest(dispatcher) {
        // Review Focus 2; design §14: "Submit fails after play: retry".
        var failNext = true
        var received = 0
        val real = FakeDailyRepository()
        val flaky = object : DailyRepository by real {
            override suspend fun submit(answers: List<QuizAnswer>): DailyResult {
                if (failNext) { failNext = false; throw IllegalStateException("connection dropped") }
                received = answers.size
                return real.submit(answers)
            }
        }
        val model = model(daily = flaky)
        playing(model)
        repeat(10) { answerRight(model) }
        runCurrent()
        assertEquals(DailyStage.Failed(DailyFailure.SUBMIT), model.stage.value)

        model.retry()
        runCurrent()

        assertEquals(10, received)
        assertEquals(10, assertIs<DailyStage.Done>(model.stage.value).result.correct)
    }

    @Test
    fun a_start_that_fails_says_so_and_retrying_reads_the_day_again() = runTest(dispatcher) {
        // Review Focus 3: the connection went between the lobby and the start.
        var failNext = true
        val real = FakeDailyRepository()
        val flaky = object : DailyRepository by real {
            override suspend fun start(locale: String) =
                if (failNext) { failNext = false; throw IllegalStateException("offline") } else real.start(locale)
        }
        val model = model(daily = flaky)
        runCurrent()

        model.start(locale = "la")
        runCurrent()
        assertEquals(DailyStage.Failed(DailyFailure.START), model.stage.value)

        model.retry()
        runCurrent()
        assertIs<DailyStage.Ready>(model.stage.value)
    }

    @Test
    fun a_day_found_already_in_progress_is_resumed_from_its_first_question() = runTest(dispatcher) {
        // The app was closed mid-attempt. The server's clock has kept running.
        val daily = FakeDailyRepository()
        daily.start(locale = "la")

        val model = model(daily = daily)
        runCurrent()

        assertEquals(0, assertIs<DailyStage.Playing>(model.stage.value).asking.index)
    }

    @Test
    fun the_first_question_is_named_in_the_locale_the_daily_was_started_in() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        model.start(locale = "en")
        runCurrent()

        val question = assertIs<DailyStage.Playing>(model.stage.value).asking.question as QuizQuestion.NameTheHighlighted
        assertEquals(true, question.options.all { it.name.startsWith("Rib ") })
    }

    @Test
    fun reading_the_day_again_after_a_failed_submit_does_not_throw_the_answers_away() = runTest(dispatcher) {
        // Going back to the home screen reads the day again. The server still says "in
        // progress", and treating that as a fresh start replayed the one attempt of the day.
        var failNext = true
        var received = 0
        val real = FakeDailyRepository()
        val flaky = object : DailyRepository by real {
            override suspend fun submit(answers: List<QuizAnswer>): DailyResult {
                if (failNext) { failNext = false; throw IllegalStateException("connection dropped") }
                received = answers.size
                return real.submit(answers)
            }
        }
        val model = model(daily = flaky)
        playing(model)
        repeat(10) { answerRight(model) }
        runCurrent()

        model.refresh()
        runCurrent()
        assertEquals(DailyStage.Failed(DailyFailure.SUBMIT), model.stage.value)

        model.retry()
        runCurrent()
        assertEquals(10, received)
    }

    @Test
    fun an_answer_counts_while_the_model_has_not_been_drawn_yet() = runTest(dispatcher) {
        // The option cards are on screen and can be pressed before the canvas is ready. The
        // guard against a double tap used the canvas's clock, so those presses were dropped
        // without a word — for good, if the model never came.
        val model = model()
        runCurrent()
        model.start(locale = "la")
        runCurrent()
        advanceTimeBy(MIN_ANSWER_MILLIS)

        val first = assertIs<DailyStage.Playing>(model.stage.value)
        model.answer(first.asking.question.right())
        runCurrent()

        assertEquals(1, assertIs<DailyStage.Playing>(model.stage.value).asking.index)
    }
}
