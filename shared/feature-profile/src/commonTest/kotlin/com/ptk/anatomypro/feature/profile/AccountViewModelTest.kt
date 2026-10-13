package com.ptk.anatomypro.feature.profile

import com.ptk.anatomypro.core.data.NotBuiltAccountRepository
import com.ptk.anatomypro.core.data.NotBuiltProviderSignIn
import com.ptk.anatomypro.core.data.fake.FakeAccountRepository
import com.ptk.anatomypro.core.data.fake.FakeBehaviour
import com.ptk.anatomypro.core.data.fake.FakeProviderSignIn
import com.ptk.anatomypro.core.data.model.Account
import com.ptk.anatomypro.core.data.model.AccountDeleted
import com.ptk.anatomypro.core.data.model.RenameOutcome
import com.ptk.anatomypro.core.data.model.SignInOutcome
import com.ptk.anatomypro.core.data.model.SignInProvider
import com.ptk.anatomypro.core.data.repository.AccountRepository
import com.ptk.anatomypro.core.data.repository.ProviderSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class AccountViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-10-13T10:00:00Z")
    private val lockedUntil = Instant.parse("2026-11-12T10:00:00Z")

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun model(
        accounts: AccountRepository = FakeAccountRepository(),
        signIn: ProviderSignIn = FakeProviderSignIn(),
    ) = AccountViewModel(accounts, signIn, now = { now }, zone = TimeZone.UTC)

    /** A repository that answers as told, and counts what it was asked. */
    private class Scripted(
        private val inner: AccountRepository = FakeAccountRepository(),
        var account: (suspend () -> Account)? = null,
        var rename: (suspend (String) -> RenameOutcome)? = null,
        var signIn: (suspend () -> SignInOutcome)? = null,
        var leave: (suspend () -> Unit)? = null,
    ) : AccountRepository {
        var renames = 0
        var signIns = 0
        var signOuts = 0
        var deletions = 0

        override suspend fun account(): Account = account?.invoke() ?: inner.account()
        override suspend fun rename(nickname: String): RenameOutcome {
            renames++
            return rename?.invoke(nickname) ?: inner.rename(nickname)
        }
        override suspend fun signIn(provider: SignInProvider, idToken: String): SignInOutcome {
            signIns++
            return signIn?.invoke() ?: inner.signIn(provider, idToken)
        }
        override suspend fun signOut() {
            signOuts++
            leave?.invoke()
            inner.signOut()
        }
        override suspend fun delete(): AccountDeleted {
            deletions++
            leave?.invoke()
            return inner.delete()
        }
    }

    // --- reading --------------------------------------------------------------------

    @Test
    fun the_account_is_shown_with_its_nickname_ready_to_edit() = runTest(dispatcher) {
        val model = model()
        assertTrue(model.state.value.isLoading)
        advanceUntilIdle()

        val state = model.state.value
        assertEquals("student-4821", state.account?.nickname)
        assertEquals("student-4821", state.draft)
        assertEquals(listOf(SignInProvider.GOOGLE, SignInProvider.APPLE), state.providers)
        assertFalse(state.isLoading)
        assertFalse(state.failed)
        // Nothing has been typed, so there is nothing to save.
        assertFalse(state.canSave)
    }

    @Test
    fun an_account_that_cannot_be_read_says_so_and_can_be_asked_for_again() = runTest(dispatcher) {
        var offline = true
        val accounts = Scripted(account = { if (offline) throw IllegalStateException("offline") else Account("costa_xii") })
        val model = model(accounts)
        advanceUntilIdle()

        assertTrue(model.state.value.failed)
        assertNull(model.state.value.account)

        offline = false
        model.onRetry()
        advanceUntilIdle()

        assertFalse(model.state.value.failed)
        assertEquals("costa_xii", model.state.value.account?.nickname)
    }

    // --- the nickname ---------------------------------------------------------------

    @Test
    fun a_new_nickname_is_saved_and_said_to_be() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.onNicknameChanged("costa_xii")
        assertTrue(model.state.value.canSave)
        model.onSaveNickname()
        advanceUntilIdle()

        val state = model.state.value
        assertEquals("costa_xii", state.account?.nickname)
        assertEquals("costa_xii", state.draft)
        assertEquals(NicknameNotice.SAVED, state.nicknameNotice)
        assertFalse(state.busy)
    }

    @Test
    fun a_nickname_of_the_wrong_shape_is_refused_without_asking_the_server() = runTest(dispatcher) {
        val accounts = Scripted()
        val model = model(accounts)
        advanceUntilIdle()

        model.onNicknameChanged("two words")
        model.onSaveNickname()
        advanceUntilIdle()

        assertEquals(NicknameNotice.INVALID, model.state.value.nicknameNotice)
        assertEquals(0, accounts.renames)
        assertEquals("student-4821", model.state.value.account?.nickname)
    }

    @Test
    fun a_nickname_the_server_calls_invalid_is_refused_in_the_same_words() = runTest(dispatcher) {
        // The blocklist is the server's: a well-formed nickname can still be refused (§5.2).
        val model = model(Scripted(rename = { RenameOutcome.Invalid }))
        advanceUntilIdle()

        model.onNicknameChanged("well_formed")
        model.onSaveNickname()
        advanceUntilIdle()

        assertEquals(NicknameNotice.INVALID, model.state.value.nicknameNotice)
    }

    @Test
    fun a_taken_nickname_is_refused_and_what_was_typed_is_kept() = runTest(dispatcher) {
        val model = model(FakeAccountRepository(taken = setOf("foramen")))
        advanceUntilIdle()

        model.onNicknameChanged("Foramen")
        model.onSaveNickname()
        advanceUntilIdle()

        val state = model.state.value
        assertEquals(NicknameNotice.TAKEN, state.nicknameNotice)
        assertEquals("Foramen", state.draft)
        assertEquals("student-4821", state.account?.nickname)
    }

    @Test
    fun a_locked_nickname_shows_the_day_it_opens_and_cannot_be_saved() = runTest(dispatcher) {
        val accounts = Scripted(FakeAccountRepository(initial = Account("student-7", nicknameLockedUntil = lockedUntil)))
        val model = model(accounts)
        advanceUntilIdle()

        assertEquals(LocalDate(2026, 11, 12), model.state.value.lockedUntil)

        model.onNicknameChanged("costa_xii")
        assertFalse(model.state.value.canSave)
        model.onSaveNickname()
        advanceUntilIdle()

        assertEquals(0, accounts.renames)
    }

    @Test
    fun a_lock_that_has_run_out_is_no_lock() = runTest(dispatcher) {
        val model = model(Scripted(account = { Account("student-7", nicknameLockedUntil = now - 1.seconds) }))
        advanceUntilIdle()

        assertNull(model.state.value.lockedUntil)
    }

    @Test
    fun a_lock_first_heard_of_on_saving_is_said_and_the_account_read_again_for_its_date() = runTest(dispatcher) {
        var locked = false
        val accounts = Scripted(
            account = { Account("student-7", nicknameLockedUntil = lockedUntil.takeIf { locked }) },
            rename = { locked = true; RenameOutcome.Locked },
        )
        val model = model(accounts)
        advanceUntilIdle()
        assertNull(model.state.value.lockedUntil)

        model.onNicknameChanged("costa_xii")
        model.onSaveNickname()
        advanceUntilIdle()

        assertEquals(NicknameNotice.LOCKED, model.state.value.nicknameNotice)
        assertEquals(LocalDate(2026, 11, 12), model.state.value.lockedUntil)
    }

    @Test
    fun a_save_that_does_not_get_through_says_so_and_keeps_what_was_typed() = runTest(dispatcher) {
        val model = model(Scripted(rename = { throw IllegalStateException("offline") }))
        advanceUntilIdle()

        model.onNicknameChanged("costa_xii")
        model.onSaveNickname()
        advanceUntilIdle()

        val state = model.state.value
        assertEquals(NicknameNotice.FAILED, state.nicknameNotice)
        assertEquals("costa_xii", state.draft)
        assertTrue(state.canSave)
    }

    @Test
    fun typing_again_takes_the_last_answer_away() = runTest(dispatcher) {
        val model = model(FakeAccountRepository(taken = setOf("foramen")))
        advanceUntilIdle()
        model.onNicknameChanged("foramen")
        model.onSaveNickname()
        advanceUntilIdle()

        model.onNicknameChanged("foramen2")

        assertNull(model.state.value.nicknameNotice)
    }

    @Test
    fun nothing_longer_than_a_nickname_may_be_is_typed() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.onNicknameChanged("a".repeat(30))

        assertEquals(20, model.state.value.draft.length)
    }

    // --- signing in -----------------------------------------------------------------

    @Test
    fun signing_in_links_the_provider_and_says_so() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.onSignIn(SignInProvider.GOOGLE)
        advanceUntilIdle()

        val state = model.state.value
        assertEquals(setOf(SignInProvider.GOOGLE), state.account?.linked)
        assertEquals(SignInNotice.SIGNED_IN, state.signInNotice)
        assertEquals("student-4821", state.account?.nickname)
    }

    @Test
    fun a_sheet_that_was_closed_asks_the_server_nothing_and_says_nothing() = runTest(dispatcher) {
        val accounts = Scripted()
        val model = model(accounts, FakeProviderSignIn(cancels = true))
        advanceUntilIdle()

        model.onSignIn(SignInProvider.GOOGLE)
        advanceUntilIdle()

        assertEquals(0, accounts.signIns)
        assertNull(model.state.value.signInNotice)
        assertFalse(model.state.value.busy)
    }

    @Test
    fun signing_in_to_an_account_from_another_device_replaces_this_one_and_says_so() = runTest(dispatcher) {
        val model = model(FakeAccountRepository(elsewhere = Account("vesalius_1543", linked = setOf(SignInProvider.APPLE))))
        advanceUntilIdle()
        model.onNicknameChanged("half_typed")

        model.onSignIn(SignInProvider.APPLE)
        advanceUntilIdle()

        val state = model.state.value
        assertEquals(SignInNotice.MERGED, state.signInNotice)
        assertEquals("vesalius_1543", state.account?.nickname)
        // What was being typed was for the account that is gone.
        assertEquals("vesalius_1543", state.draft)
    }

    @Test
    fun a_token_the_server_refuses_is_said_and_nothing_is_linked() = runTest(dispatcher) {
        val model = model(FakeAccountRepository(believesTokens = false))
        advanceUntilIdle()

        model.onSignIn(SignInProvider.GOOGLE)
        advanceUntilIdle()

        assertEquals(SignInNotice.REJECTED, model.state.value.signInNotice)
        assertEquals(emptySet(), model.state.value.account?.linked)
    }

    @Test
    fun a_sign_in_that_does_not_get_through_says_so() = runTest(dispatcher) {
        val model = model(Scripted(signIn = { throw IllegalStateException("offline") }))
        advanceUntilIdle()

        model.onSignIn(SignInProvider.GOOGLE)
        advanceUntilIdle()

        assertEquals(SignInNotice.FAILED, model.state.value.signInNotice)
        assertFalse(model.state.value.busy)
    }

    @Test
    fun a_provider_already_linked_is_not_signed_in_with_again() = runTest(dispatcher) {
        val accounts = Scripted(FakeAccountRepository(initial = Account("costa_xii", linked = setOf(SignInProvider.GOOGLE))))
        val model = model(accounts)
        advanceUntilIdle()

        model.onSignIn(SignInProvider.GOOGLE)
        advanceUntilIdle()

        assertEquals(0, accounts.signIns)
    }

    @Test
    fun a_provider_this_device_cannot_use_is_listed_only_where_it_is_linked() = runTest(dispatcher) {
        // An account signed in with Apple on a phone, seen on a device that has only Google.
        val model = model(
            FakeAccountRepository(initial = Account("costa_xii", linked = setOf(SignInProvider.APPLE))),
            FakeProviderSignIn(providers = listOf(SignInProvider.GOOGLE)),
        )
        advanceUntilIdle()

        assertEquals(listOf(SignInProvider.GOOGLE, SignInProvider.APPLE), model.state.value.providers)
        assertTrue(model.state.value.canSignInWith(SignInProvider.GOOGLE))
        assertFalse(model.state.value.canSignInWith(SignInProvider.APPLE))

        val nothing = model(FakeAccountRepository(), NotBuiltProviderSignIn)
        advanceUntilIdle()
        assertEquals(emptyList(), nothing.state.value.providers)
    }

    // --- leaving --------------------------------------------------------------------

    @Test
    fun there_is_no_signing_out_of_an_account_nobody_signed_in_to() = runTest(dispatcher) {
        // Signing out of an anonymous account would delete it: nothing could open it again.
        val model = model()
        advanceUntilIdle()

        assertFalse(model.state.value.canSignOut)
        model.onLeaveRequested(Leaving.SIGN_OUT)

        assertNull(model.state.value.confirming)
    }

    @Test
    fun signing_out_is_asked_about_first_and_then_ends_the_screen() = runTest(dispatcher) {
        val accounts = Scripted(FakeAccountRepository(initial = Account("costa_xii", linked = setOf(SignInProvider.GOOGLE))))
        val model = model(accounts)
        advanceUntilIdle()

        model.onLeaveRequested(Leaving.SIGN_OUT)
        assertEquals(Leaving.SIGN_OUT, model.state.value.confirming)
        assertEquals(0, accounts.signOuts)

        model.onLeaveConfirmed()
        advanceUntilIdle()

        assertEquals(1, accounts.signOuts)
        assertEquals(Left.SignedOut, model.state.value.left)
        assertNull(model.state.value.confirming)
    }

    @Test
    fun a_question_answered_no_changes_nothing() = runTest(dispatcher) {
        val accounts = Scripted()
        val model = model(accounts)
        advanceUntilIdle()

        model.onLeaveRequested(Leaving.DELETE)
        model.onLeaveDismissed()
        model.onLeaveConfirmed()
        advanceUntilIdle()

        assertNull(model.state.value.confirming)
        assertNull(model.state.value.left)
        assertEquals(0, accounts.deletions)
    }

    @Test
    fun deleting_is_asked_about_first_and_then_says_what_became_of_a_subscription() = runTest(dispatcher) {
        val model = model(FakeAccountRepository(subscribed = true))
        advanceUntilIdle()

        model.onLeaveRequested(Leaving.DELETE)
        assertEquals(Leaving.DELETE, model.state.value.confirming)
        model.onLeaveConfirmed()
        advanceUntilIdle()

        assertEquals(Left.Deleted(subscriptionStillActive = true), model.state.value.left)
    }

    @Test
    fun a_deletion_that_does_not_get_through_says_so_and_the_question_stands() = runTest(dispatcher) {
        val model = model(Scripted(leave = { throw IllegalStateException("offline") }))
        advanceUntilIdle()

        model.onLeaveRequested(Leaving.DELETE)
        model.onLeaveConfirmed()
        advanceUntilIdle()

        val state = model.state.value
        assertTrue(state.leavingFailed)
        assertEquals(Leaving.DELETE, state.confirming)
        assertNull(state.left)
        assertEquals("student-4821", state.account?.nickname)
    }

    @Test
    fun once_the_account_is_left_nothing_more_is_asked_of_it() = runTest(dispatcher) {
        val accounts = Scripted()
        val model = model(accounts)
        advanceUntilIdle()
        model.onLeaveRequested(Leaving.DELETE)
        model.onLeaveConfirmed()
        advanceUntilIdle()

        model.onNicknameChanged("costa_xii")
        model.onSaveNickname()
        model.onSignIn(SignInProvider.GOOGLE)
        model.onLeaveRequested(Leaving.DELETE)
        advanceUntilIdle()

        assertEquals(0, accounts.renames)
        assertEquals(0, accounts.signIns)
        assertEquals(1, accounts.deletions)
    }

    @Test
    fun one_thing_at_a_time() = runTest(dispatcher) {
        val accounts = Scripted(FakeAccountRepository(behaviour = FakeBehaviour(delay = 1.seconds)))
        val model = model(accounts)
        advanceUntilIdle()

        model.onNicknameChanged("costa_xii")
        model.onSaveNickname()
        runCurrent()
        assertTrue(model.state.value.busy)
        model.onSaveNickname()
        model.onSignIn(SignInProvider.GOOGLE)
        model.onLeaveRequested(Leaving.DELETE)
        model.onLeaveConfirmed()
        advanceUntilIdle()

        assertEquals(1, accounts.renames)
        assertEquals(0, accounts.signIns)
        assertEquals(0, accounts.deletions)
    }

    @Test
    fun there_is_an_account_to_show_only_where_one_was_built() {
        assertFalse(accountIsBuilt(NotBuiltAccountRepository))
        assertTrue(accountIsBuilt(FakeAccountRepository()))
    }
}
