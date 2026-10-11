package com.ptk.anatomypro.feature.atlas.tree

import anatomypro.shared.feature_atlas.generated.resources.Res
import anatomypro.shared.feature_atlas.generated.resources.atlas_load_failed
import anatomypro.shared.feature_atlas.generated.resources.atlas_loading
import anatomypro.shared.feature_atlas.generated.resources.tree_announce_focused
import anatomypro.shared.feature_atlas.generated.resources.tree_announce_level
import anatomypro.shared.feature_atlas.generated.resources.tree_badge
import anatomypro.shared.feature_atlas.generated.resources.tree_count
import anatomypro.shared.feature_atlas.generated.resources.tree_count_plain
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import com.ptk.anatomypro.core.designsystem.AccentMuted
import com.ptk.anatomypro.core.designsystem.Badge
import com.ptk.anatomypro.core.designsystem.FooterHairline
import com.ptk.anatomypro.core.designsystem.Hairline
import com.ptk.anatomypro.core.designsystem.HighlightOutline
import com.ptk.anatomypro.core.designsystem.LocalAnatomyType
import com.ptk.anatomypro.core.designsystem.NavSurface
import com.ptk.anatomypro.core.designsystem.OutlineButton
import com.ptk.anatomypro.core.designsystem.Overline
import com.ptk.anatomypro.core.designsystem.Panel
import com.ptk.anatomypro.core.designsystem.PrimaryButton
import com.ptk.anatomypro.core.designsystem.Surface
import com.ptk.anatomypro.core.designsystem.TextPrimary
import com.ptk.anatomypro.core.designsystem.TextSecondary
import com.ptk.anatomypro.core.designsystem.TextStrong
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
    val type = LocalAnatomyType.current
    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(stringResource(Res.string.tree_badge), color = HighlightOutline, outline = AccentMuted)
            }
            // The prototype's panel: where in the tree this is, and how much is here.
            Panel(fill = NavSurface, spacing = 4.dp) {
                Overline(
                    state.path.lastOrNull()?.let { stringResource(Res.string.tree_level, state.level, (it.latinName ?: it.name).uppercase()) }
                        ?: stringResource(Res.string.tree_level_top),
                    color = TextSecondary,
                )
                Text(
                    // The arrow is promised only where a row has one.
                    pluralStringResource(
                        if (state.anyDeeper) Res.plurals.tree_count else Res.plurals.tree_count_plain,
                        state.items.size,
                        state.items.size,
                    ),
                    style = type.caption,
                    color = TextStrong,
                )
                // Shown as a status line, and announced by screen readers because it is a polite live region.
                Text(
                    text = announcementText(state.announcement),
                    style = type.note,
                    color = TextTertiary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (state.isLoading || state.error != null) {
                Text(
                    text = stringResource(if (state.isLoading) Res.string.atlas_loading else Res.string.atlas_load_failed),
                    style = type.body,
                    color = TextTertiary,
                    modifier = Modifier.weight(1f),
                )
            } else LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                itemsIndexed(state.items, key = { _, item -> item.id.value }) { index, item ->
                    val isFocused = index == state.focusedIndex
                    val shape = RoundedCornerShape(12.dp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                            .clip(shape)
                            .background(Surface)
                            // The focused card is outlined and says FOCUSED; never the outline alone (§12).
                            .border(BorderStroke(1.dp, if (isFocused) Accent else Hairline), shape)
                            .semantics {
                                // Only the focused row says so: "not selected" on every other
                                // row is noise a screen reader would read out each time.
                                if (isFocused) selected = true
                                if (item.hasChildren) {
                                    customActions = listOf(CustomAccessibilityAction(enterLabel) { onEnter(index); true })
                                }
                            }
                            .clickable { onFocus(index) }
                            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(item.latinName ?: item.name, style = type.latin, color = TextPrimary)
                                SideBadge(item.laterality)
                            }
                            if (item.latinName != null && item.latinName != item.name) {
                                Text(item.name, style = type.caption, color = TextStrong)
                            }
                            if (isFocused) {
                                Badge(stringResource(Res.string.tree_focused_badge, index + 1, state.items.size), color = HighlightOutline, outline = AccentMuted)
                            }
                        }
                        if (item.hasChildren) {
                            Text(
                                "›",
                                style = type.heading,
                                color = TextSecondary,
                                modifier = Modifier.heightIn(min = 44.dp).widthIn(min = 44.dp)
                                    .semantics { role = Role.Button; contentDescription = enterLabel }
                                    .clickable { onEnter(index) }
                                    .padding(top = 8.dp, start = 16.dp),
                            )
                        } else {
                            Box(Modifier.widthIn(min = 14.dp))
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = FooterHairline, thickness = 1.dp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        ) {
            OutlineButton(stringResource(Res.string.tree_up), onClick = onUp, enabled = state.path.isNotEmpty(), modifier = Modifier.weight(1f))
            PrimaryButton(stringResource(Res.string.tree_open), onClick = onOpen, enabled = state.focused != null, modifier = Modifier.weight(1f))
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
