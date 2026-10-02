package com.ptk.anatomypro.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import anatomypro.shared.feature_settings.generated.resources.Res
import anatomypro.shared.feature_settings.generated.resources.settings_examination_subtitle
import anatomypro.shared.feature_settings.generated.resources.settings_examination_title
import anatomypro.shared.feature_settings.generated.resources.settings_group_accessibility
import anatomypro.shared.feature_settings.generated.resources.settings_group_languages
import anatomypro.shared.feature_settings.generated.resources.settings_group_names
import anatomypro.shared.feature_settings.generated.resources.settings_interface_subtitle
import anatomypro.shared.feature_settings.generated.resources.settings_interface_title
import anatomypro.shared.feature_settings.generated.resources.settings_loading
import anatomypro.shared.feature_settings.generated.resources.settings_names_all
import anatomypro.shared.feature_settings.generated.resources.settings_names_latin_and_display
import anatomypro.shared.feature_settings.generated.resources.settings_names_latin_only
import anatomypro.shared.feature_settings.generated.resources.settings_patterns_subtitle
import anatomypro.shared.feature_settings.generated.resources.settings_patterns_title
import anatomypro.shared.feature_settings.generated.resources.settings_timer_off
import anatomypro.shared.feature_settings.generated.resources.settings_timer_on
import anatomypro.shared.feature_settings.generated.resources.settings_timer_title
import anatomypro.shared.feature_settings.generated.resources.settings_title
import anatomypro.shared.feature_settings.generated.resources.settings_tree_subtitle
import anatomypro.shared.feature_settings.generated.resources.settings_tree_title
import com.ptk.anatomypro.core.data.model.NameDisplay
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.TextTertiary
import org.jetbrains.compose.resources.stringResource

/** Prototype screen 20. */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onInterfaceLocale: (String) -> Unit,
    onExaminationLocale: (String) -> Unit,
    onNameDisplay: (NameDisplay) -> Unit,
    onQuizTimer: (Boolean) -> Unit,
    onStructureTreeMode: (Boolean) -> Unit,
    onPatternsNotColour: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        Text(stringResource(Res.string.settings_loading), modifier = modifier.padding(16.dp))
        return
    }
    val settings = state.settings

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Text(stringResource(Res.string.settings_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

        Group(stringResource(Res.string.settings_group_languages)) {
            Choice(
                title = stringResource(Res.string.settings_interface_title),
                subtitle = stringResource(Res.string.settings_interface_subtitle),
                options = state.interfaceOptions,
                selected = settings.interfaceLocale,
                onSelect = onInterfaceLocale,
            )
            Choice(
                title = stringResource(Res.string.settings_examination_title),
                subtitle = stringResource(Res.string.settings_examination_subtitle),
                options = state.examinationOptions,
                selected = settings.examinationLocale,
                onSelect = onExaminationLocale,
            )
        }

        Group(stringResource(Res.string.settings_group_names)) {
            for (display in NameDisplay.entries) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                        .clickable { onNameDisplay(display) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(display.label(), style = MaterialTheme.typography.bodyMedium)
                    if (settings.nameDisplay == display) {
                        Text("✓", color = Accent, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Group(stringResource(Res.string.settings_group_accessibility)) {
            Toggle(
                title = stringResource(Res.string.settings_timer_title),
                subtitle = stringResource(
                    if (settings.quizTimerEnabled) Res.string.settings_timer_on else Res.string.settings_timer_off,
                ),
                checked = settings.quizTimerEnabled,
                onChange = onQuizTimer,
            )
            Toggle(
                title = stringResource(Res.string.settings_tree_title),
                subtitle = stringResource(Res.string.settings_tree_subtitle),
                checked = settings.structureTreeMode,
                onChange = onStructureTreeMode,
            )
            Toggle(
                title = stringResource(Res.string.settings_patterns_title),
                subtitle = stringResource(Res.string.settings_patterns_subtitle),
                checked = settings.patternsNotColour,
                onChange = onPatternsNotColour,
            )
        }
    }
}

@Composable
private fun NameDisplay.label() = stringResource(
    when (this) {
        NameDisplay.LatinAndDisplay -> Res.string.settings_names_latin_and_display
        NameDisplay.LatinOnly -> Res.string.settings_names_latin_only
        NameDisplay.All -> Res.string.settings_names_all
    },
)

@Composable
private fun Group(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        content()
    }
}

@Composable
private fun Choice(
    title: String,
    subtitle: String,
    options: List<LocaleOption>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (option in options) {
                Text(
                    text = option.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (option.code == selected) Accent else TextTertiary,
                    modifier = Modifier.heightIn(min = 44.dp)
                        .clickable { onSelect(option.code) }.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
