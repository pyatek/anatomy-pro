package com.ptk.anatomypro.api.client

import com.ptk.anatomypro.api.ApiPaths
import com.ptk.anatomypro.api.ApplePurchaseRequest
import com.ptk.anatomypro.api.EntitlementsResponse
import com.ptk.anatomypro.api.GooglePurchaseRequest
import com.ptk.anatomypro.api.Plan
import com.ptk.anatomypro.api.PlansResponse
import com.ptk.anatomypro.api.PurchaseResponse
import com.ptk.anatomypro.core.data.model.Entitlements
import com.ptk.anatomypro.core.data.model.PurchaseOutcome
import com.ptk.anatomypro.core.data.model.SubscriptionPlan
import com.ptk.anatomypro.core.data.repository.EntitlementRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * What a store hands the device when something is bought or restored, and the device hands
 * the server: the only thing the server will take as evidence of a purchase (§8.1).
 */
sealed interface PurchaseProof {
    /** Play Billing's purchase token, and the product it says it is for. */
    data class GooglePlay(val productId: String, val purchaseToken: String) : PurchaseProof

    /** StoreKit 2's signed transaction (its `jwsRepresentation`), exactly as received. */
    data class AppStore(val signedTransaction: String) : PurchaseProof
}

/**
 * Whether this account has paid, as the server says (backend spec §8.1).
 *
 * What the server last said is kept on the device and said first, because topic quizzes
 * work with no connection and a subscriber must not lose them on a train. A device that
 * has never been able to ask is not a subscriber.
 *
 * **Buying is half built.** The server's half is here: [onSale] reads what may be bought
 * and [present] hands the server a store's proof of purchase. The stores' half — Play
 * Billing and StoreKit 2 — is not (plan stage 7.5), so [plans] is still empty and
 * [purchase] and [restore] still fail: titles and prices are the store's to state, and
 * nothing here can ask a store for them.
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

    /** The product ids on sale and how long each runs. No prices: those are asked of the store, on the device (§8.3). */
    suspend fun onSale(): List<Plan> = api.get(ApiPaths.PLANS, PlansResponse.serializer()).plans

    /**
     * Hands the server a store's proof of a purchase — a new one, or one being restored —
     * and says what the server made of it. Safe to call again with the same proof.
     *
     * - `verified`: this account is a subscriber. Remembered, as [entitlements] would.
     * - `owned_by_another_account`: the purchase is real and another account has it. The
     *   user should sign in; nothing was moved (§8.1).
     * - `pending`: bought, and the store has not been paid yet. Nothing is unlocked, and it
     *   will be without presenting it again.
     * - `invalid`: the store does not stand behind it, or it has run out.
     *
     * [entitlements] is a cold flow: collect it again after this to see the change.
     *
     * @throws ApiFailure when the server answered and could not give a verdict — status 503
     *   when it could not reach the store, or is not set up for it. Anything else thrown
     *   is the server not being reached. In both cases present the same proof again later.
     */
    suspend fun present(proof: PurchaseProof): PurchaseResponse {
        val answer = when (proof) {
            is PurchaseProof.GooglePlay -> api.post(
                ApiPaths.GOOGLE_PURCHASE,
                GooglePurchaseRequest.serializer(),
                GooglePurchaseRequest(proof.productId, proof.purchaseToken),
                PurchaseResponse.serializer(),
            )
            is PurchaseProof.AppStore -> api.post(
                ApiPaths.APPLE_PURCHASE,
                ApplePurchaseRequest.serializer(),
                ApplePurchaseRequest(proof.signedTransaction),
                PurchaseResponse.serializer(),
            )
        }
        store.write(ApiClient.SUBSCRIBED_KEY, answer.entitlements.subscribed.toString())
        return answer
    }

    override suspend fun plans(): List<SubscriptionPlan> = emptyList()

    override suspend fun purchase(plan: SubscriptionPlan): PurchaseOutcome = PurchaseOutcome.Failed(NOT_BUILT)

    override suspend fun restore(): PurchaseOutcome = PurchaseOutcome.Failed(NOT_BUILT)

    private companion object {
        const val NOT_BUILT = "purchases are not built yet"
    }
}
