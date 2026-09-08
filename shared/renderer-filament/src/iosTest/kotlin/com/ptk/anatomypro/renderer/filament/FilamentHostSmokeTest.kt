package com.ptk.anatomypro.renderer.filament

import com.ptk.anatomypro.renderer.filament.cinterop.AR_EVENT_READY
import com.ptk.anatomypro.renderer.filament.cinterop.ar_attach_headless
import com.ptk.anatomypro.renderer.filament.cinterop.ar_create
import com.ptk.anatomypro.renderer.filament.cinterop.ar_destroy
import com.ptk.anatomypro.renderer.filament.cinterop.ar_event
import com.ptk.anatomypro.renderer.filament.cinterop.ar_poll_event
import com.ptk.anatomypro.renderer.filament.cinterop.ar_render_frame
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Phase 0 feasibility probe, reduced to its smallest form.
 *
 * If Filament cannot initialise Metal and render one offscreen frame from inside a
 * Kotlin/Native test binary, nothing else in the iOS renderer is worth building and the
 * three.js fallback in spec §14 is on the table. Everything else assumes this passes.
 */
@OptIn(ExperimentalForeignApi::class)
class FilamentHostSmokeTest {

    @Test
    fun boots_metal_headless_and_renders_a_frame() {
        val renderer = ar_create()
        assertNotNull(renderer, "ar_create returned null")

        try {
            ar_attach_headless(renderer, 256u, 256u)
            ar_render_frame(renderer, 0uL)

            memScoped {
                val event = alloc<ar_event>()
                assertTrue(ar_poll_event(renderer, event.ptr), "no event was queued")
                assertEquals(AR_EVENT_READY, event.type)
            }
        } finally {
            ar_destroy(renderer)
        }
    }
}
