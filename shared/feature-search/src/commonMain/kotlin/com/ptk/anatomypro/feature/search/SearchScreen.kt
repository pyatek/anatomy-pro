package com.ptk.anatomypro.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import anatomypro.shared.feature_search.generated.resources.Res
import anatomypro.shared.feature_search.generated.resources.search_field
import anatomypro.shared.feature_search.generated.resources.search_match
import anatomypro.shared.feature_search.generated.resources.search_recent
import anatomypro.shared.feature_search.generated.resources.search_results
import anatomypro.shared.feature_search.generated.resources.search_scope
import anatomypro.shared.feature_search.generated.resources.search_searching
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import anatomypro.shared.feature_search.generated.resources.search_clear
import com.ptk.anatomypro.core.designsystem.AccentMuted
import com.ptk.anatomypro.core.designsystem.Badge
import com.ptk.anatomypro.core.designsystem.FooterHairline
import com.ptk.anatomypro.core.designsystem.Hairline
import com.ptk.anatomypro.core.designsystem.HighlightOutline
import com.ptk.anatomypro.core.designsystem.LocalAnatomyType
import com.ptk.anatomypro.core.designsystem.Overline
import com.ptk.anatomypro.core.designsystem.Surface
import com.ptk.anatomypro.core.designsystem.TextPrimary
import com.ptk.anatomypro.core.designsystem.TextSecondary
import com.ptk.anatomypro.core.designsystem.TextStrong
import com.ptk.anatomypro.core.data.model.SearchHit
import com.ptk.anatomypro.core.designsystem.SideBadge
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.TextTertiary
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Prototype screen 06: one field, every language at once, the match badged. */
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChanged: (String) -> Unit,
    onHitSelected: (SearchHit) -> Unit,
    onRecentSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = LocalAnatomyType.current
    val fieldShape = RoundedCornerShape(12.dp)
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // The prototype's field: a glyph, the query, and a way to clear it.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .heightIn(min = 48.dp)
                .clip(fieldShape)
                .background(Surface)
                .border(BorderStroke(1.dp, Hairline), fieldShape)
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("⌕", style = type.button, color = TextSecondary, modifier = Modifier.clearAndSetSemantics { })
            val fieldLabel = stringResource(Res.string.search_field)
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (state.query.isEmpty()) Text(fieldLabel, style = type.button.copy(fontWeight = FontWeight.Normal), color = TextTertiary, modifier = Modifier.clearAndSetSemantics { })
                BasicTextField(
                    value = state.query,
                    onValueChange = onQueryChanged,
                    singleLine = true,
                    textStyle = type.button.copy(fontWeight = FontWeight.Normal, color = TextPrimary),
                    cursorBrush = SolidColor(Accent),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = fieldLabel },
                )
            }
            if (state.query.isNotEmpty()) {
                val clear = stringResource(Res.string.search_clear)
                Box(
                    modifier = Modifier.size(44.dp).clickable(role = Role.Button) { onQueryChanged("") }.semantics { contentDescription = clear },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("×", style = type.button, color = TextTertiary, modifier = Modifier.clearAndSetSemantics { })
                }
            }
        }

        Text(
            text = when {
                !state.hasQuery -> stringResource(Res.string.search_scope)
                state.isSearching -> stringResource(Res.string.search_searching)
                else -> pluralStringResource(Res.plurals.search_results, state.hits.size, state.hits.size)
            }.uppercase(),
            style = type.code,
            color = TextTertiary,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )

        if (!state.hasQuery) {
            if (state.recent.isNotEmpty()) {
                Overline(stringResource(Res.string.search_recent), color = TextSecondary)
                for (term in state.recent) {
                    Column {
                        Text(
                            text = term,
                            style = type.body,
                            color = TextStrong,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button) { onRecentSelected(term) }.padding(top = 12.dp),
                        )
                        HorizontalDivider(color = FooterHairline, thickness = 1.dp)
                    }
                }
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.hits, key = { it.summary.id.value }) { hit ->
                val cardShape = RoundedCornerShape(12.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(cardShape)
                        .background(Surface)
                        .border(BorderStroke(1.dp, Hairline), cardShape)
                        .clickable(role = Role.Button) { onHitSelected(hit) }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = hit.summary.latinName ?: hit.summary.name,
                            // Latin leads, in the serif; a group reads as a heading.
                            style = type.latin.copy(fontWeight = if (hit.summary.isGroup) FontWeight.SemiBold else FontWeight.Normal),
                            color = TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        SideBadge(hit.summary.laterality)
                        // Which language the query was found in: said, never implied by position.
                        Badge(stringResource(Res.string.search_match, hit.matchedLocale.uppercase()), color = HighlightOutline, outline = AccentMuted)
                    }
                    if (hit.summary.name != hit.summary.latinName) {
                        Text(hit.summary.name, style = type.body, color = TextStrong)
                    }
                }
            }
        }
    }
}
