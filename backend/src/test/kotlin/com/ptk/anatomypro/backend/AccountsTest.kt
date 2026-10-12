package com.ptk.anatomypro.backend

import com.ptk.anatomypro.api.AccountDeletedResponse
import com.ptk.anatomypro.api.AccountResponse
import com.ptk.anatomypro.api.AnonymousAccountResponse
import com.ptk.anatomypro.api.ApiError
import com.ptk.anatomypro.api.ApiErrorCode
import com.ptk.anatomypro.api.ApiJson
import com.ptk.anatomypro.api.ApiPaths
import com.ptk.anatomypro.api.NicknameRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.security.MessageDigest
import java.time.LocalDate
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Backend spec §5.1, §5.2, §5.4, and the error shape every endpoint shares. */
class AccountsTest {

    private val database: DataSource = TestDatabase.fresh()

    private fun server(
        settings: Settings = Settings(nicknameBlocklist = setOf("admin")),
        test: suspend ApplicationTestBuilder.(HttpClient) -> Unit,
    ) = testApplication {
        application { backend(database, settings) }
        test(createClient { install(ContentNegotiation) { json(ApiJson) } })
    }

    private suspend fun HttpClient.newAccount(): AnonymousAccountResponse = post(ApiPaths.ANONYMOUS).body()

    private suspend fun HttpClient.rename(token: String, nickname: String): HttpResponse = put(ApiPaths.NICKNAME) {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(NicknameRequest(nickname))
    }

    private suspend fun HttpResponse.error(): ApiErrorCode = body<ApiError>().code

    private fun count(sql: String): Int = database.connection.use { connection ->
        connection.createStatement().executeQuery(sql).use { it.next(); it.getInt(1) }
    }

    // --- health -------------------------------------------------------------------------

    @Test
    fun health_says_the_database_answers() = server { client ->
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
    }

    // --- anonymous accounts (§5.1) ------------------------------------------------------

    @Test
    fun an_account_is_made_without_asking_anything_and_can_be_ranked_at_once() = server { client ->
        val response = client.post(ApiPaths.ANONYMOUS)

        assertEquals(HttpStatusCode.Created, response.status)
        val created = response.body<AnonymousAccountResponse>()
        assertTrue(created.account.nickname.startsWith("student-"))
        assertTrue(created.deviceToken.length >= 43, "256 bits need at least 43 base64 characters")

        val read = client.get(ApiPaths.ACCOUNT) { bearerAuth(created.deviceToken) }
        assertEquals(created.account, read.body<AccountResponse>())
    }

    @Test
    fun two_accounts_get_different_tokens_and_different_nicknames() = server { client ->
        val one = client.newAccount()
        val two = client.newAccount()

        assertNotEquals(one.deviceToken, two.deviceToken)
        assertNotEquals(one.account.id, two.account.id)
        assertNotEquals(one.account.nickname, two.account.nickname)
    }

    @Test
    fun the_database_holds_the_tokens_hash_and_not_the_token() = server { client ->
        val token = client.newAccount().deviceToken

        val stored = database.connection.use { connection ->
            connection.createStatement().executeQuery("SELECT token_hash FROM device_token").use { it.next(); it.getBytes(1) }
        }

        assertContentEquals(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()), stored)
        assertEquals(0, count("SELECT count(*) FROM device_token WHERE encode(token_hash, 'escape') LIKE '%' || '$token' || '%'"))
    }

    @Test
    fun a_request_with_no_token_or_one_nobody_was_given_is_refused() = server { client ->
        val none = client.get(ApiPaths.ACCOUNT)
        assertEquals(HttpStatusCode.Unauthorized, none.status)
        assertEquals(ApiErrorCode.UNAUTHENTICATED, none.error())

        val forged = client.get(ApiPaths.ACCOUNT) { bearerAuth("not-a-token-anyone-was-given") }
        assertEquals(HttpStatusCode.Unauthorized, forged.status)
        assertEquals(ApiErrorCode.UNAUTHENTICATED, forged.error())
    }

    // --- nicknames (§5.2) ---------------------------------------------------------------

    @Test
    fun a_nickname_can_be_changed_to_one_the_rules_allow() = server { client ->
        val token = client.newAccount().deviceToken

        val renamed = client.rename(token, "Żaneta_92")

        assertEquals(HttpStatusCode.OK, renamed.status)
        assertEquals("Żaneta_92", renamed.body<AccountResponse>().nickname)
        assertEquals("Żaneta_92", client.get(ApiPaths.ACCOUNT) { bearerAuth(token) }.body<AccountResponse>().nickname)
    }

    @Test
    fun a_nickname_the_rules_refuse_is_refused_and_the_old_one_stays() = server { client ->
        val account = client.newAccount()

        for (bad in listOf("al", "ala ma kota", "x".repeat(21), "Admin")) {
            val response = client.rename(account.deviceToken, bad)
            assertEquals(HttpStatusCode.BadRequest, response.status, bad)
            assertEquals(ApiErrorCode.NICKNAME_INVALID, response.error(), bad)
        }
        assertEquals(
            account.account.nickname,
            client.get(ApiPaths.ACCOUNT) { bearerAuth(account.deviceToken) }.body<AccountResponse>().nickname,
        )
    }

    @Test
    fun a_nickname_someone_else_has_is_taken_whatever_its_case() = server { client ->
        val first = client.newAccount().deviceToken
        val second = client.newAccount().deviceToken
        client.rename(first, "Żaneta")

        val response = client.rename(second, "żANETA")

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(ApiErrorCode.NICKNAME_TAKEN, response.error())
    }

    @Test
    fun an_account_can_keep_its_own_nickname_or_only_change_its_case() = server { client ->
        val token = client.newAccount().deviceToken
        client.rename(token, "zaneta")

        assertEquals(HttpStatusCode.OK, client.rename(token, "Zaneta").status)
    }

    @Test
    fun an_account_whose_nickname_was_taken_from_it_cannot_choose_another_until_the_lock_ends() = server { client ->
        val account = client.newAccount()
        val until = LocalDate.now().plusDays(30)
        database.connection.use { connection ->
            connection.prepareStatement("UPDATE account SET nickname_locked_until = ? WHERE id = ?::uuid").use {
                it.setObject(1, until)
                it.setString(2, account.account.id)
                it.executeUpdate()
            }
        }

        val response = client.rename(account.deviceToken, "nowy_nick")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(ApiErrorCode.NICKNAME_LOCKED, response.error())
        val read = client.get(ApiPaths.ACCOUNT) { bearerAuth(account.deviceToken) }.body<AccountResponse>()
        assertEquals(until.toString(), read.nicknameLockedUntil)
    }

    @Test
    fun a_lock_that_has_ended_no_longer_holds() = server { client ->
        val account = client.newAccount()
        database.connection.use { connection ->
            connection.createStatement().executeUpdate("UPDATE account SET nickname_locked_until = CURRENT_DATE - 1")
        }

        assertEquals(HttpStatusCode.OK, client.rename(account.deviceToken, "nowy_nick").status)
    }

    // --- leaving (§5.4) -----------------------------------------------------------------

    @Test
    fun a_deleted_account_leaves_no_row_behind_and_its_token_stops_working() = server { client ->
        val keep = client.newAccount()
        val leave = client.newAccount()

        val response = client.delete(ApiPaths.ACCOUNT) { bearerAuth(leave.deviceToken) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(response.body<AccountDeletedResponse>().subscriptionStillActive)
        assertEquals(1, count("SELECT count(*) FROM account"))
        assertEquals(1, count("SELECT count(*) FROM device_token"))
        assertEquals(HttpStatusCode.Unauthorized, client.get(ApiPaths.ACCOUNT) { bearerAuth(leave.deviceToken) }.status)
        assertEquals(HttpStatusCode.OK, client.get(ApiPaths.ACCOUNT) { bearerAuth(keep.deviceToken) }.status)
    }

    // --- one error shape, and limits ----------------------------------------------------

    @Test
    fun a_body_that_cannot_be_read_is_an_invalid_request_not_a_stack_trace() = server { client ->
        val token = client.newAccount().deviceToken

        val response = client.put(ApiPaths.NICKNAME) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"nick""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ApiErrorCode.INVALID_REQUEST, response.error())
    }

    @Test
    fun a_path_that_is_not_there_answers_in_the_same_shape() = server { client ->
        val response = client.get("/v1/no-such-thing")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(ApiErrorCode.INVALID_REQUEST, response.error())
    }

    @Test
    fun one_address_cannot_mint_accounts_without_limit() = server(Settings(accountsPerAddressPerMinute = 3)) { client ->
        repeat(3) { assertEquals(HttpStatusCode.Created, client.post(ApiPaths.ANONYMOUS).status) }

        val refused = client.post(ApiPaths.ANONYMOUS)

        assertEquals(HttpStatusCode.TooManyRequests, refused.status)
        assertEquals(ApiErrorCode.RATE_LIMITED, refused.error())
        assertEquals(3, count("SELECT count(*) FROM account"))
    }

    @Test
    fun one_token_cannot_ask_without_limit() = server(Settings(requestsPerTokenPerMinute = 5)) { client ->
        val token = client.newAccount().deviceToken
        repeat(5) { assertEquals(HttpStatusCode.OK, client.get(ApiPaths.ACCOUNT) { bearerAuth(token) }.status) }

        val refused = client.get(ApiPaths.ACCOUNT) { bearerAuth(token) }

        assertEquals(HttpStatusCode.TooManyRequests, refused.status)
        assertEquals(ApiErrorCode.RATE_LIMITED, refused.error())
    }

    // --- what is written down (§11.2) ---------------------------------------------------

    @Test
    fun a_token_is_never_written_to_the_log() {
        val root = org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
        val written = ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>().apply { start() }
        root.addAppender(written)
        try {
            var token = ""
            server { client ->
                token = client.newAccount().deviceToken
                client.get(ApiPaths.ACCOUNT) { bearerAuth(token) }
                client.rename(token, "al")
                client.get(ApiPaths.ACCOUNT) { bearerAuth("forged-$token") }
            }

            val lines = written.list.map { it.formattedMessage }
            assertTrue(lines.any { ApiPaths.ACCOUNT in it }, "requests were not logged at all, so this proved nothing")
            assertTrue(lines.none { token in it })
        } finally {
            root.detachAppender(written)
        }
    }
}
