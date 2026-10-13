package com.ptk.anatomypro.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the app and the server say to each other (backend spec §5 to §8).
 *
 * The rules that keep an app already in someone's hands working against a newer server:
 *
 * - a field added later has a default;
 * - an enum a client reads has an "unknown" to fall back to, and the property holding it
 *   defaults to that;
 * - a wire name, once shipped, is not changed. Enum constants carry theirs in `@SerialName`,
 *   so renaming the Kotlin constant is safe and renaming the string is not.
 *
 * `ApiContractTest` pins version 1 of every message and fails when one of these is broken.
 *
 * Ids and dates travel as plain strings: a date is `yyyy-MM-dd` in UTC (the daily quiz is
 * the world's day, all-screens §4.2), an instant is epoch milliseconds.
 */
val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    // An enum value this build has never heard of becomes the property's default.
    coerceInputValues = true
    // Absent means null or default; nothing is written out to say so.
    explicitNulls = false
}

/** Every path, so that neither end spells one by hand. */
object ApiPaths {
    const val ANONYMOUS = "/v1/auth/anonymous"
    const val LINK = "/v1/auth/link"
    const val SIGN_OUT = "/v1/auth/signout"
    const val ACCOUNT = "/v1/account"
    const val NICKNAME = "/v1/account/nickname"
    const val DAILY_TODAY = "/v1/daily/today"
    const val STREAK_LEADERBOARD = "/v1/leaderboard/streaks"
    const val ENTITLEMENTS = "/v1/entitlements"
    const val PLANS = "/v1/plans"
    const val GOOGLE_PURCHASE = "/v1/purchases/google"
    const val APPLE_PURCHASE = "/v1/purchases/apple"

    fun dailyStart(date: String) = "/v1/daily/$date/start"
    fun dailySubmit(date: String) = "/v1/daily/$date/submit"
    fun dailyLeaderboard(date: String) = "/v1/leaderboard/daily/$date"
}

// --- errors -----------------------------------------------------------------------------

@Serializable
enum class ApiErrorCode {
    @SerialName("unauthenticated") UNAUTHENTICATED,
    @SerialName("not_subscribed") NOT_SUBSCRIBED,
    @SerialName("invalid_request") INVALID_REQUEST,
    @SerialName("rate_limited") RATE_LIMITED,
    @SerialName("nickname_invalid") NICKNAME_INVALID,
    @SerialName("nickname_taken") NICKNAME_TAKEN,
    @SerialName("nickname_locked") NICKNAME_LOCKED,
    @SerialName("identity_invalid") IDENTITY_INVALID,
    @SerialName("daily_unavailable") DAILY_UNAVAILABLE,
    /** The date in the path is not the server's current UTC date (§6.2). */
    @SerialName("wrong_date") WRONG_DATE,
    /** A submit for a day that was never started. */
    @SerialName("not_started") NOT_STARTED,
    @SerialName("purchase_invalid") PURCHASE_INVALID,
    /** A code from a server newer than this build. */
    @SerialName("unknown") UNKNOWN,
}

/** The body of every response that is not a success. */
@Serializable
data class ApiError(val code: ApiErrorCode = ApiErrorCode.UNKNOWN, val message: String = "")

// --- identity (§5) ----------------------------------------------------------------------

@Serializable
enum class IdentityProvider {
    @SerialName("google") GOOGLE,
    @SerialName("apple") APPLE,
}

/** All that is held about a person and shown back to them (§5.5). */
@Serializable
data class AccountResponse(
    val id: String,
    val nickname: String,
    val linked: List<IdentityProvider> = emptyList(),
    /** Set while a nickname upheld as abusive keeps the account from choosing another (§5.2). */
    val nicknameLockedUntil: String? = null,
)

/** [deviceToken] is shown this once; the server keeps only its hash (§5.1). */
@Serializable
data class AnonymousAccountResponse(val account: AccountResponse, val deviceToken: String)

@Serializable
data class NicknameRequest(val nickname: String)

@Serializable
data class LinkRequest(val provider: IdentityProvider, val idToken: String)

/** [merged]: the identity belonged to another account, and this device now speaks for that one (§5.3). */
@Serializable
data class LinkResponse(val account: AccountResponse, val merged: Boolean = false)

/** Deleting an account does not cancel a subscription; only the store can (§5.4). */
@Serializable
data class AccountDeletedResponse(val subscriptionStillActive: Boolean = false)

// --- the daily (§6) ---------------------------------------------------------------------

@Serializable
enum class DailyState {
    @SerialName("not_started") NOT_STARTED,
    @SerialName("in_progress") IN_PROGRESS,
    @SerialName("completed") COMPLETED,
    /** No eligible topic today (§4.2) — and what an unknown state is read as. */
    @SerialName("unavailable") UNAVAILABLE,
}

@Serializable
data class DailyStateResponse(
    val date: String,
    val state: DailyState = DailyState.UNAVAILABLE,
    val questionCount: Int = 0,
    /** Present while [state] is in progress. */
    val attempt: DailyAttemptResponse? = null,
    /** Present once [state] is completed. */
    val result: DailyResultResponse? = null,
)

@Serializable
data class DailyStartRequest(
    /** The examination locale the options are named in. */
    val locale: String,
    /** False when the user has turned the timer off: the attempt is scored and not ranked (§6.3). */
    val timed: Boolean = true,
)

/**
 * A name as every ranked player reads it, whatever version of the pack they hold.
 * [nameLocale] is the language [name] is really in: Latin where the locale has none.
 */
@Serializable
data class DailyOption(val id: String, val name: String, val nameLocale: String, val latinName: String? = null)

/**
 * Which option is right is not sent. It is not a secret — the app holds the atlas and
 * [highlighted] names the structure (§4.1) — and the app works it out from exactly that.
 */
@Serializable
data class DailyQuestion(val id: String, val highlighted: String, val options: List<DailyOption>)

@Serializable
data class DailyAttemptResponse(val date: String, val startedAtEpochMillis: Long, val questions: List<DailyQuestion>)

/** [chosen] is absent for a question that ran out of time. */
@Serializable
data class DailyAnswer(val questionId: String, val chosen: String? = null)

@Serializable
data class DailySubmitRequest(val answers: List<DailyAnswer>)

/** [elapsedMillis] is the server's, from start to submit. [rank] is absent when not [ranked]. */
@Serializable
data class DailyResultResponse(
    val date: String,
    val correct: Int,
    val total: Int,
    val elapsedMillis: Long,
    val totalPlayers: Int,
    val ranked: Boolean = false,
    val rank: Int? = null,
)

// --- boards (§7) ------------------------------------------------------------------------

/** [score] is correct answers on the daily board and days in a row on the streak board. */
@Serializable
data class LeaderboardRow(
    val rank: Int,
    val nickname: String,
    val score: Int,
    val elapsedMillis: Long = 0,
    val isMe: Boolean = false,
)

/** The caller's own row comes with the board, so the two cannot disagree. */
@Serializable
data class LeaderboardResponse(val entries: List<LeaderboardRow>, val totalPlayers: Int, val me: LeaderboardRow? = null)

// --- subscriptions (§8) -----------------------------------------------------------------

@Serializable
data class EntitlementsResponse(val subscribed: Boolean = false, val expiresAtEpochMillis: Long? = null)

@Serializable
enum class PlanPeriod {
    @SerialName("monthly") MONTHLY,
    @SerialName("yearly") YEARLY,
}

/** Prices are the store's to state, on the device; the server knows only what is on sale. */
@Serializable
data class Plan(val productId: String, val period: PlanPeriod = PlanPeriod.MONTHLY)

@Serializable
data class PlansResponse(val plans: List<Plan> = emptyList())

@Serializable
data class GooglePurchaseRequest(val productId: String, val purchaseToken: String)

/** StoreKit 2's signed transaction, as the device received it. */
@Serializable
data class ApplePurchaseRequest(val signedTransaction: String)

@Serializable
enum class PurchaseVerdict {
    @SerialName("verified") VERIFIED,
    /** The purchase is real and belongs to another account: sign in to reach it (§8.1). */
    @SerialName("owned_by_another_account") OWNED_BY_ANOTHER_ACCOUNT,
    /**
     * The purchase is real and is this account's, and the store has not been paid yet (a
     * slow payment method). Nothing is unlocked; it will be without being presented again.
     * Added after version 1: a build that has not heard of it reads it as [INVALID].
     */
    @SerialName("pending") PENDING,
    /** The store does not know it, or it has lapsed — and what an unknown verdict is read as. */
    @SerialName("invalid") INVALID,
}

/** What the server concluded, which is the only thing that unlocks anything (§8.1). */
@Serializable
data class PurchaseResponse(val entitlements: EntitlementsResponse, val outcome: PurchaseVerdict = PurchaseVerdict.INVALID)
