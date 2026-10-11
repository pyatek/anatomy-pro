package com.ptk.anatomypro.feature.quiz

import com.ptk.anatomypro.core.data.model.QuizQuestion
import com.ptk.anatomypro.core.data.model.QuizSession
import com.ptk.anatomypro.core.data.repository.AtlasRepository
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.SystemId
import kotlinx.coroutines.CancellationException

/**
 * What a session's canvas leaves out: every system but the ones it asks about.
 *
 * A pack with every system in it draws the body's surface over the bones, so a question
 * about a rib showed skin with an outline on it. The atlas has layers for this; a quiz
 * cannot ask the student to peel them back first, so it does.
 *
 * An atlas that cannot be read hides nothing: a question on a crowded model is still a
 * question, and a failure here must not be what ends a session.
 */
suspend fun structuresHiddenFor(atlas: AtlasRepository, session: QuizSession): Set<StructureId> = try {
    val asked = session.questions.map {
        when (it) {
            is QuizQuestion.NameTheHighlighted -> it.correct.id
            is QuizQuestion.TapTheStructure -> it.target
        }
    }
    val systems = asked.mapNotNull { atlas.detail(it, session.locale)?.systemId }.toSet()
    if (systems.isEmpty()) {
        emptySet()
    } else {
        val kept = systems.flatMap { atlas.structuresIn(SystemId(it)) }.toSet()
        // Never something asked about, whatever the atlas says its system is.
        atlas.allStructures() - kept - asked.toSet()
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    emptySet()
}

/**
 * Whether a question's time is running.
 *
 * Only while the app is in front and the model is on screen. The canvas reloads its pack
 * when it is created, and for those seconds there is nothing to answer about.
 */
fun quizClockRuns(resumed: Boolean, canvasReady: Boolean): Boolean = resumed && canvasReady
