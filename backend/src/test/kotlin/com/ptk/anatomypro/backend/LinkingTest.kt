package com.ptk.anatomypro.backend

import com.ptk.anatomypro.api.ApiErrorCode
import com.ptk.anatomypro.api.ApiPaths
import com.ptk.anatomypro.api.NicknameRequest
import com.ptk.anatomypro.api.IdentityProvider
import com.ptk.anatomypro.api.LinkResponse
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.HttpStatusCode
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Backend spec §5.3 and §5.4: signing in, the merge it can cause, and signing out. */
class LinkingTest {

    private val harness = DailyHarness()

    private fun count(table: String): Int = harness.database.connection.use { connection ->
        connection.createStatement().executeQuery("SELECT count(*) FROM $table").use { it.next(); it.getInt(1) }
    }

    // --- the three cases ----------------------------------------------------------------

    @Test
    fun an_identity_nobody_has_is_attached_and_nothing_else_changes() = harness.run {
        val player = player()
        val before = player.account()

        val linked = player.signIn("g-1")

        assertFalse(linked.merged)
        assertEquals(before.copy(linked = listOf(IdentityProvider.GOOGLE)), linked.account)
        assertEquals(linked.account, player.account())
    }

    @Test
    fun signing_in_again_with_an_identity_already_attached_changes_nothing() = harness.run {
        val player = player()
        player.signIn("g-1")

        val again = player.signIn("g-1")

        assertFalse(again.merged)
        assertEquals(1, count("identity"))
        assertEquals(1, count("account"))
    }

    @Test
    fun signing_in_on_a_second_device_makes_that_device_speak_for_the_account_signed_into() = harness.run {
        val phone = player()
        val home = phone.signIn("g-1").account
        val tablet = player()

        val linked = tablet.signIn("g-1")

        assertTrue(linked.merged)
        assertEquals(home, linked.account)
        assertEquals(home, tablet.account())
        assertEquals(home, phone.account())
        assertEquals(1, count("account"))
        assertEquals(2, count("device_token"))
    }

    @Test
    fun a_token_that_is_not_the_providers_links_nothing() = harness.run {
        val player = player()

        val response = player.linkResponse(IdentityProvider.GOOGLE, harness.identities.token("g-1", audience = "another-app"))

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(ApiErrorCode.IDENTITY_INVALID, response.errorCode())
        assertEquals(0, count("identity"))
        assertEquals(emptyList(), player.account().linked)
    }

    @Test
    fun an_account_can_hold_a_google_and_an_apple_identity_and_be_reached_by_either() = harness.run {
        val phone = player()
        phone.signIn("g-1")
        phone.linkResponse(IdentityProvider.APPLE, harness.identities.apple("a-1"))
        val home = phone.account()
        assertEquals(setOf(IdentityProvider.GOOGLE, IdentityProvider.APPLE), home.linked.toSet())

        val tablet = player()
        val linked = tablet.linkResponse(IdentityProvider.APPLE, harness.identities.apple("a-1")).body<LinkResponse>()

        assertTrue(linked.merged)
        assertEquals(home.id, linked.account.id)
    }

    // --- what a merge keeps -------------------------------------------------------------

    @Test
    fun the_days_each_account_played_all_belong_to_the_surviving_one_and_the_streak_is_counted_again() = harness.run {
        val phone = player()
        phone.signIn("g-1")
        phone.play()
        harness.nextDay()
        phone.play()
        harness.nextDay()
        val tablet = player()
        tablet.play()

        tablet.signIn("g-1")

        assertEquals(3, phone.streakBoard().me?.score)
        assertEquals(3, count("daily_attempt"))
        assertEquals(1, phone.streakBoard().totalPlayers)
    }

    @Test
    fun days_that_do_not_run_on_make_no_streak_longer_than_the_last_run() = harness.run {
        val phone = player()
        phone.signIn("g-1")
        phone.play()
        harness.nextDay()
        harness.nextDay()
        val tablet = player()
        tablet.play()

        tablet.signIn("g-1")

        assertEquals(1, phone.streakBoard().me?.score)
    }

    @Test
    fun where_both_played_the_same_day_the_attempt_submitted_first_is_the_one_kept() = harness.run {
        // The board has been showing the first one, so the first one stays.
        val phone = player()
        phone.signIn("g-1")
        val tablet = player()
        tablet.play(right = 1, thinking = Duration.ofSeconds(30))
        phone.play(right = 4, thinking = Duration.ofSeconds(30))

        tablet.signIn("g-1")

        val board = phone.dailyBoard()
        assertEquals(1, board.totalPlayers)
        assertEquals(1, board.me?.score)
        assertEquals(phone.nickname, board.me?.nickname)
    }

    @Test
    fun and_when_the_signed_in_account_played_first_its_attempt_stands() = harness.run {
        val phone = player()
        phone.signIn("g-1")
        val tablet = player()
        phone.play(right = 4, thinking = Duration.ofSeconds(30))
        tablet.play(right = 1, thinking = Duration.ofSeconds(30))

        tablet.signIn("g-1")

        assertEquals(4, phone.dailyBoard().me?.score)
        assertEquals(1, count("daily_attempt"))
    }

    @Test
    fun a_finished_attempt_is_kept_over_one_only_started() = harness.run {
        val phone = player()
        phone.signIn("g-1")
        phone.start()
        val tablet = player()
        tablet.play(right = 2)

        tablet.signIn("g-1")

        assertEquals(2, phone.dailyBoard().me?.score)
        assertEquals(1, count("daily_attempt"))
    }

    @Test
    fun the_signed_in_accounts_nickname_is_kept_and_the_other_one_is_free_again() = harness.run {
        val phone = player()
        phone.signIn("g-1")
        val tablet = player()

        tablet.signIn("g-1")

        assertEquals(phone.nickname, tablet.account().nickname)
        val someone = player()
        val rename = client.put(ApiPaths.NICKNAME) {
            bearerAuth(someone.token)
            contentType(ContentType.Application.Json)
            setBody(NicknameRequest(tablet.nickname))
        }
        assertEquals(HttpStatusCode.OK, rename.status)
    }

    @Test
    fun two_accounts_that_had_each_signed_in_become_one_with_both_identities() = harness.run {
        val google = player()
        google.signIn("g-1")
        val apple = player()
        apple.linkResponse(IdentityProvider.APPLE, harness.identities.apple("a-1"))

        val linked = apple.signIn("g-1")

        assertTrue(linked.merged)
        assertEquals(setOf(IdentityProvider.GOOGLE, IdentityProvider.APPLE), google.account().linked.toSet())
        assertEquals(1, count("account"))
        assertEquals(2, count("identity"))
    }

    @Test
    fun a_merge_is_written_down_with_both_accounts() = harness.run {
        val phone = player()
        val survivor = phone.signIn("g-1").account.id
        val tablet = player()
        val absorbed = tablet.account().id

        tablet.signIn("g-1")

        val logged = harness.database.connection.use { connection ->
            connection.createStatement().executeQuery("SELECT surviving_account::text, merged_account::text FROM account_merge").use {
                it.next(); it.getString(1) to it.getString(2)
            }
        }
        assertEquals(survivor to absorbed, logged)
    }

    @Test
    fun a_merge_that_fails_part_way_changes_nothing_at_all() = harness.run {
        val phone = player()
        phone.signIn("g-1")
        phone.play(right = 4)
        val tablet = player()
        val tabletBefore = tablet.account()
        tablet.start()
        // The last thing a merge does is write itself down. Make that fail.
        harness.database.connection.use { connection ->
            connection.createStatement().execute(
                """
                CREATE FUNCTION refuse() RETURNS trigger AS ${'$'}${'$'} BEGIN RAISE EXCEPTION 'the disk is full'; END ${'$'}${'$'} LANGUAGE plpgsql;
                CREATE TRIGGER refuse_merge BEFORE INSERT ON account_merge FOR EACH ROW EXECUTE FUNCTION refuse();
                """.trimIndent(),
            )
        }

        val response = tablet.linkResponse(IdentityProvider.GOOGLE, harness.identities.google("g-1"))

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(2, count("account"))
        assertEquals(2, count("daily_attempt"))
        assertEquals(tabletBefore, tablet.account())
        assertEquals(4, phone.dailyBoard().me?.score)
    }

    // --- leaving a device, and leaving altogether ---------------------------------------

    @Test
    fun signing_out_stops_that_device_and_leaves_the_account_to_be_signed_into_again() = harness.run {
        val phone = player()
        val home = phone.signIn("g-1").account
        phone.play(right = 3)

        assertEquals(HttpStatusCode.NoContent, phone.signOutResponse().status)

        assertEquals(HttpStatusCode.Unauthorized, phone.todayResponse().status)
        val again = player()
        val linked = again.signIn("g-1")
        assertEquals(home.id, linked.account.id)
        assertEquals(3, again.dailyBoard().me?.score)
    }

    @Test
    fun signing_out_one_device_leaves_the_others_signed_in() = harness.run {
        val phone = player()
        phone.signIn("g-1")
        val tablet = player()
        tablet.signIn("g-1")

        phone.signOutResponse()

        assertEquals(HttpStatusCode.OK, tablet.todayResponse().status)
    }

    @Test
    fun an_account_no_device_and_no_identity_can_ever_reach_again_is_not_kept() = harness.run {
        // Signed out of the only device of an account that never signed in: nothing can
        // open it again, and what cannot be opened should not be held (§5.5).
        val player = player()
        player.play()

        player.signOutResponse()

        assertEquals(0, count("account"))
        assertEquals(0, count("daily_attempt"))
    }

    @Test
    fun a_deleted_accounts_identity_goes_with_it_and_can_start_afresh() = harness.run {
        val phone = player()
        phone.signIn("g-1")
        client.delete(ApiPaths.ACCOUNT) { bearerAuth(phone.token) }
        assertEquals(0, count("identity"))

        val fresh = player()
        val linked = fresh.signIn("g-1")

        assertFalse(linked.merged)
        assertNull(fresh.dailyBoard().me)
    }
}
