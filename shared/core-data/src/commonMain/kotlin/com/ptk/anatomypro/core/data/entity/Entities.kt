package com.ptk.anatomypro.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The invariant identity of a structure. Everything that varies by language lives
 * elsewhere, so adding a locale is a data drop rather than a migration (spec §13).
 *
 * `parentId`, `systemId` and `regionId` are indexed because §8.1's three difficulty tiers
 * are each a single predicate over them — easy is a different system, medium is the same
 * region under a different parent, hard is the same parent. No hierarchy machinery beyond
 * these columns turned out to be necessary.
 */
@Entity(
    tableName = "structure",
    indices = [
        Index("parentId"),
        Index("systemId"),
        Index("regionId"),
        Index("packId"),
    ],
)
data class StructureEntity(
    @PrimaryKey val id: String,
    val taCode: String?,
    val parentId: String?,
    val systemId: String?,
    val regionId: String?,
    val laterality: String,
    val packId: String,
)

/**
 * Pack content: replaced wholesale whenever a pack updates.
 *
 * [contentHash] is what lets a verification survive a content update without silently
 * becoming a lie — see [StructureVerificationEntity].
 */
@Entity(tableName = "structure_text", primaryKeys = ["structureId", "locale"])
data class StructureTextEntity(
    val structureId: String,
    val locale: String,
    val name: String,
    val definition: String?,
    val contentHash: String,
)

@Entity(
    tableName = "structure_synonym",
    primaryKeys = ["structureId", "locale", "synonym"],
)
data class StructureSynonymEntity(
    val structureId: String,
    val locale: String,
    val synonym: String,
)

/** One node of geometry inside a pack. A structure may span several (spec §5). */
@Entity(
    tableName = "mesh_ref",
    primaryKeys = ["structureId", "packId", "nodeName"],
    indices = [Index("packId")],
)
data class MeshRefEntity(
    val structureId: String,
    val packId: String,
    val nodeName: String,
)

/**
 * Human review state, per structure per locale. Deliberately not stored beside the text
 * it describes.
 *
 * A pack update replaces [StructureTextEntity] rows freely; these survive. [verifiedTextHash]
 * records the text that was actually reviewed, so a row whose hash no longer matches the
 * current text reads as stale rather than as verified. §7 makes VERIFIED the gate on quiz
 * answers, and a stale approval surviving an edit is how a wrong name becomes a question
 * that teaches something false.
 */
@Entity(tableName = "structure_verification", primaryKeys = ["structureId", "locale"])
data class StructureVerificationEntity(
    val structureId: String,
    val locale: String,
    val state: String,
    val verifiedAt: Long,
    val verifiedTextHash: String,
)

/**
 * An installed content pack.
 *
 * Eviction under memory pressure (spec §14) clears [meshUri] and [installedAt] but leaves
 * every structure row in place: §11 syncs metadata independently of meshes, and §10's
 * paywall wants a structure to remain findable when its geometry is not resident.
 */
@Entity(tableName = "pack")
data class PackEntity(
    @PrimaryKey val id: String,
    val version: Long,
    val installedAt: Long?,
    val meshUri: String?,
)

/**
 * Search index over names and synonyms, one row per searchable term.
 *
 * A plain table rather than FTS4: Room's `@Fts4` fails to resolve its own default
 * `contentEntity` under KSP on Kotlin Multiplatform. `LIKE` over a few thousand rows is
 * not the bottleneck at this size, and keeping search in its own table means adopting FTS
 * later is a contained migration rather than a reshaping.
 *
 * [normalised] is lowercased and accent-folded at write time so the query does not have
 * to be, and so "cranium" finds "Crânium".
 */
@Entity(
    tableName = "structure_search",
    primaryKeys = ["structureId", "locale", "normalised"],
    indices = [Index("normalised"), Index("locale")],
)
data class StructureSearchEntity(
    val structureId: String,
    val locale: String,
    val normalised: String,
)
