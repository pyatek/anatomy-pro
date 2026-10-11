package com.ptk.anatomypro

import com.ptk.anatomypro.core.data.model.PackState
import com.ptk.anatomypro.core.data.model.PackStatus
import com.ptk.anatomypro.core.data.repository.InstalledPack
import com.ptk.anatomypro.core.model.PackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Which pack the canvases draw when packs are downloaded rather than shipped in the app. */
class DrawnPackTest {

    private val skeleton = PackId("skeletal-body")
    private fun listed(status: PackStatus) = listOf(PackState(skeleton, 2, "Układ szkieletowy", 10, "abc", status))
    private val onDevice = listOf(InstalledPack(skeleton, 2, "file:///packs/skeletal-body/2/pack.glb"))

    @Test
    fun the_first_pack_is_drawn_from_where_the_database_says_its_mesh_is() {
        val pack = drawnPackOf(listed(PackStatus.Installed), onDevice, first = skeleton)

        assertEquals(skeleton, pack?.id)
        assertEquals("file:///packs/skeletal-body/2/pack.glb", pack?.mesh?.uri)
        assertEquals("Układ szkieletowy", pack?.label)
    }

    @Test
    fun nothing_is_drawn_while_the_pack_is_still_arriving() {
        assertNull(drawnPackOf(listed(PackStatus.Downloading(5, 10)), emptyList(), first = skeleton))
        assertNull(drawnPackOf(listed(PackStatus.Available), emptyList(), first = skeleton))
    }

    @Test
    fun a_pack_being_deleted_is_not_drawn_from_a_row_that_is_about_to_go() {
        // The listing has moved on; the database row is a moment behind it.
        assertNull(drawnPackOf(listed(PackStatus.Available), onDevice, first = skeleton))
    }

    @Test
    fun another_pack_on_the_device_is_not_drawn_in_place_of_the_first() {
        val other = listOf(InstalledPack(PackId("trunk-all-systems"), 1, "file:///cache/trunk.glb"))

        assertNull(drawnPackOf(listed(PackStatus.Installed), other, first = skeleton))
    }
}
