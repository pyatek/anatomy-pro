package com.ptk.anatomypro.feature.onboarding

import com.ptk.anatomypro.core.data.NotBuiltPackRepository
import com.ptk.anatomypro.core.data.repository.PackRepository

/**
 * Whether onboarding has a download step.
 *
 * It does only where there is something to download with. Production has no PackRepository
 * yet — the one it is given throws on first use, by design (all-screens spec §6) — and the
 * pack ships inside the app, so there onboarding is two steps. When a real PackRepository
 * is built this returns true everywhere and can be deleted.
 */
fun onboardingHasDownloadStep(packs: PackRepository): Boolean = packs !is NotBuiltPackRepository
