package com.ptk.anatomypro.backend

import com.ptk.anatomypro.api.ApiJson
import com.ptk.anatomypro.api.EntitlementsResponse
import com.ptk.anatomypro.api.PurchaseVerdict
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Backend spec §8.1, for the App Store: a signed transaction is believed when Apple's chain
 * signed it — here a chain of the test's own, standing where Apple's would.
 */
class ApplePurchasesTest {

    private val harness = DailyHarness(Settings(requestsPerTokenPerMinute = 10_000, accountsPerAddressPerMinute = 10_000))
    private val apple get() = harness.stores.apple
    private val inAMonth get() = harness.now.plus(Duration.ofDays(30))

    @Test
    fun a_signed_transaction_that_has_not_expired_makes_its_account_a_subscriber() = harness.run {
        val player = player()
        assertEquals(HttpStatusCode.Forbidden, player.todayResponse().status)

        val bought = player.buyApple(apple.transaction("1000000000000001", inAMonth))

        assertEquals(PurchaseVerdict.VERIFIED, bought.outcome)
        assertEquals(EntitlementsResponse(subscribed = true, expiresAtEpochMillis = inAMonth.toEpochMilli()), bought.entitlements)
        assertEquals(HttpStatusCode.OK, player.todayResponse().status)
    }

    @Test
    fun an_expired_transaction_unlocks_nothing_and_is_not_kept() = harness.run {
        val answer = player().buyApple(apple.transaction("1000000000000001", harness.now.minusSeconds(60)))

        assertEquals(PurchaseVerdict.INVALID, answer.outcome)
        assertEquals(emptyList(), harness.rows("subscription"))
    }

    @Test
    fun a_refunded_transaction_unlocks_nothing_though_its_expiry_is_still_ahead() = harness.run {
        val answer = player().buyApple(apple.transaction("1000000000000001", inAMonth, revoked = harness.now.minusSeconds(60)))

        assertEquals(PurchaseVerdict.INVALID, answer.outcome)
        assertFalse(answer.entitlements.subscribed)
    }

    @Test
    fun a_transaction_apple_did_not_sign_is_invalid() = harness.run {
        val forger = TestChain(harness.now)
        val player = player()

        val answer = player.buyApple(apple.transaction("1000000000000001", inAMonth, signer = forger))

        assertEquals(PurchaseVerdict.INVALID, answer.outcome)
        assertEquals(PurchaseVerdict.INVALID, player.buyApple("eyJhbGciOiJub25lIn0.e30.").outcome)
        assertEquals(PurchaseVerdict.INVALID, player.buyApple("").outcome)
        assertEquals(emptyList(), harness.rows("subscription"))
    }

    @Test
    fun a_genuine_transaction_for_another_app_or_another_product_is_invalid() = harness.run {
        val player = player()

        assertEquals(PurchaseVerdict.INVALID, player.buyApple(apple.transaction("1000000000000001", inAMonth, bundle = "com.someone.else")).outcome)
        assertEquals(PurchaseVerdict.INVALID, player.buyApple(apple.transaction("1000000000000002", inAMonth, product = "pro.lifetime")).outcome)
        assertEquals(
            PurchaseVerdict.INVALID,
            player.buyApple(apple.transaction("1000000000000003", inAMonth, type = "Non-Consumable")).outcome,
        )
        assertEquals(emptyList(), harness.rows("subscription"))
    }

    @Test
    fun a_sandbox_purchase_costs_nothing_and_is_worth_nothing_unless_the_server_is_told_to_take_them() = harness.run {
        val answer = player().buyApple(apple.transaction("2000000000000001", inAMonth, environment = "Sandbox"))

        assertEquals(PurchaseVerdict.INVALID, answer.outcome)
    }

    @Test
    fun a_server_told_to_take_sandbox_purchases_takes_them() {
        val reviewing = DailyHarness(Settings(requestsPerTokenPerMinute = 10_000, accountsPerAddressPerMinute = 10_000))
        testApplication {
            val settings = reviewing.serverSettings.let { it.copy(apple = it.apple!!.copy(environments = setOf("Production", "Sandbox"))) }
            application { backend(reviewing.database, settings, now = { reviewing.now }) }
            val client = createClient { install(ContentNegotiation) { json(ApiJson) } }
            val player = Player.Scope(client, reviewing).player()

            val answer = player.buyApple(reviewing.stores.apple.transaction("2000000000000001", reviewing.now.plusSeconds(300), environment = "Sandbox"))

            assertEquals(PurchaseVerdict.VERIFIED, answer.outcome)
        }
    }

    // --- one purchase, one owner (§8.1) -------------------------------------------------

    @Test
    fun the_same_transaction_presented_twice_is_one_row_and_changes_nothing_the_second_time() = harness.run {
        val transaction = apple.transaction("1000000000000001", inAMonth)
        val player = player()
        val first = player.buyApple(transaction)
        val before = harness.rows("subscription")

        val second = player.buyApple(transaction)

        assertEquals(first, second)
        assertEquals(1, before.size)
        assertEquals(before, harness.rows("subscription"))
    }

    @Test
    fun a_renewal_is_the_same_subscription_with_a_later_expiry() = harness.run {
        val player = player()
        player.buyApple(apple.transaction("1000000000000001", harness.now.plus(Duration.ofDays(1))))
        harness.pass(Duration.ofHours(23))

        val renewed = player.buyApple(apple.transaction("1000000000000001", harness.now.plus(Duration.ofDays(31))))

        assertEquals(harness.now.plus(Duration.ofDays(31)).toEpochMilli(), renewed.entitlements.expiresAtEpochMillis)
        assertEquals(1, harness.rows("subscription").size)
    }

    @Test
    fun a_purchase_another_account_owns_is_not_moved_and_the_newcomer_is_told_to_sign_in() = harness.run {
        val owner = player()
        owner.buyApple(apple.transaction("1000000000000001", inAMonth))
        val before = harness.column("SELECT account_id::text FROM subscription")
        val newcomer = player()

        // A restore on a new device: the same subscription, a transaction signed just now.
        harness.pass(Duration.ofMinutes(5))
        val answer = newcomer.buyApple(apple.transaction("1000000000000001", inAMonth))

        assertEquals(PurchaseVerdict.OWNED_BY_ANOTHER_ACCOUNT, answer.outcome)
        assertFalse(answer.entitlements.subscribed)
        assertTrue(owner.entitlements().subscribed)
        assertEquals(before, harness.column("SELECT account_id::text FROM subscription"))
        assertEquals(1, before.size)
    }

    @Test
    fun an_old_transaction_cannot_bring_back_a_subscription_since_refunded() = harness.run {
        // Signed before the refund, and kept. Its expiry is still ahead; it is still Apple's
        // signature. It is also no longer true.
        val beforeTheRefund = apple.transaction("1000000000000001", inAMonth)
        val player = player()
        player.buyApple(beforeTheRefund)
        harness.pass(Duration.ofDays(2))
        player.buyApple(apple.transaction("1000000000000001", inAMonth, revoked = harness.now))
        assertFalse(player.entitlements().subscribed)

        val replayed = player.buyApple(beforeTheRefund)

        assertEquals(PurchaseVerdict.INVALID, replayed.outcome)
        assertFalse(player.entitlements().subscribed)
        assertEquals(PurchaseVerdict.INVALID, player().buyApple(beforeTheRefund).outcome)
    }
}
