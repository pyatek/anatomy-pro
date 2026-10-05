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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import anatomypro.shared.generated.resources.Res
import anatomypro.shared.generated.resources.atlas_layers
import anatomypro.shared.generated.resources.atlas_opening
import anatomypro.shared.generated.resources.atlas_search
import anatomypro.shared.generated.resources.atlas_stats
import anatomypro.shared.generated.resources.atlas_title
import com.ptk.anatomypro.core.data.repository.AtlasRepository
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.feature.atlas.AtlasScreen
import com.ptk.anatomypro.feature.atlas.AtlasUiState
import com.ptk.anatomypro.feature.atlas.AtlasViewModel
import com.ptk.anatomypro.feature.atlas.StructureDetailScreen
import com.ptk.anatomypro.feature.atlas.StructureDetailUiState
import com.ptk.anatomypro.feature.atlas.StructureDetailViewModel
import com.ptk.anatomypro.feature.atlas.layers.LayersScreen
import com.ptk.anatomypro.feature.atlas.scene.AtlasSceneViewModel
import com.ptk.anatomypro.feature.atlas.tree.StructureTreeViewModel
import com.ptk.anatomypro.feature.atlas.tree.TreeScreen
import com.ptk.anatomypro.feature.search.SearchScreen
import com.ptk.anatomypro.feature.search.SearchUiState
import com.ptk.anatomypro.feature.search.SearchViewModel
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.stringResource

/**
 * The Atlas tab's start screen: the model and the structure tree.
 *
 * Search and a structure's page are destinations of their own in the NavHost (all-screens
 * spec §7), so they get the system back gesture and their own saved state; this composable
 * only says where to go. [repository] is null while the bundled pack is still installing,
 * which is a different thing to show than an empty atlas.
 */
@Composable
fun AtlasTab(
    repository: AtlasRepository?,
    scene: AtlasSceneViewModel?,
    locale: String,
    latinOnly: Boolean,
    onOpenDetail: (StructureId) -> Unit,
    onSearch: () -> Unit,
    onLayers: () -> Unit,
) {
    if (repository == null || scene == null) {
        Opening()
        return
    }
    BrowseRoute(
        repository = repository,
        scene = scene,
        locale = locale,
        latinOnly = latinOnly,
        onSearch = onSearch,
        onLayers = onLayers,
        onOpenDetail = onOpenDetail,
    )
}

@Composable
fun TreeRoute(
    repository: AtlasRepository?,
    scene: AtlasSceneViewModel?,
    locale: String,
    pathIds: MutableState<String>,
    onOpenDetail: (StructureId) -> Unit,
) {
    if (repository == null || scene == null) {
        Opening()
        return
    }
    // The level survives a language change: the model is keyed on the locale, so the path's
    // ids are held by the caller (above the locale rebuild) and the new model reopens at them.
    val model: StructureTreeViewModel = viewModel(key = "tree-$locale") {
        StructureTreeViewModel(repository, locale, pathIds.value.split(',').filter { it.isNotEmpty() }.map(::StructureId))
    }
    val state by model.state.collectAsState()
    LaunchedEffect(state.path) {
        if (!state.isLoading) pathIds.value = state.path.joinToString(",") { it.id.value }
    }
    TreeScreen(
        state = state,
        onFocus = { index ->
            model.onFocus(index)
            state.items.getOrNull(index)?.let { item ->
                // The scene keeps the selection, so the atlas highlights and names it when
                // tree mode is left. A group draws nothing: asking the camera to frame one
                // would do nothing except replace a request that is still waiting.
                scene.onStructureSelected(item.id, isGroup = item.isGroup)
                if (!item.isGroup) scene.focusCamera(item.id)
            }
        },
        onEnter = model::onEnter,
        onUp = model::onUp,
        onOpen = { state.focused?.let { onOpenDetail(it.id) } },
    )
}

@Composable
fun SearchRoute(repository: AtlasRepository?, onBack: () -> Unit, onOpenDetail: (StructureId) -> Unit) {
    if (repository == null) {
        Opening()
        return
    }
    val model: SearchViewModel = viewModel { SearchViewModel(repository) }
    val state: SearchUiState by model.state.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopBar(title = stringResource(Res.string.atlas_search), onBack = onBack)
        SearchScreen(
            state = state,
            onQueryChanged = model::onQueryChanged,
            onHitSelected = {
                model.onHitOpened(it)
                onOpenDetail(it.summary.id)
            },
            onRecentSelected = model::onRecentSelected,
        )
    }
}

@Composable
fun DetailRoute(
    repository: AtlasRepository?,
    id: StructureId,
    locale: String,
    onBack: () -> Unit,
    onOpenDetail: (StructureId) -> Unit,
) {
    if (repository == null) {
        Opening()
        return
    }
    // Keyed on the locale too: the names are fetched once, so a language change needs a
    // fresh model rather than the one already holding the old language's names.
    val model: StructureDetailViewModel = viewModel(key = "detail-${id.value}-$locale") {
        StructureDetailViewModel(repository, id, locale)
    }
    val state: StructureDetailUiState by model.state.collectAsState()
    StructureDetailScreen(
        state = state,
        onBack = onBack,
        onAncestorSelected = onOpenDetail,
    )
}

@Composable
fun LayersRoute(
    repository: AtlasRepository?,
    scene: AtlasSceneViewModel?,
    locale: String,
    onBack: () -> Unit,
) {
    if (repository == null || scene == null) {
        Opening()
        return
    }
    val panel by scene.panel.collectAsState()
    val render by scene.render.collectAsState()
    val focus by scene.cameraFocus.collectAsState()
    var focusName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(panel.focus, locale) {
        focusName = null
        focusName = panel.focus?.let { nameOf(repository, it, locale) }
    }
    LayersScreen(
        state = panel,
        focusName = focusName,
        canvas = { modifier ->
            AnatomyCanvas(
                modifier = modifier,
                highlighted = panel.focus,
                render = render,
                focus = focus,
                onPicked = scene::onStructureSelected,
                onStats = {},
            )
        },
        onMode = scene::setLayer,
        onReset = scene::resetLayers,
        onIsolate = scene::setIsolation,
        onGhostPercent = scene::setGhostPercent,
        onBack = onBack,
    )
}

@Composable
private fun Opening() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(Res.string.atlas_opening), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BrowseRoute(
    repository: AtlasRepository,
    scene: AtlasSceneViewModel,
    locale: String,
    latinOnly: Boolean,
    onSearch: () -> Unit,
    onLayers: () -> Unit,
    onOpenDetail: (StructureId) -> Unit,
) {
    val panel by scene.panel.collectAsState()
    val render by scene.render.collectAsState()
    val focus by scene.cameraFocus.collectAsState()
    var stats by remember { mutableStateOf(CanvasStats()) }
    // Keyed on the locale for the same reason as the detail page: the tree's names are
    // loaded once per model. That is why the selection is not this model's to keep: it
    // belongs to the scene, which a language change does not replace.
    val model: AtlasViewModel = viewModel(key = "atlas-$locale") {
        AtlasViewModel(repository, locale)
    }
    val state: AtlasUiState by model.state.collectAsState()
    // The selection is an id; its name is looked up here so it follows the interface language.
    // A structure the atlas has no entry for is still named, by its id, rather than left blank.
    var selectedName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(panel.selected, locale) {
        selectedName = null
        selectedName = panel.selected?.let { nameOf(repository, it, locale) ?: it.value }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(Res.string.atlas_title), style = MaterialTheme.typography.labelSmall)
            Text(
                text = stringResource(Res.string.atlas_layers),
                style = MaterialTheme.typography.labelSmall,
                color = Accent,
                modifier = Modifier.heightIn(min = 44.dp).clickable(onClick = onLayers).padding(top = 14.dp),
            )
            Text(
                text = stringResource(Res.string.atlas_search),
                style = MaterialTheme.typography.labelSmall,
                color = Accent,
                modifier = Modifier.heightIn(min = 44.dp).clickable(onClick = onSearch).padding(top = 14.dp),
            )
        }

        AtlasScreen(
            state = state,
            highlighted = panel.focus,
            selectedName = selectedName,
            modifier = Modifier.weight(1f),
            canvas = { canvasModifier ->
                AnatomyCanvas(
                    modifier = canvasModifier,
                    highlighted = panel.focus,
                    render = render,
                    focus = focus,
                    onPicked = scene::onStructureSelected,
                    onStats = { stats = it },
                )
            },
            latinOnly = latinOnly,
            onRowToggled = model::onRowToggled,
            onRowSelected = { summary ->
                scene.onStructureSelected(summary.id, isGroup = summary.isGroup)
                onOpenDetail(summary.id)
            },
        )

        // Kept from the Phase 0 harness: §16's budget is still unconfirmed on a mid-range
        // device, and losing the number would cost the ability to notice a regression.
        Text(
            text = stringResource(
                Res.string.atlas_stats,
                stats.pack,
                stats.structures,
                stats.fps,
                stats.refreshHz,
                ((stats.gpuMillis * 10).toInt() / 10f).toString(),
            ),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(6.dp),
        )
    }
}

/** A structure's name in [locale], or null when the atlas does not know it or cannot be read. */
private suspend fun nameOf(repository: AtlasRepository, id: StructureId, locale: String): String? = try {
    repository.summary(id, locale)?.name
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
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
