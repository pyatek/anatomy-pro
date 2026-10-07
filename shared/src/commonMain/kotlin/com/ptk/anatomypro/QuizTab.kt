package com.ptk.anatomypro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.ptk.anatomypro.core.data.AppDependencies
import com.ptk.anatomypro.core.data.model.AppSettings
import com.ptk.anatomypro.feature.atlas.scene.FocusRequest
import com.ptk.anatomypro.feature.atlas.scene.RenderState
import com.ptk.anatomypro.feature.quiz.FailedDuring
import com.ptk.anatomypro.feature.quiz.QuizDestination
import com.ptk.anatomypro.feature.quiz.QuizSessionScreen
import com.ptk.anatomypro.feature.quiz.QuizSessionViewModel
import com.ptk.anatomypro.feature.quiz.QuizStage
import com.ptk.anatomypro.feature.quiz.SummaryScreen
import com.ptk.anatomypro.feature.quiz.TopicsScreen
import com.ptk.anatomypro.feature.quiz.TopicsViewModel
import com.ptk.anatomypro.feature.quiz.destinationOf
import com.ptk.anatomypro.feature.quiz.quizCanvasFor
import com.ptk.anatomypro.navigation.ProfileRoute
import com.ptk.anatomypro.navigation.QuizRoute
import kotlinx.coroutines.flow.first

/** How long the camera takes to reach a question's structure. */
private const val FOCUS_MILLIS = 400

/**
 * Keeps navigation where the session says it should be.
 *
 * The session is the truth and the back stack follows it, rather than each screen deciding
 * where to go next. That is also what makes the system back gesture harmless: popping to
 * the topic grid while a question is open lands here, and here sends it straight back
 * (decision 4 — the way out of a session is "End session").
 *
 * Keyed on the destination, not the stage: the clock ticking is not a reason to navigate.
 *
 * Only a route that is on screen navigates. One that is fading out is still composed, and
 * after a tab switch the Test tab's stack is not the live one: navigating from there would
 * push a question onto another tab.
 */
@Composable
private fun FollowSession(stage: QuizStage, here: QuizDestination, navController: NavHostController) {
    val wanted = destinationOf(stage)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(wanted, here, lifecycle) {
        if (wanted == here) return@LaunchedEffect
        // A route that has left never starts again; this effect ends with its composition.
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
        when (wanted) {
            QuizDestination.Topics -> navController.popBackStack(QuizRoute.Topics, inclusive = false)
            is QuizDestination.Session ->
                navController.navigate(QuizRoute.Question(wanted.sessionId, index = 0)) { popUpTo(QuizRoute.Topics) }
            is QuizDestination.Summary ->
                navController.navigate(QuizRoute.Summary(wanted.sessionId)) { popUpTo(QuizRoute.Topics) }
        }
    }
}

/** Prototype screen 08. */
@Composable
internal fun QuizTopicsRoute(
    dependencies: AppDependencies,
    session: QuizSessionViewModel,
    settings: AppSettings,
    navController: NavHostController,
) {
    val stage by session.stage.collectAsState()
    FollowSession(stage, here = QuizDestination.Topics, navController = navController)

    // Keyed on the interface locale: topic titles are loaded once per model.
    val topics: TopicsViewModel = viewModel(key = "quiz-topics-${settings.interfaceLocale}") {
        TopicsViewModel(dependencies.quiz, dependencies.entitlements, settings.interfaceLocale)
    }
    val state by topics.state.collectAsState()

    TopicsScreen(
        state = state,
        startFailed = stage == QuizStage.Failed(FailedDuring.START),
        finishFailed = stage == QuizStage.Failed(FailedDuring.FINISH),
        onFormat = topics::onFormat,
        onTopic = { cell ->
            if (cell.locked) {
                navController.navigate(ProfileRoute.Paywall)
            } else {
                // Names inside the session follow the examination locale, not the interface's (§13).
                session.start(cell.topic.id, state.format, settings.examinationLocale, settings.quizTimerEnabled)
            }
        },
        onRetry = topics::onRetry,
    )
}

/** Prototype screens 09 to 12. */
@Composable
internal fun QuizSessionRoute(session: QuizSessionViewModel, sessionId: String, navController: NavHostController) {
    val stage by session.stage.collectAsState()
    FollowSession(stage, here = QuizDestination.Session(sessionId), navController = navController)

    // The clock runs only while this is on screen and the app is in front (decision 3).
    // Elapsed time is read from a clock, so a question left running behind a backgrounded
    // app would have expired by the time the student came back.
    LifecycleResumeEffect(session) {
        session.onQuestionShown()
        onPauseOrDispose { session.onQuestionHidden() }
    }

    val canvas = quizCanvasFor(stage)
    // A new serial for each question and for its feedback, so the camera is asked again even
    // when two questions in a row frame the whole model.
    val serial = when (val current = stage) {
        is QuizStage.Asking -> current.index * 2
        is QuizStage.Feedback -> current.index * 2 + 1
        else -> 0
    }

    QuizSessionScreen(
        stage = stage,
        canvas = { modifier ->
            AnatomyCanvas(
                modifier = modifier,
                highlights = canvas.highlights,
                render = RenderState.None,
                focus = FocusRequest(canvas.focus, FOCUS_MILLIS, serial),
                // Empty space is not an answer (decision 5).
                onPicked = { picked -> if (canvas.pickable && picked != null) session.answer(picked) },
                onStats = {},
            )
        },
        onAnswer = session::answer,
        onNext = session::next,
        onEnd = session::abandon,
    )
}

/** Prototype screen 13. */
@Composable
internal fun QuizSummaryRoute(
    session: QuizSessionViewModel,
    sessionId: String,
    settings: AppSettings,
    navController: NavHostController,
) {
    val stage by session.stage.collectAsState()
    FollowSession(stage, here = QuizDestination.Summary(sessionId), navController = navController)

    val finished = stage as? QuizStage.Finished ?: return
    SummaryScreen(
        finished = finished,
        onAgain = { session.start(finished.topic, finished.format, settings.examinationLocale, settings.quizTimerEnabled) },
        onDone = session::abandon,
    )
}
