package com.ptk.anatomypro.feature.quiz

import com.ptk.anatomypro.core.data.fake.FakeBehaviour
import com.ptk.anatomypro.core.data.fake.FakeEntitlementRepository
import com.ptk.anatomypro.core.data.fake.FakeQuizRepository
import com.ptk.anatomypro.core.data.model.Entitlements
import com.ptk.anatomypro.core.data.model.QuizFormat
import com.ptk.anatomypro.core.data.model.SubscriptionPlan
import com.ptk.anatomypro.core.data.repository.QuizRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TopicsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun TopicsUiState.cell(id: String) = cells.first { it.topic.id.value == id }

    @Test
    fun an_untouched_topic_and_a_partly_mastered_one_are_told_apart() = runTest(dispatcher) {
        // §9: both states must exist. The fixture's cervical vertebrae are at zero, its ribs at 0.4.
        val model = TopicsViewModel(FakeQuizRepository(), FakeEntitlementRepository(), locale = "pl")
        advanceUntilIdle()

        assertTrue(model.state.value.cell("vertebrae-cervicales").untouched)
        assertFalse(model.state.value.cell("costae").untouched)
        assertEquals(0.4f, model.state.value.cell("costae").topic.mastery)
    }

    @Test
    fun a_free_user_finds_skeletal_topics_open_and_the_rest_locked_but_listed() = runTest(dispatcher) {
        // Design §29.2: skeleton quizzes are free, and a locked topic is shown, not hidden.
        val model = TopicsViewModel(FakeQuizRepository(), FakeEntitlementRepository(), locale = "pl")
        advanceUntilIdle()

        assertFalse(model.state.value.cell("costae").locked)
        assertTrue(model.state.value.cell("musculi-thoracis").locked)
        assertEquals(3, model.state.value.cells.size)
    }

    @Test
    fun a_subscriber_finds_nothing_locked() = runTest(dispatcher) {
        val subscribed = FakeEntitlementRepository(initial = Entitlements(subscribed = true, ownedSystems = emptySet()))

        val model = TopicsViewModel(FakeQuizRepository(), subscribed, locale = "pl")
        advanceUntilIdle()

        assertTrue(model.state.value.cells.none { it.locked })
    }

    @Test
    fun subscribing_while_the_grid_is_open_unlocks_it_without_reloading() = runTest(dispatcher) {
        val entitlements = FakeEntitlementRepository()
        val model = TopicsViewModel(FakeQuizRepository(), entitlements, locale = "pl")
        advanceUntilIdle()

        entitlements.purchase(SubscriptionPlan("monthly", "Miesięcznie", "29 zł", periodMonths = 1))
        advanceUntilIdle()

        assertFalse(model.state.value.cell("musculi-thoracis").locked)
    }

    @Test
    fun topics_are_titled_in_the_interface_language() = runTest(dispatcher) {
        val model = TopicsViewModel(FakeQuizRepository(), FakeEntitlementRepository(), locale = "en")
        advanceUntilIdle()

        assertEquals("Ribs", model.state.value.cell("costae").topic.title)
    }

    @Test
    fun the_format_is_the_students_choice_and_starts_on_naming() = runTest(dispatcher) {
        val model = TopicsViewModel(FakeQuizRepository(), FakeEntitlementRepository(), locale = "pl")
        advanceUntilIdle()
        assertEquals(QuizFormat.NAME_THE_HIGHLIGHTED, model.state.value.format)

        model.onFormat(QuizFormat.TAP_THE_STRUCTURE)

        assertEquals(QuizFormat.TAP_THE_STRUCTURE, model.state.value.format)
    }

    @Test
    fun topics_that_cannot_be_loaded_are_a_failure_that_can_be_retried() = runTest(dispatcher) {
        val quiz = FakeQuizRepository(FakeBehaviour(failure = { IllegalStateException("no topics") }))
        val model = TopicsViewModel(quiz, FakeEntitlementRepository(), locale = "pl")
        advanceUntilIdle()

        assertTrue(model.state.value.failed)
        assertFalse(model.state.value.isLoading)
        assertTrue(model.state.value.cells.isEmpty())
    }

    @Test
    fun retrying_after_a_failure_loads_the_topics() = runTest(dispatcher) {
        var attempts = 0
        val real = FakeQuizRepository()
        val flaky = object : QuizRepository by real {
            override suspend fun topics(locale: String) =
                if (attempts++ == 0) throw IllegalStateException("first try fails") else real.topics(locale)
        }
        val model = TopicsViewModel(flaky, FakeEntitlementRepository(), locale = "pl")
        advanceUntilIdle()
        assertTrue(model.state.value.failed)

        model.onRetry()
        advanceUntilIdle()

        assertFalse(model.state.value.failed)
        assertEquals(3, model.state.value.cells.size)
    }
}
