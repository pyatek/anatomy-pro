package com.ptk.anatomypro.feature.atlas

import com.ptk.anatomypro.core.data.model.StructureSummary

/**
 * One row of the flattened taxonomy.
 *
 * The tree is flattened in the state rather than rendered recursively so the list can be
 * a `LazyColumn` — an atlas region is hundreds of rows and a recursive layout would
 * compose all of them.
 */
data class AtlasRow(
    val summary: StructureSummary,
    val depth: Int,
    val expanded: Boolean,
)

/**
 * Everything the atlas screen draws, and nothing about how it draws it.
 *
 * The screen is a function of this value; the ViewModel is the only thing that produces
 * one. That is what makes the screen testable without a database or a GPU.
 */
data class AtlasUiState(
    val rows: List<AtlasRow> = emptyList(),
    val isLoading: Boolean = true,
    val error: AtlasError? = null,
)

/**
 * What went wrong, as a kind rather than as text: a ViewModel cannot read string resources,
 * and an exception's message is not copy a student should see.
 */
enum class AtlasError { LoadFailed }
