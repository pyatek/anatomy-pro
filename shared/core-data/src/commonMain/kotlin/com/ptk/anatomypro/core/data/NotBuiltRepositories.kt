package com.ptk.anatomypro.core.data

import com.ptk.anatomypro.core.data.model.AnswerResult
import com.ptk.anatomypro.core.data.model.DailyQuiz
import com.ptk.anatomypro.core.data.model.DailyResult
import com.ptk.anatomypro.core.data.model.DayActivity
import com.ptk.anatomypro.core.data.model.Entitlements
import com.ptk.anatomypro.core.data.model.Leaderboard
import com.ptk.anatomypro.core.data.model.PackState
import com.ptk.anatomypro.core.data.model.PurchaseOutcome
import com.ptk.anatomypro.core.data.model.QuizAnswer
import com.ptk.anatomypro.core.data.model.QuizFormat
import com.ptk.anatomypro.core.data.model.QuizSession
import com.ptk.anatomypro.core.data.model.QuizSummary
import com.ptk.anatomypro.core.data.model.QuizTopic
import com.ptk.anatomypro.core.data.model.StreakState
import com.ptk.anatomypro.core.data.model.SubscriptionPlan
import com.ptk.anatomypro.core.data.model.SystemMastery
import com.ptk.anatomypro.core.data.repository.AtlasRepository
import com.ptk.anatomypro.core.data.repository.DailyRepository
import com.ptk.anatomypro.core.data.repository.EntitlementRepository
import com.ptk.anatomypro.core.data.repository.LeaderboardRepository
import com.ptk.anatomypro.core.data.repository.PackRepository
import com.ptk.anatomypro.core.data.repository.ProgressRepository
import com.ptk.anatomypro.core.data.repository.QuizRepository
import com.ptk.anatomypro.core.data.repository.SettingsRepository
import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.core.model.QuizSessionId
import com.ptk.anatomypro.core.model.QuizTopicId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDate

private fun notBuilt(what: String): Nothing =
    TODO("$what has no implementation yet — Phase 3 (§11) and §8.1 are what fill it")

object NotBuiltQuizRepository : QuizRepository {
    override suspend fun topics(locale: String): List<QuizTopic> = notBuilt("the quiz")
    override suspend fun startSession(topic: QuizTopicId, format: QuizFormat, questionCount: Int, seed: Long, locale: String): QuizSession = notBuilt("the quiz")
    override suspend fun submit(session: QuizSessionId, answer: QuizAnswer): AnswerResult = notBuilt("the quiz")
    override suspend fun finish(session: QuizSessionId): QuizSummary = notBuilt("the quiz")
}

object NotBuiltProgressRepository : ProgressRepository {
    override val streak: Flow<StreakState> = flow { notBuilt("progress") }
    override suspend fun masteryBySystem(locale: String): List<SystemMastery> = notBuilt("progress")
    override suspend fun activity(from: LocalDate, to: LocalDate): Map<LocalDate, DayActivity> = notBuilt("progress")
    override suspend fun recordSession(summary: QuizSummary) = notBuilt("progress")
}

object NotBuiltDailyRepository : DailyRepository {
    override suspend fun today(): DailyQuiz = notBuilt("the daily quiz")
    override suspend fun start(locale: String): DailyQuiz.InProgress = notBuilt("the daily quiz")
    override suspend fun submit(answers: List<QuizAnswer>): DailyResult = notBuilt("the daily quiz")
}

object NotBuiltLeaderboardRepository : LeaderboardRepository {
    override suspend fun daily(date: LocalDate): Leaderboard = notBuilt("the leaderboard")
    override suspend fun streaks(): Leaderboard = notBuilt("the leaderboard")
}

object NotBuiltEntitlementRepository : EntitlementRepository {
    override val entitlements: Flow<Entitlements> = flow { notBuilt("entitlements") }
    override suspend fun plans(): List<SubscriptionPlan> = notBuilt("entitlements")
    override suspend fun purchase(plan: SubscriptionPlan): PurchaseOutcome = notBuilt("entitlements")
    override suspend fun restore(): PurchaseOutcome = notBuilt("entitlements")
}

object NotBuiltPackRepository : PackRepository {
    override val packs: Flow<List<PackState>> = flow { notBuilt("pack management") }
    override suspend fun download(id: PackId) = notBuilt("pack management")
    override suspend fun cancel(id: PackId) = notBuilt("pack management")
    override suspend fun delete(id: PackId) = notBuilt("pack management")
}

/**
 * The production set: the two repositories that exist, and six that refuse.
 *
 * Refusing is the point. A screen wired to one of these in a release build crashes with a
 * message naming what is missing, rather than presenting a fabricated leaderboard or a
 * purchase that never happened.
 */
fun notBuiltAppDependencies(
    atlas: AtlasRepository?,
    settings: SettingsRepository,
) = AppDependencies(
    atlas = atlas,
    settings = settings,
    quiz = NotBuiltQuizRepository,
    progress = NotBuiltProgressRepository,
    daily = NotBuiltDailyRepository,
    leaderboard = NotBuiltLeaderboardRepository,
    entitlements = NotBuiltEntitlementRepository,
    packs = NotBuiltPackRepository,
)
