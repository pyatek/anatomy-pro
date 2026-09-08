package com.ptk.anatomypro.renderer.filament

import com.ptk.anatomypro.renderer.api.AnatomyRenderer

/**
 * The returned renderer is not yet attached to anything.
 *
 * As on iOS, the platform entry point decides where it draws — a `Surface` in the app, an
 * offscreen swap chain in tests — so it casts to [FilamentAnatomyRenderer] and calls the
 * matching attach method.
 */
actual fun createAnatomyRenderer(): AnatomyRenderer = FilamentAnatomyRenderer()
