package com.ptk.anatomypro.renderer.filament

import com.ptk.anatomypro.renderer.api.AnatomyRenderer

/**
 * Builds the platform's Filament-backed renderer.
 *
 * Nothing may depend on this module except the shared umbrella and the platform entry
 * points (spec §3.1). Keeping the dependency here and not in feature modules is what makes
 * the three.js fallback in spec §14 a link-time decision rather than a rewrite.
 */
expect fun createAnatomyRenderer(): AnatomyRenderer
