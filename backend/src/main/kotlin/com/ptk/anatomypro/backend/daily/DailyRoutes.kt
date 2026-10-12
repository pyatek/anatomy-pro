package com.ptk.anatomypro.backend.daily

import com.ptk.anatomypro.api.ApiErrorCode
import com.ptk.anatomypro.api.ApiPaths
import com.ptk.anatomypro.api.DailyAttemptResponse
import com.ptk.anatomypro.api.DailyOption
import com.ptk.anatomypro.api.DailyQuestion
import com.ptk.anatomypro.api.DailyResultResponse
import com.ptk.anatomypro.api.DailyStartRequest
import com.ptk.anatomypro.api.DailyState
import com.ptk.anatomypro.api.DailyStateResponse
import com.ptk.anatomypro.api.DailySubmitRequest
import com.ptk.anatomypro.api.EntitlementsResponse
import com.ptk.anatomypro.api.LeaderboardResponse
import com.ptk.anatomypro.backend.ApiException
import com.ptk.anatomypro.backend.Settings
import com.ptk.anatomypro.backend.accounts.Account
import com.ptk.anatomypro.backend.accounts.Accounts
import com.ptk.anatomypro.backend.accounts.account
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** Who has paid. Stage 7 answers from verified purchases; until then, a setting does. */
fun interface SubscriberCheck {
    suspend fun isSubscribed(account: UUID): Boolean
}

/**
 * The daily and the boards (backend spec §6, §7). All of it is for subscribers (§8.2), and
 * every date is the world's day, UTC.
 */
fun Route.dailyRoutes(
    accounts: Accounts,
    sets: DailySets,
    attempts: Attempts,
    subscribers: SubscriberCheck,
    settings: Settings,
    now: () -> Instant,
) {
    fun today(): LocalDate = now().atOffset(ZoneOffset.UTC).toLocalDate()

    /** The caller, who must have a token and must have paid — asked in that order. */
    suspend fun ApplicationCall.subscriber(): Account {
        val account = account(accounts)
        if (!subscribers.isSubscribed(account.id)) throw ApiException(HttpStatusCode.Forbidden, ApiErrorCode.NOT_SUBSCRIBED)
        return account
    }

    fun dateOf(text: String?): LocalDate? = runCatching { LocalDate.parse(text) }.getOrNull()

    fun attemptResponse(set: DailySet, attempt: Attempt): DailyAttemptResponse {
        // Named in the language asked for where the daily is offered in it, in its first otherwise.
        val locale = attempt.locale.takeIf { it in settings.dailyLocales } ?: settings.dailyLocales.first()
        return DailyAttemptResponse(
            date = set.date.toString(),
            startedAtEpochMillis = attempt.startedAt.toEpochMilli(),
            questions = set.questions.map { question ->
                DailyQuestion(
                    id = question.id,
                    highlighted = question.highlighted,
                    options = question.options.map { option ->
                        val name = option.names.getValue(locale)
                        DailyOption(id = option.id, name = name.name, nameLocale = locale, latinName = name.latin)
                    },
                )
            },
        )
    }

    suspend fun resultResponse(account: UUID, set: DailySet, attempt: Attempt) = withContext(Dispatchers.IO) {
        DailyResultResponse(
            date = set.date.toString(),
            correct = attempt.correct ?: 0,
            total = set.questions.size,
            elapsedMillis = attempt.elapsedMillis ?: 0,
            totalPlayers = attempts.rankedCount(set.date),
            ranked = attempt.ranked,
            rank = if (attempt.ranked) attempts.rankOf(account, set.date) else null,
        )
    }

    // Not gated: this is how a device learns which side of the gate it is on (§8.1).
    get(ApiPaths.ENTITLEMENTS) {
        val account = call.account(accounts)
        call.respond(EntitlementsResponse(subscribed = subscribers.isSubscribed(account.id)))
    }

    get(ApiPaths.DAILY_TODAY) {
        val account = call.subscriber()
        val date = today()
        val set = withContext(Dispatchers.IO) { sets.forDate(date, now()) }
            ?: return@get call.respond(DailyStateResponse(date.toString(), DailyState.UNAVAILABLE))
        val attempt = withContext(Dispatchers.IO) { attempts.find(account.id, date) }
        call.respond(
            when {
                attempt == null -> DailyStateResponse(date.toString(), DailyState.NOT_STARTED, questionCount = set.questions.size)
                attempt.submittedAt == null -> DailyStateResponse(
                    date.toString(), DailyState.IN_PROGRESS, questionCount = set.questions.size, attempt = attemptResponse(set, attempt),
                )
                else -> DailyStateResponse(
                    date.toString(), DailyState.COMPLETED, questionCount = set.questions.size, result = resultResponse(account.id, set, attempt),
                )
            },
        )
    }

    post("/v1/daily/{date}/start") {
        val account = call.subscriber()
        val request = call.receive<DailyStartRequest>()
        val date = today()
        // An attempt belongs to the day it starts on, and that is the server's day (§6.2).
        if (dateOf(call.parameters["date"]) != date) throw ApiException(HttpStatusCode.Conflict, ApiErrorCode.WRONG_DATE)
        val set = withContext(Dispatchers.IO) { sets.forDate(date, now()) }
            ?: throw ApiException(HttpStatusCode.Conflict, ApiErrorCode.DAILY_UNAVAILABLE)
        val attempt = withContext(Dispatchers.IO) { attempts.start(account.id, date, request.locale, request.timed, now()) }
        call.respond(attemptResponse(set, attempt))
    }

    post("/v1/daily/{date}/submit") {
        val account = call.subscriber()
        val request = call.receive<DailySubmitRequest>()
        // Not only today: a submit that was retrying across midnight is for yesterday.
        val date = dateOf(call.parameters["date"]) ?: throw ApiException(HttpStatusCode.Conflict, ApiErrorCode.NOT_STARTED)
        val started = withContext(Dispatchers.IO) { attempts.find(account.id, date) }
            ?: throw ApiException(HttpStatusCode.Conflict, ApiErrorCode.NOT_STARTED)
        val set = withContext(Dispatchers.IO) { sets.forDate(started.date, now()) }
            ?: throw ApiException(HttpStatusCode.Conflict, ApiErrorCode.NOT_STARTED)
        val attempt = withContext(Dispatchers.IO) { attempts.submit(account.id, set, request.answers, now()) }
            ?: throw ApiException(HttpStatusCode.Conflict, ApiErrorCode.NOT_STARTED)
        call.respond(resultResponse(account.id, set, attempt))
    }

    get("/v1/leaderboard/daily/{date}") {
        val account = call.subscriber()
        val date = dateOf(call.parameters["date"]) ?: throw ApiException(HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST)
        val board = withContext(Dispatchers.IO) { attempts.dailyBoard(account.id, date) }
        call.respond(LeaderboardResponse(board.entries, board.totalPlayers, board.me))
    }

    get(ApiPaths.STREAK_LEADERBOARD) {
        val account = call.subscriber()
        val board = withContext(Dispatchers.IO) { attempts.streakBoard(account.id, today()) }
        call.respond(LeaderboardResponse(board.entries, board.totalPlayers, board.me))
    }
}
