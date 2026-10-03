package com.ptk.anatomypro.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ptk.anatomypro.core.data.entity.MeshRefEntity
import com.ptk.anatomypro.core.data.entity.PackEntity
import com.ptk.anatomypro.core.data.entity.StructureEntity
import com.ptk.anatomypro.core.data.entity.StructureSynonymEntity
import com.ptk.anatomypro.core.data.entity.StructureTextEntity
import com.ptk.anatomypro.core.data.entity.StructureVerificationEntity

/** Projection for [StructureDao.searchHits]. */
data class SearchHitRow(val structureId: String, val locale: String)

@Dao
interface StructureDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStructures(rows: List<StructureEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertText(rows: List<StructureTextEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSynonyms(rows: List<StructureSynonymEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeshRefs(rows: List<MeshRefEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPack(row: PackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVerification(row: StructureVerificationEntity)

    @Query("SELECT * FROM structure WHERE id = :id")
    suspend fun structure(id: String): StructureEntity?

    @Query("SELECT * FROM structure_text WHERE structureId = :id AND locale = :locale")
    suspend fun text(id: String, locale: String): StructureTextEntity?

    @Query("SELECT * FROM mesh_ref WHERE structureId = :id")
    suspend fun meshRefs(id: String): List<MeshRefEntity>

    @Query("SELECT * FROM structure WHERE packId = :packId ORDER BY id")
    suspend fun structuresInPack(packId: String): List<StructureEntity>

    /** The top of the taxonomy: structures nothing contains. */
    @Query("SELECT * FROM structure WHERE parentId IS NULL ORDER BY id")
    suspend fun roots(): List<StructureEntity>

    /**
     * Every structure that has at least one child.
     *
     * Read once per page of rows rather than counting children per row, which would be a
     * query per row and is the usual way a tree screen becomes slow.
     */
    @Query("SELECT DISTINCT parentId FROM structure WHERE parentId IS NOT NULL")
    suspend fun parentsWithChildren(): List<String>

    /** The taxonomy children of a structure, for atlas navigation and the encyclopedia. */
    @Query("SELECT * FROM structure WHERE parentId = :id ORDER BY id")
    suspend fun children(id: String): List<StructureEntity>

    @Query("SELECT * FROM pack WHERE id = :id")
    suspend fun pack(id: String): PackEntity?

    // --- §8.1 distractor tiers -------------------------------------------------------
    // Each is one predicate over an indexed column. `id != :exclude` keeps the structure
    // being asked about out of its own list of wrong answers.

    /** Easy: a different system entirely. */
    @Query("SELECT * FROM structure WHERE systemId IS NOT :systemId AND id IS NOT :exclude LIMIT :limit")
    suspend fun distractorsFromAnotherSystem(systemId: String?, exclude: String, limit: Int): List<StructureEntity>

    /** Medium: the same region, under a different parent. */
    @Query(
        """
        SELECT * FROM structure
        WHERE regionId IS :regionId AND parentId IS NOT :parentId AND id IS NOT :exclude
        LIMIT :limit
        """
    )
    suspend fun distractorsFromSameRegion(regionId: String?, parentId: String?, exclude: String, limit: Int): List<StructureEntity>

    /** Hard: siblings under the same parent. */
    @Query("SELECT * FROM structure WHERE parentId IS :parentId AND id IS NOT :exclude LIMIT :limit")
    suspend fun siblings(parentId: String?, exclude: String, limit: Int): List<StructureEntity>

    // --- verification ----------------------------------------------------------------

    /**
     * A verification counts only while it still describes the current text.
     *
     * The join on `contentHash` is the whole point of storing the hash: an edit that
     * arrives with a pack update leaves the row present but no longer matching, so it
     * stops satisfying this query rather than silently continuing to authorise a quiz
     * answer (spec §7).
     */
    @Query(
        """
        SELECT v.* FROM structure_verification v
        JOIN structure_text t
          ON t.structureId = v.structureId AND t.locale = v.locale
        WHERE v.structureId = :id AND v.locale = :locale
          AND v.verifiedTextHash = t.contentHash
        """
    )
    suspend fun currentVerification(id: String, locale: String): StructureVerificationEntity?

    @Query("SELECT * FROM structure_verification WHERE structureId = :id AND locale = :locale")
    suspend fun anyVerification(id: String, locale: String): StructureVerificationEntity?

    // --- search ----------------------------------------------------------------------

    /**
     * Searches every language at once, reporting which one matched.
     *
     * [normalisedQuery] must already be lowercased and accent-folded, as the rows are.
     * Shorter matches sort first so an exact term outranks a longer one that merely starts
     * with it.
     */
    @Query(
        """
        SELECT structureId, locale FROM structure_search
        WHERE normalised LIKE :normalisedQuery || '%'
        ORDER BY LENGTH(normalised), structureId
        LIMIT :limit
        """
    )
    suspend fun searchHits(normalisedQuery: String, limit: Int): List<SearchHitRow>

    /** Every locale a structure has text in, for the detail screen's name list. */
    @Query("SELECT * FROM structure_text WHERE structureId = :id")
    suspend fun texts(id: String): List<StructureTextEntity>

    /** Every system the installed atlas has structures in (screen 07). */
    @Query("SELECT DISTINCT systemId FROM structure WHERE systemId IS NOT NULL ORDER BY systemId")
    suspend fun systems(): List<String>

    @Query("SELECT id FROM structure WHERE systemId = :systemId")
    suspend fun idsInSystem(systemId: String): List<String>

    @Query("SELECT id FROM structure")
    suspend fun allIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearchRows(rows: List<com.ptk.anatomypro.core.data.entity.StructureSearchEntity>)

    @Transaction
    suspend fun replacePackContent(
        pack: PackEntity,
        structures: List<StructureEntity>,
        text: List<StructureTextEntity>,
        synonyms: List<StructureSynonymEntity>,
        meshRefs: List<MeshRefEntity>,
        searchRows: List<com.ptk.anatomypro.core.data.entity.StructureSearchEntity>,
    ) {
        upsertPack(pack)
        upsertStructures(structures)
        upsertText(text)
        upsertSynonyms(synonyms)
        upsertMeshRefs(meshRefs)
        upsertSearchRows(searchRows)
    }
}
