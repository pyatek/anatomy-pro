package com.ptk.anatomypro.api.client

import com.ptk.anatomypro.api.AccountResponse
import com.ptk.anatomypro.api.ApiErrorCode
import com.ptk.anatomypro.api.IdentityProvider
import com.ptk.anatomypro.core.data.model.Account
import com.ptk.anatomypro.core.data.model.AccountDeleted
import com.ptk.anatomypro.core.data.model.RenameOutcome
import com.ptk.anatomypro.core.data.model.SignInOutcome
import com.ptk.anatomypro.core.data.model.SignInProvider
import com.ptk.anatomypro.core.data.repository.AccountRepository
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus

/**
 * The account screen's contract, over the server (backend spec §5).
 *
 * What the server refuses on purpose becomes an outcome the screen has words for. Anything
 * else — no connection, a server that is down, a refusal with no meaning here — is thrown,
 * and the screen says it could not be done.
 */
class ServerAccountRepository(private val server: ServerAccount) : AccountRepository {

    override suspend fun account(): Account = server.read().toModel()

    override suspend fun rename(nickname: String): RenameOutcome = try {
        RenameOutcome.Renamed(server.rename(nickname).toModel())
    } catch (refused: ApiFailure) {
        when (refused.code) {
            ApiErrorCode.NICKNAME_INVALID -> RenameOutcome.Invalid
            ApiErrorCode.NICKNAME_TAKEN -> RenameOutcome.Taken
            ApiErrorCode.NICKNAME_LOCKED -> RenameOutcome.Locked
            else -> throw refused
        }
    }

    override suspend fun signIn(provider: SignInProvider, idToken: String): SignInOutcome = try {
        val linked = server.link(
            when (provider) {
                SignInProvider.GOOGLE -> IdentityProvider.GOOGLE
                SignInProvider.APPLE -> IdentityProvider.APPLE
            },
            idToken,
        )
        SignInOutcome.SignedIn(linked.account.toModel(), linked.merged)
    } catch (refused: ApiFailure) {
        if (refused.code != ApiErrorCode.IDENTITY_INVALID) throw refused
        SignInOutcome.Rejected
    }

    override suspend fun signOut() = server.signOut()

    override suspend fun delete(): AccountDeleted = AccountDeleted(server.delete().subscriptionStillActive)

    private fun AccountResponse.toModel() = Account(
        nickname = nickname,
        linked = linked.mapTo(mutableSetOf()) {
            when (it) {
                IdentityProvider.GOOGLE -> SignInProvider.GOOGLE
                IdentityProvider.APPLE -> SignInProvider.APPLE
            }
        },
        // The server names the last day the lock holds, in its own day, which is UTC. What
        // the screen wants is the first moment a new nickname can be chosen.
        nicknameLockedUntil = nicknameLockedUntil
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.plus(DatePeriod(days = 1))
            ?.atStartOfDayIn(TimeZone.UTC),
    )
}
