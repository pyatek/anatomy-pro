package com.ptk.anatomypro.feature.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import anatomypro.shared.feature_onboarding.generated.resources.Res
import anatomypro.shared.feature_onboarding.generated.resources.goals_body
import anatomypro.shared.feature_onboarding.generated.resources.goals_chosen
import anatomypro.shared.feature_onboarding.generated.resources.goals_continue
import anatomypro.shared.feature_onboarding.generated.resources.goals_count
import anatomypro.shared.feature_onboarding.generated.resources.goals_empty
import anatomypro.shared.feature_onboarding.generated.resources.goals_loading
import anatomypro.shared.feature_onboarding.generated.resources.goals_skip
import anatomypro.shared.feature_onboarding.generated.resources.goals_title
import anatomypro.shared.feature_onboarding.generated.resources.onboarding_back
import anatomypro.shared.feature_onboarding.generated.resources.onboarding_step
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.TextTertiary
import com.ptk.anatomypro.core.model.SystemId
import com.ptk.anatomypro.feature.atlas.layers.systemNames
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Prototype screen 02: goal setting.
 *
 * A chosen row says CHOSEN in words as well as in colour (§12), and the button changes its
 * wording rather than disabling: passing the step with nothing chosen is allowed.
 */
@Composable
fun GoalsScreen(
    state: GoalsUiState,
    step: Int,
    stepCount: Int,
    onToggle: (SystemId) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Counted against what is offered, so a goal kept for a pack that is gone is not announced.
    val chosen = state.systems.count { it.value in state.selected }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.onboarding_step, step, stepCount),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
            Text(
                stringResource(Res.string.onboarding_back),
                style = MaterialTheme.typography.labelSmall,
                color = Accent,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(start = 16.dp, top = 14.dp),
            )
        }
        Text(
            stringResource(Res.string.goals_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(stringResource(Res.string.goals_body), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when {
                state.isLoading ->
                    Text(stringResource(Res.string.goals_loading), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)

                state.systems.isEmpty() ->
                    Text(stringResource(Res.string.goals_empty), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)

                else -> for (system in state.systems) {
                    val isChosen = system.value in state.selected
                    val names = systemNames(system)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .toggleable(value = isChosen, role = Role.Checkbox, onValueChange = { onToggle(system) }),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(names.local, style = MaterialTheme.typography.bodyMedium)
                            Text(names.latin, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                        }
                        if (isChosen) {
                            Text(stringResource(Res.string.goals_chosen), style = MaterialTheme.typography.labelSmall, color = Accent)
                        }
                    }
                }
            }
        }

        Column {
            if (chosen > 0) {
                Text(
                    pluralStringResource(Res.plurals.goals_count, chosen, chosen),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(if (chosen > 0) Res.string.goals_continue else Res.string.goals_skip))
            }
        }
    }
}
