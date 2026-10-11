package com.ptk.anatomypro.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptk.anatomypro.core.data.model.PackFailure
import com.ptk.anatomypro.core.data.model.PackState
import com.ptk.anatomypro.core.data.model.PackStatus
import com.ptk.anatomypro.core.data.repository.PackRepository
import com.ptk.anatomypro.core.model.PackId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The pack onboarding fetches: the skeleton, which the brief names and review starts with. */
val FIRST_PACK = PackId("skeletal-body")

/** What screen 03 is showing. Spec §9's four states, and one for a pack that is not there. */
sealed interface DownloadPhase {
    /** Asked for; nothing transferring yet. */
    data object Queued : DownloadPhase

    data class Progress(val bytesDone: Long, val bytesTotal: Long) : DownloadPhase {
        /** 0 before the total is known, rather than a division by zero. */
        val fraction: Float get() = if (bytesTotal <= 0L) 0f else bytesDone.toFloat() / bytesTotal
    }

    /** [resumable] decides the wording: resume what is held, or start over. */
    data class Failed(val reason: PackFailure, val resumable: Boolean) : DownloadPhase

    data object Done : DownloadPhase

    /** There to be had, and not asked for: outside onboarding a download waits to be wanted. */
    data object Offered : DownloadPhase

    /** The repository has no such pack. Nothing to wait for, so the step can be passed. */
    data object Unavailable : DownloadPhase
}

/**
 * What to show for a pack's status.
 *
 * [requestFailed] covers the failure a status cannot: `download()` throwing before the
 * repository has written anything, as it does when asked while offline. It only speaks
 * while the pack is still untouched — once bytes move, or the repository reports its own
 * failure, the status is the better witness.
 */
fun phaseOf(status: PackStatus?, requestFailed: Boolean, asked: Boolean = true): DownloadPhase = when (status) {
    null -> DownloadPhase.Unavailable
    PackStatus.Available, PackStatus.Queued -> when {
        requestFailed -> DownloadPhase.Failed(PackFailure.NETWORK, resumable = true)
        status == PackStatus.Available && !asked -> DownloadPhase.Offered
        else -> DownloadPhase.Queued
    }
    is PackStatus.Downloading -> DownloadPhase.Progress(status.bytesDone, status.bytesTotal)
    PackStatus.Installed -> DownloadPhase.Done
    is PackStatus.Failed -> DownloadPhase.Failed(status.reason, status.resumable)
}

data class FirstDownloadUiState(
    val label: String? = null,
    val byteSize: Long = 0,
    val phase: DownloadPhase = DownloadPhase.Queued,
) {
    val canContinue: Boolean get() = phase == DownloadPhase.Done || phase == DownloadPhase.Unavailable

    /** A failure can be walked past: being offline must not hold anyone in onboarding (§14). */
    val canSkip: Boolean get() = phase is DownloadPhase.Failed
}

/**
 * Prototype screen 03: the first pack downloads.
 *
 * The repository owns the download; this only asks for it and reports. It asks once, and
 * only for a pack that is still `Available` — so reopening the screen while a download
 * runs, or after one failed, requests nothing. A failure waits for [onRetry].
 */
class FirstDownloadViewModel(
    private val packs: PackRepository,
    private val pack: PackId = FIRST_PACK,
    /**
     * Onboarding fetches the pack without being asked. Anywhere else the pack may be absent
     * because someone removed it, and it waits for [onStart].
     */
    private val autoStart: Boolean = true,
) : ViewModel() {

    private val _state = MutableStateFlow(FirstDownloadUiState())
    val state: StateFlow<FirstDownloadUiState> = _state.asStateFlow()

    private var latest: PackState? = null
    private var requestFailed = false
    private var asked = false

    /** The list of packs could not be read at all: no connection and nothing kept. */
    private var listFailed = false

    init {
        observe()
    }

    private fun observe() {
        listFailed = false
        viewModelScope.launch {
            try {
                packs.packs.collect { all ->
                    latest = all.firstOrNull { it.id == pack }
                    publish()
                    if (autoStart && latest?.status == PackStatus.Available && !asked) request()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                listFailed = true
                publish()
            }
        }
    }

    /** Asks for a pack that was only [DownloadPhase.Offered]. */
    fun onStart() {
        if (_state.value.phase != DownloadPhase.Offered) return
        request()
    }

    fun onRetry() {
        if (_state.value.phase !is DownloadPhase.Failed) return
        if (listFailed) observe() else request()
    }

    private fun request() {
        asked = true
        requestFailed = false
        publish()
        viewModelScope.launch {
            try {
                packs.download(pack)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                requestFailed = true
                publish()
            }
        }
    }

    private fun publish() {
        val current = latest
        _state.value = FirstDownloadUiState(
            label = current?.label,
            byteSize = current?.byteSize ?: 0,
            // Nothing was fetched, so there is nothing to resume: only to try again.
            phase = if (listFailed) {
                DownloadPhase.Failed(PackFailure.NETWORK, resumable = false)
            } else {
                phaseOf(current?.status, requestFailed, asked)
            },
        )
    }
}
