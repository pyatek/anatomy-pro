# All screens, mocked repositories — Design Specification

- **Date:** 2026-09-24
- **Status:** Approved for planning
- **Companion to:** `2026-08-29-anatomy-pro-design.md` §8–§14 (quiz, daily, packs,
  accessibility, i18n, error handling) and `docs/design-prompt-prototype-screens.md`
  (the 21-screen brief)

---

## 1. Purpose

Sixteen of the prototype's twenty-one screens do not exist. This document designs all of
them at once, driven by in-memory fakes rather than by a backend that has not been built.

The point is not to see the screens. The point is that **building a screen forces its data
contract to be written down**, and those contracts are what Phase 3's Ktor implementations
will have to satisfy. Designing them against fakes means designing them under a screen's
real demands instead of guessing from the endpoint table in §11.

A second, smaller purpose: the state of play records that screens 01, 05, 06, 20 and the
bottom bar have **never been run**. Wiring every screen to a fake that needs no database,
no pack, and no device makes running them a default rather than an expedition.

---

## 2. Locked decisions

| Decision | Choice | Rejected alternative, and why |
|---|---|---|
| Role of the mocks | Permanent interface seam; fakes in a module the app does not ship | A throwaway preview harness would invent each contract twice |
| Quiz logic | Fully mocked; canned question sets | Building §8.1's generator here would smuggle a TDD-shaped piece of work in behind UI work |
| Screen 12 | Built on colour **and shape**, no outlines, marked unfinished | Blocking sixteen screens on a `matc` install |
| Navigation | `org.jetbrains.androidx.navigation:navigation-compose:2.9.2` | Hand-rolled back stack; Decompose |
| Module layout | One feature module per flow | One `feature-screens` module; two large modules |
| UI strings | compose-resources now, always, no literal fallback; existing screens migrated | Deferring extraction; a per-module `Strings` object |
| Base resource locale | English in `values/`, Polish in `values-pl/` | Polish as base, which degrades to a resource key rather than to readable text |
| Compose UI tests | Out of scope | Standing up a UI test harness alongside sixteen screens |

---

## 3. What exists today

Five screens are real: **01** language selection, **04** atlas viewer, **05** structure
detail, **06** search, **20** settings. Two repositories exist — `AtlasRepository` (with a
Room implementation) and `SettingsRepository`. Routing is an enum for the bottom bar plus a
private sealed `AtlasRoute` inside `AtlasTab`. Three separate hand-rolled `AtlasRepository`
stubs exist across the feature test source sets, all of them near-identical.

---

## 4. Contracts

All interfaces live in `:shared:core-data`, `commonMain`, package
`com.ptk.anatomypro.core.data.repository`. Models live beside the existing
`StructureSummary` and `StructureDetail` in `core.data.model`. Ids that name a new kind of
thing are value classes in `core-model`, following the existing kebab-case slug rule.

### 4.1 QuizRepository — screens 08 to 13

Sessions are generated **on-device** and are seeded and reproducible (§8.2), so this is a
local contract, not a network one. The fake and the eventual real generator differ in how
they choose questions, not in where they live.

```kotlin
interface QuizRepository {
    /** Screen 08's grid: one entry per system or region, carrying its mastery. */
    suspend fun topics(locale: String): List<QuizTopic>

    /** [seed] is recorded on the session so a bad question set can be reproduced (§8.2). */
    suspend fun startSession(
        topic: QuizTopicId,
        format: QuizFormat,
        questionCount: Int,
        seed: Long,
        locale: String,          // examination locale; names in the session follow it (§13)
    ): QuizSession

    /** Scores one answer. The result carries what screens 11 and 12 need to explain it. */
    suspend fun submit(session: QuizSessionId, answer: QuizAnswer): AnswerResult

    suspend fun finish(session: QuizSessionId): QuizSummary
}
```

The question type is sealed, so adding a third format in v2 is a new subclass rather than a
nullable field on a struct that means two things:

```kotlin
sealed interface QuizQuestion {
    val id: QuizQuestionId
    val difficulty: Difficulty

    /** Screen 09: a name is shown, the body is tapped. */
    data class TapTheStructure(
        override val id: QuizQuestionId,
        override val difficulty: Difficulty,
        val prompt: String,
        val promptLocale: String,
        val target: StructureId,
    ) : QuizQuestion

    /** Screen 10: one structure is highlighted, four options are offered. */
    data class NameTheHighlighted(
        override val id: QuizQuestionId,
        override val difficulty: Difficulty,
        val highlighted: StructureId,
        val options: List<StructureSummary>,
        val correctIndex: Int,
    ) : QuizQuestion
}

/** §8.1's single lever: how close the distractors sit in the structure graph. */
enum class Difficulty { EASY, MEDIUM, HARD }
```

`AnswerResult` is shaped by screen 12, not by screen 11. The incorrect state is the highest
value moment in the app, and it needs both structures in hand at once — the right one and
the mistaken one — with enough to say how they relate:

```kotlin
data class AnswerResult(
    val questionId: QuizQuestionId,
    val correct: Boolean,
    val expected: StructureSummary,
    val chosen: StructureSummary?,
    /** Where the two diverge in the taxonomy — what screen 12 explains. */
    val sharedAncestor: StructureSummary?,
    val elapsedMillis: Long,
)

data class QuizSummary(
    val session: QuizSessionId,
    val correct: Int,
    val total: Int,
    val elapsedMillis: Long,
    /** Screen 13's "needs review" list, worst first. */
    val needsReview: List<StructureSummary>,
)
```

**Verification gate.** §7 says only `VERIFIED` structures may be quiz answers. That is a
property of question *generation*, so the fake enforces it too: its fixture carries
`VerificationState` and its canned questions never use an `UNVERIFIED` structure as an
expected answer. A test asserts this, because the real generator must inherit the rule and
a fake that ignores it would teach the screens a habit the generator cannot keep.

### 4.2 ProgressRepository — screens 08, 14, 17

```kotlin
interface ProgressRepository {
    val streak: Flow<StreakState>
    suspend fun masteryBySystem(locale: String): List<SystemMastery>
    /** Screen 17's calendar. Dates are local; the daily quiz's own dates are UTC (§9.1). */
    suspend fun activity(from: LocalDate, to: LocalDate): Map<LocalDate, DayActivity>
    suspend fun recordSession(summary: QuizSummary)
}
```

The split between local and UTC dates is deliberate and is the kind of thing that is
cheaper to decide now than to discover: a streak calendar that disagrees with the daily
quiz about which day it is will be reported as a bug, and the answer has to be that the
calendar is the user's day and the daily quiz is the world's.

### 4.3 DailyRepository — screens 14, 15

This mirrors §9.1's three-step flow rather than flattening it, because the intermediate
state is a screen: the clock starts when questions are fetched, and screen 15 is what the
user looks at while it runs.

```kotlin
interface DailyRepository {
    suspend fun today(): DailyQuiz
    /** Fetches the day's questions; the server records this as the clock start (§9.1). */
    suspend fun start(locale: String): DailyQuiz.InProgress   // examination locale (§13)
    /** Server scores against its own key and returns rank (§9.1, §9.2). */
    suspend fun submit(answers: List<QuizAnswer>): DailyResult
}

sealed interface DailyQuiz {
    data class NotStarted(val date: LocalDate, val questionCount: Int) : DailyQuiz
    data class InProgress(
        val date: LocalDate,
        val questions: List<QuizQuestion>,
        val startedAt: Instant,
    ) : DailyQuiz
    data class Completed(val date: LocalDate, val result: DailyResult) : DailyQuiz
    /** §14: offline blocks entry with a clear message and offers practice mode. */
    data object Unavailable : DailyQuiz
}
```

`Unavailable` exists in the sealed type rather than as a thrown exception because §14 makes
it a designed state with designed copy, not an error path.

### 4.4 LeaderboardRepository — screen 16

v1 boards are global daily and personal streak (§9.3). Each board carries the user's own
row, so "your position" is not a second call that can disagree with the first:

```kotlin
interface LeaderboardRepository {
    suspend fun daily(date: LocalDate): Leaderboard
    suspend fun streaks(): Leaderboard
}

data class Leaderboard(
    val entries: List<LeaderboardEntry>,
    val me: LeaderboardEntry?,
    val totalPlayers: Int,
)
```

`me` is nullable: an untimed session is unranked (§12), and a user who has not played today
has no row.

### 4.5 EntitlementRepository — screen 18

```kotlin
interface EntitlementRepository {
    val entitlements: Flow<Entitlements>
    suspend fun purchase(plan: SubscriptionPlan): PurchaseOutcome
    suspend fun restore(): PurchaseOutcome
}

data class Entitlements(val subscribed: Boolean, val ownedSystems: Set<SystemId>) {
    /** Skeletal is free forever (§10); the paywall boundary is the pack boundary. */
    fun allows(system: SystemId): Boolean = subscribed || system in ownedSystems
}
```

### 4.6 PackRepository — screens 03, 19

`PackState` carries the manifest fields §10 names — id, version, byte size, checksum, and
entitlement — because screen 19 shows sizes and screen 03 shows progress, and a download UI
that cannot report bytes is a spinner:

```kotlin
data class PackState(
    val id: PackId,
    val version: Int,
    val label: String,
    val byteSize: Long,
    val checksum: String,
    val entitlement: SystemId?,
    val status: PackStatus,
)

interface PackRepository {
    val packs: Flow<List<PackState>>
    suspend fun download(id: PackId)
    suspend fun cancel(id: PackId)
    suspend fun delete(id: PackId)
}

sealed interface PackStatus {
    data object Available : PackStatus
    data object Queued : PackStatus     // requested, nothing transferring yet
    data class Downloading(val bytesDone: Long, val bytesTotal: Long) : PackStatus
    data object Installed : PackStatus
    /** §14: resumable, so a failure is a state the UI offers to resume from. */
    data class Failed(val reason: PackFailure, val resumable: Boolean) : PackStatus
}
```

The download URL §10 also names is deliberately absent: it is issued server-side against
entitlements and never belongs in UI state.

### 4.7 A dependency the catalogue does not have

`LocalDate` and `Instant` appear in §4.2 and §4.3. **`kotlinx-datetime` is not in
`gradle/libs.versions.toml`** — nothing in the tree has needed a date until now. It is
added to the catalogue deliberately as part of the contracts step, not discovered
mid-implementation.

The alternative — epoch-millis `Long`s across the boundary — is rejected: the local/UTC
distinction in §4.2 is exactly the kind of thing a `Long` hides until it is a bug report.

### 4.8 Screen 02 gets no repository

"Which systems am I studying" is a preference, not a domain. It becomes
`studiedSystems: Set<String>` on the existing `AppSettings`, which already persists through
`RoomSettingsRepository` and already falls back on unreadable values. Adding a repository
for one set of strings would be ceremony.

---

## 5. The fake module — `:shared:core-data-fake`

A new KMP library module holding an in-memory implementation of every interface above,
plus a `FakeAtlasRepository` that replaces the three near-identical stubs currently
duplicated across the feature test source sets.

Three properties make the fakes worth a module of their own:

- **A shared fixture.** One hand-written atlas of a few dozen structures in Latin, Polish
  and English, with real sibling sets (ribs, cervical and thoracic vertebrae) so screen 10's
  four options and screen 12's "you said *Costa VII*" read like the real thing rather than
  like `Structure A`. It carries `VerificationState`, including at least one `UNVERIFIED`
  structure so §7's gate is exercised.
- **Injectable latency and failure.** Every fake takes a `FakeBehaviour` describing delay
  and failure injection. Loading states, empty states, and §14's error rows stop being
  theoretical: screen 15 can be shown offline, screen 03 can fail mid-download and resume.
- **Determinism.** Seeded, so a test and a screenshot see the same questions.

The module is a dependency of each feature module's `commonTest` and of debug entry points
only. It is never a dependency of `:shared`, and `:androidApp` takes it as
`debugImplementation`, so a release build cannot link it.

**iOS has no debug/release source split**, so on that target the fake module can end up in
the framework regardless. The guarantee is therefore not "the fake bytes are absent" — it is
**the production code path never constructs a fake**, which §6 enforces.

---

## 6. The dependency seam

`App()` currently reaches for `rememberSettingsRepository()` itself, which makes the
repository set an internal decision of the composable. It becomes a parameter:

```kotlin
data class AppDependencies(
    val atlas: AtlasRepository,
    val settings: SettingsRepository,
    val quiz: QuizRepository,
    val progress: ProgressRepository,
    val daily: DailyRepository,
    val leaderboard: LeaderboardRepository,
    val entitlements: EntitlementRepository,
    val packs: PackRepository,
)

@Composable fun App(dependencies: AppDependencies)
```

`AppDependencies` lives in `core-data`, not in `:shared` — the fake module must be able to
build one, and it cannot depend on `:shared` without a cycle.

Each platform entry point builds the set. A debug entry point builds the fake one. The
production one supplies the two repositories that exist — atlas and settings — and
`NotBuilt*` implementations of the other six, each throwing `NotImplementedError` naming
what is missing.

**Refusing is the design, not a placeholder.** Six of the eight repositories will have no
real implementation until Phase 3, so a production factory that quietly returned fakes
would let a release build present a fabricated leaderboard or a purchase that never
happened. A crash naming the gap is the better failure. It also means replacing one when
Phase 3 builds it is a one-line change at a single call site.

`rememberAtlas()` keeps its current shape — pack installation is genuinely asynchronous and
genuinely platform-specific — but moves behind the dependencies holder so a fake run needs
no pack on disk.

---

## 7. Navigation — `:shared:core-navigation`

`navigation-compose:2.9.2` (stable on Maven Central, verified 2026-09-24) with type-safe
`@Serializable` routes; the serialization plugin is already in the catalogue.

- One sealed hierarchy of destinations, one file, in `core-navigation`.
- Five top-level tabs, each with its own back stack, so leaving the quiz mid-session and
  returning lands back in the question rather than at the topic grid.
- The bottom bar reads the current destination from the `NavController` instead of holding
  its own `rememberSaveable`, which is what makes the two agree after process death.
- `AtlasTab`'s private `AtlasRoute` retires into the shared hierarchy.

**This is the one place the design takes a framework's opinion on app structure**, which is
why it is isolated in a module: if navigation-compose proves a poor fit on iOS, the blast
radius is one module and a set of route declarations.

---

## 8. Localisation

**Every UI string is a resource. There is no literal fallback.** All strings move to
compose-resources, and the five existing screens plus the bottom bar are migrated in the
same pass. Two conventions coexisting would be worse than either, and a policy with an
escape hatch is how the second convention gets in.

**Base locale is English**, in `values/`. Polish lives in `values-pl/`. English is the
fallback for any string a translation has not caught up with, which is the reason it is the
base rather than Polish: a missing Polish string should degrade to readable English, not to
a resource key. This is independent of `AppSettings.interfaceLocale`, which stays `pl` — the
app still starts in Polish, it simply falls back to English per-string rather than wholesale.

**The wrinkle, and why it is not an escape hatch.** compose-resources resolves against the
*system* locale. §13 requires the interface locale to be an app setting independent of the
system, because a student may read Polish UI while being examined in Latin — that
independence is the whole reason §13 exists. compose-resources therefore does not satisfy
§13 on its own; it needs an app-level locale override.

Three mechanisms, in preference order:

1. `AppCompatDelegate.setApplicationLocales` on Android with the `AppleLanguages` default
   on iOS — platform-native, least code.
2. A `CompositionLocal` carrying the active locale, with a lookup wrapper that resolves
   the resource against it.
3. Our own resolver over the generated `Res` accessors, keyed on `interfaceLocale`.

Proving (1) on both targets is the **first** task of the localisation phase. If it fails we
descend to (2), then (3). **Reverting to literals is not one of the options** — the failure
of a mechanism changes which mechanism is built, not whether strings are resources.

---

## 9. Screen inventory

| # | Screen | Module | Reads | States that must exist |
|---|---|---|---|---|
| 02 | Goal setting | `feature-onboarding` | `SettingsRepository`, `AtlasRepository` | none selected; some selected |
| 03 | First pack download | `feature-onboarding` | `PackRepository` | queued; progress; failed-resumable; done |
| 07 | Layer / system panel | `feature-atlas` | `AtlasRepository`, `IsolationPolicy` | all visible; systems toggled; one isolated with neighbours ghosted |
| 08 | Topic selection | `feature-quiz` | `QuizRepository`, `ProgressRepository`, `EntitlementRepository` | untouched topic; partial mastery; locked by entitlement |
| 09 | Tap the structure | `feature-quiz` | `QuizRepository`, renderer | before answering; timer running; timer disabled |
| 10 | Name the highlighted | `feature-quiz` | `QuizRepository` | four options, none chosen |
| 11 | Answer feedback, correct | `feature-quiz` | `AnswerResult` | single state |
| 12 | Answer feedback, incorrect | `feature-quiz` | `AnswerResult` | expected and chosen distinguished by hue **and** shape |
| 13 | Session summary | `feature-quiz` | `QuizSummary` | perfect score; score with review list |
| 14 | Home / dashboard | `feature-daily` | `DailyRepository`, `ProgressRepository` | daily not started; in progress; done; offline |
| 15 | Daily quiz lobby | `feature-daily` | `DailyRepository` | ready; running; already attempted today; offline |
| 16 | Leaderboard | `feature-daily` | `LeaderboardRepository` | daily board; streak board; unranked user |
| 17 | Profile | `feature-profile` | `ProgressRepository`, `PackRepository` | streak calendar; mastery by system |
| 18 | Paywall | `feature-commerce` | `EntitlementRepository` | free; subscribed; purchase failed |
| 19 | Pack manager | `feature-profile` | `PackRepository` | installed; available; downloading; delete confirm |
| 21 | Structure tree mode | `feature-atlas` | `AtlasRepository`, renderer | tree navigation with camera focus and announcement |

Screen 21 lives in `feature-atlas` because §12 defines it as coupled to the renderer —
selecting a structure focuses the camera and announces the name — not as a detached list.
Screen 07 lives there because it is what finally gives `IsolationPolicy` the production
caller §26 records it has never had.

---

## 10. Testing

Following §15 and the existing convention: a ViewModel test per screen in `commonTest`,
running on both the JVM host and the iOS simulator, driven by the shared fakes.

Three tests exist because of decisions made here, not because of a screen:

1. **Screen 12 distinguishes by more than hue.** It ships without §12's outlines, so the
   assertion that expected and chosen differ in shape as well as colour is what keeps the
   shortcut honest.
2. **No `UNVERIFIED` structure is ever an expected answer** (§7), asserted against the fake.
3. **The locale override changes rendered strings** on both targets — the §8 spike, kept as
   a regression test once it passes.

Compose UI tests are **out of scope — decided, not merely deferred.** Nothing in the tree
has them today, and standing up a UI test harness alongside sixteen new screens is two
pieces of work wearing one hat. The fakes make manual running cheap, which is the gap that
actually needs closing first. Revisit it as its own project once the screens exist.

---

## 11. Order of work

1. **Nav and locale spikes.** navigation-compose 2.9.2 against lifecycle `2.11.0-beta01`,
   and the app-level locale override. Both are single-day questions whose answers change
   the design. Nothing else starts until they are answered. The locale spike decides *which*
   of §8's three mechanisms is built, not whether strings are resources.
2. **Contracts and fakes.** All interfaces, models, `kotlinx-datetime` in the catalogue,
   `:shared:core-data-fake`, the shared fixture. Nothing visible; everything unblocked.
3. **The seam.** `AppDependencies`, entry points, debug wiring.
4. **`core-navigation`,** and `AtlasTab` migrated onto it.
5. **Localisation pass** over the five existing screens.
6. **07 and 21** — the two atlas gaps, and the first production caller of `IsolationPolicy`.
7. **02 and 03** — onboarding.
8. **08 to 13** — quiz.
9. **14 to 16** — daily and leaderboard.
10. **17, 18, 19** — profile, paywall, pack manager.

Steps 6 to 10 are independent of each other once 1 to 5 land.

---

## 12. Risks

| Risk | Consequence | Mitigation |
|---|---|---|
| navigation-compose 2.9.2 vs lifecycle 2.11.0-beta01 | Build or runtime failure late in the work | Step 1 spike; fall back to nav 2.10.0-beta01 or pin lifecycle |
| The platform locale override does not work on both targets | §13 unmet until a second mechanism is built | Step 1 spike; descend to the `CompositionLocal` wrapper, then to our own resolver (§8). Strings stay resources either way, so the cost is bounded to the lookup layer |
| `kotlinx-datetime` added mid-work | A new dependency argued about while screens wait | Added deliberately in step 2; see §4.7 |
| Fakes drift from what a real backend can do | Screens designed around impossible data | Contracts derived from §9 to §11 endpoints, not invented freely |
| Screen 12 ships without §12 outlines | An accessibility claim the app cannot make | Shape plus luminance, a test, and it stays marked unfinished |
| Sixteen screens with no UI tests | Regressions found by hand or not at all | Accepted for now; ViewModel coverage on both targets is the floor |

---

## 13. Open questions

1. **Entitlement enforcement on screen 08.** A locked topic could be hidden or shown locked.
   Showing it locked is the better sales argument and the worse study experience. Decide
   when screen 18's copy is written; the contract supports either.
2. **Practice mode** (§14 offers it when the daily is offline) is not in the 21-screen
   brief. It is out of scope here; screen 15's `Unavailable` state names it without
   implementing it.

---

## 14. What this does not do

No question generation (§8.1), no backend (§11), no billing integration, no download
implementation, no §12 outline shaders, no Compose UI tests. Each of those is named in the
contracts so the screens are built to receive them, and none is built here.
