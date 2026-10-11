package com.ptk.anatomypro.core.data.model

import com.ptk.anatomypro.core.model.Laterality
import com.ptk.anatomypro.core.model.StructureId

/**
 * What a list row or a tree node needs, and nothing more.
 *
 * Room entities stop at the repository boundary: they carry storage concerns — nullable
 * columns, string-typed enums, a pack id — that the UI has no business reasoning about,
 * and letting them through would make every screen depend on the schema.
 */
data class StructureSummary(
    val id: StructureId,
    val name: String,
    val latinName: String?,
    val laterality: Laterality,
    val isGroup: Boolean,
    val hasChildren: Boolean,
)
