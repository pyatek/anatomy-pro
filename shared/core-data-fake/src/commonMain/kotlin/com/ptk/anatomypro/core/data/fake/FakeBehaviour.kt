package com.ptk.anatomypro.core.data.fake

import kotlinx.coroutines.delay
import kotlin.time.Duration

/**
 * What a fake does before it answers.
 *
 * Loading states, empty states and §14's error rows stop being theoretical: screen 15 can
 * be shown offline and screen 03 can fail mid-download, without a backend to break.
 *
 * [failure] is a factory rather than a Throwable so each call gets its own stack. [respond]
 * takes a suspending block so one fake can answer through another, as the daily does
 * through the quiz fake.
 */
data class FakeBehaviour(
    val delay: Duration = Duration.ZERO,
    val failure: (() -> Throwable)? = null,
) {
    suspend fun <T> respond(block: suspend () -> T): T {
        if (delay > Duration.ZERO) delay(delay)
        failure?.let { throw it() }
        return block()
    }
}
