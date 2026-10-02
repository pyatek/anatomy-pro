package com.ptk.anatomypro.navigation

import kotlinx.serialization.Serializable

/**
 * Where each tab starts.
 *
 * Each tab owns its own back stack, so leaving the quiz mid-session and returning lands
 * back in the question rather than at the topic grid (spec §7).
 */
enum class TopLevel(val start: Any) {
    Today(DailyRoute.Home),
    Atlas(AtlasRoute.Browse),
    Test(QuizRoute.Topics),
    Ranking(DailyRoute.Leaderboard),
    Profile(ProfileRoute.Profile),
}

/** Screens 04, 05, 06, 07, 21. */
@Serializable
sealed interface AtlasRoute {
    @Serializable data object Browse : AtlasRoute
    @Serializable data object Search : AtlasRoute
    @Serializable data object Layers : AtlasRoute
    @Serializable data object Tree : AtlasRoute
    @Serializable data class Detail(val structureId: String) : AtlasRoute
}

/** Screens 08 to 13. */
@Serializable
sealed interface QuizRoute {
    @Serializable data object Topics : QuizRoute
    @Serializable data class Question(val sessionId: String, val index: Int) : QuizRoute
    @Serializable data class Feedback(val sessionId: String, val index: Int) : QuizRoute
    @Serializable data class Summary(val sessionId: String) : QuizRoute
}

/** Screens 14, 15, 16. */
@Serializable
sealed interface DailyRoute {
    @Serializable data object Home : DailyRoute
    @Serializable data object Lobby : DailyRoute
    @Serializable data object Leaderboard : DailyRoute
}

/** Screens 17, 18, 19, 20. */
@Serializable
sealed interface ProfileRoute {
    @Serializable data object Profile : ProfileRoute
    @Serializable data object Settings : ProfileRoute
    @Serializable data object Packs : ProfileRoute
    @Serializable data object Paywall : ProfileRoute
}

/** Screens 01, 02, 03 — shown before the tabs exist. */
@Serializable
sealed interface OnboardingRoute {
    @Serializable data object Language : OnboardingRoute
    @Serializable data object Goals : OnboardingRoute
    @Serializable data object FirstDownload : OnboardingRoute
}
