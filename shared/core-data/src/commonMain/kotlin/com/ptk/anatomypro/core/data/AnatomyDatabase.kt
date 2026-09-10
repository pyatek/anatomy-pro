package com.ptk.anatomypro.core.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.ptk.anatomypro.core.data.dao.PreferenceDao
import com.ptk.anatomypro.core.data.dao.StructureDao
import com.ptk.anatomypro.core.data.entity.MeshRefEntity
import com.ptk.anatomypro.core.data.entity.PackEntity
import com.ptk.anatomypro.core.data.entity.PreferenceEntity
import com.ptk.anatomypro.core.data.entity.StructureEntity
import com.ptk.anatomypro.core.data.entity.StructureSearchEntity
import com.ptk.anatomypro.core.data.entity.StructureSynonymEntity
import com.ptk.anatomypro.core.data.entity.StructureTextEntity
import com.ptk.anatomypro.core.data.entity.StructureVerificationEntity

@Database(
    entities = [
        StructureEntity::class,
        StructureTextEntity::class,
        StructureSynonymEntity::class,
        MeshRefEntity::class,
        StructureVerificationEntity::class,
        PackEntity::class,
        StructureSearchEntity::class,
        PreferenceEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@ConstructedBy(AnatomyDatabaseConstructor::class)
abstract class AnatomyDatabase : RoomDatabase() {
    abstract fun structures(): StructureDao
    abstract fun preferences(): PreferenceDao
}

/** Room generates the actual per platform; the expect declaration has no body by design. */
@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT")
expect object AnatomyDatabaseConstructor : RoomDatabaseConstructor<AnatomyDatabase> {
    override fun initialize(): AnatomyDatabase
}
