package com.ptk.anatomypro.feature.atlas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptk.anatomypro.core.data.model.StructureDetail
import com.ptk.anatomypro.core.data.repository.AtlasRepository
import com.ptk.anatomypro.core.model.StructureId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Prototype screen 05. Latin is the title; the other languages are listed beneath it. */
data class StructureDetailUiState(
    val detail: StructureDetail? = null,
    val isLoading: Boolean = true,
    val error: DetailError? = null,
) {
    /**
     * The title is the Latin name, because Latin is the canonical key (spec §13) and the
     * one language guaranteed to exist for every structure.
     */
    val title: String? get() = detail?.names?.get("la") ?: detail?.names?.values?.firstOrNull()

    /** Every other language, in a stable order so the list does not reshuffle per structure. */
    val otherNames: List<Pair<String, String>>
        get() = detail?.names.orEmpty()
            .filterKeys { it != "la" }
            .toList()
            .sortedBy { it.first }
}

class StructureDetailViewModel(
    private val repository: AtlasRepository,
    private val structureId: StructureId,
    private val locale: String,
) : ViewModel() {

    private val _state = MutableStateFlow(StructureDetailUiState())
    val state: StateFlow<StructureDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.detail(structureId, locale) }
                .onSuccess { detail ->
                    _state.value = StructureDetailUiState(
                        detail = detail,
                        isLoading = false,
                        // A structure the tree offered but the repository cannot produce is
                        // a data fault, not an empty screen.
                        error = if (detail == null) DetailError.NotFound else null,
                    )
                }
                .onFailure {
                    _state.value = StructureDetailUiState(
                        isLoading = false,
                        error = DetailError.LoadFailed,
                    )
                }
        }
    }
}

/** A kind rather than text, for the same reason as [AtlasError]. */
enum class DetailError { NotFound, LoadFailed }
