package com.ptk.anatomypro.backend.subscriptions

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.util.X509CertChainUtils
import com.ptk.anatomypro.api.ApiErrorCode
import com.ptk.anatomypro.api.PurchaseVerdict
import com.ptk.anatomypro.backend.ApiException
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.slf4j.LoggerFactory
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.time.Instant
import java.util.Date
import java.util.UUID

/**
 * What makes the App Store ours: the app whose purchases count, the root certificates a
 * signature must lead back to (Apple Root CA - G3, given to the server as a file), and the
 * environments whose purchases are worth anything.
 *
 * [environments] is `Production` alone unless a deployment says otherwise, because a
 * sandbox purchase is signed by the same Apple and costs nothing. A server that App Review
 * will test against has to add `Sandbox`: reviewers buy in the sandbox.
 */
data class AppStoreConfig(
    val bundleId: String,
    val roots: List<X509Certificate>,
    val environments: Set<String> = setOf("Production"),
)

/**
 * The one call made to the App Store Server API, for the re-check (§8.1). The body comes
 * back as Apple sent it; what it says is believed only where Apple signed it, which this
 * server checks for itself.
 */
fun interface AppStoreServerApi {
    /** The body of `Get All Subscription Statuses`, or null when Apple knows no such transaction. May throw: "try again". */
    fun subscriptionStatuses(originalTransactionId: String): String?
}

/**
 * Reads what Apple signed, and nothing anyone else did (backend spec §8.1).
 *
 * A signed transaction or notification is a JWS carrying its own certificate chain. It is
 * believed only when all of these hold:
 *
 * - it is signed with ES256, by the key in the first certificate of that chain;
 * - the chain is a leaf, an intermediate and a root, and the first two validate (PKIX, at
 *   this server's present time) up to one of the roots this server was given — the root
 *   in the chain itself is not trusted for being there;
 * - the leaf and the intermediate carry the markers Apple gives the certificates that sign
 *   App Store receipts. Apple's root has issued a great many certificates for other
 *   things, some to anyone with a developer account, and those must sign nothing here.
 *
 * The chain is validated by the JDK and the signature by nimbus-jose-jwt; none of it is
 * written by hand. **Revocation is not checked**: there is no call to Apple's OCSP
 * responder, so a certificate Apple withdrew before its expiry would still be believed.
 */
class AppStoreSignatures(roots: List<X509Certificate>, private val now: () -> Instant) {
    private val anchors = roots.map { TrustAnchor(it, null) }.toSet()

    /** The payload of [jws], or null when it is not believed. */
    fun payloadOf(jws: String): JsonObject? {
        if (anchors.isEmpty() || jws.length > MAX_SIGNED_LENGTH) return null
        val signed = runCatching { JWSObject.parse(jws) }.getOrNull() ?: return null
        // Apple's algorithm and no other, least of all "none".
        if (signed.header.algorithm != JWSAlgorithm.ES256) return null
        val chain = runCatching { X509CertChainUtils.parse(signed.header.x509CertChain) }.getOrNull() ?: return null
        if (chain.size != 3) return null
        val (leaf, intermediate) = chain
        if (leaf.getExtensionValue(RECEIPT_SIGNING) == null || intermediate.getExtensionValue(WORLDWIDE_DEVELOPER_RELATIONS) == null) return null

        val path = CertificateFactory.getInstance("X.509").generateCertPath(listOf(leaf, intermediate))
        val rules = PKIXParameters(anchors).apply {
            isRevocationEnabled = false
            date = Date.from(now())
        }
        if (runCatching { CertPathValidator.getInstance("PKIX").validate(path, rules) }.isFailure) return null

        val key = leaf.publicKey as? ECPublicKey ?: return null
        if (!runCatching { signed.verify(ECDSAVerifier(key)) }.getOrDefault(false)) return null
        return runCatching { StoreJson.parseToJsonElement(signed.payload.toString()) as? JsonObject }.getOrNull()
    }

    private companion object {
        /** Marks a certificate that signs App Store receipts. */
        const val RECEIPT_SIGNING = "1.2.840.113635.100.6.11.1"

        /** Marks Apple's Worldwide Developer Relations intermediate. */
        const val WORLDWIDE_DEVELOPER_RELATIONS = "1.2.840.113635.100.6.2.1"

        /** A notification carries two signed payloads inside a third; none is near this. */
        const val MAX_SIGNED_LENGTH = 100_000
    }
}

/**
 * The App Store's half of §8.1. A subscription is known by its original transaction id,
 * which stays the same through every renewal; what is true of it is what the latest thing
 * Apple signed about it says.
 *
 * No signed transaction is ever logged.
 */
class AppStore(
    private val config: AppStoreConfig,
    /** Null where there is no App Store Server API key: purchases and notifications work, the re-check does not. */
    private val api: AppStoreServerApi?,
    private val subscriptions: Subscriptions,
    /** The product ids on sale. A purchase of anything else is not ours to honour. */
    private val products: Set<String>,
    private val now: () -> Instant,
) {
    private val signatures = AppStoreSignatures(config.roots, now)

    /** Whether there is a way to ask Apple about a subscription, rather than only to be told. */
    val canAsk: Boolean get() = api != null

    /** `POST /v1/purchases/apple`: [caller] presents the transaction StoreKit gave the app. */
    fun purchase(caller: UUID, signedTransaction: String): PurchaseVerdict {
        val statement = signatures.payloadOf(signedTransaction)?.let { statementOf(it, renewal = null) } ?: return PurchaseVerdict.INVALID
        return subscriptions.record(statement, caller, now())?.verdictFor(caller, now()) ?: PurchaseVerdict.INVALID
    }

    /**
     * `POST /v1/webhooks/apple`: a version 2 server notification.
     *
     * The notification's type is not what is acted on. The transaction inside it is: Apple
     * signs the subscription's state as of the notification — a renewal has a later expiry,
     * a refund has a revocation date — and that is written down like any other statement,
     * so one that arrives late or twice cannot undo something newer.
     *
     * @throws ApiException 401 when Apple did not sign it.
     */
    fun notified(body: String) {
        val signedPayload = runCatching { (StoreJson.parseToJsonElement(body) as? JsonObject)?.text("signedPayload") }.getOrNull()
            ?: throw ApiException(HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST)
        val notification = signatures.payloadOf(signedPayload) ?: throw ApiException(HttpStatusCode.Unauthorized, ApiErrorCode.UNAUTHENTICATED)
        val id = notification.text("notificationUUID") ?: throw ApiException(HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST)
        if (subscriptions.alreadyHeard(Store.APPLE, id)) return

        // A test notification, or one about the app rather than a purchase, carries no transaction.
        val data = notification["data"] as? JsonObject
        val ours = data != null && data.text("bundleId") == config.bundleId && data.text("environment") in config.environments
        // The transaction and the renewal state are signed separately from the notification
        // that carries them, and are believed separately.
        val statement = data?.takeIf { ours }?.text("signedTransactionInfo")?.let(signatures::payloadOf)?.let { transaction ->
            statementOf(transaction, renewal = data.text("signedRenewalInfo")?.let(signatures::payloadOf))
        }
        if (statement != null) subscriptions.record(statement, caller = null, now())
        subscriptions.heard(Store.APPLE, id, now())
        log.info("an App Store notification was acted on ({}, {})", notification.text("notificationType")?.take(40), if (statement == null) "nothing of ours" else "one subscription")
    }

    /**
     * Asks Apple what [originalTransactionId] has come to, and writes it down (§8.1's
     * re-check). The answer's own signatures are checked like any other.
     *
     * @return the subscription as it now stands, or null when Apple said nothing believable about it.
     */
    fun refresh(originalTransactionId: String): Subscription? {
        val ask = api ?: return null
        val body = try {
            ask.subscriptionStatuses(originalTransactionId)
        } catch (failure: Exception) {
            log.warn("the App Store Server API could not be asked ({})", failure.javaClass.simpleName)
            throw ApiException(HttpStatusCode.ServiceUnavailable, ApiErrorCode.UNKNOWN, "the store could not be asked")
        } ?: return null
        val last = runCatching { StoreJson.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?.list("data").orEmpty()
            .flatMap { group -> (group as? JsonObject)?.list("lastTransactions").orEmpty() }
            .filterIsInstance<JsonObject>()
            .firstOrNull { it.text("originalTransactionId") == originalTransactionId }
            ?: return null
        // Written down under the subscription the signed transaction itself names, whatever
        // the unsigned wrapping around it claimed it was about.
        val transaction = last.text("signedTransactionInfo")?.let(signatures::payloadOf) ?: return null
        val statement = statementOf(transaction, renewal = last.text("signedRenewalInfo")?.let(signatures::payloadOf)) ?: return null
        return subscriptions.record(statement, caller = null, now())
    }

    /**
     * What a transaction Apple signed says, or null when it is not a subscription of ours:
     * another app's, another environment's, or a product not on sale here.
     */
    private fun statementOf(transaction: JsonObject, renewal: JsonObject?): StoreStatement? {
        if (transaction.text("bundleId") != config.bundleId) return null
        if (transaction.text("environment") !in config.environments) return null
        if (transaction.text("type") != AUTO_RENEWABLE) return null
        val product = transaction.text("productId")?.takeIf { it in products } ?: return null
        val original = transaction.text("originalTransactionId")?.takeIf { it.isNotBlank() && it.length <= MAX_TOKEN_LENGTH } ?: return null
        val signed = transaction.instant("signedDate") ?: return null
        val expires = transaction.instant("expiresDate") ?: return null
        // Billing failed and Apple is still trying: where the app is set up to allow a grace
        // period, the subscriber keeps what they had until it ends.
        val graceUntil = renewal?.takeIf { it.text("originalTransactionId") == original }?.instant("gracePeriodExpiresDate")
        val present = now()
        return when {
            // Refunded, or taken back from a family member.
            transaction["revocationDate"].let { it != null && it !is JsonNull } -> StoreStatement(Store.APPLE, original, product, SubscriptionState.REVOKED, expires, signed)
            expires.isAfter(present) -> StoreStatement(Store.APPLE, original, product, SubscriptionState.ACTIVE, expires, signed)
            graceUntil != null && graceUntil.isAfter(present) -> StoreStatement(Store.APPLE, original, product, SubscriptionState.GRACE, graceUntil, signed)
            else -> StoreStatement(Store.APPLE, original, product, SubscriptionState.EXPIRED, expires, signed)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(AppStore::class.java)!!
        const val AUTO_RENEWABLE = "Auto-Renewable Subscription"
    }
}

private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.list(name: String): List<JsonElement>? = this[name] as? JsonArray

/** Apple's dates are milliseconds since 1970. */
private fun JsonObject.instant(name: String): Instant? = (this[name] as? JsonPrimitive)?.longOrNull?.let(Instant::ofEpochMilli)
