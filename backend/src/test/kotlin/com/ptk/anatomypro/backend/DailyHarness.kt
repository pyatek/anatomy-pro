package com.ptk.anatomypro.backend

import com.ptk.anatomypro.api.AnonymousAccountResponse
import com.ptk.anatomypro.api.ApiError
import com.ptk.anatomypro.api.ApiErrorCode
import com.ptk.anatomypro.api.ApiJson
import com.ptk.anatomypro.api.ApiPaths
import com.ptk.anatomypro.api.DailyAnswer
import com.ptk.anatomypro.api.DailyAttemptResponse
import com.ptk.anatomypro.api.DailyQuestion
import com.ptk.anatomypro.api.DailyResultResponse
import com.ptk.anatomypro.api.DailyStartRequest
import com.ptk.anatomypro.api.DailyStateResponse
import com.ptk.anatomypro.api.DailySubmitRequest
import com.ptk.anatomypro.api.LeaderboardResponse
import com.ptk.anatomypro.backend.content.Content
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import javax.sql.DataSource

/**
 * A server with the sample atlas in it, a clock the test moves, and the calls a player's
 * device makes.
 */
class DailyHarness(
    val settings: Settings = Settings(everyoneIsSubscribed = true, requestsPerTokenPerMinute = 10_000, accountsPerAddressPerMinute = 10_000),
    seed: (Content) -> Unit = {
        it.ingest(SampleContent.manifest())
        it.verify(SampleContent.verifications())
    },
) {
    val database: DataSource = TestDatabase.fresh()
    val content = Content(database).also(seed)
    var now: Instant = Instant.parse("2026-10-12T10:00:00Z")
    val today: String get() = now.atOffset(ZoneOffset.UTC).toLocalDate().toString()

    fun pass(time: Duration) { now += time }
    fun nextDay(at: String = "10:00:00") { now = Instant.parse("${now.atOffset(ZoneOffset.UTC).toLocalDate().plusDays(1)}T${at}Z") }

    fun run(test: suspend Player.Scope.() -> Unit) = testApplication {
        application { backend(database, settings, now = { now }) }
        val client = createClient { install(ContentNegotiation) { json(ApiJson) } }
        Player.Scope(client, this@DailyHarness).test()
    }
}

/** One account, as its device talks to the server. */
class Player(private val client: HttpClient, private val harness: DailyHarness, val token: String, val nickname: String) {

    class Scope(val client: HttpClient, val harness: DailyHarness) {
        suspend fun player(): Player {
            val created = client.post(ApiPaths.ANONYMOUS).body<AnonymousAccountResponse>()
            return Player(client, harness, created.deviceToken, created.account.nickname)
        }
    }

    suspend fun todayResponse(): HttpResponse = client.get(ApiPaths.DAILY_TODAY) { bearerAuth(token) }
    suspend fun today(): DailyStateResponse = todayResponse().body()

    suspend fun startResponse(date: String = harness.today, locale: String = "la", timed: Boolean = true): HttpResponse =
        client.post(ApiPaths.dailyStart(date)) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(DailyStartRequest(locale, timed))
        }

    suspend fun start(locale: String = "la", timed: Boolean = true): DailyAttemptResponse = startResponse(locale = locale, timed = timed).body()

    suspend fun submitResponse(date: String, answers: List<DailyAnswer>): HttpResponse =
        client.post(ApiPaths.dailySubmit(date)) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(DailySubmitRequest(answers))
        }

    /**
     * Starts, thinks for [thinking], and submits with the first [right] questions answered
     * rightly and the rest wrongly. The right option is the highlighted structure: the
     * device knows it (backend spec §4.1), and so does this test.
     */
    suspend fun play(right: Int = Int.MAX_VALUE, thinking: Duration = Duration.ofSeconds(60), timed: Boolean = true): DailyResultResponse {
        val attempt = start(timed = timed)
        harness.pass(thinking)
        return submitResponse(attempt.date, attempt.questions.mapIndexed { index, question -> answer(question, index < right) }).body()
    }

    fun answer(question: DailyQuestion, rightly: Boolean) = DailyAnswer(
        questionId = question.id,
        chosen = if (rightly) question.highlighted else question.options.first { it.id != question.highlighted }.id,
    )

    suspend fun dailyBoardResponse(date: String = harness.today): HttpResponse = client.get(ApiPaths.dailyLeaderboard(date)) { bearerAuth(token) }
    suspend fun dailyBoard(date: String = harness.today): LeaderboardResponse = dailyBoardResponse(date).body()
    suspend fun streakBoardResponse(): HttpResponse = client.get(ApiPaths.STREAK_LEADERBOARD) { bearerAuth(token) }
    suspend fun streakBoard(): LeaderboardResponse = streakBoardResponse().body()
}

suspend fun HttpResponse.errorCode(): ApiErrorCode = body<ApiError>().code
