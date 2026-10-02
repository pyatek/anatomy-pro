package com.ptk.anatomypro.core.data.repository

import com.ptk.anatomypro.core.data.model.AnswerResult
import com.ptk.anatomypro.core.data.model.QuizAnswer
import com.ptk.anatomypro.core.data.model.QuizFormat
import com.ptk.anatomypro.core.data.model.QuizSession
import com.ptk.anatomypro.core.data.model.QuizSummary
import com.ptk.anatomypro.core.data.model.QuizTopic
import com.ptk.anatomypro.core.model.QuizSessionId
import com.ptk.anatomypro.core.model.QuizTopicId

/**
 * Screens 08 to 13.
 *
 * Sessions are generated on-device and are seeded and reproducible (§8.2), so this is a
 * local contract. The fake and the eventual real generator differ in how they choose
 * questions, not in where they live.
 */
interface QuizRepository {

    suspend fun topics(locale: String): List<QuizTopic>

    suspend fun startSession(
        topic: QuizTopicId,
        format: QuizFormat,
        questionCount: Int,
        seed: Long,
        /**
         * The examination locale (§13), which every structure name in the session is given
         * in — options, prompts and answer feedback. Independent of the interface locale.
         */
        locale: String,
    ): QuizSession

    suspend fun submit(session: QuizSessionId, answer: QuizAnswer): AnswerResult

    suspend fun finish(session: QuizSessionId): QuizSummary
}
