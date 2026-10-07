package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.model.DayActivity
import com.ptk.anatomypro.core.data.model.QuizSummary
import com.ptk.anatomypro.core.data.model.StreakState
import com.ptk.anatomypro.core.data.model.SystemMastery
import com.ptk.anatomypro.core.data.repository.ProgressRepository
import com.ptk.anatomypro.core.model.SystemId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate

/**
 * A plausible study history.
 *
 * The streak is non-zero by default because screens 14 and 17 are designed around a
 * student who has been studying — an empty state is a variant worth seeing deliberately,
 * by constructing this with [StreakState] of zero, not the thing every run shows.
 */
class FakeProgressRepository(
    private val behaviour: FakeBehaviour = FakeBehaviour(),
    initialStreak: StreakState = StreakState(
        currentDays = 12,
        longestDays = 31,
        lastStudied = LocalDate(2026, 9, 24),
    ),
) : ProgressRepository {

    private val _streak = MutableStateFlow(initialStreak)
    override val streak: Flow<StreakState> = _streak.asStateFlow()

    private val _recorded = mutableListOf<QuizSummary>()
    val recorded: List<QuizSummary> get() = _recorded.toList()

    override suspend fun masteryBySystem(locale: String): List<SystemMastery> = behaviour.respond {
        listOf(
            SystemMastery(SKELETAL, title(SKELETAL, locale), structuresSeen = 14, structuresTotal = 19),
            SystemMastery(MUSCULAR, title(MUSCULAR, locale), structuresSeen = 0, structuresTotal = 24),
        )
    }

    override suspend fun activity(from: LocalDate, to: LocalDate): Map<LocalDate, DayActivity> =
        behaviour.respond {
            require(from <= to) { "from $from is after to $to" }
            buildMap {
                var cursor = from
                while (cursor <= to) {
                    // A deterministic pattern rather than a random one, so a screenshot of
                    // screen 17 is the same screenshot tomorrow.
                    val studied = cursor.day % 3 != 0
                    put(
                        cursor,
                        DayActivity(
                            sessions = if (studied) 1 else 0,
                            questionsAnswered = if (studied) 10 else 0,
                            dailyCompleted = studied && cursor.day % 2 == 0,
                        ),
                    )
                    cursor = LocalDate.fromEpochDays(cursor.toEpochDays() + 1)
                }
            }
        }

    override suspend fun recordSession(summary: QuizSummary) {
        behaviour.respond { _recorded += summary }
    }

    /** Latin is the canonical key, so it is the fallback for a locale with no title. */
    private fun title(system: SystemId, locale: String): String {
        val names = SYSTEM_TITLES.getValue(system)
        return names[locale] ?: names.getValue("la")
    }

    private companion object {
        // The ids packs carry, so mastery lines up with the atlas and with entitlements.
        val SKELETAL = SystemId("skeletal-system")
        val MUSCULAR = SystemId("muscular-system")

        val SYSTEM_TITLES = mapOf(
            SKELETAL to mapOf("la" to "Systema skeletale", "pl" to "Układ kostny", "en" to "Skeletal system"),
            MUSCULAR to mapOf("la" to "Systema musculare", "pl" to "Układ mięśniowy", "en" to "Muscular system"),
        )
    }
}
