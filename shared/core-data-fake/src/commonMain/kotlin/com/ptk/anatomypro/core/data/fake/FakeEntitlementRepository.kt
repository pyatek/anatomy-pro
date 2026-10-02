package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.model.Entitlements
import com.ptk.anatomypro.core.data.model.PurchaseOutcome
import com.ptk.anatomypro.core.data.model.SubscriptionPlan
import com.ptk.anatomypro.core.data.repository.EntitlementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Screen 18. Skeletal is free forever, so the default state is already playable. */
class FakeEntitlementRepository(
    private val behaviour: FakeBehaviour = FakeBehaviour(),
    initial: Entitlements = Entitlements(subscribed = false, ownedSystems = emptySet()),
    private val purchaseSucceeds: Boolean = true,
) : EntitlementRepository {

    private val _entitlements = MutableStateFlow(initial)
    override val entitlements: Flow<Entitlements> = _entitlements.asStateFlow()

    override suspend fun plans(): List<SubscriptionPlan> = behaviour.respond {
        listOf(
            SubscriptionPlan("monthly", "Miesięcznie", "29 zł", periodMonths = 1),
            SubscriptionPlan("yearly", "Rocznie", "199 zł", periodMonths = 12),
        )
    }

    override suspend fun purchase(plan: SubscriptionPlan): PurchaseOutcome = behaviour.respond {
        if (!purchaseSucceeds) {
            PurchaseOutcome.Failed("Płatność odrzucona")
        } else {
            _entitlements.value = _entitlements.value.copy(subscribed = true)
            PurchaseOutcome.Succeeded(_entitlements.value)
        }
    }

    override suspend fun restore(): PurchaseOutcome = behaviour.respond {
        PurchaseOutcome.Succeeded(_entitlements.value)
    }
}
