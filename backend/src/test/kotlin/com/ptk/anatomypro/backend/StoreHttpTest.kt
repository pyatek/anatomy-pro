package com.ptk.anatomypro.backend

import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.SignedJWT
import com.ptk.anatomypro.backend.subscriptions.AppStoreHttp
import com.ptk.anatomypro.backend.subscriptions.GooglePlayHttp
import com.ptk.anatomypro.backend.subscriptions.StoreUnavailable
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.security.interfaces.ECPublicKey
import java.time.Instant
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The HTTP clients for the two stores, against a server this test runs on the loopback
 * address. **This is not Google and not Apple.** It answers what the test tells it to, so
 * what is proven is that the clients send what they were written to send and read what they
 * were written to read — not that the stores agree with either.
 */
class StoreHttpTest {

    private class Asked(val method: String, val path: String, val authorization: String?, val body: String)

    private val asked = mutableListOf<Asked>()
    private var answer: (Asked) -> Pair<Int, String> = { 404 to "" }
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange: HttpExchange ->
            val request = Asked(
                exchange.requestMethod, exchange.requestURI.rawPath, exchange.requestHeaders.getFirst("Authorization"),
                exchange.requestBody.readBytes().decodeToString(),
            )
            asked += request
            val (status, body) = answer(request)
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        start()
    }
    private val here = "http://127.0.0.1:${server.address.port}"
    private val now = Instant.parse("2026-10-12T10:00:00Z")

    @AfterTest
    fun stop() = server.stop(0)

    private fun pem(kind: String, encoded: ByteArray) =
        "-----BEGIN $kind-----\n${Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(encoded)}\n-----END $kind-----\n"

    // --- Play ---------------------------------------------------------------------------

    private val serviceKey = RSAKeyGenerator(2048).generate()
    private val serviceAccount = buildJsonObject {
        put("type", "service_account")
        put("client_email", "purchases@anatomy-pro.iam.gserviceaccount.com")
        put("private_key", pem("PRIVATE KEY", serviceKey.toRSAPrivateKey().encoded))
        put("token_uri", "$here/token")
    }.toString()

    private fun play(clock: () -> Instant = { now }) = GooglePlayHttp(serviceAccount, baseUrl = here, now = clock)

    private fun playAnswers(purchase: Pair<Int, String>): (Asked) -> Pair<Int, String> = { request ->
        if (request.path == "/token") 200 to """{"access_token":"ya29.granted","expires_in":3600,"token_type":"Bearer"}""" else purchase
    }

    @Test
    fun the_play_client_exchanges_a_token_signed_by_the_service_account_and_asks_with_what_it_is_given() {
        answer = playAnswers(200 to """{"subscriptionState":"SUBSCRIPTION_STATE_ACTIVE"}""")

        val body = play().subscription("com.ptk.anatomypro", "tok/with spaces+and.dots")

        assertEquals("""{"subscriptionState":"SUBSCRIPTION_STATE_ACTIVE"}""", body)
        val (exchange, fetch) = asked
        assertEquals("POST" to "/token", exchange.method to exchange.path)
        val form = exchange.body.split('&').associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8) }
        assertEquals("urn:ietf:params:oauth:grant-type:jwt-bearer", form["grant_type"])
        val assertion = SignedJWT.parse(form.getValue("assertion"))
        assertTrue(assertion.verify(RSASSAVerifier(serviceKey.toRSAPublicKey())))
        assertEquals("purchases@anatomy-pro.iam.gserviceaccount.com", assertion.jwtClaimsSet.issuer)
        assertEquals(listOf("$here/token"), assertion.jwtClaimsSet.audience)
        assertEquals("https://www.googleapis.com/auth/androidpublisher", assertion.jwtClaimsSet.getStringClaim("scope"))
        assertEquals(now.plusSeconds(3_600), assertion.jwtClaimsSet.expirationTime.toInstant())

        assertEquals("GET", fetch.method)
        assertEquals("/androidpublisher/v3/applications/com.ptk.anatomypro/purchases/subscriptionsv2/tokens/tok%2Fwith%20spaces%2Band.dots", fetch.path)
        assertEquals("Bearer ya29.granted", fetch.authorization)
    }

    @Test
    fun the_access_token_is_kept_until_it_is_about_to_run_out() {
        answer = playAnswers(200 to "{}")
        var clock = now
        val play = play { clock }

        play.subscription("com.ptk.anatomypro", "tok-1")
        play.subscription("com.ptk.anatomypro", "tok-2")
        assertEquals(1, asked.count { it.path == "/token" })

        clock = now.plusSeconds(3_590)
        play.subscription("com.ptk.anatomypro", "tok-3")
        assertEquals(2, asked.count { it.path == "/token" })
    }

    @Test
    fun a_purchase_google_does_not_know_is_null_and_an_outage_or_a_refusal_is_not() {
        val play = play()
        for (unknown in listOf(400, 404, 410)) {
            answer = playAnswers(unknown to """{"error":{"code":$unknown}}""")
            assertNull(play.subscription("com.ptk.anatomypro", "tok-1"), "$unknown")
        }
        for (trouble in listOf(401, 403, 429, 500, 503)) {
            answer = playAnswers(trouble to "")
            val failure = assertFailsWith<StoreUnavailable>("$trouble") { play.subscription("com.ptk.anatomypro", "secret-token") }
            assertFalse("secret-token" in failure.message.orEmpty() || failure.cause != null)
        }
    }

    @Test
    fun a_service_account_google_refuses_or_a_server_that_is_not_there_is_an_outage_that_names_no_token() {
        answer = { 401 to """{"error":"invalid_grant"}""" }
        assertFailsWith<StoreUnavailable> { play().subscription("com.ptk.anatomypro", "secret-token") }
        assertEquals(listOf("/token"), asked.map { it.path })

        server.stop(0)
        val failure = assertFailsWith<StoreUnavailable> { play().subscription("com.ptk.anatomypro", "secret-token") }
        assertFalse("secret-token" in failure.message.orEmpty() || failure.cause != null)
    }

    @Test
    fun an_acknowledgement_is_posted_for_the_product_and_the_token() {
        answer = playAnswers(204 to "")

        play().acknowledge("com.ptk.anatomypro", "pro.monthly", "tok-1")

        val sent = asked.last()
        assertEquals("POST", sent.method)
        assertEquals("/androidpublisher/v3/applications/com.ptk.anatomypro/purchases/subscriptions/pro.monthly/tokens/tok-1:acknowledge", sent.path)
        assertEquals("Bearer ya29.granted", sent.authorization)

        answer = playAnswers(500 to "")
        assertFailsWith<StoreUnavailable> { play().acknowledge("com.ptk.anatomypro", "pro.monthly", "tok-1") }
    }

    // --- the App Store ------------------------------------------------------------------

    private val apiKey = TestChain.key()

    private fun appStore(sandboxToo: Boolean) = AppStoreHttp(
        pem("PRIVATE KEY", apiKey.private.encoded), keyId = "ABC123DEFG", issuerId = "57246542-96fe-1a63-e053-0824d011072a",
        bundleId = "com.ptk.anatomypro", sandboxToo = sandboxToo, production = "$here/production", sandbox = "$here/sandbox", now = { now },
    )

    @Test
    fun the_app_store_client_signs_each_request_with_the_key_it_was_given() {
        answer = { 200 to """{"data":[]}""" }

        val body = appStore(sandboxToo = false).subscriptionStatuses("1000000000000001")

        assertEquals("""{"data":[]}""", body)
        val sent = asked.single()
        assertEquals("GET" to "/production/inApps/v1/subscriptions/1000000000000001", sent.method to sent.path)
        val token = SignedJWT.parse(sent.authorization!!.removePrefix("Bearer "))
        assertTrue(token.verify(ECDSAVerifier(apiKey.public as ECPublicKey)))
        assertEquals("ABC123DEFG", token.header.keyID)
        assertEquals("57246542-96fe-1a63-e053-0824d011072a", token.jwtClaimsSet.issuer)
        assertEquals(listOf("appstoreconnect-v1"), token.jwtClaimsSet.audience)
        assertEquals("com.ptk.anatomypro", token.jwtClaimsSet.getStringClaim("bid"))
        assertEquals(now.plusSeconds(300), token.jwtClaimsSet.expirationTime.toInstant())
    }

    @Test
    fun a_transaction_production_does_not_know_is_asked_of_the_sandbox_only_when_the_sandbox_counts() {
        answer = { request -> if (request.path.startsWith("/sandbox")) 200 to """{"environment":"Sandbox"}""" else 404 to """{"errorCode":4040010}""" }

        assertNull(appStore(sandboxToo = false).subscriptionStatuses("2000000000000001"))
        assertEquals(1, asked.size)

        assertEquals("""{"environment":"Sandbox"}""", appStore(sandboxToo = true).subscriptionStatuses("2000000000000001"))
        assertEquals("/sandbox/inApps/v1/subscriptions/2000000000000001", asked.last().path)
    }

    @Test
    fun an_outage_or_a_refused_key_at_the_app_store_is_not_an_answer() {
        for (trouble in listOf(401, 429, 500)) {
            answer = { trouble to "" }
            assertFailsWith<StoreUnavailable>("$trouble") { appStore(sandboxToo = true).subscriptionStatuses("1000000000000001") }
        }
    }
}
