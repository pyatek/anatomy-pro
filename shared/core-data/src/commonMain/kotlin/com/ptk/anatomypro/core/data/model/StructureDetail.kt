package com.ptk.anatomypro.core.data.model

import com.ptk.anatomypro.core.model.Laterality
import com.ptk.anatomypro.core.model.StructureId

/**
 * Everything the detail screen reads.
 *
 * [names] is keyed by locale rather than split into fields, so adding a language stays a
 * data drop (spec §13). [ancestors] is root-first, which is the order the hierarchy is
 * displayed in.
 */
data class StructureDetail(
    val id: StructureId,
    val names: Map<String, String>,
    val definition: String?,
    val definitionLocale: String?,
    val systemId: String?,
    val regionId: String?,
    val laterality: Laterality,
    val isGroup: Boolean,
    val ancestors: List<StructureSummary>,
)

/**
 * A search result, and the language it was found in.
 *
 * The prototype searches every language at once and badges the match, so which locale hit
 * is part of the result rather than a filter applied before it.
 */
data class SearchHit(
    val summary: StructureSummary,
    val matchedLocale: String,
)
