package com.ptk.anatomypro.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One finished quiz session.
 *
 * [day] is the user's own calendar day as an ISO date, written when the session ends: the
 * streak and the calendar are in the user's days (all-screens spec §4.2), and a day worked
 * out later from [finishedAt] would move if they travelled.
 */
@Entity(tableName = "quiz_session", indices = [Index("day")])
data class QuizSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: String,
    val finishedAt: Long,
    val correct: Int,
    val total: Int,
    val elapsedMillis: Long,
)

/**
 * How a structure has gone when asked about, over every session.
 *
 * Keyed by structure id alone, so it outlives a pack update as verifications do: the id is
 * TA-derived and stays when meshes and names are replaced.
 */
@Entity(tableName = "structure_progress")
data class StructureProgressEntity(
    @PrimaryKey val structureId: String,
    val timesCorrect: Int,
    val timesWrong: Int,
    val lastAnsweredAt: Long,
)
