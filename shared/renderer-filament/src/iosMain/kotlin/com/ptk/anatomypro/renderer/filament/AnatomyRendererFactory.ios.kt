package com.ptk.anatomypro.renderer.filament

import com.ptk.anatomypro.renderer.api.AnatomyRenderer

/**
 * The returned renderer is not yet attached to anything.
 *
 * The platform entry point decides where it draws — a `CAMetalLayer` in the app, an
 * offscreen swap chain in tests — so it casts to [FilamentAnatomyRenderer] and calls the
 * matching attach method. Everything above the entry point sees only [AnatomyRenderer].
 */
actual fun createAnatomyRenderer(): AnatomyRenderer = FilamentAnatomyRenderer()
