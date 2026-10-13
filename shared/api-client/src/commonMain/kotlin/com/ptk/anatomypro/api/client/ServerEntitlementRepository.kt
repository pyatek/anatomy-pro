package com.ptk.anatomypro.api.client

import com.ptk.anatomypro.api.ApiPaths
import com.ptk.anatomypro.api.EntitlementsResponse
import com.ptk.anatomypro.core.data.model.Entitlements
import com.ptk.anatomypro.core.data.model.PurchaseOutcome
import com.ptk.anatomypro.core.data.model.SubscriptionPlan
import com.ptk.anatomypro.core.data.repository.EntitlementRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Whether this account has paid, as the server says (backend spec §8.1).
 *
 * What the server last said is kept on the device and said first, because topic quizzes
 * work with no connection and a subscriber must not lose them on a train. A device that
 * has never been able to ask is not a subscriber.
 *
 * **Buying is not built.** [plans] is empty and [purchase] and [restore] fail until the
 * stores are wired in (plan stage 7); nothing here can make anyone a subscriber.
 */
class ServerEntitlementRepository(private val api: ApiClient, private val store: ClientStore) : EntitlementRepository {

    override val entitlements: Flow<Entitlements> = flow {
        val remembered = store.read(ApiClient.SUBSCRIBED_KEY)?.let { it == "true" }
        if (remembered != null) emit(Entitlements(remembered, emptySet()))
        try {
            val answer = api.get(ApiPaths.ENTITLEMENTS, EntitlementsResponse.serializer())
            store.write(ApiClient.SUBSCRIBED_KEY, answer.subscribed.toString())
            if (answer.subscribed != remembered) emit(Entitlements(answer.subscribed, emptySet()))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (remembered == null) emit(Entitlements(subscribed = false, ownedSystems = emptySet()))
        }
    }

    override suspend fun plans(): List<SubscriptionPlan> = emptyList()

    override suspend fun purchase(plan: SubscriptionPlan): PurchaseOutcome = PurchaseOutcome.Failed(NOT_BUILT)

    override suspend fun restore(): PurchaseOutcome = PurchaseOutcome.Failed(NOT_BUILT)

    private companion object {
        const val NOT_BUILT = "purchases are not built yet"
    }
}
