package com.ptk.anatomypro.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The prototype's recurring pieces (spec §19), so a screen is assembled from them rather
 * than redrawn. Measurements are the prototype's: 12 dp corners on controls, 14 dp on
 * panels, 52 dp buttons, 60 dp rows. Every one of them clears §12's 44 dp.
 */

private val ControlShape = RoundedCornerShape(12.dp)
private val PanelShape = RoundedCornerShape(14.dp)

/** An overline in the monospaced voice: "STEP 2 OF 3", "DAILY QUIZ". */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = TextTertiary) {
    Text(text, style = LocalAnatomyType.current.label, color = color, modifier = modifier)
}

/**
 * Where the user is in a flow of [stepCount] steps: the label, and a bar with one segment
 * per step. The bar repeats what the label says, so it is hidden from a screen reader.
 */
@Composable
fun StepHeader(
    label: String,
    step: Int,
    stepCount: Int,
    modifier: Modifier = Modifier,
    backLabel: String? = null,
    onBack: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Overline(label)
            if (backLabel != null) {
                Text(
                    backLabel,
                    style = LocalAnatomyType.current.caption,
                    color = Accent,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clickable(role = Role.Button, onClick = onBack)
                        .padding(start = 24.dp, top = 13.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (index in 1..stepCount) {
                Box(
                    Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (index <= step) Accent else Hairline),
                )
            }
        }
    }
}

/**
 * The bar across the top of a screen that was pushed: a way back, and what the screen is,
 * in the monospaced voice. [backLabel] is what the chevron is called aloud.
 */
@Composable
fun TopBar(title: String, backLabel: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(44.dp)
                .clickable(role = Role.Button, onClickLabel = backLabel, onClick = onBack)
                .semantics { contentDescription = backLabel },
            contentAlignment = Alignment.Center,
        ) {
            Text("‹", style = LocalAnatomyType.current.title, color = TextStrong, modifier = Modifier.clearAndSetSemantics { })
        }
        Overline(title, color = TextSecondary, modifier = Modifier.align(Alignment.Center))
    }
}

/**
 * Two or three views of one thing, one of them showing: the boards, the quiz formats.
 * The chosen one is filled as well as coloured, and each is a tab to a screen reader.
 */
@Composable
fun SegmentedTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val outer = RoundedCornerShape(11.dp)
    val inner = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier.fillMaxWidth().clip(outer).background(Surface).border(BorderStroke(1.dp, Hairline), outer).padding(3.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val chosen = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(inner)
                    .then(if (chosen) Modifier.background(Accent) else Modifier)
                    .selectable(selected = chosen, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = if (chosen) LocalAnatomyType.current.bodyStrong else LocalAnatomyType.current.caption,
                    color = if (chosen) OnAccent else TextSecondary,
                )
            }
        }
    }
}

/** A square with two or three letters in it, standing for a person or a kind of thing. */
@Composable
fun Monogram(text: String, modifier: Modifier = Modifier, fill: Color = MonogramFill, color: Color = TextSecondary, size: Dp = 32.dp) {
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(size / 4)).background(fill).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = LocalAnatomyType.current.code, color = color)
    }
}

/** A square control holding a glyph or a short code: search, layers, settings. [label] is what it is called aloud. */
@Composable
fun SquareButton(glyph: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(shape)
            .background(Surface)
            .border(BorderStroke(1.dp, Hairline), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            style = if (glyph.length > 1) LocalAnatomyType.current.code else LocalAnatomyType.current.button,
            color = TextStrong,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/** The prototype's switch: accent when on, a quiet outline when off. Its row says the state in words. */
@Composable
fun anatomySwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedTrackColor = Accent,
    checkedThumbColor = OnAccent,
    checkedBorderColor = Accent,
    uncheckedTrackColor = MonogramFill,
    uncheckedThumbColor = TextTertiary,
    uncheckedBorderColor = ControlOutline,
    disabledUncheckedTrackColor = Surface,
    disabledUncheckedThumbColor = Hairline,
    disabledUncheckedBorderColor = Hairline,
)

/** A numbered line in a list of rules or reasons. */
@Composable
fun NumberedLine(number: Int, text: String, modifier: Modifier = Modifier) {
    val type = LocalAnatomyType.current
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(number.toString().padStart(2, '0'), style = type.value, color = TextTertiary, modifier = Modifier.clearAndSetSemantics { })
        Text(text, style = type.caption, color = TextStrong, modifier = Modifier.weight(1f))
    }
}

/** A small outlined tag: "FREE", "LOCKED", "YOU". Words, so never colour alone (§12). */
@Composable
fun Badge(text: String, color: Color, outline: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = LocalAnatomyType.current.code,
        color = color,
        modifier = modifier
            .border(BorderStroke(1.dp, outline), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

/**
 * A row that is chosen or not: a tick box, a Latin name leading, the local name beneath.
 *
 * A chosen card differs by its tick as well as its border and fill, and is a checkbox to a
 * screen reader.
 */
@Composable
fun ChoiceCard(
    selected: Boolean,
    onToggle: () -> Unit,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    val type = LocalAnatomyType.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(ControlShape)
            .background(if (selected) SurfaceRaised else Surface)
            .border(BorderStroke(1.dp, if (selected) Accent else Hairline), ControlShape)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .then(
                    if (selected) Modifier.background(Accent)
                    else Modifier.border(BorderStroke(2.dp, ControlOutline), RoundedCornerShape(6.dp)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Text("✓", style = type.bodyStrong, color = OnAccent)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = type.latin, color = TextPrimary)
            if (subtitle != null) Text(subtitle, style = type.caption, color = TextSecondary)
        }
        trailing()
    }
}

/**
 * One of several, exactly one of which is chosen: a language, a way of showing names.
 *
 * The chosen card carries [selectedBadge] in words beside its filled dot (§12), and each is
 * a radio button to a screen reader. [serifTitle] is for a title that is Latin.
 */
@Composable
fun RadioCard(
    selected: Boolean,
    onSelect: () -> Unit,
    title: String,
    subtitle: String?,
    selectedBadge: String,
    modifier: Modifier = Modifier,
    serifTitle: Boolean = false,
) {
    val type = LocalAnatomyType.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(ControlShape)
            .background(if (selected) SurfaceRaised else Surface)
            .border(BorderStroke(1.dp, if (selected) Accent else Hairline), ControlShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(22.dp).border(BorderStroke(2.dp, if (selected) Accent else ControlOutline), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Accent))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = if (serifTitle) type.latin else type.button, color = TextPrimary)
            if (subtitle != null) Text(subtitle, style = type.note, color = TextSecondary)
        }
        if (selected) Badge(selectedBadge, color = Accent, outline = AccentMuted)
    }
}

/** The one thing a screen most wants pressed. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(ControlShape)
            .background(if (enabled) Accent else Hairline)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = LocalAnatomyType.current.button, color = if (enabled) OnAccent else TextTertiary, textAlign = TextAlign.Center)
    }
}

/** A second choice beside or beneath the primary one. */
@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(ControlShape)
            .border(BorderStroke(1.dp, if (enabled) ControlOutline else Hairline), ControlShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = LocalAnatomyType.current.button, color = if (enabled) TextPrimary else TextTertiary, textAlign = TextAlign.Center)
    }
}

/** A button that belongs to one row: "Download", "Delete", "Stop". Still 44 dp tall (§12). */
@Composable
fun RowAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = TextPrimary) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .border(BorderStroke(1.dp, ControlOutline), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = LocalAnatomyType.current.caption.copy(fontWeight = FontWeight.SemiBold), color = color)
    }
}

/** A way on that is not the point of the screen: "Continue without the pack". */
@Composable
fun QuietAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = TextSecondary) {
    Box(
        modifier = modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = LocalAnatomyType.current.body, color = color, textAlign = TextAlign.Center)
    }
}

/** A glyph in a filled disc: the tick of a right answer, the cross of a wrong one. */
@Composable
fun GlyphDisc(glyph: String, fill: Color, ink: Color, modifier: Modifier = Modifier, size: Dp = 26.dp) {
    Box(modifier = modifier.size(size).clip(CircleShape).background(fill).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Text(glyph, style = LocalAnatomyType.current.code.copy(fontWeight = FontWeight.Bold, fontSize = (size.value * 0.55f).sp), color = ink)
    }
}

/**
 * A panel whose outline is dashed: the learner's wrong answer, told from the right one by
 * the line itself and not only its colour (§12).
 */
@Composable
fun DashedPanel(modifier: Modifier = Modifier, outline: Color, spacing: Dp = 5.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(Surface)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawRoundRect(
                    color = outline,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** A bordered panel on the screen's ground. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    outline: Color = Hairline,
    fill: Color = Surface,
    spacing: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(fill)
            .border(BorderStroke(1.dp, outline), PanelShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** The card a screen leads with: the daily quiz. Warm at the top, outlined in a quiet accent. */
@Composable
fun HeroPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(AccentWash, Surface)))
            .border(BorderStroke(1.dp, AccentMuted), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** One fact in a panel: what it is on the left, its value in the monospaced voice on the right. */
@Composable
fun FactRow(label: String, value: String, modifier: Modifier = Modifier) {
    val type = LocalAnatomyType.current
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = type.caption, color = TextSecondary)
        Text(value, style = type.value, color = TextPrimary)
    }
}

/** A screen's bottom: a rule, then whatever is pressed to leave it. */
@Composable
fun ScreenFooter(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = FooterHairline, thickness = 1.dp)
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/**
 * Progress as an arc round whatever is put inside it. [fraction] null draws the ring empty:
 * waiting, or stopped. Decorative — the caller says the figure in words.
 */
@Composable
fun ProgressRing(
    fraction: Float?,
    modifier: Modifier = Modifier,
    diameter: Dp = 190.dp,
    color: Color = Accent,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter).clearAndSetSemantics { }) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Hairline, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(1.dp.toPx()))
            if (fraction != null) {
                drawArc(
                    color, startAngle = -90f, sweepAngle = 360f * fraction.coerceIn(0f, 1f), useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/** A thin bar for a share of a whole: mastery, a download in a list. Decorative. */
@Composable
fun MeterBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Accent) {
    Box(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Hairline).clearAndSetSemantics { }) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(4.dp).background(color))
    }
}
