package com.ptk.anatomypro.renderer.filament

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutlineMaterialDataTest {

    @Test
    fun the_compiled_material_is_a_filament_package() {
        val bytes = OutlineMaterialData.bytes

        // Every .filamat begins with this chunk tag.
        assertEquals("SREV_TAM", bytes.copyOfRange(0, 8).decodeToString())
        // Three backends' shaders: tens of kilobytes. A stub would be a few hundred bytes.
        assertTrue(bytes.size > 10_000, "only ${bytes.size} bytes")
    }
}
