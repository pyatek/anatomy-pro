package com.ptk.anatomypro.backend.subscriptions

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.interfaces.ECPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date

// The two stores' APIs, over HTTPS.
//
// NEITHER CLASS IN THIS FILE HAS EVER SPOKEN TO GOOGLE OR TO APPLE. There is no Play
// Console account, no service account and no App Store Server API key yet (plan stage 7),
// so the paths, the way each request is signed and what each status is taken to mean are
// written from the stores' documentation as remembered, and are checked only against a
// server the test runs itself (`StoreHttpTest`) — which proves that this code does what it
// was meant to, and nothing about whether that is what the stores expect. The first call
// against a real store is the first real test of everything below.

/**
 * A store could not be asked. The message never names what it was asked about: the URL of
 * a Play request contains the purchase token.
 */
class StoreUnavailable(message: String) : Exception(message)

/** Short, because a purchase or a notification is waiting on the answer. */
private val TIMEOUT: Duration = Duration.ofSeconds(8)

private fun client(): HttpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()

private fun HttpClient.ask(request: HttpRequest.Builder): HttpResponse<String> = try {
    send(request.timeout(TIMEOUT).build(), HttpResponse.BodyHandlers.ofString())
} catch (failure: Exception) {
    if (failure is InterruptedException) Thread.currentThread().interrupt()
    // The type only, and no cause: an exception from the HTTP client may carry the URL.
    throw StoreUnavailable("the store did not answer (${failure.javaClass.simpleName})")
}

private fun segment(value: String): String = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

/** A PKCS#8 private key as a `.p8` file or a service account's JSON holds it. */
private fun privateKey(pem: String, algorithm: String): PrivateKey {
    val encoded = pem.lineSequence().filterNot { it.startsWith("-----") }.joinToString("").filterNot { it.isWhitespace() }
    return KeyFactory.getInstance(algorithm).generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded)))
}

/**
 * The Play Developer API with a service account's key (**never run against Google**; see
 * the top of this file).
 *
 * [serviceAccountJson] is the key file Google Cloud hands out for a service account, which
 * must have been given access to the app in the Play Console. An access token is asked
 * for with it and kept until it is about to run out.
 *
 * Taken to mean "Google knows no such purchase": 400, 404 and 410. Anything else that is
 * not a success — a refused credential, an outage — is [StoreUnavailable], so a purchase is
 * never called bad because this server is misconfigured.
 */
class GooglePlayHttp(
    serviceAccountJson: String,
    private val baseUrl: String = "https://androidpublisher.googleapis.com",
    private val now: () -> Instant = Instant::now,
) : PlayDeveloperApi {

    @Serializable
    private class ServiceAccount(
        @SerialName("client_email") val clientEmail: String,
        @SerialName("private_key") val privateKey: String,
        @SerialName("token_uri") val tokenUri: String = "https://oauth2.googleapis.com/token",
    )

    @Serializable
    private class AccessToken(@SerialName("access_token") val token: String, @SerialName("expires_in") val expiresIn: Long = 0)

    private val account = StoreJson.decodeFromString(ServiceAccount.serializer(), serviceAccountJson)
    private val key = privateKey(account.privateKey, "RSA")
    private val http = client()
    private var held: Pair<String, Instant>? = null

    override fun subscription(packageName: String, purchaseToken: String): String? {
        val url = "$baseUrl/androidpublisher/v3/applications/${segment(packageName)}/purchases/subscriptionsv2/tokens/${segment(purchaseToken)}"
        val response = http.ask(HttpRequest.newBuilder(URI(url)).header("Authorization", "Bearer ${accessToken()}").GET())
        return when (response.statusCode()) {
            200 -> response.body()
            400, 404, 410 -> null
            else -> throw StoreUnavailable("the Play Developer API answered ${response.statusCode()}")
        }
    }

    override fun acknowledge(packageName: String, productId: String, purchaseToken: String) {
        val url = "$baseUrl/androidpublisher/v3/applications/${segment(packageName)}/purchases/subscriptions/${segment(productId)}" +
            "/tokens/${segment(purchaseToken)}:acknowledge"
        val response = http.ask(
            HttpRequest.newBuilder(URI(url)).header("Authorization", "Bearer ${accessToken()}").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")),
        )
        if (response.statusCode() !in 200..299) throw StoreUnavailable("the Play Developer API answered ${response.statusCode()} to an acknowledgement")
    }

    /** OAuth's JWT bearer grant: a short-lived token signed with the service account's key, exchanged for an access token. */
    @Synchronized
    private fun accessToken(): String {
        held?.takeIf { (_, until) -> until.isAfter(now().plusSeconds(60)) }?.let { return it.first }
        val assertion = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build(),
            JWTClaimsSet.Builder().issuer(account.clientEmail).audience(account.tokenUri)
                .claim("scope", "https://www.googleapis.com/auth/androidpublisher")
                .issueTime(Date.from(now())).expirationTime(Date.from(now().plusSeconds(3_600)))
                .build(),
        ).apply { sign(RSASSASigner(key)) }.serialize()
        val form = "grant_type=${segment("urn:ietf:params:oauth:grant-type:jwt-bearer")}&assertion=${segment(assertion)}"
        val response = http.ask(
            HttpRequest.newBuilder(URI(account.tokenUri)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)),
        )
        if (response.statusCode() != 200) throw StoreUnavailable("Google refused the service account (${response.statusCode()})")
        val granted = runCatching { StoreJson.decodeFromString(AccessToken.serializer(), response.body()) }.getOrNull()
            ?: throw StoreUnavailable("Google's answer to the service account was not a token")
        held = granted.token to now().plusSeconds(granted.expiresIn)
        return granted.token
    }
}

/**
 * The App Store Server API with an in-app purchase key (**never run against Apple**; see
 * the top of this file).
 *
 * Each request carries a token signed with the `.p8` key from App Store Connect, naming
 * the key, the issuer and the app. Production is asked first; a transaction it does not
 * know is asked of the sandbox as well when [sandboxToo], since a sandbox purchase exists
 * only there.
 *
 * Taken to mean "Apple knows no such transaction": 400 and 404.
 */
class AppStoreHttp(
    privateKeyPem: String,
    private val keyId: String,
    private val issuerId: String,
    private val bundleId: String,
    private val sandboxToo: Boolean,
    private val production: String = "https://api.storekit.itunes.apple.com",
    private val sandbox: String = "https://api.storekit-sandbox.itunes.apple.com",
    private val now: () -> Instant = Instant::now,
) : AppStoreServerApi {

    private val key = privateKey(privateKeyPem, "EC") as ECPrivateKey
    private val http = client()

    override fun subscriptionStatuses(originalTransactionId: String): String? {
        for (host in listOfNotNull(production, sandbox.takeIf { sandboxToo })) {
            val url = "$host/inApps/v1/subscriptions/${segment(originalTransactionId)}"
            val response = http.ask(HttpRequest.newBuilder(URI(url)).header("Authorization", "Bearer ${token()}").GET())
            when (response.statusCode()) {
                200 -> return response.body()
                400, 404 -> continue
                else -> throw StoreUnavailable("the App Store Server API answered ${response.statusCode()}")
            }
        }
        return null
    }

    /** Good for five minutes; Apple accepts up to an hour, and there is no reason to hold one that long. */
    private fun token(): String = SignedJWT(
        JWSHeader.Builder(JWSAlgorithm.ES256).keyID(keyId).type(JOSEObjectType.JWT).build(),
        JWTClaimsSet.Builder().issuer(issuerId).audience("appstoreconnect-v1").claim("bid", bundleId)
            .issueTime(Date.from(now())).expirationTime(Date.from(now().plusSeconds(300)))
            .build(),
    ).apply { sign(ECDSASigner(key)) }.serialize()
}
