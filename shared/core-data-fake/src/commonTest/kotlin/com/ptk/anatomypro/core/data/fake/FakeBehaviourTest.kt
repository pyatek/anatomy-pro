package com.ptk.anatomypro.core.data.fake

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds

class FakeBehaviourTest {

    @Test
    fun the_default_behaviour_answers_immediately() = runTest {
        assertEquals("ok", FakeBehaviour().respond { "ok" })
    }

    @OptIn(ExperimentalCoroutinesApi::class) // currentTime is the only way to observe virtual time
    @Test
    fun an_injected_delay_is_awaited_so_a_loading_state_can_be_seen() = runTest {
        val behaviour = FakeBehaviour(delay = 500.milliseconds)

        val before = currentTime
        behaviour.respond { "ok" }

        assertEquals(500, currentTime - before)
    }

    @Test
    fun an_injected_failure_is_thrown_so_error_states_are_designable() = runTest {
        val behaviour = FakeBehaviour(failure = { IllegalStateException("offline") })

        assertFailsWith<IllegalStateException> { behaviour.respond { "ok" } }
    }
}
