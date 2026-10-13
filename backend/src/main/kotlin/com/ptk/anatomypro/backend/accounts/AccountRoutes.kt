package com.ptk.anatomypro.backend.accounts

import com.ptk.anatomypro.api.AccountDeletedResponse
import com.ptk.anatomypro.api.AccountResponse
import com.ptk.anatomypro.api.AnonymousAccountResponse
import com.ptk.anatomypro.api.ApiErrorCode
import com.ptk.anatomypro.api.ApiPaths
import com.ptk.anatomypro.api.IdentityProvider
import com.ptk.anatomypro.api.LinkRequest
import com.ptk.anatomypro.api.LinkResponse
import com.ptk.anatomypro.api.NicknameRequest
import com.ptk.anatomypro.backend.ApiException
import com.ptk.anatomypro.backend.subscriptions.Entitlements
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

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

private fun Account.toResponse(linked: List<IdentityProvider> = emptyList()) = AccountResponse(
    id = id.toString(),
    nickname = nickname,
    linked = linked,
    nicknameLockedUntil = nicknameLockedUntil?.toString(),
)

private suspend fun Accounts.response(account: Account): AccountResponse =
    account.toResponse(withContext(Dispatchers.IO) { linked(account.id) })

/** `POST /v1/auth/anonymous`. Apart from the rest, because it is limited by address, not by token. */
fun Route.anonymousAccountRoute(accounts: Accounts) {
    post(ApiPaths.ANONYMOUS) {
        val created = withContext(Dispatchers.IO) { accounts.createAnonymous() }
        call.respond(HttpStatusCode.Created, AnonymousAccountResponse(created.account.toResponse(), created.deviceToken))
    }
}

fun Route.accountRoutes(
    accounts: Accounts,
    nicknames: NicknameRules,
    identities: Identities,
    verifier: IdentityVerifier,
    entitlements: Entitlements,
    now: () -> Instant,
) {
    val today: () -> LocalDate = { now().atOffset(ZoneOffset.UTC).toLocalDate() }

    get(ApiPaths.ACCOUNT) {
        call.respond(accounts.response(call.account(accounts)))
    }

    // Signing in (§5.3). The token is checked before anything is touched.
    post(ApiPaths.LINK) {
        val caller = call.account(accounts)
        val request = call.receive<LinkRequest>()
        val linked = withContext(Dispatchers.IO) {
            val subject = verifier.subjectOf(request.provider, request.idToken)
            identities.link(caller.id, request.provider, subject, now())
        }
        val account = withContext(Dispatchers.IO) { accounts.byId(linked.account) }!!
        call.respond(LinkResponse(accounts.response(account), merged = linked.merged))
    }

    post(ApiPaths.SIGN_OUT) {
        call.account(accounts)
        withContext(Dispatchers.IO) { accounts.signOut(call.bearerToken()!!) }
        call.respond(HttpStatusCode.NoContent)
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
            is RenameResult.Renamed -> call.respond(accounts.response(result.account))
            RenameResult.Taken -> throw ApiException(HttpStatusCode.Conflict, ApiErrorCode.NICKNAME_TAKEN)
        }
    }

    delete(ApiPaths.ACCOUNT) {
        val account = call.account(accounts)
        // A subscription is the store's to cancel, not ours (§5.4): one that is still running
        // goes on being charged for, and the person leaving is told so. Read before the
        // account goes, because afterwards it is nobody's.
        val stillPaying = withContext(Dispatchers.IO) {
            entitlements.paidUntil(account.id).also { accounts.delete(account) }
        }
        call.respond(AccountDeletedResponse(subscriptionStillActive = stillPaying != null))
    }
}
