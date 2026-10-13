package com.ptk.anatomypro.api.client

import com.ptk.anatomypro.api.AccountDeletedResponse
import com.ptk.anatomypro.api.AccountResponse
import com.ptk.anatomypro.api.ApiPaths
import com.ptk.anatomypro.api.IdentityProvider
import com.ptk.anatomypro.api.LinkRequest
import com.ptk.anatomypro.api.LinkResponse
import com.ptk.anatomypro.api.NicknameRequest

/**
 * The account this device speaks for (backend spec §5): its nickname, signing in, signing
 * out, and leaving.
 *
 * It answers in the wire's own types. No screen shows any of this yet, and the shape the
 * screens want is theirs to say when they are designed.
 */
class ServerAccount(private val api: ApiClient) {

    suspend fun read(): AccountResponse = api.get(ApiPaths.ACCOUNT, AccountResponse.serializer())

    /** @throws ApiFailure `nickname_invalid`, `nickname_taken` or `nickname_locked`. */
    suspend fun rename(nickname: String): AccountResponse =
        api.put(ApiPaths.NICKNAME, NicknameRequest.serializer(), NicknameRequest(nickname), AccountResponse.serializer())

    /**
     * Signs in with a provider's ID token. Where the identity already opens another
     * account this device now speaks for that one (§5.3), so what was remembered about
     * the old one is dropped.
     *
     * @throws ApiFailure `identity_invalid` for a token the provider did not sign for us.
     */
    suspend fun link(provider: IdentityProvider, idToken: String): LinkResponse =
        api.post(ApiPaths.LINK, LinkRequest.serializer(), LinkRequest(provider, idToken), LinkResponse.serializer())
            .also { if (it.merged) api.forgetWhatWasRemembered() }

    /** Ends this device's access. It starts again as someone new. */
    suspend fun signOut() {
        api.post(ApiPaths.SIGN_OUT)
        api.forgetAccount()
    }

    /** Deletes the account everywhere (§5.4). A subscription is the store's to cancel, and the answer says if one remains. */
    suspend fun delete(): AccountDeletedResponse =
        api.delete(ApiPaths.ACCOUNT, AccountDeletedResponse.serializer()).also { api.forgetAccount() }
}
