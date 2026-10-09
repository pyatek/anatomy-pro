package com.ptk.anatomypro.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import anatomypro.shared.core_designsystem.generated.resources.Res
import anatomypro.shared.core_designsystem.generated.resources.side_left
import anatomypro.shared.core_designsystem.generated.resources.side_right
import com.ptk.anatomypro.core.model.Laterality
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The word for a structure's side, or null: a median structure has no side to name.
 *
 * In the interface language, not in Latin. A paired structure is two structures with one
 * name, and every list printed the pair as two identical rows — a left and a right first rib
 * that nothing on screen, and nothing a screen reader said, told apart. Every screen that
 * names a structure takes the word from here, so they cannot come to differ.
 */
fun Laterality.sideResource(): StringResource? = when (this) {
    Laterality.LEFT -> Res.string.side_left
    Laterality.RIGHT -> Res.string.side_right
    Laterality.MEDIAN -> null
}

@Composable
fun sideLabel(laterality: Laterality): String? = laterality.sideResource()?.let { stringResource(it) }

/** A name as it is spoken: with its side after it, when it has one. */
fun withSide(name: String, side: String?): String = if (side == null) name else "$name, $side"

/** The side as a small badge beside a name. Draws nothing for a median structure. */
@Composable
fun SideBadge(laterality: Laterality, modifier: Modifier = Modifier) {
    val side = sideLabel(laterality) ?: return
    Text(
        text = side.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextTertiary,
        modifier = modifier,
    )
}
