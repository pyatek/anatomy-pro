package com.ptk.anatomypro.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import anatomypro.shared.feature_settings.generated.resources.Res
import anatomypro.shared.feature_settings.generated.resources.language_body
import anatomypro.shared.feature_settings.generated.resources.language_continue
import anatomypro.shared.feature_settings.generated.resources.language_examination_label
import anatomypro.shared.feature_settings.generated.resources.language_interface_label
import anatomypro.shared.feature_settings.generated.resources.language_selected
import anatomypro.shared.feature_settings.generated.resources.language_step
import anatomypro.shared.feature_settings.generated.resources.language_summary
import anatomypro.shared.feature_settings.generated.resources.language_title
import anatomypro.shared.feature_settings.generated.resources.locale_note_examination_en
import anatomypro.shared.feature_settings.generated.resources.locale_note_examination_la
import anatomypro.shared.feature_settings.generated.resources.locale_note_interface_en
import anatomypro.shared.feature_settings.generated.resources.locale_note_interface_pl
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.TextTertiary
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Prototype screen 01: the first-run choice.
 *
 * Two separate pickers rather than one, and the explanation above them, because the whole
 * point of the screen is that the two languages are independent — a user reading Polish
 * menus while being examined in Latin is the expected case, not an edge one (spec §13).
 */
@Composable
fun LanguageSelectionScreen(
    state: SettingsUiState,
    onInterfaceLocale: (String) -> Unit,
    onExaminationLocale: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(stringResource(Res.string.language_step), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        Text(
            stringResource(Res.string.language_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(Res.string.language_body),
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary,
        )

        Picker(
            label = stringResource(Res.string.language_interface_label),
            options = state.interfaceOptions,
            noteOf = ::interfaceNote,
            selected = settings.interfaceLocale,
            onSelect = onInterfaceLocale,
        )
        Picker(
            label = stringResource(Res.string.language_examination_label),
            options = state.examinationOptions,
            noteOf = ::examinationNote,
            selected = settings.examinationLocale,
            onSelect = onExaminationLocale,
        )

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Bottom) {
            Text(
                text = stringResource(
                    Res.string.language_summary,
                    settings.interfaceLocale.uppercase(),
                    settings.examinationLocale.uppercase(),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.language_continue))
            }
        }
    }
}

@Composable
private fun Picker(
    label: String,
    options: List<LocaleOption>,
    noteOf: (String) -> StringResource?,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        for (option in options) {
            val isSelected = option.code == selected
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable { onSelect(option.code) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(option.name, style = MaterialTheme.typography.bodyMedium)
                    noteOf(option.code)?.let { note ->
                        Text(stringResource(note), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    }
                }
                if (isSelected) {
                    Text(stringResource(Res.string.language_selected), style = MaterialTheme.typography.labelSmall, color = Accent)
                }
            }
        }
    }
}

/** What an interface language is, in the interface language: "Polski" is "Polish" in English. */
private fun interfaceNote(code: String): StringResource? = when (code) {
    "pl" -> Res.string.locale_note_interface_pl
    "en" -> Res.string.locale_note_interface_en
    else -> null
}

/** What examining in a language means, in the interface language. */
private fun examinationNote(code: String): StringResource? = when (code) {
    "la" -> Res.string.locale_note_examination_la
    "en" -> Res.string.locale_note_examination_en
    else -> null
}
