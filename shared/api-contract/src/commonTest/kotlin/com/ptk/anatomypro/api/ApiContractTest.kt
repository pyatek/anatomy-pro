package com.ptk.anatomypro.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The wire, pinned.
 *
 * Each JSON below is what version 1 of the other end sends, and it must keep decoding for
 * as long as that version is in anyone's hands. Adding a field without a default, renaming
 * one, or renaming an enum's wire name fails here — which is the point: the app in the
 * store cannot be redeployed with the server.
 */
class ApiContractTest {

    private inline fun <reified T> pinned(json: String): T {
        val serializer: KSerializer<T> = serializer()
        val decoded = ApiJson.decodeFromString(serializer, json)
        // And back: what one end writes, the other reads to the same value.
        assertEquals(decoded, ApiJson.decodeFromString(serializer, ApiJson.encodeToString(serializer, decoded)))
        return decoded
    }

    // --- identity (§5) ------------------------------------------------------------------

    @Test
    fun a_new_anonymous_account() {
        val account = pinned<AnonymousAccountResponse>(
            """{"account":{"id":"a1","nickname":"student-4821"},"deviceToken":"dG9rZW4"}""",
        )
        assertEquals("dG9rZW4", account.deviceToken)
        assertEquals("student-4821", account.account.nickname)
        assertEquals(emptyList(), account.account.linked)
        assertNull(account.account.nicknameLockedUntil)
    }

    @Test
    fun an_account_that_has_signed_in_and_lost_the_right_to_rename_itself() {
        val account = pinned<AccountResponse>(
            """{"id":"a1","nickname":"student-7","linked":["google","apple"],"nicknameLockedUntil":"2026-11-10"}""",
        )
        assertEquals(listOf(IdentityProvider.GOOGLE, IdentityProvider.APPLE), account.linked)
        assertEquals("2026-11-10", account.nicknameLockedUntil)
    }

    @Test
    fun identity_requests() {
        assertEquals("ala", pinned<NicknameRequest>("""{"nickname":"ala"}""").nickname)
        val link = pinned<LinkRequest>("""{"provider":"apple","idToken":"eyJ"}""")
        assertEquals(IdentityProvider.APPLE, link.provider)
        val linked = pinned<LinkResponse>("""{"account":{"id":"a2","nickname":"ala"},"merged":true}""")
        assertTrue(linked.merged)
        assertTrue(pinned<AccountDeletedResponse>("""{"subscriptionStillActive":true}""").subscriptionStillActive)
    }

    // --- the daily (§6) -----------------------------------------------------------------

    @Test
    fun a_day_not_yet_played() {
        val day = pinned<DailyStateResponse>("""{"date":"2026-10-12","state":"not_started","questionCount":10}""")
        assertEquals(DailyState.NOT_STARTED, day.state)
        assertEquals(10, day.questionCount)
        assertNull(day.attempt)
        assertNull(day.result)
    }

    @Test
    fun a_started_attempt_carries_its_questions_without_saying_which_option_is_right() {
        val json = """{"date":"2026-10-12","startedAtEpochMillis":1791800000000,"questions":[
            {"id":"q-1-0","highlighted":"costa-4-left","options":[
              {"id":"costa-4-left","name":"Costa IV","nameLocale":"la"},
              {"id":"musculus-1","name":"Mięsień","nameLocale":"pl","latinName":"Musculus"}]}]}"""
        val attempt = pinned<DailyAttemptResponse>(json)

        val question = attempt.questions.single()
        assertEquals("costa-4-left", question.highlighted)
        assertEquals(listOf(null, "Musculus"), question.options.map { it.latinName })
        assertTrue("correct" !in ApiJson.encodeToString(DailyAttemptResponse.serializer(), attempt))
    }

    @Test
    fun starting_and_submitting() {
        val start = pinned<DailyStartRequest>("""{"locale":"la"}""")
        // Timed unless the device says otherwise: only the untimed setting gives up a rank.
        assertTrue(start.timed)

        val submit = pinned<DailySubmitRequest>("""{"answers":[{"questionId":"q-1-0","chosen":"costa-4-left"},{"questionId":"q-1-1"}]}""")
        assertNull(submit.answers[1].chosen)
    }

    @Test
    fun a_result_ranked_and_one_that_is_not() {
        val ranked = pinned<DailyResultResponse>(
            """{"date":"2026-10-12","correct":9,"total":10,"elapsedMillis":41500,"ranked":true,"rank":17,"totalPlayers":412}""",
        )
        assertEquals(17, ranked.rank)

        val unranked = pinned<DailyResultResponse>(
            """{"date":"2026-10-12","correct":9,"total":10,"elapsedMillis":41500,"ranked":false,"totalPlayers":412}""",
        )
        assertNull(unranked.rank)
    }

    @Test
    fun a_completed_day_carries_its_result() {
        val day = pinned<DailyStateResponse>(
            """{"date":"2026-10-12","state":"completed","result":{"date":"2026-10-12","correct":3,"total":10,"elapsedMillis":9,"ranked":false,"totalPlayers":1}}""",
        )
        assertEquals(3, day.result?.correct)
    }

    // --- boards (§7) --------------------------------------------------------------------

    @Test
    fun a_board_with_the_callers_own_row() {
        val board = pinned<LeaderboardResponse>(
            """{"entries":[{"rank":1,"nickname":"ala","score":10,"elapsedMillis":30000}],
                "me":{"rank":17,"nickname":"ja","score":9,"elapsedMillis":41500,"isMe":true},"totalPlayers":412}""",
        )
        assertEquals(false, board.entries.single().isMe)
        assertEquals(17, board.me?.rank)
        assertNull(pinned<LeaderboardResponse>("""{"entries":[],"totalPlayers":0}""").me)
    }

    // --- subscriptions (§8) -------------------------------------------------------------

    @Test
    fun entitlements_plans_and_purchases() {
        assertNull(pinned<EntitlementsResponse>("""{"subscribed":false}""").expiresAtEpochMillis)
        assertEquals(1L, pinned<EntitlementsResponse>("""{"subscribed":true,"expiresAtEpochMillis":1}""").expiresAtEpochMillis)

        val plans = pinned<PlansResponse>("""{"plans":[{"productId":"pro.monthly","period":"monthly"},{"productId":"pro.yearly","period":"yearly"}]}""")
        assertEquals(listOf(PlanPeriod.MONTHLY, PlanPeriod.YEARLY), plans.plans.map { it.period })

        assertEquals("tok", pinned<GooglePurchaseRequest>("""{"productId":"pro.monthly","purchaseToken":"tok"}""").purchaseToken)
        assertEquals("eyJ", pinned<ApplePurchaseRequest>("""{"signedTransaction":"eyJ"}""").signedTransaction)

        val purchase = pinned<PurchaseResponse>("""{"outcome":"owned_by_another_account","entitlements":{"subscribed":false}}""")
        assertEquals(PurchaseVerdict.OWNED_BY_ANOTHER_ACCOUNT, purchase.outcome)
    }

    @Test
    fun a_purchase_verdict_has_the_wire_name_it_shipped_with_and_one_never_heard_of_is_read_as_invalid() {
        val names = PurchaseVerdict.entries.associateWith { ApiJson.encodeToString(PurchaseVerdict.serializer(), it).trim('"') }
        assertEquals(
            mapOf(
                PurchaseVerdict.VERIFIED to "verified",
                PurchaseVerdict.OWNED_BY_ANOTHER_ACCOUNT to "owned_by_another_account",
                PurchaseVerdict.PENDING to "pending",
                PurchaseVerdict.INVALID to "invalid",
            ),
            names,
        )
        // Added after version 1, with the purchase's own account none the richer for it.
        val pending = pinned<PurchaseResponse>("""{"outcome":"pending","entitlements":{"subscribed":false}}""")
        assertEquals(PurchaseVerdict.PENDING, pending.outcome)
        val newer = ApiJson.decodeFromString(PurchaseResponse.serializer(), """{"outcome":"refunded","entitlements":{"subscribed":false}}""")
        assertEquals(PurchaseVerdict.INVALID, newer.outcome)
    }

    // --- errors, and a newer server -----------------------------------------------------

    @Test
    fun an_error_names_its_code() {
        val error = pinned<ApiError>("""{"code":"not_subscribed","message":"The daily quiz needs a subscription."}""")
        assertEquals(ApiErrorCode.NOT_SUBSCRIBED, error.code)
        assertEquals("", pinned<ApiError>("""{"code":"rate_limited"}""").message)
    }

    @Test
    fun every_error_code_has_the_wire_name_the_spec_gives_it() {
        val names = ApiErrorCode.entries.associateWith { ApiJson.encodeToString(ApiErrorCode.serializer(), it).trim('"') }

        assertEquals("not_subscribed", names[ApiErrorCode.NOT_SUBSCRIBED])
        assertTrue(names.values.all { name -> name == name.lowercase() && ' ' !in name })
        assertEquals(names.size, names.values.toSet().size)
    }

    @Test
    fun a_field_a_newer_server_adds_is_ignored() {
        val board = pinned<LeaderboardResponse>("""{"entries":[],"totalPlayers":0,"season":"autumn","prizes":[1,2]}""")
        assertEquals(0, board.totalPlayers)
    }

    @Test
    fun a_code_or_state_a_newer_server_adds_is_read_as_unknown_rather_than_crashing() {
        assertEquals(ApiErrorCode.UNKNOWN, ApiJson.decodeFromString(ApiError.serializer(), """{"code":"account_frozen"}""").code)
        assertEquals(
            DailyState.UNAVAILABLE,
            ApiJson.decodeFromString(DailyStateResponse.serializer(), """{"date":"2026-10-12","state":"postponed"}""").state,
        )
    }

    // --- where things are ---------------------------------------------------------------

    @Test
    fun paths_are_versioned_and_built_in_one_place() {
        assertEquals("/v1/daily/2026-10-12/start", ApiPaths.dailyStart("2026-10-12"))
        assertEquals("/v1/daily/2026-10-12/submit", ApiPaths.dailySubmit("2026-10-12"))
        assertEquals("/v1/leaderboard/daily/2026-10-12", ApiPaths.dailyLeaderboard("2026-10-12"))
        assertTrue(
            listOf(
                ApiPaths.ANONYMOUS, ApiPaths.LINK, ApiPaths.SIGN_OUT, ApiPaths.ACCOUNT, ApiPaths.NICKNAME, ApiPaths.DAILY_TODAY,
                ApiPaths.STREAK_LEADERBOARD, ApiPaths.ENTITLEMENTS, ApiPaths.PLANS, ApiPaths.GOOGLE_PURCHASE, ApiPaths.APPLE_PURCHASE,
            ).all { it.startsWith("/v1/") },
        )
    }
}
