package com.ptk.anatomypro.navigation

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class DestinationsTest {

    @Test
    fun a_screen_that_belongs_to_no_tab_is_shown_under_the_tab_it_was_opened_from() {
        // The paywall opened from a locked topic sat on the Test tab's stack while the bar
        // showed Profile. Root first, as a back stack is: atlas, the Test tab, the paywall.
        assertEquals(TopLevel.Test, owningTab(listOf(TopLevel.Atlas, TopLevel.Test, null)))
        assertEquals(TopLevel.Profile, owningTab(listOf(TopLevel.Atlas, TopLevel.Profile, null)))
    }

    @Test
    fun a_screen_with_a_tab_of_its_own_is_shown_under_it() {
        assertEquals(TopLevel.Ranking, owningTab(listOf(TopLevel.Atlas, TopLevel.Today, TopLevel.Ranking)))
    }

    @Test
    fun an_empty_stack_or_one_of_tabless_screens_belongs_to_no_tab() {
        assertEquals(null, owningTab(emptyList()))
        assertEquals(null, owningTab(listOf(null, null)))
    }

    @Test
    fun there_are_five_top_level_tabs_in_the_prototypes_order() {
        assertEquals(
            listOf("Today", "Atlas", "Test", "Ranking", "Profile"),
            TopLevel.entries.map { it.name },
        )
    }

    @Test
    fun every_tab_has_its_own_start_route_so_each_keeps_its_own_back_stack() {
        val starts = TopLevel.entries.map { it.start }

        assertEquals(starts.distinct().size, starts.size)
    }

    @Test
    fun a_route_carrying_a_structure_id_round_trips() {
        val route = AtlasRoute.Detail("costa-vii")

        assertEquals(route, Json.decodeFromString(AtlasRoute.Detail.serializer(), Json.encodeToString(AtlasRoute.Detail.serializer(), route)))
    }

    @Test
    fun a_quiz_session_route_carries_both_the_session_and_the_question_index() {
        val route = QuizRoute.Question("session-0", index = 3)

        assertEquals(3, route.index)
        assertEquals("session-0", route.sessionId)
    }
}
