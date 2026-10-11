package com.ptk.anatomypro.feature.commerce

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptk.anatomypro.core.data.NotBuiltEntitlementRepository
import com.ptk.anatomypro.core.data.model.PurchaseOutcome
import com.ptk.anatomypro.core.data.model.SubscriptionPlan
import com.ptk.anatomypro.core.data.repository.EntitlementRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the paywall has to tell the user, as a kind. The screen supplies the words. */
enum class PaywallNotice { PURCHASE_FAILED, NOTHING_TO_RESTORE, RESTORE_FAILED, PLANS_FAILED }

data class PaywallUiState(
    val subscribed: Boolean = false,
    val plans: List<SubscriptionPlan> = emptyList(),
    val isLoading: Boolean = true,
    /** The id of the plan being bought, or null. */
    val purchasing: String? = null,
    val restoring: Boolean = false,
    val notice: PaywallNotice? = null,
    /**
     * The plans could not be loaded. Its own field, not only a notice: a notice is replaced
     * by the next thing that happens, and the way to try again must outlast it.
     */
    val plansFailed: Boolean = false,
) {
    /** A purchase or a restore is under way: nothing else may be started. */
    val busy: Boolean get() = purchasing != null || restoring
}

/**
 * Prototype screen 18: the paywall.
 *
 * What is sold is the learning system — quizzes beyond the skeleton, the daily quiz, the
 * leaderboard — and never content: the whole atlas is free (design spec §29).
 *
 * A repository's own failure message is not passed on. It is written for a log, in whatever
 * language the store uses; the screen says what happened in its own words.
 */
class PaywallViewModel(private val entitlements: EntitlementRepository) : ViewModel() {

    private val _state = MutableStateFlow(PaywallUiState())
    val state: StateFlow<PaywallUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            entitlements.entitlements.collect { current -> _state.update { it.copy(subscribed = current.subscribed) } }
        }
        loadPlans()
    }

    fun onPurchase(plan: SubscriptionPlan) {
        if (_state.value.busy || _state.value.subscribed) return
        _state.update { it.copy(purchasing = plan.id, notice = null) }
        viewModelScope.launch {
            val notice = try {
                when (entitlements.purchase(plan)) {
                    is PurchaseOutcome.Succeeded -> null
                    // The user closed the sheet. They know; saying so would be noise.
                    PurchaseOutcome.Cancelled -> null
                    is PurchaseOutcome.Failed -> PaywallNotice.PURCHASE_FAILED
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                PaywallNotice.PURCHASE_FAILED
            }
            _state.update { it.copy(purchasing = null, notice = notice) }
        }
    }

    fun onRestore() {
        if (_state.value.busy) return
        _state.update { it.copy(restoring = true, notice = null) }
        viewModelScope.launch {
            val notice = try {
                val outcome = entitlements.restore()
                val restored = outcome is PurchaseOutcome.Succeeded && outcome.entitlements.subscribed
                when {
                    restored -> null
                    // The store could not be asked. Not the same as having nothing: a
                    // subscriber told they bought nothing would believe it.
                    outcome is PurchaseOutcome.Failed -> PaywallNotice.RESTORE_FAILED
                    // A restore that finds nothing must say so, or it reads as a dead button.
                    else -> PaywallNotice.NOTHING_TO_RESTORE
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                PaywallNotice.RESTORE_FAILED
            }
            _state.update { it.copy(restoring = false, notice = notice) }
        }
    }

    fun onRetry() {
        if (_state.value.isLoading) return
        loadPlans()
    }

    private fun loadPlans() {
        _state.update { it.copy(isLoading = true, notice = null, plansFailed = false) }
        viewModelScope.launch {
            try {
                val plans = entitlements.plans()
                _state.update { it.copy(plans = plans, isLoading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, notice = PaywallNotice.PLANS_FAILED, plansFailed = true) }
            }
        }
    }
}

/**
 * Whether there is anything to buy.
 *
 * Production has no EntitlementRepository yet; the one it is given throws on first use, by
 * design (all-screens spec §6) — a release build must never present a purchase that cannot
 * happen. There the paywall is a placeholder.
 */
fun commerceIsBuilt(entitlements: EntitlementRepository): Boolean = entitlements !is NotBuiltEntitlementRepository
