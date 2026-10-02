package com.ptk.anatomypro.core.data.model

import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.core.model.SystemId

/**
 * The manifest fields §10 names, minus the download URL: that is issued server-side
 * against entitlements and never belongs in UI state.
 */
data class PackState(
    val id: PackId,
    val version: Int,
    val label: String,
    val byteSize: Long,
    val checksum: String,
    val entitlement: SystemId?,
    val status: PackStatus,
)

sealed interface PackStatus {
    data object Available : PackStatus

    data class Downloading(val bytesDone: Long, val bytesTotal: Long) : PackStatus {
        /** 0f before the total is known, rather than a division by zero on screen 03. */
        val fraction: Float get() = if (bytesTotal == 0L) 0f else bytesDone.toFloat() / bytesTotal
    }

    data object Installed : PackStatus

    /** §14: downloads are resumable, so a failure is a state the UI offers to resume from. */
    data class Failed(val reason: PackFailure, val resumable: Boolean) : PackStatus
}

enum class PackFailure { NETWORK, CHECKSUM, OUT_OF_SPACE, NOT_ENTITLED }
