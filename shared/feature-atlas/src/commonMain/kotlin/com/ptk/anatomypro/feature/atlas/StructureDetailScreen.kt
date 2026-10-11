package com.ptk.anatomypro.feature.atlas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import anatomypro.shared.feature_atlas.generated.resources.Res
import anatomypro.shared.feature_atlas.generated.resources.detail_definition_credit
import anatomypro.shared.feature_atlas.generated.resources.detail_definition_unreviewed
import anatomypro.shared.feature_atlas.generated.resources.detail_load_failed
import anatomypro.shared.feature_atlas.generated.resources.detail_loading
import anatomypro.shared.feature_atlas.generated.resources.detail_not_found
import anatomypro.shared.feature_atlas.generated.resources.detail_section_definition
import anatomypro.shared.feature_atlas.generated.resources.detail_section_hierarchy
import anatomypro.shared.feature_atlas.generated.resources.detail_section_structure
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import anatomypro.shared.feature_atlas.generated.resources.layers_back
import com.ptk.anatomypro.core.designsystem.AccentOnLight
import com.ptk.anatomypro.core.designsystem.LightGround
import com.ptk.anatomypro.core.designsystem.LightHairline
import com.ptk.anatomypro.core.designsystem.LightRule
import com.ptk.anatomypro.core.designsystem.LightSurface
import com.ptk.anatomypro.core.designsystem.LightTextBody
import com.ptk.anatomypro.core.designsystem.LightTextPrimary
import com.ptk.anatomypro.core.designsystem.LightTextSecondary
import com.ptk.anatomypro.core.designsystem.LightTextTertiary
import com.ptk.anatomypro.core.designsystem.LocalAnatomyType
import com.ptk.anatomypro.core.designsystem.SideBadge
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.TextTertiary
import org.jetbrains.compose.resources.stringResource

/** Prototype screen 05: read about one structure. */
@Composable
fun StructureDetailScreen(
    state: StructureDetailUiState,
    onBack: () -> Unit,
    onAncestorSelected: (com.ptk.anatomypro.core.model.StructureId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = LocalAnatomyType.current
    val backLabel = stringResource(Res.string.layers_back)
    // One of the prototype's two light screens: this one is for reading.
    Column(modifier = modifier.fillMaxSize().background(LightGround)) {
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 12.dp)) {
            Text(
                text = "‹",
                style = type.title,
                color = LightTextSecondary,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .heightIn(min = 44.dp)
                    .clickable(role = Role.Button, onClick = onBack)
                    // A glyph says nothing aloud.
                    .semantics { contentDescription = backLabel }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Text(
                stringResource(Res.string.detail_section_structure),
                style = type.label,
                color = LightTextTertiary,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        HorizontalDivider(color = LightHairline, thickness = 1.dp)
        when {
            state.isLoading -> Note(stringResource(Res.string.detail_loading))
            state.error != null -> Note(
                stringResource(
                    when (state.error) {
                        DetailError.NotFound -> Res.string.detail_not_found
                        DetailError.LoadFailed -> Res.string.detail_load_failed
                    },
                ),
            )
            else -> Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.title?.let {
                        Text(it, style = type.title.copy(fontSize = 30.sp, lineHeight = 34.sp), color = LightTextPrimary)
                    }
                    // Which of a pair this is. "Costa prima" alone read the same for both.
                    state.detail?.let { SideBadge(it.laterality, color = LightTextTertiary) }
                    if (state.otherNames.isNotEmpty()) {
                        // The prototype's list of names, ruled down its left edge.
                        Row(modifier = Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.width(2.dp).fillMaxHeight().background(LightRule))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                for ((locale, name) in state.otherNames) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(locale.uppercase(), style = type.code, color = LightTextTertiary, modifier = Modifier.width(22.dp))
                                        Text(
                                            name,
                                            // Latin in the serif, wherever it is named.
                                            style = if (locale == "la") type.latin.copy(fontSize = 15.sp) else type.body.copy(fontSize = 15.sp),
                                            color = LightTextPrimary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                state.detail?.let { detail ->
                    detail.definition?.let {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            SectionLabel(stringResource(Res.string.detail_section_definition))
                            Text(it, style = type.body, color = LightTextBody)
                            // Someone else's prose, never reviewed here: say whose, and say so.
                            val source = definitionSourceName(detail.definitionSource, detail.definitionLicence)
                            Text(
                                text = if (source == null) {
                                    stringResource(Res.string.detail_definition_unreviewed)
                                } else {
                                    stringResource(
                                        Res.string.detail_definition_credit,
                                        listOfNotNull(source, detail.definitionLicence).joinToString(" · "),
                                    )
                                },
                                style = type.note,
                                color = LightTextTertiary,
                            )
                        }
                    }
                }
                val ancestors = state.detail?.ancestors.orEmpty()
                if (ancestors.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        SectionLabel(stringResource(Res.string.detail_section_hierarchy))
                        val shape = RoundedCornerShape(12.dp)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(LightSurface)
                                .border(BorderStroke(1.dp, LightHairline), shape)
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                        ) {
                            ancestors.forEachIndexed { depth, ancestor ->
                                Text(
                                    text = ancestor.name,
                                    style = type.caption,
                                    color = LightTextTertiary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 44.dp)
                                        .clickable(role = Role.Button) { onAncestorSelected(ancestor.id) }
                                        .padding(start = (depth * 14).dp, top = 13.dp),
                                )
                            }
                            // Where this structure sits beneath them: last, and in the accent.
                            state.title?.let {
                                Text(
                                    it,
                                    style = type.latin.copy(fontSize = 14.sp),
                                    color = AccentOnLight,
                                    modifier = Modifier.padding(start = (ancestors.size * 14).dp, top = 8.dp, bottom = 10.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text.uppercase(), style = LocalAnatomyType.current.label, color = LightTextTertiary)
}

@Composable
private fun Note(text: String) {
    Text(text, style = LocalAnatomyType.current.body, color = LightTextTertiary, modifier = Modifier.padding(22.dp))
}
