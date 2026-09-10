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
import com.ptk.anatomypro.core.data.model.NameDisplay
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.TextTertiary

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
        Text("Wczytywanie…", modifier = modifier.padding(16.dp))
        return
    }
    val settings = state.settings

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Text("Ustawienia", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

        Group("JĘZYKI · NIEZALEŻNE") {
            Choice(
                title = "Język interfejsu",
                subtitle = "Menu, przyciski, opisy",
                options = state.interfaceOptions,
                selected = settings.interfaceLocale,
                onSelect = onInterfaceLocale,
            )
            Choice(
                title = "Język egzaminowania",
                subtitle = "Pytania i odpowiedzi w testach",
                options = state.examinationOptions,
                selected = settings.examinationLocale,
                onSelect = onExaminationLocale,
            )
        }

        Group("WYŚWIETLANIE NAZW") {
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

        Group("DOSTĘPNOŚĆ") {
            Toggle(
                title = "Limit czasu w testach",
                subtitle = if (settings.quizTimerEnabled) "Włączony" else "Wyłączony · testy bez zegara",
                checked = settings.quizTimerEnabled,
                onChange = onQuizTimer,
            )
            Toggle(
                title = "Tryb drzewa struktur",
                subtitle = "Lista zamiast modelu 3D",
                checked = settings.structureTreeMode,
                onChange = onStructureTreeMode,
            )
            Toggle(
                title = "Wzory zamiast kolorów",
                subtitle = "Kreskowanie i etykiety na modelu",
                checked = settings.patternsNotColour,
                onChange = onPatternsNotColour,
            )
        }
    }
}

private fun NameDisplay.label() = when (this) {
    NameDisplay.LatinAndDisplay -> "Łacina + język interfejsu"
    NameDisplay.LatinOnly -> "Tylko łacina"
    NameDisplay.All -> "Wszystkie"
}

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
