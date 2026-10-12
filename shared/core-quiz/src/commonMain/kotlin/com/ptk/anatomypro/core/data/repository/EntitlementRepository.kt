package com.ptk.anatomypro.core.data.repository

import com.ptk.anatomypro.core.data.model.Entitlements
import com.ptk.anatomypro.core.data.model.PurchaseOutcome
import com.ptk.anatomypro.core.data.model.SubscriptionPlan
import kotlinx.coroutines.flow.Flow

/** Screen 18. */
interface EntitlementRepository {
    val entitlements: Flow<Entitlements>
    suspend fun plans(): List<SubscriptionPlan>
    suspend fun purchase(plan: SubscriptionPlan): PurchaseOutcome
    suspend fun restore(): PurchaseOutcome
}
