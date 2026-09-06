package com.ptk.anatomypro.renderer.filament

import kotlin.test.Test
import kotlin.test.assertFailsWith

class AnatomyRendererFactoryTest {

    /**
     * The factory is a deliberate stub until Phase 0 implements the Filament hosts.
     * When Phase 0 lands, delete this test and replace it with the renderer-api contract
     * tests run against the real implementation.
     */
    @Test
    fun factory_is_wired_but_not_yet_implemented() {
        assertFailsWith<NotImplementedError> { createAnatomyRenderer() }
    }
}
