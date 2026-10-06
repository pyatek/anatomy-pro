package com.ptk.anatomypro.core.data.model

import com.ptk.anatomypro.core.model.SystemId

/**
 * What a user may be quizzed on without paying: the skeletal system (design spec §29.2).
 *
 * Entitlements gate quiz topics, not packs — the whole atlas is free (all-screens spec
 * §15.2). The id is the one packs and the atlas use.
 */
val FREE_SYSTEMS: Set<SystemId> = setOf(SystemId("skeletal-system"))

data class Entitlements(val subscribed: Boolean, val ownedSystems: Set<SystemId>) {
    fun allows(system: SystemId): Boolean =
        subscribed || system in FREE_SYSTEMS || system in ownedSystems
}

data class SubscriptionPlan(
    val id: String,
    val title: String,
    val priceLabel: String,
    val periodMonths: Int,
)

sealed interface PurchaseOutcome {
    data class Succeeded(val entitlements: Entitlements) : PurchaseOutcome
    data object Cancelled : PurchaseOutcome
    data class Failed(val message: String) : PurchaseOutcome
}
