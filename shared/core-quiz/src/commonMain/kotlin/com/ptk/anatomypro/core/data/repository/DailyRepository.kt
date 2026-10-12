package com.ptk.anatomypro.core.data.repository

import com.ptk.anatomypro.core.data.model.DailyQuiz
import com.ptk.anatomypro.core.data.model.DailyResult
import com.ptk.anatomypro.core.data.model.QuizAnswer

/** Screens 14 and 15. */
interface DailyRepository {
    suspend fun today(): DailyQuiz
    /** Fetches the day's questions; the server records this as the clock start (§9.1). */
    /** [locale] is the examination locale the questions are named in (§13). */
    suspend fun start(locale: String): DailyQuiz.InProgress
    /** The server scores against its own key and returns rank (§9.1, §9.2). */
    suspend fun submit(answers: List<QuizAnswer>): DailyResult
}
