package com.ptk.anatomypro.feature.commerce

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import anatomypro.shared.feature_commerce.generated.resources.Res
import anatomypro.shared.feature_commerce.generated.resources.paywall_back
import anatomypro.shared.feature_commerce.generated.resources.paywall_benefit_board
import anatomypro.shared.feature_commerce.generated.resources.paywall_benefit_daily
import anatomypro.shared.feature_commerce.generated.resources.paywall_benefit_topics
import anatomypro.shared.feature_commerce.generated.resources.paywall_body
import anatomypro.shared.feature_commerce.generated.resources.paywall_buying
import anatomypro.shared.feature_commerce.generated.resources.paywall_loading
import anatomypro.shared.feature_commerce.generated.resources.paywall_notice_nothing_to_restore
import anatomypro.shared.feature_commerce.generated.resources.paywall_notice_plans_failed
import anatomypro.shared.feature_commerce.generated.resources.paywall_notice_purchase_failed
import anatomypro.shared.feature_commerce.generated.resources.paywall_plan
import anatomypro.shared.feature_commerce.generated.resources.paywall_restore
import anatomypro.shared.feature_commerce.generated.resources.paywall_restoring
import anatomypro.shared.feature_commerce.generated.resources.paywall_retry
import anatomypro.shared.feature_commerce.generated.resources.paywall_subscribed_body
import anatomypro.shared.feature_commerce.generated.resources.paywall_subscribed_title
import anatomypro.shared.feature_commerce.generated.resources.paywall_title
import com.ptk.anatomypro.core.data.model.SubscriptionPlan
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.IncorrectAmber
import com.ptk.anatomypro.core.designsystem.TextTertiary
import org.jetbrains.compose.resources.stringResource

/**
 * Prototype screen 18: the paywall.
 *
 * It opens by saying what is free, because that is what makes the rest honest: the atlas is
 * not for sale and nothing here may read as if it were (design spec §29).
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
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(Res.string.paywall_back),
            style = MaterialTheme.typography.labelSmall,
            color = Accent,
            modifier = Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onBack).padding(top = 14.dp),
        )
        Text(stringResource(Res.string.paywall_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

        if (state.subscribed) {
            Text(stringResource(Res.string.paywall_subscribed_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(Res.string.paywall_subscribed_body), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
            return@Column
        }

        Text(stringResource(Res.string.paywall_body), style = MaterialTheme.typography.bodyMedium)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("·  " + stringResource(Res.string.paywall_benefit_topics), style = MaterialTheme.typography.bodyMedium)
            Text("·  " + stringResource(Res.string.paywall_benefit_daily), style = MaterialTheme.typography.bodyMedium)
            Text("·  " + stringResource(Res.string.paywall_benefit_board), style = MaterialTheme.typography.bodyMedium)
        }

        // Announced as it arrives: a failed purchase must not be something only sighted users learn.
        Column(modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
            when (state.notice) {
                PaywallNotice.PURCHASE_FAILED ->
                    Text(stringResource(Res.string.paywall_notice_purchase_failed), style = MaterialTheme.typography.bodyMedium, color = IncorrectAmber)
                PaywallNotice.NOTHING_TO_RESTORE ->
                    Text(stringResource(Res.string.paywall_notice_nothing_to_restore), style = MaterialTheme.typography.bodyMedium, color = IncorrectAmber)
                PaywallNotice.PLANS_FAILED ->
                    Text(stringResource(Res.string.paywall_notice_plans_failed), style = MaterialTheme.typography.bodyMedium, color = IncorrectAmber)
                null -> Unit
            }
        }

        when {
            state.isLoading ->
                Text(stringResource(Res.string.paywall_loading), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)

            state.notice == PaywallNotice.PLANS_FAILED ->
                OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(Res.string.paywall_retry))
                }

            else -> for (plan in state.plans) {
                Button(
                    onClick = { onPurchase(plan) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(
                        if (state.purchasing == plan.id) stringResource(Res.string.paywall_buying)
                        else stringResource(Res.string.paywall_plan, plan.title, plan.priceLabel)
                    )
                }
            }
        }

        OutlinedButton(onClick = onRestore, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(if (state.restoring) Res.string.paywall_restoring else Res.string.paywall_restore))
        }
    }
}
