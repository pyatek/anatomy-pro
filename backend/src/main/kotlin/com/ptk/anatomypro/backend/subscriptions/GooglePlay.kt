package com.ptk.anatomypro.backend.subscriptions

import com.ptk.anatomypro.api.ApiErrorCode
import com.ptk.anatomypro.api.PurchaseVerdict
import com.ptk.anatomypro.backend.ApiException
import com.ptk.anatomypro.backend.accounts.IdentityVerifier
import com.ptk.anatomypro.backend.accounts.ProviderConfig
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * What makes Google Play ours: the app whose purchases are asked about, and what a push
 * must carry to be believed — the audience we told Pub/Sub to make its token out to, and
 * the service account we told it to sign as.
 */
data class GooglePlayConfig(
    val packageName: String,
    val pushAudience: String,
    val pushServiceAccount: String,
    /** Where Google publishes the keys it signs push tokens with: the same ones it signs sign-ins with. */
    val keysUrl: String = "https://www.googleapis.com/oauth2/v3/certs",
)

/**
 * The two calls made to the Play Developer API. Bodies come back as Google sent them, so
 * that reading them is this server's code and is tested, whatever fetched them.
 *
 * Either call may throw when Google cannot be reached or refuses us; that is "try again",
 * never "the purchase is bad".
 */
interface PlayDeveloperApi {
    /** The body of `purchases.subscriptionsv2.get`, or null when Google knows no such purchase. */
    fun subscription(packageName: String, purchaseToken: String): String?

    /** `purchases.subscriptions.acknowledge`. Google refunds a purchase nobody acknowledges within three days. */
    fun acknowledge(packageName: String, productId: String, purchaseToken: String)
}

/**
 * Google Play's half of §8.1. A purchase token is worth what the Play Developer API says of
 * it at that moment and nothing else: the device's word, and a notification's, are only
 * reasons to ask.
 *
 * No purchase token is ever logged, and nothing a store client throws is logged beyond its
 * type, because its message may name the token it was asked about.
 */
class GooglePlay(
    private val config: GooglePlayConfig,
    private val api: PlayDeveloperApi,
    private val subscriptions: Subscriptions,
    /** The product ids on sale. A purchase of anything else is not ours to honour. */
    private val products: Set<String>,
    fetchKeys: (url: String) -> String,
    private val now: () -> Instant,
) {
    private val pushTokens = IdentityVerifier(emptyMap(), fetchKeys, now)
    private val pushIssuer = ProviderConfig(setOf("https://accounts.google.com", "accounts.google.com"), setOf(config.pushAudience), config.keysUrl)

    /** `POST /v1/purchases/google`: [caller] presents a purchase. */
    fun purchase(caller: UUID, productId: String, purchaseToken: String): PurchaseVerdict {
        // Not ours, or not a token at all: Google is not troubled with it.
        if (productId !in products || purchaseToken.isBlank() || purchaseToken.length > MAX_TOKEN_LENGTH) return PurchaseVerdict.INVALID
        return refresh(purchaseToken, caller, productId)?.verdictFor(caller, now()) ?: PurchaseVerdict.INVALID
    }

    /**
     * Asks Google about [purchaseToken] and writes down the answer. With [wanted], the
     * purchase must be of that product.
     *
     * @return the subscription as it now stands, or null when it is worth nothing to anyone.
     */
    fun refresh(purchaseToken: String, caller: UUID? = null, wanted: String? = null): Subscription? {
        val body = try {
            api.subscription(config.packageName, purchaseToken)
        } catch (failure: Exception) {
            log.warn("the Play Developer API could not be asked ({})", failure.javaClass.simpleName)
            throw ApiException(HttpStatusCode.ServiceUnavailable, ApiErrorCode.UNKNOWN, "the store could not be asked")
        } ?: return null
        val purchase = runCatching { StoreJson.decodeFromString(PlaySubscription.serializer(), body) }.getOrNull() ?: return null

        // The line that is ours: the product asked about, or else whichever of our products
        // runs longest (a purchase heard of through a notification names none).
        val line = purchase.lineItems
            .filter { if (wanted != null) it.productId == wanted else it.productId in products }
            .maxByOrNull { it.expiry() ?: Instant.MIN }
            ?: return null
        val statement = StoreStatement(
            store = Store.GOOGLE,
            storeId = purchaseToken,
            productId = line.productId,
            state = stateOf(purchase.subscriptionState),
            expiresAt = line.expiry(),
            statedAt = now(),
            supersedes = purchase.linkedPurchaseToken?.takeIf { it.isNotBlank() && it != purchaseToken },
        )
        val held = subscriptions.record(statement, caller, now())

        // Acknowledged only once it is somebody's and lets them in: acknowledging is telling
        // Google the buyer got what they paid for. Not having managed to is not the buyer's
        // problem; it is tried again the next time this purchase is asked about.
        if (purchase.acknowledgementState == NOT_ACKNOWLEDGED && held?.account != null && held.entitles(now())) {
            try {
                api.acknowledge(config.packageName, line.productId, purchaseToken)
            } catch (failure: Exception) {
                log.warn("a Play purchase could not be acknowledged ({})", failure.javaClass.simpleName)
            }
        }
        return held
    }

    /**
     * `POST /v1/webhooks/google`: a real-time developer notification, pushed by Pub/Sub.
     *
     * What the notification says happened is not believed, or even read: it names a
     * purchase, and Google is asked about that purchase. So a notification can only ever
     * make this server's picture truer, and one delivered twice asks nothing the second time.
     *
     * @throws ApiException 401 when the push does not carry Google's token made out to us.
     */
    fun notified(bearer: String?, body: String) {
        val claims = try {
            pushTokens.claimsOf(pushIssuer, bearer ?: throw unauthenticated())
        } catch (refusal: ApiException) {
            // No keys to check against is Google's side or the network's: Pub/Sub tries again.
            throw if (refusal.status == HttpStatusCode.ServiceUnavailable) refusal else unauthenticated()
        }
        // Any Google service account can have a token made out to our audience. Only the
        // one we gave the subscription is Pub/Sub speaking for us.
        val ours = runCatching { claims.getStringClaim("email") == config.pushServiceAccount && claims.getBooleanClaim("email_verified") == true }
        if (!ours.getOrDefault(false)) throw unauthenticated()

        val message = runCatching { StoreJson.decodeFromString(PushEnvelope.serializer(), body).message }.getOrNull()
            ?: throw ApiException(HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST)
        if (message.messageId.isBlank()) throw ApiException(HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST)
        if (subscriptions.alreadyHeard(Store.GOOGLE, message.messageId)) return

        val notification = runCatching {
            StoreJson.decodeFromString(DeveloperNotification.serializer(), String(Base64.getDecoder().decode(message.data)))
        }.getOrNull()
        // A purchase, renewal, cancellation, expiry or the like — or a refund Google made.
        // Anything else (a test message, a one-time product) names nothing of ours.
        val purchaseToken = notification?.takeIf { it.packageName == config.packageName }
            ?.let { it.subscriptionNotification ?: it.voidedPurchaseNotification }
            ?.purchaseToken?.takeIf { it.isNotBlank() && it.length <= MAX_TOKEN_LENGTH }
        if (purchaseToken != null) refresh(purchaseToken)
        subscriptions.heard(Store.GOOGLE, message.messageId, now())
        log.info("a Play notification was acted on ({})", if (purchaseToken == null) "nothing of ours" else "one purchase asked about")
    }

    private fun stateOf(name: String): SubscriptionState = when (name) {
        "SUBSCRIPTION_STATE_ACTIVE" -> SubscriptionState.ACTIVE
        "SUBSCRIPTION_STATE_CANCELED" -> SubscriptionState.CANCELLED
        "SUBSCRIPTION_STATE_IN_GRACE_PERIOD" -> SubscriptionState.GRACE
        "SUBSCRIPTION_STATE_PENDING" -> SubscriptionState.PENDING
        "SUBSCRIPTION_STATE_ON_HOLD" -> SubscriptionState.ON_HOLD
        "SUBSCRIPTION_STATE_PAUSED" -> SubscriptionState.PAUSED
        "SUBSCRIPTION_STATE_EXPIRED", "SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED" -> SubscriptionState.EXPIRED
        else -> {
            // A state Google added after this was written. Shut is the safe way to be wrong,
            // and loud is the way to find out.
            log.warn("Play reported a subscription state this server does not know: {}", name.take(64))
            SubscriptionState.EXPIRED
        }
    }

    private fun unauthenticated() = ApiException(HttpStatusCode.Unauthorized, ApiErrorCode.UNAUTHENTICATED)

    private companion object {
        val log = LoggerFactory.getLogger(GooglePlay::class.java)!!
        const val NOT_ACKNOWLEDGED = "ACKNOWLEDGEMENT_STATE_PENDING"
    }
}

/** Longer than any store's token or signed transaction, and short enough to refuse a flood. */
internal const val MAX_TOKEN_LENGTH = 2_000

/** For what the stores send: fields they add later are ignored, and ones they leave out are empty. */
internal val StoreJson = Json { ignoreUnknownKeys = true }

// `SubscriptionPurchaseV2`, as much of it as is read.
@Serializable
private class PlaySubscription(
    val subscriptionState: String = "",
    val lineItems: List<PlayLineItem> = emptyList(),
    val linkedPurchaseToken: String? = null,
    val acknowledgementState: String = "",
)

@Serializable
private class PlayLineItem(val productId: String = "", val expiryTime: String? = null) {
    fun expiry(): Instant? = expiryTime?.let { runCatching { Instant.parse(it) }.getOrNull() }
}

// A Pub/Sub push, and the `DeveloperNotification` inside its `data`.
@Serializable
private class PushEnvelope(val message: PushMessage? = null)

@Serializable
private class PushMessage(val data: String = "", val messageId: String = "")

@Serializable
private class DeveloperNotification(
    val packageName: String = "",
    val subscriptionNotification: NamedPurchase? = null,
    val voidedPurchaseNotification: NamedPurchase? = null,
)

@Serializable
private class NamedPurchase(val purchaseToken: String = "")
