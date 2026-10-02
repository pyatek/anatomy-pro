package com.ptk.anatomypro.core.data.model

/**
 * How structure names are shown in lists.
 *
 * Latin is always present because it is the canonical key (spec §13); the others depend on
 * what a pack ships, which today is English only.
 */
enum class NameDisplay { LatinOnly, LatinAndDisplay, All }

/**
 * Settings, as the prototype's screen 20 groups them.
 *
 * Interface and examination languages are deliberately independent: §13 says a user may
 * read the UI in Polish while being examined in Latin, and collapsing them into one
 * "language" setting would remove the distinction the design is built on.
 */
data class AppSettings(
    val interfaceLocale: String = "pl",
    val examinationLocale: String = "la",
    val nameDisplay: NameDisplay = NameDisplay.LatinAndDisplay,
    /** §12: timers must be disableable. Off means untimed, and untimed means unranked. */
    val quizTimerEnabled: Boolean = true,
    /** §12's screen-reader path: the tree is an equivalent, not a fallback. */
    val structureTreeMode: Boolean = false,
    /** §12: never distinguish state by hue alone. */
    val patternsNotColour: Boolean = true,
    /** Whether the first-run language choice has been made. */
    val onboarded: Boolean = false,
    /** Screen 02's goal setting. A preference, not a domain (spec §4.8). */
    val studiedSystems: Set<String> = emptySet(),
)

/** Sorted so an unchanged set encodes identically and writes no row (see the update loop). */
internal fun encodeStudiedSystems(systems: Set<String>): String = systems.sorted().joinToString(",")

internal fun decodeStudiedSystems(stored: String?): Set<String> =
    stored?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
