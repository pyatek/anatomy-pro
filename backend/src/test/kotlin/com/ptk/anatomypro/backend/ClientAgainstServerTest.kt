package com.ptk.anatomypro.backend

import com.ptk.anatomypro.api.ApiErrorCode
import com.ptk.anatomypro.api.IdentityProvider
import com.ptk.anatomypro.api.Plan
import com.ptk.anatomypro.api.PlanPeriod
import com.ptk.anatomypro.api.PurchaseVerdict
import com.ptk.anatomypro.api.client.ApiClient
import com.ptk.anatomypro.api.client.ApiFailure
import com.ptk.anatomypro.api.client.ClientStore
import com.ptk.anatomypro.api.client.KtorDailyRepository
import com.ptk.anatomypro.api.client.KtorLeaderboardRepository
import com.ptk.anatomypro.api.client.PurchaseProof
import com.ptk.anatomypro.api.client.ServerAccount
import com.ptk.anatomypro.api.client.ServerAccountRepository
import com.ptk.anatomypro.api.client.ServerEntitlementRepository
import com.ptk.anatomypro.core.data.model.DailyQuiz
import com.ptk.anatomypro.core.data.model.RenameOutcome
import com.ptk.anatomypro.core.data.model.SignInOutcome
import com.ptk.anatomypro.core.data.model.SignInProvider
import com.ptk.anatomypro.core.data.model.QuizAnswer
import com.ptk.anatomypro.core.data.model.QuizQuestion
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.LocalDate
import java.time.Duration
import kotlin.test.Test
import kotlin.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The app's repositories against the real server, through the same wire types (backend
 * spec §12). This is the test that would catch a field renamed on one side.
 */
class ClientAgainstServerTest {

    private class MemoryStore : ClientStore {
        val values = mutableMapOf<String, String>()
        override suspend fun read(key: String) = values[key]
        override suspend fun write(key: String, value: String) { values[key] = value }
        override suspend fun remove(key: String) { values.remove(key) }
    }

    /** One device: its store, and the repositories built over it. */
    private class Device(http: HttpClient, val store: MemoryStore = MemoryStore(), timed: Boolean = true) {
        val api = ApiClient(http, baseUrl = "", store = store)
        val daily = KtorDailyRepository(api, timed = { timed })
        val boards = KtorLeaderboardRepository(api)
        val entitlements = ServerEntitlementRepository(api, store)
        val account = ServerAccount(api)
        val accounts = ServerAccountRepository(account)
    }

    private val harness = DailyHarness()

    private fun server(harness: DailyHarness = this.harness, test: suspend (HttpClient) -> Unit) = testApplication {
        application { harness.serve(this) }
        test(createClient { })
    }

    private val unreachable = HttpClient(MockEngine { throw java.io.IOException("no route to host") })

    private fun accounts(): Int = harness.database.connection.use { connection ->
        connection.createStatement().executeQuery("SELECT count(*) FROM account").use { it.next(); it.getInt(1) }
    }

    private suspend fun Device.playToday(right: Int = Int.MAX_VALUE, thinking: Duration = Duration.ofSeconds(60)) = run {
        val round = daily.start("la")
        harness.pass(thinking)
        daily.submit(
            round.questions.mapIndexed { index, question ->
                question as QuizQuestion.NameTheHighlighted
                val chosen = if (index < right) question.correct.id else question.options.first { it.id != question.correct.id }.id
                QuizAnswer(question.id, chosen, elapsedMillis = 1)
            },
        )
    }

    // --- the account behind every call (§5.1) -------------------------------------------

    @Test
    fun the_first_call_makes_an_account_and_every_later_one_uses_it() = server { http ->
        val device = Device(http)

        device.daily.today()
        device.daily.today()
        device.boards.streaks()

        assertEquals(1, accounts())
        assertNotNull(device.store.values[ApiClient.TOKEN_KEY])
    }

    @Test
    fun calls_made_together_on_a_new_device_still_make_one_account() = server { http ->
        val device = Device(http)

        coroutineScope { List(6) { async { device.daily.today() } }.awaitAll() }

        assertEquals(1, accounts())
    }

    @Test
    fun a_device_keeps_its_account_across_launches() = server { http ->
        val store = MemoryStore()
        Device(http, store).daily.today()

        Device(http, store).daily.today()

        assertEquals(1, accounts())
    }

    @Test
    fun a_token_the_server_no_longer_knows_is_replaced_and_the_call_goes_through() = server { http ->
        val device = Device(http)
        device.daily.today()
        val old = device.store.values.getValue(ApiClient.TOKEN_KEY)
        harness.database.connection.use { it.createStatement().executeUpdate("DELETE FROM account") }

        val day = device.daily.today()

        assertIs<DailyQuiz.NotStarted>(day)
        assertNotEquals(old, device.store.values.getValue(ApiClient.TOKEN_KEY))
    }

    // --- the daily (§6) -----------------------------------------------------------------

    @Test
    fun a_day_is_read_started_played_and_read_back_completed() = server { http ->
        val device = Device(http)

        val before = assertIs<DailyQuiz.NotStarted>(device.daily.today())
        assertEquals(LocalDate.parse(harness.today), before.date)

        val round = device.daily.start("la")
        assertEquals(before.questionCount, round.questions.size)
        assertEquals(harness.now.toEpochMilli(), round.startedAt.toEpochMilliseconds())
        assertEquals(round, device.daily.today())

        val result = device.playToday(right = 2, thinking = Duration.ofSeconds(45))
        assertEquals(2, result.correct)
        assertEquals(before.questionCount, result.total)
        assertEquals(45_000, result.elapsedMillis)
        assertEquals(1, result.rank)
        assertEquals(1, result.totalPlayers)

        assertEquals(result, assertIs<DailyQuiz.Completed>(device.daily.today()).result)
    }

    @Test
    fun the_right_option_is_worked_out_from_the_highlighted_structure() = server { http ->
        val round = Device(http).daily.start("la")

        for (question in round.questions) {
            question as QuizQuestion.NameTheHighlighted
            assertEquals(question.highlighted, question.correct.id)
            assertEquals(4, question.options.size)
            assertTrue(question.options.all { it.name.isNotBlank() && it.latinName != null })
        }
    }

    @Test
    fun every_answer_right_is_a_full_score_on_the_servers_count() = server { http ->
        val result = Device(http).playToday()

        assertEquals(result.total, result.correct)
    }

    @Test
    fun a_question_that_ran_out_of_time_is_sent_as_unanswered() = server { http ->
        val device = Device(http)
        val round = device.daily.start("la")
        harness.pass(Duration.ofSeconds(60))

        val result = device.daily.submit(round.questions.map { QuizAnswer(it.id, chosen = null, elapsedMillis = 1) })

        assertEquals(0, result.correct)
    }

    @Test
    fun with_the_timer_off_the_attempt_is_scored_and_has_no_place() = server { http ->
        val result = Device(http, timed = false).playToday()

        assertEquals(result.total, result.correct)
        assertNull(result.rank)
    }

    @Test
    fun starting_after_the_day_has_turned_starts_the_new_day() = server { http ->
        val device = Device(http)
        device.daily.today()
        harness.nextDay()

        val round = device.daily.start("la")

        assertEquals(LocalDate.parse(harness.today), round.date)
    }

    @Test
    fun a_submit_sent_again_after_it_had_arrived_gets_the_stored_result() = server { http ->
        val device = Device(http)
        val first = device.playToday(right = 1)

        val again = device.daily.submit(emptyList())

        assertEquals(first, again)
    }

    @Test
    fun a_submit_retried_across_midnight_is_still_for_the_day_it_was_played() = server { http ->
        val device = Device(http)
        val played = harness.today
        val round = device.daily.start("la")
        harness.nextDay(at = "00:05:00")

        val result = device.daily.submit(round.questions.map { QuizAnswer(it.id, (it as QuizQuestion.NameTheHighlighted).correct.id, 1) })

        assertEquals(LocalDate.parse(played), result.date)
        assertNotNull(result.rank)
    }

    // --- failure, as the client meets it (§6.4) -----------------------------------------

    @Test
    fun with_no_connection_the_day_is_unavailable_which_is_a_state_and_not_an_error() = server { _ ->
        assertEquals(DailyQuiz.Unavailable, Device(unreachable).daily.today())
    }

    @Test
    fun with_nothing_to_ask_the_day_is_unavailable_too() {
        val bare = DailyHarness(seed = { it.ingest(SampleContent.manifest()) })
        server(bare) { http -> assertEquals(DailyQuiz.Unavailable, Device(http).daily.today()) }
    }

    @Test
    fun a_start_or_a_submit_that_cannot_reach_the_server_fails_so_the_screen_can_offer_it_again() = server { _ ->
        val device = Device(unreachable)

        assertFailsWith<Exception> { device.daily.start("la") }
        assertFailsWith<Exception> { device.daily.submit(emptyList()) }
    }

    @Test
    fun an_account_that_has_not_paid_is_refused_by_name() {
        val shut = DailyHarness(Settings())
        server(shut) { http ->
            val refusal = assertFailsWith<ApiFailure> { Device(http).daily.today() }
            assertEquals(ApiErrorCode.NOT_SUBSCRIBED, refusal.code)
        }
    }

    // --- boards (§7) --------------------------------------------------------------------

    @Test
    fun the_days_board_lists_the_players_and_marks_the_callers_own_row() = server { http ->
        val first = Device(http)
        val second = Device(http)
        first.playToday(thinking = Duration.ofSeconds(20))
        second.playToday(thinking = Duration.ofSeconds(40))

        val board = second.boards.daily(LocalDate.parse(harness.today))

        assertEquals(listOf(1, 2), board.entries.map { it.rank })
        assertEquals(listOf(false, true), board.entries.map { it.isMe })
        assertEquals(20_000, board.entries.first().elapsedMillis)
        assertEquals(2, board.me?.rank)
        assertEquals(2, board.totalPlayers)
    }

    @Test
    fun the_streak_board_counts_days_and_has_no_row_for_someone_who_has_not_played() = server { http ->
        val player = Device(http)
        val watcher = Device(http)
        player.playToday()
        harness.nextDay()
        player.playToday()

        val board = watcher.boards.streaks()

        assertEquals(listOf(2), board.entries.map { it.score })
        assertNull(board.me)
    }

    // --- entitlements (§8.1) ------------------------------------------------------------

    @Test
    fun whether_someone_is_a_subscriber_is_the_servers_to_say() {
        server { http -> assertTrue(Device(http).entitlements.entitlements.toList().last().subscribed) }

        val shut = DailyHarness(Settings())
        server(shut) { http -> assertFalse(Device(http).entitlements.entitlements.toList().last().subscribed) }
    }

    @Test
    fun what_the_server_last_said_is_remembered_when_it_cannot_be_asked() = server { http ->
        // A subscriber on a train still has their topics: the atlas and its quizzes work offline.
        val store = MemoryStore()
        Device(http, store).entitlements.entitlements.toList()

        val offline = Device(unreachable, store)

        assertTrue(offline.entitlements.entitlements.first().subscribed)
        assertTrue(offline.entitlements.entitlements.toList().all { it.subscribed })
    }

    @Test
    fun a_device_that_has_never_asked_and_cannot_ask_is_not_a_subscriber() = server { _ ->
        assertEquals(listOf(false), Device(unreachable).entitlements.entitlements.toList().map { it.subscribed })
    }

    // --- buying (§8.1) ------------------------------------------------------------------

    /** A server where nobody is a subscriber by decree, and its stand-in stores. */
    private val paying by lazy { DailyHarness(Settings(requestsPerTokenPerMinute = 10_000, accountsPerAddressPerMinute = 10_000)) }
    private val inAMonth get() = paying.now.plus(Duration.ofDays(30))

    @Test
    fun what_is_on_sale_is_read_from_the_server() = server(paying) { http ->
        val plans = Device(http).entitlements.onSale()

        assertEquals(listOf(Plan(TestStores.MONTHLY, PlanPeriod.MONTHLY), Plan(TestStores.YEARLY, PlanPeriod.YEARLY)), plans)
    }

    @Test
    fun a_play_purchase_handed_to_the_server_makes_the_account_a_subscriber_and_the_device_remembers() = server(paying) { http ->
        paying.stores.play.purchases["tok-1"] = PlayAnswers.active(inAMonth)
        val store = MemoryStore()
        val device = Device(http, store)
        assertFalse(device.entitlements.entitlements.toList().last().subscribed)

        val answer = device.entitlements.present(PurchaseProof.GooglePlay(TestStores.MONTHLY, "tok-1"))

        assertEquals(PurchaseVerdict.VERIFIED, answer.outcome)
        assertEquals(inAMonth.toEpochMilli(), answer.entitlements.expiresAtEpochMillis)
        assertTrue(device.entitlements.entitlements.toList().last().subscribed)
        assertIs<DailyQuiz.NotStarted>(device.daily.today())
        // And on a train straight afterwards, without having asked again.
        assertTrue(Device(unreachable, store).entitlements.entitlements.first().subscribed)
    }

    @Test
    fun an_app_store_transaction_handed_to_the_server_does_the_same() = server(paying) { http ->
        val device = Device(http)

        val answer = device.entitlements.present(PurchaseProof.AppStore(paying.stores.apple.transaction("1000000000000001", inAMonth)))

        assertEquals(PurchaseVerdict.VERIFIED, answer.outcome)
        assertTrue(device.entitlements.entitlements.toList().last().subscribed)
    }

    @Test
    fun a_purchase_the_store_does_not_stand_behind_unlocks_nothing_on_the_device_either() = server(paying) { http ->
        val store = MemoryStore()
        val device = Device(http, store)

        val answer = device.entitlements.present(PurchaseProof.GooglePlay(TestStores.MONTHLY, "made-up"))

        assertEquals(PurchaseVerdict.INVALID, answer.outcome)
        assertFalse(Device(unreachable, store).entitlements.entitlements.first().subscribed)
        assertEquals(ApiErrorCode.NOT_SUBSCRIBED, assertFailsWith<ApiFailure> { device.daily.today() }.code)
    }

    @Test
    fun a_restore_on_a_second_device_is_told_to_sign_in_and_signing_in_brings_the_subscription() = server(paying) { http ->
        paying.stores.play.purchases["tok-1"] = PlayAnswers.active(inAMonth)
        val phone = Device(http)
        phone.account.link(IdentityProvider.GOOGLE, paying.identities.google("g-1"))
        phone.entitlements.present(PurchaseProof.GooglePlay(TestStores.MONTHLY, "tok-1"))
        val tablet = Device(http)

        val restored = tablet.entitlements.present(PurchaseProof.GooglePlay(TestStores.MONTHLY, "tok-1"))

        assertEquals(PurchaseVerdict.OWNED_BY_ANOTHER_ACCOUNT, restored.outcome)
        assertFalse(tablet.entitlements.entitlements.toList().last().subscribed)

        tablet.account.link(IdentityProvider.GOOGLE, paying.identities.google("g-1"))
        assertTrue(tablet.entitlements.entitlements.toList().last().subscribed)
    }

    @Test
    fun a_purchase_that_could_not_be_checked_fails_so_it_can_be_presented_again() = server(paying) { http ->
        paying.stores.play.purchases["tok-1"] = PlayAnswers.active(inAMonth)
        val device = Device(http)
        paying.stores.play.down = true

        // The server reached and the store not: a refusal that is not a verdict.
        assertEquals(503, assertFailsWith<ApiFailure> { device.entitlements.present(PurchaseProof.GooglePlay(TestStores.MONTHLY, "tok-1")) }.status)
        // The server not reached at all.
        assertFailsWith<Exception> { Device(unreachable).entitlements.present(PurchaseProof.GooglePlay(TestStores.MONTHLY, "tok-1")) }

        paying.stores.play.down = false
        assertEquals(PurchaseVerdict.VERIFIED, device.entitlements.present(PurchaseProof.GooglePlay(TestStores.MONTHLY, "tok-1")).outcome)
    }

    @Test
    fun deleting_an_account_that_is_still_paying_says_so() = server(paying) { http ->
        paying.stores.play.purchases["tok-1"] = PlayAnswers.active(inAMonth)
        val device = Device(http)
        device.entitlements.present(PurchaseProof.GooglePlay(TestStores.MONTHLY, "tok-1"))

        assertTrue(device.account.delete().subscriptionStillActive)
    }

    // --- the account itself (§5.2 to §5.4) ----------------------------------------------

    @Test
    fun a_nickname_is_changed_and_one_already_taken_is_refused_by_name() = server { http ->
        val one = Device(http)
        val two = Device(http)

        assertEquals("Zaneta", one.account.rename("Zaneta").nickname)
        assertEquals("Zaneta", one.account.read().nickname)

        val refusal = assertFailsWith<ApiFailure> { two.account.rename("zaneta") }
        assertEquals(ApiErrorCode.NICKNAME_TAKEN, refusal.code)
    }

    @Test
    fun signing_in_on_a_second_device_brings_the_first_devices_results_with_it() = server { http ->
        val phone = Device(http)
        val linked = phone.account.link(IdentityProvider.GOOGLE, harness.identities.google("g-1"))
        assertFalse(linked.merged)
        assertEquals(listOf(IdentityProvider.GOOGLE), linked.account.linked)
        val result = phone.playToday(right = 3)

        val tablet = Device(http)
        tablet.daily.today()
        val merged = tablet.account.link(IdentityProvider.GOOGLE, harness.identities.google("g-1"))

        assertTrue(merged.merged)
        assertEquals(phone.account.read(), tablet.account.read())
        assertEquals(result, assertIs<DailyQuiz.Completed>(tablet.daily.today()).result)
        assertEquals(1, accounts())
    }

    @Test
    fun a_token_the_provider_did_not_sign_is_refused_by_name() = server { http ->
        val refusal = assertFailsWith<ApiFailure> {
            Device(http).account.link(IdentityProvider.GOOGLE, harness.identities.token("g-1", audience = "another-app"))
        }

        assertEquals(ApiErrorCode.IDENTITY_INVALID, refusal.code)
    }

    @Test
    fun after_signing_out_the_device_starts_again_as_someone_new() = server { http ->
        val device = Device(http)
        val home = device.account.link(IdentityProvider.GOOGLE, harness.identities.google("g-1")).account
        device.entitlements.entitlements.toList()

        device.account.signOut()

        assertNull(device.store.values[ApiClient.TOKEN_KEY])
        assertTrue(device.store.values.isEmpty(), "something of the old account was left on the device")
        assertNotEquals(home.id, device.account.read().id)
        assertEquals(2, accounts())
    }

    @Test
    fun deleting_the_account_leaves_nothing_on_the_server_or_the_device() = server { http ->
        val device = Device(http)
        device.playToday()

        device.account.delete()

        assertEquals(0, accounts())
        assertTrue(device.store.values.isEmpty())
    }

    // --- the account, as the account screen is given it ---------------------------------

    @Test
    fun the_account_screens_contract_reads_and_renames_the_real_account() = server { http ->
        val device = Device(http)

        val account = device.accounts.account()
        assertTrue(account.nickname.startsWith("student-"))
        assertEquals(emptySet(), account.linked)
        assertNull(account.nicknameLockedUntil)

        val renamed = assertIs<RenameOutcome.Renamed>(device.accounts.rename("Zaneta_92"))
        assertEquals("Zaneta_92", renamed.account.nickname)
        assertEquals("Zaneta_92", device.accounts.account().nickname)
    }

    @Test
    fun each_way_the_server_refuses_a_nickname_is_an_outcome_of_its_own() = server { http ->
        val one = Device(http)
        val two = Device(http)
        one.accounts.rename("Zaneta")

        assertEquals(RenameOutcome.Taken, two.accounts.rename("zANETA"))
        assertEquals(RenameOutcome.Invalid, two.accounts.rename("ala ma kota"))

        harness.database.connection.use { it.createStatement().executeUpdate("UPDATE account SET nickname_locked_until = DATE '2026-11-11'") }
        assertEquals(RenameOutcome.Locked, two.accounts.rename("nowy_nick"))
        // Locked through the 11th: the first moment a new one can be chosen is the 12th, UTC.
        assertEquals(Instant.parse("2026-11-12T00:00:00Z"), two.accounts.account().nicknameLockedUntil)
    }

    @Test
    fun signing_in_through_the_contract_links_merges_and_rejects() = server { http ->
        val phone = Device(http)
        val first = assertIs<SignInOutcome.SignedIn>(phone.accounts.signIn(SignInProvider.GOOGLE, harness.identities.google("g-1")))
        assertFalse(first.merged)
        assertEquals(setOf(SignInProvider.GOOGLE), first.account.linked)

        val tablet = Device(http)
        val second = assertIs<SignInOutcome.SignedIn>(tablet.accounts.signIn(SignInProvider.GOOGLE, harness.identities.google("g-1")))
        assertTrue(second.merged)
        assertEquals(first.account, second.account)

        assertEquals(
            SignInOutcome.Rejected,
            Device(http).accounts.signIn(SignInProvider.GOOGLE, harness.identities.token("g-2", audience = "another-app")),
        )
    }

    @Test
    fun signing_out_and_deleting_through_the_contract_leave_the_device_clean() = server { http ->
        val device = Device(http)
        device.accounts.signIn(SignInProvider.GOOGLE, harness.identities.google("g-1"))
        device.accounts.signOut()
        assertTrue(device.store.values.isEmpty())

        val other = Device(http)
        other.playToday()
        val deleted = other.accounts.delete()
        assertFalse(deleted.subscriptionStillActive)
        assertTrue(other.store.values.isEmpty())
    }

    @Test
    fun an_account_that_cannot_be_reached_is_thrown_not_dressed_as_a_refusal() = server { _ ->
        val device = Device(unreachable)

        assertFailsWith<Exception> { device.accounts.account() }
        assertFailsWith<Exception> { device.accounts.rename("Zaneta") }
    }
}
