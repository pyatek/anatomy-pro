package com.ptk.anatomypro.backend

import com.ptk.anatomypro.api.ApiError
import com.ptk.anatomypro.api.ApiErrorCode
import com.ptk.anatomypro.api.ApiJson
import com.ptk.anatomypro.backend.accounts.Accounts
import com.ptk.anatomypro.backend.accounts.Identities
import com.ptk.anatomypro.backend.accounts.IdentityVerifier
import com.ptk.anatomypro.backend.accounts.NicknameRules
import com.ptk.anatomypro.backend.accounts.accountRoutes
import com.ptk.anatomypro.backend.accounts.anonymousAccountRoute
import com.ptk.anatomypro.backend.accounts.bearerToken
import com.ptk.anatomypro.backend.content.Content
import com.ptk.anatomypro.backend.daily.Attempts
import com.ptk.anatomypro.backend.daily.DailySets
import com.ptk.anatomypro.backend.daily.SubscriberCheck
import com.ptk.anatomypro.backend.daily.dailyRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.Instant
import javax.sql.DataSource
import kotlin.time.Duration.Companion.minutes

private val BY_ADDRESS = RateLimitName("by-address")
private val BY_TOKEN = RateLimitName("by-token")
private val log = LoggerFactory.getLogger("com.ptk.anatomypro.backend")

/**
 * The whole server, over a database that is already open and migrated.
 *
 * Tests call this with a schema of their own; `main` calls it with the real pool. Nothing
 * here reads the environment.
 */
fun Application.backend(
    database: DataSource,
    settings: Settings = Settings(),
    now: () -> Instant = Instant::now,
    subscribers: SubscriberCheck = SubscriberCheck { settings.everyoneIsSubscribed },
    /** Reads a provider's published keys. The real one asks the provider; a test's does not. */
    fetchKeys: (url: String) -> String = ::fetchOverHttps,
) {
    val nicknames = NicknameRules(settings.nicknameBlocklist)
    val accounts = Accounts(database, nicknames)
    val identities = Identities(database)
    val verifier = IdentityVerifier(settings.identityProviders, fetchKeys, now)
    val sets = DailySets(database, Content(database), settings)
    val attempts = Attempts(database, settings)

    if (settings.trustForwardedHeaders) install(XForwardedHeaders)
    // Method, path and status. Headers are never written, so a token never is (§11.2).
    install(CallLogging)
    install(ContentNegotiation) { json(ApiJson) }

    // One shape for every refusal, whoever raised it.
    install(StatusPages) {
        exception<ApiException> { call, refusal ->
            call.respond(refusal.status, ApiError(refusal.code, refusal.message.orEmpty()))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ApiError(ApiErrorCode.INVALID_REQUEST))
        }
        exception<Throwable> { call, failure ->
            log.error("unhandled failure", failure)
            call.respond(HttpStatusCode.InternalServerError, ApiError(ApiErrorCode.UNKNOWN))
        }
        status(HttpStatusCode.NotFound, HttpStatusCode.MethodNotAllowed) { call, status ->
            call.respond(status, ApiError(ApiErrorCode.INVALID_REQUEST))
        }
        status(HttpStatusCode.TooManyRequests) { call, status ->
            call.respond(status, ApiError(ApiErrorCode.RATE_LIMITED))
        }
    }

    install(RateLimit) {
        register(BY_ADDRESS) {
            rateLimiter(limit = settings.accountsPerAddressPerMinute, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
        register(BY_TOKEN) {
            rateLimiter(limit = settings.requestsPerTokenPerMinute, refillPeriod = 1.minutes)
            // A request with no token is counted against its address instead.
            requestKey { call -> call.bearerToken() ?: call.request.origin.remoteHost }
        }
    }

    routing {
        get("/health") {
            val answers = runCatching {
                withContext(Dispatchers.IO) {
                    database.connection.use { it.createStatement().executeQuery("SELECT 1").use { rows -> rows.next() } }
                }
            }.getOrDefault(false)
            if (answers) call.respond(mapOf("status" to "ok")) else call.respond(HttpStatusCode.ServiceUnavailable, ApiError())
        }
        rateLimit(BY_ADDRESS) { anonymousAccountRoute(accounts) }
        rateLimit(BY_TOKEN) {
            accountRoutes(accounts, nicknames, identities, verifier, now)
            dailyRoutes(accounts, sets, attempts, subscribers, settings, now)
        }
    }
}

/** A provider's key set, from the provider. Short timeouts: a sign-in is waiting on it. */
private fun fetchOverHttps(url: String): String {
    val client = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build()
    val request = java.net.http.HttpRequest.newBuilder(java.net.URI(url)).timeout(java.time.Duration.ofSeconds(5)).GET().build()
    val response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) { "$url answered ${response.statusCode()}" }
    return response.body()
}
