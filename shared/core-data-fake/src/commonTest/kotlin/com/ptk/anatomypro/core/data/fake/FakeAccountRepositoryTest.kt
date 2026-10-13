package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.model.Account
import com.ptk.anatomypro.core.data.model.RenameOutcome
import com.ptk.anatomypro.core.data.model.SignInOutcome
import com.ptk.anatomypro.core.data.model.SignInProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class FakeAccountRepositoryTest {

    @Test
    fun a_new_device_has_a_generated_nickname_and_no_sign_in() = runTest {
        val account = FakeAccountRepository().account()

        assertTrue(account.nickname.startsWith("student-"))
        assertTrue(account.linked.isEmpty())
        assertNull(account.nicknameLockedUntil)
    }

    @Test
    fun a_free_well_formed_nickname_is_taken_on_and_kept() = runTest {
        val accounts = FakeAccountRepository()

        val outcome = accounts.rename("costa_xii")

        assertEquals("costa_xii", assertIs<RenameOutcome.Renamed>(outcome).account.nickname)
        assertEquals("costa_xii", accounts.account().nickname)
    }

    @Test
    fun each_refusal_is_its_own_outcome_and_changes_nothing() = runTest {
        val accounts = FakeAccountRepository(taken = setOf("Foramen"))
        val before = accounts.account()

        assertEquals(RenameOutcome.Invalid, accounts.rename("no"))
        // Unique whatever the case (§5.2).
        assertEquals(RenameOutcome.Taken, accounts.rename("FORAMEN"))
        assertEquals(before, accounts.account())

        val locked = FakeAccountRepository(initial = Account("student-1", nicknameLockedUntil = Instant.parse("2026-11-12T00:00:00Z")))
        assertEquals(RenameOutcome.Locked, locked.rename("costa_xii"))
        assertEquals("student-1", locked.account().nickname)
    }

    @Test
    fun signing_in_attaches_the_provider_to_the_account() = runTest {
        val accounts = FakeAccountRepository()
        val nickname = accounts.account().nickname

        val outcome = assertIs<SignInOutcome.SignedIn>(accounts.signIn(SignInProvider.GOOGLE, "token"))

        assertEquals(false, outcome.merged)
        assertEquals(Account(nickname, linked = setOf(SignInProvider.GOOGLE)), accounts.account())
    }

    @Test
    fun signing_in_to_an_identity_that_opens_another_account_becomes_that_account() = runTest {
        // §5.3, case 3: the signed-in account's nickname is the one kept.
        val accounts = FakeAccountRepository(elsewhere = Account("vesalius_1543", linked = setOf(SignInProvider.APPLE)))

        val outcome = assertIs<SignInOutcome.SignedIn>(accounts.signIn(SignInProvider.APPLE, "token"))

        assertTrue(outcome.merged)
        assertEquals("vesalius_1543", accounts.account().nickname)
    }

    @Test
    fun a_token_the_server_does_not_believe_signs_nobody_in() = runTest {
        val accounts = FakeAccountRepository(believesTokens = false)

        assertEquals(SignInOutcome.Rejected, accounts.signIn(SignInProvider.GOOGLE, "token"))
        assertTrue(accounts.account().linked.isEmpty())
    }

    @Test
    fun signing_out_or_deleting_leaves_the_device_as_someone_new() = runTest {
        val accounts = FakeAccountRepository(initial = Account("costa_xii", linked = setOf(SignInProvider.GOOGLE)))

        accounts.signOut()
        val afterSignOut = accounts.account()
        assertNotEquals("costa_xii", afterSignOut.nickname)
        assertTrue(afterSignOut.linked.isEmpty())

        accounts.rename("os_hyoideum")
        accounts.delete()
        assertNotEquals("os_hyoideum", accounts.account().nickname)
    }

    @Test
    fun deleting_says_whether_a_subscription_is_left_running() = runTest {
        assertEquals(false, FakeAccountRepository().delete().subscriptionStillActive)
        assertEquals(true, FakeAccountRepository(subscribed = true).delete().subscriptionStillActive)
    }

    @Test
    fun the_sign_in_sheet_hands_over_a_token_or_nothing_when_it_is_closed() = runTest {
        assertEquals("fake-id-token", FakeProviderSignIn().idToken(SignInProvider.GOOGLE))
        assertNull(FakeProviderSignIn(cancels = true).idToken(SignInProvider.GOOGLE))
        assertEquals(listOf(SignInProvider.GOOGLE, SignInProvider.APPLE), FakeProviderSignIn().providers)
    }
}
