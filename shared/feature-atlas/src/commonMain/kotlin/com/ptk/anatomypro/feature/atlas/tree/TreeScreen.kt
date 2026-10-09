package com.ptk.anatomypro.feature.atlas.tree

import anatomypro.shared.feature_atlas.generated.resources.Res
import anatomypro.shared.feature_atlas.generated.resources.atlas_load_failed
import anatomypro.shared.feature_atlas.generated.resources.atlas_loading
import anatomypro.shared.feature_atlas.generated.resources.tree_announce_focused
import anatomypro.shared.feature_atlas.generated.resources.tree_announce_level
import anatomypro.shared.feature_atlas.generated.resources.tree_badge
import anatomypro.shared.feature_atlas.generated.resources.tree_count
import anatomypro.shared.feature_atlas.generated.resources.tree_enter
import anatomypro.shared.feature_atlas.generated.resources.tree_focused_badge
import anatomypro.shared.feature_atlas.generated.resources.tree_level
import anatomypro.shared.feature_atlas.generated.resources.tree_level_top
import anatomypro.shared.feature_atlas.generated.resources.tree_open
import anatomypro.shared.feature_atlas.generated.resources.tree_top_name
import anatomypro.shared.feature_atlas.generated.resources.tree_up
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ptk.anatomypro.core.designsystem.SideBadge
import com.ptk.anatomypro.core.designsystem.sideLabel
import com.ptk.anatomypro.core.designsystem.withSide
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.TextTertiary
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Prototype screen 21: an equivalent of the canvas, not a fallback (design spec §12). */
@Composable
fun TreeScreen(
    state: TreeUiState,
    onFocus: (Int) -> Unit,
    onEnter: (Int) -> Unit,
    onUp: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enterLabel = stringResource(Res.string.tree_enter)

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(Res.string.tree_badge), style = MaterialTheme.typography.labelSmall, color = Accent)
        Text(
            text = state.path.lastOrNull()?.let { stringResource(Res.string.tree_level, state.level, (it.latinName ?: it.name).uppercase()) }
                ?: stringResource(Res.string.tree_level_top),
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
        )
        Text(
            text = pluralStringResource(Res.plurals.tree_count, state.items.size, state.items.size),
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
        )

        // Shown as a status line, and announced by screen readers because it is a polite live region.
        Text(
            text = announcementText(state.announcement),
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )

        if (state.isLoading || state.error != null) {
            Text(
                text = stringResource(if (state.isLoading) Res.string.atlas_loading else Res.string.atlas_load_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary,
                modifier = Modifier.weight(1f),
            )
        } else LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(state.items, key = { _, item -> item.id.value }) { index, item ->
                val isFocused = index == state.focusedIndex
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .semantics {
                            // Only the focused row says so: "not selected" on every other
                            // row is noise a screen reader would read out each time.
                            if (isFocused) selected = true
                            if (item.hasChildren) {
                                customActions = listOf(CustomAccessibilityAction(enterLabel) { onEnter(index); true })
                            }
                        }
                        .clickable { onFocus(index) },
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(item.latinName ?: item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal)
                            SideBadge(item.laterality)
                        }
                        if (item.latinName != null && item.latinName != item.name) {
                            Text(item.name, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                        }
                        if (isFocused) {
                            Text(
                                stringResource(Res.string.tree_focused_badge, index + 1, state.items.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = Accent,
                            )
                        }
                    }
                    if (item.hasChildren) {
                        Text(
                            "›",
                            style = MaterialTheme.typography.titleLarge,
                            color = Accent,
                            modifier = Modifier.heightIn(min = 44.dp).widthIn(min = 44.dp)
                                .semantics { role = Role.Button; contentDescription = enterLabel }
                                .clickable { onEnter(index) }
                                .padding(top = 8.dp, start = 16.dp),
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onUp, enabled = state.path.isNotEmpty(), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.tree_up))
            }
            Button(onClick = onOpen, enabled = state.focused != null, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.tree_open))
            }
        }
    }
}

@Composable
private fun announcementText(announcement: TreeAnnouncement?): String = when (announcement) {
    null -> ""
    is TreeAnnouncement.Focused -> stringResource(
        Res.string.tree_announce_focused,
        withSide(announcement.name, sideLabel(announcement.laterality)),
        announcement.position,
        announcement.total,
    )
    is TreeAnnouncement.Level -> pluralStringResource(
        Res.plurals.tree_announce_level,
        announcement.count,
        announcement.level,
        announcement.name ?: stringResource(Res.string.tree_top_name),
        announcement.count,
    )
}
