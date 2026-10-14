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
import anatomypro.shared.feature_settings.generated.resources.settings_account_subtitle
import anatomypro.shared.feature_settings.generated.resources.settings_account_title
import anatomypro.shared.feature_settings.generated.resources.settings_examination_subtitle
import anatomypro.shared.feature_settings.generated.resources.settings_group_account
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import anatomypro.shared.feature_settings.generated.resources.settings_back
import com.ptk.anatomypro.core.designsystem.LightTextSecondary
import com.ptk.anatomypro.core.designsystem.AccentOnLight
import com.ptk.anatomypro.core.designsystem.AccentOnLightSurface
import com.ptk.anatomypro.core.designsystem.LightGround
import com.ptk.anatomypro.core.designsystem.LightHairline
import com.ptk.anatomypro.core.designsystem.LightRule
import com.ptk.anatomypro.core.designsystem.LightSurface
import com.ptk.anatomypro.core.designsystem.LightTextPrimary
import com.ptk.anatomypro.core.designsystem.LightTextTertiary
import com.ptk.anatomypro.core.designsystem.LocalAnatomyType
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
    /** Set where settings were opened from another screen; null where they are a tab's first. */
    onBack: (() -> Unit)? = null,
    /** Opens the account. Null where the build has none, and then nothing is said about one. */
    onAccount: (() -> Unit)? = null,
) {
    val type = LocalAnatomyType.current
    // The other of the prototype's two light screens.
    if (state.isLoading) {
        Text(
            stringResource(Res.string.settings_loading),
            style = type.body,
            color = LightTextTertiary,
            modifier = modifier.fillMaxSize().background(LightGround).padding(20.dp),
        )
        return
    }
    val settings = state.settings
    Column(
        modifier = modifier.fillMaxSize().background(LightGround).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                val back = stringResource(Res.string.settings_back)
                Box(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clickable(role = Role.Button, onClick = onBack)
                        .semantics { contentDescription = back }
                        .padding(end = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text("‹", style = type.title, color = LightTextSecondary, modifier = Modifier.clearAndSetSemantics { })
                }
            }
            Text(stringResource(Res.string.settings_title), style = type.heading.copy(fontSize = 19.sp), color = LightTextPrimary)
        }

        Group(stringResource(Res.string.settings_group_languages)) {
            Card {
                Choice(
                    title = stringResource(Res.string.settings_interface_title),
                    subtitle = stringResource(Res.string.settings_interface_subtitle),
                    options = state.interfaceOptions,
                    selected = settings.interfaceLocale,
                    onSelect = onInterfaceLocale,
                )
                HorizontalDivider(color = LightHairline, thickness = 1.dp)
                Choice(
                    title = stringResource(Res.string.settings_examination_title),
                    subtitle = stringResource(Res.string.settings_examination_subtitle),
                    options = state.examinationOptions,
                    selected = settings.examinationLocale,
                    onSelect = onExaminationLocale,
                )
            }
        }

        Group(stringResource(Res.string.settings_group_names)) {
            Card {
                Row(modifier = Modifier.padding(14.dp).height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (display in NameDisplay.entries) {
                        Chip(display.label(), selected = settings.nameDisplay == display, modifier = Modifier.weight(1f).fillMaxHeight()) { onNameDisplay(display) }
                    }
                }
            }
        }

        Group(stringResource(Res.string.settings_group_accessibility)) {
            Card {
                Toggle(
                    title = stringResource(Res.string.settings_timer_title),
                    subtitle = stringResource(
                        if (settings.quizTimerEnabled) Res.string.settings_timer_on else Res.string.settings_timer_off,
                    ),
                    checked = settings.quizTimerEnabled,
                    onChange = onQuizTimer,
                )
                HorizontalDivider(color = LightHairline, thickness = 1.dp)
                Toggle(
                    title = stringResource(Res.string.settings_tree_title),
                    subtitle = stringResource(Res.string.settings_tree_subtitle),
                    checked = settings.structureTreeMode,
                    onChange = onStructureTreeMode,
                )
                HorizontalDivider(color = LightHairline, thickness = 1.dp)
                Toggle(
                    title = stringResource(Res.string.settings_patterns_title),
                    subtitle = stringResource(Res.string.settings_patterns_subtitle),
                    checked = settings.patternsNotColour,
                    onChange = onPatternsNotColour,
                )
            }
        }

        // Not in the prototype: the account came after it. Here too, because a build with no
        // profile home opens settings in its place, and the account would be out of reach.
        if (onAccount != null) {
            Group(stringResource(Res.string.settings_group_account)) {
                Card {
                    Link(
                        title = stringResource(Res.string.settings_account_title),
                        subtitle = stringResource(Res.string.settings_account_subtitle),
                        onClick = onAccount,
                    )
                }
            }
        }
    }
}

/** A row that opens another screen. */
@Composable
private fun Link(title: String, subtitle: String, onClick: () -> Unit) {
    val type = LocalAnatomyType.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = type.bodyStrong, color = LightTextPrimary)
            Text(subtitle, style = type.note, color = LightTextTertiary)
        }
        Text("›", style = type.button, color = LightTextTertiary, modifier = Modifier.clearAndSetSemantics { })
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
        Text(label, style = LocalAnatomyType.current.label, color = LightTextTertiary)
        content()
    }
}

/** A white card on the light ground. */
@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier.fillMaxWidth().clip(shape).background(LightSurface).border(BorderStroke(1.dp, LightHairline), shape),
        content = content,
    )
}

/**
 * One of a few, as a chip. The chosen one is filled and ticked: the tick is what says so
 * without colour (§12).
 */
@Composable
private fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onSelect: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(AccentOnLight) else Modifier.border(BorderStroke(1.dp, LightRule), shape))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (selected) "✓ $text" else text,
            style = LocalAnatomyType.current.caption.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (selected) AccentOnLightSurface else LightTextPrimary,
            textAlign = TextAlign.Center,
        )
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
    val type = LocalAnatomyType.current
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = type.bodyStrong, color = LightTextPrimary)
        Text(subtitle, style = type.note, color = LightTextTertiary)
        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (option in options) {
                Chip(option.name, selected = option.code == selected) { onSelect(option.code) }
            }
        }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val type = LocalAnatomyType.current
    // The whole row is the switch, so a screen reader hears its text as the label.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = type.bodyStrong, color = LightTextPrimary)
            Text(subtitle, style = type.note, color = LightTextTertiary)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = AccentOnLight,
                checkedThumbColor = LightSurface,
                checkedBorderColor = AccentOnLight,
                uncheckedTrackColor = LightHairline,
                uncheckedThumbColor = LightSurface,
                uncheckedBorderColor = LightRule,
            ),
        )
    }
}
