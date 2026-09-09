package com.ptk.anatomypro.core.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Proves the Android database wiring, not the mapping.
 *
 * The behavioural coverage lives in the iOS test against the same bundled SQLite build.
 * What only a device can show is that Room's Android builder, the bundled driver and the
 * app's own database directory work together at all.
 */
@RunWith(AndroidJUnit4::class)
class AndroidDatabaseTest {

    @Test
    fun installs_a_pack_into_a_real_android_database() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = anatomyDatabase(context, name = "anatomy-test-${System.nanoTime()}.db")
        try {
            PackInstaller(database).install(SAMPLE_MANIFEST, version = 1, meshUri = "file:///m.glb")
            val dao = database.structures()

            val clavicle = assertNotNull(dao.structure("1168-clavicula-left"))
            assertEquals("trunk", clavicle.regionId)
            assertEquals("Clavicula", assertNotNull(dao.text(clavicle.id, "la")).name)
            assertTrue(dao.searchHits("clavic", 10).isNotEmpty())
            assertEquals(1, dao.siblings(clavicle.parentId, exclude = clavicle.id, limit = 10).size)
        } finally {
            database.close()
        }
    }
}
