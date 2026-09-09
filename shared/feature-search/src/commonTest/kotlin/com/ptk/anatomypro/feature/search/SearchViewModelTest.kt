package com.ptk.anatomypro.feature.search

import com.ptk.anatomypro.core.data.model.SearchHit
import com.ptk.anatomypro.core.data.model.StructureDetail
import com.ptk.anatomypro.core.data.model.StructureSummary
import com.ptk.anatomypro.core.data.repository.AtlasRepository
import com.ptk.anatomypro.core.model.Laterality
import com.ptk.anatomypro.core.model.StructureId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun hit(id: String, latin: String, locale: String) = SearchHit(
    StructureSummary(StructureId(id), latin, latin, Laterality.MEDIAN, false, false),
    locale,
)

private class RecordingRepository(private val results: List<SearchHit>) : AtlasRepository {
    val queries = mutableListOf<String>()
    override suspend fun roots(locale: String) = emptyList<StructureSummary>()
    override suspend fun children(parent: StructureId, locale: String) = emptyList<StructureSummary>()
    override suspend fun summary(id: StructureId, locale: String): StructureSummary? = null
    override suspend fun detail(id: StructureId, locale: String): StructureDetail? = null
    override suspend fun search(query: String, limit: Int): List<SearchHit> {
        queries += query
        return results
    }
}

class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val results = listOf(hit("1105-costae-median", "Costae", "la"))
    private val repository = RecordingRepository(results)

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
        model.onQueryChanged("costa")
        advanceUntilIdle()

        assertEquals("la", model.state.value.hits.single().matchedLocale)
    }

    @Test
    fun clearing_the_query_empties_the_results_without_a_query() = runTest(dispatcher) {
        val model = SearchViewModel(repository)
        model.onQueryChanged("costa")
        advanceUntilIdle()
        repository.queries.clear()

        model.onQueryCleared()
        advanceUntilIdle()

        assertTrue(model.state.value.hits.isEmpty())
        assertTrue(repository.queries.isEmpty(), "a blank query still hit the database")
    }

    @Test
    fun opening_a_result_remembers_the_term_once_and_most_recent_first() = runTest(dispatcher) {
        val model = SearchViewModel(repository)

        model.onQueryChanged("costa"); advanceUntilIdle()
        model.onHitOpened(results.first())
        model.onQueryChanged("sternum"); advanceUntilIdle()
        model.onHitOpened(results.first())
        model.onQueryChanged("costa"); advanceUntilIdle()
        model.onHitOpened(results.first())

        assertEquals(listOf("costa", "sternum"), model.state.value.recent)
    }

    @Test
    fun a_failing_repository_leaves_the_screen_usable() = runTest(dispatcher) {
        // Search failing is not worth an error screen; an empty result and a working field
        // lets the user try again, which is what they would do anyway.
        val broken = object : AtlasRepository by RecordingRepository(emptyList()) {
            override suspend fun search(query: String, limit: Int): List<SearchHit> = error("no database")
        }
        val model = SearchViewModel(broken)

        model.onQueryChanged("costa")
        advanceUntilIdle()

        assertTrue(model.state.value.hits.isEmpty())
        assertEquals(false, model.state.value.isSearching)
    }
}
