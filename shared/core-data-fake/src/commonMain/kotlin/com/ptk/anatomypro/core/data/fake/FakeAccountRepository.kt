package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.model.Account
import com.ptk.anatomypro.core.data.model.AccountDeleted
import com.ptk.anatomypro.core.data.model.RenameOutcome
import com.ptk.anatomypro.core.data.model.SignInOutcome
import com.ptk.anatomypro.core.data.model.SignInProvider
import com.ptk.anatomypro.core.data.model.nicknameIsWellFormed
import com.ptk.anatomypro.core.data.repository.AccountRepository
import com.ptk.anatomypro.core.data.repository.ProviderSignIn

/**
 * The account, as the server keeps it (backend spec §5), with each of its refusals to hand.
 *
 * [taken] are other people's nicknames. [elsewhere] is the account a sign-in opens when the
 * identity is already someone's: this device then becomes that account (§5.3, case 3).
 * [believesTokens] off is a token the provider did not sign for us. A [initial] that
 * carries a lock refuses every rename; the fake has no clock to let it run out.
 */
class FakeAccountRepository(
    private val behaviour: FakeBehaviour = FakeBehaviour(),
    initial: Account = Account(nickname = "student-4821"),
    private val taken: Set<String> = setOf("anatomia_ninja", "foramen", "vesalius_1543"),
    private val elsewhere: Account? = null,
    private val believesTokens: Boolean = true,
    private val subscribed: Boolean = false,
) : AccountRepository {

    private var current = initial
    private var made = 0

    override suspend fun account(): Account = behaviour.respond { current }

    override suspend fun rename(nickname: String): RenameOutcome = behaviour.respond {
        when {
            current.nicknameLockedUntil != null -> RenameOutcome.Locked
            !nicknameIsWellFormed(nickname) -> RenameOutcome.Invalid
            taken.any { it.equals(nickname, ignoreCase = true) } -> RenameOutcome.Taken
            else -> {
                current = current.copy(nickname = nickname)
                RenameOutcome.Renamed(current)
            }
        }
    }

    override suspend fun signIn(provider: SignInProvider, idToken: String): SignInOutcome = behaviour.respond {
        when {
            !believesTokens -> SignInOutcome.Rejected
            elsewhere != null -> {
                current = elsewhere.copy(linked = elsewhere.linked + provider)
                SignInOutcome.SignedIn(current, merged = true)
            }
            else -> {
                current = current.copy(linked = current.linked + provider)
                SignInOutcome.SignedIn(current, merged = false)
            }
        }
    }

    override suspend fun signOut() = behaviour.respond { current = someoneNew() }

    override suspend fun delete(): AccountDeleted = behaviour.respond {
        current = someoneNew()
        AccountDeleted(subscriptionStillActive = subscribed)
    }

    /** The anonymous account the server makes for a device it does not know (§5.1). */
    private fun someoneNew(): Account = Account(nickname = "student-${5000 + made++}")
}

/**
 * The platform's sign-in sheet, without the platform: it answers at once with a token no
 * server would believe, or with nothing where [cancels] stands for a sheet that was closed.
 */
class FakeProviderSignIn(
    override val providers: List<SignInProvider> = listOf(SignInProvider.GOOGLE, SignInProvider.APPLE),
    private val cancels: Boolean = false,
) : ProviderSignIn {
    override suspend fun idToken(provider: SignInProvider): String? = if (cancels) null else "fake-id-token"
}
