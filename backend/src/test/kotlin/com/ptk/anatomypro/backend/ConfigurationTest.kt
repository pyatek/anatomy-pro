package com.ptk.anatomypro.backend

import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.ptk.anatomypro.api.Plan
import com.ptk.anatomypro.api.PlanPeriod
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the environment makes of the server (backend spec §11.1), as far as subscriptions go. */
class ConfigurationTest {

    private val directory: File = Files.createTempDirectory("configuration-test").toFile().apply { deleteOnExit() }
    private val chain = TestChain(Instant.parse("2026-10-12T10:00:00Z"))

    private fun file(name: String, content: ByteArray) = File(directory, name).apply { writeBytes(content); deleteOnExit() }.path
    private fun pem(kind: String, encoded: ByteArray) =
        "-----BEGIN $kind-----\n${Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(encoded)}\n-----END $kind-----\n"

    private fun configured(vararg environment: Pair<String, String>) = configuration(environment.toMap()::get)

    @Test
    fun with_nothing_set_nobody_is_a_subscriber_nothing_is_on_sale_and_no_store_is_asked() {
        val bare = configured()

        assertFalse(bare.settings.everyoneIsSubscribed)
        assertEquals(emptyList(), bare.settings.plans)
        assertNull(bare.settings.google)
        assertNull(bare.settings.apple)
        assertNull(bare.play)
        assertNull(bare.appStoreApi)
        assertEquals(Duration.ofHours(6), bare.settings.recheckEvery)
    }

    @Test
    fun the_plans_are_product_ids_and_periods() {
        val settings = configured("PLANS" to "pro.monthly:monthly, pro.yearly:yearly").settings

        assertEquals(listOf(Plan("pro.monthly", PlanPeriod.MONTHLY), Plan("pro.yearly", PlanPeriod.YEARLY)), settings.plans)
        assertFailsWith<IllegalStateException> { configured("PLANS" to "pro.monthly:fortnightly") }
        assertFailsWith<IllegalStateException> { configured("PLANS" to "pro.monthly") }
    }

    @Test
    fun google_play_is_set_up_whole_or_the_server_does_not_start() {
        val key = RSAKeyGenerator(2048).generate()
        val account = """{"client_email":"purchases@anatomy-pro.iam.gserviceaccount.com","private_key":${
            kotlinx.serialization.json.JsonPrimitive(pem("PRIVATE KEY", key.toRSAPrivateKey().encoded))
        }}"""
        val whole = arrayOf(
            "GOOGLE_PLAY_PACKAGE" to "com.ptk.anatomypro",
            "GOOGLE_SERVICE_ACCOUNT_FILE" to file("play.json", account.toByteArray()),
            "GOOGLE_PUSH_AUDIENCE" to "https://api.example.org/v1/webhooks/google",
            "GOOGLE_PUSH_SERVICE_ACCOUNT" to "play-push@anatomy-pro.iam.gserviceaccount.com",
        )

        val google = configured(*whole)

        assertEquals("com.ptk.anatomypro", google.settings.google?.packageName)
        assertEquals("https://api.example.org/v1/webhooks/google", google.settings.google?.pushAudience)
        assertEquals("play-push@anatomy-pro.iam.gserviceaccount.com", google.settings.google?.pushServiceAccount)
        assertNotNull(google.play)
        for (missing in whole.drop(1)) {
            val failure = assertFailsWith<IllegalStateException> { configured(*whole.filter { it != missing }.toTypedArray()) }
            assertTrue(missing.first in failure.message.orEmpty(), "the missing value is not named: ${failure.message}")
        }
    }

    @Test
    fun the_app_store_needs_its_root_and_takes_production_purchases_only_unless_told() {
        val root = file("root.cer", chain.root.encoded)

        val production = configured("APPLE_BUNDLE_ID" to "com.ptk.anatomypro", "APPLE_ROOT_CA_FILE" to root)
        val reviewing = configured(
            "APPLE_BUNDLE_ID" to "com.ptk.anatomypro", "APPLE_ROOT_CA_FILE" to file("root.pem", pem("CERTIFICATE", chain.root.encoded).toByteArray()),
            "APPLE_ENVIRONMENTS" to "Production, Sandbox",
        )

        assertEquals(setOf("Production"), production.settings.apple?.environments)
        assertEquals(listOf(chain.root), production.settings.apple?.roots)
        assertNull(production.appStoreApi)
        assertEquals(setOf("Production", "Sandbox"), reviewing.settings.apple?.environments)
        assertEquals(listOf(chain.root), reviewing.settings.apple?.roots)
        assertTrue("APPLE_ROOT_CA_FILE" in assertFailsWith<IllegalStateException> { configured("APPLE_BUNDLE_ID" to "com.ptk.anatomypro") }.message.orEmpty())
        assertFailsWith<IllegalStateException> {
            configured("APPLE_BUNDLE_ID" to "com.ptk.anatomypro", "APPLE_ROOT_CA_FILE" to file("empty.cer", ByteArray(0)))
        }
    }

    @Test
    fun the_app_store_server_api_is_asked_only_when_its_key_is_given_whole() {
        val whole = arrayOf(
            "APPLE_BUNDLE_ID" to "com.ptk.anatomypro",
            "APPLE_ROOT_CA_FILE" to file("root.cer", chain.root.encoded),
            "APPLE_API_KEY_FILE" to file("AuthKey.p8", pem("PRIVATE KEY", TestChain.key().private.encoded).toByteArray()),
            "APPLE_API_KEY_ID" to "ABC123DEFG",
            "APPLE_API_ISSUER_ID" to "57246542-96fe-1a63-e053-0824d011072a",
        )

        assertNotNull(configured(*whole).appStoreApi)
        for (missing in whole.drop(3)) {
            val failure = assertFailsWith<IllegalStateException> { configured(*whole.filter { it != missing }.toTypedArray()) }
            assertTrue(missing.first in failure.message.orEmpty())
        }
    }

    @Test
    fun the_recheck_can_be_made_more_or_less_often_or_turned_off() {
        assertEquals(Duration.ofHours(1), configured("RECHECK_EVERY_HOURS" to "1").settings.recheckEvery)
        assertNull(configured("RECHECK_EVERY_HOURS" to "0").settings.recheckEvery)
    }
}
