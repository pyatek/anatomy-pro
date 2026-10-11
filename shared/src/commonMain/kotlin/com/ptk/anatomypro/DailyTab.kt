package com.ptk.anatomypro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ptk.anatomypro.core.data.repository.AtlasRepository
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.feature.quiz.quizClockRuns
import com.ptk.anatomypro.feature.quiz.structuresHiddenFor
import com.ptk.anatomypro.core.data.AppDependencies
import com.ptk.anatomypro.core.data.model.AppSettings
import com.ptk.anatomypro.core.data.model.QuizTopic
import com.ptk.anatomypro.feature.atlas.scene.FocusRequest
import com.ptk.anatomypro.feature.atlas.scene.RenderState
import com.ptk.anatomypro.feature.daily.DailyStage
import com.ptk.anatomypro.feature.daily.DailyViewModel
import com.ptk.anatomypro.feature.daily.HomeScreen
import com.ptk.anatomypro.feature.daily.HomeViewModel
import com.ptk.anatomypro.feature.daily.LeaderboardScreen
import com.ptk.anatomypro.feature.daily.LeaderboardViewModel
import com.ptk.anatomypro.feature.daily.LobbyScreen
import com.ptk.anatomypro.feature.quiz.QuizSessionScreen
import com.ptk.anatomypro.feature.quiz.quizCanvasFor
import com.ptk.anatomypro.navigation.DailyRoute
import com.ptk.anatomypro.navigation.ProfileRoute
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val DAILY_FOCUS_MILLIS = 400

/** Prototype screen 14. */
@Composable
internal fun HomeRoute(dependencies: AppDependencies, daily: DailyViewModel, navController: NavHostController) {
    val home: HomeViewModel = viewModel { HomeViewModel(dependencies.progress) }
    val homeState by home.state.collectAsState()
    val stage by daily.stage.collectAsState()

    // Coming back to the tab is a reason to look again: a day may have turned.
    LaunchedEffect(daily) { daily.refresh() }

    HomeScreen(
        home = homeState,
        daily = stage,
        onOpenDaily = { navController.navigate(DailyRoute.Lobby) },
        onSubscribe = { navController.navigate(ProfileRoute.Paywall) },
    )
}

/** Prototype screen 15, and the daily quiz itself. */
@Composable
internal fun LobbyRoute(
    daily: DailyViewModel,
    atlas: AtlasRepository?,
    settings: AppSettings,
    navController: NavHostController,
    onPractice: (QuizTopic) -> Unit,
    onLeaderboard: () -> Unit,
) {
    val stage by daily.stage.collectAsState()

    // The clock runs only while this is on screen and the model is drawn: the canvas
    // reloads its pack when it is created, and the server times the whole attempt.
    var canvasReady by remember { mutableStateOf(false) }
    val clockRuns = quizClockRuns(resumed = true, canvasReady = canvasReady)
    DisposableEffect(daily, clockRuns) {
        if (clockRuns) daily.onQuestionShown()
        onDispose { if (clockRuns) daily.onQuestionHidden() }
    }
    val asked = (stage as? DailyStage.Playing)?.asking?.session
    val hidden by produceState(emptySet<StructureId>(), asked?.id, atlas) {
        value = if (asked != null && atlas != null) structuresHiddenFor(atlas, asked) else emptySet()
    }

    val playing = stage as? DailyStage.Playing
    if (playing != null) {
        val canvas = quizCanvasFor(playing.asking)
        QuizSessionScreen(
            stage = playing.asking,
            canvas = { modifier ->
                AnatomyCanvas(
                    modifier = modifier,
                    highlights = canvas.highlights,
                    render = RenderState(hidden = hidden, ghosted = emptySet(), ghostAlpha = 1f),
                    focus = FocusRequest(canvas.focus, DAILY_FOCUS_MILLIS, serial = playing.asking.index),
                    onPicked = { picked -> if (canvas.pickable && picked != null) daily.answer(picked) },
                    onStats = {},
                    onReady = { canvasReady = it },
                )
            },
            onAnswer = daily::answer,
            // The daily has no feedback stage, so there is never a "next" to press.
            onNext = {},
            // One attempt a day: leaving does not end it. The question waits.
            onEnd = { navController.popBackStack() },
        )
        return
    }

    LobbyScreen(
        stage = stage,
        onStart = { daily.start(settings.examinationLocale) },
        onRetry = { if (stage is DailyStage.Failed) daily.retry() else daily.refresh() },
        onPractice = onPractice,
        onSubscribe = { navController.navigate(ProfileRoute.Paywall) },
        onBack = { navController.popBackStack() },
        onLeaderboard = onLeaderboard,
    )
}

/** Prototype screen 16. */
@Composable
internal fun LeaderboardRoute(dependencies: AppDependencies, navController: NavHostController) {
    val model: LeaderboardViewModel = viewModel {
        LeaderboardViewModel(
            leaderboard = dependencies.leaderboard,
            entitlements = dependencies.entitlements,
            // The daily quiz's day is UTC (design spec §9.1), not the user's.
            today = { Clock.System.now().toLocalDateTime(TimeZone.UTC).date },
        )
    }
    val state by model.state.collectAsState()

    LeaderboardScreen(
        state = state,
        onBoard = model::onBoard,
        onRetry = model::onRetry,
        onSubscribe = { navController.navigate(ProfileRoute.Paywall) },
    )
}
