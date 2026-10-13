package com.ptk.anatomypro.backend

import io.ktor.http.HttpStatusCode
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Backend spec §8.1: App Store server notifications, version 2, to `/v1/webhooks/apple`.
 * The notification is its own proof: Apple signed it, and the transaction inside it.
 */
class AppleNotificationsTest {

    private val harness = DailyHarness(Settings(requestsPerTokenPerMinute = 10_000, accountsPerAddressPerMinute = 10_000))
    private val apple get() = harness.stores.apple
    private val original = "1000000000000001"

    /** A subscriber whose subscription runs out tomorrow, and a little time gone by since they bought it. */
    private suspend fun Player.Scope.subscriber(): Player {
        val player = player()
        player.buyApple(apple.transaction(original, harness.now.plus(Duration.ofDays(1))))
        harness.pass(Duration.ofHours(20))
        return player
    }

    // --- what a notification does -------------------------------------------------------

    @Test
    fun a_renewal_is_heard_of_without_the_app_being_opened() = harness.run {
        val player = subscriber()
        val renewedUntil = harness.now.plus(Duration.ofDays(31))

        val response = appleWebhook(apple.notification("DID_RENEW", apple.transaction(original, renewedUntil)))

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(renewedUntil.toEpochMilli(), player.entitlements().expiresAtEpochMillis)
    }

    @Test
    fun a_refund_locks_the_account_again_without_the_app_being_opened() = harness.run {
        val player = subscriber()
        assertEquals(HttpStatusCode.OK, player.todayResponse().status)

        appleWebhook(apple.notification("REFUND", apple.transaction(original, harness.now.plus(Duration.ofHours(4)), revoked = harness.now)))

        assertFalse(player.entitlements().subscribed)
        assertEquals(HttpStatusCode.Forbidden, player.todayResponse().status)
    }

    @Test
    fun a_revoked_family_share_locks_the_account_too() = harness.run {
        val player = subscriber()

        appleWebhook(apple.notification("REVOKE", apple.transaction(original, harness.now.plus(Duration.ofHours(4)), revoked = harness.now)))

        assertFalse(player.entitlements().subscribed)
    }

    @Test
    fun an_expiry_is_written_down() = harness.run {
        val player = subscriber()
        harness.pass(Duration.ofHours(5))

        appleWebhook(apple.notification("EXPIRED", apple.transaction(original, harness.now.minus(Duration.ofHours(1))), subtype = "VOLUNTARY"))

        assertFalse(player.entitlements().subscribed)
        assertEquals(listOf<String?>("EXPIRED"), harness.column("SELECT state FROM subscription"))
    }

    @Test
    fun turning_renewal_off_takes_nothing_away_before_the_time_paid_for_is_up() = harness.run {
        val player = subscriber()
        val paidUntil = player.entitlements().expiresAtEpochMillis

        appleWebhook(
            apple.notification(
                "DID_CHANGE_RENEWAL_STATUS", apple.transaction(original, harness.now.plus(Duration.ofHours(4))), subtype = "AUTO_RENEW_DISABLED",
            ),
        )

        assertEquals(paidUntil, player.entitlements().expiresAtEpochMillis)
    }

    @Test
    fun a_failed_renewal_with_a_grace_period_keeps_the_subscriber_in_until_the_grace_ends() = harness.run {
        val player = subscriber()
        harness.pass(Duration.ofHours(5))
        assertFalse(player.entitlements().subscribed)
        val graceUntil = harness.now.plus(Duration.ofDays(6))

        appleWebhook(
            apple.notification(
                "DID_FAIL_TO_RENEW",
                apple.transaction(original, harness.now.minus(Duration.ofHours(1))),
                subtype = "GRACE_PERIOD",
                renewal = apple.renewalInfo(original, graceUntil = graceUntil),
            ),
        )

        assertEquals(graceUntil.toEpochMilli(), player.entitlements().expiresAtEpochMillis)
    }

    @Test
    fun a_notification_about_a_subscription_nobody_presented_gives_it_to_nobody() = harness.run {
        val bystander = player()

        val response = appleWebhook(apple.notification("SUBSCRIBED", apple.transaction("1000000000000099", harness.now.plus(Duration.ofDays(30)))))

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(emptyList(), harness.rows("subscription"))
        assertFalse(bystander.entitlements().subscribed)
    }

    @Test
    fun a_test_notification_is_accepted_and_does_nothing() = harness.run {
        assertEquals(HttpStatusCode.NoContent, appleWebhook(apple.notification("TEST", transaction = null)).status)
    }

    // --- delivered twice, or late (§8.1) ------------------------------------------------

    @Test
    fun a_notification_delivered_twice_changes_nothing_the_second_time() = harness.run {
        subscriber()
        val renewal = apple.notification("DID_RENEW", apple.transaction(original, harness.now.plus(Duration.ofDays(31))), uuid = "5f1c7c0e-0000-4000-8000-000000000001")
        appleWebhook(renewal)
        val subscriptions = harness.rows("subscription")
        val heard = harness.rows("store_notification")
        harness.pass(Duration.ofMinutes(10))

        val again = appleWebhook(renewal)

        assertEquals(HttpStatusCode.NoContent, again.status)
        assertEquals(subscriptions, harness.rows("subscription"))
        assertEquals(heard, harness.rows("store_notification"))
        assertEquals(1, heard.size)
    }

    @Test
    fun a_notification_that_arrives_after_a_later_one_does_not_undo_it() = harness.run {
        val player = subscriber()
        val renewal = apple.notification("DID_RENEW", apple.transaction(original, harness.now.plus(Duration.ofDays(31))))
        harness.pass(Duration.ofHours(2))
        val refund = apple.notification("REFUND", apple.transaction(original, harness.now.plus(Duration.ofDays(31)), revoked = harness.now))

        // Apple retries what it could not deliver: the refund lands, then the older renewal.
        appleWebhook(refund)
        appleWebhook(renewal)

        assertFalse(player.entitlements().subscribed)
    }

    // --- who is speaking ----------------------------------------------------------------

    @Test
    fun a_notification_apple_did_not_sign_is_refused_and_changes_nothing() = harness.run {
        val player = subscriber()
        val before = harness.rows("subscription")
        val forger = TestChain(harness.now)
        val refund = apple.transaction(original, harness.now.plus(Duration.ofHours(4)), revoked = harness.now, signer = forger)

        assertEquals(HttpStatusCode.Unauthorized, appleWebhook(apple.notification("REFUND", refund, signer = forger)).status)
        assertEquals(HttpStatusCode.Unauthorized, appleWebhook("""{"signedPayload":"eyJhbGciOiJub25lIn0.e30."}""").status)
        assertEquals(HttpStatusCode.BadRequest, appleWebhook("""{"hello":"there"}""").status)
        assertEquals(HttpStatusCode.BadRequest, appleWebhook("not json").status)

        assertTrue(player.entitlements().subscribed)
        assertEquals(before, harness.rows("subscription"))
        assertEquals(emptyList(), harness.rows("store_notification"))
    }

    @Test
    fun a_genuine_notification_wrapped_around_a_forged_transaction_changes_nothing() = harness.run {
        // Both layers are signed, and both are checked: the outer one alone proves nothing
        // about a transaction somebody else could have put inside a payload of their own.
        val player = subscriber()
        val forger = TestChain(harness.now)
        val forged = apple.transaction(original, harness.now.plus(Duration.ofDays(3650)), signer = forger)

        appleWebhook(apple.notification("DID_RENEW", forged))

        assertEquals(harness.now.plus(Duration.ofHours(4)).toEpochMilli(), player.entitlements().expiresAtEpochMillis)
    }

    @Test
    fun a_notification_for_another_app_or_from_the_sandbox_changes_nothing() = harness.run {
        val player = subscriber()
        val before = harness.rows("subscription")
        val refund = apple.transaction(original, harness.now.plus(Duration.ofHours(4)), revoked = harness.now)

        assertEquals(HttpStatusCode.NoContent, appleWebhook(apple.notification("REFUND", refund, bundle = "com.someone.else")).status)
        assertEquals(HttpStatusCode.NoContent, appleWebhook(apple.notification("REFUND", refund, environment = "Sandbox")).status)
        val sandboxRefund = apple.transaction(original, harness.now.plus(Duration.ofHours(4)), revoked = harness.now, environment = "Sandbox")
        assertEquals(HttpStatusCode.NoContent, appleWebhook(apple.notification("REFUND", sandboxRefund)).status)

        assertTrue(player.entitlements().subscribed)
        assertEquals(before, harness.rows("subscription"))
    }
}
