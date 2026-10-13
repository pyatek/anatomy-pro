package com.ptk.anatomypro.backend

import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.ptk.anatomypro.api.PurchaseVerdict
import io.ktor.http.HttpStatusCode
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Backend spec §8.1: real-time developer notifications, pushed to `/v1/webhooks/google`.
 * A notification is believed only as far as "ask Google about this purchase".
 */
class GoogleNotificationsTest {

    private val harness = DailyHarness(Settings(requestsPerTokenPerMinute = 10_000, accountsPerAddressPerMinute = 10_000))
    private val stores get() = harness.stores
    private val play get() = harness.stores.play
    private val inAMonth get() = harness.now.plus(Duration.ofDays(30))

    // --- what a notification does -------------------------------------------------------

    @Test
    fun a_renewal_is_heard_of_without_the_app_being_opened() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.active(harness.now.plus(Duration.ofDays(1)))
        val player = player()
        player.buyGoogle("tok-1")
        val renewedUntil = harness.now.plus(Duration.ofDays(31))
        play.purchases["tok-1"] = PlayAnswers.active(renewedUntil)

        val response = googleWebhook(stores.googleNotification("tok-1", type = 2))

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(renewedUntil.toEpochMilli(), player.entitlements().expiresAtEpochMillis)
    }

    @Test
    fun a_refund_locks_the_account_again_without_the_app_being_opened() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.active(inAMonth)
        val player = player()
        player.buyGoogle("tok-1")
        assertEquals(HttpStatusCode.OK, player.todayResponse().status)
        // Google revokes it: the purchase now reads as expired, as of a moment ago.
        play.purchases["tok-1"] = PlayAnswers.expired(harness.now.minusSeconds(5))

        googleWebhook(stores.googleNotification("tok-1", type = 12))

        assertFalse(player.entitlements().subscribed)
        assertEquals(HttpStatusCode.Forbidden, player.todayResponse().status)
    }

    @Test
    fun a_refund_google_reports_as_a_voided_purchase_is_asked_about_too() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.active(inAMonth)
        val player = player()
        player.buyGoogle("tok-1")
        play.purchases["tok-1"] = PlayAnswers.expired(harness.now.minusSeconds(5))

        googleWebhook(stores.googleNotification("tok-1", kind = "voidedPurchaseNotification"))

        assertFalse(player.entitlements().subscribed)
    }

    @Test
    fun what_the_notification_claims_happened_is_not_believed_only_what_google_then_says() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.active(inAMonth)
        val player = player()
        player.buyGoogle("tok-1")
        val before = harness.rows("subscription")

        // "Expired" (13), while Google itself still says active.
        googleWebhook(stores.googleNotification("tok-1", type = 13))

        assertTrue(player.entitlements().subscribed)
        assertEquals(before, harness.rows("subscription"))
    }

    @Test
    fun a_pending_purchase_that_is_paid_unlocks_the_account_without_being_presented_again() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.pending()
        val player = player()
        assertEquals(PurchaseVerdict.PENDING, player.buyGoogle("tok-1").outcome)
        play.purchases["tok-1"] = PlayAnswers.active(inAMonth, acknowledged = false)

        googleWebhook(stores.googleNotification("tok-1", type = 4))

        assertTrue(player.entitlements().subscribed)
        assertEquals(listOf("tok-1"), play.acknowledged)
    }

    @Test
    fun a_new_purchase_that_replaces_one_we_hold_is_taken_up_for_the_same_account() = harness.run {
        // Subscribed again from the Play Store itself: the app never saw the new token.
        play.purchases["tok-old"] = PlayAnswers.cancelled(harness.now.plus(Duration.ofDays(2)))
        val player = player()
        player.buyGoogle("tok-old")
        play.purchases["tok-new"] = PlayAnswers.active(inAMonth, linked = "tok-old")

        googleWebhook(stores.googleNotification("tok-new", type = 4))

        assertEquals(inAMonth.toEpochMilli(), player.entitlements().expiresAtEpochMillis)
    }

    @Test
    fun a_notification_about_a_purchase_nobody_presented_gives_it_to_nobody() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.active(inAMonth, acknowledged = false)
        val bystander = player()

        val response = googleWebhook(stores.googleNotification("tok-1", type = 4))

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(emptyList(), harness.rows("subscription"))
        assertEquals(emptyList(), play.acknowledged)
        assertFalse(bystander.entitlements().subscribed)
    }

    // --- delivered twice (§8.1) ---------------------------------------------------------

    @Test
    fun a_notification_delivered_twice_changes_nothing_and_asks_nothing_the_second_time() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.active(harness.now.plus(Duration.ofDays(1)))
        val player = player()
        player.buyGoogle("tok-1")
        play.purchases["tok-1"] = PlayAnswers.active(inAMonth)
        val notification = stores.googleNotification("tok-1", messageId = "msg-77")
        googleWebhook(notification)
        val subscriptions = harness.rows("subscription")
        val heard = harness.rows("store_notification")
        val asked = play.fetches
        harness.pass(Duration.ofMinutes(10))

        val again = googleWebhook(notification)

        assertEquals(HttpStatusCode.NoContent, again.status)
        assertEquals(subscriptions, harness.rows("subscription"))
        assertEquals(heard, harness.rows("store_notification"))
        assertEquals(asked, play.fetches)
        assertEquals(1, heard.size)
    }

    @Test
    fun a_notification_that_could_not_be_acted_on_is_acted_on_when_it_comes_again() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.active(harness.now.plus(Duration.ofDays(1)))
        val player = player()
        player.buyGoogle("tok-1")
        play.purchases["tok-1"] = PlayAnswers.active(inAMonth)
        val notification = stores.googleNotification("tok-1", messageId = "msg-78")
        play.down = true

        // Anything but a success makes Pub/Sub send it again.
        assertEquals(HttpStatusCode.ServiceUnavailable, googleWebhook(notification).status)
        assertEquals(emptyList(), harness.rows("store_notification"))

        play.down = false
        assertEquals(HttpStatusCode.NoContent, googleWebhook(notification).status)
        assertEquals(inAMonth.toEpochMilli(), player.entitlements().expiresAtEpochMillis)
    }

    // --- who is speaking ----------------------------------------------------------------

    @Test
    fun a_push_is_refused_unless_it_carries_googles_token_made_out_to_us_by_our_sender() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.active(inAMonth)
        val player = player()
        player.buyGoogle("tok-1")
        play.purchases["tok-1"] = PlayAnswers.expired(harness.now.minusSeconds(5))
        val asked = play.fetches
        val notification = stores.googleNotification("tok-1")
        val strangersKey = RSAKeyGenerator(2048).keyID(harness.identities.googleKey.keyID).generate()

        val refused = listOf(
            "no token" to null,
            "not a token" to "let-me-in",
            "another audience" to stores.pushToken(audience = "https://someone-else.example/hook"),
            "another service account" to stores.pushToken(email = "attacker@their-project.iam.gserviceaccount.com"),
            "an unverified address" to stores.pushToken(verified = false),
            "expired" to stores.pushToken(expires = harness.now.minusSeconds(1)),
            "signed by someone else" to stores.pushToken(key = strangersKey),
            "a person's sign-in token" to harness.identities.google("g-1"),
        )

        for ((why, bearer) in refused) {
            assertEquals(HttpStatusCode.Unauthorized, googleWebhook(notification, bearer).status, why)
        }
        assertEquals(asked, play.fetches)
        assertTrue(player.entitlements().subscribed)
        assertEquals(emptyList(), harness.rows("store_notification"))
    }

    @Test
    fun a_test_message_or_one_about_another_app_is_accepted_and_does_nothing() = harness.run {
        play.purchases["tok-1"] = PlayAnswers.active(inAMonth)

        assertEquals(HttpStatusCode.NoContent, googleWebhook(stores.googleNotification("tok-1", kind = "testNotification")).status)
        assertEquals(HttpStatusCode.NoContent, googleWebhook(stores.googleNotification("tok-1", packageName = "com.someone.else")).status)

        assertEquals(0, play.fetches)
    }

    @Test
    fun a_push_that_is_not_a_pub_sub_message_is_refused_as_malformed() = harness.run {
        assertEquals(HttpStatusCode.BadRequest, googleWebhook("""{"hello":"there"}""").status)
        assertEquals(HttpStatusCode.BadRequest, googleWebhook("not json").status)
    }
}
