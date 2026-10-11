package com.ptk.anatomypro

import com.ptk.anatomypro.feature.onboarding.stepAfter
import com.ptk.anatomypro.feature.onboarding.onboardingSteps
import com.ptk.anatomypro.feature.onboarding.OnboardingStep
import com.ptk.anatomypro.core.data.repository.PackRepository
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.ptk.anatomypro.core.data.AppDependencies
import com.ptk.anatomypro.feature.onboarding.FirstDownloadScreen
import com.ptk.anatomypro.feature.onboarding.FirstDownloadViewModel
import com.ptk.anatomypro.feature.onboarding.GoalsScreen
import com.ptk.anatomypro.feature.onboarding.GoalsUiState
import com.ptk.anatomypro.feature.onboarding.GoalsViewModel
import com.ptk.anatomypro.feature.onboarding.onboardingHasDownloadStep
import com.ptk.anatomypro.feature.settings.LanguageSelectionScreen
import com.ptk.anatomypro.feature.settings.SettingsUiState
import com.ptk.anatomypro.feature.settings.SettingsViewModel
import com.ptk.anatomypro.navigation.OnboardingRoute

/**
 * Prototype screens 01 to 03.
 *
 * [navController] is passed in because it must be remembered above ProvideAppLocale:
 * choosing a language on screen 01 rebuilds everything beneath it.
 *
 * Nothing here records which step was reached. Each choice is saved as it is made, so a
 * relaunch that starts again at step 1 shows every earlier answer already given.
 *
 * The order of the steps is [onboardingSteps]'s: where the download is what brings the
 * atlas ([atlasArrivesByDownload]) it comes before the goals.
 */
@Composable
internal fun OnboardingFlow(
    dependencies: AppDependencies,
    state: SettingsUiState,
    model: SettingsViewModel,
    navController: NavHostController,
    atlasArrivesByDownload: Boolean = false,
) {
    val steps = remember(dependencies.packs, atlasArrivesByDownload) {
        onboardingSteps(onboardingHasDownloadStep(dependencies.packs), atlasArrivesByDownload)
    }
    val stepCount = steps.size
    // Read when a step is left, not when this was composed: the atlas arrives while the
    // download step is on screen.
    val atlasInstalled by rememberUpdatedState(dependencies.atlas != null)
    val leave: (OnboardingStep) -> Unit = { step ->
        when (stepAfter(step, steps, atlasInstalled)) {
            OnboardingStep.LANGUAGE -> navController.navigate(OnboardingRoute.Language)
            OnboardingStep.GOALS -> navController.navigate(OnboardingRoute.Goals)
            OnboardingStep.DOWNLOAD -> navController.navigate(OnboardingRoute.FirstDownload)
            null -> model.onOnboardingComplete()
        }
    }

    NavHost(navController = navController, startDestination = OnboardingRoute.Language) {
        composable<OnboardingRoute.Language> {
            LanguageSelectionScreen(
                state = state,
                stepCount = stepCount,
                onInterfaceLocale = model::onInterfaceLocale,
                onExaminationLocale = model::onExaminationLocale,
                onContinue = { leave(OnboardingStep.LANGUAGE) },
            )
        }

        composable<OnboardingRoute.Goals> {
            val step = steps.indexOf(OnboardingStep.GOALS) + 1
            val onContinue: () -> Unit = { leave(OnboardingStep.GOALS) }
            val atlas = dependencies.atlas
            if (atlas == null) {
                // The bundled pack is still installing. The step is shown, and can be passed.
                GoalsScreen(
                    state = GoalsUiState(),
                    step = step,
                    stepCount = stepCount,
                    onToggle = {},
                    onBack = { navController.popBackStack() },
                    onContinue = onContinue,
                )
            } else {
                val goals: GoalsViewModel = viewModel(key = "goals") { GoalsViewModel(dependencies.settings, atlas) }
                val goalsState by goals.state.collectAsState()
                GoalsScreen(
                    state = goalsState,
                    step = step,
                    stepCount = stepCount,
                    onToggle = goals::onToggle,
                    onBack = { navController.popBackStack() },
                    onContinue = onContinue,
                )
            }
        }

        composable<OnboardingRoute.FirstDownload> {
            val download: FirstDownloadViewModel = viewModel { FirstDownloadViewModel(dependencies.packs) }
            val downloadState by download.state.collectAsState()
            FirstDownloadScreen(
                state = downloadState,
                step = steps.indexOf(OnboardingStep.DOWNLOAD) + 1,
                stepCount = stepCount,
                onRetry = download::onRetry,
                onBack = { navController.popBackStack() },
                onFinish = { leave(OnboardingStep.DOWNLOAD) },
                lastStep = steps.last() == OnboardingStep.DOWNLOAD,
            )
        }
    }
}

/**
 * The atlas tab while there is no pack: the download step's screen, outside onboarding.
 *
 * The pack is offered, not fetched: it may be absent because it was removed on purpose.
 * Nothing leads away from here, and nothing needs to — the atlas takes this screen's place
 * as soon as the pack is installed.
 */
@Composable
internal fun NoPackRoute(packs: PackRepository) {
    val download: FirstDownloadViewModel = viewModel(key = "no-pack") { FirstDownloadViewModel(packs, autoStart = false) }
    val downloadState by download.state.collectAsState()
    FirstDownloadScreen(
        state = downloadState,
        step = 1,
        stepCount = 1,
        onRetry = download::onRetry,
        onBack = {},
        onFinish = {},
        onStart = download::onStart,
        standalone = true,
    )
}
