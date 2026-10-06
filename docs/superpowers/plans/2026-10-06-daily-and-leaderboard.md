# Home, Daily Quiz and Leaderboard (Screens 14–16) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the home dashboard (14), the daily quiz lobby and the daily quiz itself (15), and the leaderboard (16), each with a designed state for a user who has not subscribed, and practice mode for when the daily cannot be reached.

**Architecture:** A new `:shared:feature-daily` module. One app-scoped `DailyViewModel` is a state machine over the day's quiz — not subscribed, ready, playing, submitting, done, offline — which screens 14 and 15 both read. The daily has no answer keys on the device, so it has no feedback between questions: it reuses the quiz plan's question screen and canvas mapping by wrapping the day's questions in a `QuizStage.Asking`, and submits every answer at the end. Practice mode is not a second quiz: it picks a topic and starts an ordinary session on the quiz plan's `QuizSessionViewModel`.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform 1.11.1, navigation-compose 2.9.2, kotlinx-datetime 0.8.0, kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-09-24-all-screens-mocked-design.md` §4.2–§4.4 (progress, daily, leaderboard contracts), §9 with §15.3 (states that must exist, including "not subscribed"), §15.4 (practice mode), §10, §11 step 9. Design spec `docs/superpowers/specs/2026-08-29-anatomy-pro-design.md` §9 (server authority, scoring, boards), §12, §14 (daily offline: block entry, offer practice; submit fails: retry), §29.2 (the daily and the leaderboard are paid).

**Prerequisite:** `docs/superpowers/plans/2026-10-06-quiz-flow.md` is executed. This plan uses `QuizStage`, `QuizSessionScreen`, `quizCanvasFor`, `QuizSessionViewModel`, `AtlasQuizSource` and the `ProfileRoute.Paywall` placeholder from it.

## Global Constraints

- **Repository root is `~/StudioProjects/AnatomyPro`.** Never edit `~/Projekty/anatomy pro`.
- **Tests run on two targets.** `./gradlew :shared:<module>:allTests` runs the JVM host and the iOS simulator. A test that passes on one and not the other is a failure.
- **Do not run `allTests` while an app is running on a simulator.** The iOS GPU contract test `reports_a_pick` times out under that contention.
- `./gradlew --stop` between long sessions. This machine runs out of memory.
- **No `Co-Authored-By` or `Claude-Session` trailers on commits.**
- **UI strings are always resources** (all-screens spec §8). English in `values/`, Polish in `values-pl/`. Key names are `<screen>_<purpose>` in lower snake case.
- **ViewModels never produce user-visible text.** Nicknames, topic titles and structure names are data and are shown as given.
- **The daily quiz's day is UTC; the streak's day is the user's** (all-screens spec §4.2). They are never compared.
- **Answer keys never reach the device** (design §9.1). The daily shows no right-or-wrong between questions; the score comes back from `submit`.
- **A free user is the expected case, not an error** (all-screens spec §15.3). "Not subscribed" has its own copy and a way to the paywall on all three screens, and a free user's device never calls `DailyRepository` or `LeaderboardRepository`.
- **§12:** touch targets at least 44 dp; the user's own row on a board is marked in words, not only by colour.
- **Colours come from `core-designsystem`.** Never introduce a colour literal.
- **No Compose UI tests** (all-screens spec §10, decided). Screens are checked by hand in Task 6.
- **Out of scope:** the backend (Phase 3); nickname choice and sign-in (§9.3); the paywall itself (screen 18 — "subscribe" leads to the placeholder); a rule for whether an untimed daily is ranked (§12 says untimed is unranked; the rank is the server's to withhold, and `DailyResult.rank` is already nullable); resuming a half-played daily with its earlier answers after the app is killed — it resumes from the first question.

## Decisions this plan makes

1. **The daily is played in the lobby's destination**, not a separate one. "Running" is one of screen 15's states (spec §9), and one destination keeps one canvas.
2. **The daily has no time limit per question.** The server measures the whole attempt (§9.1); a per-question countdown would be a second clock that disagrees with it.
3. **An answer given within 300 ms of a question appearing is ignored.** With no feedback between questions, a double tap would otherwise answer the next question too.
4. **A failed submit keeps the answers and offers to send them again** (§14). It does not replay the quiz.
5. **Practice picks its own topic** by spec §15.4: the first topic in a system the student is studying and may be quizzed on; otherwise a skeletal topic; otherwise any topic they may be quizzed on. With no such topic, practice is not offered.
6. **Production shows the Today and Ranking placeholders.** Their repositories refuse (spec §6).
7. **The home screen is the streak and the daily's card.** Mastery by system belongs to the profile (screen 17), and is not repeated here.

## Review Focus

1. **A double tap on an answer**: the next question is not answered by it. — Task 2.
2. **Submitting fails after the last question** (the connection dropped mid-attempt): the answers are kept and can be sent again; nothing is replayed. — Task 2.
3. **The connection is lost between the lobby and the start**: starting fails, the lobby says so, and trying again re-reads the day's state. — Task 2.
4. **The user subscribes while a "not subscribed" screen is open**: it becomes the real screen without being reopened. — Tasks 2, 4.
5. **A board the user is not on** (has not played today): the board is shown, and says they are not on it, rather than a row of zeroes. — Task 4.

## File Structure

| File | Responsibility |
|---|---|
| `shared/core-data-fake/.../FakeDailyRepository.kt` + `FakeDailyRepositoryTest.kt` | Modify. The daily's topic can be "whatever the quiz offers first". |
| `settings.gradle.kts`, `shared/build.gradle.kts`, `shared/feature-daily/build.gradle.kts` | Modify / create. |
| `shared/feature-daily/.../Practice.kt` | Create. `practiceTopic`, `dailyIsBuilt`. |
| `shared/feature-daily/.../DailyViewModel.kt` | Create. `DailyStage` and the day's state machine. |
| `shared/feature-daily/.../HomeViewModel.kt` | Create. The streak. |
| `shared/feature-daily/.../LeaderboardViewModel.kt` | Create. |
| `shared/feature-daily/.../HomeScreen.kt`, `LobbyScreen.kt`, `LeaderboardScreen.kt` | Create. Screens 14, 15, 16. |
| `shared/feature-daily/src/commonMain/composeResources/values{,-pl}/strings.xml` | Create. |
| `shared/feature-daily/src/commonTest/...` | Create. `PracticeTest`, `DailyViewModelTest`, `HomeViewModelTest`, `LeaderboardViewModelTest`. |
| `shared/src/commonMain/.../DailyTab.kt`, `App.kt` | Create / modify. The routes. |
| `androidApp/src/debug/.../EntryDependencies.kt` | Modify. Intent extras for the states. |
| `docs/state-of-play.md`, the design spec | Modify. |

Paths abbreviate `shared/feature-daily/src/commonMain/kotlin/com/ptk/anatomypro/feature/daily/` as `.../daily/`.

---

### Task 1: The fake daily can ask about whatever the quiz offers

`FakeDailyRepository` always draws from the topic `costae`. Over the quiz plan's `AtlasQuizSource` there is no such topic, so a debug build's daily could not start.

**Files:**
- Modify: `shared/core-data-fake/src/commonMain/kotlin/com/ptk/anatomypro/core/data/fake/FakeDailyRepository.kt`
- Test: `shared/core-data-fake/src/commonTest/kotlin/com/ptk/anatomypro/core/data/fake/FakeDailyRepositoryTest.kt`

**Interfaces:**
- Produces: `FakeDailyRepository(behaviour, available, alreadyAttempted, date, quiz, topic: QuizTopicId? = QuizTopicId("costae"))`. `null` means the first topic `quiz` offers.

- [ ] **Step 1: Write the failing test**

Add to `FakeDailyRepositoryTest`:

```kotlin
    @Test
    fun with_no_topic_named_the_daily_asks_about_the_first_one_the_quiz_offers() = runTest {
        // A debug build's quiz asks about the installed atlas, where "costae" may not exist.
        val quiz = FakeQuizRepository(source = AtlasQuizSource(FakeAtlasRepository()))
        val repository = FakeDailyRepository(quiz = quiz, topic = null)

        val started = repository.start(locale = "la")

        val first = quiz.topics("la").first()
        val asked = (started.questions.first() as QuizQuestion.NameTheHighlighted).highlighted
        assertTrue(quiz.startSession(first.id, com.ptk.anatomypro.core.data.model.QuizFormat.NAME_THE_HIGHLIGHTED, 50, 1, "la")
            .questions.any { (it as QuizQuestion.NameTheHighlighted).highlighted == asked })
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :shared:core-data-fake:allTests`
Expected: compilation FAILS with `No parameter with name 'topic'`.

- [ ] **Step 3: Implement**

In `FakeDailyRepository.kt`, add a constructor parameter after `quiz`:

```kotlin
    /** What the day's questions are about; null is the first topic [quiz] offers. */
    private val topic: QuizTopicId? = QuizTopicId("costae"),
```

and in `start`, replace `topic = QuizTopicId("costae"),` with:

```kotlin
            topic = topic ?: quiz.topics(locale).first().id,
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :shared:core-data-fake:allTests`
Expected: BUILD SUCCESSFUL on both targets, every existing daily test unchanged.

- [ ] **Step 5: Commit**

```bash
git add shared/core-data-fake
git commit -m "test(fake): the daily can ask about whatever the quiz offers first"
```

---

### Task 2: The module, practice's topic, and the day's state machine

**Files:**
- Modify: `settings.gradle.kts`, `shared/build.gradle.kts`
- Create: `shared/feature-daily/build.gradle.kts`
- Create: `.../daily/Practice.kt`, `.../daily/DailyViewModel.kt`
- Test: `shared/feature-daily/src/commonTest/kotlin/com/ptk/anatomypro/feature/daily/PracticeTest.kt`, `DailyViewModelTest.kt`

**Interfaces:**
- Consumes: `DailyRepository.today / start / submit`, `DailyQuiz`, `DailyResult`, `EntitlementRepository.entitlements`, `Entitlements`, `FREE_SYSTEMS`, `QuizRepository.topics`, `SettingsRepository.settings`, `QuizStage.Asking` (quiz plan), the fakes.
- Produces:
  ```kotlin
  fun practiceTopic(topics: List<QuizTopic>, studied: Set<String>, entitlements: Entitlements): QuizTopic?
  fun dailyIsBuilt(daily: DailyRepository, leaderboard: LeaderboardRepository): Boolean

  const val MIN_ANSWER_MILLIS = 300L
  enum class DailyFailure { LOAD, START, SUBMIT }

  sealed interface DailyStage {
      data object Loading : DailyStage
      data object NotSubscribed : DailyStage
      data class Ready(val date: LocalDate, val questionCount: Int) : DailyStage
      /** [asking] is what the quiz's question screen draws. Its `remainingMillis` is always null. */
      data class Playing(val asking: QuizStage.Asking) : DailyStage
      data object Submitting : DailyStage
      data class Done(val result: DailyResult) : DailyStage
      /** [practice] is the topic practice mode would use, or null when there is none. */
      data class Offline(val practice: QuizTopic?) : DailyStage
      data class Failed(val during: DailyFailure) : DailyStage
  }

  class DailyViewModel(
      daily: DailyRepository, entitlements: EntitlementRepository,
      quiz: QuizRepository, settings: SettingsRepository,
  ) : ViewModel() {
      val stage: StateFlow<DailyStage>
      fun refresh()
      fun start(locale: String)
      fun onQuestionShown()
      fun onQuestionHidden()
      fun answer(chosen: StructureId)
      fun retry()
  }
  ```

- [ ] **Step 1: Create the module**

`settings.gradle.kts`, add: `include(":shared:feature-daily")`

`shared/build.gradle.kts`, in `commonMain.dependencies`, add: `api(project(":shared:feature-daily"))`

`shared/feature-daily/build.gradle.kts`:

```kotlin
plugins { id("anatomypro.kmp.compose") }

kotlin {
    android { namespace = "com.ptk.anatomypro.feature.daily" }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.components.resources)
            api(project(":shared:core-data"))
            // The daily is played on the quiz's question screen, as a QuizStage.Asking.
            api(project(":shared:feature-quiz"))
            implementation(project(":shared:core-designsystem"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(project(":shared:core-data-fake"))
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
```

- [ ] **Step 2: Write the failing tests for practice's topic**

`PracticeTest.kt`:

```kotlin
package com.ptk.anatomypro.feature.daily

import com.ptk.anatomypro.core.data.NotBuiltDailyRepository
import com.ptk.anatomypro.core.data.NotBuiltLeaderboardRepository
import com.ptk.anatomypro.core.data.fake.FakeDailyRepository
import com.ptk.anatomypro.core.data.fake.FakeLeaderboardRepository
import com.ptk.anatomypro.core.data.model.Entitlements
import com.ptk.anatomypro.core.data.model.QuizTopic
import com.ptk.anatomypro.core.model.QuizTopicId
import com.ptk.anatomypro.core.model.SystemId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PracticeTest {

    private fun topic(id: String, system: String) =
        QuizTopic(QuizTopicId(id), title = id, system = SystemId(system), structureCount = 8, mastery = 0f)

    private val ribs = topic("costae", "skeletal-system")
    private val chest = topic("musculi-thoracis", "muscular-system")
    private val nerves = topic("nervi-craniales", "nervous-system")

    private val free = Entitlements(subscribed = false, ownedSystems = emptySet())
    private val subscribed = Entitlements(subscribed = true, ownedSystems = emptySet())

    @Test
    fun practice_is_on_a_system_the_student_said_they_study() {
        // All-screens spec §15.4. Order in the list is not what decides.
        assertEquals(chest, practiceTopic(listOf(ribs, chest, nerves), studied = setOf("muscular-system"), subscribed))
    }

    @Test
    fun a_studied_system_the_student_may_not_be_quizzed_on_is_passed_over() {
        assertEquals(ribs, practiceTopic(listOf(chest, ribs), studied = setOf("muscular-system"), free))
    }

    @Test
    fun with_nothing_studied_practice_is_on_the_skeleton() {
        assertEquals(ribs, practiceTopic(listOf(chest, ribs), studied = emptySet(), subscribed))
    }

    @Test
    fun with_no_skeletal_topic_it_is_any_topic_allowed() {
        assertEquals(chest, practiceTopic(listOf(chest, nerves), studied = emptySet(), subscribed))
    }

    @Test
    fun with_no_topic_allowed_there_is_no_practice() {
        assertNull(practiceTopic(listOf(chest, nerves), studied = setOf("muscular-system"), free))
        assertNull(practiceTopic(emptyList(), studied = emptySet(), subscribed))
    }

    @Test
    fun the_daily_is_not_built_where_either_repository_refuses() {
        // All-screens spec §6.
        assertFalse(dailyIsBuilt(NotBuiltDailyRepository, FakeLeaderboardRepository()))
        assertFalse(dailyIsBuilt(FakeDailyRepository(), NotBuiltLeaderboardRepository))
        assertTrue(dailyIsBuilt(FakeDailyRepository(), FakeLeaderboardRepository()))
    }
}
```

- [ ] **Step 3: Run them to verify they fail, then implement**

Run: `./gradlew :shared:feature-daily:allTests`
Expected: compilation FAILS with `Unresolved reference 'practiceTopic'` and `'dailyIsBuilt'`.

`.../daily/Practice.kt`:

```kotlin
package com.ptk.anatomypro.feature.daily

import com.ptk.anatomypro.core.data.NotBuiltDailyRepository
import com.ptk.anatomypro.core.data.NotBuiltLeaderboardRepository
import com.ptk.anatomypro.core.data.model.Entitlements
import com.ptk.anatomypro.core.data.model.FREE_SYSTEMS
import com.ptk.anatomypro.core.data.model.QuizTopic
import com.ptk.anatomypro.core.data.repository.DailyRepository
import com.ptk.anatomypro.core.data.repository.LeaderboardRepository

/**
 * The topic practice mode uses, or null when there is nothing to practise on.
 *
 * Practice is an ordinary topic session offered when the daily cannot be reached (design
 * spec §9.1, §14; all-screens spec §15.4). It is on something the student is studying if
 * they may be quizzed on it; otherwise on the skeleton, which everyone may be; otherwise on
 * anything they may be.
 */
fun practiceTopic(topics: List<QuizTopic>, studied: Set<String>, entitlements: Entitlements): QuizTopic? {
    val allowed = topics.filter { entitlements.allows(it.system) }
    return allowed.firstOrNull { it.system.value in studied }
        ?: allowed.firstOrNull { it.system in FREE_SYSTEMS }
        ?: allowed.firstOrNull()
}

/**
 * Whether there is a daily quiz and a leaderboard to show.
 *
 * Production's repositories for both throw on first use, by design (all-screens spec §6).
 * There the Today and Ranking tabs keep their placeholders.
 */
fun dailyIsBuilt(daily: DailyRepository, leaderboard: LeaderboardRepository): Boolean =
    daily !is NotBuiltDailyRepository && leaderboard !is NotBuiltLeaderboardRepository
```

Run: `./gradlew :shared:feature-daily:allTests`
Expected: BUILD SUCCESSFUL, `PracticeTest` six passing per target.

- [ ] **Step 4: Write the failing tests for the state machine**

`DailyViewModelTest.kt`:

```kotlin
package com.ptk.anatomypro.feature.daily

import com.ptk.anatomypro.core.data.fake.FakeBehaviour
import com.ptk.anatomypro.core.data.fake.FakeDailyRepository
import com.ptk.anatomypro.core.data.fake.FakeEntitlementRepository
import com.ptk.anatomypro.core.data.fake.FakeQuizRepository
import com.ptk.anatomypro.core.data.fake.FakeSettingsRepository
import com.ptk.anatomypro.core.data.model.AppSettings
import com.ptk.anatomypro.core.data.model.DailyResult
import com.ptk.anatomypro.core.data.model.Entitlements
import com.ptk.anatomypro.core.data.model.QuizAnswer
import com.ptk.anatomypro.core.data.model.QuizQuestion
import com.ptk.anatomypro.core.data.model.SubscriptionPlan
import com.ptk.anatomypro.core.data.repository.DailyRepository
import com.ptk.anatomypro.core.model.StructureId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DailyViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val subscriber = Entitlements(subscribed = true, ownedSystems = emptySet())

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun model(
        daily: DailyRepository = FakeDailyRepository(),
        entitlements: FakeEntitlementRepository = FakeEntitlementRepository(initial = subscriber),
        settings: AppSettings = AppSettings(),
    ) = DailyViewModel(daily, entitlements, FakeQuizRepository(), FakeSettingsRepository(settings))

    private fun QuizQuestion.right(): StructureId = when (this) {
        is QuizQuestion.NameTheHighlighted -> correct.id
        is QuizQuestion.TapTheStructure -> target
    }

    /** Starts the daily with its first question on screen and old enough to be answered. */
    private fun TestScope.playing(model: DailyViewModel): DailyStage.Playing {
        runCurrent()
        model.start(locale = "la")
        runCurrent()
        model.onQuestionShown()
        advanceTimeBy(MIN_ANSWER_MILLIS)
        runCurrent()
        return assertIs<DailyStage.Playing>(model.stage.value)
    }

    /** Answers the question on screen rightly, then waits until the next may be answered. */
    private fun TestScope.answerRight(model: DailyViewModel) {
        val playing = assertIs<DailyStage.Playing>(model.stage.value)
        model.answer(playing.asking.question.right())
        advanceTimeBy(MIN_ANSWER_MILLIS)
        runCurrent()
    }

    // --- §15.3: not subscribed --------------------------------------------------------

    @Test
    fun a_free_user_is_told_they_are_not_subscribed_and_the_server_is_never_asked() = runTest(dispatcher) {
        var asked = 0
        val counting = object : DailyRepository by FakeDailyRepository() {
            override suspend fun today() = FakeDailyRepository().today().also { asked++ }
        }

        val model = model(daily = counting, entitlements = FakeEntitlementRepository())
        runCurrent()

        assertEquals(DailyStage.NotSubscribed, model.stage.value)
        assertEquals(0, asked)
    }

    @Test
    fun subscribing_while_the_screen_is_open_shows_the_daily() = runTest(dispatcher) {
        // Review Focus 4.
        val entitlements = FakeEntitlementRepository()
        val model = model(entitlements = entitlements)
        runCurrent()

        entitlements.purchase(SubscriptionPlan("monthly", "Miesięcznie", "29 zł", periodMonths = 1))
        runCurrent()

        assertIs<DailyStage.Ready>(model.stage.value)
    }

    // --- §9, screens 14 and 15 --------------------------------------------------------

    @Test
    fun a_day_not_yet_played_is_ready_and_says_how_many_questions() = runTest(dispatcher) {
        val model = model()
        runCurrent()

        assertEquals(10, assertIs<DailyStage.Ready>(model.stage.value).questionCount)
    }

    @Test
    fun a_day_already_played_shows_its_result() = runTest(dispatcher) {
        val model = model(daily = FakeDailyRepository(alreadyAttempted = true))
        runCurrent()

        val result = assertIs<DailyStage.Done>(model.stage.value).result
        assertEquals(7, result.correct)
        assertEquals(412, result.rank)
    }

    @Test
    fun offline_is_a_state_and_offers_practice_on_something_the_student_studies() = runTest(dispatcher) {
        // §14: block entry with a clear message; offer practice mode.
        val model = model(
            daily = FakeDailyRepository(available = false),
            settings = AppSettings(studiedSystems = setOf("muscular-system")),
        )
        runCurrent()

        val offline = assertIs<DailyStage.Offline>(model.stage.value)
        assertEquals("musculi-thoracis", offline.practice?.id?.value)
    }

    @Test
    fun a_day_that_cannot_be_read_is_a_failure_that_can_be_retried() = runTest(dispatcher) {
        var broken = true
        val real = FakeDailyRepository()
        val flaky = object : DailyRepository by real {
            override suspend fun today() = if (broken) throw IllegalStateException("no answer") else real.today()
        }
        val model = model(daily = flaky)
        runCurrent()
        assertEquals(DailyStage.Failed(DailyFailure.LOAD), model.stage.value)

        broken = false
        model.retry()
        runCurrent()

        assertIs<DailyStage.Ready>(model.stage.value)
    }

    // --- playing ----------------------------------------------------------------------

    @Test
    fun starting_shows_the_first_question_with_no_time_limit() = runTest(dispatcher) {
        // Decision 2: the server times the attempt; there is no second clock.
        val playing = playing(model())

        assertEquals(0, playing.asking.index)
        assertEquals(10, playing.asking.total)
        assertNull(playing.asking.remainingMillis)
    }

    @Test
    fun an_answer_moves_straight_to_the_next_question_with_no_verdict() = runTest(dispatcher) {
        // §9.1: answer keys never leave the server, so there is nothing to say yet.
        val model = model()
        playing(model)

        answerRight(model)

        assertEquals(1, assertIs<DailyStage.Playing>(model.stage.value).asking.index)
    }

    @Test
    fun a_second_tap_straight_after_an_answer_does_not_answer_the_next_question() = runTest(dispatcher) {
        // Review Focus 1.
        val model = model()
        val first = playing(model)

        model.answer(first.asking.question.right())
        model.answer(first.asking.question.right())
        runCurrent()

        assertEquals(1, assertIs<DailyStage.Playing>(model.stage.value).asking.index)
    }

    @Test
    fun the_last_answer_submits_them_all_and_shows_the_result() = runTest(dispatcher) {
        val model = model()
        playing(model)

        repeat(10) { answerRight(model) }
        runCurrent()

        val result = assertIs<DailyStage.Done>(model.stage.value).result
        assertEquals(10, result.correct)
        assertEquals(10, result.total)
    }

    @Test
    fun each_answer_carries_how_long_its_question_was_on_screen() = runTest(dispatcher) {
        var submitted: List<QuizAnswer> = emptyList()
        val real = FakeDailyRepository()
        val recording = object : DailyRepository by real {
            override suspend fun submit(answers: List<QuizAnswer>): DailyResult {
                submitted = answers
                return real.submit(answers)
            }
        }
        val model = model(daily = recording)
        playing(model)
        advanceTimeBy(2_000)
        runCurrent()

        repeat(10) { answerRight(model) }
        runCurrent()

        assertEquals(MIN_ANSWER_MILLIS + 2_000, submitted.first().elapsedMillis)
        assertEquals(MIN_ANSWER_MILLIS, submitted[1].elapsedMillis)
    }

    @Test
    fun the_clock_does_not_run_while_the_question_is_off_screen() = runTest(dispatcher) {
        var submitted: List<QuizAnswer> = emptyList()
        val real = FakeDailyRepository()
        val recording = object : DailyRepository by real {
            override suspend fun submit(answers: List<QuizAnswer>): DailyResult {
                submitted = answers
                return real.submit(answers)
            }
        }
        val model = model(daily = recording)
        playing(model)

        model.onQuestionHidden()
        advanceTimeBy(60_000)
        model.onQuestionShown()
        runCurrent()
        repeat(10) { answerRight(model) }
        runCurrent()

        assertEquals(MIN_ANSWER_MILLIS, submitted.first().elapsedMillis)
    }

    @Test
    fun a_submit_that_fails_keeps_the_answers_and_sends_them_again_on_retry() = runTest(dispatcher) {
        // Review Focus 2; design §14: "Submit fails after play: retry".
        var failNext = true
        var received = 0
        val real = FakeDailyRepository()
        val flaky = object : DailyRepository by real {
            override suspend fun submit(answers: List<QuizAnswer>): DailyResult {
                if (failNext) { failNext = false; throw IllegalStateException("connection dropped") }
                received = answers.size
                return real.submit(answers)
            }
        }
        val model = model(daily = flaky)
        playing(model)
        repeat(10) { answerRight(model) }
        runCurrent()
        assertEquals(DailyStage.Failed(DailyFailure.SUBMIT), model.stage.value)

        model.retry()
        runCurrent()

        assertEquals(10, received)
        assertEquals(10, assertIs<DailyStage.Done>(model.stage.value).result.correct)
    }

    @Test
    fun a_start_that_fails_says_so_and_retrying_reads_the_day_again() = runTest(dispatcher) {
        // Review Focus 3: the connection went between the lobby and the start.
        var failNext = true
        val real = FakeDailyRepository()
        val flaky = object : DailyRepository by real {
            override suspend fun start(locale: String) =
                if (failNext) { failNext = false; throw IllegalStateException("offline") } else real.start(locale)
        }
        val model = model(daily = flaky)
        runCurrent()

        model.start(locale = "la")
        runCurrent()
        assertEquals(DailyStage.Failed(DailyFailure.START), model.stage.value)

        model.retry()
        runCurrent()
        assertIs<DailyStage.Ready>(model.stage.value)
    }

    @Test
    fun a_day_found_already_in_progress_is_resumed_from_its_first_question() = runTest(dispatcher) {
        // The app was closed mid-attempt. The server's clock has kept running.
        val daily = FakeDailyRepository()
        daily.start(locale = "la")

        val model = model(daily = daily)
        runCurrent()

        assertEquals(0, assertIs<DailyStage.Playing>(model.stage.value).asking.index)
    }

    @Test
    fun the_first_question_is_named_in_the_locale_the_daily_was_started_in() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        model.start(locale = "en")
        runCurrent()

        val question = assertIs<DailyStage.Playing>(model.stage.value).asking.question as QuizQuestion.NameTheHighlighted
        assertEquals(true, question.options.all { it.name.startsWith("Rib ") })
    }
}
```

- [ ] **Step 5: Run them to verify they fail**

Run: `./gradlew :shared:feature-daily:allTests`
Expected: compilation FAILS with `Unresolved reference 'DailyViewModel'` and `'DailyStage'`.

- [ ] **Step 6: Implement**

`.../daily/DailyViewModel.kt`:

```kotlin
package com.ptk.anatomypro.feature.daily

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptk.anatomypro.core.data.model.DailyQuiz
import com.ptk.anatomypro.core.data.model.DailyResult
import com.ptk.anatomypro.core.data.model.Entitlements
import com.ptk.anatomypro.core.data.model.QuizAnswer
import com.ptk.anatomypro.core.data.model.QuizFormat
import com.ptk.anatomypro.core.data.model.QuizQuestion
import com.ptk.anatomypro.core.data.model.QuizSession
import com.ptk.anatomypro.core.data.model.QuizTopic
import com.ptk.anatomypro.core.data.repository.DailyRepository
import com.ptk.anatomypro.core.data.repository.EntitlementRepository
import com.ptk.anatomypro.core.data.repository.QuizRepository
import com.ptk.anatomypro.core.data.repository.SettingsRepository
import com.ptk.anatomypro.core.model.QuizSessionId
import com.ptk.anatomypro.core.model.QuizTopicId
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.feature.quiz.QuizStage
import com.ptk.anatomypro.feature.quiz.TICK_MILLIS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * How long a question must have been on screen before an answer counts.
 *
 * The daily shows nothing between questions, so the tap that answers one lands on the next
 * a moment later. A second tap inside this window is that tap again, not an answer.
 */
const val MIN_ANSWER_MILLIS = 300L

enum class DailyFailure { LOAD, START, SUBMIT }

/** Where today's quiz is. Screens 14 and 15 are both pictures of this. */
sealed interface DailyStage {
    data object Loading : DailyStage

    /** A free user. Designed, not an error: it has its own copy and a way to the paywall. */
    data object NotSubscribed : DailyStage

    data class Ready(val date: LocalDate, val questionCount: Int) : DailyStage

    /**
     * [asking] is what the quiz's question screen draws. It never carries a time limit:
     * the server times the whole attempt (design spec §9.1).
     */
    data class Playing(val asking: QuizStage.Asking) : DailyStage

    data object Submitting : DailyStage

    data class Done(val result: DailyResult) : DailyStage

    /** §14. [practice] is the topic practice mode would use, or null when there is none. */
    data class Offline(val practice: QuizTopic?) : DailyStage

    data class Failed(val during: DailyFailure) : DailyStage
}

/**
 * Today's quiz, from the lobby to its result.
 *
 * App-scoped: the home screen and the lobby show the same day, and a question left for
 * another tab is still there on the way back.
 *
 * There is no feedback between questions because there is nothing to give: answer keys
 * never reach the device (§9.1). Answers are collected and sent together.
 */
class DailyViewModel(
    private val daily: DailyRepository,
    private val entitlements: EntitlementRepository,
    private val quiz: QuizRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _stage = MutableStateFlow<DailyStage>(DailyStage.Loading)
    val stage: StateFlow<DailyStage> = _stage.asStateFlow()

    private var held = Entitlements(subscribed = false, ownedSystems = emptySet())
    private var locale = "la"
    private val answers = mutableListOf<QuizAnswer>()
    private var elapsedMillis = 0L
    private var shown = false
    private var clock: Job? = null

    init {
        viewModelScope.launch {
            entitlements.entitlements.collect { current ->
                val was = held.subscribed
                held = current
                when {
                    !current.subscribed -> {
                        stopClock()
                        _stage.value = DailyStage.NotSubscribed
                    }
                    // The first reading, or a subscription bought while the screen was open.
                    !was || _stage.value == DailyStage.Loading -> refresh()
                }
            }
        }
    }

    /** Reads the day again. A free user's device never asks. */
    fun refresh() {
        if (!held.subscribed) return
        if (_stage.value is DailyStage.Playing || _stage.value == DailyStage.Submitting) return
        _stage.value = DailyStage.Loading
        viewModelScope.launch {
            try {
                when (val today = daily.today()) {
                    is DailyQuiz.NotStarted -> _stage.value = DailyStage.Ready(today.date, today.questionCount)
                    is DailyQuiz.InProgress -> play(today)
                    is DailyQuiz.Completed -> _stage.value = DailyStage.Done(today.result)
                    DailyQuiz.Unavailable -> _stage.value = DailyStage.Offline(findPractice())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _stage.value = DailyStage.Failed(DailyFailure.LOAD)
            }
        }
    }

    /** [locale] is the examination locale the questions are named in (§13). */
    fun start(locale: String) {
        if (_stage.value !is DailyStage.Ready) return
        this.locale = locale
        _stage.value = DailyStage.Loading
        viewModelScope.launch {
            try {
                play(daily.start(locale))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _stage.value = DailyStage.Failed(DailyFailure.START)
            }
        }
    }

    fun onQuestionShown() {
        shown = true
        startClock()
    }

    fun onQuestionHidden() {
        shown = false
        stopClock()
    }

    fun answer(chosen: StructureId) {
        val playing = _stage.value as? DailyStage.Playing ?: return
        if (elapsedMillis < MIN_ANSWER_MILLIS) return
        val asking = playing.asking
        answers += QuizAnswer(asking.question.id, chosen, elapsedMillis)
        elapsedMillis = 0
        if (asking.index == asking.session.questions.lastIndex) {
            submit()
        } else {
            _stage.value = DailyStage.Playing(asking.copy(index = asking.index + 1))
        }
    }

    /** After a failure: read the day again, or send the answers again. */
    fun retry() {
        when ((_stage.value as? DailyStage.Failed)?.during) {
            DailyFailure.SUBMIT -> submit()
            DailyFailure.LOAD, DailyFailure.START -> {
                _stage.value = DailyStage.Loading
                refresh()
            }
            null -> Unit
        }
    }

    private fun play(started: DailyQuiz.InProgress) {
        answers.clear()
        elapsedMillis = 0
        if (started.questions.isEmpty()) {
            submit()
            return
        }
        // Dressed as a quiz session so the quiz's question screen and canvas can draw it.
        val session = QuizSession(
            id = QuizSessionId("daily-${started.date}"),
            topic = QuizTopicId("daily"),
            format = when (started.questions.first()) {
                is QuizQuestion.NameTheHighlighted -> QuizFormat.NAME_THE_HIGHLIGHTED
                is QuizQuestion.TapTheStructure -> QuizFormat.TAP_THE_STRUCTURE
            },
            questions = started.questions,
            seed = 0,
            locale = locale,
        )
        _stage.value = DailyStage.Playing(QuizStage.Asking(session, index = 0, remainingMillis = null))
        if (shown) startClock()
    }

    private fun submit() {
        stopClock()
        _stage.value = DailyStage.Submitting
        viewModelScope.launch {
            try {
                _stage.value = DailyStage.Done(daily.submit(answers.toList()))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The answers are kept. §14: retry, do not replay.
                _stage.value = DailyStage.Failed(DailyFailure.SUBMIT)
            }
        }
    }

    private suspend fun findPractice(): QuizTopic? = try {
        val current = settings.settings.first()
        practiceTopic(quiz.topics(current.interfaceLocale), current.studiedSystems, held)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private fun startClock() {
        if (clock?.isActive == true || _stage.value !is DailyStage.Playing) return
        clock = viewModelScope.launch {
            while (true) {
                delay(TICK_MILLIS)
                if (_stage.value !is DailyStage.Playing) return@launch
                elapsedMillis += TICK_MILLIS
            }
        }
    }

    private fun stopClock() {
        clock?.cancel()
        clock = null
    }
}
```

`QuizSessionId("daily-2026-09-24")` is a valid slug: lowercase letters, digits and single hyphens.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :shared:feature-daily:allTests`
Expected: BUILD SUCCESSFUL; `DailyViewModelTest` sixteen passing per target.

If `a_free_user_is_told…` hangs on `Loading`: the entitlement collector must set `NotSubscribed` on its first reading, before anything calls `refresh()`.

If `subscribing_while_the_screen_is_open…` stays on `NotSubscribed`: `was` must be read before `held` is replaced.

- [ ] **Step 8: Commit**

```bash
git add settings.gradle.kts shared/build.gradle.kts shared/feature-daily
git commit -m "feat(daily): today's quiz is a state machine a free user never reaches the server through"
```

---

### Task 3: The streak, for the home screen

**Files:**
- Create: `.../daily/HomeViewModel.kt`
- Test: `shared/feature-daily/src/commonTest/kotlin/com/ptk/anatomypro/feature/daily/HomeViewModelTest.kt`

**Interfaces:**
- Consumes: `ProgressRepository.streak: Flow<StreakState>`, `FakeProgressRepository(behaviour, initialStreak)`.
- Produces:
  ```kotlin
  data class HomeUiState(val streak: StreakState? = null)
  class HomeViewModel(progress: ProgressRepository) : ViewModel() { val state: StateFlow<HomeUiState> }
  ```
  A null streak is "not known": still loading, or the store could not be read. The screen shows the daily card either way.

- [ ] **Step 1: Write the failing tests**

`HomeViewModelTest.kt`:

```kotlin
package com.ptk.anatomypro.feature.daily

import com.ptk.anatomypro.core.data.fake.FakeProgressRepository
import com.ptk.anatomypro.core.data.model.DayActivity
import com.ptk.anatomypro.core.data.model.QuizSummary
import com.ptk.anatomypro.core.data.model.StreakState
import com.ptk.anatomypro.core.data.model.SystemMastery
import com.ptk.anatomypro.core.data.repository.ProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun the_streak_is_shown_as_the_store_has_it() = runTest(dispatcher) {
        val model = HomeViewModel(FakeProgressRepository())
        advanceUntilIdle()

        assertEquals(12, model.state.value.streak?.currentDays)
        assertEquals(31, model.state.value.streak?.longestDays)
    }

    @Test
    fun a_student_who_has_never_studied_has_a_streak_of_nothing_not_no_streak() = runTest(dispatcher) {
        val never = StreakState(currentDays = 0, longestDays = 0, lastStudied = null)

        val model = HomeViewModel(FakeProgressRepository(initialStreak = never))
        advanceUntilIdle()

        assertEquals(never, model.state.value.streak)
    }

    @Test
    fun a_streak_that_cannot_be_read_is_unknown_and_nothing_crashes() = runTest(dispatcher) {
        val broken = object : ProgressRepository {
            override val streak: Flow<StreakState> = flow { throw IllegalStateException("no store") }
            override suspend fun masteryBySystem(locale: String): List<SystemMastery> = emptyList()
            override suspend fun activity(from: LocalDate, to: LocalDate): Map<LocalDate, DayActivity> = emptyMap()
            override suspend fun recordSession(summary: QuizSummary) = Unit
        }

        val model = HomeViewModel(broken)
        advanceUntilIdle()

        assertNull(model.state.value.streak)
    }
}
```

- [ ] **Step 2: Run them to verify they fail, then implement**

Run: `./gradlew :shared:feature-daily:allTests`
Expected: compilation FAILS with `Unresolved reference 'HomeViewModel'`.

`.../daily/HomeViewModel.kt`:

```kotlin
package com.ptk.anatomypro.feature.daily

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptk.anatomypro.core.data.model.StreakState
import com.ptk.anatomypro.core.data.repository.ProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** A null [streak] is not known: still loading, or the store could not be read. */
data class HomeUiState(val streak: StreakState? = null)

/**
 * Prototype screen 14's own data: the streak.
 *
 * The day's quiz is the other half of that screen and is [DailyViewModel]'s, because the
 * lobby shows the same day and the two must not disagree.
 */
class HomeViewModel(progress: ProgressRepository) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            progress.streak
                // An unreadable streak leaves the card out; the daily is still there.
                .catch { }
                .collect { _state.value = HomeUiState(streak = it) }
        }
    }
}
```

Run: `./gradlew :shared:feature-daily:allTests`
Expected: BUILD SUCCESSFUL, `HomeViewModelTest` three passing per target.

- [ ] **Step 3: Commit**

```bash
git add shared/feature-daily
git commit -m "feat(daily): the home screen's streak"
```

---

### Task 4: The leaderboard's ViewModel

**Files:**
- Create: `.../daily/LeaderboardViewModel.kt`
- Test: `shared/feature-daily/src/commonTest/kotlin/com/ptk/anatomypro/feature/daily/LeaderboardViewModelTest.kt`

**Interfaces:**
- Consumes: `LeaderboardRepository.daily(date) / streaks()`, `Leaderboard`, `EntitlementRepository.entitlements`, `FakeLeaderboardRepository(behaviour, ranked)`.
- Produces:
  ```kotlin
  enum class Board { DAILY, STREAK }
  data class LeaderboardUiState(
      val subscribed: Boolean = false,
      val board: Board = Board.DAILY,
      val leaderboard: Leaderboard? = null,
      val isLoading: Boolean = true,
      val failed: Boolean = false,
  ) { val unranked: Boolean }
  class LeaderboardViewModel(
      leaderboard: LeaderboardRepository, entitlements: EntitlementRepository, today: () -> LocalDate,
  ) : ViewModel() {
      val state: StateFlow<LeaderboardUiState>
      fun onBoard(board: Board)
      fun onRetry()
  }
  ```
  `today` supplies the UTC date the daily board is asked for.

- [ ] **Step 1: Write the failing tests**

`LeaderboardViewModelTest.kt`:

```kotlin
package com.ptk.anatomypro.feature.daily

import com.ptk.anatomypro.core.data.fake.FakeEntitlementRepository
import com.ptk.anatomypro.core.data.fake.FakeLeaderboardRepository
import com.ptk.anatomypro.core.data.model.Entitlements
import com.ptk.anatomypro.core.data.model.Leaderboard
import com.ptk.anatomypro.core.data.model.SubscriptionPlan
import com.ptk.anatomypro.core.data.repository.LeaderboardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LeaderboardViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val day = LocalDate(2026, 9, 24)
    private val subscriber = Entitlements(subscribed = true, ownedSystems = emptySet())

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun model(
        leaderboard: LeaderboardRepository = FakeLeaderboardRepository(),
        entitlements: FakeEntitlementRepository = FakeEntitlementRepository(initial = subscriber),
    ) = LeaderboardViewModel(leaderboard, entitlements, today = { day })

    @Test
    fun the_daily_board_is_shown_first_with_the_users_own_row() = runTest(dispatcher) {
        // §9, screen 16: daily board.
        val model = model()
        advanceUntilIdle()

        val state = model.state.value
        assertEquals(Board.DAILY, state.board)
        assertEquals(412, state.leaderboard?.me?.rank)
        assertFalse(state.unranked)
        assertFalse(state.isLoading)
    }

    @Test
    fun the_daily_board_is_asked_for_todays_date() = runTest(dispatcher) {
        var asked: LocalDate? = null
        val real = FakeLeaderboardRepository()
        val recording = object : LeaderboardRepository by real {
            override suspend fun daily(date: LocalDate): Leaderboard = real.daily(date).also { asked = date }
        }

        model(leaderboard = recording)
        advanceUntilIdle()

        assertEquals(day, asked)
    }

    @Test
    fun switching_to_the_streak_board_loads_it() = runTest(dispatcher) {
        // §9, screen 16: streak board.
        val model = model()
        advanceUntilIdle()

        model.onBoard(Board.STREAK)
        advanceUntilIdle()

        assertEquals(Board.STREAK, model.state.value.board)
        assertEquals(88, model.state.value.leaderboard?.me?.rank)
    }

    @Test
    fun a_user_who_is_not_on_the_board_is_unranked_and_the_board_is_still_shown() = runTest(dispatcher) {
        // §9, screen 16: unranked user. Review Focus 5.
        val model = model(leaderboard = FakeLeaderboardRepository(ranked = false))
        advanceUntilIdle()

        assertTrue(model.state.value.unranked)
        assertNull(model.state.value.leaderboard?.me)
        assertEquals(10, model.state.value.leaderboard?.entries?.size)
    }

    @Test
    fun a_free_user_is_told_they_are_not_subscribed_and_no_board_is_asked_for() = runTest(dispatcher) {
        // §15.3.
        var asked = 0
        val real = FakeLeaderboardRepository()
        val counting = object : LeaderboardRepository by real {
            override suspend fun daily(date: LocalDate): Leaderboard = real.daily(date).also { asked++ }
            override suspend fun streaks(): Leaderboard = real.streaks().also { asked++ }
        }

        val model = model(leaderboard = counting, entitlements = FakeEntitlementRepository())
        advanceUntilIdle()

        assertFalse(model.state.value.subscribed)
        assertNull(model.state.value.leaderboard)
        assertEquals(0, asked)
    }

    @Test
    fun subscribing_while_the_screen_is_open_loads_the_board() = runTest(dispatcher) {
        // Review Focus 4.
        val entitlements = FakeEntitlementRepository()
        val model = model(entitlements = entitlements)
        advanceUntilIdle()

        entitlements.purchase(SubscriptionPlan("monthly", "Miesięcznie", "29 zł", periodMonths = 1))
        advanceUntilIdle()

        assertTrue(model.state.value.subscribed)
        assertEquals(412, model.state.value.leaderboard?.me?.rank)
    }

    @Test
    fun a_board_that_cannot_be_loaded_is_a_failure_that_can_be_retried() = runTest(dispatcher) {
        var broken = true
        val real = FakeLeaderboardRepository()
        val flaky = object : LeaderboardRepository by real {
            override suspend fun daily(date: LocalDate): Leaderboard =
                if (broken) throw IllegalStateException("no board") else real.daily(date)
        }
        val model = model(leaderboard = flaky)
        advanceUntilIdle()
        assertTrue(model.state.value.failed)

        broken = false
        model.onRetry()
        advanceUntilIdle()

        assertFalse(model.state.value.failed)
        assertEquals(412, model.state.value.leaderboard?.me?.rank)
    }

    @Test
    fun a_slow_board_does_not_overwrite_the_one_switched_to_after_it() = runTest(dispatcher) {
        // The daily board answers late, after the user has moved to streaks.
        val real = FakeLeaderboardRepository()
        val slowDaily = object : LeaderboardRepository by real {
            override suspend fun daily(date: LocalDate): Leaderboard {
                kotlinx.coroutines.delay(5_000)
                return real.daily(date)
            }
        }
        val model = model(leaderboard = slowDaily)
        // Let the daily board's request go out and be left waiting.
        runCurrent()
        assertTrue(model.state.value.isLoading)

        model.onBoard(Board.STREAK)
        advanceUntilIdle()

        assertEquals(Board.STREAK, model.state.value.board)
        assertEquals(88, model.state.value.leaderboard?.me?.rank)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :shared:feature-daily:allTests`
Expected: compilation FAILS with `Unresolved reference 'LeaderboardViewModel'`.

- [ ] **Step 3: Implement**

`.../daily/LeaderboardViewModel.kt`:

```kotlin
package com.ptk.anatomypro.feature.daily

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptk.anatomypro.core.data.model.Leaderboard
import com.ptk.anatomypro.core.data.repository.EntitlementRepository
import com.ptk.anatomypro.core.data.repository.LeaderboardRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** v1's two boards (design spec §9.3). */
enum class Board { DAILY, STREAK }

data class LeaderboardUiState(
    val subscribed: Boolean = false,
    val board: Board = Board.DAILY,
    val leaderboard: Leaderboard? = null,
    val isLoading: Boolean = true,
    val failed: Boolean = false,
) {
    /** The board is there and the user is not on it: they have not played, or played untimed. */
    val unranked: Boolean get() = leaderboard != null && leaderboard.me == null
}

/**
 * Prototype screen 16.
 *
 * [today] gives the date the daily board is asked for. The daily quiz's day is UTC (design
 * spec §9.1), so the caller supplies a UTC date.
 */
class LeaderboardViewModel(
    private val leaderboard: LeaderboardRepository,
    private val entitlements: EntitlementRepository,
    private val today: () -> LocalDate,
) : ViewModel() {

    private val _state = MutableStateFlow(LeaderboardUiState())
    val state: StateFlow<LeaderboardUiState> = _state.asStateFlow()

    private var loading: Job? = null

    init {
        viewModelScope.launch {
            entitlements.entitlements.collect { current ->
                val was = _state.value.subscribed
                _state.update { it.copy(subscribed = current.subscribed) }
                when {
                    // A free user's device never asks for a board.
                    !current.subscribed -> {
                        loading?.cancel()
                        _state.update { it.copy(leaderboard = null, isLoading = false, failed = false) }
                    }
                    !was -> load()
                }
            }
        }
    }

    fun onBoard(board: Board) {
        if (board == _state.value.board && !_state.value.failed) return
        _state.update { it.copy(board = board) }
        load()
    }

    fun onRetry() = load()

    private fun load() {
        if (!_state.value.subscribed) return
        // The latest request wins: a board that answers late must not replace the one the
        // user has since switched to.
        loading?.cancel()
        val board = _state.value.board
        _state.update { it.copy(isLoading = true, failed = false, leaderboard = null) }
        loading = viewModelScope.launch {
            try {
                val loaded = when (board) {
                    Board.DAILY -> leaderboard.daily(today())
                    Board.STREAK -> leaderboard.streaks()
                }
                _state.update { it.copy(leaderboard = loaded, isLoading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, failed = true) }
            }
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :shared:feature-daily:allTests`
Expected: BUILD SUCCESSFUL; `LeaderboardViewModelTest` eight passing per target.

- [ ] **Step 5: Commit**

```bash
git add shared/feature-daily
git commit -m "feat(daily): the leaderboard's two boards, an unranked user and a free one"
```

---

### Task 5: Screens 14, 15 and 16

No tests: Compose UI tests are out of scope. They are looked at in Task 6.

**Files:**
- Create: `shared/feature-daily/src/commonMain/composeResources/values/strings.xml`, `values-pl/strings.xml`
- Create: `.../daily/HomeScreen.kt`, `.../daily/LobbyScreen.kt`, `.../daily/LeaderboardScreen.kt`

**Interfaces:**
- Consumes: `HomeUiState`, `DailyStage`, `DailyFailure`, `LeaderboardUiState`, `Board` (Tasks 2–4).
- Produces:
  ```kotlin
  @Composable fun HomeScreen(home: HomeUiState, daily: DailyStage, onOpenDaily: () -> Unit, onSubscribe: () -> Unit, modifier: Modifier = Modifier)
  /** Every stage except Playing, which the route draws with the quiz's question screen. */
  @Composable fun LobbyScreen(
      stage: DailyStage, onStart: () -> Unit, onRetry: () -> Unit, onPractice: (QuizTopic) -> Unit,
      onSubscribe: () -> Unit, onBack: () -> Unit, onLeaderboard: () -> Unit, modifier: Modifier = Modifier,
  )
  @Composable fun LeaderboardScreen(state: LeaderboardUiState, onBoard: (Board) -> Unit, onRetry: () -> Unit, onSubscribe: () -> Unit, modifier: Modifier = Modifier)
  ```

- [ ] **Step 1: Write the English strings**

`values/strings.xml`:

```xml
<resources>
    <string name="home_title">Today</string>
    <string name="home_streak_unit">day streak</string>
    <string name="home_streak_longest">Longest: %1$d</string>
    <string name="home_streak_none">No streak yet. A session a day starts one.</string>
    <string name="home_daily_heading">DAILY QUIZ</string>
    <string name="home_daily_loading">Checking today\'s quiz…</string>
    <string name="home_daily_ready">%1$d questions, the same for everyone today.</string>
    <string name="home_daily_playing">You have a quiz in progress.</string>
    <string name="home_daily_done">Done today: %1$d of %2$d.</string>
    <string name="home_daily_offline">Today\'s quiz needs a connection.</string>
    <string name="home_daily_failed">Today\'s quiz could not be checked.</string>
    <string name="home_daily_open">Open</string>
    <string name="home_daily_continue">Continue</string>
    <string name="home_daily_result">See the result</string>
    <string name="subscribe_title">Part of the subscription</string>
    <string name="subscribe_daily">The daily quiz and its ranking are for subscribers. The atlas and skeletal quizzes stay free.</string>
    <string name="subscribe_board">The ranking is for subscribers, who play the daily quiz.</string>
    <string name="subscribe_action">See the subscription</string>
    <string name="lobby_title">Daily quiz</string>
    <string name="lobby_back">Back</string>
    <string name="lobby_loading">Checking today\'s quiz…</string>
    <string name="lobby_ready_body">%1$d questions. One attempt. Everyone gets the same set, and the clock starts when you do.</string>
    <string name="lobby_no_verdicts">You will not be told which answers were right until the end.</string>
    <string name="lobby_start">Start</string>
    <string name="lobby_submitting">Sending your answers…</string>
    <string name="lobby_done_score">%1$d of %2$d correct</string>
    <string name="lobby_done_rank">Place %1$d of %2$d</string>
    <string name="lobby_done_unranked">Not ranked today.</string>
    <string name="lobby_done_time">%1$d s</string>
    <string name="lobby_done_tomorrow">One attempt a day. A new quiz comes tomorrow.</string>
    <string name="lobby_board">See the ranking</string>
    <string name="lobby_offline">The daily quiz needs a connection to start and to be scored.</string>
    <string name="lobby_practice_offer">Practise instead: %1$s. Practice is never ranked.</string>
    <string name="lobby_practice">Practise</string>
    <string name="lobby_practice_none">There is nothing to practise on yet.</string>
    <string name="lobby_failed_load">Today\'s quiz could not be checked.</string>
    <string name="lobby_failed_start">The quiz could not be started. Nothing was used up.</string>
    <string name="lobby_failed_submit">Your answers could not be sent. They are kept; send them again.</string>
    <string name="lobby_retry">Try again</string>
    <string name="lobby_resend">Send again</string>
    <string name="board_title">Ranking</string>
    <string name="board_daily">Today</string>
    <string name="board_streak">Streaks</string>
    <string name="board_loading">Loading the ranking…</string>
    <string name="board_failed">The ranking could not be loaded.</string>
    <string name="board_retry">Try again</string>
    <string name="board_players">%1$d players</string>
    <string name="board_you">YOU</string>
    <string name="board_unranked_daily">You are not on today\'s board. Play the daily quiz, timed, to be ranked.</string>
    <string name="board_unranked_streak">You are not on the streak board yet.</string>
    <string name="board_score_daily">%1$d pts · %2$d s</string>
    <string name="board_score_streak">%1$d days</string>
    <string name="board_row_description">Place %1$d, %2$s</string>
    <string name="board_row_description_you">Place %1$d, you</string>
</resources>
```

- [ ] **Step 2: Write the Polish strings**

`values-pl/strings.xml`:

```xml
<resources>
    <string name="home_title">Dziś</string>
    <string name="home_streak_unit">dni z rzędu</string>
    <string name="home_streak_longest">Najdłuższa seria: %1$d</string>
    <string name="home_streak_none">Nie masz jeszcze serii. Zaczyna ją jedna sesja dziennie.</string>
    <string name="home_daily_heading">QUIZ DNIA</string>
    <string name="home_daily_loading">Sprawdzanie dzisiejszego quizu…</string>
    <string name="home_daily_ready">Pytań: %1$d, dziś takie same dla wszystkich.</string>
    <string name="home_daily_playing">Masz rozpoczęty quiz.</string>
    <string name="home_daily_done">Dziś zaliczone: %1$d z %2$d.</string>
    <string name="home_daily_offline">Dzisiejszy quiz wymaga połączenia.</string>
    <string name="home_daily_failed">Nie udało się sprawdzić dzisiejszego quizu.</string>
    <string name="home_daily_open">Otwórz</string>
    <string name="home_daily_continue">Kontynuuj</string>
    <string name="home_daily_result">Zobacz wynik</string>
    <string name="subscribe_title">W ramach subskrypcji</string>
    <string name="subscribe_daily">Quiz dnia i jego ranking są dla subskrybentów. Atlas i quizy z układu kostnego pozostają darmowe.</string>
    <string name="subscribe_board">Ranking jest dla subskrybentów, którzy grają w quiz dnia.</string>
    <string name="subscribe_action">Zobacz subskrypcję</string>
    <string name="lobby_title">Quiz dnia</string>
    <string name="lobby_back">Wstecz</string>
    <string name="lobby_loading">Sprawdzanie dzisiejszego quizu…</string>
    <string name="lobby_ready_body">Pytań: %1$d. Jedno podejście. Wszyscy dostają ten sam zestaw, a czas liczy się od startu.</string>
    <string name="lobby_no_verdicts">O tym, które odpowiedzi były poprawne, dowiesz się na końcu.</string>
    <string name="lobby_start">Start</string>
    <string name="lobby_submitting">Wysyłanie odpowiedzi…</string>
    <string name="lobby_done_score">Poprawnie: %1$d z %2$d</string>
    <string name="lobby_done_rank">Miejsce %1$d z %2$d</string>
    <string name="lobby_done_unranked">Dziś bez miejsca w rankingu.</string>
    <string name="lobby_done_time">%1$d s</string>
    <string name="lobby_done_tomorrow">Jedno podejście dziennie. Nowy quiz będzie jutro.</string>
    <string name="lobby_board">Zobacz ranking</string>
    <string name="lobby_offline">Quiz dnia wymaga połączenia, żeby go rozpocząć i ocenić.</string>
    <string name="lobby_practice_offer">Zamiast tego poćwicz: %1$s. Ćwiczenia nigdy nie trafiają do rankingu.</string>
    <string name="lobby_practice">Ćwicz</string>
    <string name="lobby_practice_none">Nie ma jeszcze na czym ćwiczyć.</string>
    <string name="lobby_failed_load">Nie udało się sprawdzić dzisiejszego quizu.</string>
    <string name="lobby_failed_start">Nie udało się rozpocząć quizu. Podejście nie zostało wykorzystane.</string>
    <string name="lobby_failed_submit">Nie udało się wysłać odpowiedzi. Są zachowane; wyślij je ponownie.</string>
    <string name="lobby_retry">Spróbuj ponownie</string>
    <string name="lobby_resend">Wyślij ponownie</string>
    <string name="board_title">Ranking</string>
    <string name="board_daily">Dziś</string>
    <string name="board_streak">Serie</string>
    <string name="board_loading">Wczytywanie rankingu…</string>
    <string name="board_failed">Nie udało się wczytać rankingu.</string>
    <string name="board_retry">Spróbuj ponownie</string>
    <string name="board_players">Graczy: %1$d</string>
    <string name="board_you">TY</string>
    <string name="board_unranked_daily">Nie ma cię w dzisiejszym rankingu. Zagraj w quiz dnia z limitem czasu, żeby się w nim znaleźć.</string>
    <string name="board_unranked_streak">Nie ma cię jeszcze w rankingu serii.</string>
    <string name="board_score_daily">%1$d pkt · %2$d s</string>
    <string name="board_score_streak">Dni: %1$d</string>
    <string name="board_row_description">Miejsce %1$d, %2$s</string>
    <string name="board_row_description_you">Miejsce %1$d, ty</string>
</resources>
```

`lobby_failed_start` says nothing was used up. That is true of the fake and is what §9.1 intends — the attempt begins when the server hands out questions — but it is a promise the real backend has to keep. Note it in Task 6's record.

- [ ] **Step 3: Write screen 14**

`.../daily/HomeScreen.kt`:

```kotlin
package com.ptk.anatomypro.feature.daily

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import anatomypro.shared.feature_daily.generated.resources.Res
import anatomypro.shared.feature_daily.generated.resources.home_daily_continue
import anatomypro.shared.feature_daily.generated.resources.home_daily_done
import anatomypro.shared.feature_daily.generated.resources.home_daily_failed
import anatomypro.shared.feature_daily.generated.resources.home_daily_heading
import anatomypro.shared.feature_daily.generated.resources.home_daily_loading
import anatomypro.shared.feature_daily.generated.resources.home_daily_offline
import anatomypro.shared.feature_daily.generated.resources.home_daily_open
import anatomypro.shared.feature_daily.generated.resources.home_daily_playing
import anatomypro.shared.feature_daily.generated.resources.home_daily_ready
import anatomypro.shared.feature_daily.generated.resources.home_daily_result
import anatomypro.shared.feature_daily.generated.resources.home_streak_longest
import anatomypro.shared.feature_daily.generated.resources.home_streak_none
import anatomypro.shared.feature_daily.generated.resources.home_streak_unit
import anatomypro.shared.feature_daily.generated.resources.home_title
import anatomypro.shared.feature_daily.generated.resources.subscribe_action
import anatomypro.shared.feature_daily.generated.resources.subscribe_daily
import anatomypro.shared.feature_daily.generated.resources.subscribe_title
import com.ptk.anatomypro.core.designsystem.TextTertiary
import org.jetbrains.compose.resources.stringResource

/**
 * Prototype screen 14: the home dashboard.
 *
 * The streak, and today's quiz in whichever of its states it is in (spec §9 and §15.3):
 * not started, in progress, done, offline, not subscribed.
 */
@Composable
fun HomeScreen(
    home: HomeUiState,
    daily: DailyStage,
    onOpenDaily: () -> Unit,
    onSubscribe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(stringResource(Res.string.home_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

        home.streak?.let { streak ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (streak.currentDays == 0) {
                    Text(stringResource(Res.string.home_streak_none), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
                } else {
                    Text(streak.currentDays.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(Res.string.home_streak_unit), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                }
                if (streak.longestDays > 0) {
                    Text(
                        stringResource(Res.string.home_streak_longest, streak.longestDays),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.home_daily_heading), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            when (daily) {
                DailyStage.NotSubscribed -> {
                    Text(stringResource(Res.string.subscribe_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(Res.string.subscribe_daily), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
                    OutlinedButton(onClick = onSubscribe, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.subscribe_action))
                    }
                }

                DailyStage.Loading ->
                    Text(stringResource(Res.string.home_daily_loading), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)

                is DailyStage.Ready -> {
                    Text(stringResource(Res.string.home_daily_ready, daily.questionCount), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onOpenDaily, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.home_daily_open))
                    }
                }

                is DailyStage.Playing, DailyStage.Submitting -> {
                    Text(stringResource(Res.string.home_daily_playing), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onOpenDaily, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.home_daily_continue))
                    }
                }

                is DailyStage.Done -> {
                    Text(
                        stringResource(Res.string.home_daily_done, daily.result.correct, daily.result.total),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = onOpenDaily, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.home_daily_result))
                    }
                }

                is DailyStage.Offline -> {
                    Text(stringResource(Res.string.home_daily_offline), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onOpenDaily, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.home_daily_open))
                    }
                }

                is DailyStage.Failed -> {
                    Text(stringResource(Res.string.home_daily_failed), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onOpenDaily, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.home_daily_open))
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Write screen 15**

`.../daily/LobbyScreen.kt`:

```kotlin
package com.ptk.anatomypro.feature.daily

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import anatomypro.shared.feature_daily.generated.resources.Res
import anatomypro.shared.feature_daily.generated.resources.lobby_back
import anatomypro.shared.feature_daily.generated.resources.lobby_board
import anatomypro.shared.feature_daily.generated.resources.lobby_done_rank
import anatomypro.shared.feature_daily.generated.resources.lobby_done_score
import anatomypro.shared.feature_daily.generated.resources.lobby_done_time
import anatomypro.shared.feature_daily.generated.resources.lobby_done_tomorrow
import anatomypro.shared.feature_daily.generated.resources.lobby_done_unranked
import anatomypro.shared.feature_daily.generated.resources.lobby_failed_load
import anatomypro.shared.feature_daily.generated.resources.lobby_failed_start
import anatomypro.shared.feature_daily.generated.resources.lobby_failed_submit
import anatomypro.shared.feature_daily.generated.resources.lobby_loading
import anatomypro.shared.feature_daily.generated.resources.lobby_no_verdicts
import anatomypro.shared.feature_daily.generated.resources.lobby_offline
import anatomypro.shared.feature_daily.generated.resources.lobby_practice
import anatomypro.shared.feature_daily.generated.resources.lobby_practice_none
import anatomypro.shared.feature_daily.generated.resources.lobby_practice_offer
import anatomypro.shared.feature_daily.generated.resources.lobby_ready_body
import anatomypro.shared.feature_daily.generated.resources.lobby_resend
import anatomypro.shared.feature_daily.generated.resources.lobby_retry
import anatomypro.shared.feature_daily.generated.resources.lobby_start
import anatomypro.shared.feature_daily.generated.resources.lobby_submitting
import anatomypro.shared.feature_daily.generated.resources.lobby_title
import anatomypro.shared.feature_daily.generated.resources.subscribe_action
import anatomypro.shared.feature_daily.generated.resources.subscribe_daily
import anatomypro.shared.feature_daily.generated.resources.subscribe_title
import com.ptk.anatomypro.core.data.model.QuizTopic
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.IncorrectAmber
import com.ptk.anatomypro.core.designsystem.TextTertiary
import org.jetbrains.compose.resources.stringResource

/**
 * Prototype screen 15: the daily quiz lobby.
 *
 * Every stage but [DailyStage.Playing]. That one is the quiz's question screen, drawn by
 * the route in this same destination so that its canvas is not recreated on the way in.
 */
@Composable
fun LobbyScreen(
    stage: DailyStage,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    onPractice: (QuizTopic) -> Unit,
    onSubscribe: () -> Unit,
    onBack: () -> Unit,
    onLeaderboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(Res.string.lobby_back),
            style = MaterialTheme.typography.labelSmall,
            color = Accent,
            modifier = Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onBack).padding(top = 14.dp),
        )
        Text(stringResource(Res.string.lobby_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

        // Announced as it changes: sending, sent, failed.
        Column(
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (stage) {
                DailyStage.NotSubscribed -> {
                    Text(stringResource(Res.string.subscribe_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(Res.string.subscribe_daily), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
                    Button(onClick = onSubscribe, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.subscribe_action))
                    }
                }

                DailyStage.Loading ->
                    Text(stringResource(Res.string.lobby_loading), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)

                is DailyStage.Ready -> {
                    Text(stringResource(Res.string.lobby_ready_body, stage.questionCount), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(Res.string.lobby_no_verdicts), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
                    Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.lobby_start))
                    }
                }

                DailyStage.Submitting ->
                    Text(stringResource(Res.string.lobby_submitting), style = MaterialTheme.typography.bodyMedium)

                is DailyStage.Done -> {
                    val result = stage.result
                    Text(
                        stringResource(Res.string.lobby_done_score, result.correct, result.total),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val rank = result.rank
                    Text(
                        if (rank != null) stringResource(Res.string.lobby_done_rank, rank, result.totalPlayers)
                        else stringResource(Res.string.lobby_done_unranked),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(Res.string.lobby_done_time, (result.elapsedMillis / 1000).toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                    )
                    Text(stringResource(Res.string.lobby_done_tomorrow), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
                    OutlinedButton(onClick = onLeaderboard, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.lobby_board))
                    }
                }

                is DailyStage.Offline -> {
                    Text(stringResource(Res.string.lobby_offline), style = MaterialTheme.typography.bodyMedium)
                    val practice = stage.practice
                    if (practice != null) {
                        Text(
                            stringResource(Res.string.lobby_practice_offer, practice.title),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextTertiary,
                        )
                        Button(onClick = { onPractice(practice) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(Res.string.lobby_practice))
                        }
                    } else {
                        Text(stringResource(Res.string.lobby_practice_none), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
                    }
                    OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.lobby_retry))
                    }
                }

                is DailyStage.Failed -> {
                    val message = when (stage.during) {
                        DailyFailure.LOAD -> Res.string.lobby_failed_load
                        DailyFailure.START -> Res.string.lobby_failed_start
                        DailyFailure.SUBMIT -> Res.string.lobby_failed_submit
                    }
                    Text(stringResource(message), style = MaterialTheme.typography.bodyMedium, color = IncorrectAmber)
                    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(
                            stringResource(
                                if (stage.during == DailyFailure.SUBMIT) Res.string.lobby_resend else Res.string.lobby_retry
                            )
                        )
                    }
                }

                // Drawn by the route, with the quiz's question screen.
                is DailyStage.Playing -> Unit
            }
        }
    }
}
```

On `Offline`, "Try again" calls `onRetry`; the route maps that to `DailyViewModel.refresh()` there, because `retry()` acts only on a failure.

- [ ] **Step 5: Write screen 16**

`.../daily/LeaderboardScreen.kt`:

```kotlin
package com.ptk.anatomypro.feature.daily

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import anatomypro.shared.feature_daily.generated.resources.Res
import anatomypro.shared.feature_daily.generated.resources.board_daily
import anatomypro.shared.feature_daily.generated.resources.board_failed
import anatomypro.shared.feature_daily.generated.resources.board_loading
import anatomypro.shared.feature_daily.generated.resources.board_players
import anatomypro.shared.feature_daily.generated.resources.board_retry
import anatomypro.shared.feature_daily.generated.resources.board_row_description
import anatomypro.shared.feature_daily.generated.resources.board_row_description_you
import anatomypro.shared.feature_daily.generated.resources.board_score_daily
import anatomypro.shared.feature_daily.generated.resources.board_score_streak
import anatomypro.shared.feature_daily.generated.resources.board_streak
import anatomypro.shared.feature_daily.generated.resources.board_title
import anatomypro.shared.feature_daily.generated.resources.board_unranked_daily
import anatomypro.shared.feature_daily.generated.resources.board_unranked_streak
import anatomypro.shared.feature_daily.generated.resources.board_you
import anatomypro.shared.feature_daily.generated.resources.subscribe_action
import anatomypro.shared.feature_daily.generated.resources.subscribe_board
import anatomypro.shared.feature_daily.generated.resources.subscribe_title
import com.ptk.anatomypro.core.data.model.LeaderboardEntry
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.TextTertiary
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Prototype screen 16: the leaderboard.
 *
 * The user's own row says YOU, in a word as well as a colour (§12). A user who is not on
 * the board is told so in a sentence, under a board that is still shown.
 */
@Composable
fun LeaderboardScreen(
    state: LeaderboardUiState,
    onBoard: (Board) -> Unit,
    onRetry: () -> Unit,
    onSubscribe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(Res.string.board_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

        if (!state.subscribed) {
            Text(stringResource(Res.string.subscribe_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(Res.string.subscribe_board), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
            Button(onClick = onSubscribe, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.subscribe_action))
            }
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            BoardOption(Res.string.board_daily, state.board == Board.DAILY) { onBoard(Board.DAILY) }
            BoardOption(Res.string.board_streak, state.board == Board.STREAK) { onBoard(Board.STREAK) }
        }

        val board = state.leaderboard
        when {
            state.isLoading ->
                Text(stringResource(Res.string.board_loading), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)

            state.failed || board == null -> {
                Text(stringResource(Res.string.board_failed), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(Res.string.board_retry))
                }
            }

            else -> {
                Text(
                    stringResource(Res.string.board_players, board.totalPlayers),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    for (entry in board.entries) EntryRow(entry, state.board)
                }
                val me = board.me
                if (me != null) {
                    // Shown apart only when the listed rows do not already include it.
                    if (board.entries.none { it.isMe }) EntryRow(me, state.board)
                } else {
                    Text(
                        stringResource(
                            if (state.board == Board.DAILY) Res.string.board_unranked_daily else Res.string.board_unranked_streak
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun BoardOption(label: StringResource, selected: Boolean, onSelect: () -> Unit) {
    Text(
        stringResource(label),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) Accent else TextTertiary,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onSelect)
            .padding(top = 12.dp),
    )
}

@Composable
private fun EntryRow(entry: LeaderboardEntry, board: Board) {
    val description = if (entry.isMe) {
        stringResource(Res.string.board_row_description_you, entry.rank)
    } else {
        stringResource(Res.string.board_row_description, entry.rank, entry.nickname)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            // One announcement per row, not rank, name and score as three.
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(entry.rank.toString(), style = MaterialTheme.typography.bodyMedium, color = TextTertiary, modifier = Modifier.width(48.dp))
        Text(
            if (entry.isMe) stringResource(Res.string.board_you) else entry.nickname,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (entry.isMe) FontWeight.SemiBold else FontWeight.Normal,
            color = if (entry.isMe) Accent else MaterialTheme.typography.bodyMedium.color,
            modifier = Modifier.weight(1f),
        )
        Text(
            when (board) {
                Board.DAILY -> stringResource(Res.string.board_score_daily, entry.score, (entry.elapsedMillis / 1000).toInt())
                Board.STREAK -> stringResource(Res.string.board_score_streak, entry.score)
            },
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
        )
    }
}
```

- [ ] **Step 6: Verify they compile on both targets**

Run: `./gradlew :shared:feature-daily:allTests`
Expected: BUILD SUCCESSFUL, every test still passing.

- [ ] **Step 7: Commit**

```bash
git add shared/feature-daily
git commit -m "feat(daily): screens 14, 15 and 16"
```

---

### Task 6: The Today and Ranking tabs, practice, and a run by hand

**Files:**
- Create: `shared/src/commonMain/kotlin/com/ptk/anatomypro/DailyTab.kt`
- Modify: `shared/src/commonMain/kotlin/com/ptk/anatomypro/App.kt`
- Modify: `androidApp/src/debug/kotlin/com/ptk/anatomypro/EntryDependencies.kt`
- Modify: `docs/state-of-play.md`, `docs/superpowers/specs/2026-08-29-anatomy-pro-design.md`

**Interfaces:**
- Consumes: everything Tasks 2–5 produce; from the quiz plan `QuizSessionViewModel.start(topic, format, locale, timed)`, `QuizSessionScreen`, `quizCanvasFor`, `QuizStage`; `AnatomyCanvas`, `RenderState.None`, `FocusRequest`; `DailyRoute.Home / Lobby / Leaderboard`, `ProfileRoute.Paywall`, `TopLevel`.
- Produces:
  ```kotlin
  @Composable internal fun HomeRoute(dependencies: AppDependencies, daily: DailyViewModel, navController: NavHostController)
  @Composable internal fun LobbyRoute(daily: DailyViewModel, settings: AppSettings, navController: NavHostController, onPractice: (QuizTopic) -> Unit)
  @Composable internal fun LeaderboardRoute(dependencies: AppDependencies, navController: NavHostController)
  ```

- [ ] **Step 1: Write the routes**

`shared/src/commonMain/kotlin/com/ptk/anatomypro/DailyTab.kt`:

```kotlin
package com.ptk.anatomypro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
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
    settings: AppSettings,
    navController: NavHostController,
    onPractice: (QuizTopic) -> Unit,
) {
    val stage by daily.stage.collectAsState()

    // The clock runs only while this is on screen.
    DisposableEffect(daily) {
        daily.onQuestionShown()
        onDispose { daily.onQuestionHidden() }
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
                    render = RenderState.None,
                    focus = FocusRequest(canvas.focus, DAILY_FOCUS_MILLIS, serial = playing.asking.index),
                    onPicked = { picked -> if (canvas.pickable && picked != null) daily.answer(picked) },
                    onStats = {},
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
        onLeaderboard = { navController.navigate(DailyRoute.Leaderboard) },
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
```

"End session" on the daily's question screen reads as it does in the quiz but only leaves the screen. That wording is wrong for the daily; the screen takes no label parameter. Note it in the record as a defect to fix when the question screen next changes, rather than widening this plan.

- [ ] **Step 2: Wire the tabs**

In `App.kt`, add the imports:

```kotlin
import com.ptk.anatomypro.core.data.model.QuizFormat
import com.ptk.anatomypro.feature.daily.DailyViewModel
import com.ptk.anatomypro.feature.daily.dailyIsBuilt
```

In `MainScaffold`, after the quiz plan's `quizSession` declaration, add:

```kotlin
    // App-scoped: the home screen and the lobby show the same day.
    val daily: DailyViewModel? = if (dailyIsBuilt(dependencies.daily, dependencies.leaderboard)) {
        viewModel(key = "daily") {
            DailyViewModel(dependencies.daily, dependencies.entitlements, dependencies.quiz, dependencies.settings)
        }
    } else {
        null
    }
```

Replace the two lines

```kotlin
                composable<DailyRoute.Home> { NotBuilt(Res.string.tab_today) }
```

and

```kotlin
                composable<DailyRoute.Leaderboard> { NotBuilt(Res.string.tab_ranking) }
```

with

```kotlin
                // Production has no daily quiz or leaderboard yet: their repositories refuse.
                composable<DailyRoute.Home> {
                    if (daily == null) NotBuilt(Res.string.tab_today) else HomeRoute(dependencies, daily, navController)
                }
                composable<DailyRoute.Lobby> {
                    if (daily != null) {
                        LobbyRoute(
                            daily = daily,
                            settings = state.settings,
                            navController = navController,
                            // Practice is an ordinary topic session (all-screens spec §15.4):
                            // start one, and go where sessions are played.
                            onPractice = { topic ->
                                quizSession?.start(
                                    topic.id,
                                    QuizFormat.NAME_THE_HIGHLIGHTED,
                                    state.settings.examinationLocale,
                                    state.settings.quizTimerEnabled,
                                )
                                navController.selectTab(TopLevel.Test, current = tab)
                            },
                        )
                    }
                }
                composable<DailyRoute.Leaderboard> {
                    if (daily == null) NotBuilt(Res.string.tab_ranking) else LeaderboardRoute(dependencies, navController)
                }
```

`DailyRoute.Leaderboard` is the Ranking tab's start and is also navigated to from the lobby. Reaching it from the lobby pushes it on the Today tab's stack, and `NavDestination.tab()` reports Ranking for it, so the bottom bar moves to Ranking. That is acceptable — the user is looking at the ranking — and is recorded in Step 6.

- [ ] **Step 3: Give the debug build its states**

In `androidApp/src/debug/kotlin/com/ptk/anatomypro/EntryDependencies.kt`, add the imports:

```kotlin
import com.ptk.anatomypro.core.data.fake.FakeDailyRepository
import com.ptk.anatomypro.core.data.fake.FakeEntitlementRepository
import com.ptk.anatomypro.core.data.fake.FakeLeaderboardRepository
import com.ptk.anatomypro.core.data.model.Entitlements
```

Above the function's final `return`, read three more extras (if `intent` is not already declared by the onboarding plan, add `val intent = androidx.activity.compose.LocalActivity.current?.intent`):

```kotlin
    // adb shell am start -n com.ptk.anatomypro/.MainActivity --ez subscribed true --es daily offline
    //   subscribed: true shows the daily and the boards; without it, "not subscribed".
    //   daily: "offline" or "attempted"; anything else is a day not yet played.
    //   unranked: true leaves the user off the boards.
    val subscribed = intent?.getBooleanExtra("subscribed", false) ?: false
    val dailyState = intent?.getStringExtra("daily")
    val unranked = intent?.getBooleanExtra("unranked", false) ?: false
```

and replace the final `return remember(atlas, fakes) { … }` block from the quiz plan with:

```kotlin
    return remember(atlas, fakes, subscribed, dailyState, unranked) {
        val installed = atlas?.repository
        // The quiz and the daily ask about the atlas on screen: fixture ids are not in the
        // pack. A harness — it skips §7's verified-only rule.
        val quiz = installed?.let { FakeQuizRepository(source = AtlasQuizSource(it)) }
        fakes.copy(
            atlas = installed,
            quiz = quiz ?: fakes.quiz,
            daily = FakeDailyRepository(
                available = dailyState != "offline",
                alreadyAttempted = dailyState == "attempted",
                quiz = quiz ?: FakeQuizRepository(),
                topic = null,
            ),
            leaderboard = FakeLeaderboardRepository(ranked = !unranked),
            entitlements = FakeEntitlementRepository(
                initial = Entitlements(subscribed = subscribed, ownedSystems = emptySet()),
            ),
        )
    }
```

- [ ] **Step 4: Build**

Run: `./gradlew :androidApp:assembleDebug :androidApp:assembleRelease`
Expected: BUILD SUCCESSFUL.

Run:

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17' build
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 5: Run it by hand on Android**

`./gradlew :androidApp:installDebug`. For each launch, `adb shell am force-stop com.ptk.anatomypro` first. Write down what was seen.

Plain launch (a free user):

1. **14** DZIŚ: the streak (12, longest 31) and "W ramach subskrypcji" with "Zobacz subskrypcję", which opens the paywall placeholder under PROFIL.
2. **16** RANKING: the same "W ramach subskrypcji", no board.

`adb shell am start -n com.ptk.anatomypro/.MainActivity --ez subscribed true`:

3. **14**: "Pytań: 10, dziś takie same dla wszystkich." and "Otwórz".
4. **15**: the ready lobby; "Start" shows the first question on the model with "Bez limitu czasu". The model was not blank when the question appeared for longer than a pack load.
5. Answering moves to the next question with no verdict. Tapping an answer twice quickly does not skip a question.
6. Leaving for ATLAS and returning to DZIŚ shows "Masz rozpoczęty quiz." and "Kontynuuj", which returns to the same question.
7. After the tenth answer: "Wysyłanie odpowiedzi…", then the score, the place, the time and "Zobacz ranking".
8. **14** now says "Dziś zaliczone: … z 10."
9. **16**: ten rows and a separate YOU row at place 412; "Serie" switches board and the row moves to 88.

`--ez subscribed true --es daily attempted`:

10. **15** opens straight on the result: 7 of 10, place 412.

`--ez subscribed true --es daily offline`:

11. **14** says the quiz needs a connection. **15** says so too and offers practice on a named topic.
12. "Ćwicz" lands on TEST in a question on that topic, and the session ends in an ordinary summary.

`--ez subscribed true --ez unranked true`:

13. **16** shows the board and "Nie ma cię w dzisiejszym rankingu…", with no row of zeroes.

Then in English (Settings → interface English): screens 14, 15 and 16 once each.

**Not seen by hand, and say so:** the three failures (load, start, submit) — the fake has no switch for them and they are covered only by `DailyViewModelTest`; a subscription arriving while a screen is open; TalkBack on any of the three.

- [ ] **Step 6: Check production, and record**

On the iOS simulator, the DZIŚ and RANKING tabs still read "— jeszcze nie zbudowane".

In `docs/state-of-play.md`: add 14, 15 and 16 to the screens that are real, with the qualifications — on fakes, on the Android debug build only, failures unseen. Add the three intent extras beside the onboarding one.

Append an addendum to the design spec, numbered one above the last, recording: the daily is played without verdicts and submitted whole; an answer within 300 ms of a question appearing is ignored; practice is an ordinary session on a chosen topic; a free user's device never calls the daily or the leaderboard; the lobby's promise that a failed start uses nothing up is the backend's to keep; reaching the ranking from the lobby moves the bottom bar to Ranking; "End session" is the wrong label on the daily's question screen; what was and was not seen in Step 5.

- [ ] **Step 7: Run everything once more**

Run: `./gradlew allTests`
Expected: BUILD SUCCESSFUL on both targets. Stop any app running on a simulator first.

- [ ] **Step 8: Commit**

```bash
git add shared/src androidApp/src/debug docs
git commit -m "feat(daily): the Today and Ranking tabs, and practice when the daily is out of reach"
```

---

## Spec coverage

| Spec requirement | Where |
|---|---|
| §9 / §15.3 screen 14: daily not started; in progress; done; offline; not subscribed | Task 2 stage tests; Task 5 `HomeScreen`; Task 6 Step 5 items 1, 3, 6, 8, 11 |
| §9 / §15.3 screen 15: ready; running; already attempted; offline, offering practice; not subscribed | Task 2; Task 6 Step 5 items 4–7, 10–12 |
| §9 / §15.3 screen 16: daily board; streak board; unranked user; not subscribed | Task 4; Task 6 Step 5 items 2, 9, 13 |
| §15.4 practice mode: an ordinary session, topic by studied systems, never ranked | Task 2 `practiceTopic`; Task 6 Step 2 `onPractice` |
| §15.3: a free user's device never calls the paid repositories | Task 2 and Task 4 "never asked" tests |
| §4.2: the streak's day and the daily's day are different days | Task 6 Step 1 — the board is asked for a UTC date; the streak is shown as stored |
| §6: refusing repositories are never called | Task 2 `dailyIsBuilt`; Task 6 Steps 2, 6 |
| Design §9.1: answer keys never reach the device | Task 2 — no verdict stage; answers submitted whole |
| Design §14: daily offline blocks entry and offers practice; submit fails → retry | Task 2 offline and submit-retry tests |
| §10: a ViewModel test per screen, both targets | Tasks 2, 3, 4 |
