package com.ptk.anatomypro.navigation

/**
 * The five top-level destinations, in the prototype's order.
 *
 * Labels are Polish because the prototype is: §13 makes Polish a display locale and the UI
 * strings are not extracted for localisation yet, so they are literals here rather than
 * pretending to a mechanism that does not exist.
 */
enum class AnatomyDestination(val label: String) {
    Today("DZIŚ"),
    Atlas("ATLAS"),
    Test("TEST"),
    Ranking("RANKING"),
    Profile("PROFIL"),
}
