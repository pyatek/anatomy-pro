package com.ptk.anatomypro.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import anatomypro.shared.generated.resources.Res
import anatomypro.shared.generated.resources.tab_atlas
import anatomypro.shared.generated.resources.tab_profile
import anatomypro.shared.generated.resources.tab_ranking
import anatomypro.shared.generated.resources.tab_test
import anatomypro.shared.generated.resources.tab_today
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.NavHairline
import com.ptk.anatomypro.core.designsystem.NavSurface
import com.ptk.anatomypro.core.designsystem.TextTertiary
import org.jetbrains.compose.resources.stringResource

/**
 * The prototype's bottom bar.
 *
 * Icons are drawn rather than shipped as assets: they are five simple line figures on a
 * 24-unit grid, and drawing them keeps the stroke weight tied to the design instead of to
 * whatever a rasteriser decides at each density.
 */
@Composable
fun AnatomyBottomBar(
    selected: TopLevel,
    onSelect: (TopLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = NavSurface, modifier = modifier.fillMaxWidth()) {
        Column {
            Canvas(Modifier.fillMaxWidth().height(1.dp)) {
                drawRect(color = NavHairline, size = size)
            }
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 77.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (destination in TopLevel.entries) {
                    val isSelected = destination == selected
                    val tint = if (isSelected) Accent else TextTertiary
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            // §12 asks for 44dp minimum targets; the prototype's own row
                            // is 56, so the accessible size is the designed size.
                            .heightIn(min = 56.dp)
                            .clickable(
                                role = Role.Tab,
                                onClick = { onSelect(destination) },
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
                    ) {
                        Canvas(Modifier.size(21.dp)) { drawIcon(destination, tint) }
                        Text(
                            text = destination.label(),
                            color = tint,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

/** Stroke weight and joins as the prototype specifies them, on its 24-unit grid. */
private fun DrawScope.iconStroke(scale: Float) = Stroke(
    width = 1.7f * scale,
    cap = StrokeCap.Round,
    join = StrokeJoin.Round,
)

private fun DrawScope.drawIcon(destination: TopLevel, tint: Color) {
    val s = size.minDimension / 24f
    val stroke = iconStroke(s)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(tint, Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), stroke.width, StrokeCap.Round)
    fun box(x: Float, y: Float, w: Float, h: Float, r: Float, fill: Boolean = false) =
        drawRoundRect(
            color = tint,
            topLeft = Offset(x * s, y * s),
            size = Size(w * s, h * s),
            cornerRadius = CornerRadius(r * s, r * s),
            style = if (fill) androidx.compose.ui.graphics.drawscope.Fill else stroke,
        )
    fun ring(cx: Float, cy: Float, r: Float) =
        drawCircle(tint, r * s, Offset(cx * s, cy * s), style = stroke)

    when (destination) {
        // A calendar with one day marked — today.
        TopLevel.Today -> {
            box(3.5f, 5.5f, 17f, 15f, 2.5f)
            line(3.5f, 10.5f, 20.5f, 10.5f)
            line(8f, 3f, 8f, 7f)
            line(16f, 3f, 16f, 7f)
            box(7f, 14f, 4f, 3.5f, 1f, fill = true)
        }
        // A standing figure: the atlas is a body.
        TopLevel.Atlas -> {
            ring(12f, 5f, 2.6f)
            box(8.6f, 9f, 6.8f, 8f, 3f)
            line(6f, 10.5f, 6f, 16f)
            line(18f, 10.5f, 18f, 16f)
            line(10.2f, 17f, 10.2f, 21f)
            line(13.8f, 17f, 13.8f, 21f)
        }
        // A checklist: questions answered.
        TopLevel.Test -> {
            box(3.5f, 4.5f, 5f, 5f, 1.2f)
            box(3.5f, 14.5f, 5f, 5f, 1.2f)
            line(4.8f, 6.9f, 6f, 8.2f)
            line(6f, 8.2f, 8f, 5.8f)
            line(12f, 7f, 20.5f, 7f)
            line(12f, 17f, 20.5f, 17f)
        }
        TopLevel.Ranking -> {
            box(3.5f, 14f, 4.5f, 6.5f, 1f)
            box(9.8f, 8f, 4.5f, 12.5f, 1f)
            box(16.1f, 11f, 4.5f, 9.5f, 1f)
        }
        TopLevel.Profile -> {
            ring(12f, 8f, 3.6f)
            // The shoulders arc, drawn as two lines and a curve's worth of segments would
            // be heavier than it needs to be at 21dp; an arc reads identically.
            drawArc(
                color = tint,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(5f * s, 14.9f * s),
                size = Size(14f * s, 11.2f * s),
                style = stroke,
            )
        }
    }
}

/** The tab's label, in the interface language. */
@Composable
private fun TopLevel.label(): String = stringResource(
    when (this) {
        TopLevel.Today -> Res.string.tab_today
        TopLevel.Atlas -> Res.string.tab_atlas
        TopLevel.Test -> Res.string.tab_test
        TopLevel.Ranking -> Res.string.tab_ranking
        TopLevel.Profile -> Res.string.tab_profile
    },
)
