package com.ptk.anatomypro

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
 */
@Composable
internal fun OnboardingFlow(
    dependencies: AppDependencies,
    state: SettingsUiState,
    model: SettingsViewModel,
    navController: NavHostController,
) {
    val hasDownload = remember(dependencies.packs) { onboardingHasDownloadStep(dependencies.packs) }
    val stepCount = if (hasDownload) 3 else 2

    NavHost(navController = navController, startDestination = OnboardingRoute.Language) {
        composable<OnboardingRoute.Language> {
            LanguageSelectionScreen(
                state = state,
                stepCount = stepCount,
                onInterfaceLocale = model::onInterfaceLocale,
                onExaminationLocale = model::onExaminationLocale,
                onContinue = { navController.navigate(OnboardingRoute.Goals) },
            )
        }

        composable<OnboardingRoute.Goals> {
            val onContinue: () -> Unit = {
                if (hasDownload) navController.navigate(OnboardingRoute.FirstDownload) else model.onOnboardingComplete()
            }
            val atlas = dependencies.atlas
            if (atlas == null) {
                // The bundled pack is still installing. The step is shown, and can be passed.
                GoalsScreen(
                    state = GoalsUiState(),
                    step = 2,
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
                    step = 2,
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
                step = 3,
                stepCount = stepCount,
                onRetry = download::onRetry,
                onBack = { navController.popBackStack() },
                onFinish = model::onOnboardingComplete,
            )
        }
    }
}
