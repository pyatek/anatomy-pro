package com.ptk.anatomypro.backend.subscriptions

import com.ptk.anatomypro.api.ApiErrorCode
import com.ptk.anatomypro.api.ApiPaths
import com.ptk.anatomypro.api.ApplePurchaseRequest
import com.ptk.anatomypro.api.EntitlementsResponse
import com.ptk.anatomypro.api.GooglePurchaseRequest
import com.ptk.anatomypro.api.PlansResponse
import com.ptk.anatomypro.api.PurchaseResponse
import com.ptk.anatomypro.backend.ApiException
import com.ptk.anatomypro.backend.Settings
import com.ptk.anatomypro.backend.accounts.Accounts
import com.ptk.anatomypro.backend.accounts.account
import com.ptk.anatomypro.backend.accounts.bearerToken
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

/**
 * What an account is entitled to, which is the one thing that unlocks anything (§8.1): a
 * verified subscription that has not run out — or [Settings.everyoneIsSubscribed], the
 * override a deployment turns on by name, which claims no expiry because there is none.
 */
class Entitlements(private val subscriptions: Subscriptions, private val settings: Settings, private val now: () -> Instant) {
    /** Until when [account] has really paid, the override aside; null when it has not. */
    fun paidUntil(account: UUID): Instant? = subscriptions.entitledUntil(account, now())

    fun of(account: UUID): EntitlementsResponse {
        val until = paidUntil(account)
        return EntitlementsResponse(subscribed = settings.everyoneIsSubscribed || until != null, expiresAtEpochMillis = until?.toEpochMilli())
    }
}

/** The calls a device makes about subscribing (§8). */
fun Route.subscriptionRoutes(accounts: Accounts, entitlements: Entitlements, google: GooglePlay?, apple: AppStore?, settings: Settings) {
    /** A store this server has not been given the means to ask is "not now", never "your purchase is bad". */
    fun notConfigured() = ApiException(HttpStatusCode.ServiceUnavailable, ApiErrorCode.UNKNOWN, "purchases from this store are not set up")

    // Not gated: this is how a device learns which side of the gate it is on (§8.1).
    get(ApiPaths.ENTITLEMENTS) {
        val account = call.account(accounts)
        call.respond(withContext(Dispatchers.IO) { entitlements.of(account.id) })
    }

    // What is on sale. The prices are the store's to state, on the device (§8.3).
    get(ApiPaths.PLANS) {
        call.respond(PlansResponse(settings.plans))
    }

    // Presenting a purchase again is how a restore is done, so both of these are as safe to
    // call twice as once: the store is asked again and the same row is written again.
    post(ApiPaths.GOOGLE_PURCHASE) {
        val account = call.account(accounts)
        val request = call.receive<GooglePurchaseRequest>()
        val store = google ?: throw notConfigured()
        call.respond(
            withContext(Dispatchers.IO) {
                val verdict = store.purchase(account.id, request.productId, request.purchaseToken)
                PurchaseResponse(entitlements.of(account.id), verdict)
            },
        )
    }

    post(ApiPaths.APPLE_PURCHASE) {
        val account = call.account(accounts)
        val request = call.receive<ApplePurchaseRequest>()
        val store = apple ?: throw notConfigured()
        call.respond(
            withContext(Dispatchers.IO) {
                val verdict = store.purchase(account.id, request.signedTransaction)
                PurchaseResponse(entitlements.of(account.id), verdict)
            },
        )
    }
}

const val GOOGLE_WEBHOOK = "/v1/webhooks/google"
const val APPLE_WEBHOOK = "/v1/webhooks/apple"

/**
 * Where the stores tell the server that something changed (§8.1). No device calls these and
 * no device token opens them: each proves who is speaking in the store's own way, before
 * anything is read.
 */
fun Route.storeWebhooks(google: GooglePlay?, apple: AppStore?) {
    // Answered 204 whenever the message was dealt with, including by having nothing to do:
    // any other answer makes the store send it again.
    post(GOOGLE_WEBHOOK) {
        val store = google ?: throw ApiException(HttpStatusCode.NotFound, ApiErrorCode.INVALID_REQUEST)
        val bearer = call.bearerToken()
        val body = call.receiveText()
        withContext(Dispatchers.IO) { store.notified(bearer, body) }
        call.respond(HttpStatusCode.NoContent)
    }

    post(APPLE_WEBHOOK) {
        val store = apple ?: throw ApiException(HttpStatusCode.NotFound, ApiErrorCode.INVALID_REQUEST)
        val body = call.receiveText()
        withContext(Dispatchers.IO) { store.notified(body) }
        call.respond(HttpStatusCode.NoContent)
    }
}
