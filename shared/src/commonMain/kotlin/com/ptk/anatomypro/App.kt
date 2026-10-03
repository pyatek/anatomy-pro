package com.ptk.anatomypro

import anatomypro.shared.generated.resources.Res
import anatomypro.shared.generated.resources.not_built_yet
import anatomypro.shared.generated.resources.tab_ranking
import anatomypro.shared.generated.resources.tab_test
import anatomypro.shared.generated.resources.tab_today
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.ptk.anatomypro.core.data.AppDependencies
import com.ptk.anatomypro.core.data.model.NameDisplay
import com.ptk.anatomypro.core.designsystem.AnatomyTheme
import com.ptk.anatomypro.core.designsystem.ProvideAppLocale
import com.ptk.anatomypro.feature.settings.LanguageSelectionScreen
import com.ptk.anatomypro.feature.settings.SettingsScreen
import com.ptk.anatomypro.feature.settings.SettingsUiState
import com.ptk.anatomypro.feature.settings.SettingsViewModel
import com.ptk.anatomypro.navigation.AnatomyBottomBar
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.feature.atlas.scene.AtlasSceneViewModel
import com.ptk.anatomypro.navigation.AtlasRoute
import com.ptk.anatomypro.navigation.DailyRoute
import com.ptk.anatomypro.navigation.ProfileRoute
import com.ptk.anatomypro.navigation.QuizRoute
import com.ptk.anatomypro.navigation.TopLevel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The app shell.
 *
 * Settings are read once here and passed down rather than reached for by each screen, so
 * a language change reaches the atlas, the detail page and search from a single source.
 * The repositories arrive as [dependencies] rather than being chosen here, so a debug entry
 * point can supply fakes while production supplies Room and refusals (all-screens spec §6).
 * [ProvideAppLocale] makes every string resource follow the interface locale, not the
 * system's (§13).
 */
@Composable
fun App(dependencies: AppDependencies) {
    AnatomyTheme {
        val settingsModel: SettingsViewModel = viewModel { SettingsViewModel(dependencies.settings) }
        val settingsState: SettingsUiState by settingsModel.state.collectAsState()
        // Held above ProvideAppLocale on purpose. A language change rebuilds everything
        // beneath it (that is how string resources pick the new locale up), and navigation
        // remembered down there would be thrown away with it: switching language on the
        // settings tab used to land the user back on the atlas.
        val navController = rememberNavController()

        ProvideAppLocale(settingsState.settings.interfaceLocale) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    when {
                        settingsState.isLoading -> Centered("…")

                        // Prototype screen 01. Shown until the choice is made, not until a
                        // language differs from the default: accepting the defaults is a
                        // decision too, and it has to be recorded as one.
                        !settingsState.settings.onboarded -> LanguageSelectionScreen(
                            state = settingsState,
                            onInterfaceLocale = settingsModel::onInterfaceLocale,
                            onExaminationLocale = settingsModel::onExaminationLocale,
                            onContinue = settingsModel::onOnboardingComplete,
                        )

                        else -> MainScaffold(
                            dependencies = dependencies,
                            state = settingsState,
                            model = settingsModel,
                            navController = navController,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The five tabs over one NavHost.
 *
 * Each tab keeps its own back stack (all-screens spec §7) through navigation-compose's
 * save-and-restore: leaving a tab saves its stack, returning restores it. The selected tab
 * is read from the back stack rather than held beside it, so the system back gesture cannot
 * leave the bar pointing at a tab the user has left.
 */
@Composable
private fun MainScaffold(
    dependencies: AppDependencies,
    state: SettingsUiState,
    model: SettingsViewModel,
    navController: NavHostController,
) {
    val entry by navController.currentBackStackEntryAsState()
    val tab = entry?.destination?.tab() ?: TopLevel.Atlas
    val locale = state.settings.interfaceLocale
    val openDetail: (StructureId) -> Unit = { navController.navigate(AtlasRoute.Detail(it.value)) }

    // App-scoped: a system hidden on screen 07 stays hidden on the atlas, and a structure
    // focused in tree mode is framed when the model is next on screen.
    val scene: AtlasSceneViewModel? = dependencies.atlas?.let { atlas ->
        viewModel(key = "atlas-scene") { AtlasSceneViewModel(atlas) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            NavHost(navController = navController, startDestination = TopLevel.Atlas.start) {
                composable<AtlasRoute.Browse> {
                    AtlasTab(
                        repository = dependencies.atlas,
                        scene = scene,
                        locale = locale,
                        latinOnly = state.settings.nameDisplay == NameDisplay.LatinOnly,
                        onOpenDetail = openDetail,
                        onSearch = { navController.navigate(AtlasRoute.Search) },
                        onLayers = { navController.navigate(AtlasRoute.Layers) },
                    )
                }
                composable<AtlasRoute.Layers> {
                    LayersRoute(repository = dependencies.atlas, scene = scene, locale = locale, onBack = { navController.popBackStack() })
                }
                composable<AtlasRoute.Search> {
                    SearchRoute(
                        repository = dependencies.atlas,
                        onBack = { navController.popBackStack() },
                        onOpenDetail = openDetail,
                    )
                }
                composable<AtlasRoute.Detail> { backStackEntry ->
                    DetailRoute(
                        repository = dependencies.atlas,
                        id = StructureId(backStackEntry.toRoute<AtlasRoute.Detail>().structureId),
                        locale = locale,
                        onBack = { navController.popBackStack() },
                        onOpenDetail = openDetail,
                    )
                }

                // Profile is not built. Settings live behind it in the prototype, so the
                // tab shows settings rather than a second placeholder.
                composable<ProfileRoute.Profile> {
                    SettingsScreen(
                        state = state,
                        onInterfaceLocale = model::onInterfaceLocale,
                        onExaminationLocale = model::onExaminationLocale,
                        onNameDisplay = model::onNameDisplay,
                        onQuizTimer = model::onQuizTimer,
                        onStructureTreeMode = model::onStructureTreeMode,
                        onPatternsNotColour = model::onPatternsNotColour,
                    )
                }

                // Plans 2-6 add their composable<Route> entries here. Until then each unbuilt
                // tab's start route needs a screen, or selecting the tab crashes the NavHost.
                composable<DailyRoute.Home> { NotBuilt(Res.string.tab_today) }
                composable<QuizRoute.Topics> { NotBuilt(Res.string.tab_test) }
                composable<DailyRoute.Leaderboard> { NotBuilt(Res.string.tab_ranking) }
            }
        }
        AnatomyBottomBar(selected = tab, onSelect = { navController.selectTab(it, current = tab) })
    }
}

/** Selecting the current tab returns it to its start; selecting another restores its stack. */
private fun NavHostController.selectTab(target: TopLevel, current: TopLevel) {
    if (target == current) {
        popBackStack(target.start, inclusive = false)
        return
    }
    navigate(target.start) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Which tab a destination belongs to. The route types do not say on their own: the Today and
 * Ranking tabs both start on a [DailyRoute].
 */
private fun NavDestination.tab(): TopLevel? = when {
    hasRoute<DailyRoute.Leaderboard>() -> TopLevel.Ranking
    hasRoute<DailyRoute.Home>() || hasRoute<DailyRoute.Lobby>() -> TopLevel.Today
    hasRoute<QuizRoute.Topics>() || hasRoute<QuizRoute.Question>() ||
        hasRoute<QuizRoute.Feedback>() || hasRoute<QuizRoute.Summary>() -> TopLevel.Test
    hasRoute<ProfileRoute.Profile>() || hasRoute<ProfileRoute.Settings>() ||
        hasRoute<ProfileRoute.Packs>() || hasRoute<ProfileRoute.Paywall>() -> TopLevel.Profile
    hasRoute<AtlasRoute.Browse>() || hasRoute<AtlasRoute.Search>() || hasRoute<AtlasRoute.Layers>() ||
        hasRoute<AtlasRoute.Tree>() || hasRoute<AtlasRoute.Detail>() -> TopLevel.Atlas
    else -> null
}

@Composable
private fun NotBuilt(tab: StringResource) {
    Centered(stringResource(Res.string.not_built_yet, stringResource(tab)))
}

@Composable
private fun Centered(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
