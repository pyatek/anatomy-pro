package com.ptk.anatomypro.api.client

import com.ptk.anatomypro.api.ApiPaths
import com.ptk.anatomypro.api.LeaderboardResponse
import com.ptk.anatomypro.api.LeaderboardRow
import com.ptk.anatomypro.core.data.model.Leaderboard
import com.ptk.anatomypro.core.data.model.LeaderboardEntry
import com.ptk.anatomypro.core.data.repository.LeaderboardRepository
import kotlinx.datetime.LocalDate

/** The boards, from the server (backend spec §7). Failures are thrown; the screen has a state for them. */
class KtorLeaderboardRepository(private val api: ApiClient) : LeaderboardRepository {

    override suspend fun daily(date: LocalDate): Leaderboard =
        api.get(ApiPaths.dailyLeaderboard(date.toString()), LeaderboardResponse.serializer()).toModel()

    override suspend fun streaks(): Leaderboard = api.get(ApiPaths.STREAK_LEADERBOARD, LeaderboardResponse.serializer()).toModel()

    private fun LeaderboardResponse.toModel() = Leaderboard(entries.map { it.toModel() }, me?.toModel(), totalPlayers)

    private fun LeaderboardRow.toModel() = LeaderboardEntry(rank, nickname, score, elapsedMillis, isMe)
}
