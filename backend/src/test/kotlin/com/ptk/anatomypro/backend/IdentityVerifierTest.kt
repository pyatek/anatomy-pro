package com.ptk.anatomypro.backend

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.ptk.anatomypro.api.ApiErrorCode
import com.ptk.anatomypro.api.IdentityProvider
import com.ptk.anatomypro.backend.accounts.IdentityVerifier
import io.ktor.http.HttpStatusCode
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Backend spec §5.3: a token is believed only if the provider's own key signed it, for us, and it has not run out. */
class IdentityVerifierTest {

    private var now = Instant.parse("2026-10-12T10:00:00Z")
    private val identities = TestIdentities { now }
    private val verifier = IdentityVerifier(identities.providers, identities::fetch) { now }

    private fun refused(provider: IdentityProvider, token: String) {
        val refusal = assertFailsWith<ApiException> { verifier.subjectOf(provider, token) }
        assertEquals(ApiErrorCode.IDENTITY_INVALID, refusal.code)
        assertEquals(HttpStatusCode.Unauthorized, refusal.status)
    }

    @Test
    fun a_token_the_provider_signed_for_this_app_gives_its_subject_and_nothing_else() {
        assertEquals("g-123", verifier.subjectOf(IdentityProvider.GOOGLE, identities.google("g-123")))
        assertEquals("a-456", verifier.subjectOf(IdentityProvider.APPLE, identities.apple("a-456")))
    }

    @Test
    fun a_token_made_out_to_another_app_is_refused() {
        refused(IdentityProvider.GOOGLE, identities.token("g-123", audience = "someone-elses-app.apps.googleusercontent.com"))
    }

    @Test
    fun a_token_from_another_issuer_is_refused_even_when_the_key_is_right() {
        refused(IdentityProvider.GOOGLE, identities.token("g-123", issuer = "https://accounts.example.org"))
    }

    @Test
    fun a_token_that_has_run_out_is_refused() {
        val token = identities.google("g-123")
        now += Duration.ofHours(2)

        refused(IdentityProvider.GOOGLE, token)
    }

    @Test
    fun a_token_signed_by_someone_else_under_the_providers_key_id_is_refused() {
        val impostor = RSAKeyGenerator(2048).keyID(identities.googleKey.keyID).generate()

        refused(IdentityProvider.GOOGLE, identities.token("g-123", key = impostor))
    }

    @Test
    fun one_providers_token_is_not_accepted_as_anothers() {
        refused(IdentityProvider.APPLE, identities.google("g-123"))
    }

    @Test
    fun a_token_signed_with_a_shared_secret_or_not_a_token_at_all_is_refused() {
        refused(IdentityProvider.GOOGLE, identities.symmetric("g-123"))
        refused(IdentityProvider.GOOGLE, "not.a.token")
        refused(IdentityProvider.GOOGLE, "")
    }

    @Test
    fun a_provider_this_server_was_given_no_client_id_for_accepts_nothing() {
        val googleOnly = IdentityVerifier(identities.providers.filterKeys { it == IdentityProvider.GOOGLE }, identities::fetch) { now }

        val refusal = assertFailsWith<ApiException> { googleOnly.subjectOf(IdentityProvider.APPLE, identities.apple("a-456")) }
        assertEquals(ApiErrorCode.IDENTITY_INVALID, refusal.code)
    }

    @Test
    fun the_providers_keys_are_fetched_once_and_kept() {
        repeat(5) { verifier.subjectOf(IdentityProvider.GOOGLE, identities.google("g-$it")) }

        assertEquals(1, identities.fetches)
    }

    @Test
    fun a_key_the_provider_has_rotated_to_is_fetched_when_first_seen() {
        verifier.subjectOf(IdentityProvider.GOOGLE, identities.google("g-1"))
        val rotated = RSAKeyGenerator(2048).keyID("google-2").generate()
        identities.published[TestIdentities.GOOGLE_KEYS] = JWKSet(rotated.toPublicJWK()).toString()
        now += Duration.ofMinutes(5)

        assertEquals("g-2", verifier.subjectOf(IdentityProvider.GOOGLE, identities.token("g-2", key = rotated)))
    }

    @Test
    fun tokens_naming_keys_that_do_not_exist_cannot_make_the_server_hammer_the_provider() {
        verifier.subjectOf(IdentityProvider.GOOGLE, identities.google("g-1"))
        val unknown = RSAKeyGenerator(2048).keyID("made-up").generate()
        now += Duration.ofMinutes(5)

        repeat(20) { refused(IdentityProvider.GOOGLE, identities.token("g-1", key = unknown)) }

        // The first sight of an unknown key is worth one look; the next nineteen are not.
        assertEquals(2, identities.fetches)
    }

    @Test
    fun when_the_providers_keys_cannot_be_fetched_the_answer_is_try_later_not_wrong_token() {
        identities.published.remove(TestIdentities.GOOGLE_KEYS)

        val refusal = assertFailsWith<ApiException> { verifier.subjectOf(IdentityProvider.GOOGLE, identities.google("g-123")) }

        assertEquals(HttpStatusCode.ServiceUnavailable, refusal.status)
    }
}
