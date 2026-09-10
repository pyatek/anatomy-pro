package com.ptk.anatomypro.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single stored preference.
 *
 * Key/value rather than a one-row table with a column per setting: settings arrive over
 * time and a column each would mean a schema migration for every one. Reading is a handful
 * of rows, which is not worth a more structured shape.
 */
@Entity(tableName = "preference")
data class PreferenceEntity(
    @PrimaryKey val key: String,
    val value: String,
)
