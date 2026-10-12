package com.ptk.anatomypro.api.client

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Backend spec §5.1: the token is kept under the Keystore, never in the clear. */
@RunWith(AndroidJUnit4::class)
class KeystoreClientStoreTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = "client-store-test"
    private fun store() = KeystoreClientStore(context, file)

    @Test fun what_is_written_is_read_back_by_a_store_made_later() = runBlocking {
        store().write("device-token", "dG9rZW4tMTIzNDU2Nzg5MA")

        assertEquals("dG9rZW4tMTIzNDU2Nzg5MA", store().read("device-token"))
    }

    @Test fun what_is_on_disk_is_not_the_value() = runBlocking {
        store().write("device-token", "dG9rZW4tMTIzNDU2Nzg5MA")

        val onDisk = context.getSharedPreferences(file, Context.MODE_PRIVATE).all.values.joinToString()
        assertFalse("dG9rZW4tMTIzNDU2Nzg5MA" in onDisk)
        assertFalse(onDisk.isEmpty())
    }

    @Test fun a_value_can_be_replaced_and_removed() = runBlocking {
        val store = store()
        store.write("subscribed", "false")
        store.write("subscribed", "true")
        assertEquals("true", store.read("subscribed"))

        store.remove("subscribed")
        assertNull(store.read("subscribed"))
        assertNull(store.read("never-written"))
    }

    @Test fun a_value_that_cannot_be_decrypted_reads_as_absent_rather_than_crashing() = runBlocking {
        // A restored backup carries the file and not the key.
        context.getSharedPreferences(file, Context.MODE_PRIVATE).edit().putString("device-token", "bm90LW91cnM").commit()

        assertNull(store().read("device-token"))
    }
}
