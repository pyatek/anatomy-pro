package com.ptk.anatomypro.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ptk.anatomypro.core.data.entity.PreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PreferenceDao {

    /** A Flow so a settings change reaches every screen without anything re-reading. */
    @Query("SELECT * FROM preference")
    fun observeAll(): Flow<List<PreferenceEntity>>

    @Query("SELECT * FROM preference")
    suspend fun all(): List<PreferenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(preference: PreferenceEntity)
}
