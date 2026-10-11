package com.ptk.anatomypro.core.data.progress

import com.ptk.anatomypro.core.data.AnatomyDatabase
import com.ptk.anatomypro.core.data.entity.QuizSessionEntity
import com.ptk.anatomypro.core.data.model.DayActivity
import com.ptk.anatomypro.core.data.model.QuizSummary
import com.ptk.anatomypro.core.data.model.StreakState
import com.ptk.anatomypro.core.data.model.SystemMastery
import com.ptk.anatomypro.core.data.repository.ProgressRepository
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.SystemId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/** The canonical language, and the one a title falls back to (spec §13). */
private const val LATIN = "la"

/**
 * Progress kept on the device: what was studied on which day, and which structures have
 * been answered rightly.
 *
 * Nothing is held here between calls, so what one session writes the next launch reads.
 * [today] is the user's own day (all-screens spec §4.2); it is asked for at the moment a
 * session is recorded and whenever the streak is read, never cached.
 *
 * What it does not know yet: whether a day's daily quiz was completed. That is the
 * server's to say (design §9.1), so [DayActivity.dailyCompleted] is false until Phase 3.
 */
class RoomProgressRepository(
    private val database: AnatomyDatabase,
    private val today: () -> LocalDate,
    private val now: () -> Long,
) : ProgressRepository {

    private val dao = database.progress()

    override val streak: Flow<StreakState> =
        dao.observeDays().map { days -> streakOf(days.mapNotNull(::parseDay).toSet(), today()) }

    override suspend fun masteryBySystem(locale: String): List<SystemMastery> {
        val structures = database.structures()
        return dao.masteryBySystem().map { row ->
            // A system is named by the structure that is its root, where the atlas has one.
            val title = structures.text(row.systemId, locale)?.name
                ?: structures.text(row.systemId, LATIN)?.name
                ?: row.systemId
            SystemMastery(SystemId(row.systemId), title, structuresSeen = row.seen, structuresTotal = row.total)
        }
    }

    override suspend fun activity(from: LocalDate, to: LocalDate): Map<LocalDate, DayActivity> {
        require(from <= to) { "from $from is after to $to" }
        return dao.activity(from.toString(), to.toString()).mapNotNull { row ->
            parseDay(row.day)?.let { it to DayActivity(sessions = row.sessions, questionsAnswered = row.questions, dailyCompleted = false) }
        }.toMap()
    }

    override suspend fun recordSession(summary: QuizSummary) {
        dao.record(
            QuizSessionEntity(
                day = today().toString(),
                finishedAt = now(),
                correct = summary.correct,
                total = summary.total,
                elapsedMillis = summary.elapsedMillis,
            ),
            summary.answered.map { it.structure.value to it.correct },
        )
    }

    /** Every structure answered rightly at least once: what a topic's mastery is counted from. */
    suspend fun mastered(): Set<StructureId> = dao.mastered().map(::StructureId).toSet()

    /** A day that does not parse was not written by this class; it is left out, not fatal. */
    private fun parseDay(day: String): LocalDate? = try {
        LocalDate.parse(day)
    } catch (_: IllegalArgumentException) {
        null
    }
}
