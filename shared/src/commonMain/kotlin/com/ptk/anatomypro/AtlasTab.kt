package com.ptk.anatomypro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ptk.anatomypro.core.data.repository.AtlasRepository
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.feature.atlas.AtlasScreen
import com.ptk.anatomypro.feature.atlas.AtlasUiState
import com.ptk.anatomypro.feature.atlas.AtlasViewModel
import com.ptk.anatomypro.feature.atlas.StructureDetailScreen
import com.ptk.anatomypro.feature.atlas.StructureDetailUiState
import com.ptk.anatomypro.feature.atlas.StructureDetailViewModel
import com.ptk.anatomypro.feature.search.SearchScreen
import com.ptk.anatomypro.feature.search.SearchUiState
import com.ptk.anatomypro.feature.search.SearchViewModel

/**
 * Where the Atlas tab is: the model, a search field, or one structure's page.
 *
 * Kept as a sealed state rather than a navigation library because the tab has three
 * destinations and no deep links yet. When either becomes untrue this is the seam to
 * replace, and nothing outside this file knows about it.
 */
private sealed interface AtlasRoute {
    data object Browse : AtlasRoute
    data object Search : AtlasRoute
    data class Detail(val id: StructureId) : AtlasRoute
}

@Composable
fun AtlasTab(locale: String, latinOnly: Boolean) {
    val atlas = rememberAtlas()

    if (atlas == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Otwieranie atlasu…", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    var route: AtlasRoute by remember(atlas.pack.id) { mutableStateOf(AtlasRoute.Browse) }

    when (val current = route) {
        AtlasRoute.Browse -> BrowseRoute(
            repository = atlas.repository,
            packKey = atlas.pack.id.value,
            locale = locale,
            latinOnly = latinOnly,
            onSearch = { route = AtlasRoute.Search },
            onOpenDetail = { route = AtlasRoute.Detail(it) },
        )

        AtlasRoute.Search -> {
            val model: SearchViewModel = viewModel(key = "search-${atlas.pack.id.value}") {
                SearchViewModel(atlas.repository)
            }
            val state: SearchUiState by model.state.collectAsState()
            Column(Modifier.fillMaxSize()) {
                TopBar(title = "SZUKAJ", onBack = { route = AtlasRoute.Browse })
                SearchScreen(
                    state = state,
                    onQueryChanged = model::onQueryChanged,
                    onHitSelected = {
                        model.onHitOpened(it)
                        route = AtlasRoute.Detail(it.summary.id)
                    },
                    onRecentSelected = model::onRecentSelected,
                )
            }
        }

        is AtlasRoute.Detail -> {
            val model: StructureDetailViewModel = viewModel(key = "detail-${current.id.value}") {
                StructureDetailViewModel(atlas.repository, current.id, locale)
            }
            val state: StructureDetailUiState by model.state.collectAsState()
            StructureDetailScreen(
                state = state,
                onBack = { route = AtlasRoute.Browse },
                onAncestorSelected = { route = AtlasRoute.Detail(it) },
            )
        }
    }
}

@Composable
private fun BrowseRoute(
    repository: AtlasRepository,
    packKey: String,
    locale: String,
    latinOnly: Boolean,
    onSearch: () -> Unit,
    onOpenDetail: (StructureId) -> Unit,
) {
    var stats by remember { mutableStateOf(CanvasStats()) }
    val model: AtlasViewModel = viewModel(key = "atlas-$packKey") {
        AtlasViewModel(repository, locale)
    }
    val state: AtlasUiState by model.state.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("ATLAS", style = MaterialTheme.typography.labelSmall)
            Text(
                text = "SZUKAJ",
                style = MaterialTheme.typography.labelSmall,
                color = Accent,
                modifier = Modifier.heightIn(min = 44.dp).clickable(onClick = onSearch).padding(top = 14.dp),
            )
        }

        AtlasScreen(
            state = state,
            modifier = Modifier.weight(1f),
            canvas = { canvasModifier ->
                AnatomyCanvas(
                    modifier = canvasModifier,
                    highlighted = state.selected,
                    onPicked = model::onPickedInModel,
                    onStats = { stats = it },
                )
            },
            latinOnly = latinOnly,
            onRowToggled = model::onRowToggled,
            onRowSelected = { summary ->
                model.onRowSelected(summary)
                onOpenDetail(summary.id)
            },
        )

        // Kept from the Phase 0 harness: §16's budget is still unconfirmed on a mid-range
        // device, and losing the number would cost the ability to notice a regression.
        Text(
            text = "${stats.pack} · ${stats.structures} struktur · " +
                "${stats.fps}/${stats.refreshHz} fps · GPU ${(stats.gpuMillis * 10).toInt() / 10f} ms",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(6.dp),
        )
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "‹",
            style = MaterialTheme.typography.titleLarge,
            color = Accent,
            modifier = Modifier.heightIn(min = 44.dp).clickable(onClick = onBack).padding(horizontal = 8.dp, vertical = 8.dp),
        )
        Text(title, style = MaterialTheme.typography.labelSmall)
    }
}
