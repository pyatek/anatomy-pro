package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.model.StructureId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FakeAtlasRepositoryTest {

    private val repository = FakeAtlasRepository()

    @Test
    fun roots_are_the_structures_with_no_parent() = runTest {
        assertEquals(listOf("skeletal", "muscular"), repository.roots("pl").map { it.id.value })
    }

    @Test
    fun the_fixture_has_two_systems_so_a_toggle_can_be_exercised() = runTest {
        assertEquals(listOf("skeletal-system", "muscular-system"), repository.systems().map { it.value })
    }

    @Test
    fun a_system_contains_its_groups_and_its_leaves() = runTest {
        val muscles = repository.structuresIn(com.ptk.anatomypro.core.model.SystemId("muscular-system"))

        assertTrue(StructureId("musculi-thoracis") in muscles)
        assertTrue(StructureId("musculus-pectoralis-major") in muscles)
        assertTrue(StructureId("costa-vii") !in muscles)
    }

    @Test
    fun every_structure_is_in_the_atlas_wide_set() = runTest {
        assertEquals(AtlasFixture.all.size, repository.allStructures().size)
    }

    @Test
    fun a_name_comes_back_in_the_requested_locale() = runTest {
        assertEquals("Żebra", repository.summary(StructureId("costae"), "pl")?.name)
        assertEquals("Ribs", repository.summary(StructureId("costae"), "en")?.name)
    }

    @Test
    fun an_unknown_locale_falls_back_to_latin_because_latin_is_the_canonical_key() = runTest {
        assertEquals("Costae", repository.summary(StructureId("costae"), "de")?.name)
    }

    @Test
    fun search_matches_every_language_at_once_and_reports_which_one_hit() = runTest {
        val polish = repository.search("Żebro VII")
        val english = repository.search("Rib VII")

        assertEquals("costa-vii" to "pl", polish.first().let { it.summary.id.value to it.matchedLocale })
        assertEquals("costa-vii" to "en", english.first().let { it.summary.id.value to it.matchedLocale })
    }

    @Test
    fun search_is_a_prefix_match_ranked_shortest_first_like_the_real_atlas() = runTest {
        // "Rib VII" is also the start of "Rib VIII". The real search returns both, exact
        // term first, so the fake must too — and must not match from the middle of a name.
        assertEquals(listOf("costa-vii", "costa-viii"), repository.search("Rib VII").map { it.summary.id.value })
        assertEquals(emptyList(), repository.search("VII"))
    }

    @Test
    fun search_folds_accents_so_a_query_typed_without_them_still_matches() = runTest {
        assertEquals("costa-i", repository.search("zebro i").first().summary.id.value)
    }

    @Test
    fun a_search_hit_is_summarised_in_latin_with_the_matched_language_beside_it() = runTest {
        assertEquals("Costa VII", repository.search("Rib VII").first().summary.name)
    }

    @Test
    fun search_records_what_it_was_asked_so_a_debounce_can_be_proven() = runTest {
        repository.search("costa")

        assertEquals(listOf("costa"), repository.queries)
    }

    @Test
    fun children_calls_are_counted_so_a_cache_can_be_proven() = runTest {
        repository.children(StructureId("skeletal"), "pl")
        repository.children(StructureId("costae"), "pl")

        assertEquals(2, repository.childrenCalls)
    }

    @Test
    fun an_injected_failure_surfaces_so_error_states_can_be_driven() = runTest {
        val broken = FakeAtlasRepository(FakeBehaviour(failure = { IllegalStateException("no database") }))

        assertFailsWith<IllegalStateException> { broken.roots("pl") }
    }

    @Test
    fun a_detail_is_null_for_a_structure_that_does_not_exist() = runTest {
        assertTrue(repository.detail(StructureId("nonexistent"), "pl") == null)
    }
}
