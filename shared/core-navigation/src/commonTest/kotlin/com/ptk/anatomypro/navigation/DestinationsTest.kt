package com.ptk.anatomypro.navigation

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class DestinationsTest {

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
