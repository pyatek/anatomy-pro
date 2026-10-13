package com.ptk.anatomypro.backend

import com.ptk.anatomypro.api.ApiErrorCode
import com.ptk.anatomypro.api.ApiJson
import com.ptk.anatomypro.api.ApiPaths
import com.ptk.anatomypro.api.EntitlementsResponse
import com.ptk.anatomypro.api.PlanPeriod
import com.ptk.anatomypro.api.PlansResponse
import com.ptk.anatomypro.api.PurchaseVerdict
import com.ptk.anatomypro.backend.subscriptions.Store
import com.ptk.anatomypro.backend.subscriptions.StoreStatement
import com.ptk.anatomypro.backend.subscriptions.SubscriptionState
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Backend spec §8.1, for Google Play: what a purchase token is worth is what the Play
 * Developer API says of it — here a stand-in answering in the documented shape.
 */
class GooglePurchasesTest {

    /** Nobody is a subscriber by decree here: the gate is answered from purchases alone. */
    private val harness = DailyHarness(Settings(requestsPerTokenPerMinute = 10_000, accountsPerAddressPerMinute = 10_000))
    private val play get() = harness.stores.play
    private val inAMonth get() = harness.now.plus(Duration.ofDays(30))

    // --- what the store says, and what follows ------------------------------------------

    @Test
    fun an_active_purchase_makes_its_account_a_subscriber_until_it_expires() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.active(inAMonth)
        val player = player()
        assertEquals(EntitlementsResponse(subscribed = false), player.entitlements())
        assertEquals(HttpStatusCode.Forbidden, player.todayResponse().status)

        val bought = player.buyGoogle("tok-1")

        assertEquals(PurchaseVerdict.VERIFIED, bought.outcome)
        assertEquals(EntitlementsResponse(subscribed = true, expiresAtEpochMillis = inAMonth.toEpochMilli()), bought.entitlements)
        assertEquals(bought.entitlements, player.entitlements())
        assertEquals(HttpStatusCode.OK, player.todayResponse().status)
    }

    @Test
    fun a_subscription_stops_counting_the_moment_it_has_expired_whatever_was_last_heard() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.active(inAMonth)
        val player = player()
        player.buyGoogle("tok-1")

        harness.pass(Duration.ofDays(30).plusSeconds(1))

        assertEquals(EntitlementsResponse(subscribed = false), player.entitlements())
        assertEquals(ApiErrorCode.NOT_SUBSCRIBED, player.todayResponse().errorCode())
    }

    @Test
    fun a_cancelled_subscription_is_still_paid_for_until_it_runs_out() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.cancelled(inAMonth)
        val player = player()

        assertEquals(PurchaseVerdict.VERIFIED, player.buyGoogle("tok-1").outcome)
        assertTrue(player.entitlements().subscribed)
    }

    @Test
    fun a_subscription_in_its_grace_period_is_still_let_in() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.inGrace(harness.now.plus(Duration.ofDays(3)))

        assertEquals(PurchaseVerdict.VERIFIED, player().buyGoogle("tok-1").outcome)
    }

    @Test
    fun an_expired_purchase_unlocks_nothing_and_is_not_kept() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.expired(harness.now.minus(Duration.ofDays(2)))
        val player = player()

        val answer = player.buyGoogle("tok-1")

        assertEquals(PurchaseVerdict.INVALID, answer.outcome)
        assertFalse(answer.entitlements.subscribed)
        assertEquals(emptyList(), harness.rows("subscription"))
    }

    @Test
    fun a_purchase_on_hold_unlocks_nothing() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.onHold(harness.now.minus(Duration.ofDays(2)))

        assertEquals(PurchaseVerdict.INVALID, player().buyGoogle("tok-1").outcome)
    }

    @Test
    fun a_state_this_server_has_never_heard_of_unlocks_nothing() = harness.run {
        // Shut is the safe way to be wrong about a state Google adds later.
        play.purchases["tok-1"] = PlayAnswers.inAStateNotHeardOf(inAMonth)

        assertEquals(PurchaseVerdict.INVALID, player().buyGoogle("tok-1").outcome)
    }

    @Test
    fun a_token_google_does_not_know_is_invalid() = harness.run {
        val player = player()

        val answer = player.buyGoogle("made-up")

        assertEquals(PurchaseVerdict.INVALID, answer.outcome)
        assertFalse(player.entitlements().subscribed)
        assertEquals(emptyList(), harness.rows("subscription"))
    }

    @Test
    fun a_pending_purchase_is_recorded_and_unlocks_nothing_until_google_says_it_is_paid() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.pending()
        val player = player()

        val answer = player.buyGoogle("tok-1")

        assertEquals(PurchaseVerdict.PENDING, answer.outcome)
        assertFalse(answer.entitlements.subscribed)
        assertEquals(emptyList(), play.acknowledged)

        // The payment clears. The same token, presented again, is now worth something.
        play.purchases["tok-1"] = PlayAnswers.active(inAMonth)
        assertEquals(PurchaseVerdict.VERIFIED, player.buyGoogle("tok-1").outcome)
    }

    @Test
    fun a_purchase_of_something_that_is_not_on_sale_here_is_invalid_and_google_is_not_asked() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.active(inAMonth, product = "another.app.gold")

        assertEquals(PurchaseVerdict.INVALID, player().buyGoogle("tok-1", product = "another.app.gold").outcome)
        assertEquals(0, play.fetches)
    }

    @Test
    fun a_purchase_claimed_to_be_one_product_and_really_another_is_invalid() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.active(inAMonth, product = "another.app.gold")

        assertEquals(PurchaseVerdict.INVALID, player().buyGoogle("tok-1", product = TestStores.MONTHLY).outcome)
        assertEquals(emptyList(), harness.rows("subscription"))
    }

    @Test
    fun a_blank_or_absurdly_long_token_is_invalid_and_google_is_not_asked() = harness.run {
        val player = player()

        assertEquals(PurchaseVerdict.INVALID, player.buyGoogle(" ").outcome)
        assertEquals(PurchaseVerdict.INVALID, player.buyGoogle("x".repeat(5_000)).outcome)
        assertEquals(0, play.fetches)
    }

    @Test
    fun a_purchase_needs_an_account() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.active(inAMonth)

        val response = client.post(ApiPaths.GOOGLE_PURCHASE) {
            contentType(ContentType.Application.Json)
            setBody("""{"productId":"pro.monthly","purchaseToken":"tok-1"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(0, play.fetches)
    }

    @Test
    fun when_google_cannot_be_asked_the_answer_is_try_again_and_not_that_the_purchase_is_bad() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.active(inAMonth)
        play.down = true
        val player = player()

        val response = player.googlePurchaseResponse("tok-1")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        play.down = false
        assertEquals(PurchaseVerdict.VERIFIED, player.buyGoogle("tok-1").outcome)
    }

    @Test
    fun a_server_with_no_play_account_configured_says_so_rather_than_calling_a_real_purchase_bad() {
        val unconfigured = DailyHarness(Settings())
        testApplication {
            application { backend(unconfigured.database, Settings(plans = TestStores.PLANS), now = { unconfigured.now }) }
            val client = createClient { install(ContentNegotiation) { json(ApiJson) } }
            val player = Player.Scope(client, unconfigured).player()

            assertEquals(HttpStatusCode.ServiceUnavailable, player.googlePurchaseResponse("tok-1").status)
            assertEquals(HttpStatusCode.ServiceUnavailable, player.applePurchaseResponse("eyJ").status)
        }
    }

    // --- acknowledging (Google refunds a purchase nobody acknowledges within three days) --

    @Test
    fun a_verified_purchase_is_acknowledged_to_google_once() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.active(inAMonth, acknowledged = false)
        val player = player()

        player.buyGoogle("tok-1")
        player.buyGoogle("tok-1")

        assertEquals(listOf("tok-1"), play.acknowledged)
    }

    @Test
    fun a_purchase_that_could_not_be_acknowledged_is_still_verified_and_is_acknowledged_next_time() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.active(inAMonth, acknowledged = false)
        play.refusesToAcknowledge = true
        val player = player()

        assertEquals(PurchaseVerdict.VERIFIED, player.buyGoogle("tok-1").outcome)

        play.refusesToAcknowledge = false
        player.buyGoogle("tok-1")
        assertEquals(listOf("tok-1"), play.acknowledged)
    }

    @Test
    fun a_purchase_that_unlocks_nothing_is_not_acknowledged() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.expired(harness.now.minus(Duration.ofDays(1))).replace("STATE_ACKNOWLEDGED", "STATE_PENDING")

        player().buyGoogle("tok-1")

        assertEquals(emptyList(), play.acknowledged)
    }

    // --- one purchase, one owner (§8.1) -------------------------------------------------

    @Test
    fun the_same_purchase_presented_twice_is_one_row_and_changes_nothing_the_second_time() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.active(inAMonth)
        val player = player()
        val first = player.buyGoogle("tok-1")
        val before = harness.rows("subscription")

        val second = player.buyGoogle("tok-1")

        assertEquals(first, second)
        assertEquals(1, before.size)
        assertEquals(before, harness.rows("subscription"))
    }

    @Test
    fun a_purchase_another_account_owns_is_not_moved_and_the_newcomer_is_told_to_sign_in() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.active(inAMonth)
        val owner = player()
        owner.buyGoogle("tok-1")
        val before = harness.rows("subscription")
        val newcomer = player()

        val answer = newcomer.buyGoogle("tok-1")

        assertEquals(PurchaseVerdict.OWNED_BY_ANOTHER_ACCOUNT, answer.outcome)
        assertFalse(answer.entitlements.subscribed)
        assertFalse(newcomer.entitlements().subscribed)
        assertTrue(owner.entitlements().subscribed)
        assertEquals(before, harness.rows("subscription"))
    }

    @Test
    fun and_once_the_newcomer_signs_in_as_the_owner_the_subscription_is_theirs_too() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.active(inAMonth)
        val phone = player()
        phone.signIn("g-1")
        phone.buyGoogle("tok-1")
        val tablet = player()
        assertEquals(PurchaseVerdict.OWNED_BY_ANOTHER_ACCOUNT, tablet.buyGoogle("tok-1").outcome)

        tablet.signIn("g-1")

        assertTrue(tablet.entitlements().subscribed)
        assertEquals(PurchaseVerdict.VERIFIED, tablet.buyGoogle("tok-1").outcome)
    }

    @Test
    fun two_accounts_presenting_a_new_purchase_at_the_same_moment_leave_it_with_exactly_one_of_them() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.active(inAMonth)
        val players = List(6) { player() }

        val verdicts = coroutineScope { players.map { async { it.buyGoogle("tok-1").outcome } }.awaitAll() }

        assertEquals(1, verdicts.count { it == PurchaseVerdict.VERIFIED })
        assertEquals(5, verdicts.count { it == PurchaseVerdict.OWNED_BY_ANOTHER_ACCOUNT })
        assertEquals(1, harness.rows("subscription").size)
        assertEquals(1, players.count { it.entitlements().subscribed })
    }

    @Test
    fun a_lapsed_purchase_someone_else_owns_is_simply_invalid_to_a_newcomer() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.active(inAMonth)
        player().buyGoogle("tok-1")
        play.purchases["tok-1"] = PlayAnswers.expired(harness.now.minus(Duration.ofHours(1)))

        assertEquals(PurchaseVerdict.INVALID, player().buyGoogle("tok-1").outcome)
    }

    @Test
    fun an_account_with_two_subscriptions_is_a_subscriber_until_the_later_one_ends() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.active(harness.now.plus(Duration.ofDays(5)))
        play.purchases["tok-2"] = PlayAnswers.active(harness.now.plus(Duration.ofDays(300)), product = TestStores.YEARLY)
        val player = player()
        player.buyGoogle("tok-1")

        val both = player.buyGoogle("tok-2", product = TestStores.YEARLY)

        assertEquals(harness.now.plus(Duration.ofDays(300)).toEpochMilli(), both.entitlements.expiresAtEpochMillis)
    }

    @Test
    fun a_purchase_presented_by_an_account_that_has_just_gone_is_asked_for_again_and_given_to_nobody() = harness.run {
        // A merge or a deletion can remove the caller between its token being read and the
        // purchase being written. That is "ask again", not "invalid".
        val gone = java.util.UUID.randomUUID()
        val statement = StoreStatement(Store.GOOGLE, "tok-1", TestStores.MONTHLY, SubscriptionState.ACTIVE, inAMonth, harness.now)

        val refusal = assertFailsWith<ApiException> { harness.storeServices().subscriptions.record(statement, gone, harness.now) }

        assertEquals(HttpStatusCode.ServiceUnavailable, refusal.status)
        assertEquals(emptyList(), harness.rows("subscription"))
    }

    // --- a purchase that replaces another (an upgrade, or subscribing again) ------------

    @Test
    fun a_purchase_that_replaces_another_belongs_to_whoever_owned_the_one_it_replaces() = harness.run {
        play.purchases["tok-old"] = PlayAnswers.active(harness.now.plus(Duration.ofDays(3)))
        val owner = player()
        owner.buyGoogle("tok-old")
        play.purchases["tok-new"] = PlayAnswers.active(harness.now.plus(Duration.ofDays(365)), product = TestStores.YEARLY, linked = "tok-old")
        val newcomer = player()

        assertEquals(PurchaseVerdict.OWNED_BY_ANOTHER_ACCOUNT, newcomer.buyGoogle("tok-new", product = TestStores.YEARLY).outcome)

        assertEquals(harness.now.plus(Duration.ofDays(365)).toEpochMilli(), owner.entitlements().expiresAtEpochMillis)
        assertFalse(newcomer.entitlements().subscribed)
    }

    @Test
    fun and_the_purchase_it_replaced_is_finished_even_if_google_still_calls_it_active() = harness.run {
        play.purchases["tok-old"] = PlayAnswers.active(inAMonth)
        val owner = player()
        owner.buyGoogle("tok-old")
        play.purchases["tok-new"] = PlayAnswers.active(harness.now.plus(Duration.ofDays(7)), linked = "tok-old")
        owner.buyGoogle("tok-new")

        // The old token, presented again by anyone, is worth nothing now.
        assertEquals(PurchaseVerdict.INVALID, player().buyGoogle("tok-old").outcome)
        owner.buyGoogle("tok-old")
        assertEquals(harness.now.plus(Duration.ofDays(7)).toEpochMilli(), owner.entitlements().expiresAtEpochMillis)
    }

    // --- what is on sale (§8.3) ---------------------------------------------------------

    @Test
    fun the_plans_are_the_configured_product_ids_and_their_periods() = harness.run {
        val plans = client.get(ApiPaths.PLANS) { bearerAuth(player().token) }.body<PlansResponse>()

        assertEquals(listOf(TestStores.MONTHLY to PlanPeriod.MONTHLY, TestStores.YEARLY to PlanPeriod.YEARLY), plans.plans.map { it.productId to it.period })
    }

    // --- the override -------------------------------------------------------------------

    @Test
    fun with_everyone_declared_a_subscriber_the_gate_is_open_and_no_expiry_is_invented() {
        DailyHarness().run {
            val player = player()

            assertEquals(EntitlementsResponse(subscribed = true), player.entitlements())
            assertNull(player.entitlements().expiresAtEpochMillis)
            assertEquals(HttpStatusCode.OK, player.todayResponse().status)
        }
    }
}
