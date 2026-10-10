package com.ptk.anatomypro.feature.onboarding

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
import anatomypro.shared.feature_onboarding.generated.resources.Res
import anatomypro.shared.feature_onboarding.generated.resources.goals_body
import anatomypro.shared.feature_onboarding.generated.resources.goals_continue
import anatomypro.shared.feature_onboarding.generated.resources.goals_count
import anatomypro.shared.feature_onboarding.generated.resources.goals_empty
import anatomypro.shared.feature_onboarding.generated.resources.goals_free
import anatomypro.shared.feature_onboarding.generated.resources.goals_loading
import anatomypro.shared.feature_onboarding.generated.resources.goals_skip
import anatomypro.shared.feature_onboarding.generated.resources.goals_title
import anatomypro.shared.feature_onboarding.generated.resources.onboarding_back
import anatomypro.shared.feature_onboarding.generated.resources.onboarding_step
import com.ptk.anatomypro.core.data.model.FREE_SYSTEMS
import com.ptk.anatomypro.core.designsystem.Badge
import com.ptk.anatomypro.core.designsystem.ChoiceCard
import com.ptk.anatomypro.core.designsystem.CorrectGreen
import com.ptk.anatomypro.core.designsystem.FreeOutline
import com.ptk.anatomypro.core.designsystem.LocalAnatomyType
import com.ptk.anatomypro.core.designsystem.PrimaryButton
import com.ptk.anatomypro.core.designsystem.ScreenFooter
import com.ptk.anatomypro.core.designsystem.StepHeader
import com.ptk.anatomypro.core.designsystem.TextPrimary
import com.ptk.anatomypro.core.designsystem.TextSecondary
import com.ptk.anatomypro.core.designsystem.TextTertiary
import com.ptk.anatomypro.core.model.SystemId
import com.ptk.anatomypro.feature.atlas.layers.systemNames
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Prototype screen 02: goal setting.
 *
 * Each system is a card with its Latin name leading and a tick box, as the prototype draws
 * it. A chosen card is ticked as well as outlined (§12), and the button changes its wording
 * rather than disabling: passing the step with nothing chosen is allowed.
 *
 * The prototype also gives each system a structure count and the footer a total. The atlas
 * does not report counts per system yet, so they are left out rather than made up.
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
    val type = LocalAnatomyType.current
    // Counted against what is offered, so a goal kept for a pack that is gone is not announced.
    val chosen = state.systems.count { it.value in state.selected }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StepHeader(
                label = stringResource(Res.string.onboarding_step, step, stepCount),
                step = step,
                stepCount = stepCount,
                backLabel = stringResource(Res.string.onboarding_back),
                onBack = onBack,
            )
            Text(stringResource(Res.string.goals_title), style = type.title, color = TextPrimary, modifier = Modifier.padding(top = 10.dp))
            Text(stringResource(Res.string.goals_body), style = type.body, color = TextSecondary)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                state.isLoading -> Text(stringResource(Res.string.goals_loading), style = type.body, color = TextTertiary)
                state.systems.isEmpty() -> Text(stringResource(Res.string.goals_empty), style = type.body, color = TextTertiary)
                else -> for (system in state.systems) {
                    val names = systemNames(system)
                    ChoiceCard(
                        selected = system.value in state.selected,
                        onToggle = { onToggle(system) },
                        title = names.latin,
                        subtitle = names.local,
                        trailing = {
                            // Its quizzes need no subscription (design §29); the atlas is free throughout.
                            if (system in FREE_SYSTEMS) {
                                Badge(stringResource(Res.string.goals_free), color = CorrectGreen, outline = FreeOutline)
                            }
                        },
                    )
                }
            }
        }

        ScreenFooter {
            if (chosen > 0) {
                Text(pluralStringResource(Res.plurals.goals_count, chosen, chosen), style = type.note, color = TextTertiary)
            }
            PrimaryButton(
                text = stringResource(if (chosen > 0) Res.string.goals_continue else Res.string.goals_skip),
                onClick = onContinue,
            )
        }
    }
}
