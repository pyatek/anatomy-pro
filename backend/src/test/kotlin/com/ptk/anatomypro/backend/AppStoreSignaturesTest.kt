package com.ptk.anatomypro.backend

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.util.Base64
import com.ptk.anatomypro.backend.subscriptions.AppStoreSignatures
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Backend spec §8.1: what Apple signs is believed only when its certificate chain ends at a
 * root this server was given. The chains here are made in the test; Apple's own has never
 * been put through this.
 */
class AppStoreSignaturesTest {

    private var now: Instant = Instant.parse("2026-10-12T10:00:00Z")
    private val apple = TestChain(now)
    private val signatures = AppStoreSignatures(listOf(apple.root)) { now }
    private val payload = buildJsonObject { put("originalTransactionId", "1000000000000001") }

    @Test
    fun a_payload_signed_by_a_leaf_that_chains_to_the_root_is_read() {
        assertEquals(JsonPrimitive("1000000000000001"), signatures.payloadOf(apple.sign(payload))?.get("originalTransactionId"))
    }

    @Test
    fun a_chain_from_somebody_elses_root_is_not_believed_however_well_formed() {
        val forger = TestChain(now)

        assertNull(signatures.payloadOf(forger.sign(payload)))
    }

    @Test
    fun a_forged_chain_that_merely_ends_with_the_real_root_certificate_is_not_believed() {
        // The root is public: anyone can append it. It has to have signed something.
        val forger = TestChain(now)

        assertNull(signatures.payloadOf(forger.sign(payload, certificates = listOf(forger.leaf, forger.intermediate, apple.root))))
    }

    @Test
    fun the_real_chain_with_a_signature_from_another_key_is_not_believed() {
        val forger = TestChain(now)

        assertNull(signatures.payloadOf(apple.sign(payload, with = forger.leafKey)))
    }

    @Test
    fun a_payload_changed_after_signing_is_not_believed() {
        val (header, _, signature) = apple.sign(payload).split('.')
        val changed = Base64.encode(buildJsonObject { put("originalTransactionId", "someone-elses") }.toString()).toString().trimEnd('=').replace('+', '-').replace('/', '_')

        assertNull(signatures.payloadOf("$header.$changed.$signature"))
    }

    @Test
    fun a_certificate_the_root_issued_for_some_other_purpose_cannot_sign_a_transaction() {
        // Apple's root signs intermediates for many things, and those sign certificates for
        // anyone with a developer account. Only the ones marked for the App Store count.
        val unmarkedLeaf = TestChain(now, leafMarker = null)
        val unmarkedIntermediate = TestChain(now, intermediateMarker = null)

        assertNull(AppStoreSignatures(listOf(unmarkedLeaf.root)) { now }.payloadOf(unmarkedLeaf.sign(payload)))
        assertNull(AppStoreSignatures(listOf(unmarkedIntermediate.root)) { now }.payloadOf(unmarkedIntermediate.sign(payload)))
    }

    @Test
    fun a_leaf_cannot_be_made_an_authority_by_whoever_holds_it() {
        // The middle certificate is not allowed to issue others, so what it "issued" is nothing.
        val chain = TestChain(now, intermediateIsAuthority = false)

        assertNull(AppStoreSignatures(listOf(chain.root)) { now }.payloadOf(chain.sign(payload)))
    }

    @Test
    fun an_expired_certificate_signs_nothing() {
        val signed = apple.sign(payload)
        now = now.plusSeconds(366 * TestChain.DAY)

        assertNull(signatures.payloadOf(signed))
    }

    @Test
    fun a_chain_that_is_too_short_or_absent_is_not_believed() {
        assertNull(signatures.payloadOf(apple.sign(payload, certificates = listOf(apple.leaf))))
        assertNull(signatures.payloadOf(apple.sign(payload, certificates = listOf(apple.leaf, apple.root))))
        assertNull(signatures.payloadOf(apple.sign(payload, certificates = listOf(apple.root, apple.root, apple.root), with = apple.rootKey)))
    }

    @Test
    fun a_token_signed_with_a_shared_secret_or_not_at_all_is_not_believed() {
        val chain = listOf(apple.leaf, apple.intermediate, apple.root).map { Base64.encode(it.encoded) }
        val symmetric = JWSObject(JWSHeader.Builder(JWSAlgorithm.HS256).x509CertChain(chain).build(), Payload(payload.toString()))
            .apply { sign(MACSigner(apple.leaf.publicKey.encoded.copyOf(64))) }.serialize()
        val (header, body, _) = apple.sign(payload).split('.')
        val unsignedHeader = Base64.encode("""{"alg":"none"}""").toString().trimEnd('=')

        assertNull(signatures.payloadOf(symmetric))
        assertNull(signatures.payloadOf("$header.$body."))
        assertNull(signatures.payloadOf("$unsignedHeader.$body."))
        assertNull(signatures.payloadOf("not a jws"))
        assertNull(signatures.payloadOf(""))
    }

    @Test
    fun with_no_root_configured_nothing_is_believed() {
        assertNull(AppStoreSignatures(emptyList()) { now }.payloadOf(apple.sign(payload)))
    }
}
