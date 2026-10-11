package com.ptk.anatomypro.feature.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import anatomypro.shared.feature_quiz.generated.resources.Res
import anatomypro.shared.feature_quiz.generated.resources.summary_again
import anatomypro.shared.feature_quiz.generated.resources.summary_done
import anatomypro.shared.feature_quiz.generated.resources.summary_per_question
import anatomypro.shared.feature_quiz.generated.resources.summary_percent
import anatomypro.shared.feature_quiz.generated.resources.summary_perfect
import anatomypro.shared.feature_quiz.generated.resources.summary_review
import anatomypro.shared.feature_quiz.generated.resources.summary_score
import anatomypro.shared.feature_quiz.generated.resources.summary_seconds
import anatomypro.shared.feature_quiz.generated.resources.summary_time_label
import anatomypro.shared.feature_quiz.generated.resources.summary_title
import com.ptk.anatomypro.core.designsystem.CorrectText
import com.ptk.anatomypro.core.designsystem.Hairline
import com.ptk.anatomypro.core.designsystem.IncorrectAmber
import com.ptk.anatomypro.core.designsystem.IncorrectText
import com.ptk.anatomypro.core.designsystem.LocalAnatomyType
import com.ptk.anatomypro.core.designsystem.Overline
import com.ptk.anatomypro.core.designsystem.Panel
import com.ptk.anatomypro.core.designsystem.PrimaryButton
import com.ptk.anatomypro.core.designsystem.QuietAction
import com.ptk.anatomypro.core.designsystem.ScreenFooter
import com.ptk.anatomypro.core.designsystem.TextPrimary
import com.ptk.anatomypro.core.designsystem.TextSecondary
import com.ptk.anatomypro.core.designsystem.TextTertiary
import org.jetbrains.compose.resources.stringResource

/**
 * Prototype screen 13: the session's summary.
 *
 * The prototype's page: the score as a large figure, the time in tiles, and what to go over
 * again as cards with an amber edge and a cross. It also compares with the last session,
 * shows the streak, lists what was mastered and offers to repeat only the misses; none of
 * those is in the summary the repository returns, so they are left out.
 */
@Composable
fun SummaryScreen(
    finished: QuizStage.Finished,
    onAgain: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = LocalAnatomyType.current
    val summary = finished.summary
    val seconds = (summary.elapsedMillis / 1000).toInt()

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Overline(stringResource(Res.string.summary_title).uppercase(), color = TextSecondary)

            val spoken = stringResource(Res.string.summary_score, summary.correct, summary.total)
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.clearAndSetSemantics { }) {
                    Text(summary.correct.toString(), style = type.value.copy(fontSize = 56.sp, lineHeight = 58.sp), color = TextPrimary)
                    Text("/${summary.total}", style = type.value.copy(fontSize = 22.sp, lineHeight = 30.sp), color = TextTertiary, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(
                    stringResource(Res.string.summary_percent, (summary.fraction * 100).toInt()),
                    style = type.bodyStrong.copy(fontSize = 14.sp),
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 10.dp).clearAndSetSemantics { },
                )
            }

            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(
                    stringResource(Res.string.summary_time_label),
                    "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
                    Modifier.weight(1f).fillMaxHeight(),
                )
                Tile(
                    stringResource(Res.string.summary_per_question),
                    stringResource(Res.string.summary_seconds, if (summary.total > 0) seconds / summary.total else 0),
                    Modifier.weight(1f).fillMaxHeight(),
                )
            }

            if (summary.needsReview.isEmpty()) {
                // An empty session has nothing to review either, and was not perfect.
                if (summary.total > 0) Text("✓  " + stringResource(Res.string.summary_perfect), style = type.body, color = CorrectText)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Overline("${stringResource(Res.string.summary_review)} · ${summary.needsReview.size}", color = IncorrectText)
                    HorizontalDivider(color = Hairline, modifier = Modifier.weight(1f))
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (structure in summary.needsReview) {
                        // An amber edge and a cross: marked twice, so not by colour alone (§12).
                        Panel(spacing = 2.dp) {
                            Row(modifier = Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.width(3.dp).fillMaxHeight().background(IncorrectAmber))
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(structure.name, style = type.latin.copy(fontSize = 15.sp), color = TextPrimary)
                                    val latin = structure.latinName
                                    if (latin != null && latin != structure.name) Text(latin, style = type.note, color = TextSecondary)
                                }
                                Text("✕", style = type.code, color = IncorrectText, modifier = Modifier.clearAndSetSemantics { })
                            }
                        }
                    }
                }
            }
        }

        ScreenFooter {
            PrimaryButton(stringResource(Res.string.summary_again), onClick = onAgain)
            QuietAction(stringResource(Res.string.summary_done), onClick = onDone)
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    val type = LocalAnatomyType.current
    Panel(modifier = modifier, spacing = 4.dp) {
        Text(label, style = type.code, color = TextSecondary)
        Text(value, style = type.value.copy(fontSize = 19.sp, lineHeight = 24.sp), color = TextPrimary)
    }
}
