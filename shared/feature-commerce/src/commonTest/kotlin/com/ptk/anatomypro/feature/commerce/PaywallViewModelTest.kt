package com.ptk.anatomypro.feature.commerce

import com.ptk.anatomypro.core.data.NotBuiltEntitlementRepository
import com.ptk.anatomypro.core.data.fake.FakeEntitlementRepository
import com.ptk.anatomypro.core.data.model.Entitlements
import com.ptk.anatomypro.core.data.model.PurchaseOutcome
import com.ptk.anatomypro.core.data.model.SubscriptionPlan
import com.ptk.anatomypro.core.data.repository.EntitlementRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaywallViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    /** A repository whose purchase answers whatever the test says. */
    private class Scripted(var outcome: () -> PurchaseOutcome) : EntitlementRepository {
        private val real = FakeEntitlementRepository()
        var purchases = 0
        override val entitlements = real.entitlements
        override suspend fun plans() = real.plans()
        override suspend fun purchase(plan: SubscriptionPlan): PurchaseOutcome {
            purchases++
            return outcome()
        }
        override suspend fun restore() = real.restore()
    }

    @Test
    fun a_free_user_is_offered_the_plans() = runTest(dispatcher) {
        // §9, screen 18: free.
        val model = PaywallViewModel(FakeEntitlementRepository())
        advanceUntilIdle()

        val state = model.state.value
        assertFalse(state.subscribed)
        assertEquals(listOf("monthly", "yearly"), state.plans.map { it.id })
        assertFalse(state.isLoading)
        assertNull(state.notice)
    }

    @Test
    fun a_subscriber_is_told_they_are_subscribed() = runTest(dispatcher) {
        // §9, screen 18: subscribed.
        val model = PaywallViewModel(FakeEntitlementRepository(initial = Entitlements(subscribed = true, ownedSystems = emptySet())))
        advanceUntilIdle()

        assertTrue(model.state.value.subscribed)
    }

    @Test
    fun a_purchase_that_succeeds_makes_the_user_a_subscriber() = runTest(dispatcher) {
        val model = PaywallViewModel(FakeEntitlementRepository())
        advanceUntilIdle()

        model.onPurchase(model.state.value.plans.first())
        advanceUntilIdle()

        assertTrue(model.state.value.subscribed)
        assertNull(model.state.value.purchasing)
        assertNull(model.state.value.notice)
    }

    @Test
    fun a_purchase_that_fails_says_so_and_leaves_the_user_free() = runTest(dispatcher) {
        // §9, screen 18: purchase failed.
        val model = PaywallViewModel(FakeEntitlementRepository(purchaseSucceeds = false))
        advanceUntilIdle()

        model.onPurchase(model.state.value.plans.first())
        advanceUntilIdle()

        assertEquals(PaywallNotice.PURCHASE_FAILED, model.state.value.notice)
        assertFalse(model.state.value.subscribed)
        assertFalse(model.state.value.busy)
    }

    @Test
    fun a_purchase_that_throws_is_a_failed_purchase_not_a_crash() = runTest(dispatcher) {
        // Review Focus 2: the store could not be reached at all.
        val model = PaywallViewModel(Scripted { throw IllegalStateException("store unreachable") })
        advanceUntilIdle()

        model.onPurchase(model.state.value.plans.first())
        advanceUntilIdle()

        assertEquals(PaywallNotice.PURCHASE_FAILED, model.state.value.notice)
        assertFalse(model.state.value.busy)
    }

    @Test
    fun a_cancelled_purchase_says_nothing() = runTest(dispatcher) {
        // Decision 3: the user closed the sheet; they know.
        val model = PaywallViewModel(Scripted { PurchaseOutcome.Cancelled })
        advanceUntilIdle()

        model.onPurchase(model.state.value.plans.first())
        advanceUntilIdle()

        assertNull(model.state.value.notice)
        assertFalse(model.state.value.busy)
    }

    @Test
    fun pressing_buy_twice_buys_once() = runTest(dispatcher) {
        // Review Focus 1.
        val repository = Scripted { PurchaseOutcome.Cancelled }
        val model = PaywallViewModel(repository)
        advanceUntilIdle()
        val plan = model.state.value.plans.first()

        model.onPurchase(plan)
        model.onPurchase(plan)
        model.onPurchase(model.state.value.plans.last())
        advanceUntilIdle()

        assertEquals(1, repository.purchases)
    }

    @Test
    fun a_failed_purchase_can_be_tried_again_and_the_notice_goes_when_it_is() = runTest(dispatcher) {
        var fail = true
        val real = FakeEntitlementRepository()
        val repository = object : EntitlementRepository by real {
            override suspend fun purchase(plan: SubscriptionPlan): PurchaseOutcome =
                if (fail) PurchaseOutcome.Failed("declined") else real.purchase(plan)
        }
        val model = PaywallViewModel(repository)
        advanceUntilIdle()
        model.onPurchase(model.state.value.plans.first())
        advanceUntilIdle()
        assertEquals(PaywallNotice.PURCHASE_FAILED, model.state.value.notice)

        fail = false
        model.onPurchase(model.state.value.plans.first())
        advanceUntilIdle()

        assertNull(model.state.value.notice)
        assertTrue(model.state.value.subscribed)
    }

    @Test
    fun restoring_with_nothing_to_restore_says_so() = runTest(dispatcher) {
        // Decision 4. The fake's restore succeeds with whatever is held, which for a free
        // user is nothing.
        val model = PaywallViewModel(FakeEntitlementRepository())
        advanceUntilIdle()

        model.onRestore()
        advanceUntilIdle()

        assertEquals(PaywallNotice.NOTHING_TO_RESTORE, model.state.value.notice)
        assertFalse(model.state.value.restoring)
    }

    @Test
    fun plans_that_cannot_be_loaded_are_a_failure_that_can_be_retried() = runTest(dispatcher) {
        var broken = true
        val real = FakeEntitlementRepository()
        val repository = object : EntitlementRepository by real {
            override suspend fun plans() = if (broken) throw IllegalStateException("no plans") else real.plans()
        }
        val model = PaywallViewModel(repository)
        advanceUntilIdle()
        assertEquals(PaywallNotice.PLANS_FAILED, model.state.value.notice)

        broken = false
        model.onRetry()
        advanceUntilIdle()

        assertNull(model.state.value.notice)
        assertEquals(2, model.state.value.plans.size)
    }

    @Test
    fun there_is_no_paywall_where_the_repository_refuses() {
        // All-screens spec §6: a release build must never present a purchase that cannot happen.
        assertFalse(commerceIsBuilt(NotBuiltEntitlementRepository))
        assertTrue(commerceIsBuilt(FakeEntitlementRepository()))
    }
}
