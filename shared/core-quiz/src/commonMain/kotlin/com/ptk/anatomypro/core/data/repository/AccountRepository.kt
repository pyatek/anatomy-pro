package com.ptk.anatomypro.core.data.repository

import com.ptk.anatomypro.core.data.model.Account
import com.ptk.anatomypro.core.data.model.AccountDeleted
import com.ptk.anatomypro.core.data.model.RenameOutcome
import com.ptk.anatomypro.core.data.model.SignInOutcome
import com.ptk.anatomypro.core.data.model.SignInProvider

/**
 * The account this device speaks for (backend spec §5). No prototype screen: the account
 * screen was drafted after it.
 *
 * What the server refuses on purpose comes back as an outcome. Anything else — no
 * connection, a server that is down — is thrown.
 */
interface AccountRepository {
    /** Reading it is what makes the anonymous account, where the device has none yet (§5.1). */
    suspend fun account(): Account

    suspend fun rename(nickname: String): RenameOutcome

    /** [idToken] is the provider's, as [ProviderSignIn] hands it over; the server verifies it. */
    suspend fun signIn(provider: SignInProvider, idToken: String): SignInOutcome

    /** Ends this device's access. It starts again as someone new the next time it asks. */
    suspend fun signOut()

    /** Deletes the account everywhere (§5.4). */
    suspend fun delete(): AccountDeleted
}

/**
 * The platform's own sign-in sheet: Credential Manager on Android, AuthenticationServices
 * on iOS. Apart from [AccountRepository] because it is a piece of the device, not of the
 * server, and needs an activity or a window to show itself in.
 */
interface ProviderSignIn {
    /** What this device can sign in with. Empty where nothing is, and then nothing is offered. */
    val providers: List<SignInProvider>

    /** The provider's ID token, or null where the user closed the sheet. */
    suspend fun idToken(provider: SignInProvider): String?
}
