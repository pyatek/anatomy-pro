package com.ptk.anatomypro.backend

import com.ptk.anatomypro.api.IdentityProvider
import com.ptk.anatomypro.api.Plan
import com.ptk.anatomypro.api.PlanPeriod
import com.ptk.anatomypro.backend.accounts.ProviderConfig
import com.ptk.anatomypro.backend.content.Content
import com.ptk.anatomypro.backend.subscriptions.AppStoreConfig
import com.ptk.anatomypro.backend.subscriptions.AppStoreHttp
import com.ptk.anatomypro.backend.subscriptions.AppStoreServerApi
import com.ptk.anatomypro.backend.subscriptions.GooglePlayConfig
import com.ptk.anatomypro.backend.subscriptions.GooglePlayHttp
import com.ptk.anatomypro.backend.subscriptions.PlayDeveloperApi
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Duration

/**
 * Configuration is the environment's, from a file on the server that is not in the
 * repository (backend spec §11.1). A missing value stops the server at start, by name.
 */
fun main(args: Array<String>) {
    fun env(name: String): String = System.getenv(name) ?: error("$name is not set")

    val database = Database.open(
        DatabaseConfig(
            jdbcUrl = env("DATABASE_URL"),
            user = env("DATABASE_USER"),
            password = env("DATABASE_PASSWORD"),
        ),
    )
    // `backend ingest …` and `backend verify …` do their work and end.
    contentCommand(args.toList(), Content(database))?.let { done ->
        println(done)
        return
    }

    val configured = configuration(System::getenv)
    configured.settings.apple?.roots?.forEach { root ->
        // Printed so that it can be compared, by eye, with the fingerprint Apple publishes
        // for its root: everything the App Store is believed about rests on this file.
        println("trusting App Store root ${root.subjectX500Principal.name}, SHA-256 ${fingerprint(root)}")
    }

    embeddedServer(Netty, port = (System.getenv("PORT") ?: "8080").toInt()) {
        backend(database, configured.settings, play = configured.play, appStoreApi = configured.appStoreApi)
    }.start(wait = true)
}

/** What the environment asks for: the settings, and the stores' APIs where there are keys to call them with. */
class Configured(val settings: Settings, val play: PlayDeveloperApi?, val appStoreApi: AppStoreServerApi?)

/**
 * Reads the server's configuration from [env]. A store is set up whole or not at all: one
 * named without the rest of what it needs stops the server at start, by the name of what
 * is missing, rather than starting a server that would call real purchases bad.
 */
fun configuration(env: (String) -> String?): Configured {
    fun need(name: String, why: String): String = env(name)?.takeIf { it.isNotBlank() } ?: error("$name is not set, and $why needs it")
    fun list(name: String): List<String> = env(name).orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }

    val blocklist = object {}.javaClass.getResourceAsStream("/nickname-blocklist.txt")!!
        .bufferedReader().readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .toSet()

    // `pro.monthly:monthly,pro.yearly:yearly`: what is on sale, the same ids in both stores (§8.3).
    val plans = list("PLANS").map { entry ->
        val period = PlanPeriod.entries.firstOrNull { it.name.equals(entry.substringAfter(':', ""), ignoreCase = true) }
            ?: error("PLANS: '$entry' is not a product id, a colon, and monthly or yearly")
        Plan(entry.substringBefore(':').trim(), period)
    }

    val google = env("GOOGLE_PLAY_PACKAGE")?.takeIf { it.isNotBlank() }?.let { packageName ->
        GooglePlayConfig(
            packageName = packageName,
            pushAudience = need("GOOGLE_PUSH_AUDIENCE", "GOOGLE_PLAY_PACKAGE"),
            pushServiceAccount = need("GOOGLE_PUSH_SERVICE_ACCOUNT", "GOOGLE_PLAY_PACKAGE"),
        )
    }
    val play = google?.let { GooglePlayHttp(File(need("GOOGLE_SERVICE_ACCOUNT_FILE", "GOOGLE_PLAY_PACKAGE")).readText()) }

    val apple = env("APPLE_BUNDLE_ID")?.takeIf { it.isNotBlank() }?.let { bundleId ->
        val roots = File(need("APPLE_ROOT_CA_FILE", "APPLE_BUNDLE_ID")).inputStream().use { file ->
            CertificateFactory.getInstance("X.509").generateCertificates(file).filterIsInstance<X509Certificate>()
        }
        check(roots.isNotEmpty()) { "APPLE_ROOT_CA_FILE holds no certificate" }
        // Sandbox purchases cost nothing, so they count only where this says so by name.
        AppStoreConfig(bundleId, roots, environments = list("APPLE_ENVIRONMENTS").toSet().ifEmpty { setOf("Production") })
    }
    val appStoreApi = apple?.let { config ->
        env("APPLE_API_KEY_FILE")?.takeIf { it.isNotBlank() }?.let { keyFile ->
            AppStoreHttp(
                privateKeyPem = File(keyFile).readText(),
                keyId = need("APPLE_API_KEY_ID", "APPLE_API_KEY_FILE"),
                issuerId = need("APPLE_API_ISSUER_ID", "APPLE_API_KEY_FILE"),
                bundleId = config.bundleId,
                sandboxToo = "Sandbox" in config.environments,
            )
        }
    }

    val settings = Settings(
        nicknameBlocklist = blocklist,
        trustForwardedHeaders = env("TRUST_FORWARDED_HEADERS") == "true",
        // Comma-separated client ids. A provider with none set accepts no sign-in.
        identityProviders = buildMap {
            list("GOOGLE_CLIENT_IDS").toSet().takeIf { it.isNotEmpty() }?.let { put(IdentityProvider.GOOGLE, ProviderConfig.google(it)) }
            list("APPLE_CLIENT_IDS").toSet().takeIf { it.isNotEmpty() }?.let { put(IdentityProvider.APPLE, ProviderConfig.apple(it)) }
        },
        // Who has paid is answered from verified purchases. This lets everyone in regardless.
        everyoneIsSubscribed = env("EVERYONE_IS_SUBSCRIBED") == "true",
        plans = plans,
        google = google,
        apple = apple,
        // The spec's daily job, made four times a day: someone shut out by a lost renewal
        // waits for the next one. Zero turns it off.
        recheckEvery = (env("RECHECK_EVERY_HOURS")?.toLongOrNull() ?: 6).takeIf { it > 0 }?.let(Duration::ofHours),
    )
    return Configured(settings, play, appStoreApi)
}

private fun fingerprint(certificate: X509Certificate): String =
    MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString(":") { "%02X".format(it) }
