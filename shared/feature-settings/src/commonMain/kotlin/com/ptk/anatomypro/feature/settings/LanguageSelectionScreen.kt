package com.ptk.anatomypro.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import com.ptk.anatomypro.core.designsystem.LocalAnatomyType
import com.ptk.anatomypro.core.designsystem.Overline
import com.ptk.anatomypro.core.designsystem.PrimaryButton
import com.ptk.anatomypro.core.designsystem.RadioCard
import com.ptk.anatomypro.core.designsystem.ScreenFooter
import com.ptk.anatomypro.core.designsystem.StepHeader
import com.ptk.anatomypro.core.designsystem.TextPrimary
import com.ptk.anatomypro.core.designsystem.TextSecondary
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
    stepCount: Int,
    onInterfaceLocale: (String) -> Unit,
    onExaminationLocale: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = LocalAnatomyType.current
    val settings = state.settings

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StepHeader(label = stringResource(Res.string.language_step, stepCount), step = 1, stepCount = stepCount)
            Text(stringResource(Res.string.language_title), style = type.title, color = TextPrimary, modifier = Modifier.padding(top = 10.dp))
            Text(stringResource(Res.string.language_body), style = type.body, color = TextSecondary)

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
        }

        ScreenFooter {
            Text(
                stringResource(Res.string.language_summary, settings.interfaceLocale.uppercase(), settings.examinationLocale.uppercase()),
                style = type.note,
                color = TextTertiary,
            )
            PrimaryButton(stringResource(Res.string.language_continue), onClick = onContinue)
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
    Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Overline(label, color = TextSecondary, modifier = Modifier.padding(bottom = 2.dp))
        for (option in options) {
            RadioCard(
                selected = option.code == selected,
                onSelect = { onSelect(option.code) },
                title = option.name,
                subtitle = noteOf(option.code)?.let { stringResource(it) },
                selectedBadge = stringResource(Res.string.language_selected),
                // Latin leads in the serif wherever it is named.
                serifTitle = option.code == "la",
            )
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
