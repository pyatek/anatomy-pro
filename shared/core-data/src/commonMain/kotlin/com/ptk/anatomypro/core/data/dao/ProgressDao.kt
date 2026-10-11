package com.ptk.anatomypro.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ptk.anatomypro.core.data.entity.QuizSessionEntity
import com.ptk.anatomypro.core.data.entity.StructureProgressEntity
import kotlinx.coroutines.flow.Flow

/** Projection for [ProgressDao.activity]. */
data class DayRow(val day: String, val sessions: Int, val questions: Int)

/** Projection for [ProgressDao.masteryBySystem]. */
data class SystemMasteryRow(val systemId: String, val total: Int, val seen: Int)

@Dao
interface ProgressDao {

    @Insert
    suspend fun insertSession(session: QuizSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(row: StructureProgressEntity)

    @Query("SELECT * FROM structure_progress WHERE structureId = :id")
    suspend fun progress(id: String): StructureProgressEntity?

    /** A Flow, so the streak on the home screen moves when a session ends on another tab. */
    @Query("SELECT DISTINCT day FROM quiz_session")
    fun observeDays(): Flow<List<String>>

    /** ISO dates compare as text in date order, which is what BETWEEN relies on here. */
    @Query(
        """
        SELECT day, COUNT(*) AS sessions, SUM(total) AS questions FROM quiz_session
        WHERE day BETWEEN :from AND :to GROUP BY day
        """
    )
    suspend fun activity(from: String, to: String): List<DayRow>

    /** Every structure answered rightly at least once. */
    @Query("SELECT structureId FROM structure_progress WHERE timesCorrect > 0")
    suspend fun mastered(): List<String>

    /**
     * Per system: how many drawn structures it has, and how many of them have been answered
     * rightly. Groups are not counted; they are never an answer.
     */
    @Query(
        """
        SELECT s.systemId AS systemId, COUNT(*) AS total,
               SUM(CASE WHEN p.timesCorrect > 0 THEN 1 ELSE 0 END) AS seen
        FROM structure s LEFT JOIN structure_progress p ON p.structureId = s.id
        WHERE s.isGroup = 0 AND s.systemId IS NOT NULL
        GROUP BY s.systemId ORDER BY s.systemId
        """
    )
    suspend fun masteryBySystem(): List<SystemMasteryRow>

    /**
     * A finished session and what it says about each structure, together or not at all: a
     * session counted without its answers would raise the streak and teach mastery nothing.
     */
    @Transaction
    suspend fun record(session: QuizSessionEntity, answered: List<Pair<String, Boolean>>) {
        insertSession(session)
        for ((structureId, correct) in answered) {
            val before = progress(structureId)
            upsertProgress(
                StructureProgressEntity(
                    structureId = structureId,
                    timesCorrect = (before?.timesCorrect ?: 0) + if (correct) 1 else 0,
                    timesWrong = (before?.timesWrong ?: 0) + if (correct) 0 else 1,
                    lastAnsweredAt = session.finishedAt,
                )
            )
        }
    }
}
