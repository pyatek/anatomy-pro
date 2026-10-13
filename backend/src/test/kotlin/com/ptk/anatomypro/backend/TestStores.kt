package com.ptk.anatomypro.backend

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.ptk.anatomypro.api.Plan
import com.ptk.anatomypro.api.PlanPeriod
import com.ptk.anatomypro.backend.subscriptions.AppStoreConfig
import com.ptk.anatomypro.backend.subscriptions.AppStoreServerApi
import com.ptk.anatomypro.backend.subscriptions.GooglePlayConfig
import com.ptk.anatomypro.backend.subscriptions.PlayDeveloperApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID

/**
 * Stand-ins for the two stores. **Nothing here has ever been compared with a real answer
 * from Google or Apple**: the bodies are written to the shapes the stores document, by
 * hand, and the certificates are made here. What the tests over them prove is that the
 * server does what it should with a store that answers as documented.
 */
class TestStores(private val now: () -> Instant, private val pushKey: RSAKey) {
    val play = TestPlay()
    val apple = TestAppStore(now)

    val googleConfig = GooglePlayConfig(
        packageName = PACKAGE,
        pushAudience = PUSH_AUDIENCE,
        pushServiceAccount = PUSH_SENDER,
        keysUrl = TestIdentities.GOOGLE_KEYS,
    )

    /** The token Pub/Sub attaches to a push, so the receiver can tell it is Google's. */
    fun pushToken(
        email: String = PUSH_SENDER,
        audience: String = PUSH_AUDIENCE,
        key: RSAKey = pushKey,
        verified: Boolean = true,
        expires: Instant = now().plusSeconds(3600),
    ): String = SignedJWT(
        JWSHeader.Builder(JWSAlgorithm.RS256).keyID(pushKey.keyID).type(JOSEObjectType.JWT).build(),
        JWTClaimsSet.Builder().issuer("https://accounts.google.com").audience(audience).subject("1029384756")
            .claim("email", email).claim("email_verified", verified)
            .issueTime(Date.from(now().minusSeconds(5))).expirationTime(Date.from(expires))
            .build(),
    ).apply { sign(RSASSASigner(key)) }.serialize()

    /** A Pub/Sub push carrying a real-time developer notification, as Google documents it. */
    fun googleNotification(
        purchaseToken: String,
        messageId: String = UUID.randomUUID().toString(),
        type: Int = 2,
        packageName: String = PACKAGE,
        kind: String = "subscriptionNotification",
    ): String {
        val notification = buildJsonObject {
            put("version", "1.0")
            put("packageName", packageName)
            put("eventTimeMillis", now().toEpochMilli().toString())
            putJsonObject(kind) {
                put("version", "1.0")
                put("notificationType", type)
                put("purchaseToken", purchaseToken)
                put("subscriptionId", MONTHLY)
            }
        }
        return buildJsonObject {
            putJsonObject("message") {
                put("data", Base64.getEncoder().encodeToString(notification.toString().toByteArray()))
                put("messageId", messageId)
                put("message_id", messageId)
                put("publishTime", now().toString())
            }
            put("subscription", "projects/anatomy-pro/subscriptions/play-notifications")
        }.toString()
    }

    companion object {
        const val PACKAGE = "com.ptk.anatomypro"
        const val PUSH_AUDIENCE = "https://api.anatomypro.test/v1/webhooks/google"
        const val PUSH_SENDER = "play-push@anatomy-pro.iam.gserviceaccount.com"
        const val MONTHLY = "pro.monthly"
        const val YEARLY = "pro.yearly"
        val PLANS = listOf(Plan(MONTHLY, PlanPeriod.MONTHLY), Plan(YEARLY, PlanPeriod.YEARLY))
    }
}

/** The Play Developer API, as far as the server uses it: what it would say about each token. */
class TestPlay : PlayDeveloperApi {
    /** Purchase token → the body `purchases.subscriptionsv2.get` answers with. Absent: Google does not know it. */
    val purchases = mutableMapOf<String, String>()
    val acknowledged = mutableListOf<String>()
    var fetches = 0
    var down = false
    var refusesToAcknowledge = false

    override fun subscription(packageName: String, purchaseToken: String): String? {
        fetches++
        // The message names the token on purpose: the log test looks for it.
        if (down) throw java.io.IOException("cannot reach Play about $purchaseToken")
        check(packageName == TestStores.PACKAGE) { "asked about another app: $packageName" }
        return purchases[purchaseToken]
    }

    override fun acknowledge(packageName: String, productId: String, purchaseToken: String) {
        if (refusesToAcknowledge) throw java.io.IOException("cannot acknowledge $purchaseToken")
        acknowledged += purchaseToken
        purchases[purchaseToken] = purchases.getValue(purchaseToken).replace("ACKNOWLEDGEMENT_STATE_PENDING", "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED")
    }
}

/**
 * `SubscriptionPurchaseV2` bodies, in the shape Google documents. Written by hand from the
 * reference; not recorded from a real account, because there is none yet.
 */
object PlayAnswers {
    private fun body(
        state: String,
        product: String,
        expires: Instant?,
        acknowledged: Boolean = true,
        linked: String? = null,
        extra: JsonObject = JsonObject(emptyMap()),
    ): String = buildJsonObject {
        put("kind", "androidpublisher#subscriptionPurchaseV2")
        put("startTime", "2026-09-12T10:00:00.123Z")
        put("regionCode", "PL")
        put("subscriptionState", state)
        put("latestOrderId", "GPA.3312-4567-8901-23456")
        linked?.let { put("linkedPurchaseToken", it) }
        put("acknowledgementState", if (acknowledged) "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED" else "ACKNOWLEDGEMENT_STATE_PENDING")
        putJsonArray("lineItems") {
            add(
                buildJsonObject {
                    put("productId", product)
                    expires?.let { put("expiryTime", it.toString()) }
                    putJsonObject("autoRenewingPlan") { if (state == "SUBSCRIPTION_STATE_ACTIVE") put("autoRenewEnabled", true) }
                    putJsonObject("offerDetails") { put("basePlanId", "monthly") }
                },
            )
        }
        for ((name, value) in extra) put(name, value)
    }.toString()

    fun active(expires: Instant, product: String = TestStores.MONTHLY, acknowledged: Boolean = true, linked: String? = null) =
        body("SUBSCRIPTION_STATE_ACTIVE", product, expires, acknowledged, linked)

    /** Cancelled by the user, and paid for until [expires]. */
    fun cancelled(expires: Instant, product: String = TestStores.MONTHLY) = body(
        "SUBSCRIPTION_STATE_CANCELED", product, expires,
        extra = buildJsonObject { putJsonObject("canceledStateContext") { putJsonObject("userInitiatedCancellation") { put("cancelTime", "2026-10-01T08:00:00Z") } } },
    )

    fun expired(expired: Instant, product: String = TestStores.MONTHLY) = body("SUBSCRIPTION_STATE_EXPIRED", product, expired)
    fun pending(product: String = TestStores.MONTHLY) = body("SUBSCRIPTION_STATE_PENDING", product, expires = null, acknowledged = false)
    fun inGrace(expires: Instant, product: String = TestStores.MONTHLY) = body("SUBSCRIPTION_STATE_IN_GRACE_PERIOD", product, expires)
    fun onHold(expired: Instant, product: String = TestStores.MONTHLY) = body("SUBSCRIPTION_STATE_ON_HOLD", product, expired)
    fun inAStateNotHeardOf(expires: Instant) = body("SUBSCRIPTION_STATE_SOMETHING_NEW", TestStores.MONTHLY, expires)
}

/** A certificate authority of the test's own, shaped like Apple's: a root, an intermediate, a leaf that signs. */
class TestChain(
    now: Instant,
    leafMarker: String? = LEAF_MARKER,
    intermediateMarker: String? = INTERMEDIATE_MARKER,
    intermediateIsAuthority: Boolean = true,
    leafExpires: Instant = now.plusSeconds(365 * DAY),
) {
    val rootKey: KeyPair = key()
    val intermediateKey: KeyPair = key()
    val leafKey: KeyPair = key()
    val root: X509Certificate = certificate("Test Root CA", rootKey, "Test Root CA", rootKey, authority = true, marker = null, now.minusSeconds(DAY), now.plusSeconds(3650 * DAY))
    val intermediate: X509Certificate =
        certificate("Test WWDR", intermediateKey, "Test Root CA", rootKey, intermediateIsAuthority, intermediateMarker, now.minusSeconds(DAY), now.plusSeconds(1000 * DAY))
    val leaf: X509Certificate =
        certificate("Test Receipt Signing", leafKey, "Test WWDR", intermediateKey, authority = false, leafMarker, now.minusSeconds(DAY), leafExpires)

    /** A JWS as the App Store signs them: ES256, with the chain in the header. */
    fun sign(payload: JsonObject, certificates: List<X509Certificate> = listOf(leaf, intermediate, root), with: KeyPair = leafKey): String =
        JWSObject(
            JWSHeader.Builder(JWSAlgorithm.ES256).x509CertChain(certificates.map { com.nimbusds.jose.util.Base64.encode(it.encoded) }).build(),
            Payload(payload.toString()),
        ).apply { sign(ECDSASigner(with.private as ECPrivateKey)) }.serialize()

    companion object {
        const val DAY = 86_400L
        const val LEAF_MARKER = "1.2.840.113635.100.6.11.1"
        const val INTERMEDIATE_MARKER = "1.2.840.113635.100.6.2.1"
        private val random = SecureRandom()

        fun key(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

        fun certificate(
            subject: String, key: KeyPair, issuer: String, issuerKey: KeyPair, authority: Boolean, marker: String?, from: Instant, until: Instant,
        ): X509Certificate {
            val builder = JcaX509v3CertificateBuilder(
                X500Name("CN=$issuer"), BigInteger(64, random), Date.from(from), Date.from(until), X500Name("CN=$subject"), key.public,
            )
            builder.addExtension(Extension.basicConstraints, true, BasicConstraints(authority))
            marker?.let { builder.addExtension(ASN1ObjectIdentifier(it), false, DERNull.INSTANCE) }
            return JcaX509CertificateConverter().getCertificate(builder.build(JcaContentSignerBuilder("SHA256withECDSA").build(issuerKey.private)))
        }
    }
}

/**
 * The App Store: signed transactions, server notifications and the Server API's answer,
 * signed by a chain made here. Field names follow Apple's `JWSTransactionDecodedPayload`,
 * `JWSRenewalInfoDecodedPayload` and `responseBodyV2DecodedPayload`; none was recorded.
 */
class TestAppStore(private val now: () -> Instant) : AppStoreServerApi {
    val chain = TestChain(now())
    val config = AppStoreConfig(bundleId = BUNDLE, roots = listOf(chain.root), environments = setOf("Production"))

    /** Original transaction id → what `Get All Subscription Statuses` answers. Absent: Apple does not know it. */
    val statuses = mutableMapOf<String, String>()
    var fetches = 0
    var down = false

    override fun subscriptionStatuses(originalTransactionId: String): String? {
        fetches++
        if (down) throw java.io.IOException("cannot reach the App Store about $originalTransactionId")
        return statuses[originalTransactionId]
    }

    fun transactionPayload(
        original: String,
        expires: Instant,
        product: String = TestStores.MONTHLY,
        revoked: Instant? = null,
        signed: Instant = now(),
        bundle: String = BUNDLE,
        environment: String = "Production",
        type: String = "Auto-Renewable Subscription",
    ): JsonObject = buildJsonObject {
        put("transactionId", "${original}0${signed.epochSecond % 1000}")
        put("originalTransactionId", original)
        put("webOrderLineItemId", "2000000000000001")
        put("bundleId", bundle)
        put("productId", product)
        put("subscriptionGroupIdentifier", "21001234")
        put("purchaseDate", expires.minusSeconds(30 * TestChain.DAY).toEpochMilli())
        put("originalPurchaseDate", 1_757_671_200_000)
        put("expiresDate", expires.toEpochMilli())
        put("quantity", 1)
        put("type", type)
        put("inAppOwnershipType", "PURCHASED")
        put("signedDate", signed.toEpochMilli())
        put("environment", environment)
        put("transactionReason", "PURCHASE")
        put("storefront", "POL")
        put("price", 19990)
        put("currency", "PLN")
        revoked?.let {
            put("revocationDate", it.toEpochMilli())
            put("revocationReason", 0)
        }
    }

    /** What StoreKit 2 hands the app after a purchase, and the app hands the server. */
    fun transaction(
        original: String,
        expires: Instant,
        product: String = TestStores.MONTHLY,
        revoked: Instant? = null,
        signed: Instant = now(),
        bundle: String = BUNDLE,
        environment: String = "Production",
        type: String = "Auto-Renewable Subscription",
        signer: TestChain = chain,
    ): String = signer.sign(transactionPayload(original, expires, product, revoked, signed, bundle, environment, type))

    fun renewalInfo(original: String, graceUntil: Instant? = null, product: String = TestStores.MONTHLY): String = chain.sign(
        buildJsonObject {
            put("originalTransactionId", original)
            put("autoRenewProductId", product)
            put("productId", product)
            put("autoRenewStatus", 1)
            put("signedDate", now().toEpochMilli())
            put("environment", "Production")
            graceUntil?.let {
                put("isInBillingRetryPeriod", true)
                put("gracePeriodExpiresDate", it.toEpochMilli())
            }
        },
    )

    /** The body of a version 2 server notification. */
    fun notification(
        type: String,
        transaction: String?,
        subtype: String? = null,
        uuid: String = UUID.randomUUID().toString(),
        renewal: String? = null,
        bundle: String = BUNDLE,
        environment: String = "Production",
        signer: TestChain = chain,
    ): String {
        val payload = buildJsonObject {
            put("notificationType", type)
            subtype?.let { put("subtype", it) }
            put("notificationUUID", uuid)
            put("version", "2.0")
            put("signedDate", now().toEpochMilli())
            if (transaction != null) {
                putJsonObject("data") {
                    put("appAppleId", 6_700_000_001)
                    put("bundleId", bundle)
                    put("bundleVersion", "12")
                    put("environment", environment)
                    put("signedTransactionInfo", transaction)
                    renewal?.let { put("signedRenewalInfo", it) }
                    put("status", 1)
                }
            }
        }
        return JsonObject(mapOf("signedPayload" to JsonPrimitive(signer.sign(payload)))).toString()
    }

    /** What the Server API says of [original] now: its latest transaction, and its renewal state. */
    fun says(original: String, transaction: String, renewal: String? = null) {
        statuses[original] = buildJsonObject {
            put("environment", "Production")
            put("bundleId", BUNDLE)
            put("appAppleId", 6_700_000_001)
            put(
                "data",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("subscriptionGroupIdentifier", "21001234")
                            putJsonArray("lastTransactions") {
                                add(
                                    buildJsonObject {
                                        put("originalTransactionId", original)
                                        put("status", 1)
                                        put("signedTransactionInfo", transaction)
                                        renewal?.let { put("signedRenewalInfo", it) }
                                    },
                                )
                            }
                        },
                    ),
                ),
            )
        }.toString()
    }

    companion object {
        const val BUNDLE = "com.ptk.anatomypro"
    }
}
