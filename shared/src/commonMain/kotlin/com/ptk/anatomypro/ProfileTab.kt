package com.ptk.anatomypro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.ptk.anatomypro.core.data.AppDependencies
import com.ptk.anatomypro.core.data.model.Entitlements
import com.ptk.anatomypro.feature.commerce.PaywallScreen
import com.ptk.anatomypro.feature.commerce.PaywallViewModel
import com.ptk.anatomypro.feature.commerce.commerceIsBuilt
import com.ptk.anatomypro.feature.profile.AccountScreen
import com.ptk.anatomypro.feature.profile.AccountViewModel
import com.ptk.anatomypro.feature.profile.accountIsBuilt
import com.ptk.anatomypro.feature.profile.PacksScreen
import com.ptk.anatomypro.feature.profile.PacksViewModel
import com.ptk.anatomypro.feature.profile.ProfileScreen
import com.ptk.anatomypro.feature.profile.ProfileViewModel
import com.ptk.anatomypro.navigation.ProfileRoute
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Prototype screen 17. */
@Composable
internal fun ProfileHomeRoute(dependencies: AppDependencies, locale: String, navController: NavHostController) {
    // Keyed on the locale: system titles are loaded once per model.
    val model: ProfileViewModel = viewModel(key = "profile-$locale") {
        ProfileViewModel(
            progress = dependencies.progress,
            packs = dependencies.packs,
            locale = locale,
            // The user's own day, not UTC: the calendar is their habit (all-screens spec §4.2).
            today = { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date },
        )
    }
    val state by model.state.collectAsState()
    // The model outlives a tab switch; a session finished meanwhile is not in its calendar.
    LaunchedEffect(model) { model.refresh() }

    // Read here rather than in the ViewModel: it is one flag, and only where there is
    // something to subscribe to.
    val subscribed = if (commerceIsBuilt(dependencies.entitlements)) {
        val held by dependencies.entitlements.entitlements
            .collectAsState(initial = Entitlements(subscribed = false, ownedSystems = emptySet()))
        held.subscribed
    } else {
        false
    }

    ProfileScreen(
        state = state,
        subscribed = subscribed,
        onRetry = model::onRetry,
        onSettings = { navController.navigate(ProfileRoute.Settings) },
        onPacks = { navController.navigate(ProfileRoute.Packs) },
        onSubscription = { navController.navigate(ProfileRoute.Paywall) },
        onAccount = if (accountIsBuilt(dependencies.account)) {
            { navController.navigate(ProfileRoute.Account) }
        } else {
            null
        },
    )
}

/** The account. The prototype has no screen for it; this one is a draft (backend spec §5). */
@Composable
internal fun AccountRoute(dependencies: AppDependencies, navController: NavHostController) {
    val model: AccountViewModel = viewModel {
        AccountViewModel(
            accounts = dependencies.account,
            signIn = dependencies.signIn,
            now = { Clock.System.now() },
            // The day a lock ends is said in the user's own days.
            zone = TimeZone.currentSystemDefault(),
        )
    }
    val state by model.state.collectAsState()

    AccountScreen(
        state = state,
        onNicknameChanged = model::onNicknameChanged,
        onSaveNickname = model::onSaveNickname,
        onSignIn = model::onSignIn,
        onLeaveRequested = model::onLeaveRequested,
        onLeaveConfirmed = model::onLeaveConfirmed,
        onLeaveDismissed = model::onLeaveDismissed,
        onRetry = model::onRetry,
        onBack = { navController.popBackStack() },
    )
}

/** Prototype screen 19. */
@Composable
internal fun PacksRoute(dependencies: AppDependencies, navController: NavHostController) {
    val model: PacksViewModel = viewModel { PacksViewModel(dependencies.packs) }
    val state by model.state.collectAsState()

    PacksScreen(
        state = state,
        onDownload = model::onDownload,
        onCancel = model::onCancel,
        onDeleteRequested = model::onDeleteRequested,
        onDeleteConfirmed = model::onDeleteConfirmed,
        onDeleteDismissed = model::onDeleteDismissed,
        onBack = { navController.popBackStack() },
    )
}

/** Prototype screen 18. */
@Composable
internal fun PaywallRoute(dependencies: AppDependencies, navController: NavHostController) {
    val model: PaywallViewModel = viewModel { PaywallViewModel(dependencies.entitlements) }
    val state by model.state.collectAsState()

    PaywallScreen(
        state = state,
        onPurchase = model::onPurchase,
        onRestore = model::onRestore,
        onRetry = model::onRetry,
        onBack = { navController.popBackStack() },
    )
}
