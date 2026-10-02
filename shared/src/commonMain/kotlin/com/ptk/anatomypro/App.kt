package com.ptk.anatomypro

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ptk.anatomypro.core.data.AppDependencies
import com.ptk.anatomypro.core.data.model.NameDisplay
import com.ptk.anatomypro.core.designsystem.AnatomyTheme
import com.ptk.anatomypro.core.designsystem.ProvideAppLocale
import com.ptk.anatomypro.feature.settings.LanguageSelectionScreen
import com.ptk.anatomypro.feature.settings.SettingsScreen
import com.ptk.anatomypro.feature.settings.SettingsUiState
import com.ptk.anatomypro.feature.settings.SettingsViewModel
import com.ptk.anatomypro.navigation.AnatomyBottomBar
import com.ptk.anatomypro.navigation.AnatomyDestination

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

                        else -> MainScaffold(settingsState, settingsModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun MainScaffold(state: SettingsUiState, model: SettingsViewModel) {
    var destination by rememberSaveable { mutableStateOf(AnatomyDestination.Atlas) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (destination) {
                AnatomyDestination.Atlas -> AtlasTab(
                    locale = state.settings.interfaceLocale,
                    latinOnly = state.settings.nameDisplay == NameDisplay.LatinOnly,
                )

                // Profile is not built. Settings live behind it in the prototype, so the
                // tab shows settings rather than a second placeholder.
                AnatomyDestination.Profile -> SettingsScreen(
                    state = state,
                    onInterfaceLocale = model::onInterfaceLocale,
                    onExaminationLocale = model::onExaminationLocale,
                    onNameDisplay = model::onNameDisplay,
                    onQuizTimer = model::onQuizTimer,
                    onStructureTreeMode = model::onStructureTreeMode,
                    onPatternsNotColour = model::onPatternsNotColour,
                )

                else -> Centered("${destination.label} — jeszcze nie zbudowane")
            }
        }
        AnatomyBottomBar(selected = destination, onSelect = { destination = it })
    }
}

@Composable
private fun Centered(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
