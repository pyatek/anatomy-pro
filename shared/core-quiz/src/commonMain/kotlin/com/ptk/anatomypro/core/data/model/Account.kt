package com.ptk.anatomypro.core.data.model

import kotlin.time.Instant

/** Who a person can sign in with (backend spec §5.3). */
enum class SignInProvider { GOOGLE, APPLE }

/**
 * All that is held about a person and shown back to them (backend spec §5.5): a nickname,
 * and which providers open the account. There is no email, name or picture to show.
 *
 * [nicknameLockedUntil] is set while a nickname upheld as abusive keeps the account from
 * choosing another (§5.2).
 */
data class Account(
    val nickname: String,
    val linked: Set<SignInProvider> = emptySet(),
    val nicknameLockedUntil: Instant? = null,
)

/**
 * Whether [nickname] has the shape §5.2 asks for: 3 to 20 letters, digits, `_` and `-`.
 *
 * Only the shape. Whether it is free, or on the blocklist, is the server's to say.
 */
fun nicknameIsWellFormed(nickname: String): Boolean =
    nickname.length in NICKNAME_MIN_LENGTH..NICKNAME_MAX_LENGTH &&
        nickname.all { it.isLetterOrDigit() || it == '_' || it == '-' }

const val NICKNAME_MIN_LENGTH = 3
const val NICKNAME_MAX_LENGTH = 20

/** A refusal is an answer, not a failure: each is said to the user in its own words. */
sealed interface RenameOutcome {
    data class Renamed(val account: Account) : RenameOutcome
    /** Not the shape §5.2 asks for, or on the blocklist. */
    data object Invalid : RenameOutcome
    /** Someone has it, whatever the case of its letters. */
    data object Taken : RenameOutcome
    /** The account may not choose one for now. Until when is on the [Account]. */
    data object Locked : RenameOutcome
}

sealed interface SignInOutcome {
    /**
     * [merged]: the identity already opened another account, and this device now speaks
     * for that one — its nickname and its results, not the ones it had (§5.3).
     */
    data class SignedIn(val account: Account, val merged: Boolean) : SignInOutcome
    /** The server did not believe the provider's token. */
    data object Rejected : SignInOutcome
}

/** Deleting an account does not cancel a subscription; only the store can (§5.4). */
data class AccountDeleted(val subscriptionStillActive: Boolean)
