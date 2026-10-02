package com.ptk.anatomypro.core.data.model

import com.ptk.anatomypro.core.model.SystemId

/** Skeletal is free forever (§10). Enforced here, not by whoever fills ownedSystems. */
val FREE_SYSTEMS: Set<SystemId> = setOf(SystemId("skeletal"))

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
