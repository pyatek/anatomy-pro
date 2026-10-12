package com.ptk.anatomypro.api.client

import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Backend spec §5.1: the token is kept in the Keychain.
 *
 * Ignored, and so proving nothing, for a reason outside this code: a test binary spawned on
 * the simulator has no application behind it and therefore no keychain — `SecItemAdd`
 * answers -25291, "no keychain is available". These run only inside an app. Until there is
 * an app-hosted test target, the store is checked by hand: launch the app against a server,
 * relaunch, and see that no second account was made.
 */
@Ignore
class KeychainClientStoreTest {

    private fun store() = KeychainClientStore(service = "com.ptk.anatomypro.test")

    @Test
    fun what_is_written_is_read_back_by_a_store_made_later() = runTest {
        store().write("device-token", "dG9rZW4tMTIzNDU2Nzg5MA")

        assertEquals("dG9rZW4tMTIzNDU2Nzg5MA", store().read("device-token"))
    }

    @Test
    fun a_value_can_be_replaced_and_removed() = runTest {
        val store = store()
        store.write("subscribed", "false")
        store.write("subscribed", "true")
        assertEquals("true", store.read("subscribed"))

        store.remove("subscribed")
        assertNull(store.read("subscribed"))
        assertNull(store.read("never-written"))
    }
}
