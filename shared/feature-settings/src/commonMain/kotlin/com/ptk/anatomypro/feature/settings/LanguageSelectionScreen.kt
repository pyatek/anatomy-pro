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
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.TextTertiary

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
        Text("KROK 1 Z 3", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        Text(
            "Dwa języki, ustawiane osobno",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Czytaj interfejs w jednym języku, a egzaminuj się w innym. " +
                "Oba można zmienić w Ustawieniach.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary,
        )

        Picker(
            label = "JĘZYK INTERFEJSU",
            options = state.interfaceOptions,
            selected = settings.interfaceLocale,
            onSelect = onInterfaceLocale,
        )
        Picker(
            label = "JĘZYK EGZAMINOWANIA",
            options = state.examinationOptions,
            selected = settings.examinationLocale,
            onSelect = onExaminationLocale,
        )

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Bottom) {
            Text(
                text = "Interfejs ${settings.interfaceLocale.uppercase()} · " +
                    "Egzamin ${settings.examinationLocale.uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Dalej")
            }
        }
    }
}

@Composable
private fun Picker(
    label: String,
    options: List<LocaleOption>,
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
                    Text(option.note, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                }
                if (isSelected) {
                    Text("WYBRANE", style = MaterialTheme.typography.labelSmall, color = Accent)
                }
            }
        }
    }
}
