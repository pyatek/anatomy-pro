package com.ptk.anatomypro.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptk.anatomypro.core.data.NotBuiltAccountRepository
import com.ptk.anatomypro.core.data.model.Account
import com.ptk.anatomypro.core.data.model.NICKNAME_MAX_LENGTH
import com.ptk.anatomypro.core.data.model.RenameOutcome
import com.ptk.anatomypro.core.data.model.SignInOutcome
import com.ptk.anatomypro.core.data.model.SignInProvider
import com.ptk.anatomypro.core.data.model.nicknameIsWellFormed
import com.ptk.anatomypro.core.data.repository.AccountRepository
import com.ptk.anatomypro.core.data.repository.ProviderSignIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** What became of the last nickname that was saved, as a kind. The screen supplies the words. */
enum class NicknameNotice { SAVED, INVALID, TAKEN, LOCKED, FAILED }

/** What became of the last sign-in. [MERGED]: this device now speaks for another account (§5.3). */
enum class SignInNotice { SIGNED_IN, MERGED, REJECTED, FAILED }

/** The two ways out of an account. Both are asked about before they happen. */
enum class Leaving { SIGN_OUT, DELETE }

/** The account this screen was showing is no longer this device's. */
sealed interface Left {
    data object SignedOut : Left
    data class Deleted(val subscriptionStillActive: Boolean) : Left
}

data class AccountUiState(
    val account: Account? = null,
    /** The nickname as it stands in the field. */
    val draft: String = "",
    /** The first day a new nickname may be chosen, in the user's own days; null where there is no lock. */
    val lockedUntil: LocalDate? = null,
    /** What this device can sign in with. */
    val offered: List<SignInProvider> = emptyList(),
    val isLoading: Boolean = true,
    val failed: Boolean = false,
    /** A request is on its way: nothing else may be started. */
    val busy: Boolean = false,
    val nicknameNotice: NicknameNotice? = null,
    val signInNotice: SignInNotice? = null,
    /** The way out that is waiting for a yes or a no. */
    val confirming: Leaving? = null,
    /** The way out that was said yes to did not get through. The question stands. */
    val leavingFailed: Boolean = false,
    val left: Left? = null,
) {
    /** The providers to list: those this device can use, and those that already open the account. */
    val providers: List<SignInProvider>
        get() = SignInProvider.entries.filter { it in offered || account?.linked?.contains(it) == true }

    val canSave: Boolean
        get() = !busy && left == null && lockedUntil == null && account != null && draft.isNotEmpty() && draft != account.nickname

    /**
     * Only an account someone signed in to can be signed out of. An anonymous one would be
     * deleted by it — nothing could open it again — and that is what deleting is for.
     */
    val canSignOut: Boolean get() = account?.linked?.isNotEmpty() == true

    fun canSignInWith(provider: SignInProvider): Boolean =
        provider in offered && account?.linked?.contains(provider) == false
}

/**
 * The account: its nickname, signing in, signing out and deleting it (backend spec §5).
 *
 * The prototype has no screen for this. What is here was drafted for the owner to review.
 *
 * [now] and [zone] turn the server's instant into the day a lock ends for this user.
 */
class AccountViewModel(
    private val accounts: AccountRepository,
    private val signIn: ProviderSignIn,
    private val now: () -> Instant,
    private val zone: TimeZone,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountUiState(offered = signIn.providers))
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun onRetry() {
        if (_state.value.isLoading) return
        load()
    }

    fun onNicknameChanged(text: String) {
        if (_state.value.left != null) return
        // A nickname cannot be longer, so the field does not grow past it; what else is
        // wrong with one is said when it is saved, not while it is being typed.
        _state.update { it.copy(draft = text.take(NICKNAME_MAX_LENGTH), nicknameNotice = null) }
    }

    fun onSaveNickname() {
        val state = _state.value
        if (!state.canSave) return
        val nickname = state.draft
        if (!nicknameIsWellFormed(nickname)) {
            // The server would say the same; there is no need to ask it.
            _state.update { it.copy(nicknameNotice = NicknameNotice.INVALID) }
            return
        }
        request {
            try {
                when (val outcome = accounts.rename(nickname)) {
                    is RenameOutcome.Renamed -> _state.update { it.showing(outcome.account).copy(nicknameNotice = NicknameNotice.SAVED) }
                    RenameOutcome.Invalid -> _state.update { it.copy(nicknameNotice = NicknameNotice.INVALID) }
                    RenameOutcome.Taken -> _state.update { it.copy(nicknameNotice = NicknameNotice.TAKEN) }
                    RenameOutcome.Locked -> {
                        _state.update { it.copy(nicknameNotice = NicknameNotice.LOCKED) }
                        // Until when is on the account, and this copy of it did not know.
                        val read = runCatchingRequest { accounts.account() }
                        if (read != null) _state.update { it.copy(account = read, lockedUntil = lockOf(read)) }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(nicknameNotice = NicknameNotice.FAILED) }
            }
        }
    }

    fun onSignIn(provider: SignInProvider) {
        val state = _state.value
        if (state.busy || state.left != null || !state.canSignInWith(provider)) return
        _state.update { it.copy(signInNotice = null) }
        request {
            val notice = try {
                // Null: the user closed the sheet. They know; saying so would be noise.
                val token = signIn.idToken(provider)
                when (val outcome = token?.let { accounts.signIn(provider, it) }) {
                    null -> null
                    is SignInOutcome.SignedIn -> {
                        _state.update { it.showing(outcome.account) }
                        if (outcome.merged) SignInNotice.MERGED else SignInNotice.SIGNED_IN
                    }
                    SignInOutcome.Rejected -> SignInNotice.REJECTED
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                SignInNotice.FAILED
            }
            _state.update { it.copy(signInNotice = notice) }
        }
    }

    fun onLeaveRequested(way: Leaving) {
        val state = _state.value
        if (state.busy || state.left != null || state.account == null) return
        if (way == Leaving.SIGN_OUT && !state.canSignOut) return
        _state.update { it.copy(confirming = way, leavingFailed = false) }
    }

    fun onLeaveDismissed() {
        if (_state.value.busy) return
        _state.update { it.copy(confirming = null, leavingFailed = false) }
    }

    fun onLeaveConfirmed() {
        val state = _state.value
        val way = state.confirming ?: return
        if (state.busy || state.left != null) return
        _state.update { it.copy(leavingFailed = false) }
        request {
            try {
                val left = when (way) {
                    Leaving.SIGN_OUT -> {
                        accounts.signOut()
                        Left.SignedOut
                    }
                    Leaving.DELETE -> Left.Deleted(accounts.delete().subscriptionStillActive)
                }
                // The account is not read again: reading is what makes a new one (§5.1), and
                // one should not be made the moment the last was deleted.
                _state.update { it.copy(left = left, confirming = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(leavingFailed = true) }
            }
        }
    }

    private fun load() {
        _state.update { it.copy(isLoading = true, failed = false) }
        viewModelScope.launch {
            try {
                val account = accounts.account()
                _state.update { it.showing(account).copy(isLoading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, failed = true) }
            }
        }
    }

    /** Runs one request with everything else held off until it has answered. */
    private fun request(block: suspend () -> Unit) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun <T> runCatchingRequest(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    /** [account] as the screen starts from it: the field holds its nickname. */
    private fun AccountUiState.showing(account: Account): AccountUiState =
        copy(account = account, draft = account.nickname, lockedUntil = lockOf(account))

    private fun lockOf(account: Account): LocalDate? =
        account.nicknameLockedUntil?.takeIf { it > now() }?.toLocalDateTime(zone)?.date
}

/**
 * Whether there is an account to show.
 *
 * A build with no server has none; the repository it is given throws on first use, by
 * design (all-screens spec §6). There the profile says nothing about an account.
 */
fun accountIsBuilt(accounts: AccountRepository): Boolean = accounts !is NotBuiltAccountRepository
