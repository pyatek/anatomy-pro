package com.ptk.anatomypro.core.data.model

import com.ptk.anatomypro.core.model.PackId

/**
 * A pack as the index of the bucket lists it (backend spec §9.1), and what this device has
 * done about it. Packs are free, so nothing here says who may have one (all-screens §15.2).
 */
data class PackState(
    val id: PackId,
    val version: Int,
    val label: String,
    val byteSize: Long,
    val checksum: String,
    val status: PackStatus,
)

sealed interface PackStatus {
    data object Available : PackStatus

    /** Requested, nothing transferring yet: screen 03's first state (all-screens spec §9). */
    data object Queued : PackStatus

    data class Downloading(val bytesDone: Long, val bytesTotal: Long) : PackStatus {
        /** 0f before the total is known, rather than a division by zero on screen 03. */
        val fraction: Float get() = if (bytesTotal == 0L) 0f else bytesDone.toFloat() / bytesTotal
    }

    data object Installed : PackStatus

    /** §14: downloads are resumable, so a failure is a state the UI offers to resume from. */
    data class Failed(val reason: PackFailure, val resumable: Boolean) : PackStatus
}

enum class PackFailure { NETWORK, CHECKSUM, OUT_OF_SPACE }
