package com.ptk.anatomypro.feature.commerce

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import anatomypro.shared.feature_commerce.generated.resources.Res
import anatomypro.shared.feature_commerce.generated.resources.paywall_added
import anatomypro.shared.feature_commerce.generated.resources.paywall_back
import anatomypro.shared.feature_commerce.generated.resources.paywall_benefit_board
import anatomypro.shared.feature_commerce.generated.resources.paywall_benefit_daily
import anatomypro.shared.feature_commerce.generated.resources.paywall_benefit_topics
import anatomypro.shared.feature_commerce.generated.resources.paywall_body
import anatomypro.shared.feature_commerce.generated.resources.paywall_buying
import anatomypro.shared.feature_commerce.generated.resources.paywall_headline
import anatomypro.shared.feature_commerce.generated.resources.paywall_loading
import anatomypro.shared.feature_commerce.generated.resources.paywall_notice_nothing_to_restore
import anatomypro.shared.feature_commerce.generated.resources.paywall_notice_plans_failed
import anatomypro.shared.feature_commerce.generated.resources.paywall_notice_purchase_failed
import anatomypro.shared.feature_commerce.generated.resources.paywall_notice_restore_failed
import anatomypro.shared.feature_commerce.generated.resources.paywall_plans
import anatomypro.shared.feature_commerce.generated.resources.paywall_restore
import anatomypro.shared.feature_commerce.generated.resources.paywall_restoring
import anatomypro.shared.feature_commerce.generated.resources.paywall_retry
import anatomypro.shared.feature_commerce.generated.resources.paywall_subscribed_body
import anatomypro.shared.feature_commerce.generated.resources.paywall_subscribed_title
import anatomypro.shared.feature_commerce.generated.resources.paywall_title
import com.ptk.anatomypro.core.data.model.SubscriptionPlan
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.AccentWash
import com.ptk.anatomypro.core.designsystem.Hairline
import com.ptk.anatomypro.core.designsystem.IncorrectAmber
import com.ptk.anatomypro.core.designsystem.LocalAnatomyType
import com.ptk.anatomypro.core.designsystem.NumberedLine
import com.ptk.anatomypro.core.designsystem.OutlineButton
import com.ptk.anatomypro.core.designsystem.Overline
import com.ptk.anatomypro.core.designsystem.Panel
import com.ptk.anatomypro.core.designsystem.QuietAction
import com.ptk.anatomypro.core.designsystem.ScreenFooter
import com.ptk.anatomypro.core.designsystem.Surface
import com.ptk.anatomypro.core.designsystem.TextPrimary
import com.ptk.anatomypro.core.designsystem.TextSecondary
import com.ptk.anatomypro.core.designsystem.TextTertiary
import com.ptk.anatomypro.core.designsystem.TopBar
import org.jetbrains.compose.resources.stringResource

/**
 * Prototype screen 18: the paywall.
 *
 * The prototype's shape — what stays free said first, what is added listed, the plans as
 * cards — with the words of the later decision: the subscription is the learning system,
 * never content (design §29). The prototype was drawn when packs were to be sold, and its
 * copy and its "four more systems" list are not used. Its trial button is not either:
 * trial length is still an open question, and a plan here is bought by pressing its card.
 */
@Composable
fun PaywallScreen(
    state: PaywallUiState,
    onPurchase: (SubscriptionPlan) -> Unit,
    onRestore: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = LocalAnatomyType.current
    Column(modifier = modifier.fillMaxSize()) {
        TopBar(stringResource(Res.string.paywall_title).uppercase(), backLabel = stringResource(Res.string.paywall_back), onBack = onBack)

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.subscribed) {
                Text(stringResource(Res.string.paywall_subscribed_title), style = type.title, color = TextPrimary)
                Text(stringResource(Res.string.paywall_subscribed_body), style = type.body, color = TextSecondary)
                return@Column
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(Res.string.paywall_headline), style = type.title, color = TextPrimary)
                Text(stringResource(Res.string.paywall_body), style = type.body, color = TextSecondary)
            }

            Panel(spacing = 9.dp) {
                Overline(stringResource(Res.string.paywall_added), color = TextSecondary)
                NumberedLine(1, stringResource(Res.string.paywall_benefit_topics))
                NumberedLine(2, stringResource(Res.string.paywall_benefit_daily))
                NumberedLine(3, stringResource(Res.string.paywall_benefit_board))
            }

            // Announced as it arrives: a failed purchase must not be something only sighted users learn.
            Column(modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                val notice = when (state.notice) {
                    PaywallNotice.PURCHASE_FAILED -> Res.string.paywall_notice_purchase_failed
                    PaywallNotice.NOTHING_TO_RESTORE -> Res.string.paywall_notice_nothing_to_restore
                    PaywallNotice.RESTORE_FAILED -> Res.string.paywall_notice_restore_failed
                    PaywallNotice.PLANS_FAILED -> Res.string.paywall_notice_plans_failed
                    null -> null
                }
                if (notice != null) Text(stringResource(notice), style = type.body, color = IncorrectAmber)
            }

            when {
                state.isLoading -> Text(stringResource(Res.string.paywall_loading), style = type.body, color = TextTertiary)
                state.plansFailed -> {
                    // Said here as well as in the notice above, which the next action replaces.
                    if (state.notice != PaywallNotice.PLANS_FAILED) {
                        Text(stringResource(Res.string.paywall_notice_plans_failed), style = type.body, color = TextTertiary)
                    }
                    OutlineButton(stringResource(Res.string.paywall_retry), onClick = onRetry)
                }
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Overline(stringResource(Res.string.paywall_plans), color = TextSecondary)
                    state.plans.forEachIndexed { index, plan ->
                        PlanCard(
                            plan = plan,
                            // The longer plan leads, as in the prototype.
                            leading = index == state.plans.indices.maxByOrNull { state.plans[it].periodMonths },
                            buying = state.purchasing == plan.id,
                            enabled = !state.busy,
                            onPurchase = { onPurchase(plan) },
                        )
                    }
                }
            }
        }

        if (!state.subscribed) {
            ScreenFooter {
                QuietAction(
                    stringResource(if (state.restoring) Res.string.paywall_restoring else Res.string.paywall_restore),
                    onClick = { if (!state.busy) onRestore() },
                )
            }
        }
    }
}

@Composable
private fun PlanCard(plan: SubscriptionPlan, leading: Boolean, buying: Boolean, enabled: Boolean, onPurchase: () -> Unit) {
    val type = LocalAnatomyType.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp)
            .clip(shape)
            .background(if (leading) AccentWash else Surface)
            .border(BorderStroke(1.dp, if (leading) Accent else Hairline), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onPurchase)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (buying) stringResource(Res.string.paywall_buying) else plan.title,
            style = type.button,
            color = if (enabled || buying) TextPrimary else TextTertiary,
            modifier = Modifier.weight(1f),
        )
        Text(plan.priceLabel, style = type.value.copy(fontSize = 15.sp), color = if (enabled) TextPrimary else TextTertiary)
    }
}
