package com.ptk.anatomypro.feature.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import anatomypro.shared.feature_quiz.generated.resources.Res
import anatomypro.shared.feature_quiz.generated.resources.summary_again
import anatomypro.shared.feature_quiz.generated.resources.summary_done
import anatomypro.shared.feature_quiz.generated.resources.summary_perfect
import anatomypro.shared.feature_quiz.generated.resources.summary_review
import anatomypro.shared.feature_quiz.generated.resources.summary_score
import anatomypro.shared.feature_quiz.generated.resources.summary_time
import anatomypro.shared.feature_quiz.generated.resources.summary_title
import com.ptk.anatomypro.core.designsystem.TextTertiary
import org.jetbrains.compose.resources.stringResource

/**
 * Prototype screen 13: how the session went.
 *
 * Two states (spec §9): a perfect score, which says so, and a score with the structures to
 * go back to, slowest miss first as the summary gives them.
 */
@Composable
fun SummaryScreen(
    finished: QuizStage.Finished,
    onAgain: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = finished.summary

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(Res.string.summary_title), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        Text(
            stringResource(Res.string.summary_score, summary.correct, summary.total),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(Res.string.summary_time, (summary.elapsedMillis / 1000).toInt()),
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
        )

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (summary.needsReview.isEmpty()) {
                // An empty session has nothing to review either, and was not perfect.
                if (summary.total > 0) {
                    Text(stringResource(Res.string.summary_perfect), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text(stringResource(Res.string.summary_review), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                for (structure in summary.needsReview) {
                    Text(structure.name, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAgain, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.summary_again))
            }
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.summary_done))
            }
        }
    }
}
