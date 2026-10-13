package com.ptk.anatomypro.backend

import com.ptk.anatomypro.backend.subscriptions.Rechecked
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Backend spec §8.1: subscriptions near their expiry are asked about again, so a
 * notification that never came cannot keep someone out, or in, for long.
 */
class RecheckTest {

    private val harness = DailyHarness(Settings(requestsPerTokenPerMinute = 10_000, accountsPerAddressPerMinute = 10_000))
    private val play get() = harness.stores.play
    private val apple get() = harness.stores.apple
    private val services by lazy { harness.storeServices() }

    private fun fromNow(time: Duration) = harness.now.plus(time)

    // --- Google -------------------------------------------------------------------------

    @Test
    fun a_renewal_whose_notification_never_came_is_found() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.active(fromNow(Duration.ofHours(30)))
        val player = player()
        player.buyGoogle("tok-1")
        harness.pass(Duration.ofHours(10))
        val renewedUntil = fromNow(Duration.ofDays(30))
        play.purchases["tok-1"] = PlayAnswers.active(renewedUntil)

        val done = services.recheck()

        assertEquals(Rechecked(asked = 1, failed = 0), done)
        assertEquals(renewedUntil.toEpochMilli(), player.entitlements().expiresAtEpochMillis)
    }

    @Test
    fun a_refund_whose_notification_never_came_is_found_when_the_subscription_nears_its_end() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.active(fromNow(Duration.ofHours(12)))
        val player = player()
        player.buyGoogle("tok-1")
        play.purchases["tok-1"] = PlayAnswers.expired(harness.now.minusSeconds(60))

        services.recheck()

        assertFalse(player.entitlements().subscribed)
    }

    @Test
    fun someone_who_paid_and_was_shut_out_by_a_lost_renewal_is_let_back_in() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.active(fromNow(Duration.ofDays(2)))
        val player = player()
        player.buyGoogle("tok-1")
        harness.pass(Duration.ofDays(4))
        assertFalse(player.entitlements().subscribed)
        play.purchases["tok-1"] = PlayAnswers.active(fromNow(Duration.ofDays(28)), acknowledged = false)

        services.recheck()

        assertTrue(player.entitlements().subscribed)
        assertEquals(listOf("tok-1"), play.acknowledged)
    }

    @Test
    fun a_purchase_waiting_to_be_paid_for_is_asked_about_until_it_is_or_a_week_has_gone() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.pending()
        val player = player()
        player.buyGoogle("tok-1")
        assertEquals(Rechecked(asked = 1, failed = 0), services.recheck())
        assertFalse(player.entitlements().subscribed)

        harness.pass(Duration.ofDays(2))
        play.purchases["tok-1"] = PlayAnswers.active(fromNow(Duration.ofDays(30)))
        services.recheck()
        assertTrue(player.entitlements().subscribed)

        play.purchases["tok-never"] = PlayAnswers.pending()
        player().buyGoogle("tok-never")
        harness.pass(Duration.ofDays(8))
        assertEquals(Rechecked(asked = 0, failed = 0), services.recheck())
    }

    @Test
    fun a_subscription_with_days_to_run_or_long_since_over_is_not_asked_about() = harness.run {
        play.purchases["tok-far"] = PlayAnswers.active(fromNow(Duration.ofDays(20)))
        play.purchases["tok-gone"] = PlayAnswers.active(fromNow(Duration.ofDays(1)))
        player().buyGoogle("tok-far")
        player().buyGoogle("tok-gone")
        harness.pass(Duration.ofDays(10))
        val asked = play.fetches

        assertEquals(Rechecked(asked = 0, failed = 0), services.recheck())
        assertEquals(asked, play.fetches)
    }

    @Test
    fun a_recheck_run_twice_changes_nothing_the_second_time() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.active(fromNow(Duration.ofHours(12)))
        player().buyGoogle("tok-1")
        play.purchases["tok-1"] = PlayAnswers.active(fromNow(Duration.ofDays(30)))
        services.recheck()
        val before = harness.rows("subscription")

        assertEquals(Rechecked(asked = 0, failed = 0), services.recheck())
        assertEquals(before, harness.rows("subscription"))
    }

    @Test
    fun a_subscription_whose_account_was_deleted_is_still_kept_true_and_still_lets_nobody_in() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.active(fromNow(Duration.ofHours(12)))
        val player = player()
        player.buyGoogle("tok-1")
        client.delete(com.ptk.anatomypro.api.ApiPaths.ACCOUNT) { bearerAuth(player.token) }
        play.purchases["tok-1"] = PlayAnswers.expired(harness.now.minusSeconds(60))

        assertEquals(Rechecked(asked = 1, failed = 0), services.recheck())

        assertEquals(listOf<String?>("EXPIRED"), harness.column("SELECT state FROM subscription"))
        assertEquals(listOf<String?>(null), harness.column("SELECT account_id::text FROM subscription"))
    }

    // --- Apple --------------------------------------------------------------------------

    @Test
    fun an_app_store_renewal_whose_notification_never_came_is_found_through_the_server_api() = harness.run {
        val player = player()
        player.buyApple(apple.transaction("1000000000000001", fromNow(Duration.ofHours(30))))
        harness.pass(Duration.ofHours(29))
        val renewedUntil = fromNow(Duration.ofDays(30))
        apple.says("1000000000000001", apple.transaction("1000000000000001", renewedUntil), apple.renewalInfo("1000000000000001"))

        assertEquals(Rechecked(asked = 1, failed = 0), services.recheck())

        assertEquals(renewedUntil.toEpochMilli(), player.entitlements().expiresAtEpochMillis)
    }

    @Test
    fun an_app_store_refund_whose_notification_never_came_is_found() = harness.run {
        val player = player()
        player.buyApple(apple.transaction("1000000000000001", fromNow(Duration.ofHours(12))))
        harness.pass(Duration.ofHours(1))
        apple.says("1000000000000001", apple.transaction("1000000000000001", fromNow(Duration.ofHours(11)), revoked = harness.now))

        services.recheck()

        assertFalse(player.entitlements().subscribed)
    }

    @Test
    fun what_the_server_api_answers_is_believed_only_where_apple_signed_it_and_only_about_what_was_asked() = harness.run {
        val player = player()
        player.buyApple(apple.transaction("1000000000000001", fromNow(Duration.ofHours(12))))
        val before = harness.rows("subscription")
        harness.pass(Duration.ofHours(1))
        val forger = TestChain(harness.now)

        // Unsigned by Apple; then signed, and about somebody else's subscription.
        apple.says("1000000000000001", apple.transaction("1000000000000001", fromNow(Duration.ofDays(3650)), signer = forger))
        services.recheck()
        apple.says("1000000000000001", apple.transaction("1000000000000002", fromNow(Duration.ofDays(3650))))
        services.recheck()
        apple.statuses["1000000000000001"] = "<html>503</html>"
        services.recheck()

        assertEquals(before, harness.rows("subscription"))
        assertEquals(fromNow(Duration.ofHours(11)).toEpochMilli(), player.entitlements().expiresAtEpochMillis)
    }

    @Test
    fun with_no_server_api_key_app_store_subscriptions_are_left_as_they_are_and_nothing_fails() = harness.run {
        player().buyApple(apple.transaction("1000000000000001", fromNow(Duration.ofHours(12))))
        val before = harness.rows("subscription")

        val done = harness.storeServices(appStoreApi = null).recheck()

        assertEquals(Rechecked(asked = 0, failed = 0), done)
        assertEquals(before, harness.rows("subscription"))
    }

    // --- when a store cannot be asked ---------------------------------------------------

    @Test
    fun one_store_being_down_does_not_stop_the_other_being_asked() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.active(fromNow(Duration.ofHours(12)))
        val android = player()
        android.buyGoogle("tok-1")
        val iphone = player()
        iphone.buyApple(apple.transaction("1000000000000001", fromNow(Duration.ofHours(12))))
        harness.pass(Duration.ofHours(1))
        val renewedUntil = fromNow(Duration.ofDays(30))
        apple.says("1000000000000001", apple.transaction("1000000000000001", renewedUntil))
        play.down = true

        val done = services.recheck()

        assertEquals(Rechecked(asked = 2, failed = 1), done)
        assertEquals(renewedUntil.toEpochMilli(), iphone.entitlements().expiresAtEpochMillis)
        // Not being able to ask takes nothing away from someone who had paid.
        assertTrue(android.entitlements().subscribed)
    }

    // --- on its own ---------------------------------------------------------------------

    @Test
    fun a_server_told_how_often_makes_the_recheck_itself() {
        val scheduled = DailyHarness(Settings(recheckEvery = Duration.ofMillis(50), requestsPerTokenPerMinute = 10_000, accountsPerAddressPerMinute = 10_000))
        scheduled.run {
            scheduled.stores.play.purchases["tok-1"] = PlayAnswers.active(scheduled.now.plus(Duration.ofHours(12)))
            val player = player()
            player.buyGoogle("tok-1")
            val renewedUntil = scheduled.now.plus(Duration.ofDays(30))
            scheduled.stores.play.purchases["tok-1"] = PlayAnswers.active(renewedUntil)

            // Real time, because the schedule is the thing under test. Nobody asks for it.
            withContext(Dispatchers.Default) {
                withTimeout(10_000) { while (player.entitlements().expiresAtEpochMillis != renewedUntil.toEpochMilli()) delay(50) }
            }
        }
    }
}
