package com.ptk.anatomypro.backend.accounts

import com.ptk.anatomypro.api.AccountDeletedResponse
import com.ptk.anatomypro.api.AccountResponse
import com.ptk.anatomypro.api.AnonymousAccountResponse
import com.ptk.anatomypro.api.ApiErrorCode
import com.ptk.anatomypro.api.ApiPaths
import com.ptk.anatomypro.api.NicknameRequest
import com.ptk.anatomypro.backend.ApiException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

private const val BEARER = "Bearer "

/** The token a request carries, if it carries one. Never logged, never echoed. */
fun ApplicationCall.bearerToken(): String? =
    request.headers[HttpHeaders.Authorization]?.takeIf { it.startsWith(BEARER) }?.removePrefix(BEARER)?.trim()

/** The account this request speaks for, or a 401. */
suspend fun ApplicationCall.account(accounts: Accounts): Account {
    val token = bearerToken() ?: throw ApiException(HttpStatusCode.Unauthorized, ApiErrorCode.UNAUTHENTICATED)
    return withContext(Dispatchers.IO) { accounts.byToken(token) }
        ?: throw ApiException(HttpStatusCode.Unauthorized, ApiErrorCode.UNAUTHENTICATED)
}

private fun Account.toResponse() = AccountResponse(
    id = id.toString(),
    nickname = nickname,
    nicknameLockedUntil = nicknameLockedUntil?.toString(),
)

/** `POST /v1/auth/anonymous`. Apart from the rest, because it is limited by address, not by token. */
fun Route.anonymousAccountRoute(accounts: Accounts) {
    post(ApiPaths.ANONYMOUS) {
        val created = withContext(Dispatchers.IO) { accounts.createAnonymous() }
        call.respond(HttpStatusCode.Created, AnonymousAccountResponse(created.account.toResponse(), created.deviceToken))
    }
}

fun Route.accountRoutes(accounts: Accounts, nicknames: NicknameRules, today: () -> LocalDate) {
    get(ApiPaths.ACCOUNT) {
        call.respond(call.account(accounts).toResponse())
    }

    put(ApiPaths.NICKNAME) {
        val account = call.account(accounts)
        val wanted = call.receive<NicknameRequest>().nickname
        val lockedUntil = account.nicknameLockedUntil
        if (lockedUntil != null && !today().isAfter(lockedUntil)) {
            throw ApiException(HttpStatusCode.Forbidden, ApiErrorCode.NICKNAME_LOCKED)
        }
        if (!nicknames.allows(wanted)) throw ApiException(HttpStatusCode.BadRequest, ApiErrorCode.NICKNAME_INVALID)
        when (val result = withContext(Dispatchers.IO) { accounts.rename(account, wanted) }) {
            is RenameResult.Renamed -> call.respond(result.account.toResponse())
            RenameResult.Taken -> throw ApiException(HttpStatusCode.Conflict, ApiErrorCode.NICKNAME_TAKEN)
        }
    }

    delete(ApiPaths.ACCOUNT) {
        val account = call.account(accounts)
        withContext(Dispatchers.IO) { accounts.delete(account) }
        // No subscriptions exist yet (stage 7); when they do, this says whether one outlives the account.
        call.respond(AccountDeletedResponse(subscriptionStillActive = false))
    }
}
