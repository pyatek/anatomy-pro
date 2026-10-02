package com.ptk.anatomypro.core.data.repository

import com.ptk.anatomypro.core.data.model.PackState
import com.ptk.anatomypro.core.model.PackId
import kotlinx.coroutines.flow.Flow

/** Screens 03 and 19. */
interface PackRepository {
    val packs: Flow<List<PackState>>
    suspend fun download(id: PackId)
    suspend fun cancel(id: PackId)
    suspend fun delete(id: PackId)
}
