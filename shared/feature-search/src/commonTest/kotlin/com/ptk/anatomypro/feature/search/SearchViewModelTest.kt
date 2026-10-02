package com.ptk.anatomypro.feature.search

import com.ptk.anatomypro.core.data.fake.FakeAtlasRepository
import com.ptk.anatomypro.core.data.fake.FakeBehaviour
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
import kotlin.test.assertTrue

class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeAtlasRepository()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun typing_runs_one_query_rather_than_one_per_keystroke() = runTest(dispatcher) {
        val model = SearchViewModel(repository)

        "costa".forEachIndexed { index, _ -> model.onQueryChanged("costa".take(index + 1)) }
        advanceUntilIdle()

        assertEquals(listOf("costa"), repository.queries)
    }

    @Test
    fun results_carry_the_language_they_matched_in() = runTest(dispatcher) {
        val model = SearchViewModel(repository)
        model.onQueryChanged("Żebro VII")
        advanceUntilIdle()

        val best = model.state.value.hits.first()
        assertEquals("costa-vii", best.summary.id.value)
        assertEquals("pl", best.matchedLocale)
    }

    @Test
    fun clearing_the_query_empties_the_results_without_a_query() = runTest(dispatcher) {
        val model = SearchViewModel(repository)
        model.onQueryChanged("costa")
        advanceUntilIdle()
        val asked = repository.queries.size

        model.onQueryCleared()
        advanceUntilIdle()

        assertTrue(model.state.value.hits.isEmpty())
        assertEquals(asked, repository.queries.size, "a blank query still hit the database")
    }

    @Test
    fun opening_a_result_remembers_the_term_once_and_most_recent_first() = runTest(dispatcher) {
        val model = SearchViewModel(repository)

        model.onQueryChanged("costa"); advanceUntilIdle()
        model.onHitOpened(model.state.value.hits.first())
        model.onQueryChanged("vertebra"); advanceUntilIdle()
        model.onHitOpened(model.state.value.hits.first())
        model.onQueryChanged("costa"); advanceUntilIdle()
        model.onHitOpened(model.state.value.hits.first())

        assertEquals(listOf("costa", "vertebra"), model.state.value.recent)
    }

    @Test
    fun a_failing_repository_leaves_the_screen_usable() = runTest(dispatcher) {
        // Search failing is not worth an error screen; an empty result and a working field
        // lets the user try again, which is what they would do anyway.
        val broken = FakeAtlasRepository(FakeBehaviour(failure = { IllegalStateException("no database") }))
        val model = SearchViewModel(broken)

        model.onQueryChanged("costa")
        advanceUntilIdle()

        assertTrue(model.state.value.hits.isEmpty())
        assertEquals(false, model.state.value.isSearching)
    }
}
