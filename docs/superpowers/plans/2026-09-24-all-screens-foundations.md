# All Screens — Foundations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build every contract, fake, seam and mechanism the sixteen unbuilt screens need, so that plans 2–6 add screens and nothing else.

**Architecture:** Six repository interfaces land in `core-data` as the permanent seam Phase 3's Ktor implementations will satisfy. A new `:shared:core-data-fake` module implements them in memory over one shared trilingual fixture. `App()` stops constructing its own repositories and takes an `AppDependencies` holder, which is what keeps fakes out of release builds. Navigation moves to navigation-compose in its own module, and every UI string becomes a compose-resources lookup.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform 1.11.1, Material3 1.11.0-alpha07, navigation-compose 2.9.2, kotlinx-datetime, kotlinx-coroutines 1.10.2, Room 2.8.4, kotlin.test.

**Spec:** `docs/superpowers/specs/2026-09-24-all-screens-mocked-design.md`

## Global Constraints

- **Repository root is `~/StudioProjects/AnatomyPro`.** The tree at `~/Projekty/anatomy pro` is a superseded docs-only repo — never edit it.
- **Toolchain checked 2026-10-02:** `git`, `python3` and the iOS simulator test target work (Xcode 27.0). `xcodebuild -checkFirstLaunchStatus` still reports first-launch tasks pending; if an `xcodebuild` step fails, run `sudo xcodebuild -runFirstLaunch`.
- `perl -pi` without `-CSD` double-encodes every non-ASCII byte in a file it rewrites. Edit files with an editor or a quoted heredoc, not with `perl -pi`.
- **No `Co-Authored-By` or `Claude-Session` trailers on commits.** Project rule.
- **Tests run on two targets.** `./gradlew :shared:<module>:allTests` runs the JVM host and the iOS simulator. A test that passes on one and not the other is a failure.
- **`./gradlew --stop` between long sessions.** Daemons accumulate and this machine runs out of memory.
- Ids are lowercase kebab-case slugs, validated in `init` (`core-model/Ids.kt`).
- Package root is `com.ptk.anatomypro`.
- Only `VERIFIED` structures may be quiz answers (spec §7). This is enforced in the fakes, not only in the future real generator.
- UI strings are always resources. There is no literal fallback (spec §8).
- Base resource locale is **English** in `values/`; Polish in `values-pl/`.

---

### Task 1: Prove navigation-compose 2.9.2 against lifecycle 2.11.0-beta01

This is a spike. Its deliverable is a verified version pair plus the catalogue entry — not a screen. Everything else in this plan assumes the pair works; if it does not, the fallback is recorded in Step 6.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Create: `shared/core-navigation/build.gradle.kts`
- Create: `shared/core-navigation/src/commonMain/kotlin/com/ptk/anatomypro/navigation/SpikeRoutes.kt`
- Create: `shared/core-navigation/src/commonTest/kotlin/com/ptk/anatomypro/navigation/SpikeRoutesTest.kt`
- Modify: `settings.gradle.kts`

**Interfaces:**
- Consumes: nothing.
- Produces: the `:shared:core-navigation` module and a `libs.navigation.compose` catalogue accessor, both used from Task 14.

- [ ] **Step 1: Add the catalogue entries**

In `gradle/libs.versions.toml`, under `[versions]`:

```toml
navigation = "2.9.2"
```

Under `[libraries]`:

```toml
navigation-compose = { module = "org.jetbrains.androidx.navigation:navigation-compose", version.ref = "navigation" }
```

- [ ] **Step 2: Register the module**

In `settings.gradle.kts`, after `include(":shared:core-designsystem")`:

```kotlin
include(":shared:core-navigation")
```

- [ ] **Step 3: Create the module build file**

`shared/core-navigation/build.gradle.kts`:

```kotlin
plugins {
    id("anatomypro.kmp.compose")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android { namespace = "com.ptk.anatomypro.navigation" }

    sourceSets {
        commonMain.dependencies {
            api(libs.navigation.compose)
            implementation(libs.compose.runtime)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
```

- [ ] **Step 4: Write the failing test**

Type-safe routes are `@Serializable`, so route encoding is testable without a UI test harness — which this project deliberately does not have (spec §10).

`shared/core-navigation/src/commonTest/kotlin/com/ptk/anatomypro/navigation/SpikeRoutesTest.kt`:

```kotlin
package com.ptk.anatomypro.navigation

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SpikeRoutesTest {

    @Test
    fun a_route_with_an_argument_round_trips() {
        val encoded = Json.encodeToString(SpikeDetail.serializer(), SpikeDetail("costa-vii"))
        val decoded = Json.decodeFromString(SpikeDetail.serializer(), encoded)

        assertEquals(SpikeDetail("costa-vii"), decoded)
    }
}
```

- [ ] **Step 5: Run it and watch it fail**

Run: `./gradlew :shared:core-navigation:allTests`
Expected: FAIL — `SpikeDetail` is unresolved.

- [ ] **Step 6: Write the spike source**

`shared/core-navigation/src/commonMain/kotlin/com/ptk/anatomypro/navigation/SpikeRoutes.kt`:

```kotlin
package com.ptk.anatomypro.navigation

import kotlinx.serialization.Serializable

/**
 * Throwaway. Task 14 replaces these with the real destination hierarchy; they exist only
 * so the version pair in Task 1 is proven against something that compiles.
 */
@Serializable
data object SpikeHome

@Serializable
data class SpikeDetail(val structureId: String)
```

- [ ] **Step 7: Run the test and the compile checks on both targets**

Run all three, and all three must pass:

```bash
./gradlew :shared:core-navigation:allTests
./gradlew :shared:core-navigation:compileKotlinIosSimulatorArm64
./gradlew :androidApp:assembleDebug
```

Expected: BUILD SUCCESSFUL for each.

**If any fails with a lifecycle version conflict** (a `NoSuchMethodError`, a duplicate-class error, or a Gradle resolution failure naming `org.jetbrains.androidx.lifecycle`), the fallback order from spec §12 is: (a) raise `navigation` to `2.10.0-beta01`, re-run; (b) if that fails, pin `androidx-lifecycle` to the version navigation 2.9.2 resolves, re-run. Record which one you landed on in the commit message — Task 14 and plans 2–6 all sit on this.

- [ ] **Step 8: Commit**

```bash
git add gradle/libs.versions.toml settings.gradle.kts shared/core-navigation
git commit -m "build: add core-navigation on navigation-compose 2.9.2"
```

---

### Task 2: Prove the app-level locale override

The second spike. compose-resources resolves against the *system* locale; §13 needs it to follow `AppSettings.interfaceLocale` instead. This task decides which of spec §8's three mechanisms gets built. Reverting to literals is not an option — if mechanism 1 fails, build 2; if 2 fails, build 3.

**Files:**
- Modify: `shared/core-designsystem/build.gradle.kts`
- Create: `shared/core-designsystem/src/commonMain/composeResources/values/strings.xml`
- Create: `shared/core-designsystem/src/commonMain/composeResources/values-pl/strings.xml`
- Create: `shared/core-designsystem/src/commonMain/kotlin/com/ptk/anatomypro/core/designsystem/AppLocale.kt`
- Create: `shared/core-designsystem/src/commonTest/kotlin/com/ptk/anatomypro/core/designsystem/AppLocaleTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `LocalAppLocale: ProvidableCompositionLocal<String>` and `@Composable fun ProvideAppLocale(locale: String, content: @Composable () -> Unit)`, used by Task 13 and Task 16 and by every screen in plans 2–6.

- [ ] **Step 1: Turn on compose-resources in core-designsystem**

In `shared/core-designsystem/build.gradle.kts`, add to `commonMain.dependencies`:

```kotlin
implementation(libs.compose.components.resources)
```

- [ ] **Step 2: Add the two string files**

`shared/core-designsystem/src/commonMain/composeResources/values/strings.xml` — English is the base (spec §8):

```xml
<resources>
    <string name="locale_probe">Atlas</string>
</resources>
```

`shared/core-designsystem/src/commonMain/composeResources/values-pl/strings.xml`:

```xml
<resources>
    <string name="locale_probe">Atlas anatomiczny</string>
</resources>
```

The two values differ so the probe can tell which bundle was resolved. `Atlas` alone would pass whichever bundle answered.

- [ ] **Step 3: Write the failing test**

`shared/core-designsystem/src/commonTest/kotlin/com/ptk/anatomypro/core/designsystem/AppLocaleTest.kt`:

```kotlin
package com.ptk.anatomypro.core.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals

class AppLocaleTest {

    @Test
    fun the_default_is_the_apps_default_interface_locale_not_the_systems() {
        assertEquals("pl", DEFAULT_APP_LOCALE)
    }

    @Test
    fun a_supported_locale_is_kept() {
        assertEquals("en", resolveAppLocale("en"))
        assertEquals("pl", resolveAppLocale("pl"))
    }

    @Test
    fun an_unsupported_locale_falls_back_to_english_because_english_is_the_base_bundle() {
        assertEquals("en", resolveAppLocale("de"))
        assertEquals("en", resolveAppLocale(""))
    }
}
```

- [ ] **Step 4: Run it and watch it fail**

Run: `./gradlew :shared:core-designsystem:allTests`
Expected: FAIL — `DEFAULT_APP_LOCALE` and `resolveAppLocale` are unresolved.

- [ ] **Step 5: Write the implementation**

`shared/core-designsystem/src/commonMain/kotlin/com/ptk/anatomypro/core/designsystem/AppLocale.kt`:

```kotlin
package com.ptk.anatomypro.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/** Matches AppSettings.interfaceLocale's default: the app starts in Polish (spec §8). */
const val DEFAULT_APP_LOCALE = "pl"

/** English is the base bundle, so it is what an unknown locale degrades to (spec §8). */
const val BASE_APP_LOCALE = "en"

/** The locales a UI string bundle exists for. Latin is a study locale, never a UI one. */
val SUPPORTED_UI_LOCALES = setOf("en", "pl")

fun resolveAppLocale(requested: String): String =
    if (requested in SUPPORTED_UI_LOCALES) requested else BASE_APP_LOCALE

/**
 * The interface locale, independent of the system's.
 *
 * §13 makes the interface language an app setting: a student may read Polish UI while
 * being examined in Latin. compose-resources resolves against the system locale, so the
 * active locale has to be carried explicitly rather than read from the platform.
 */
val LocalAppLocale: ProvidableCompositionLocal<String> =
    compositionLocalOf { DEFAULT_APP_LOCALE }

@Composable
fun ProvideAppLocale(locale: String, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppLocale provides resolveAppLocale(locale), content)
}
```

- [ ] **Step 6: Run the test and watch it pass**

Run: `./gradlew :shared:core-designsystem:allTests`
Expected: PASS on both the JVM host and the iOS simulator.

- [ ] **Step 7: Prove the override end to end, by hand**

The unit test proves the resolver, not that compose-resources honours it. Do this manually, once:

1. In `App.kt`, temporarily wrap the existing content in `ProvideAppLocale("en") { ... }` and render `stringResource(Res.string.locale_probe)` somewhere visible.
2. `./gradlew :androidApp:assembleDebug`, install, launch. Expected: **`Atlas`** (English), on a device whose system language is Polish.
3. Change the wrap to `ProvideAppLocale("pl")`, rebuild. Expected: **`Atlas anatomiczny`**.
4. Repeat on the iOS simulator via `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` and the Xcode host.
5. Revert the temporary probe from `App.kt`. **Keep `locale_probe` in both bundles.** It is the only string in the tree whose value differs between locales by construction, which makes it the cheapest way to re-run this check after any compose-resources or Kotlin upgrade. It is a kept tool, not a leftover — Task 16 does not remove it.

**If the string does not change with the `CompositionLocal`,** compose-resources is reading the platform locale and mechanism 2 is insufficient. Escalate to §8 mechanism 1 first (`AppCompatDelegate.setApplicationLocales` on Android, the `AppleLanguages` `NSUserDefaults` key on iOS, both invoked from the platform entry points); if that also fails, build mechanism 3 — a resolver that selects the bundle by `LocalAppLocale` over the generated `Res` accessors. Record which mechanism you landed on in the commit message. Plans 2–6 write every screen against whichever one this task leaves behind.

- [ ] **Step 8: Commit**

```bash
git add shared/core-designsystem
git commit -m "feat: carry the interface locale independently of the system locale"
```

---

### Task 3: kotlinx-datetime, and the three quiz ids

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `shared/core-data/build.gradle.kts`
- Create: `shared/core-model/src/commonMain/kotlin/com/ptk/anatomypro/core/model/QuizIds.kt`
- Create: `shared/core-model/src/commonTest/kotlin/com/ptk/anatomypro/core/model/QuizIdsTest.kt`

**Interfaces:**
- Consumes: the slug rule in `core-model/Ids.kt`.
- Produces: `QuizTopicId(value: String)`, `QuizSessionId(value: String)`, `QuizQuestionId(value: String)` — consumed by Tasks 4, 5, 10.

- [ ] **Step 1: Add kotlinx-datetime to the catalogue**

Spec §4.7: nothing in the tree has needed a date before now, so this is a deliberate addition rather than a mid-task discovery.

In `gradle/libs.versions.toml`, under `[versions]`:

```toml
kotlinx-datetime = "0.8.0"
```

Under `[libraries]`:

```toml
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinx-datetime" }
```

- [ ] **Step 2: Wire it into core-data**

In `shared/core-data/build.gradle.kts`, add to `commonMain.dependencies`:

```kotlin
api(libs.kotlinx.datetime)
```

`api` rather than `implementation`: `LocalDate` and `Instant` appear in the repository signatures in Task 5, so every consumer of `core-data` sees them.

- [ ] **Step 3: Pin down the date API before six files depend on it**

kotlinx-datetime moved `Instant` to the standard library in 0.7 and widened epoch days from `Int` to `Long`, so the API this plan is written against was read from the 0.8.0 dump rather than compiled. Confirm it once, here, instead of discovering it in Task 11.

Create `shared/core-data/src/commonTest/kotlin/com/ptk/anatomypro/core/data/DateApiProbeTest.kt`:

```kotlin
package com.ptk.anatomypro.core.data

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class DateApiProbeTest {

    @Test
    fun the_api_this_plan_assumes_is_the_api_this_version_has() {
        val date = LocalDate(2026, 9, 24)

        assertEquals(24, date.day)
        assertEquals(LocalDate(2026, 9, 25), LocalDate.fromEpochDays(date.toEpochDays() + 1))
        assertEquals(0L, Instant.fromEpochSeconds(0).epochSeconds)
    }
}
```

Run: `./gradlew :shared:core-data:allTests`
Expected: PASS.

**If it does not compile**, fix the probe against whatever the version actually offers, then apply the same correction wherever this plan uses that call — `Instant` in Task 5's `Daily.kt` and Task 11's `FakeDailyRepository`, `day` and `fromEpochDays` in Task 11's `FakeProgressRepository.activity`. Delete the probe afterwards; it is scaffolding, not coverage.

- [ ] **Step 4: Write the failing test**

`shared/core-model/src/commonTest/kotlin/com/ptk/anatomypro/core/model/QuizIdsTest.kt`:

```kotlin
package com.ptk.anatomypro.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QuizIdsTest {

    @Test
    fun a_topic_id_is_a_slug_like_every_other_id() {
        assertEquals("skeletal-thorax", QuizTopicId("skeletal-thorax").value)
    }

    @Test
    fun a_session_id_generated_at_runtime_is_still_a_slug() {
        assertEquals("session-1a2b3c", QuizSessionId("session-1a2b3c").value)
    }

    @Test
    fun a_question_id_rejects_uppercase_so_ids_cannot_drift_apart_by_case() {
        assertFailsWith<IllegalArgumentException> { QuizQuestionId("Q1") }
    }

    @Test
    fun all_three_reject_blank() {
        assertFailsWith<IllegalArgumentException> { QuizTopicId("") }
        assertFailsWith<IllegalArgumentException> { QuizSessionId("") }
        assertFailsWith<IllegalArgumentException> { QuizQuestionId("") }
    }
}
```

- [ ] **Step 5: Run it and watch it fail**

Run: `./gradlew :shared:core-model:allTests`
Expected: FAIL — the three types are unresolved.

- [ ] **Step 6: Write the implementation**

`Ids.kt` keeps `requireSlug` private, so the new file repeats the pattern rather than reaching into it. Add `QuizIds.kt`:

```kotlin
package com.ptk.anatomypro.core.model

import kotlin.jvm.JvmInline

private val QUIZ_SLUG = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

private fun requireQuizSlug(value: String, kind: String): String {
    require(value.matches(QUIZ_SLUG)) {
        "$kind must be a lowercase kebab-case slug, was: '$value'"
    }
    return value
}

/** A system or region a quiz can be scoped to (spec §8.2). */
@JvmInline
value class QuizTopicId(val value: String) {
    init { requireQuizSlug(value, "QuizTopicId") }
}

/** One run through a topic. Generated at runtime, still a slug. */
@JvmInline
value class QuizSessionId(val value: String) {
    init { requireQuizSlug(value, "QuizSessionId") }
}

@JvmInline
value class QuizQuestionId(val value: String) {
    init { requireQuizSlug(value, "QuizQuestionId") }
}
```

- [ ] **Step 7: Run the test and watch it pass**

Run: `./gradlew :shared:core-model:allTests`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add gradle/libs.versions.toml shared/core-model shared/core-data/build.gradle.kts
git commit -m "feat: add quiz ids and kotlinx-datetime"
```

---

### Task 4: Quiz domain models

**Files:**
- Create: `shared/core-data/src/commonMain/kotlin/com/ptk/anatomypro/core/data/model/Quiz.kt`
- Create: `shared/core-data/src/commonTest/kotlin/com/ptk/anatomypro/core/data/model/QuizTest.kt`

**Interfaces:**
- Consumes: `QuizTopicId`, `QuizSessionId`, `QuizQuestionId` (Task 3); `StructureSummary`, `StructureId`.
- Produces: `QuizFormat`, `Difficulty`, `QuizQuestion` (sealed, with `TapTheStructure` and `NameTheHighlighted`), `QuizAnswer`, `QuizTopic`, `QuizSession`, `AnswerResult`, `QuizSummary` — consumed by Tasks 5, 10, 11 and all of plan 4.

- [ ] **Step 1: Write the failing test**

The models are mostly data; the behaviour worth testing is the invariant that keeps a malformed question out of the UI. A `correctIndex` pointing past the options is the bug that would surface as a crash three screens later.

`shared/core-data/src/commonTest/kotlin/com/ptk/anatomypro/core/data/model/QuizTest.kt`:

```kotlin
package com.ptk.anatomypro.core.data.model

import com.ptk.anatomypro.core.model.Laterality
import com.ptk.anatomypro.core.model.QuizQuestionId
import com.ptk.anatomypro.core.model.StructureId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private fun summary(id: String) = StructureSummary(
    id = StructureId(id),
    name = id,
    latinName = id,
    laterality = Laterality.MEDIAN,
    isGroup = false,
    hasChildren = false,
)

class QuizTest {

    private val options = listOf(summary("costa-i"), summary("costa-ii"), summary("costa-iii"), summary("costa-iv"))

    @Test
    fun a_multiple_choice_question_names_its_correct_option() {
        val question = QuizQuestion.NameTheHighlighted(
            id = QuizQuestionId("q1"),
            difficulty = Difficulty.HARD,
            highlighted = StructureId("costa-ii"),
            options = options,
            correctIndex = 1,
        )

        assertEquals(summary("costa-ii"), question.correct)
    }

    @Test
    fun a_correct_index_outside_the_options_is_rejected_at_construction() {
        assertFailsWith<IllegalArgumentException> {
            QuizQuestion.NameTheHighlighted(
                id = QuizQuestionId("q1"),
                difficulty = Difficulty.HARD,
                highlighted = StructureId("costa-ii"),
                options = options,
                correctIndex = 4,
            )
        }
    }

    @Test
    fun repeated_options_are_rejected_because_two_identical_answers_cannot_both_be_wrong() {
        assertFailsWith<IllegalArgumentException> {
            QuizQuestion.NameTheHighlighted(
                id = QuizQuestionId("q1"),
                difficulty = Difficulty.EASY,
                highlighted = StructureId("costa-i"),
                options = listOf(summary("costa-i"), summary("costa-i")),
                correctIndex = 0,
            )
        }
    }

    @Test
    fun a_summary_scores_as_a_fraction_and_survives_an_empty_session() {
        val scored = QuizSummary(QuizSessionId("s1"), correct = 3, total = 4, elapsedMillis = 0, needsReview = emptyList())
        val empty = QuizSummary(QuizSessionId("s2"), correct = 0, total = 0, elapsedMillis = 0, needsReview = emptyList())

        assertEquals(0.75f, scored.fraction)
        assertEquals(0f, empty.fraction)
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `./gradlew :shared:core-data:allTests`
Expected: FAIL — `QuizQuestion`, `Difficulty`, `QuizSummary` are unresolved.

- [ ] **Step 3: Write the implementation**

`shared/core-data/src/commonMain/kotlin/com/ptk/anatomypro/core/data/model/Quiz.kt`:

```kotlin
package com.ptk.anatomypro.core.data.model

import com.ptk.anatomypro.core.model.QuizQuestionId
import com.ptk.anatomypro.core.model.QuizSessionId
import com.ptk.anatomypro.core.model.QuizTopicId
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.SystemId

/** The two v1 formats (spec §8). Both generate from the structure graph. */
enum class QuizFormat { TAP_THE_STRUCTURE, NAME_THE_HIGHLIGHTED }

/** §8.1's single lever: how close the distractors sit in the structure graph. */
enum class Difficulty { EASY, MEDIUM, HARD }

/** Screen 08's grid cell. */
data class QuizTopic(
    val id: QuizTopicId,
    val title: String,
    val system: SystemId,
    val structureCount: Int,
    /** 0f to 1f. Screen 08 shows this as progress, never as a percentage of nothing. */
    val mastery: Float,
) {
    init { require(mastery in 0f..1f) { "mastery must be a fraction, was $mastery" } }
}

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

    /** Screen 10: one structure is highlighted, options are offered. */
    data class NameTheHighlighted(
        override val id: QuizQuestionId,
        override val difficulty: Difficulty,
        val highlighted: StructureId,
        val options: List<StructureSummary>,
        val correctIndex: Int,
    ) : QuizQuestion {
        init {
            require(correctIndex in options.indices) {
                "correctIndex $correctIndex is outside ${options.size} options"
            }
            require(options.distinctBy { it.id }.size == options.size) {
                "options must be distinct; two identical answers cannot both be wrong"
            }
        }

        val correct: StructureSummary get() = options[correctIndex]
    }
}

/** What the user did. [chosen] is null when a timed question ran out. */
data class QuizAnswer(
    val questionId: QuizQuestionId,
    val chosen: StructureId?,
    val elapsedMillis: Long,
)

data class QuizSession(
    val id: QuizSessionId,
    val topic: QuizTopicId,
    val format: QuizFormat,
    val questions: List<QuizQuestion>,
    /** Recorded so a bad question set can be reproduced (spec §8.2). */
    val seed: Long,
)

/**
 * Shaped by screen 12 rather than screen 11.
 *
 * The incorrect state needs both structures at once — the right one and the mistaken one —
 * and [sharedAncestor] is what lets it say how they relate rather than only that they differ.
 */
data class AnswerResult(
    val questionId: QuizQuestionId,
    val correct: Boolean,
    val expected: StructureSummary,
    val chosen: StructureSummary?,
    val sharedAncestor: StructureSummary?,
    val elapsedMillis: Long,
)

data class QuizSummary(
    val session: QuizSessionId,
    val correct: Int,
    val total: Int,
    val elapsedMillis: Long,
    /** Screen 13's review list, worst first. */
    val needsReview: List<StructureSummary>,
) {
    /** 0f for an empty session rather than a division by zero on a screen. */
    val fraction: Float get() = if (total == 0) 0f else correct.toFloat() / total
}
```

- [ ] **Step 4: Run the test and watch it pass**

Run: `./gradlew :shared:core-data:allTests`
Expected: PASS on both targets.

- [ ] **Step 5: Commit**

```bash
git add shared/core-data/src/commonMain/kotlin/com/ptk/anatomypro/core/data/model/Quiz.kt shared/core-data/src/commonTest
git commit -m "feat: add quiz domain models"
```

---

### Task 5: Progress, daily, leaderboard, entitlement and pack models

**Files:**
- Create: `shared/core-data/src/commonMain/kotlin/com/ptk/anatomypro/core/data/model/Progress.kt`
- Create: `shared/core-data/src/commonMain/kotlin/com/ptk/anatomypro/core/data/model/Daily.kt`
- Create: `shared/core-data/src/commonMain/kotlin/com/ptk/anatomypro/core/data/model/Entitlements.kt`
- Create: `shared/core-data/src/commonMain/kotlin/com/ptk/anatomypro/core/data/model/Packs.kt`
- Create: `shared/core-data/src/commonTest/kotlin/com/ptk/anatomypro/core/data/model/EntitlementsTest.kt`
- Create: `shared/core-data/src/commonTest/kotlin/com/ptk/anatomypro/core/data/model/PacksTest.kt`

**Interfaces:**
- Consumes: `SystemId`, `PackId`, `QuizQuestion` (Task 4), `kotlinx.datetime` (Task 3).
- Produces: `StreakState`, `SystemMastery`, `DayActivity`, `DailyQuiz`, `DailyResult`, `LeaderboardEntry`, `Leaderboard`, `Entitlements`, `SubscriptionPlan`, `PurchaseOutcome`, `PackState`, `PackStatus`, `PackFailure` — consumed by Tasks 6, 11, 12 and plans 5 and 6.

- [ ] **Step 1: Write the failing tests**

Two models carry real logic. `Entitlements.allows` decides the paywall, and it must enforce "skeletal is free forever" itself — the spec's §4.5 sketch left that to the caller, which would make the free tier depend on a server remembering to include it in every response.

`shared/core-data/src/commonTest/kotlin/com/ptk/anatomypro/core/data/model/EntitlementsTest.kt`:

```kotlin
package com.ptk.anatomypro.core.data.model

import com.ptk.anatomypro.core.model.SystemId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntitlementsTest {

    private val skeletal = SystemId("skeletal")
    private val muscular = SystemId("muscular")

    @Test
    fun skeletal_is_free_forever_even_with_no_subscription_and_nothing_owned() {
        val none = Entitlements(subscribed = false, ownedSystems = emptySet())

        assertTrue(none.allows(skeletal))
    }

    @Test
    fun another_system_is_not_free() {
        val none = Entitlements(subscribed = false, ownedSystems = emptySet())

        assertFalse(none.allows(muscular))
    }

    @Test
    fun a_subscription_allows_everything() {
        val subscribed = Entitlements(subscribed = true, ownedSystems = emptySet())

        assertTrue(subscribed.allows(muscular))
    }

    @Test
    fun an_owned_system_is_allowed_without_a_subscription() {
        val owned = Entitlements(subscribed = false, ownedSystems = setOf(muscular))

        assertTrue(owned.allows(muscular))
    }
}
```

`shared/core-data/src/commonTest/kotlin/com/ptk/anatomypro/core/data/model/PacksTest.kt`:

```kotlin
package com.ptk.anatomypro.core.data.model

import kotlin.test.Test
import kotlin.test.assertEquals

class PacksTest {

    @Test
    fun a_download_reports_a_fraction_screen_03_can_draw() {
        assertEquals(0.25f, PackStatus.Downloading(bytesDone = 25, bytesTotal = 100).fraction)
    }

    @Test
    fun a_download_that_has_not_learned_its_total_reports_zero_rather_than_dividing_by_it() {
        assertEquals(0f, PackStatus.Downloading(bytesDone = 0, bytesTotal = 0).fraction)
    }
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `./gradlew :shared:core-data:allTests`
Expected: FAIL — `Entitlements` and `PackStatus` are unresolved.

- [ ] **Step 3: Write Progress.kt**

```kotlin
package com.ptk.anatomypro.core.data.model

import com.ptk.anatomypro.core.model.SystemId
import kotlinx.datetime.LocalDate

/**
 * Screen 14's streak and screen 17's calendar.
 *
 * [lastStudied] is a local date. The daily quiz works in UTC (§9.1), and the two must not
 * be conflated: the calendar is the user's day, the daily quiz is the world's.
 */
data class StreakState(
    val currentDays: Int,
    val longestDays: Int,
    val lastStudied: LocalDate?,
)

data class SystemMastery(
    val system: SystemId,
    val title: String,
    val structuresSeen: Int,
    val structuresTotal: Int,
) {
    val fraction: Float get() = if (structuresTotal == 0) 0f else structuresSeen.toFloat() / structuresTotal
}

/** One square in screen 17's calendar. */
data class DayActivity(val sessions: Int, val questionsAnswered: Int, val dailyCompleted: Boolean)
```

- [ ] **Step 4: Write Daily.kt**

```kotlin
package com.ptk.anatomypro.core.data.model

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * Mirrors §9.1's three-step flow rather than flattening it: the clock starts when the
 * questions are fetched, and screen 15 is what the user looks at while it runs.
 */
sealed interface DailyQuiz {
    data class NotStarted(val date: LocalDate, val questionCount: Int) : DailyQuiz

    data class InProgress(
        val date: LocalDate,
        val questions: List<QuizQuestion>,
        val startedAt: Instant,
    ) : DailyQuiz

    data class Completed(val date: LocalDate, val result: DailyResult) : DailyQuiz

    /**
     * §14: offline blocks entry with a clear message and offers practice mode.
     *
     * A state rather than a thrown exception, because it has designed copy and a designed
     * screen — it is not an error path.
     */
    data object Unavailable : DailyQuiz
}

/** Scored server-side against a key that never leaves the server (§9.1). */
data class DailyResult(
    val date: LocalDate,
    val correct: Int,
    val total: Int,
    val elapsedMillis: Long,
    val rank: Int?,
    val totalPlayers: Int,
)

data class LeaderboardEntry(
    val rank: Int,
    val nickname: String,
    val score: Int,
    val elapsedMillis: Long,
    val isMe: Boolean,
)

/**
 * [me] is nullable: an untimed session is unranked (§12), and a user who has not played
 * today has no row. Carrying it on the board keeps "your position" from being a second
 * call that can disagree with the first.
 */
data class Leaderboard(
    val entries: List<LeaderboardEntry>,
    val me: LeaderboardEntry?,
    val totalPlayers: Int,
)
```

- [ ] **Step 5: Write Entitlements.kt**

```kotlin
package com.ptk.anatomypro.core.data.model

import com.ptk.anatomypro.core.model.SystemId

/** Skeletal is free forever (§10). Enforced here, not by whoever fills ownedSystems. */
val FREE_SYSTEMS: Set<SystemId> = setOf(SystemId("skeletal"))

data class Entitlements(val subscribed: Boolean, val ownedSystems: Set<SystemId>) {
    fun allows(system: SystemId): Boolean =
        subscribed || system in FREE_SYSTEMS || system in ownedSystems
}

data class SubscriptionPlan(
    val id: String,
    val title: String,
    val priceLabel: String,
    val periodMonths: Int,
)

sealed interface PurchaseOutcome {
    data class Succeeded(val entitlements: Entitlements) : PurchaseOutcome
    data object Cancelled : PurchaseOutcome
    data class Failed(val message: String) : PurchaseOutcome
}
```

- [ ] **Step 6: Write Packs.kt**

```kotlin
package com.ptk.anatomypro.core.data.model

import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.core.model.SystemId

/**
 * The manifest fields §10 names, minus the download URL: that is issued server-side
 * against entitlements and never belongs in UI state.
 */
data class PackState(
    val id: PackId,
    val version: Int,
    val label: String,
    val byteSize: Long,
    val checksum: String,
    val entitlement: SystemId?,
    val status: PackStatus,
)

sealed interface PackStatus {
    data object Available : PackStatus

    data class Downloading(val bytesDone: Long, val bytesTotal: Long) : PackStatus {
        /** 0f before the total is known, rather than a division by zero on screen 03. */
        val fraction: Float get() = if (bytesTotal == 0L) 0f else bytesDone.toFloat() / bytesTotal
    }

    data object Installed : PackStatus

    /** §14: downloads are resumable, so a failure is a state the UI offers to resume from. */
    data class Failed(val reason: PackFailure, val resumable: Boolean) : PackStatus
}

enum class PackFailure { NETWORK, CHECKSUM, OUT_OF_SPACE, NOT_ENTITLED }
```

- [ ] **Step 7: Run the tests and watch them pass**

Run: `./gradlew :shared:core-data:allTests`
Expected: PASS on both targets.

- [ ] **Step 8: Commit**

```bash
git add shared/core-data/src
git commit -m "feat: add progress, daily, entitlement and pack models"
```

---

### Task 6: The six repository interfaces

**Files:**
- Create: `shared/core-data/src/commonMain/kotlin/com/ptk/anatomypro/core/data/repository/QuizRepository.kt`
- Create: `shared/core-data/src/commonMain/kotlin/com/ptk/anatomypro/core/data/repository/ProgressRepository.kt`
- Create: `shared/core-data/src/commonMain/kotlin/com/ptk/anatomypro/core/data/repository/DailyRepository.kt`
- Create: `shared/core-data/src/commonMain/kotlin/com/ptk/anatomypro/core/data/repository/LeaderboardRepository.kt`
- Create: `shared/core-data/src/commonMain/kotlin/com/ptk/anatomypro/core/data/repository/EntitlementRepository.kt`
- Create: `shared/core-data/src/commonMain/kotlin/com/ptk/anatomypro/core/data/repository/PackRepository.kt`

**Interfaces:**
- Consumes: every model from Tasks 4 and 5.
- Produces: the six interfaces. Tasks 10–13 implement and consume them; plans 2–6 depend on nothing else.

Interfaces carry no behaviour, so there is no test of their own — Tasks 10–12 test them through the fakes. This task is a single compile-and-commit.

- [ ] **Step 1: Write QuizRepository.kt**

```kotlin
package com.ptk.anatomypro.core.data.repository

import com.ptk.anatomypro.core.data.model.AnswerResult
import com.ptk.anatomypro.core.data.model.QuizAnswer
import com.ptk.anatomypro.core.data.model.QuizFormat
import com.ptk.anatomypro.core.data.model.QuizSession
import com.ptk.anatomypro.core.data.model.QuizSummary
import com.ptk.anatomypro.core.data.model.QuizTopic
import com.ptk.anatomypro.core.model.QuizSessionId
import com.ptk.anatomypro.core.model.QuizTopicId

/**
 * Screens 08 to 13.
 *
 * Sessions are generated on-device and are seeded and reproducible (§8.2), so this is a
 * local contract. The fake and the eventual real generator differ in how they choose
 * questions, not in where they live.
 */
interface QuizRepository {

    suspend fun topics(locale: String): List<QuizTopic>

    suspend fun startSession(
        topic: QuizTopicId,
        format: QuizFormat,
        questionCount: Int,
        seed: Long,
    ): QuizSession

    suspend fun submit(session: QuizSessionId, answer: QuizAnswer): AnswerResult

    suspend fun finish(session: QuizSessionId): QuizSummary
}
```

- [ ] **Step 2: Write ProgressRepository.kt**

```kotlin
package com.ptk.anatomypro.core.data.repository

import com.ptk.anatomypro.core.data.model.DayActivity
import com.ptk.anatomypro.core.data.model.QuizSummary
import com.ptk.anatomypro.core.data.model.StreakState
import com.ptk.anatomypro.core.data.model.SystemMastery
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/** Screens 08, 14 and 17. */
interface ProgressRepository {
    val streak: Flow<StreakState>
    suspend fun masteryBySystem(locale: String): List<SystemMastery>
    /** Screen 17's calendar. Local dates; the daily quiz's own dates are UTC (§9.1). */
    suspend fun activity(from: LocalDate, to: LocalDate): Map<LocalDate, DayActivity>
    suspend fun recordSession(summary: QuizSummary)
}
```

- [ ] **Step 3: Write DailyRepository.kt**

```kotlin
package com.ptk.anatomypro.core.data.repository

import com.ptk.anatomypro.core.data.model.DailyQuiz
import com.ptk.anatomypro.core.data.model.DailyResult
import com.ptk.anatomypro.core.data.model.QuizAnswer

/** Screens 14 and 15. */
interface DailyRepository {
    suspend fun today(): DailyQuiz
    /** Fetches the day's questions; the server records this as the clock start (§9.1). */
    suspend fun start(): DailyQuiz.InProgress
    /** The server scores against its own key and returns rank (§9.1, §9.2). */
    suspend fun submit(answers: List<QuizAnswer>): DailyResult
}
```

- [ ] **Step 4: Write LeaderboardRepository.kt**

```kotlin
package com.ptk.anatomypro.core.data.repository

import com.ptk.anatomypro.core.data.model.Leaderboard
import kotlinx.datetime.LocalDate

/** Screen 16. v1 boards are global daily and personal streak (§9.3). */
interface LeaderboardRepository {
    suspend fun daily(date: LocalDate): Leaderboard
    suspend fun streaks(): Leaderboard
}
```

- [ ] **Step 5: Write EntitlementRepository.kt**

```kotlin
package com.ptk.anatomypro.core.data.repository

import com.ptk.anatomypro.core.data.model.Entitlements
import com.ptk.anatomypro.core.data.model.PurchaseOutcome
import com.ptk.anatomypro.core.data.model.SubscriptionPlan
import kotlinx.coroutines.flow.Flow

/** Screen 18. */
interface EntitlementRepository {
    val entitlements: Flow<Entitlements>
    suspend fun plans(): List<SubscriptionPlan>
    suspend fun purchase(plan: SubscriptionPlan): PurchaseOutcome
    suspend fun restore(): PurchaseOutcome
}
```

- [ ] **Step 6: Write PackRepository.kt**

```kotlin
package com.ptk.anatomypro.core.data.repository

import com.ptk.anatomypro.core.data.model.PackState
import com.ptk.anatomypro.core.model.PackId
import kotlinx.coroutines.flow.Flow

/** Screens 03 and 19. */
interface PackRepository {
    val packs: Flow<List<PackState>>
    suspend fun download(id: PackId)
    suspend fun cancel(id: PackId)
    suspend fun delete(id: PackId)
}
```

- [ ] **Step 7: Compile both targets**

Run: `./gradlew :shared:core-data:allTests`
Expected: PASS — the existing tests still pass and the new sources compile on the JVM host and the iOS simulator.

- [ ] **Step 8: Commit**

```bash
git add shared/core-data/src/commonMain/kotlin/com/ptk/anatomypro/core/data/repository
git commit -m "feat: add the six repository interfaces the unbuilt screens need"
```

---

### Task 7: studiedSystems on AppSettings

Screen 02 gets no repository (spec §4.8): "which systems am I studying" is a preference.

**Files:**
- Modify: `shared/core-data/src/commonMain/kotlin/com/ptk/anatomypro/core/data/model/AppSettings.kt`
- Modify: `shared/core-data/src/commonMain/kotlin/com/ptk/anatomypro/core/data/repository/SettingsRepository.kt`
- Create: `shared/core-data/src/commonTest/kotlin/com/ptk/anatomypro/core/data/model/StudiedSystemsTest.kt`

**Interfaces:**
- Consumes: `AppSettings`, `RoomSettingsRepository`.
- Produces: `AppSettings.studiedSystems: Set<String>`, read by screen 02 in plan 3 and by screen 08 in plan 4.

- [ ] **Step 1: Write the failing test**

The preference table stores strings, so a set has to survive a round trip through one. The empty case is the one that breaks naively: `"".split(",")` yields a list containing one empty string, not an empty list.

```kotlin
package com.ptk.anatomypro.core.data.model

import kotlin.test.Test
import kotlin.test.assertEquals

class StudiedSystemsTest {

    @Test
    fun a_set_of_systems_round_trips_through_the_preference_row() {
        assertEquals(setOf("skeletal", "muscular"), decodeStudiedSystems(encodeStudiedSystems(setOf("skeletal", "muscular"))))
    }

    @Test
    fun an_empty_set_round_trips_to_an_empty_set_not_a_set_holding_one_empty_string() {
        assertEquals(emptySet(), decodeStudiedSystems(encodeStudiedSystems(emptySet())))
    }

    @Test
    fun the_encoding_is_stable_so_an_unchanged_setting_writes_no_row() {
        assertEquals(encodeStudiedSystems(setOf("muscular", "skeletal")), encodeStudiedSystems(setOf("skeletal", "muscular")))
    }

    @Test
    fun the_default_is_empty_because_a_student_has_not_chosen_yet() {
        assertEquals(emptySet(), AppSettings().studiedSystems)
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `./gradlew :shared:core-data:allTests`
Expected: FAIL — `encodeStudiedSystems`, `decodeStudiedSystems` and `studiedSystems` are unresolved.

- [ ] **Step 3: Add the field and the codec**

In `AppSettings.kt`, add to the data class, after `onboarded`:

```kotlin
    /** Screen 02's goal setting. A preference, not a domain (spec §4.8). */
    val studiedSystems: Set<String> = emptySet(),
```

and below the class, in the same file:

```kotlin
/** Sorted so an unchanged set encodes identically and writes no row (see the update loop). */
internal fun encodeStudiedSystems(systems: Set<String>): String = systems.sorted().joinToString(",")

internal fun decodeStudiedSystems(stored: String?): Set<String> =
    stored?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
```

Note: the test calls these from the same package, so `internal` is sufficient and they stay out of the public surface.

- [ ] **Step 4: Persist it**

In `SettingsRepository.kt`, add to `toSettings()`'s `AppSettings(...)` construction:

```kotlin
            studiedSystems = decodeStudiedSystems(map[KEY_STUDIED]),
```

to `toRows()`'s map:

```kotlin
        KEY_STUDIED to encodeStudiedSystems(studiedSystems),
```

and to the companion:

```kotlin
        const val KEY_STUDIED = "study.systems"
```

- [ ] **Step 5: Run the tests and watch them pass**

Run: `./gradlew :shared:core-data:allTests`
Expected: PASS. `SettingsViewModelTest` must still pass unchanged — the new field is defaulted, so no existing construction breaks.

- [ ] **Step 6: Commit**

```bash
git add shared/core-data/src
git commit -m "feat: record which systems a student is studying"
```

---

### Task 8: The core-data-fake module, its fixture, and FakeBehaviour

**Files:**
- Modify: `settings.gradle.kts`
- Create: `shared/core-data-fake/build.gradle.kts`
- Create: `shared/core-data-fake/src/commonMain/kotlin/com/ptk/anatomypro/core/data/fake/FakeBehaviour.kt`
- Create: `shared/core-data-fake/src/commonMain/kotlin/com/ptk/anatomypro/core/data/fake/Fixture.kt`
- Create: `shared/core-data-fake/src/commonTest/kotlin/com/ptk/anatomypro/core/data/fake/FakeBehaviourTest.kt`
- Create: `shared/core-data-fake/src/commonTest/kotlin/com/ptk/anatomypro/core/data/fake/FixtureTest.kt`

**Interfaces:**
- Consumes: `StructureSummary`, `StructureDetail`, `VerificationState`, `Laterality`, `StructureId`, `SystemId`.
- Produces: `FakeBehaviour`, `FakeBehaviour.respond`, `FixtureStructure`, `AtlasFixture` with `all`, `byId`, `childrenOf`, `summary`, `detail` — consumed by Tasks 9–12.

- [ ] **Step 1: Register the module**

In `settings.gradle.kts`, after `include(":shared:core-data")`:

```kotlin
include(":shared:core-data-fake")
```

- [ ] **Step 2: Create the module build file**

`shared/core-data-fake/build.gradle.kts`:

```kotlin
plugins { id("anatomypro.kmp.library") }

kotlin {
    android { namespace = "com.ptk.anatomypro.core.data.fake" }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core-data"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
```

This module is depended on by feature `commonTest` source sets and by debug entry points only. It is never a dependency of `:shared` or of `:androidApp`'s release configuration (spec §5).

- [ ] **Step 3: Write the failing FakeBehaviour test**

```kotlin
package com.ptk.anatomypro.core.data.fake

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds

class FakeBehaviourTest {

    @Test
    fun the_default_behaviour_answers_immediately() = runTest {
        assertEquals("ok", FakeBehaviour().respond { "ok" })
    }

    @Test
    fun an_injected_delay_is_awaited_so_a_loading_state_can_be_seen() = runTest {
        val behaviour = FakeBehaviour(delay = 500.milliseconds)

        val before = currentTime
        behaviour.respond { "ok" }

        assertEquals(500, currentTime - before)
    }

    @Test
    fun an_injected_failure_is_thrown_so_error_states_are_designable() = runTest {
        val behaviour = FakeBehaviour(failure = { IllegalStateException("offline") })

        assertFailsWith<IllegalStateException> { behaviour.respond { "ok" } }
    }
}
```

- [ ] **Step 4: Run it and watch it fail**

Run: `./gradlew :shared:core-data-fake:allTests`
Expected: FAIL — `FakeBehaviour` is unresolved.

- [ ] **Step 5: Write FakeBehaviour.kt**

```kotlin
package com.ptk.anatomypro.core.data.fake

import kotlinx.coroutines.delay
import kotlin.time.Duration

/**
 * What a fake does before it answers.
 *
 * Loading states, empty states and §14's error rows stop being theoretical: screen 15 can
 * be shown offline and screen 03 can fail mid-download, without a backend to break.
 *
 * [failure] is a factory rather than a Throwable so each call gets its own stack.
 */
data class FakeBehaviour(
    val delay: Duration = Duration.ZERO,
    val failure: (() -> Throwable)? = null,
) {
    suspend fun <T> respond(block: () -> T): T {
        if (delay > Duration.ZERO) delay(delay)
        failure?.let { throw it() }
        return block()
    }
}
```

- [ ] **Step 6: Write the failing fixture test**

The fixture is the shared trilingual atlas every fake reads (spec §5). Its invariants are what keep screen 10's four options and screen 12's "you said *Costa VII*" readable.

```kotlin
package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.VerificationState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FixtureTest {

    @Test
    fun every_structure_is_named_in_all_three_languages() {
        val missing = AtlasFixture.all.filter { structure ->
            setOf("la", "pl", "en").any { it !in structure.names }
        }

        assertEquals(emptyList(), missing, "structures missing a name: ${missing.map { it.id.value }}")
    }

    @Test
    fun the_ribs_are_a_real_sibling_set_so_the_hard_tier_has_distractors() {
        assertEquals(12, AtlasFixture.childrenOf(StructureId("costae")).size)
    }

    @Test
    fun the_cervical_vertebrae_are_a_second_sibling_set() {
        assertEquals(7, AtlasFixture.childrenOf(StructureId("vertebrae-cervicales")).size)
    }

    @Test
    fun at_least_one_structure_is_unverified_so_the_quiz_gate_is_exercised() {
        val unverified = AtlasFixture.all.filter {
            it.verification["la"] != VerificationState.VERIFIED
        }

        assertTrue(unverified.isNotEmpty(), "the fixture must contain an unverified structure")
    }

    @Test
    fun a_detail_carries_its_ancestors_root_first() {
        val detail = AtlasFixture.detail(StructureId("costa-vii"), "pl")

        assertEquals(listOf("skeletal", "costae"), detail?.ancestors?.map { it.id.value })
    }
}
```

- [ ] **Step 7: Run it and watch it fail**

Run: `./gradlew :shared:core-data-fake:allTests`
Expected: FAIL — `AtlasFixture` is unresolved.

- [ ] **Step 8: Write Fixture.kt**

```kotlin
package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.model.StructureDetail
import com.ptk.anatomypro.core.data.model.StructureSummary
import com.ptk.anatomypro.core.model.Laterality
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.VerificationState

/**
 * One structure in the fake atlas.
 *
 * Verification is tracked per locale, matching VerificationState's own rule: a Latin name
 * may be correct while its Polish translation is wrong.
 */
data class FixtureStructure(
    val id: StructureId,
    val parent: StructureId?,
    val names: Map<String, String>,
    val definition: String?,
    val isGroup: Boolean,
    val laterality: Laterality = Laterality.MEDIAN,
    val verification: Map<String, VerificationState> =
        mapOf("la" to VerificationState.VERIFIED, "pl" to VerificationState.VERIFIED, "en" to VerificationState.VERIFIED),
)

private fun roman(index: Int): String =
    listOf("i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x", "xi", "xii")[index - 1]

private fun romanUpper(index: Int): String = roman(index).uppercase()

/**
 * The shared fake atlas: one system, two group nodes, and two real sibling sets.
 *
 * Ribs and cervical vertebrae are here because §8.1's hard tier draws distractors from
 * siblings under the same parent, and a fixture of unrelated structures would let a
 * generator look correct while producing questions no student would find hard.
 */
object AtlasFixture {

    private val skeletal = FixtureStructure(
        id = StructureId("skeletal"),
        parent = null,
        names = mapOf("la" to "Systema skeletale", "pl" to "Układ kostny", "en" to "Skeletal system"),
        definition = "The bones of the body, and the joints between them.",
        isGroup = true,
    )

    private val costae = FixtureStructure(
        id = StructureId("costae"),
        parent = StructureId("skeletal"),
        names = mapOf("la" to "Costae", "pl" to "Żebra", "en" to "Ribs"),
        definition = "Twelve paired bones forming the thoracic cage.",
        isGroup = true,
    )

    private val cervicales = FixtureStructure(
        id = StructureId("vertebrae-cervicales"),
        parent = StructureId("skeletal"),
        names = mapOf("la" to "Vertebrae cervicales", "pl" to "Kręgi szyjne", "en" to "Cervical vertebrae"),
        definition = "The seven vertebrae of the neck.",
        isGroup = true,
    )

    private val ribs: List<FixtureStructure> = (1..12).map { index ->
        FixtureStructure(
            id = StructureId("costa-${roman(index)}"),
            parent = costae.id,
            names = mapOf(
                "la" to "Costa ${romanUpper(index)}",
                "pl" to "Żebro ${romanUpper(index)}",
                "en" to "Rib ${romanUpper(index)}",
            ),
            definition = "Rib ${romanUpper(index)}.",
            isGroup = false,
            laterality = Laterality.MEDIAN,
            // Costa XII is deliberately unverified: §7 forbids it as a quiz answer, and a
            // fixture with nothing unverified would let that rule pass untested.
            verification = if (index == 12) {
                mapOf("la" to VerificationState.UNVERIFIED, "pl" to VerificationState.UNVERIFIED, "en" to VerificationState.UNVERIFIED)
            } else {
                mapOf("la" to VerificationState.VERIFIED, "pl" to VerificationState.VERIFIED, "en" to VerificationState.VERIFIED)
            },
        )
    }

    private val cervicalVertebrae: List<FixtureStructure> = (1..7).map { index ->
        FixtureStructure(
            id = StructureId("vertebra-cervicalis-${roman(index)}"),
            parent = cervicales.id,
            names = mapOf(
                "la" to "Vertebra cervicalis ${romanUpper(index)}",
                "pl" to "Kręg szyjny ${romanUpper(index)}",
                "en" to "Cervical vertebra ${romanUpper(index)}",
            ),
            definition = "Cervical vertebra ${romanUpper(index)}.",
            isGroup = false,
        )
    }

    val all: List<FixtureStructure> =
        listOf(skeletal, costae, cervicales) + ribs + cervicalVertebrae

    private val index: Map<StructureId, FixtureStructure> = all.associateBy { it.id }

    fun byId(id: StructureId): FixtureStructure? = index[id]

    fun childrenOf(parent: StructureId): List<FixtureStructure> = all.filter { it.parent == parent }

    fun roots(): List<FixtureStructure> = all.filter { it.parent == null }

    /** Latin is the canonical key, so it is the fallback when a display name is missing. */
    fun nameOf(structure: FixtureStructure, locale: String): String =
        structure.names[locale] ?: structure.names.getValue("la")

    fun summary(id: StructureId, locale: String): StructureSummary? =
        byId(id)?.let { toSummary(it, locale) }

    fun toSummary(structure: FixtureStructure, locale: String) = StructureSummary(
        id = structure.id,
        name = nameOf(structure, locale),
        latinName = structure.names["la"],
        laterality = structure.laterality,
        isGroup = structure.isGroup,
        hasChildren = childrenOf(structure.id).isNotEmpty(),
    )

    /** [StructureDetail.ancestors] is root-first, which is the order the hierarchy displays in. */
    fun ancestorsOf(id: StructureId, locale: String): List<StructureSummary> {
        val chain = mutableListOf<FixtureStructure>()
        var parent = byId(id)?.parent
        while (parent != null) {
            val node = byId(parent) ?: break
            chain += node
            parent = node.parent
        }
        return chain.reversed().map { toSummary(it, locale) }
    }

    fun detail(id: StructureId, locale: String): StructureDetail? {
        val structure = byId(id) ?: return null
        return StructureDetail(
            id = structure.id,
            names = structure.names,
            definition = structure.definition,
            definitionLocale = structure.definition?.let { "en" },
            systemId = "skeletal",
            regionId = null,
            laterality = structure.laterality,
            isGroup = structure.isGroup,
            ancestors = ancestorsOf(id, locale),
        )
    }
}
```

- [ ] **Step 9: Run both test classes and watch them pass**

Run: `./gradlew :shared:core-data-fake:allTests`
Expected: PASS on both targets.

- [ ] **Step 10: Commit**

```bash
git add settings.gradle.kts shared/core-data-fake
git commit -m "feat: add the fake data module, its fixture and injectable behaviour"
```

---

### Task 9: FakeAtlasRepository and FakeSettingsRepository, replacing four hand-rolled stubs

Four stubs exist today: `FakeAtlasRepository` and an anonymous `object : AtlasRepository` in `AtlasViewModelTest.kt`, `RecordingRepository` in `SearchViewModelTest.kt`, and `InMemorySettings` in `SettingsViewModelTest.kt`. All four are replaced by two shared fakes.

**Files:**
- Create: `shared/core-data-fake/src/commonMain/kotlin/com/ptk/anatomypro/core/data/fake/FakeAtlasRepository.kt`
- Create: `shared/core-data-fake/src/commonMain/kotlin/com/ptk/anatomypro/core/data/fake/FakeSettingsRepository.kt`
- Create: `shared/core-data-fake/src/commonTest/kotlin/com/ptk/anatomypro/core/data/fake/FakeAtlasRepositoryTest.kt`
- Modify: `shared/feature-atlas/build.gradle.kts`, `shared/feature-search/build.gradle.kts`, `shared/feature-settings/build.gradle.kts`
- Modify: `shared/feature-atlas/src/commonTest/kotlin/com/ptk/anatomypro/feature/atlas/AtlasViewModelTest.kt`
- Modify: `shared/feature-search/src/commonTest/kotlin/com/ptk/anatomypro/feature/search/SearchViewModelTest.kt`
- Modify: `shared/feature-settings/src/commonTest/kotlin/com/ptk/anatomypro/feature/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `AtlasFixture`, `FakeBehaviour` (Task 8).
- Produces: `FakeAtlasRepository(behaviour, fixture)` with a public `queries: List<String>`, and `FakeSettingsRepository(initial)` — consumed by every feature test in plans 2–6 and by Task 13.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.model.StructureId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FakeAtlasRepositoryTest {

    private val repository = FakeAtlasRepository()

    @Test
    fun roots_are_the_structures_with_no_parent() = runTest {
        assertEquals(listOf("skeletal"), repository.roots("pl").map { it.id.value })
    }

    @Test
    fun a_name_comes_back_in_the_requested_locale() = runTest {
        assertEquals("Żebra", repository.summary(StructureId("costae"), "pl")?.name)
        assertEquals("Ribs", repository.summary(StructureId("costae"), "en")?.name)
    }

    @Test
    fun an_unknown_locale_falls_back_to_latin_because_latin_is_the_canonical_key() = runTest {
        assertEquals("Costae", repository.summary(StructureId("costae"), "de")?.name)
    }

    @Test
    fun search_matches_every_language_at_once_and_reports_which_one_hit() = runTest {
        val polish = repository.search("Żebro VII")
        val english = repository.search("Rib VII")

        assertEquals("pl", polish.single().matchedLocale)
        assertEquals("en", english.single().matchedLocale)
    }

    @Test
    fun search_records_what_it_was_asked_so_a_debounce_can_be_proven() = runTest {
        repository.search("costa")

        assertEquals(listOf("costa"), repository.queries)
    }

    @Test
    fun an_injected_failure_surfaces_so_error_states_can_be_driven() = runTest {
        val broken = FakeAtlasRepository(FakeBehaviour(failure = { IllegalStateException("no database") }))

        assertFailsWith<IllegalStateException> { broken.roots("pl") }
    }

    @Test
    fun a_detail_is_null_for_a_structure_that_does_not_exist() = runTest {
        assertTrue(repository.detail(StructureId("nonexistent"), "pl") == null)
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `./gradlew :shared:core-data-fake:allTests`
Expected: FAIL — `FakeAtlasRepository` is unresolved.

- [ ] **Step 3: Write FakeAtlasRepository.kt**

```kotlin
package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.model.SearchHit
import com.ptk.anatomypro.core.data.model.StructureDetail
import com.ptk.anatomypro.core.data.model.StructureSummary
import com.ptk.anatomypro.core.data.repository.AtlasRepository
import com.ptk.anatomypro.core.model.StructureId

/**
 * The atlas, in memory, over [AtlasFixture].
 *
 * [queries] replaces the recording stub each feature test used to carry: proving a
 * debounce needs to know what was asked, and every caller wanted the same list.
 */
class FakeAtlasRepository(
    private val behaviour: FakeBehaviour = FakeBehaviour(),
) : AtlasRepository {

    private val _queries = mutableListOf<String>()
    val queries: List<String> get() = _queries.toList()

    override suspend fun roots(locale: String): List<StructureSummary> = behaviour.respond {
        AtlasFixture.roots().map { AtlasFixture.toSummary(it, locale) }
    }

    override suspend fun children(parent: StructureId, locale: String): List<StructureSummary> =
        behaviour.respond {
            AtlasFixture.childrenOf(parent).map { AtlasFixture.toSummary(it, locale) }
        }

    override suspend fun summary(id: StructureId, locale: String): StructureSummary? =
        behaviour.respond { AtlasFixture.summary(id, locale) }

    override suspend fun detail(id: StructureId, locale: String): StructureDetail? =
        behaviour.respond { AtlasFixture.detail(id, locale) }

    /** Searches every language at once and badges the match, as screen 06 does. */
    override suspend fun search(query: String, limit: Int): List<SearchHit> {
        _queries += query
        return behaviour.respond {
            AtlasFixture.all.mapNotNull { structure ->
                val matched = structure.names.entries
                    .firstOrNull { (_, name) -> name.contains(query, ignoreCase = true) }
                    ?: return@mapNotNull null
                SearchHit(AtlasFixture.toSummary(structure, matched.key), matched.key)
            }.take(limit)
        }
    }
}
```

- [ ] **Step 4: Write FakeSettingsRepository.kt**

```kotlin
package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.model.AppSettings
import com.ptk.anatomypro.core.data.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {

    private val _settings = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = _settings.asStateFlow()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        _settings.value = transform(_settings.value)
    }
}
```

- [ ] **Step 5: Run the fake module's tests and watch them pass**

Run: `./gradlew :shared:core-data-fake:allTests`
Expected: PASS.

- [ ] **Step 6: Let the feature tests see the fakes**

In each of `shared/feature-atlas/build.gradle.kts`, `shared/feature-search/build.gradle.kts` and `shared/feature-settings/build.gradle.kts`, add to `commonTest.dependencies`:

```kotlin
implementation(project(":shared:core-data-fake"))
```

- [ ] **Step 7: Delete the four stubs and rewire their tests**

In `AtlasViewModelTest.kt`: delete the private `FakeAtlasRepository` class and the anonymous `object : AtlasRepository` used for the failure case. Import `com.ptk.anatomypro.core.data.fake.FakeAtlasRepository` and `com.ptk.anatomypro.core.data.fake.FakeBehaviour`. The failure case becomes:

```kotlin
val broken = FakeAtlasRepository(FakeBehaviour(failure = { IllegalStateException("no database") }))
```

In `SearchViewModelTest.kt`: delete `RecordingRepository` and the private `hit(...)` helper. Import the shared fake. `repository.queries` keeps the same name, so the debounce assertions are unchanged. Assertions that named the old hand-made hits must now name fixture structures — `costa-vii` and `Costa VII` are present.

In `SettingsViewModelTest.kt`: delete `InMemorySettings` and import `FakeSettingsRepository`; the constructor signature matches, so call sites are unchanged.

- [ ] **Step 8: Run every affected module and watch them pass**

Run: `./gradlew :shared:feature-atlas:allTests :shared:feature-search:allTests :shared:feature-settings:allTests`
Expected: PASS. If a search assertion fails on a name that no longer exists, change the assertion to a fixture structure — do not add the old ad-hoc structure back into the fixture.

- [ ] **Step 9: Commit**

```bash
git add shared/core-data-fake shared/feature-atlas shared/feature-search shared/feature-settings
git commit -m "refactor: one shared atlas fake in place of four hand-rolled stubs"
```

---

### Task 10: FakeQuizRepository and the §7 verification gate

**Files:**
- Create: `shared/core-data-fake/src/commonMain/kotlin/com/ptk/anatomypro/core/data/fake/FakeQuizRepository.kt`
- Create: `shared/core-data-fake/src/commonTest/kotlin/com/ptk/anatomypro/core/data/fake/FakeQuizRepositoryTest.kt`

**Interfaces:**
- Consumes: `QuizRepository` (Task 6), quiz models (Task 4), `AtlasFixture`, `FakeBehaviour` (Task 8).
- Produces: `FakeQuizRepository(behaviour)` — consumed by Task 13 and all of plan 4.

- [ ] **Step 1: Write the failing test**

The gate is the point of this task. §7 forbids an `UNVERIFIED` structure as a quiz answer, and the fixture's `costa-xii` exists precisely so the rule can fail loudly if it is ever dropped.

```kotlin
package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.model.QuizAnswer
import com.ptk.anatomypro.core.data.model.QuizFormat
import com.ptk.anatomypro.core.data.model.QuizQuestion
import com.ptk.anatomypro.core.model.QuizTopicId
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.VerificationState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeQuizRepositoryTest {

    private val repository = FakeQuizRepository()
    private val ribs = QuizTopicId("costae")

    private suspend fun session(count: Int = 4) =
        repository.startSession(ribs, QuizFormat.NAME_THE_HIGHLIGHTED, count, seed = 1L)

    @Test
    fun a_topic_grid_is_offered_with_mastery_as_a_fraction() = runTest {
        val topics = repository.topics("pl")

        assertTrue(topics.isNotEmpty())
        assertTrue(topics.all { it.mastery in 0f..1f })
    }

    @Test
    fun the_same_seed_produces_the_same_questions_so_a_bad_set_is_reproducible() = runTest {
        val first = session().questions.map { it.id.value }
        val second = session().questions.map { it.id.value }

        assertEquals(first, second)
    }

    @Test
    fun no_unverified_structure_is_ever_the_expected_answer() = runTest {
        val unverified = AtlasFixture.all
            .filter { it.verification["la"] != VerificationState.VERIFIED }
            .map { it.id }
            .toSet()

        val expected = session(count = 12).questions.map { question ->
            when (question) {
                is QuizQuestion.NameTheHighlighted -> question.correct.id
                is QuizQuestion.TapTheStructure -> question.target
            }
        }

        assertTrue(unverified.isNotEmpty(), "the fixture must contain an unverified structure")
        assertTrue(expected.none { it in unverified }, "an unverified structure was used as an answer")
    }

    @Test
    fun a_right_answer_scores_correct() = runTest {
        val session = session()
        val question = session.questions.first() as QuizQuestion.NameTheHighlighted

        val result = repository.submit(
            session.id,
            QuizAnswer(question.id, question.correct.id, elapsedMillis = 1_200),
        )

        assertTrue(result.correct)
        assertEquals(question.correct.id, result.expected.id)
    }

    @Test
    fun a_wrong_answer_carries_both_structures_and_where_they_diverge() = runTest {
        val session = session()
        val question = session.questions.first() as QuizQuestion.NameTheHighlighted
        val wrong = question.options.first { it.id != question.correct.id }

        val result = repository.submit(session.id, QuizAnswer(question.id, wrong.id, elapsedMillis = 900))

        assertFalse(result.correct)
        assertEquals(wrong.id, result.chosen?.id)
        assertEquals(StructureId("costae"), result.sharedAncestor?.id)
    }

    @Test
    fun a_timed_out_question_has_no_chosen_structure_and_is_not_correct() = runTest {
        val session = session()
        val question = session.questions.first()

        val result = repository.submit(session.id, QuizAnswer(question.id, chosen = null, elapsedMillis = 30_000))

        assertFalse(result.correct)
        assertEquals(null, result.chosen)
    }

    @Test
    fun the_summary_counts_what_was_submitted_and_lists_the_misses_for_review() = runTest {
        val session = session(count = 2)
        val first = session.questions[0] as QuizQuestion.NameTheHighlighted
        val second = session.questions[1] as QuizQuestion.NameTheHighlighted
        val wrong = second.options.first { it.id != second.correct.id }

        repository.submit(session.id, QuizAnswer(first.id, first.correct.id, 1_000))
        repository.submit(session.id, QuizAnswer(second.id, wrong.id, 1_000))

        val summary = repository.finish(session.id)

        assertEquals(1, summary.correct)
        assertEquals(2, summary.total)
        assertEquals(listOf(second.correct.id), summary.needsReview.map { it.id })
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `./gradlew :shared:core-data-fake:allTests`
Expected: FAIL — `FakeQuizRepository` is unresolved.

- [ ] **Step 3: Write FakeQuizRepository.kt**

```kotlin
package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.model.AnswerResult
import com.ptk.anatomypro.core.data.model.Difficulty
import com.ptk.anatomypro.core.data.model.QuizAnswer
import com.ptk.anatomypro.core.data.model.QuizFormat
import com.ptk.anatomypro.core.data.model.QuizQuestion
import com.ptk.anatomypro.core.data.model.QuizSession
import com.ptk.anatomypro.core.data.model.QuizSummary
import com.ptk.anatomypro.core.data.model.QuizTopic
import com.ptk.anatomypro.core.data.model.StructureSummary
import com.ptk.anatomypro.core.data.repository.QuizRepository
import com.ptk.anatomypro.core.model.QuizQuestionId
import com.ptk.anatomypro.core.model.QuizSessionId
import com.ptk.anatomypro.core.model.QuizTopicId
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.SystemId
import com.ptk.anatomypro.core.model.VerificationState
import kotlin.random.Random

/**
 * Canned questions over [AtlasFixture].
 *
 * This is deliberately not §8.1's generator: difficulty here is a label on a question, not
 * a computed distractor distance. Building the real generator behind a screen would hide a
 * piece of work that deserves its own tests. What this fake does honour is §7's gate —
 * an UNVERIFIED structure is never an expected answer — because the screens must never
 * learn a habit the real generator cannot keep.
 */
class FakeQuizRepository(
    private val behaviour: FakeBehaviour = FakeBehaviour(),
) : QuizRepository {

    private val sessions = mutableMapOf<QuizSessionId, QuizSession>()
    private val submitted = mutableMapOf<QuizSessionId, MutableList<AnswerResult>>()
    private var nextSession = 0

    /** §7: only VERIFIED structures may be answers. Groups are not answers either. */
    private fun answerable(topic: QuizTopicId): List<FixtureStructure> =
        AtlasFixture.childrenOf(StructureId(topic.value))
            .filter { !it.isGroup && it.verification["la"] == VerificationState.VERIFIED }

    override suspend fun topics(locale: String): List<QuizTopic> = behaviour.respond {
        AtlasFixture.all.filter { it.isGroup && it.parent != null }.map { group ->
            QuizTopic(
                id = QuizTopicId(group.id.value),
                title = AtlasFixture.nameOf(group, locale),
                system = SystemId("skeletal"),
                structureCount = AtlasFixture.childrenOf(group.id).size,
                // A fixed spread so screen 08 shows untouched, partial and near-complete
                // cells without a progress store existing yet.
                mastery = when (group.id.value) {
                    "costae" -> 0.4f
                    "vertebrae-cervicales" -> 0f
                    else -> 0.8f
                },
            )
        }
    }

    override suspend fun startSession(
        topic: QuizTopicId,
        format: QuizFormat,
        questionCount: Int,
        seed: Long,
    ): QuizSession = behaviour.respond {
        val pool = answerable(topic)
        require(pool.size >= OPTION_COUNT) {
            "topic ${topic.value} has ${pool.size} verified answerable structures, needs $OPTION_COUNT"
        }

        val random = Random(seed)
        val targets = pool.shuffled(random).take(questionCount.coerceAtMost(pool.size))

        val questions = targets.mapIndexed { index, target ->
            val distractors = pool.filter { it.id != target.id }.shuffled(random).take(OPTION_COUNT - 1)
            val options = (distractors + target).shuffled(random).map { AtlasFixture.toSummary(it, "pl") }
            val id = QuizQuestionId("q-${seed}-$index")

            when (format) {
                QuizFormat.NAME_THE_HIGHLIGHTED -> QuizQuestion.NameTheHighlighted(
                    id = id,
                    difficulty = Difficulty.HARD,
                    highlighted = target.id,
                    options = options,
                    correctIndex = options.indexOfFirst { it.id == target.id },
                )
                QuizFormat.TAP_THE_STRUCTURE -> QuizQuestion.TapTheStructure(
                    id = id,
                    difficulty = Difficulty.HARD,
                    prompt = AtlasFixture.nameOf(target, "la"),
                    promptLocale = "la",
                    target = target.id,
                )
            }
        }

        val session = QuizSession(
            id = QuizSessionId("session-${nextSession++}"),
            topic = topic,
            format = format,
            questions = questions,
            seed = seed,
        )
        sessions[session.id] = session
        submitted[session.id] = mutableListOf()
        session
    }

    override suspend fun submit(session: QuizSessionId, answer: QuizAnswer): AnswerResult =
        behaviour.respond {
            val held = requireNotNull(sessions[session]) { "unknown session ${session.value}" }
            val question = held.questions.first { it.id == answer.questionId }
            val expectedId = when (question) {
                is QuizQuestion.NameTheHighlighted -> question.correct.id
                is QuizQuestion.TapTheStructure -> question.target
            }

            val result = AnswerResult(
                questionId = answer.questionId,
                correct = answer.chosen == expectedId,
                expected = requireNotNull(AtlasFixture.summary(expectedId, "pl")),
                chosen = answer.chosen?.let { AtlasFixture.summary(it, "pl") },
                sharedAncestor = answer.chosen?.let { sharedAncestorOf(expectedId, it) },
                elapsedMillis = answer.elapsedMillis,
            )
            submitted.getValue(session) += result
            result
        }

    override suspend fun finish(session: QuizSessionId): QuizSummary = behaviour.respond {
        val results = submitted[session].orEmpty()
        QuizSummary(
            session = session,
            correct = results.count { it.correct },
            total = results.size,
            elapsedMillis = results.sumOf { it.elapsedMillis },
            // Slowest miss first: the one that cost the most time is the one to revisit.
            needsReview = results.filterNot { it.correct }
                .sortedByDescending { it.elapsedMillis }
                .map { it.expected },
        )
    }

    /** The lowest group both structures sit under — what screen 12 explains. */
    private fun sharedAncestorOf(a: StructureId, b: StructureId): StructureSummary? {
        val chainOfA = AtlasFixture.ancestorsOf(a, "pl").map { it.id }.toSet()
        return AtlasFixture.ancestorsOf(b, "pl").lastOrNull { it.id in chainOfA }
    }

    private companion object {
        /** Screen 10 offers four options (spec §8). */
        const val OPTION_COUNT = 4
    }
}
```

- [ ] **Step 4: Run the tests and watch them pass**

Run: `./gradlew :shared:core-data-fake:allTests`
Expected: PASS on both targets.

- [ ] **Step 5: Commit**

```bash
git add shared/core-data-fake
git commit -m "feat: add the quiz fake, enforcing the verification gate"
```

---

### Task 11: FakeProgressRepository and FakeDailyRepository

**Files:**
- Create: `shared/core-data-fake/src/commonMain/kotlin/com/ptk/anatomypro/core/data/fake/FakeProgressRepository.kt`
- Create: `shared/core-data-fake/src/commonMain/kotlin/com/ptk/anatomypro/core/data/fake/FakeDailyRepository.kt`
- Create: `shared/core-data-fake/src/commonTest/kotlin/com/ptk/anatomypro/core/data/fake/FakeProgressRepositoryTest.kt`
- Create: `shared/core-data-fake/src/commonTest/kotlin/com/ptk/anatomypro/core/data/fake/FakeDailyRepositoryTest.kt`

**Interfaces:**
- Consumes: `ProgressRepository`, `DailyRepository` (Task 6); Task 5's models; `FakeQuizRepository` (Task 10) for the daily's question set.
- Produces: `FakeProgressRepository(behaviour, initialStreak)` and `FakeDailyRepository(behaviour, available, alreadyAttempted)` — consumed by Task 13 and by plan 5.

- [ ] **Step 1: Write the failing progress test**

```kotlin
package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.model.QuizSummary
import com.ptk.anatomypro.core.model.QuizSessionId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeProgressRepositoryTest {

    @Test
    fun a_streak_is_offered_for_screens_14_and_17() = runTest {
        val streak = FakeProgressRepository().streak.first()

        assertTrue(streak.currentDays > 0)
        assertTrue(streak.longestDays >= streak.currentDays)
    }

    @Test
    fun mastery_is_reported_per_system_as_a_fraction() = runTest {
        val mastery = FakeProgressRepository().masteryBySystem("pl")

        assertTrue(mastery.isNotEmpty())
        assertTrue(mastery.all { it.fraction in 0f..1f })
    }

    @Test
    fun the_calendar_covers_every_day_in_the_range_including_both_ends() = runTest {
        val from = LocalDate(2026, 9, 1)
        val to = LocalDate(2026, 9, 7)

        val activity = FakeProgressRepository().activity(from, to)

        assertEquals(7, activity.size)
        assertTrue(from in activity && to in activity)
    }

    @Test
    fun recording_a_session_raises_the_questions_answered_for_that_day() = runTest {
        val repository = FakeProgressRepository()
        val before = repository.streak.first().currentDays

        repository.recordSession(
            QuizSummary(QuizSessionId("s1"), correct = 4, total = 4, elapsedMillis = 5_000, needsReview = emptyList()),
        )

        assertEquals(before, repository.streak.first().currentDays)
        assertEquals(1, repository.recorded.size)
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `./gradlew :shared:core-data-fake:allTests`
Expected: FAIL — `FakeProgressRepository` is unresolved.

- [ ] **Step 3: Write FakeProgressRepository.kt**

```kotlin
package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.model.DayActivity
import com.ptk.anatomypro.core.data.model.QuizSummary
import com.ptk.anatomypro.core.data.model.StreakState
import com.ptk.anatomypro.core.data.model.SystemMastery
import com.ptk.anatomypro.core.data.repository.ProgressRepository
import com.ptk.anatomypro.core.model.SystemId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate

/**
 * A plausible study history.
 *
 * The streak is non-zero by default because screens 14 and 17 are designed around a
 * student who has been studying — an empty state is a variant worth seeing deliberately,
 * by constructing this with [StreakState] of zero, not the thing every run shows.
 */
class FakeProgressRepository(
    private val behaviour: FakeBehaviour = FakeBehaviour(),
    initialStreak: StreakState = StreakState(
        currentDays = 12,
        longestDays = 31,
        lastStudied = LocalDate(2026, 9, 24),
    ),
) : ProgressRepository {

    private val _streak = MutableStateFlow(initialStreak)
    override val streak: Flow<StreakState> = _streak.asStateFlow()

    private val _recorded = mutableListOf<QuizSummary>()
    val recorded: List<QuizSummary> get() = _recorded.toList()

    override suspend fun masteryBySystem(locale: String): List<SystemMastery> = behaviour.respond {
        listOf(
            SystemMastery(SystemId("skeletal"), "Układ kostny", structuresSeen = 14, structuresTotal = 19),
            SystemMastery(SystemId("muscular"), "Układ mięśniowy", structuresSeen = 0, structuresTotal = 24),
        )
    }

    override suspend fun activity(from: LocalDate, to: LocalDate): Map<LocalDate, DayActivity> =
        behaviour.respond {
            require(from <= to) { "from $from is after to $to" }
            buildMap {
                var cursor = from
                while (cursor <= to) {
                    // A deterministic pattern rather than a random one, so a screenshot of
                    // screen 17 is the same screenshot tomorrow.
                    val studied = cursor.day % 3 != 0
                    put(
                        cursor,
                        DayActivity(
                            sessions = if (studied) 1 else 0,
                            questionsAnswered = if (studied) 10 else 0,
                            dailyCompleted = studied && cursor.day % 2 == 0,
                        ),
                    )
                    cursor = LocalDate.fromEpochDays(cursor.toEpochDays() + 1)
                }
            }
        }

    override suspend fun recordSession(summary: QuizSummary) {
        behaviour.respond { _recorded += summary }
    }
}
```

- [ ] **Step 4: Write the failing daily test**

```kotlin
package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.model.DailyQuiz
import com.ptk.anatomypro.core.data.model.QuizAnswer
import com.ptk.anatomypro.core.data.model.QuizQuestion
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FakeDailyRepositoryTest {

    @Test
    fun a_fresh_day_has_not_been_started() = runTest {
        assertIs<DailyQuiz.NotStarted>(FakeDailyRepository().today())
    }

    @Test
    fun starting_fetches_questions_and_records_the_clock_start() = runTest {
        val repository = FakeDailyRepository()

        val started = repository.start()

        assertTrue(started.questions.isNotEmpty())
        assertIs<DailyQuiz.InProgress>(repository.today())
    }

    @Test
    fun offline_reports_unavailable_rather_than_throwing_because_it_is_a_designed_screen() = runTest {
        assertIs<DailyQuiz.Unavailable>(FakeDailyRepository(available = false).today())
    }

    @Test
    fun starting_while_offline_fails_because_the_daily_needs_a_connection() = runTest {
        assertFailsWith<IllegalStateException> { FakeDailyRepository(available = false).start() }
    }

    @Test
    fun one_attempt_per_day_is_enforced() = runTest {
        val repository = FakeDailyRepository(alreadyAttempted = true)

        assertIs<DailyQuiz.Completed>(repository.today())
        assertFailsWith<IllegalStateException> { repository.start() }
    }

    @Test
    fun submitting_scores_server_side_and_returns_a_rank() = runTest {
        val repository = FakeDailyRepository()
        val started = repository.start()
        val answers = started.questions.map { question ->
            val chosen = when (question) {
                is QuizQuestion.NameTheHighlighted -> question.correct.id
                is QuizQuestion.TapTheStructure -> question.target
            }
            QuizAnswer(question.id, chosen, elapsedMillis = 1_000)
        }

        val result = repository.submit(answers)

        assertEquals(answers.size, result.correct)
        assertTrue((result.rank ?: 0) > 0)
        assertIs<DailyQuiz.Completed>(repository.today())
    }
}
```

- [ ] **Step 5: Run it and watch it fail**

Run: `./gradlew :shared:core-data-fake:allTests`
Expected: FAIL — `FakeDailyRepository` is unresolved.

- [ ] **Step 6: Write FakeDailyRepository.kt**

```kotlin
package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.model.DailyQuiz
import com.ptk.anatomypro.core.data.model.DailyResult
import com.ptk.anatomypro.core.data.model.QuizAnswer
import com.ptk.anatomypro.core.data.model.QuizFormat
import com.ptk.anatomypro.core.data.model.QuizQuestion
import com.ptk.anatomypro.core.data.repository.DailyRepository
import com.ptk.anatomypro.core.model.QuizTopicId
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * The daily quiz, played against a server that does not exist.
 *
 * [available] drives §14's offline row: the daily requires a connection to start and to
 * submit, and offline play is practice mode and never ranked. Keeping that as a
 * constructor flag rather than an injected exception means screen 15's offline state is
 * something you switch on, not something you have to break the app to see.
 */
class FakeDailyRepository(
    private val behaviour: FakeBehaviour = FakeBehaviour(),
    private val available: Boolean = true,
    alreadyAttempted: Boolean = false,
    private val date: LocalDate = LocalDate(2026, 9, 24),
    private val quiz: FakeQuizRepository = FakeQuizRepository(),
) : DailyRepository {

    private var state: DailyQuiz =
        if (alreadyAttempted) {
            DailyQuiz.Completed(date, DailyResult(date, correct = 7, total = 10, elapsedMillis = 64_000, rank = 412, totalPlayers = 5_180))
        } else {
            DailyQuiz.NotStarted(date, questionCount = QUESTION_COUNT)
        }

    override suspend fun today(): DailyQuiz = behaviour.respond {
        if (!available) DailyQuiz.Unavailable else state
    }

    override suspend fun start(): DailyQuiz.InProgress = behaviour.respond {
        check(available) { "the daily quiz requires a connection (§14)" }
        check(state is DailyQuiz.NotStarted) { "one attempt per day (§9.1)" }

        val session = quiz.startSession(
            topic = QuizTopicId("costae"),
            format = QuizFormat.NAME_THE_HIGHLIGHTED,
            questionCount = QUESTION_COUNT,
            // Seeded on the date: everyone gets the same set today (§9.1).
            seed = date.toEpochDays(),
        )
        val started = DailyQuiz.InProgress(date, session.questions, startedAt = CLOCK_START)
        state = started
        started
    }

    override suspend fun submit(answers: List<QuizAnswer>): DailyResult = behaviour.respond {
        check(available) { "the daily quiz requires a connection to submit (§14)" }
        val inProgress = state as? DailyQuiz.InProgress ?: error("the daily quiz has not been started")

        val correct = answers.count { answer ->
            val question = inProgress.questions.first { it.id == answer.questionId }
            val expected = when (question) {
                is QuizQuestion.NameTheHighlighted -> question.correct.id
                is QuizQuestion.TapTheStructure -> question.target
            }
            answer.chosen == expected
        }

        val result = DailyResult(
            date = date,
            correct = correct,
            total = answers.size,
            elapsedMillis = answers.sumOf { it.elapsedMillis },
            // Better scores rank higher; the arithmetic only has to be plausible.
            rank = (TOTAL_PLAYERS - correct * 400).coerceAtLeast(1),
            totalPlayers = TOTAL_PLAYERS,
        )
        state = DailyQuiz.Completed(date, result)
        result
    }

    private companion object {
        const val QUESTION_COUNT = 10
        const val TOTAL_PLAYERS = 5_180
        val CLOCK_START = Instant.fromEpochSeconds(1_790_000_000)
    }
}
```

- [ ] **Step 7: Run both test classes and watch them pass**

Run: `./gradlew :shared:core-data-fake:allTests`
Expected: PASS on both targets.

- [ ] **Step 8: Commit**

```bash
git add shared/core-data-fake
git commit -m "feat: add the progress and daily fakes"
```

---

### Task 12: FakeLeaderboardRepository, FakeEntitlementRepository, FakePackRepository

**Files:**
- Create: `shared/core-data-fake/src/commonMain/kotlin/com/ptk/anatomypro/core/data/fake/FakeLeaderboardRepository.kt`
- Create: `shared/core-data-fake/src/commonMain/kotlin/com/ptk/anatomypro/core/data/fake/FakeEntitlementRepository.kt`
- Create: `shared/core-data-fake/src/commonMain/kotlin/com/ptk/anatomypro/core/data/fake/FakePackRepository.kt`
- Create: `shared/core-data-fake/src/commonTest/kotlin/com/ptk/anatomypro/core/data/fake/FakeCommerceTest.kt`

**Interfaces:**
- Consumes: the three interfaces from Task 6; Task 5's models.
- Produces: `FakeLeaderboardRepository(behaviour, ranked)`, `FakeEntitlementRepository(behaviour, initial)`, `FakePackRepository(behaviour)` — consumed by Task 13 and by plans 5 and 6.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.model.Entitlements
import com.ptk.anatomypro.core.data.model.PackStatus
import com.ptk.anatomypro.core.data.model.PurchaseOutcome
import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.core.model.SystemId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FakeCommerceTest {

    @Test
    fun a_board_carries_the_users_own_row_so_your_position_is_not_a_second_call() = runTest {
        val board = FakeLeaderboardRepository().streaks()

        assertTrue(board.entries.isNotEmpty())
        assertEquals(true, board.me?.isMe)
    }

    @Test
    fun an_unranked_user_has_no_row_rather_than_a_row_of_zeroes() = runTest {
        val board = FakeLeaderboardRepository(ranked = false).streaks()

        assertEquals(null, board.me)
    }

    @Test
    fun the_board_is_ordered_by_rank() = runTest {
        val ranks = FakeLeaderboardRepository().streaks().entries.map { it.rank }

        assertEquals(ranks.sorted(), ranks)
    }

    @Test
    fun skeletal_is_playable_before_any_purchase() = runTest {
        val entitlements = FakeEntitlementRepository().entitlements.first()

        assertTrue(entitlements.allows(SystemId("skeletal")))
        assertTrue(!entitlements.allows(SystemId("muscular")))
    }

    @Test
    fun a_purchase_unlocks_everything_and_is_observed_by_the_flow() = runTest {
        val repository = FakeEntitlementRepository()
        val plan = repository.plans().first()

        val outcome = repository.purchase(plan)

        assertIs<PurchaseOutcome.Succeeded>(outcome)
        assertTrue(repository.entitlements.first().allows(SystemId("muscular")))
    }

    @Test
    fun a_purchase_can_be_made_to_fail_so_screen_18_has_an_error_state() = runTest {
        val repository = FakeEntitlementRepository(purchaseSucceeds = false)
        val plan = repository.plans().first()

        assertIs<PurchaseOutcome.Failed>(repository.purchase(plan))
    }

    @Test
    fun the_free_pack_is_installed_and_a_paid_one_is_merely_available() = runTest {
        val packs = FakePackRepository().packs.first()

        assertIs<PackStatus.Installed>(packs.first { it.id == PackId("skeletal-body") }.status)
        assertIs<PackStatus.Available>(packs.first { it.id == PackId("muscular-body") }.status)
    }

    @Test
    fun downloading_a_pack_installs_it_and_deleting_it_makes_it_available_again() = runTest {
        val repository = FakePackRepository()

        repository.download(PackId("muscular-body"))
        assertIs<PackStatus.Installed>(repository.packs.first().first { it.id == PackId("muscular-body") }.status)

        repository.delete(PackId("muscular-body"))
        assertIs<PackStatus.Available>(repository.packs.first().first { it.id == PackId("muscular-body") }.status)
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `./gradlew :shared:core-data-fake:allTests`
Expected: FAIL — the three fakes are unresolved.

- [ ] **Step 3: Write FakeLeaderboardRepository.kt**

```kotlin
package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.model.Leaderboard
import com.ptk.anatomypro.core.data.model.LeaderboardEntry
import com.ptk.anatomypro.core.data.repository.LeaderboardRepository
import kotlinx.datetime.LocalDate

/**
 * v1's two boards (§9.3).
 *
 * [ranked] off is the untimed case: §12 makes a disabled timer unranked, and screen 16 has
 * to show a board the user is not on.
 */
class FakeLeaderboardRepository(
    private val behaviour: FakeBehaviour = FakeBehaviour(),
    private val ranked: Boolean = true,
) : LeaderboardRepository {

    private val nicknames = listOf(
        "anatomia_ninja", "costa_vii", "m_sternocleido", "foramen", "vesalius_1543",
        "kostny", "scapula", "os_hyoideum", "pterygoid", "trochlea",
    )

    private fun board(myRank: Int, scoreOf: (Int) -> Int): Leaderboard {
        val entries = nicknames.mapIndexed { index, nickname ->
            LeaderboardEntry(
                rank = index + 1,
                nickname = nickname,
                score = scoreOf(index),
                elapsedMillis = 40_000L + index * 1_500L,
                isMe = false,
            )
        }
        val me = if (!ranked) null else LeaderboardEntry(
            rank = myRank,
            nickname = "ty",
            score = scoreOf(myRank - 1),
            elapsedMillis = 64_000,
            isMe = true,
        )
        return Leaderboard(entries = entries, me = me, totalPlayers = 5_180)
    }

    override suspend fun daily(date: LocalDate): Leaderboard =
        behaviour.respond { board(myRank = 412) { index -> 10 - index / 4 } }

    override suspend fun streaks(): Leaderboard =
        behaviour.respond { board(myRank = 88) { index -> 120 - index * 7 } }
}
```

- [ ] **Step 4: Write FakeEntitlementRepository.kt**

```kotlin
package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.model.Entitlements
import com.ptk.anatomypro.core.data.model.PurchaseOutcome
import com.ptk.anatomypro.core.data.model.SubscriptionPlan
import com.ptk.anatomypro.core.data.repository.EntitlementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Screen 18. Skeletal is free forever, so the default state is already playable. */
class FakeEntitlementRepository(
    private val behaviour: FakeBehaviour = FakeBehaviour(),
    initial: Entitlements = Entitlements(subscribed = false, ownedSystems = emptySet()),
    private val purchaseSucceeds: Boolean = true,
) : EntitlementRepository {

    private val _entitlements = MutableStateFlow(initial)
    override val entitlements: Flow<Entitlements> = _entitlements.asStateFlow()

    override suspend fun plans(): List<SubscriptionPlan> = behaviour.respond {
        listOf(
            SubscriptionPlan("monthly", "Miesięcznie", "29 zł", periodMonths = 1),
            SubscriptionPlan("yearly", "Rocznie", "199 zł", periodMonths = 12),
        )
    }

    override suspend fun purchase(plan: SubscriptionPlan): PurchaseOutcome = behaviour.respond {
        if (!purchaseSucceeds) {
            PurchaseOutcome.Failed("Płatność odrzucona")
        } else {
            _entitlements.value = _entitlements.value.copy(subscribed = true)
            PurchaseOutcome.Succeeded(_entitlements.value)
        }
    }

    override suspend fun restore(): PurchaseOutcome = behaviour.respond {
        PurchaseOutcome.Succeeded(_entitlements.value)
    }
}
```

- [ ] **Step 5: Write FakePackRepository.kt**

```kotlin
package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.model.PackState
import com.ptk.anatomypro.core.data.model.PackStatus
import com.ptk.anatomypro.core.data.repository.PackRepository
import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.core.model.SystemId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Screens 03 and 19.
 *
 * [download] steps through progress before settling, so screen 03's progress bar has
 * something to draw. The steps are emissions rather than sleeps: a test should not have to
 * wait out a fake download, and a FakeBehaviour delay is how you make it slow on purpose.
 */
class FakePackRepository(
    private val behaviour: FakeBehaviour = FakeBehaviour(),
) : PackRepository {

    private val _packs = MutableStateFlow(
        listOf(
            PackState(
                id = PackId("skeletal-body"),
                version = 1,
                label = "Układ kostny",
                byteSize = 48_300_000,
                checksum = "sha256:2f1c0a",
                entitlement = null,
                status = PackStatus.Installed,
            ),
            PackState(
                id = PackId("muscular-body"),
                version = 1,
                label = "Układ mięśniowy",
                byteSize = 112_700_000,
                checksum = "sha256:9b4e17",
                entitlement = SystemId("muscular"),
                status = PackStatus.Available,
            ),
            PackState(
                id = PackId("nervous-body"),
                version = 1,
                label = "Układ nerwowy",
                byteSize = 67_400_000,
                checksum = "sha256:c30d82",
                entitlement = SystemId("nervous"),
                status = PackStatus.Available,
            ),
        ),
    )

    override val packs: Flow<List<PackState>> = _packs.asStateFlow()

    private fun setStatus(id: PackId, status: PackStatus) {
        _packs.value = _packs.value.map { if (it.id == id) it.copy(status = status) else it }
    }

    override suspend fun download(id: PackId) {
        behaviour.respond {
            val pack = _packs.value.first { it.id == id }
            for (step in 1..DOWNLOAD_STEPS) {
                setStatus(id, PackStatus.Downloading(pack.byteSize * step / DOWNLOAD_STEPS, pack.byteSize))
            }
            setStatus(id, PackStatus.Installed)
        }
    }

    override suspend fun cancel(id: PackId) {
        behaviour.respond { setStatus(id, PackStatus.Available) }
    }

    override suspend fun delete(id: PackId) {
        behaviour.respond { setStatus(id, PackStatus.Available) }
    }

    private companion object {
        const val DOWNLOAD_STEPS = 4
    }
}
```

- [ ] **Step 6: Run the tests and watch them pass**

Run: `./gradlew :shared:core-data-fake:allTests`
Expected: PASS on both targets.

- [ ] **Step 7: Commit**

```bash
git add shared/core-data-fake
git commit -m "feat: add the leaderboard, entitlement and pack fakes"
```

---


### Task 13: AppDependencies, and a production path that cannot construct a fake

The seam. The protection here is **not** that the fake bytes stay out of the binary — on iOS there is no debug/release source split, so they cannot be kept out. The protection is that the production code path never constructs a fake: `rememberAppDependencies()` supplies `NotImplemented*` for the six repositories that have no real implementation, so a release build that reaches one fails loudly instead of showing a fabricated leaderboard.

`AppDependencies` therefore lives in `:shared:core-data`, not in `:shared` — `:shared:core-data-fake` must be able to build one, and it cannot depend on `:shared` without a cycle.

**Files:**
- Create: `shared/core-data/src/commonMain/kotlin/com/ptk/anatomypro/core/data/AppDependencies.kt`
- Create: `shared/core-data/src/commonMain/kotlin/com/ptk/anatomypro/core/data/NotBuiltRepositories.kt`
- Create: `shared/core-data/src/commonTest/kotlin/com/ptk/anatomypro/core/data/NotBuiltRepositoriesTest.kt`
- Create: `shared/core-data-fake/src/commonMain/kotlin/com/ptk/anatomypro/core/data/fake/FakeAppDependencies.kt`
- Modify: `shared/src/commonMain/kotlin/com/ptk/anatomypro/Atlas.kt`
- Modify: `shared/src/commonMain/kotlin/com/ptk/anatomypro/App.kt`
- Modify: `androidApp/src/main/kotlin/com/ptk/anatomypro/MainActivity.kt`
- Create: `androidApp/src/debug/kotlin/com/ptk/anatomypro/DebugEntryPoint.kt`
- Modify: `androidApp/build.gradle.kts`
- Modify: `shared/src/iosMain/kotlin/com/ptk/anatomypro/MainViewController.kt`

**Interfaces:**
- Consumes: all six interfaces (Task 6), `AtlasRepository`, `SettingsRepository`, every fake (Tasks 9–12), `ProvideAppLocale` (Task 2).
- Produces: `AppDependencies`, `notBuiltAppDependencies(atlas, settings)`, `fakeAppDependencies()`, and `App(dependencies: AppDependencies)` — the entry point every screen in plans 2–6 reaches its repositories through.

- [ ] **Step 1: Write AppDependencies.kt**

```kotlin
package com.ptk.anatomypro.core.data

import com.ptk.anatomypro.core.data.repository.AtlasRepository
import com.ptk.anatomypro.core.data.repository.DailyRepository
import com.ptk.anatomypro.core.data.repository.EntitlementRepository
import com.ptk.anatomypro.core.data.repository.LeaderboardRepository
import com.ptk.anatomypro.core.data.repository.PackRepository
import com.ptk.anatomypro.core.data.repository.ProgressRepository
import com.ptk.anatomypro.core.data.repository.QuizRepository
import com.ptk.anatomypro.core.data.repository.SettingsRepository

/**
 * Everything the app reads, in one place.
 *
 * App() used to construct its own settings repository, which made the choice of
 * implementation an internal decision of a composable. Passing the set in is what lets a
 * debug entry point supply fakes while production supplies Room — and it is the same swap
 * Phase 3 performs when Ktor implementations arrive (spec §6).
 *
 * This lives in core-data rather than in :shared so that :shared:core-data-fake can build
 * one without depending on :shared, which would be a cycle.
 *
 * [atlas] is nullable because the bundled pack installs asynchronously: null means "still
 * opening", which is a different thing to show than an empty atlas.
 */
data class AppDependencies(
    val atlas: AtlasRepository?,
    val settings: SettingsRepository,
    val quiz: QuizRepository,
    val progress: ProgressRepository,
    val daily: DailyRepository,
    val leaderboard: LeaderboardRepository,
    val entitlements: EntitlementRepository,
    val packs: PackRepository,
)
```

- [ ] **Step 2: Write the failing test for the not-built six**

This test uses local stand-ins, **not** the fakes from `:shared:core-data-fake`. That module depends on `core-data`, so importing it here — at any scope, test included — is a project dependency cycle, which Gradle rejects.

```kotlin
package com.ptk.anatomypro.core.data

import com.ptk.anatomypro.core.data.model.AppSettings
import com.ptk.anatomypro.core.data.model.SearchHit
import com.ptk.anatomypro.core.data.model.StructureDetail
import com.ptk.anatomypro.core.data.model.StructureSummary
import com.ptk.anatomypro.core.data.repository.AtlasRepository
import com.ptk.anatomypro.core.data.repository.SettingsRepository
import com.ptk.anatomypro.core.model.StructureId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Empty, but real: the point is that it answers rather than throws. */
private class StandInAtlasRepository : AtlasRepository {
    override suspend fun roots(locale: String) = emptyList<StructureSummary>()
    override suspend fun children(parent: StructureId, locale: String) = emptyList<StructureSummary>()
    override suspend fun summary(id: StructureId, locale: String): StructureSummary? = null
    override suspend fun detail(id: StructureId, locale: String): StructureDetail? = null
    override suspend fun search(query: String, limit: Int) = emptyList<SearchHit>()
}

private class StandInSettingsRepository : SettingsRepository {
    private val state = MutableStateFlow(AppSettings())
    override val settings: Flow<AppSettings> = state.asStateFlow()
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}

/**
 * The production path must not be able to serve invented data.
 *
 * A fabricated leaderboard reaching a real user is worse than a crash, so the six
 * repositories Phase 3 has not built yet throw rather than answer.
 */
class NotBuiltRepositoriesTest {

    @Test
    fun the_quiz_is_not_built() = runTest {
        assertFailsWith<NotImplementedError> { NotBuiltQuizRepository.topics("pl") }
        assertFailsWith<NotImplementedError> { NotBuiltQuizRepository.finish(SESSION) }
    }

    @Test
    fun progress_is_not_built() = runTest {
        assertFailsWith<NotImplementedError> { NotBuiltProgressRepository.masteryBySystem("pl") }
        assertFailsWith<NotImplementedError> { NotBuiltProgressRepository.streak.first() }
    }

    @Test
    fun the_daily_quiz_is_not_built() = runTest {
        assertFailsWith<NotImplementedError> { NotBuiltDailyRepository.today() }
    }

    @Test
    fun the_leaderboard_is_not_built() = runTest {
        assertFailsWith<NotImplementedError> { NotBuiltLeaderboardRepository.streaks() }
    }

    @Test
    fun entitlements_are_not_built() = runTest {
        assertFailsWith<NotImplementedError> { NotBuiltEntitlementRepository.plans() }
    }

    @Test
    fun packs_are_not_built() = runTest {
        assertFailsWith<NotImplementedError> { NotBuiltPackRepository.packs.first() }
    }

    @Test
    fun the_two_repositories_that_do_exist_are_carried_through_untouched() = runTest {
        val atlas = StandInAtlasRepository()
        val settings = StandInSettingsRepository()

        val dependencies = notBuiltAppDependencies(atlas, settings)

        assertFailsWith<NotImplementedError> { dependencies.quiz.topics("pl") }
        // The atlas is the real one, so it answers.
        dependencies.atlas!!.roots("pl")
    }

    private companion object {
        val SESSION = com.ptk.anatomypro.core.model.QuizSessionId("session-0")
    }
}
```

Add no new dependency to `core-data` for this — the stand-ins are why none is needed.

- [ ] **Step 3: Run it and watch it fail**

Run: `./gradlew :shared:core-data:allTests`
Expected: FAIL — the `NotBuilt*` objects are unresolved.

- [ ] **Step 4: Write NotBuiltRepositories.kt**

```kotlin
package com.ptk.anatomypro.core.data

import com.ptk.anatomypro.core.data.model.AnswerResult
import com.ptk.anatomypro.core.data.model.DailyQuiz
import com.ptk.anatomypro.core.data.model.DailyResult
import com.ptk.anatomypro.core.data.model.DayActivity
import com.ptk.anatomypro.core.data.model.Entitlements
import com.ptk.anatomypro.core.data.model.Leaderboard
import com.ptk.anatomypro.core.data.model.PackState
import com.ptk.anatomypro.core.data.model.PurchaseOutcome
import com.ptk.anatomypro.core.data.model.QuizAnswer
import com.ptk.anatomypro.core.data.model.QuizFormat
import com.ptk.anatomypro.core.data.model.QuizSession
import com.ptk.anatomypro.core.data.model.QuizSummary
import com.ptk.anatomypro.core.data.model.QuizTopic
import com.ptk.anatomypro.core.data.model.StreakState
import com.ptk.anatomypro.core.data.model.SubscriptionPlan
import com.ptk.anatomypro.core.data.model.SystemMastery
import com.ptk.anatomypro.core.data.repository.AtlasRepository
import com.ptk.anatomypro.core.data.repository.DailyRepository
import com.ptk.anatomypro.core.data.repository.EntitlementRepository
import com.ptk.anatomypro.core.data.repository.LeaderboardRepository
import com.ptk.anatomypro.core.data.repository.PackRepository
import com.ptk.anatomypro.core.data.repository.ProgressRepository
import com.ptk.anatomypro.core.data.repository.QuizRepository
import com.ptk.anatomypro.core.data.repository.SettingsRepository
import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.core.model.QuizSessionId
import com.ptk.anatomypro.core.model.QuizTopicId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDate

private fun notBuilt(what: String): Nothing =
    TODO("$what has no implementation yet — Phase 3 (§11) and §8.1 are what fill it")

object NotBuiltQuizRepository : QuizRepository {
    override suspend fun topics(locale: String): List<QuizTopic> = notBuilt("the quiz")
    override suspend fun startSession(topic: QuizTopicId, format: QuizFormat, questionCount: Int, seed: Long): QuizSession = notBuilt("the quiz")
    override suspend fun submit(session: QuizSessionId, answer: QuizAnswer): AnswerResult = notBuilt("the quiz")
    override suspend fun finish(session: QuizSessionId): QuizSummary = notBuilt("the quiz")
}

object NotBuiltProgressRepository : ProgressRepository {
    override val streak: Flow<StreakState> = flow { notBuilt("progress") }
    override suspend fun masteryBySystem(locale: String): List<SystemMastery> = notBuilt("progress")
    override suspend fun activity(from: LocalDate, to: LocalDate): Map<LocalDate, DayActivity> = notBuilt("progress")
    override suspend fun recordSession(summary: QuizSummary) = notBuilt("progress")
}

object NotBuiltDailyRepository : DailyRepository {
    override suspend fun today(): DailyQuiz = notBuilt("the daily quiz")
    override suspend fun start(): DailyQuiz.InProgress = notBuilt("the daily quiz")
    override suspend fun submit(answers: List<QuizAnswer>): DailyResult = notBuilt("the daily quiz")
}

object NotBuiltLeaderboardRepository : LeaderboardRepository {
    override suspend fun daily(date: LocalDate): Leaderboard = notBuilt("the leaderboard")
    override suspend fun streaks(): Leaderboard = notBuilt("the leaderboard")
}

object NotBuiltEntitlementRepository : EntitlementRepository {
    override val entitlements: Flow<Entitlements> = flow { notBuilt("entitlements") }
    override suspend fun plans(): List<SubscriptionPlan> = notBuilt("entitlements")
    override suspend fun purchase(plan: SubscriptionPlan): PurchaseOutcome = notBuilt("entitlements")
    override suspend fun restore(): PurchaseOutcome = notBuilt("entitlements")
}

object NotBuiltPackRepository : PackRepository {
    override val packs: Flow<List<PackState>> = flow { notBuilt("pack management") }
    override suspend fun download(id: PackId) = notBuilt("pack management")
    override suspend fun cancel(id: PackId) = notBuilt("pack management")
    override suspend fun delete(id: PackId) = notBuilt("pack management")
}

/**
 * The production set: the two repositories that exist, and six that refuse.
 *
 * Refusing is the point. A screen wired to one of these in a release build crashes with a
 * message naming what is missing, rather than presenting a fabricated leaderboard or a
 * purchase that never happened.
 */
fun notBuiltAppDependencies(
    atlas: AtlasRepository?,
    settings: SettingsRepository,
) = AppDependencies(
    atlas = atlas,
    settings = settings,
    quiz = NotBuiltQuizRepository,
    progress = NotBuiltProgressRepository,
    daily = NotBuiltDailyRepository,
    leaderboard = NotBuiltLeaderboardRepository,
    entitlements = NotBuiltEntitlementRepository,
    packs = NotBuiltPackRepository,
)
```

`TODO()` throws `NotImplementedError`, which is what the test asserts.

- [ ] **Step 5: Run the test and watch it pass**

Run: `./gradlew :shared:core-data:allTests`
Expected: PASS on both targets.

- [ ] **Step 6: Write the fake set, in the fake module**

`shared/core-data-fake/src/commonMain/kotlin/com/ptk/anatomypro/core/data/fake/FakeAppDependencies.kt`:

```kotlin
package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.AppDependencies
import com.ptk.anatomypro.core.data.model.AppSettings

/**
 * A fully fake dependency set: no database, no pack on disk, no device.
 *
 * Called only from debug entry points. Every screen can be reached from here, which is
 * what makes running a screen a default rather than an expedition.
 */
fun fakeAppDependencies(): AppDependencies {
    val quiz = FakeQuizRepository()
    return AppDependencies(
        atlas = FakeAtlasRepository(),
        settings = FakeSettingsRepository(AppSettings(onboarded = true)),
        quiz = quiz,
        progress = FakeProgressRepository(),
        daily = FakeDailyRepository(quiz = quiz),
        leaderboard = FakeLeaderboardRepository(),
        entitlements = FakeEntitlementRepository(),
        packs = FakePackRepository(),
    )
}
```

- [ ] **Step 7: Add the production factory to Atlas.kt**

`:shared` depends on `:shared:core-data` but **not** on `:shared:core-data-fake`. Do not add that dependency.

At the bottom of `shared/src/commonMain/kotlin/com/ptk/anatomypro/Atlas.kt`:

```kotlin
/**
 * The production set.
 *
 * Six of the eight repositories refuse rather than answer; see notBuiltAppDependencies.
 * Replacing one when Phase 3 builds it is a one-line change here and nothing else.
 */
@Composable
fun rememberAppDependencies(): AppDependencies {
    val atlas = rememberAtlas()
    val settings = rememberSettingsRepository()
    return remember(atlas, settings) {
        notBuiltAppDependencies(atlas = atlas?.repository, settings = settings)
    }
}
```

with imports for `com.ptk.anatomypro.core.data.AppDependencies` and `com.ptk.anatomypro.core.data.notBuiltAppDependencies`.

- [ ] **Step 8: Change App()'s signature**

In `App.kt`, replace the first lines of `App()`:

```kotlin
@Composable
fun App(dependencies: AppDependencies) {
    AnatomyTheme {
        val settingsModel: SettingsViewModel = viewModel { SettingsViewModel(dependencies.settings) }
        val settingsState: SettingsUiState by settingsModel.state.collectAsState()

        ProvideAppLocale(settingsState.settings.interfaceLocale) {
            Surface(modifier = Modifier.fillMaxSize()) {
                // ...the existing Box and when-block, unchanged except that
                // MainScaffold now also takes `dependencies`
            }
        }
    }
}
```

Delete the `val repository = rememberSettingsRepository()` line. Add imports for `com.ptk.anatomypro.core.data.AppDependencies` and `com.ptk.anatomypro.core.designsystem.ProvideAppLocale`.

`MainScaffold` gains a `dependencies: AppDependencies` parameter and passes `dependencies.atlas` to `AtlasTab`; `AtlasTab` keeps its current `rememberAtlas()` call for now and Task 15 removes it.

- [ ] **Step 9: Wire the two entry points**

`androidApp/src/main/kotlin/com/ptk/anatomypro/MainActivity.kt` — the `setContent` block becomes:

```kotlin
setContent { App(appDependencies()) }
```

`androidApp` is a plain Android application module with build-type source sets, not a KMP module, so `appDependencies()` is **not** an `expect`/`actual` pair. It is one function declared once per build type, and the build type decides which file compiles.

Create `androidApp/src/release/kotlin/com/ptk/anatomypro/EntryDependencies.kt`:

```kotlin
package com.ptk.anatomypro

import androidx.compose.runtime.Composable
import com.ptk.anatomypro.core.data.AppDependencies

@Composable
internal fun appDependencies(): AppDependencies = rememberAppDependencies()
```

and `androidApp/src/debug/kotlin/com/ptk/anatomypro/EntryDependencies.kt`:

```kotlin
package com.ptk.anatomypro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ptk.anatomypro.core.data.AppDependencies
import com.ptk.anatomypro.core.data.fake.fakeAppDependencies

/**
 * Debug builds run entirely on fakes, so every screen is reachable without a database,
 * a pack on disk, or a backend.
 */
@Composable
internal fun appDependencies(): AppDependencies = remember { fakeAppDependencies() }
```

In `androidApp/build.gradle.kts`, add:

```kotlin
dependencies {
    debugImplementation(project(":shared:core-data-fake"))
}
```

The release source set never sees the fake module, so a release build cannot link it.

For iOS there is no debug/release source split, so `shared/src/iosMain/kotlin/com/ptk/anatomypro/MainViewController.kt` keeps one production entry point:

```kotlin
fun mainViewController() = ComposeUIViewController { App(rememberAppDependencies()) }
```

The fake path on iOS is reached by the iOS host calling a second exported function; that is plan 2's problem, not this one. The `NotBuilt*` refusals are what protect iOS release builds, not the linkage.

- [ ] **Step 10: Build both targets**

```bash
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:assembleRelease
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
./gradlew check
```

Expected: BUILD SUCCESSFUL for each, and every existing test still passing. The release build is in this list deliberately: it is what proves the fake module is not on the release classpath.

- [ ] **Step 11: Commit**

```bash
git add shared androidApp
git commit -m "refactor: pass the repository set into App, and refuse what is not built"
```

---

### Task 14: The real destination hierarchy

**Files:**
- Delete: `shared/core-navigation/src/commonMain/kotlin/com/ptk/anatomypro/navigation/SpikeRoutes.kt`
- Delete: `shared/core-navigation/src/commonTest/kotlin/com/ptk/anatomypro/navigation/SpikeRoutesTest.kt`
- Create: `shared/core-navigation/src/commonMain/kotlin/com/ptk/anatomypro/navigation/Destinations.kt`
- Create: `shared/core-navigation/src/commonTest/kotlin/com/ptk/anatomypro/navigation/DestinationsTest.kt`

**Interfaces:**
- Consumes: the proven navigation-compose version (Task 1).
- Produces: `TopLevel` (enum with `route`), `AtlasRoute`, `QuizRoute`, `DailyRoute`, `ProfileRoute` — every screen in plans 2–6 declares its route here.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ptk.anatomypro.navigation

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DestinationsTest {

    @Test
    fun there_are_five_top_level_tabs_in_the_prototypes_order() {
        assertEquals(
            listOf("Today", "Atlas", "Test", "Ranking", "Profile"),
            TopLevel.entries.map { it.name },
        )
    }

    @Test
    fun every_tab_has_its_own_start_route_so_each_keeps_its_own_back_stack() {
        val starts = TopLevel.entries.map { it.start }

        assertEquals(starts.distinct().size, starts.size)
    }

    @Test
    fun a_route_carrying_a_structure_id_round_trips() {
        val route = AtlasRoute.Detail("costa-vii")

        assertEquals(route, Json.decodeFromString(AtlasRoute.Detail.serializer(), Json.encodeToString(AtlasRoute.Detail.serializer(), route)))
    }

    @Test
    fun a_quiz_session_route_carries_both_the_session_and_the_question_index() {
        val route = QuizRoute.Question("session-0", index = 3)

        assertEquals(3, route.index)
        assertEquals("session-0", route.sessionId)
    }

    @Test
    fun the_structure_tree_is_an_atlas_route_because_it_is_the_atlas_not_a_settings_page() {
        assertTrue(AtlasRoute.Tree is AtlasRoute)
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `./gradlew :shared:core-navigation:allTests`
Expected: FAIL — `TopLevel` and the route types are unresolved.

- [ ] **Step 3: Delete the spike and write Destinations.kt**

```bash
rm shared/core-navigation/src/commonMain/kotlin/com/ptk/anatomypro/navigation/SpikeRoutes.kt
rm shared/core-navigation/src/commonTest/kotlin/com/ptk/anatomypro/navigation/SpikeRoutesTest.kt
```

```kotlin
package com.ptk.anatomypro.navigation

import kotlinx.serialization.Serializable

/**
 * Where each tab starts.
 *
 * Each tab owns its own back stack, so leaving the quiz mid-session and returning lands
 * back in the question rather than at the topic grid (spec §7).
 */
enum class TopLevel(val start: Any) {
    Today(DailyRoute.Home),
    Atlas(AtlasRoute.Browse),
    Test(QuizRoute.Topics),
    Ranking(DailyRoute.Leaderboard),
    Profile(ProfileRoute.Profile),
}

/** Screens 04, 05, 06, 07, 21. */
@Serializable
sealed interface AtlasRoute {
    @Serializable data object Browse : AtlasRoute
    @Serializable data object Search : AtlasRoute
    @Serializable data object Layers : AtlasRoute
    @Serializable data object Tree : AtlasRoute
    @Serializable data class Detail(val structureId: String) : AtlasRoute
}

/** Screens 08 to 13. */
@Serializable
sealed interface QuizRoute {
    @Serializable data object Topics : QuizRoute
    @Serializable data class Question(val sessionId: String, val index: Int) : QuizRoute
    @Serializable data class Feedback(val sessionId: String, val index: Int) : QuizRoute
    @Serializable data class Summary(val sessionId: String) : QuizRoute
}

/** Screens 14, 15, 16. */
@Serializable
sealed interface DailyRoute {
    @Serializable data object Home : DailyRoute
    @Serializable data object Lobby : DailyRoute
    @Serializable data object Leaderboard : DailyRoute
}

/** Screens 17, 18, 19, 20. */
@Serializable
sealed interface ProfileRoute {
    @Serializable data object Profile : ProfileRoute
    @Serializable data object Settings : ProfileRoute
    @Serializable data object Packs : ProfileRoute
    @Serializable data object Paywall : ProfileRoute
}

/** Screens 01, 02, 03 — shown before the tabs exist. */
@Serializable
sealed interface OnboardingRoute {
    @Serializable data object Language : OnboardingRoute
    @Serializable data object Goals : OnboardingRoute
    @Serializable data object FirstDownload : OnboardingRoute
}
```

- [ ] **Step 4: Run the test and watch it pass**

Run: `./gradlew :shared:core-navigation:allTests`
Expected: PASS on both targets.

- [ ] **Step 5: Commit**

```bash
git add shared/core-navigation
git commit -m "feat: declare every destination the twenty-one screens need"
```

---

### Task 15: Move the shell onto core-navigation

**Files:**
- Modify: `shared/build.gradle.kts`
- Modify: `shared/src/commonMain/kotlin/com/ptk/anatomypro/App.kt`
- Modify: `shared/src/commonMain/kotlin/com/ptk/anatomypro/AtlasTab.kt`
- Modify: `shared/src/commonMain/kotlin/com/ptk/anatomypro/navigation/AnatomyBottomBar.kt`
- Delete: `shared/src/commonMain/kotlin/com/ptk/anatomypro/navigation/AnatomyDestination.kt`

**Interfaces:**
- Consumes: `TopLevel`, the route types (Task 14), `AppDependencies` (Task 13).
- Produces: a `NavHost`-based shell. Plans 2–6 add screens by adding `composable<Route>` entries, nothing else.

- [ ] **Step 1: Depend on the navigation module**

In `shared/build.gradle.kts`, add to `commonMain.dependencies`:

```kotlin
api(project(":shared:core-navigation"))
```

- [ ] **Step 2: Move the bottom bar onto TopLevel**

`AnatomyDestination` and `TopLevel` are the same list twice. Delete `AnatomyDestination.kt` and change `AnatomyBottomBar.kt`:

- `selected: AnatomyDestination` becomes `selected: TopLevel`; `onSelect: (AnatomyDestination) -> Unit` becomes `(TopLevel) -> Unit`.
- `AnatomyDestination.entries` becomes `TopLevel.entries`.
- `drawIcon(destination: AnatomyDestination, ...)` becomes `drawIcon(destination: TopLevel, ...)` and its `when` arms become `TopLevel.Today`, `TopLevel.Atlas`, `TopLevel.Test`, `TopLevel.Ranking`, `TopLevel.Profile`. The drawing code inside each arm is unchanged.
- The `label` the bar renders comes from a string resource in Task 16, not from the enum. Until then, keep a local `private val TopLevel.label: String` in `AnatomyBottomBar.kt` with the existing values — `DZIŚ`, `ATLAS`, `TEST`, `RANKING`, `PROFIL` — so this task changes navigation only.

- [ ] **Step 3: Replace MainScaffold's when-block with a NavHost**

In `App.kt`:

```kotlin
@Composable
private fun MainScaffold(
    dependencies: AppDependencies,
    state: SettingsUiState,
    model: SettingsViewModel,
) {
    var tab by rememberSaveable { mutableStateOf(TopLevel.Atlas) }
    // One controller per tab, so each keeps its own back stack (spec §7).
    val controllers = TopLevel.entries.associateWith { rememberNavController() }
    val controller = controllers.getValue(tab)

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            NavHost(navController = controller, startDestination = tab.start) {
                composable<AtlasRoute.Browse> {
                    AtlasTab(
                        repository = dependencies.atlas,
                        locale = state.settings.interfaceLocale,
                        latinOnly = state.settings.nameDisplay == NameDisplay.LatinOnly,
                        onOpenDetail = { controller.navigate(AtlasRoute.Detail(it.value)) },
                        onSearch = { controller.navigate(AtlasRoute.Search) },
                    )
                }
                composable<AtlasRoute.Search> { SearchRoute(dependencies, controller) }
                composable<AtlasRoute.Detail> { entry ->
                    DetailRoute(dependencies, controller, entry.toRoute<AtlasRoute.Detail>().structureId, state)
                }
                composable<ProfileRoute.Profile> {
                    SettingsScreen(
                        state = state,
                        onInterfaceLocale = model::onInterfaceLocale,
                        onExaminationLocale = model::onExaminationLocale,
                        onNameDisplay = model::onNameDisplay,
                        onQuizTimer = model::onQuizTimer,
                        onStructureTreeMode = model::onStructureTreeMode,
                        onPatternsNotColour = model::onPatternsNotColour,
                    )
                }
                // Plans 2-6 add their composable<Route> entries here. Until then the three
                // unbuilt tabs start on a route that has no screen, so each declares a
                // placeholder rather than crashing the NavHost.
                composable<DailyRoute.Home> { Centered("DZIŚ — jeszcze nie zbudowane") }
                composable<QuizRoute.Topics> { Centered("TEST — jeszcze nie zbudowane") }
                composable<DailyRoute.Leaderboard> { Centered("RANKING — jeszcze nie zbudowane") }
            }
        }
        AnatomyBottomBar(selected = tab, onSelect = { tab = it })
    }
}
```

Add the imports: `androidx.navigation.compose.NavHost`, `androidx.navigation.compose.composable`, `androidx.navigation.compose.rememberNavController`, `androidx.navigation.toRoute`, and the route types from `com.ptk.anatomypro.navigation`.

`SearchRoute` and `DetailRoute` are the two bodies lifted out of `AtlasTab`'s old `when(route)` block, unchanged apart from taking a `NavController` and calling `controller.navigate(...)` / `controller.popBackStack()` where they used to assign to `route`.

- [ ] **Step 4: Strip AtlasTab down to the browse screen**

`AtlasTab.kt` loses its private `AtlasRoute` sealed interface, its `when(route)` block, and its `rememberAtlas()` call. It becomes the browse screen only, taking `repository: AtlasRepository?` and the two callbacks in the signature above. Its null branch keeps showing `Otwieranie atlasu…`. `BrowseRoute` and `TopBar` stay as they are.

- [ ] **Step 5: Build, run every test, and check the tabs by hand**

```bash
./gradlew check
./gradlew :androidApp:assembleDebug
```

Expected: BUILD SUCCESSFUL and all tests passing. Then install and confirm by hand: each of the five tabs selects; Atlas opens a structure's detail and the system back gesture returns to the model; switching to another tab and back leaves the atlas where it was.

- [ ] **Step 6: Commit**

```bash
git add shared
git commit -m "refactor: move the shell onto navigation-compose"
```

---

### Task 16: Every existing string becomes a resource

The last foundation. After this, a screen in plans 2–6 that writes a literal is a review failure rather than a matter of taste.

**Files:**
- Modify: `shared/build.gradle.kts`, `shared/feature-atlas/build.gradle.kts`, `shared/feature-search/build.gradle.kts`, `shared/feature-settings/build.gradle.kts`
- Create: `values/strings.xml` and `values-pl/strings.xml` under each of those modules' `src/commonMain/composeResources/`
- Modify: `AnatomyBottomBar.kt`, `AtlasTab.kt`, `App.kt`, `AtlasScreen.kt`, `StructureDetailScreen.kt`, `SearchScreen.kt`, `SettingsScreen.kt`, `LanguageSelectionScreen.kt`

**Interfaces:**
- Consumes: `ProvideAppLocale` / `LocalAppLocale` (Task 2), and whichever override mechanism Task 2 landed on.
- Produces: a per-module `Res` accessor and the convention every screen in plans 2–6 follows.

- [ ] **Step 1: Turn on compose-resources in each module**

In all four build files, add to `commonMain.dependencies`:

```kotlin
implementation(libs.compose.components.resources)
```

- [ ] **Step 2: Find every literal**

Run this and keep the output next to you — it is the checklist for Steps 3 and 4:

```bash
grep -rn '"[^"]*[ąćęłńóśźżĄĆĘŁŃÓŚŹŻ][^"]*"' shared/src/commonMain shared/feature-*/src/commonMain --include=*.kt
grep -rn 'Text("' shared/src/commonMain shared/feature-*/src/commonMain --include=*.kt
```

Expected: the bottom bar's five labels, `ATLAS`, `SZUKAJ`, `Otwieranie atlasu…`, the settings screen's group headings and switch labels, the language screen's copy, and the detail screen's section headings.

- [ ] **Step 3: Write the string files**

For each module, `src/commonMain/composeResources/values/strings.xml` holds **English** (the base bundle) and `values-pl/strings.xml` holds Polish. For `:shared`, the bottom bar and shell:

`shared/src/commonMain/composeResources/values/strings.xml`:

```xml
<resources>
    <string name="tab_today">TODAY</string>
    <string name="tab_atlas">ATLAS</string>
    <string name="tab_test">TEST</string>
    <string name="tab_ranking">RANKING</string>
    <string name="tab_profile">PROFILE</string>
    <string name="atlas_opening">Opening the atlas…</string>
    <string name="atlas_search">SEARCH</string>
    <string name="not_built_yet">%1$s — not built yet</string>
</resources>
```

`shared/src/commonMain/composeResources/values-pl/strings.xml`:

```xml
<resources>
    <string name="tab_today">DZIŚ</string>
    <string name="tab_atlas">ATLAS</string>
    <string name="tab_test">TEST</string>
    <string name="tab_ranking">RANKING</string>
    <string name="tab_profile">PROFIL</string>
    <string name="atlas_opening">Otwieranie atlasu…</string>
    <string name="atlas_search">SZUKAJ</string>
    <string name="not_built_yet">%1$s — jeszcze nie zbudowane</string>
</resources>
```

Do the same for `feature-atlas`, `feature-search` and `feature-settings`, taking the Polish from the literals Step 2 listed and writing the English yourself. Key names are `<screen>_<purpose>` in lower snake case.

- [ ] **Step 4: Replace every literal with a lookup**

Each `Text("SZUKAJ")` becomes `Text(stringResource(Res.string.atlas_search))`, importing `org.jetbrains.compose.resources.stringResource` and the module's generated `Res`. The bottom bar's `private val TopLevel.label` from Task 15 is deleted; the bar reads:

```kotlin
val label = when (destination) {
    TopLevel.Today -> stringResource(Res.string.tab_today)
    TopLevel.Atlas -> stringResource(Res.string.tab_atlas)
    TopLevel.Test -> stringResource(Res.string.tab_test)
    TopLevel.Ranking -> stringResource(Res.string.tab_ranking)
    TopLevel.Profile -> stringResource(Res.string.tab_profile)
}
```

- [ ] **Step 5: Prove no literal survived**

Re-run Step 2's two greps.
Expected: no hits in `commonMain` outside `composeResources`. A hit is a miss to fix, not a judgement call.

- [ ] **Step 6: Build and check both languages by hand**

```bash
./gradlew check
./gradlew :androidApp:assembleDebug
```

Install and confirm: with the interface language set to Polish, the bar reads `DZIŚ / ATLAS / TEST / RANKING / PROFIL`; switching it to English in settings changes them to `TODAY / ATLAS / TEST / RANKING / PROFILE` **without restarting the app**, and without the device's system language changing. That last clause is the whole point of Task 2 — if the strings only change after a restart or only follow the system, the override is not working and Task 2's escalation path applies.

- [ ] **Step 7: Commit**

```bash
git add shared
git commit -m "feat: every UI string is a resource, English base and Polish"
```

---

## Done when

- `./gradlew check` passes, and `./gradlew :shared:core-data-fake:allTests` passes on the JVM host and the iOS simulator.
- The app builds and runs on Android and the iOS simulator with all five tabs selectable.
- Switching the interface language in settings changes the UI language immediately, independently of the system language.
- No literal UI string remains in any `commonMain` source outside `composeResources`.
- `:shared:core-data-fake` is not a dependency of `:androidApp`'s release configuration.

Plans 2–6 can then be written and executed in any order.
