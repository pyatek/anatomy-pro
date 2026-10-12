package com.ptk.anatomypro.backend

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.ptk.anatomypro.api.IdentityProvider
import com.ptk.anatomypro.backend.accounts.ProviderConfig
import java.time.Instant
import java.util.Date

/**
 * A stand-in for Google and Apple: keys made here, the key set they would publish, and
 * tokens signed with them. Nothing in the server knows these are not the real providers —
 * it is given a URL to fetch keys from and a function that fetches.
 */
class TestIdentities(private val now: () -> Instant = { Instant.parse("2026-10-12T10:00:00Z") }) {
    val googleKey: RSAKey = RSAKeyGenerator(2048).keyID("google-1").generate()
    val appleKey: RSAKey = RSAKeyGenerator(2048).keyID("apple-1").generate()

    /** What each provider's key URL serves; a test swaps an entry to rotate keys or break the fetch. */
    val published = mutableMapOf(
        GOOGLE_KEYS to JWKSet(googleKey.toPublicJWK()).toString(),
        APPLE_KEYS to JWKSet(appleKey.toPublicJWK()).toString(),
    )
    var fetches = 0

    val providers = mapOf(
        IdentityProvider.GOOGLE to ProviderConfig(setOf("https://accounts.google.com", "accounts.google.com"), setOf(GOOGLE_CLIENT), GOOGLE_KEYS),
        IdentityProvider.APPLE to ProviderConfig(setOf("https://appleid.apple.com"), setOf(APPLE_CLIENT), APPLE_KEYS),
    )

    fun fetch(url: String): String {
        fetches++
        return published[url] ?: throw java.io.IOException("cannot reach $url")
    }

    fun token(
        subject: String,
        key: RSAKey = googleKey,
        issuer: String = "https://accounts.google.com",
        audience: String = GOOGLE_CLIENT,
        expires: Instant = now().plusSeconds(3600),
        keyId: String = key.keyID,
    ): String = SignedJWT(
        JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).type(JOSEObjectType.JWT).build(),
        JWTClaimsSet.Builder().subject(subject).issuer(issuer).audience(audience)
            .issueTime(Date.from(now().minusSeconds(60))).expirationTime(Date.from(expires))
            // What a real token also carries, and what must never be kept (§5.5).
            .claim("email", "$subject@example.org").claim("name", "Someone Real")
            .build(),
    ).apply { sign(RSASSASigner(key)) }.serialize()

    fun google(subject: String) = token(subject)
    fun apple(subject: String) = token(subject, key = appleKey, issuer = "https://appleid.apple.com", audience = APPLE_CLIENT)

    /** Signed with a shared secret instead of the provider's key: the classic way to forge one. */
    fun symmetric(subject: String): String = SignedJWT(
        JWSHeader.Builder(JWSAlgorithm.HS256).keyID(googleKey.keyID).build(),
        JWTClaimsSet.Builder().subject(subject).issuer("https://accounts.google.com").audience(GOOGLE_CLIENT)
            .expirationTime(Date.from(now().plusSeconds(3600))).build(),
    ).apply { sign(MACSigner(googleKey.toPublicJWK().toRSAPublicKey().encoded.copyOf(64))) }.serialize()

    companion object {
        const val GOOGLE_KEYS = "https://keys.test/google"
        const val APPLE_KEYS = "https://keys.test/apple"
        const val GOOGLE_CLIENT = "anatomypro-android.apps.googleusercontent.com"
        const val APPLE_CLIENT = "com.ptk.anatomypro"
    }
}
