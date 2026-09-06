package com.ptk.anatomypro.core.model

/**
 * Tracked per locale, not per structure: a Latin name may be correct while its Polish
 * translation is wrong, and a single flag would force re-verifying everything whenever one
 * language is corrected (spec §5).
 *
 * Only VERIFIED structures may be used as quiz answers (spec §7).
 */
enum class VerificationState {
    UNVERIFIED,
    VERIFIED,
    DISPUTED,
}
