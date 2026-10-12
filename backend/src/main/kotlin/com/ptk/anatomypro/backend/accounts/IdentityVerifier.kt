package com.ptk.anatomypro.backend.accounts

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.SignedJWT
import com.ptk.anatomypro.api.ApiErrorCode
import com.ptk.anatomypro.api.IdentityProvider
import com.ptk.anatomypro.backend.ApiException
import io.ktor.http.HttpStatusCode
import java.time.Duration
import java.time.Instant

/**
 * What makes a provider's token one of ours: who issued it, which of our client ids it was
 * made out to, and where the provider publishes the keys it signs with.
 */
data class ProviderConfig(val issuers: Set<String>, val audiences: Set<String>, val keysUrl: String) {
    companion object {
        fun google(clientIds: Set<String>) =
            ProviderConfig(setOf("https://accounts.google.com", "accounts.google.com"), clientIds, "https://www.googleapis.com/oauth2/v3/certs")

        fun apple(clientIds: Set<String>) =
            ProviderConfig(setOf("https://appleid.apple.com"), clientIds, "https://appleid.apple.com/auth/keys")
    }
}

/**
 * Checks a Google or Apple ID token and reads the one thing kept from it: the stable
 * subject (backend spec §5.3). No email, name or picture is read, let alone stored.
 *
 * A token is believed only when all of these hold: it is signed with RS256 by a key the
 * provider publishes; it was issued by that provider; it was made out to one of our client
 * ids; and it has not expired. The signature check itself is the library's.
 *
 * The provider's keys are fetched once and kept. A token naming a key not yet seen causes
 * one more fetch — providers rotate their keys — but no more than one a minute, so made-up
 * key ids cannot be used to make this server hammer the provider.
 */
class IdentityVerifier(
    private val providers: Map<IdentityProvider, ProviderConfig>,
    private val fetchKeys: (url: String) -> String,
    private val now: () -> Instant,
) {
    private class Kept(val keys: JWKSet, val fetchedAt: Instant)

    private val kept = mutableMapOf<String, Kept>()

    /** @throws ApiException `identity_invalid` for a token that is not believed. */
    fun subjectOf(provider: IdentityProvider, idToken: String): String {
        val config = providers[provider] ?: throw invalid()
        val token = runCatching { SignedJWT.parse(idToken) }.getOrNull() ?: throw invalid()
        // The provider's own algorithm and no other: a token "signed" with a shared secret
        // made from the public key is the oldest forgery there is.
        if (token.header.algorithm != JWSAlgorithm.RS256) throw invalid()
        val key = keyFor(config.keysUrl, token.header.keyID ?: throw invalid()) ?: throw invalid()
        if (!runCatching { token.verify(RSASSAVerifier(key.toRSAPublicKey())) }.getOrDefault(false)) throw invalid()

        val claims = runCatching { token.jwtClaimsSet }.getOrNull() ?: throw invalid()
        val expires = claims.expirationTime?.toInstant() ?: throw invalid()
        if (claims.issuer !in config.issuers) throw invalid()
        if (claims.audience.orEmpty().none { it in config.audiences }) throw invalid()
        if (!expires.isAfter(now())) throw invalid()
        return claims.subject?.takeIf { it.isNotBlank() } ?: throw invalid()
    }

    @Synchronized
    private fun keyFor(url: String, keyId: String): RSAKey? {
        val held = kept[url]
        held?.keys?.getKeyByKeyId(keyId)?.let { return it as? RSAKey }
        if (held != null && Duration.between(held.fetchedAt, now()) < REFETCH_AFTER) return null

        val fetched = try {
            JWKSet.parse(fetchKeys(url))
        } catch (failure: Exception) {
            // Nothing to check the token against. That is not the caller's fault, and the
            // answer is to try again, not that the token is bad.
            if (held == null) throw ApiException(HttpStatusCode.ServiceUnavailable, ApiErrorCode.UNKNOWN, "the provider's keys could not be read")
            return null
        }
        kept[url] = Kept(fetched, now())
        return fetched.getKeyByKeyId(keyId) as? RSAKey
    }

    private fun invalid() = ApiException(HttpStatusCode.Unauthorized, ApiErrorCode.IDENTITY_INVALID)

    private companion object {
        val REFETCH_AFTER: Duration = Duration.ofMinutes(1)
    }
}
