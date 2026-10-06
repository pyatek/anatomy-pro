# Profile, Paywall and Pack Manager (Screens 17–19) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the profile (17: streak calendar and mastery by system), the paywall (18: free, subscribed, purchase failed) and the pack manager (19: installed, available, downloading, delete confirmation), and put settings behind the profile where the prototype has them.

**Architecture:** Two new modules, as the all-screens spec lays out: `:shared:feature-profile` for screens 17 and 19, `:shared:feature-commerce` for screen 18. Each screen has one ViewModel over one or two repositories. The paywall sells the learning system and never content; the pack manager locks nothing. Production has none of the three repositories, so there the Profile tab keeps showing settings, as it does today.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform 1.11.1, navigation-compose 2.9.2, kotlinx-datetime 0.8.0, kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-09-24-all-screens-mocked-design.md` §4.2 (progress), §4.5 with §15.2 (entitlements gate topics, not packs), §4.6 (packs), §9 with §15.3 (states that must exist), §10, §11 step 10. Design spec `docs/superpowers/specs/2026-08-29-anatomy-pro-design.md` §10 (packs: resumable, checksummed), §12, §14, §29 (the whole atlas is free; the subscription is the learning system; packs carry no entitlement).

**Prerequisite:** none of the other step plans. Where one of them has already been executed this plan says what to reuse (the paywall placeholder from the quiz plan, `megabytes` from the onboarding plan).

## Global Constraints

- **Repository root is `~/StudioProjects/AnatomyPro`.** Never edit `~/Projekty/anatomy pro`.
- **Tests run on two targets.** `./gradlew :shared:<module>:allTests` runs the JVM host and the iOS simulator. A test that passes on one and not the other is a failure.
- **Do not run `allTests` while an app is running on a simulator.** The iOS GPU contract test `reports_a_pick` times out under that contention.
- `./gradlew --stop` between long sessions. This machine runs out of memory.
- **No `Co-Authored-By` or `Claude-Session` trailers on commits.**
- **UI strings are always resources** (all-screens spec §8). English in `values/`, Polish in `values-pl/`. Key names are `<screen>_<purpose>` in lower snake case.
- **ViewModels never produce user-visible text.** Plan titles, price labels, pack labels and system titles are data from a repository and are shown as given. A failure message returned by a repository is **not** shown: the screen says what happened in its own words.
- **The paywall sells the learning system** — quizzes beyond the skeleton, the daily quiz, the leaderboard — **and never content** (design §29). No sentence on it may suggest that an atlas, a system or a pack is unlocked by paying.
- **No pack is ever locked** (all-screens spec §15.3). `PackState.entitlement` is not read.
- **The profile's calendar is in the user's own days** (all-screens spec §4.2), not UTC.
- **§12:** touch targets at least 44 dp; a studied day is marked by a glyph and described in words, not by colour alone; a destructive action is confirmed.
- **Colours come from `core-designsystem`.** Never introduce a colour literal.
- **No Compose UI tests** (all-screens spec §10, decided). Screens are checked by hand in Task 7.
- **Out of scope:** billing (Phase 4) and prices — the fake's plans stand in, and pricing and trial length are still open questions; real downloads (Phase 1's own task); sign-in and nickname (§9.3); removing `PackState.entitlement` and `PackFailure.NOT_ENTITLED` from the contract, which touches the onboarding screens and is its own change.

## Decisions this plan makes

1. **The Profile tab opens the profile, and settings move one step in**, to `ProfileRoute.Settings`, as in the prototype. In production, where there is no profile to show, the tab opens settings directly as it does now.
2. **The calendar shows the last five weeks**, 35 days ending today, oldest first.
3. **A cancelled purchase says nothing.** The user closed the sheet; they know.
4. **Restoring with nothing to restore says so.** Silence there reads as a button that does nothing.
5. **Deleting a pack is confirmed in place**, on its row, not in a dialog: one row changes to "Delete? Yes / No". A download is not confirmed and can be cancelled.
6. **`megabytes` lives in `core-designsystem`.** Both onboarding and the pack manager print sizes, and a feature module should not depend on another for a formatter.

## Review Focus

1. **The purchase button is pressed twice**: one purchase is attempted. — Task 4.
2. **A purchase throws instead of returning an outcome** (the store is unreachable): the paywall says the purchase failed, and is usable again. — Task 4.
3. **The pack list changes while a delete is waiting for confirmation** (the pack finished downloading, or vanished): the confirmation is for the pack that was chosen, or is dropped if that pack is gone. — Task 5.
4. **A download fails**: the row says so in words and offers to try again; the other rows still work. — Task 5.
5. **Progress cannot be read**: the profile says so and can be retried; it does not show an empty calendar as if nothing had been studied. — Task 3.

## File Structure

| File | Responsibility |
|---|---|
| `shared/core-designsystem/.../Megabytes.kt` + its test | Create (or move from `feature-onboarding`). Byte counts as text. |
| `shared/core-data-fake/.../FakeProgressRepository.kt`, `FakePackRepository.kt` + tests | Modify. Real system ids; no pack entitled. |
| `settings.gradle.kts`, `shared/build.gradle.kts` | Modify. Two modules. |
| `shared/feature-profile/build.gradle.kts`, `shared/feature-commerce/build.gradle.kts` | Create. |
| `shared/feature-profile/.../ProfileViewModel.kt`, `ProfileScreen.kt` | Create. Screen 17. |
| `shared/feature-profile/.../PacksViewModel.kt`, `PacksScreen.kt` | Create. Screen 19. |
| `shared/feature-commerce/.../PaywallViewModel.kt`, `PaywallScreen.kt` | Create. Screen 18. |
| `composeResources/values{,-pl}/strings.xml` in both modules | Create. |
| `commonTest` in both modules | Create. `ProfileViewModelTest`, `PacksViewModelTest`, `PaywallViewModelTest`. |
| `shared/src/commonMain/.../ProfileTab.kt`, `App.kt`, shell `strings.xml` | Create / modify. The routes. |
| `androidApp/src/debug/.../EntryDependencies.kt` | Modify. An extra that makes purchases fail. |
| `docs/state-of-play.md`, the design spec | Modify. |

Paths abbreviate `shared/feature-profile/src/commonMain/kotlin/com/ptk/anatomypro/feature/profile/` as `.../profile/` and `shared/feature-commerce/src/commonMain/kotlin/com/ptk/anatomypro/feature/commerce/` as `.../commerce/`.

---

### Task 1: Sizes as text, in one place

**Files:**
- Create: `shared/core-designsystem/src/commonMain/kotlin/com/ptk/anatomypro/core/designsystem/Megabytes.kt`
- Test: `shared/core-designsystem/src/commonTest/kotlin/com/ptk/anatomypro/core/designsystem/MegabytesTest.kt`

**Interfaces:**
- Produces: `fun megabytes(bytes: Long, decimalSeparator: String): String` in package `com.ptk.anatomypro.core.designsystem`.

**If the onboarding plan has been executed**, `shared/feature-onboarding` already has `Megabytes.kt` and `MegabytesTest.kt` with exactly this function. Then this task is a move: `git mv` both files to the paths above, change their `package` lines to `com.ptk.anatomypro.core.designsystem`, add `import com.ptk.anatomypro.core.designsystem.megabytes` to `FirstDownloadScreen.kt`, run `./gradlew :shared:core-designsystem:allTests :shared:feature-onboarding:allTests`, and commit. Skip Steps 1–4.

- [ ] **Step 1: Write the failing test**

`MegabytesTest.kt`:

```kotlin
package com.ptk.anatomypro.core.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals

class MegabytesTest {

    @Test
    fun a_size_is_shown_to_one_decimal_place() {
        assertEquals("48.3", megabytes(48_300_000, "."))
    }

    @Test
    fun the_separator_is_the_callers_because_languages_differ() {
        assertEquals("48,3", megabytes(48_300_000, ","))
    }

    @Test
    fun a_whole_number_keeps_its_zero_so_the_figure_does_not_change_width_as_it_grows() {
        assertEquals("12.0", megabytes(12_000_000, "."))
    }

    @Test
    fun less_than_a_tenth_rounds_down_to_zero_rather_than_up_to_progress_that_has_not_happened() {
        assertEquals("0.0", megabytes(99_999, "."))
    }

    @Test
    fun a_negative_count_is_shown_as_nothing() {
        assertEquals("0.0", megabytes(-5, "."))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :shared:core-designsystem:allTests`
Expected: compilation FAILS with `Unresolved reference 'megabytes'`.

- [ ] **Step 3: Implement**

`Megabytes.kt`:

```kotlin
package com.ptk.anatomypro.core.designsystem

/**
 * A byte count in megabytes to one decimal place, truncated.
 *
 * Decimal megabytes, as download sizes are quoted. Truncated rather than rounded so a
 * figure never claims bytes that have not arrived. [decimalSeparator] comes from a string
 * resource: common Kotlin has no locale-aware number formatter.
 */
fun megabytes(bytes: Long, decimalSeparator: String): String {
    val tenths = bytes.coerceAtLeast(0) / 100_000
    return "${tenths / 10}$decimalSeparator${tenths % 10}"
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :shared:core-designsystem:allTests`
Expected: BUILD SUCCESSFUL, `MegabytesTest` five passing per target.

- [ ] **Step 5: Commit**

```bash
git add shared/core-designsystem shared/feature-onboarding
git commit -m "feat(designsystem): byte counts as text, where every screen can reach them"
```

---

### Task 2: Fakes that match the decision

The progress fake names its systems `skeletal` and `muscular`; real data and `FREE_SYSTEMS` say `skeletal-system`. The pack fake marks two packs as entitled to a system; packs are free (all-screens spec §15.2).

**Files:**
- Modify: `shared/core-data-fake/src/commonMain/kotlin/com/ptk/anatomypro/core/data/fake/FakeProgressRepository.kt`
- Modify: `shared/core-data-fake/src/commonMain/kotlin/com/ptk/anatomypro/core/data/fake/FakePackRepository.kt`
- Test: `shared/core-data-fake/src/commonTest/kotlin/com/ptk/anatomypro/core/data/fake/FakeProgressRepositoryTest.kt`, `FakeCommerceTest.kt`

**Interfaces:**
- Produces: `FakeProgressRepository.masteryBySystem` returns systems `skeletal-system` and `muscular-system`; every `PackState.entitlement` from `FakePackRepository` is null.

- [ ] **Step 1: Write the failing tests**

Add to `FakeProgressRepositoryTest`:

```kotlin
    @Test
    fun mastery_is_reported_under_the_ids_the_atlas_uses() = runTest {
        val systems = FakeProgressRepository().masteryBySystem("pl").map { it.system.value }

        assertEquals(listOf("skeletal-system", "muscular-system"), systems)
    }
```

Add to `FakeCommerceTest`:

```kotlin
    @Test
    fun no_pack_is_entitled_to_anything_because_packs_are_free() = runTest {
        // All-screens spec §15.2: entitlements gate quiz topics, not packs.
        assertTrue(FakePackRepository().packs.first().all { it.entitlement == null })
    }
```

Add any import the files lack (`kotlinx.coroutines.test.runTest`, `kotlin.test.assertEquals`, `kotlinx.coroutines.flow.first`).

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :shared:core-data-fake:allTests`
Expected: FAIL — `[skeletal, muscular]` where `[skeletal-system, muscular-system]` was expected, and two packs with an entitlement.

- [ ] **Step 3: Implement**

In `FakeProgressRepository.kt`, replace the two `SystemMastery(…)` lines with:

```kotlin
            SystemMastery(SystemId("skeletal-system"), title("skeletal-system", locale), structuresSeen = 14, structuresTotal = 19),
            SystemMastery(SystemId("muscular-system"), title("muscular-system", locale), structuresSeen = 0, structuresTotal = 24),
```

and the two keys of `SYSTEM_TITLES`, `"skeletal"` and `"muscular"`, with `"skeletal-system"` and `"muscular-system"`.

In `FakePackRepository.kt`, replace `entitlement = SystemId("muscular"),` and `entitlement = SystemId("nervous"),` with `entitlement = null,`, and remove the `SystemId` import if nothing else in the file uses it.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :shared:core-data-fake:allTests`
Expected: BUILD SUCCESSFUL on both targets.

- [ ] **Step 5: Commit**

```bash
git add shared/core-data-fake
git commit -m "test(fake): progress under the atlas's system ids, and no pack entitled"
```

---

### Task 3: The profile module and screen 17's ViewModel

**Files:**
- Modify: `settings.gradle.kts`, `shared/build.gradle.kts`
- Create: `shared/feature-profile/build.gradle.kts`
- Create: `.../profile/ProfileViewModel.kt`
- Test: `shared/feature-profile/src/commonTest/kotlin/com/ptk/anatomypro/feature/profile/ProfileViewModelTest.kt`

**Interfaces:**
- Consumes: `ProgressRepository.streak / masteryBySystem / activity`, `PackRepository.packs`, `StreakState`, `SystemMastery`, `DayActivity`, `PackStatus.Installed`, the fakes.
- Produces:
  ```kotlin
  const val CALENDAR_DAYS = 35
  data class CalendarDay(val date: LocalDate, val activity: DayActivity?)
  data class ProfileUiState(
      val streak: StreakState? = null,
      val calendar: List<CalendarDay> = emptyList(),
      val mastery: List<SystemMastery> = emptyList(),
      val installedPacks: Int = 0,
      val installedBytes: Long = 0,
      val isLoading: Boolean = true,
      val failed: Boolean = false,
  )
  class ProfileViewModel(
      progress: ProgressRepository, packs: PackRepository, locale: String, today: () -> LocalDate,
  ) : ViewModel() {
      val state: StateFlow<ProfileUiState>
      fun onRetry()
  }
  fun profileIsBuilt(progress: ProgressRepository, packs: PackRepository): Boolean
  ```
  `today` supplies the user's local date.

- [ ] **Step 1: Create the module**

`settings.gradle.kts`, add:

```kotlin
include(":shared:feature-profile")
include(":shared:feature-commerce")
```

`shared/build.gradle.kts`, in `commonMain.dependencies`, add:

```kotlin
            api(project(":shared:feature-profile"))
            api(project(":shared:feature-commerce"))
```

`shared/feature-profile/build.gradle.kts`:

```kotlin
plugins { id("anatomypro.kmp.compose") }

kotlin {
    android { namespace = "com.ptk.anatomypro.feature.profile" }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.components.resources)
            api(project(":shared:core-data"))
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

`shared/feature-commerce/build.gradle.kts` is the same file with the namespace `com.ptk.anatomypro.feature.commerce`. Create it now so the settings file resolves; its sources come in Task 4.

- [ ] **Step 2: Write the failing tests**

`ProfileViewModelTest.kt`:

```kotlin
package com.ptk.anatomypro.feature.profile

import com.ptk.anatomypro.core.data.NotBuiltPackRepository
import com.ptk.anatomypro.core.data.NotBuiltProgressRepository
import com.ptk.anatomypro.core.data.fake.FakeBehaviour
import com.ptk.anatomypro.core.data.fake.FakePackRepository
import com.ptk.anatomypro.core.data.fake.FakeProgressRepository
import com.ptk.anatomypro.core.data.model.DayActivity
import com.ptk.anatomypro.core.data.repository.ProgressRepository
import kotlinx.coroutines.Dispatchers
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate(2026, 9, 24)

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun model(progress: ProgressRepository = FakeProgressRepository(), locale: String = "pl") =
        ProfileViewModel(progress, FakePackRepository(), locale, today = { today })

    @Test
    fun the_calendar_is_the_last_five_weeks_ending_today_oldest_first() = runTest(dispatcher) {
        // §9, screen 17: streak calendar.
        val model = model()
        advanceUntilIdle()

        val calendar = model.state.value.calendar
        assertEquals(CALENDAR_DAYS, calendar.size)
        assertEquals(LocalDate(2026, 8, 21), calendar.first().date)
        assertEquals(today, calendar.last().date)
    }

    @Test
    fun a_day_carries_what_was_done_on_it() = runTest(dispatcher) {
        // The fake studies every day whose number is not a multiple of three, and completes
        // the daily on the even ones among them.
        val model = model()
        advanceUntilIdle()
        val days = model.state.value.calendar.associateBy { it.date }

        assertEquals(DayActivity(sessions = 1, questionsAnswered = 10, dailyCompleted = true), days.getValue(LocalDate(2026, 9, 22)).activity)
        assertEquals(DayActivity(sessions = 1, questionsAnswered = 10, dailyCompleted = false), days.getValue(LocalDate(2026, 9, 23)).activity)
        assertEquals(0, days.getValue(today).activity?.sessions)
    }

    @Test
    fun the_streak_is_shown_beside_the_calendar() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        assertEquals(12, model.state.value.streak?.currentDays)
    }

    @Test
    fun mastery_is_listed_by_system_and_titled_in_the_interface_language() = runTest(dispatcher) {
        // §9, screen 17: mastery by system.
        val model = model(locale = "en")
        advanceUntilIdle()

        val mastery = model.state.value.mastery
        assertEquals(listOf("Skeletal system", "Muscular system"), mastery.map { it.title })
        assertEquals(14, mastery.first().structuresSeen)
        assertEquals(0f, mastery.last().fraction)
    }

    @Test
    fun what_is_installed_is_counted_and_sized() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        assertEquals(1, model.state.value.installedPacks)
        assertEquals(48_300_000L, model.state.value.installedBytes)
    }

    @Test
    fun progress_that_cannot_be_read_is_a_failure_not_an_empty_calendar() = runTest(dispatcher) {
        // Review Focus 5. An empty calendar would say "you have studied nothing".
        val model = model(progress = FakeProgressRepository(FakeBehaviour(failure = { IllegalStateException("no store") })))
        advanceUntilIdle()

        assertTrue(model.state.value.failed)
        assertFalse(model.state.value.isLoading)
        assertTrue(model.state.value.calendar.isEmpty())
    }

    @Test
    fun retrying_after_a_failure_loads_the_profile() = runTest(dispatcher) {
        var broken = true
        val real = FakeProgressRepository()
        val flaky = object : ProgressRepository by real {
            override suspend fun masteryBySystem(locale: String) =
                if (broken) throw IllegalStateException("no store") else real.masteryBySystem(locale)
        }
        val model = model(progress = flaky)
        advanceUntilIdle()
        assertTrue(model.state.value.failed)

        broken = false
        model.onRetry()
        advanceUntilIdle()

        assertFalse(model.state.value.failed)
        assertEquals(CALENDAR_DAYS, model.state.value.calendar.size)
    }

    @Test
    fun there_is_no_profile_where_its_repositories_refuse() {
        // All-screens spec §6.
        assertFalse(profileIsBuilt(NotBuiltProgressRepository, FakePackRepository()))
        assertFalse(profileIsBuilt(FakeProgressRepository(), NotBuiltPackRepository))
        assertTrue(profileIsBuilt(FakeProgressRepository(), FakePackRepository()))
    }
}
```

- [ ] **Step 3: Run them to verify they fail**

Run: `./gradlew :shared:feature-profile:allTests`
Expected: compilation FAILS with `Unresolved reference 'ProfileViewModel'`.

- [ ] **Step 4: Implement**

`.../profile/ProfileViewModel.kt`:

```kotlin
package com.ptk.anatomypro.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptk.anatomypro.core.data.NotBuiltPackRepository
import com.ptk.anatomypro.core.data.NotBuiltProgressRepository
import com.ptk.anatomypro.core.data.model.DayActivity
import com.ptk.anatomypro.core.data.model.PackStatus
import com.ptk.anatomypro.core.data.model.StreakState
import com.ptk.anatomypro.core.data.model.SystemMastery
import com.ptk.anatomypro.core.data.repository.PackRepository
import com.ptk.anatomypro.core.data.repository.ProgressRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Five weeks: enough to see a habit, few enough to fit one screen. */
const val CALENDAR_DAYS = 35

/** One square of the calendar. A null [activity] is a day the store said nothing about. */
data class CalendarDay(val date: LocalDate, val activity: DayActivity?)

data class ProfileUiState(
    val streak: StreakState? = null,
    val calendar: List<CalendarDay> = emptyList(),
    val mastery: List<SystemMastery> = emptyList(),
    val installedPacks: Int = 0,
    val installedBytes: Long = 0,
    val isLoading: Boolean = true,
    val failed: Boolean = false,
)

/**
 * Prototype screen 17: the profile.
 *
 * [today] is the user's own date. The calendar is in the user's days, never UTC: it is
 * their habit, and the daily quiz's day is a different thing (all-screens spec §4.2).
 */
class ProfileViewModel(
    private val progress: ProgressRepository,
    private val packs: PackRepository,
    private val locale: String,
    private val today: () -> LocalDate,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            // Unknown rather than zero if it cannot be read: the rest of the screen stands.
            progress.streak.catch { }.collect { streak -> _state.update { it.copy(streak = streak) } }
        }
        viewModelScope.launch {
            packs.packs.catch { }.collect { all ->
                val installed = all.filter { it.status == PackStatus.Installed }
                _state.update { it.copy(installedPacks = installed.size, installedBytes = installed.sumOf { p -> p.byteSize }) }
            }
        }
    }

    fun onRetry() {
        if (_state.value.isLoading) return
        load()
    }

    private fun load() {
        _state.update { it.copy(isLoading = true, failed = false) }
        viewModelScope.launch {
            try {
                val end = today()
                val start = LocalDate.fromEpochDays(end.toEpochDays() - (CALENDAR_DAYS - 1))
                val activity = progress.activity(start, end)
                val mastery = progress.masteryBySystem(locale)
                val calendar = (0 until CALENDAR_DAYS).map { offset ->
                    val date = LocalDate.fromEpochDays(start.toEpochDays() + offset)
                    CalendarDay(date, activity[date])
                }
                _state.update { it.copy(calendar = calendar, mastery = mastery, isLoading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Not an empty calendar: that would say nothing had been studied.
                _state.update { it.copy(calendar = emptyList(), mastery = emptyList(), isLoading = false, failed = true) }
            }
        }
    }
}

/**
 * Whether there is a profile to show.
 *
 * Production has no ProgressRepository or PackRepository yet; the ones it is given throw on
 * first use, by design (all-screens spec §6). There the Profile tab opens settings.
 */
fun profileIsBuilt(progress: ProgressRepository, packs: PackRepository): Boolean =
    progress !is NotBuiltProgressRepository && packs !is NotBuiltPackRepository
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :shared:feature-profile:allTests`
Expected: BUILD SUCCESSFUL, eight tests passing per target.

If `toEpochDays()` returns a `Long` in this kotlinx-datetime and `fromEpochDays` wants an `Int` (or the reverse), convert at those two call sites; `FakeProgressRepository.activity` does the same arithmetic and shows which this version needs.

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts shared/build.gradle.kts shared/feature-profile shared/feature-commerce
git commit -m "feat(profile): five weeks of days, mastery by system, and what is installed"
```

---

### Task 4: Screen 18's ViewModel

**Files:**
- Create: `.../commerce/PaywallViewModel.kt`
- Test: `shared/feature-commerce/src/commonTest/kotlin/com/ptk/anatomypro/feature/commerce/PaywallViewModelTest.kt`

**Interfaces:**
- Consumes: `EntitlementRepository.entitlements / plans / purchase / restore`, `SubscriptionPlan`, `PurchaseOutcome`, `Entitlements`, `FakeEntitlementRepository(behaviour, initial, purchaseSucceeds)`.
- Produces:
  ```kotlin
  enum class PaywallNotice { PURCHASE_FAILED, NOTHING_TO_RESTORE, PLANS_FAILED }
  data class PaywallUiState(
      val subscribed: Boolean = false,
      val plans: List<SubscriptionPlan> = emptyList(),
      val isLoading: Boolean = true,
      /** The plan being bought, or null. While it is set, nothing else can be started. */
      val purchasing: String? = null,
      val restoring: Boolean = false,
      val notice: PaywallNotice? = null,
  ) { val busy: Boolean }
  class PaywallViewModel(entitlements: EntitlementRepository) : ViewModel() {
      val state: StateFlow<PaywallUiState>
      fun onPurchase(plan: SubscriptionPlan)
      fun onRestore()
      fun onRetry()
  }
  fun commerceIsBuilt(entitlements: EntitlementRepository): Boolean
  ```

- [ ] **Step 1: Write the failing tests**

`PaywallViewModelTest.kt`:

```kotlin
package com.ptk.anatomypro.feature.commerce

import com.ptk.anatomypro.core.data.NotBuiltEntitlementRepository
import com.ptk.anatomypro.core.data.fake.FakeEntitlementRepository
import com.ptk.anatomypro.core.data.model.Entitlements
import com.ptk.anatomypro.core.data.model.PurchaseOutcome
import com.ptk.anatomypro.core.data.model.SubscriptionPlan
import com.ptk.anatomypro.core.data.repository.EntitlementRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaywallViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    /** A repository whose purchase answers whatever the test says. */
    private class Scripted(var outcome: () -> PurchaseOutcome) : EntitlementRepository {
        private val real = FakeEntitlementRepository()
        var purchases = 0
        override val entitlements = real.entitlements
        override suspend fun plans() = real.plans()
        override suspend fun purchase(plan: SubscriptionPlan): PurchaseOutcome {
            purchases++
            return outcome()
        }
        override suspend fun restore() = real.restore()
    }

    @Test
    fun a_free_user_is_offered_the_plans() = runTest(dispatcher) {
        // §9, screen 18: free.
        val model = PaywallViewModel(FakeEntitlementRepository())
        advanceUntilIdle()

        val state = model.state.value
        assertFalse(state.subscribed)
        assertEquals(listOf("monthly", "yearly"), state.plans.map { it.id })
        assertFalse(state.isLoading)
        assertNull(state.notice)
    }

    @Test
    fun a_subscriber_is_told_they_are_subscribed() = runTest(dispatcher) {
        // §9, screen 18: subscribed.
        val model = PaywallViewModel(FakeEntitlementRepository(initial = Entitlements(subscribed = true, ownedSystems = emptySet())))
        advanceUntilIdle()

        assertTrue(model.state.value.subscribed)
    }

    @Test
    fun a_purchase_that_succeeds_makes_the_user_a_subscriber() = runTest(dispatcher) {
        val model = PaywallViewModel(FakeEntitlementRepository())
        advanceUntilIdle()

        model.onPurchase(model.state.value.plans.first())
        advanceUntilIdle()

        assertTrue(model.state.value.subscribed)
        assertNull(model.state.value.purchasing)
        assertNull(model.state.value.notice)
    }

    @Test
    fun a_purchase_that_fails_says_so_and_leaves_the_user_free() = runTest(dispatcher) {
        // §9, screen 18: purchase failed.
        val model = PaywallViewModel(FakeEntitlementRepository(purchaseSucceeds = false))
        advanceUntilIdle()

        model.onPurchase(model.state.value.plans.first())
        advanceUntilIdle()

        assertEquals(PaywallNotice.PURCHASE_FAILED, model.state.value.notice)
        assertFalse(model.state.value.subscribed)
        assertFalse(model.state.value.busy)
    }

    @Test
    fun a_purchase_that_throws_is_a_failed_purchase_not_a_crash() = runTest(dispatcher) {
        // Review Focus 2: the store could not be reached at all.
        val model = PaywallViewModel(Scripted { throw IllegalStateException("store unreachable") })
        advanceUntilIdle()

        model.onPurchase(model.state.value.plans.first())
        advanceUntilIdle()

        assertEquals(PaywallNotice.PURCHASE_FAILED, model.state.value.notice)
        assertFalse(model.state.value.busy)
    }

    @Test
    fun a_cancelled_purchase_says_nothing() = runTest(dispatcher) {
        // Decision 3: the user closed the sheet; they know.
        val model = PaywallViewModel(Scripted { PurchaseOutcome.Cancelled })
        advanceUntilIdle()

        model.onPurchase(model.state.value.plans.first())
        advanceUntilIdle()

        assertNull(model.state.value.notice)
        assertFalse(model.state.value.busy)
    }

    @Test
    fun pressing_buy_twice_buys_once() = runTest(dispatcher) {
        // Review Focus 1.
        val repository = Scripted { PurchaseOutcome.Cancelled }
        val model = PaywallViewModel(repository)
        advanceUntilIdle()
        val plan = model.state.value.plans.first()

        model.onPurchase(plan)
        model.onPurchase(plan)
        model.onPurchase(model.state.value.plans.last())
        advanceUntilIdle()

        assertEquals(1, repository.purchases)
    }

    @Test
    fun a_failed_purchase_can_be_tried_again_and_the_notice_goes_when_it_is() = runTest(dispatcher) {
        var fail = true
        val real = FakeEntitlementRepository()
        val repository = object : EntitlementRepository by real {
            override suspend fun purchase(plan: SubscriptionPlan): PurchaseOutcome =
                if (fail) PurchaseOutcome.Failed("declined") else real.purchase(plan)
        }
        val model = PaywallViewModel(repository)
        advanceUntilIdle()
        model.onPurchase(model.state.value.plans.first())
        advanceUntilIdle()
        assertEquals(PaywallNotice.PURCHASE_FAILED, model.state.value.notice)

        fail = false
        model.onPurchase(model.state.value.plans.first())
        advanceUntilIdle()

        assertNull(model.state.value.notice)
        assertTrue(model.state.value.subscribed)
    }

    @Test
    fun restoring_with_nothing_to_restore_says_so() = runTest(dispatcher) {
        // Decision 4. The fake's restore succeeds with whatever is held, which for a free
        // user is nothing.
        val model = PaywallViewModel(FakeEntitlementRepository())
        advanceUntilIdle()

        model.onRestore()
        advanceUntilIdle()

        assertEquals(PaywallNotice.NOTHING_TO_RESTORE, model.state.value.notice)
        assertFalse(model.state.value.restoring)
    }

    @Test
    fun plans_that_cannot_be_loaded_are_a_failure_that_can_be_retried() = runTest(dispatcher) {
        var broken = true
        val real = FakeEntitlementRepository()
        val repository = object : EntitlementRepository by real {
            override suspend fun plans() = if (broken) throw IllegalStateException("no plans") else real.plans()
        }
        val model = PaywallViewModel(repository)
        advanceUntilIdle()
        assertEquals(PaywallNotice.PLANS_FAILED, model.state.value.notice)

        broken = false
        model.onRetry()
        advanceUntilIdle()

        assertNull(model.state.value.notice)
        assertEquals(2, model.state.value.plans.size)
    }

    @Test
    fun there_is_no_paywall_where_the_repository_refuses() {
        // All-screens spec §6: a release build must never present a purchase that cannot happen.
        assertFalse(commerceIsBuilt(NotBuiltEntitlementRepository))
        assertTrue(commerceIsBuilt(FakeEntitlementRepository()))
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :shared:feature-commerce:allTests`
Expected: compilation FAILS with `Unresolved reference 'PaywallViewModel'`.

- [ ] **Step 3: Implement**

`.../commerce/PaywallViewModel.kt`:

```kotlin
package com.ptk.anatomypro.feature.commerce

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptk.anatomypro.core.data.NotBuiltEntitlementRepository
import com.ptk.anatomypro.core.data.model.PurchaseOutcome
import com.ptk.anatomypro.core.data.model.SubscriptionPlan
import com.ptk.anatomypro.core.data.repository.EntitlementRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the paywall has to tell the user, as a kind. The screen supplies the words. */
enum class PaywallNotice { PURCHASE_FAILED, NOTHING_TO_RESTORE, PLANS_FAILED }

data class PaywallUiState(
    val subscribed: Boolean = false,
    val plans: List<SubscriptionPlan> = emptyList(),
    val isLoading: Boolean = true,
    /** The id of the plan being bought, or null. */
    val purchasing: String? = null,
    val restoring: Boolean = false,
    val notice: PaywallNotice? = null,
) {
    /** A purchase or a restore is under way: nothing else may be started. */
    val busy: Boolean get() = purchasing != null || restoring
}

/**
 * Prototype screen 18: the paywall.
 *
 * What is sold is the learning system — quizzes beyond the skeleton, the daily quiz, the
 * leaderboard — and never content: the whole atlas is free (design spec §29).
 *
 * A repository's own failure message is not passed on. It is written for a log, in whatever
 * language the store uses; the screen says what happened in its own words.
 */
class PaywallViewModel(private val entitlements: EntitlementRepository) : ViewModel() {

    private val _state = MutableStateFlow(PaywallUiState())
    val state: StateFlow<PaywallUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            entitlements.entitlements.collect { current -> _state.update { it.copy(subscribed = current.subscribed) } }
        }
        loadPlans()
    }

    fun onPurchase(plan: SubscriptionPlan) {
        if (_state.value.busy || _state.value.subscribed) return
        _state.update { it.copy(purchasing = plan.id, notice = null) }
        viewModelScope.launch {
            val notice = try {
                when (entitlements.purchase(plan)) {
                    is PurchaseOutcome.Succeeded -> null
                    // The user closed the sheet. They know; saying so would be noise.
                    PurchaseOutcome.Cancelled -> null
                    is PurchaseOutcome.Failed -> PaywallNotice.PURCHASE_FAILED
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                PaywallNotice.PURCHASE_FAILED
            }
            _state.update { it.copy(purchasing = null, notice = notice) }
        }
    }

    fun onRestore() {
        if (_state.value.busy) return
        _state.update { it.copy(restoring = true, notice = null) }
        viewModelScope.launch {
            val notice = try {
                val outcome = entitlements.restore()
                val restored = outcome is PurchaseOutcome.Succeeded && outcome.entitlements.subscribed
                // A restore that finds nothing must say so, or it reads as a dead button.
                if (restored) null else PaywallNotice.NOTHING_TO_RESTORE
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                PaywallNotice.NOTHING_TO_RESTORE
            }
            _state.update { it.copy(restoring = false, notice = notice) }
        }
    }

    fun onRetry() {
        if (_state.value.isLoading) return
        loadPlans()
    }

    private fun loadPlans() {
        _state.update { it.copy(isLoading = true, notice = null) }
        viewModelScope.launch {
            try {
                val plans = entitlements.plans()
                _state.update { it.copy(plans = plans, isLoading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, notice = PaywallNotice.PLANS_FAILED) }
            }
        }
    }
}

/**
 * Whether there is anything to buy.
 *
 * Production has no EntitlementRepository yet; the one it is given throws on first use, by
 * design (all-screens spec §6) — a release build must never present a purchase that cannot
 * happen. There the paywall is a placeholder.
 */
fun commerceIsBuilt(entitlements: EntitlementRepository): Boolean = entitlements !is NotBuiltEntitlementRepository
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :shared:feature-commerce:allTests`
Expected: BUILD SUCCESSFUL, eleven tests passing per target.

- [ ] **Step 5: Commit**

```bash
git add shared/feature-commerce
git commit -m "feat(commerce): the paywall's three states, one purchase at a time"
```

---

### Task 5: Screen 19's ViewModel

**Files:**
- Create: `.../profile/PacksViewModel.kt`
- Test: `shared/feature-profile/src/commonTest/kotlin/com/ptk/anatomypro/feature/profile/PacksViewModelTest.kt`

**Interfaces:**
- Consumes: `PackRepository.packs / download / cancel / delete`, `PackState`, `PackStatus`, `PackFailure`, `FakePackRepository(behaviour, downloadFailure)`.
- Produces:
  ```kotlin
  data class PacksUiState(
      val packs: List<PackState> = emptyList(),
      /** The pack whose deletion is waiting for a yes or a no. */
      val confirmingDelete: PackId? = null,
      val isLoading: Boolean = true,
      val failed: Boolean = false,
  ) { val installedBytes: Long }
  class PacksViewModel(packs: PackRepository) : ViewModel() {
      val state: StateFlow<PacksUiState>
      fun onDownload(id: PackId)
      fun onCancel(id: PackId)
      fun onDeleteRequested(id: PackId)
      fun onDeleteConfirmed()
      fun onDeleteDismissed()
  }
  ```

- [ ] **Step 1: Write the failing tests**

`PacksViewModelTest.kt`:

```kotlin
package com.ptk.anatomypro.feature.profile

import com.ptk.anatomypro.core.data.fake.FakePackRepository
import com.ptk.anatomypro.core.data.model.PackFailure
import com.ptk.anatomypro.core.data.model.PackState
import com.ptk.anatomypro.core.data.model.PackStatus
import com.ptk.anatomypro.core.data.repository.PackRepository
import com.ptk.anatomypro.core.model.PackId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PacksViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val skeletal = PackId("skeletal-body")
    private val muscular = PackId("muscular-body")

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun PacksUiState.status(id: PackId): PackStatus = packs.first { it.id == id }.status

    @Test
    fun installed_and_available_packs_are_both_listed() = runTest(dispatcher) {
        // §9, screen 19: installed; available.
        val model = PacksViewModel(FakePackRepository())
        advanceUntilIdle()

        assertEquals(3, model.state.value.packs.size)
        assertEquals(PackStatus.Installed, model.state.value.status(skeletal))
        assertEquals(PackStatus.Available, model.state.value.status(muscular))
        assertFalse(model.state.value.isLoading)
    }

    @Test
    fun what_is_installed_is_totalled() = runTest(dispatcher) {
        val model = PacksViewModel(FakePackRepository())
        advanceUntilIdle()

        assertEquals(48_300_000L, model.state.value.installedBytes)
    }

    @Test
    fun a_download_is_seen_in_progress_and_ends_installed() = runTest(dispatcher) {
        // §9, screen 19: downloading.
        val model = PacksViewModel(FakePackRepository())
        advanceUntilIdle()
        val seen = mutableListOf<PackStatus>()
        val watching = backgroundScope.launchWatching(model, muscular, seen)

        model.onDownload(muscular)
        advanceUntilIdle()
        watching.cancel()

        assertTrue(seen.any { it is PackStatus.Downloading }, "no progress was seen: $seen")
        assertEquals(PackStatus.Installed, model.state.value.status(muscular))
    }

    @Test
    fun a_download_can_be_cancelled_and_the_pack_is_available_again() = runTest(dispatcher) {
        val model = PacksViewModel(FakePackRepository())
        advanceUntilIdle()

        model.onDownload(muscular)
        runCurrent()
        model.onCancel(muscular)
        advanceUntilIdle()

        // The fake's download has no real duration, so this proves the request reaches the
        // repository and its answer reaches the screen — not that a transfer was interrupted.
        // FakeCommerceTest covers stopping one half way.
        assertEquals(PackStatus.Available, model.state.value.status(muscular))
    }

    @Test
    fun deleting_asks_first_and_deletes_nothing_until_it_is_confirmed() = runTest(dispatcher) {
        // §9, screen 19: delete confirm. §12: a destructive action is confirmed.
        val model = PacksViewModel(FakePackRepository())
        advanceUntilIdle()

        model.onDeleteRequested(skeletal)
        advanceUntilIdle()

        assertEquals(skeletal, model.state.value.confirmingDelete)
        assertEquals(PackStatus.Installed, model.state.value.status(skeletal))
    }

    @Test
    fun confirming_deletes_the_pack_that_was_asked_about() = runTest(dispatcher) {
        val model = PacksViewModel(FakePackRepository())
        advanceUntilIdle()
        model.onDeleteRequested(skeletal)

        model.onDeleteConfirmed()
        advanceUntilIdle()

        assertEquals(PackStatus.Available, model.state.value.status(skeletal))
        assertNull(model.state.value.confirmingDelete)
    }

    @Test
    fun dismissing_keeps_the_pack() = runTest(dispatcher) {
        val model = PacksViewModel(FakePackRepository())
        advanceUntilIdle()
        model.onDeleteRequested(skeletal)

        model.onDeleteDismissed()
        advanceUntilIdle()

        assertEquals(PackStatus.Installed, model.state.value.status(skeletal))
        assertNull(model.state.value.confirmingDelete)
    }

    @Test
    fun only_an_installed_pack_can_be_asked_about() = runTest(dispatcher) {
        val model = PacksViewModel(FakePackRepository())
        advanceUntilIdle()

        model.onDeleteRequested(muscular)

        assertNull(model.state.value.confirmingDelete)
    }

    @Test
    fun a_confirmation_is_dropped_when_its_pack_is_no_longer_there_to_delete() = runTest(dispatcher) {
        // Review Focus 3. The list changes under the question.
        val listed = MutableStateFlow(
            listOf(PackState(skeletal, 1, "Układ kostny", 48_300_000, "sha256:2f1c0a", entitlement = null, status = PackStatus.Installed)),
        )
        val repository = object : PackRepository {
            override val packs: Flow<List<PackState>> = listed
            override suspend fun download(id: PackId) = Unit
            override suspend fun cancel(id: PackId) = Unit
            override suspend fun delete(id: PackId) = Unit
        }
        val model = PacksViewModel(repository)
        advanceUntilIdle()
        model.onDeleteRequested(skeletal)

        listed.value = emptyList()
        advanceUntilIdle()

        assertNull(model.state.value.confirmingDelete)
    }

    @Test
    fun a_download_that_fails_is_shown_as_failed_and_can_be_tried_again() = runTest(dispatcher) {
        // Review Focus 4.
        val model = PacksViewModel(FakePackRepository(downloadFailure = PackFailure.NETWORK, failingAttempts = 1))
        advanceUntilIdle()

        model.onDownload(muscular)
        advanceUntilIdle()
        val failed = assertIs<PackStatus.Failed>(model.state.value.status(muscular))
        assertTrue(failed.resumable)

        model.onDownload(muscular)
        advanceUntilIdle()
        assertEquals(PackStatus.Installed, model.state.value.status(muscular))
    }

    @Test
    fun a_list_that_cannot_be_read_is_a_failure_not_an_empty_manager() = runTest(dispatcher) {
        val repository = object : PackRepository {
            override val packs: Flow<List<PackState>> = flow { throw IllegalStateException("no list") }
            override suspend fun download(id: PackId) = Unit
            override suspend fun cancel(id: PackId) = Unit
            override suspend fun delete(id: PackId) = Unit
        }

        val model = PacksViewModel(repository)
        advanceUntilIdle()

        assertTrue(model.state.value.failed)
        assertFalse(model.state.value.isLoading)
    }

    @Test
    fun an_action_that_throws_does_not_take_the_manager_down() = runTest(dispatcher) {
        val real = FakePackRepository()
        val repository = object : PackRepository by real {
            override suspend fun download(id: PackId) = throw IllegalStateException("offline")
        }
        val model = PacksViewModel(repository)
        advanceUntilIdle()

        model.onDownload(muscular)
        advanceUntilIdle()

        assertEquals(3, model.state.value.packs.size)
        assertFalse(model.state.value.failed)
    }
}

/** Records every status [pack] passes through in [model]'s state. */
private fun CoroutineScope.launchWatching(
    model: PacksViewModel,
    pack: PackId,
    into: MutableList<PackStatus>,
) = launch {
    model.state.collect { state -> state.packs.firstOrNull { it.id == pack }?.let { into += it.status } }
}
```

`a_download_that_fails…` uses `FakePackRepository(failingAttempts = 1)`, which the onboarding plan adds. **If that plan has not been executed**, add the parameter here first, exactly as its Task 1 describes (`failingAttempts: Int = Int.MAX_VALUE`, a `downloadRequests` counter, and `spoiled = downloadFailure != null && downloadRequests <= failingAttempts`), with its three tests.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :shared:feature-profile:allTests`
Expected: compilation FAILS with `Unresolved reference 'PacksViewModel'`.

- [ ] **Step 3: Implement**

`.../profile/PacksViewModel.kt`:

```kotlin
package com.ptk.anatomypro.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptk.anatomypro.core.data.model.PackState
import com.ptk.anatomypro.core.data.model.PackStatus
import com.ptk.anatomypro.core.data.repository.PackRepository
import com.ptk.anatomypro.core.model.PackId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PacksUiState(
    val packs: List<PackState> = emptyList(),
    /** The pack whose deletion is waiting for a yes or a no. */
    val confirmingDelete: PackId? = null,
    val isLoading: Boolean = true,
    val failed: Boolean = false,
) {
    val installedBytes: Long get() = packs.filter { it.status == PackStatus.Installed }.sumOf { it.byteSize }
}

/**
 * Prototype screen 19: the pack manager.
 *
 * Nothing here is ever locked: packs are free (all-screens spec §15.2). The repository owns
 * every download; this asks, and shows what the repository reports.
 */
class PacksViewModel(private val packs: PackRepository) : ViewModel() {

    private val _state = MutableStateFlow(PacksUiState())
    val state: StateFlow<PacksUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                packs.packs.collect { all ->
                    _state.update { state ->
                        // A confirmation is for an installed pack. If the pack has gone, or is
                        // no longer installed, there is nothing left to say yes to.
                        val stillThere = all.any { it.id == state.confirmingDelete && it.status == PackStatus.Installed }
                        state.copy(
                            packs = all,
                            confirmingDelete = state.confirmingDelete.takeIf { stillThere },
                            isLoading = false,
                            failed = false,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, failed = true) }
            }
        }
    }

    fun onDownload(id: PackId) = act { packs.download(id) }

    fun onCancel(id: PackId) = act { packs.cancel(id) }

    fun onDeleteRequested(id: PackId) {
        val installed = _state.value.packs.any { it.id == id && it.status == PackStatus.Installed }
        if (installed) _state.update { it.copy(confirmingDelete = id) }
    }

    fun onDeleteConfirmed() {
        val id = _state.value.confirmingDelete ?: return
        _state.update { it.copy(confirmingDelete = null) }
        act { packs.delete(id) }
    }

    fun onDeleteDismissed() = _state.update { it.copy(confirmingDelete = null) }

    /**
     * Runs one request. A request that throws is dropped: what happened to the pack is the
     * repository's to report through its status, and an exception here is not that.
     */
    private fun act(request: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                request()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :shared:feature-profile:allTests`
Expected: BUILD SUCCESSFUL; `PacksViewModelTest` twelve passing per target, `ProfileViewModelTest` unchanged.

- [ ] **Step 5: Commit**

```bash
git add shared/feature-profile shared/core-data-fake
git commit -m "feat(profile): the pack manager asks before it deletes"
```

---

### Task 6: Screens 17, 18 and 19

No tests: Compose UI tests are out of scope. They are looked at in Task 7.

**Files:**
- Create: `shared/feature-profile/src/commonMain/composeResources/values/strings.xml`, `values-pl/strings.xml`
- Create: `shared/feature-commerce/src/commonMain/composeResources/values/strings.xml`, `values-pl/strings.xml`
- Create: `.../profile/ProfileScreen.kt`, `.../profile/PacksScreen.kt`, `.../commerce/PaywallScreen.kt`

**Interfaces:**
- Consumes: `ProfileUiState`, `CalendarDay`, `PacksUiState`, `PaywallUiState`, `PaywallNotice` (Tasks 3–5); `megabytes` (Task 1).
- Produces:
  ```kotlin
  @Composable fun ProfileScreen(state: ProfileUiState, subscribed: Boolean, onRetry: () -> Unit, onSettings: () -> Unit, onPacks: () -> Unit, onSubscription: () -> Unit, modifier: Modifier = Modifier)
  @Composable fun PacksScreen(state: PacksUiState, onDownload: (PackId) -> Unit, onCancel: (PackId) -> Unit, onDeleteRequested: (PackId) -> Unit, onDeleteConfirmed: () -> Unit, onDeleteDismissed: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier)
  @Composable fun PaywallScreen(state: PaywallUiState, onPurchase: (SubscriptionPlan) -> Unit, onRestore: () -> Unit, onRetry: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier)
  ```

- [ ] **Step 1: Write the profile module's strings**

`shared/feature-profile/.../values/strings.xml`:

```xml
<resources>
    <string name="decimal_separator">.</string>
    <string name="profile_title">Profile</string>
    <string name="profile_loading">Loading your progress…</string>
    <string name="profile_failed">Your progress could not be read.</string>
    <string name="profile_retry">Try again</string>
    <string name="profile_streak">%1$d day streak · longest %2$d</string>
    <string name="profile_streak_none">No streak yet.</string>
    <string name="profile_calendar_heading">LAST FIVE WEEKS</string>
    <string name="profile_calendar_legend">● studied   ◆ studied and daily quiz   · nothing</string>
    <string name="profile_day_studied">%1$s: studied</string>
    <string name="profile_day_daily">%1$s: studied, daily quiz done</string>
    <string name="profile_day_nothing">%1$s: nothing</string>
    <string name="profile_mastery_heading">BY SYSTEM</string>
    <string name="profile_mastery">%1$d of %2$d structures seen</string>
    <string name="profile_settings">Settings</string>
    <string name="profile_packs">Packs · %1$d installed, %2$s MB</string>
    <string name="profile_subscription_free">Subscription · not subscribed</string>
    <string name="profile_subscription_active">Subscription · active</string>
    <string name="packs_title">Packs</string>
    <string name="packs_back">Back</string>
    <string name="packs_loading">Loading packs…</string>
    <string name="packs_failed">The list of packs could not be read.</string>
    <string name="packs_total">%1$s MB installed</string>
    <string name="packs_size">%1$s MB</string>
    <string name="packs_installed">Installed</string>
    <string name="packs_available">Not downloaded</string>
    <string name="packs_queued">Waiting to start…</string>
    <string name="packs_downloading">%1$s of %2$s MB · %3$d %</string>
    <string name="packs_failed_network">The connection dropped. What was downloaded is kept.</string>
    <string name="packs_failed_checksum">The file arrived damaged.</string>
    <string name="packs_failed_space">Not enough free space on this device.</string>
    <string name="packs_failed_other">The download failed.</string>
    <string name="packs_download">Download</string>
    <string name="packs_resume">Resume</string>
    <string name="packs_restart">Start again</string>
    <string name="packs_cancel">Cancel</string>
    <string name="packs_delete">Delete</string>
    <string name="packs_delete_confirm">Delete this pack? It can be downloaded again.</string>
    <string name="packs_delete_yes">Delete</string>
    <string name="packs_delete_no">Keep</string>
</resources>
```

`values-pl/strings.xml`:

```xml
<resources>
    <string name="decimal_separator">,</string>
    <string name="profile_title">Profil</string>
    <string name="profile_loading">Wczytywanie postępów…</string>
    <string name="profile_failed">Nie udało się odczytać postępów.</string>
    <string name="profile_retry">Spróbuj ponownie</string>
    <string name="profile_streak">Dni z rzędu: %1$d · najdłużej: %2$d</string>
    <string name="profile_streak_none">Nie masz jeszcze serii.</string>
    <string name="profile_calendar_heading">OSTATNIE PIĘĆ TYGODNI</string>
    <string name="profile_calendar_legend">● nauka   ◆ nauka i quiz dnia   · nic</string>
    <string name="profile_day_studied">%1$s: nauka</string>
    <string name="profile_day_daily">%1$s: nauka, quiz dnia zaliczony</string>
    <string name="profile_day_nothing">%1$s: nic</string>
    <string name="profile_mastery_heading">WEDŁUG UKŁADÓW</string>
    <string name="profile_mastery">Poznane struktury: %1$d z %2$d</string>
    <string name="profile_settings">Ustawienia</string>
    <string name="profile_packs">Pakiety · zainstalowane: %1$d, %2$s MB</string>
    <string name="profile_subscription_free">Subskrypcja · brak</string>
    <string name="profile_subscription_active">Subskrypcja · aktywna</string>
    <string name="packs_title">Pakiety</string>
    <string name="packs_back">Wstecz</string>
    <string name="packs_loading">Wczytywanie pakietów…</string>
    <string name="packs_failed">Nie udało się odczytać listy pakietów.</string>
    <string name="packs_total">Zainstalowano %1$s MB</string>
    <string name="packs_size">%1$s MB</string>
    <string name="packs_installed">Zainstalowany</string>
    <string name="packs_available">Niepobrany</string>
    <string name="packs_queued">Oczekiwanie na rozpoczęcie…</string>
    <string name="packs_downloading">%1$s z %2$s MB · %3$d %</string>
    <string name="packs_failed_network">Połączenie zostało przerwane. Pobrana część została zachowana.</string>
    <string name="packs_failed_checksum">Plik dotarł uszkodzony.</string>
    <string name="packs_failed_space">Na urządzeniu brakuje wolnego miejsca.</string>
    <string name="packs_failed_other">Pobieranie nie powiodło się.</string>
    <string name="packs_download">Pobierz</string>
    <string name="packs_resume">Wznów</string>
    <string name="packs_restart">Zacznij od nowa</string>
    <string name="packs_cancel">Anuluj</string>
    <string name="packs_delete">Usuń</string>
    <string name="packs_delete_confirm">Usunąć ten pakiet? Można go pobrać ponownie.</string>
    <string name="packs_delete_yes">Usuń</string>
    <string name="packs_delete_no">Zostaw</string>
</resources>
```

A literal percent sign is a single `%` (design spec §27.5).

- [ ] **Step 2: Write the commerce module's strings**

`shared/feature-commerce/.../values/strings.xml`:

```xml
<resources>
    <string name="paywall_back">Back</string>
    <string name="paywall_title">Subscription</string>
    <string name="paywall_body">The whole atlas is free, and so are quizzes on the skeleton. A subscription adds the rest of the learning:</string>
    <string name="paywall_benefit_topics">Quizzes on every other system</string>
    <string name="paywall_benefit_daily">The daily quiz, the same for everyone</string>
    <string name="paywall_benefit_board">Your place in the ranking</string>
    <string name="paywall_loading">Loading the plans…</string>
    <string name="paywall_plan">%1$s · %2$s</string>
    <string name="paywall_buying">Buying…</string>
    <string name="paywall_restore">Restore a purchase</string>
    <string name="paywall_restoring">Looking for a purchase…</string>
    <string name="paywall_subscribed_title">You are subscribed</string>
    <string name="paywall_subscribed_body">Every quiz, the daily quiz and the ranking are open.</string>
    <string name="paywall_notice_purchase_failed">The purchase did not go through. You have not been charged by this app.</string>
    <string name="paywall_notice_nothing_to_restore">No earlier purchase was found for this account.</string>
    <string name="paywall_notice_plans_failed">The plans could not be loaded.</string>
    <string name="paywall_retry">Try again</string>
</resources>
```

`values-pl/strings.xml`:

```xml
<resources>
    <string name="paywall_back">Wstecz</string>
    <string name="paywall_title">Subskrypcja</string>
    <string name="paywall_body">Cały atlas jest darmowy, podobnie jak quizy z układu kostnego. Subskrypcja dodaje resztę nauki:</string>
    <string name="paywall_benefit_topics">Quizy ze wszystkich pozostałych układów</string>
    <string name="paywall_benefit_daily">Quiz dnia, taki sam dla wszystkich</string>
    <string name="paywall_benefit_board">Twoje miejsce w rankingu</string>
    <string name="paywall_loading">Wczytywanie planów…</string>
    <string name="paywall_plan">%1$s · %2$s</string>
    <string name="paywall_buying">Kupowanie…</string>
    <string name="paywall_restore">Przywróć zakup</string>
    <string name="paywall_restoring">Szukanie zakupu…</string>
    <string name="paywall_subscribed_title">Masz subskrypcję</string>
    <string name="paywall_subscribed_body">Wszystkie quizy, quiz dnia i ranking są otwarte.</string>
    <string name="paywall_notice_purchase_failed">Zakup się nie powiódł. Ta aplikacja nie pobrała opłaty.</string>
    <string name="paywall_notice_nothing_to_restore">Nie znaleziono wcześniejszego zakupu dla tego konta.</string>
    <string name="paywall_notice_plans_failed">Nie udało się wczytać planów.</string>
    <string name="paywall_retry">Spróbuj ponownie</string>
</resources>
```

`paywall_notice_purchase_failed` says the app has not charged. That is a claim about billing which the fake cannot test and Phase 4 must make true, or the sentence must change. Note it in Task 7's record.

- [ ] **Step 3: Write screen 17**

`.../profile/ProfileScreen.kt`:

```kotlin
package com.ptk.anatomypro.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import anatomypro.shared.feature_profile.generated.resources.Res
import anatomypro.shared.feature_profile.generated.resources.decimal_separator
import anatomypro.shared.feature_profile.generated.resources.profile_calendar_heading
import anatomypro.shared.feature_profile.generated.resources.profile_calendar_legend
import anatomypro.shared.feature_profile.generated.resources.profile_day_daily
import anatomypro.shared.feature_profile.generated.resources.profile_day_nothing
import anatomypro.shared.feature_profile.generated.resources.profile_day_studied
import anatomypro.shared.feature_profile.generated.resources.profile_failed
import anatomypro.shared.feature_profile.generated.resources.profile_loading
import anatomypro.shared.feature_profile.generated.resources.profile_mastery
import anatomypro.shared.feature_profile.generated.resources.profile_mastery_heading
import anatomypro.shared.feature_profile.generated.resources.profile_packs
import anatomypro.shared.feature_profile.generated.resources.profile_retry
import anatomypro.shared.feature_profile.generated.resources.profile_settings
import anatomypro.shared.feature_profile.generated.resources.profile_streak
import anatomypro.shared.feature_profile.generated.resources.profile_streak_none
import anatomypro.shared.feature_profile.generated.resources.profile_subscription_active
import anatomypro.shared.feature_profile.generated.resources.profile_subscription_free
import anatomypro.shared.feature_profile.generated.resources.profile_title
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.Hairline
import com.ptk.anatomypro.core.designsystem.TextTertiary
import com.ptk.anatomypro.core.designsystem.megabytes
import org.jetbrains.compose.resources.stringResource

/**
 * Prototype screen 17: the profile.
 *
 * A day is a glyph — ● studied, ◆ studied and the daily done, · nothing — with a sentence
 * for a screen reader, so the calendar says the same thing without colour (§12).
 */
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    subscribed: Boolean,
    onRetry: () -> Unit,
    onSettings: () -> Unit,
    onPacks: () -> Unit,
    onSubscription: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(stringResource(Res.string.profile_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

        val streak = state.streak
        if (streak != null) {
            Text(
                if (streak.currentDays == 0 && streak.longestDays == 0) stringResource(Res.string.profile_streak_none)
                else stringResource(Res.string.profile_streak, streak.currentDays, streak.longestDays),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        when {
            state.isLoading ->
                Text(stringResource(Res.string.profile_loading), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)

            state.failed -> {
                Text(stringResource(Res.string.profile_failed), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(Res.string.profile_retry))
                }
            }

            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(Res.string.profile_calendar_heading), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    for (week in state.calendar.chunked(7)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (day in week) DayCell(day, Modifier.weight(1f))
                        }
                    }
                    Text(stringResource(Res.string.profile_calendar_legend), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(Res.string.profile_mastery_heading), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    for (system in state.mastery) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(system.title, style = MaterialTheme.typography.bodyMedium)
                            LinearProgressIndicator(
                                progress = { system.fraction },
                                modifier = Modifier.fillMaxWidth(),
                                color = Accent,
                                trackColor = Hairline,
                            )
                            Text(
                                stringResource(Res.string.profile_mastery, system.structuresSeen, system.structuresTotal),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary,
                            )
                        }
                    }
                }
            }
        }

        Column {
            Link(stringResource(Res.string.profile_settings), onSettings)
            Link(
                stringResource(
                    Res.string.profile_packs,
                    state.installedPacks,
                    megabytes(state.installedBytes, stringResource(Res.string.decimal_separator)),
                ),
                onPacks,
            )
            Link(
                stringResource(if (subscribed) Res.string.profile_subscription_active else Res.string.profile_subscription_free),
                onSubscription,
            )
        }
    }
}

@Composable
private fun DayCell(day: CalendarDay, modifier: Modifier) {
    val activity = day.activity
    val studied = activity != null && activity.sessions > 0
    val glyph = when {
        studied && activity?.dailyCompleted == true -> "◆"
        studied -> "●"
        else -> "·"
    }
    val date = day.date.toString()
    val description = when {
        studied && activity?.dailyCompleted == true -> stringResource(Res.string.profile_day_daily, date)
        studied -> stringResource(Res.string.profile_day_studied, date)
        else -> stringResource(Res.string.profile_day_nothing, date)
    }
    Text(
        glyph,
        style = MaterialTheme.typography.bodyMedium,
        color = if (studied) Accent else TextTertiary,
        textAlign = TextAlign.Center,
        modifier = modifier.padding(vertical = 4.dp).clearAndSetSemantics { contentDescription = description },
    )
}

@Composable
private fun Link(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(top = 14.dp),
    )
}
```

A day's date is read out as its ISO form (`2026-09-22`). A spoken date in the interface language needs a date formatter that common code does not have; note it in Task 7's record rather than building one here.

- [ ] **Step 4: Write screen 19**

`.../profile/PacksScreen.kt`:

```kotlin
package com.ptk.anatomypro.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import anatomypro.shared.feature_profile.generated.resources.Res
import anatomypro.shared.feature_profile.generated.resources.decimal_separator
import anatomypro.shared.feature_profile.generated.resources.packs_available
import anatomypro.shared.feature_profile.generated.resources.packs_back
import anatomypro.shared.feature_profile.generated.resources.packs_cancel
import anatomypro.shared.feature_profile.generated.resources.packs_delete
import anatomypro.shared.feature_profile.generated.resources.packs_delete_confirm
import anatomypro.shared.feature_profile.generated.resources.packs_delete_no
import anatomypro.shared.feature_profile.generated.resources.packs_delete_yes
import anatomypro.shared.feature_profile.generated.resources.packs_download
import anatomypro.shared.feature_profile.generated.resources.packs_downloading
import anatomypro.shared.feature_profile.generated.resources.packs_failed
import anatomypro.shared.feature_profile.generated.resources.packs_failed_checksum
import anatomypro.shared.feature_profile.generated.resources.packs_failed_network
import anatomypro.shared.feature_profile.generated.resources.packs_failed_other
import anatomypro.shared.feature_profile.generated.resources.packs_failed_space
import anatomypro.shared.feature_profile.generated.resources.packs_installed
import anatomypro.shared.feature_profile.generated.resources.packs_loading
import anatomypro.shared.feature_profile.generated.resources.packs_queued
import anatomypro.shared.feature_profile.generated.resources.packs_resume
import anatomypro.shared.feature_profile.generated.resources.packs_restart
import anatomypro.shared.feature_profile.generated.resources.packs_size
import anatomypro.shared.feature_profile.generated.resources.packs_title
import anatomypro.shared.feature_profile.generated.resources.packs_total
import com.ptk.anatomypro.core.data.model.PackFailure
import com.ptk.anatomypro.core.data.model.PackState
import com.ptk.anatomypro.core.data.model.PackStatus
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.Hairline
import com.ptk.anatomypro.core.designsystem.IncorrectAmber
import com.ptk.anatomypro.core.designsystem.TextTertiary
import com.ptk.anatomypro.core.designsystem.megabytes
import com.ptk.anatomypro.core.model.PackId
import org.jetbrains.compose.resources.stringResource

/**
 * Prototype screen 19: the pack manager.
 *
 * Every row says its state in words. Nothing is ever locked. Deleting is asked about on the
 * row itself, so the question sits beside the thing it is about.
 */
@Composable
fun PacksScreen(
    state: PacksUiState,
    onDownload: (PackId) -> Unit,
    onCancel: (PackId) -> Unit,
    onDeleteRequested: (PackId) -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteDismissed: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val separator = stringResource(Res.string.decimal_separator)

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(Res.string.packs_back),
            style = MaterialTheme.typography.labelSmall,
            color = Accent,
            modifier = Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onBack).padding(top = 14.dp),
        )
        Text(stringResource(Res.string.packs_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

        when {
            state.isLoading ->
                Text(stringResource(Res.string.packs_loading), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)

            state.failed ->
                Text(stringResource(Res.string.packs_failed), style = MaterialTheme.typography.bodyMedium)

            else -> {
                Text(
                    stringResource(Res.string.packs_total, megabytes(state.installedBytes, separator)),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    for (pack in state.packs) {
                        PackRow(
                            pack = pack,
                            separator = separator,
                            confirming = state.confirmingDelete == pack.id,
                            onDownload = { onDownload(pack.id) },
                            onCancel = { onCancel(pack.id) },
                            onDeleteRequested = { onDeleteRequested(pack.id) },
                            onDeleteConfirmed = onDeleteConfirmed,
                            onDeleteDismissed = onDeleteDismissed,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PackRow(
    pack: PackState,
    separator: String,
    confirming: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDeleteRequested: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteDismissed: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(pack.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(Res.string.packs_size, megabytes(pack.byteSize, separator)),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
        }

        when (val status = pack.status) {
            PackStatus.Installed -> {
                if (confirming) {
                    Text(stringResource(Res.string.packs_delete_confirm), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Action(stringResource(Res.string.packs_delete_yes), onDeleteConfirmed)
                        Action(stringResource(Res.string.packs_delete_no), onDeleteDismissed)
                    }
                } else {
                    Text(stringResource(Res.string.packs_installed), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    Action(stringResource(Res.string.packs_delete), onDeleteRequested)
                }
            }

            PackStatus.Available -> {
                Text(stringResource(Res.string.packs_available), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                Action(stringResource(Res.string.packs_download), onDownload)
            }

            PackStatus.Queued -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Accent, trackColor = Hairline)
                Text(stringResource(Res.string.packs_queued), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                Action(stringResource(Res.string.packs_cancel), onCancel)
            }

            is PackStatus.Downloading -> {
                LinearProgressIndicator(
                    progress = { status.fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = Accent,
                    trackColor = Hairline,
                )
                Text(
                    stringResource(
                        Res.string.packs_downloading,
                        megabytes(status.bytesDone, separator),
                        megabytes(status.bytesTotal, separator),
                        (status.fraction * 100).toInt(),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
                Action(stringResource(Res.string.packs_cancel), onCancel)
            }

            is PackStatus.Failed -> {
                val message = when (status.reason) {
                    PackFailure.NETWORK -> Res.string.packs_failed_network
                    PackFailure.CHECKSUM -> Res.string.packs_failed_checksum
                    PackFailure.OUT_OF_SPACE -> Res.string.packs_failed_space
                    // Cannot occur: packs carry no entitlement (all-screens spec §15.2).
                    PackFailure.NOT_ENTITLED -> Res.string.packs_failed_other
                }
                Text(stringResource(message), style = MaterialTheme.typography.bodyMedium, color = IncorrectAmber)
                Action(stringResource(if (status.resumable) Res.string.packs_resume else Res.string.packs_restart), onDownload)
            }
        }
    }
}

@Composable
private fun Action(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
}
```

- [ ] **Step 5: Write screen 18**

`.../commerce/PaywallScreen.kt`:

```kotlin
package com.ptk.anatomypro.feature.commerce

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import anatomypro.shared.feature_commerce.generated.resources.Res
import anatomypro.shared.feature_commerce.generated.resources.paywall_back
import anatomypro.shared.feature_commerce.generated.resources.paywall_benefit_board
import anatomypro.shared.feature_commerce.generated.resources.paywall_benefit_daily
import anatomypro.shared.feature_commerce.generated.resources.paywall_benefit_topics
import anatomypro.shared.feature_commerce.generated.resources.paywall_body
import anatomypro.shared.feature_commerce.generated.resources.paywall_buying
import anatomypro.shared.feature_commerce.generated.resources.paywall_loading
import anatomypro.shared.feature_commerce.generated.resources.paywall_notice_nothing_to_restore
import anatomypro.shared.feature_commerce.generated.resources.paywall_notice_plans_failed
import anatomypro.shared.feature_commerce.generated.resources.paywall_notice_purchase_failed
import anatomypro.shared.feature_commerce.generated.resources.paywall_plan
import anatomypro.shared.feature_commerce.generated.resources.paywall_restore
import anatomypro.shared.feature_commerce.generated.resources.paywall_restoring
import anatomypro.shared.feature_commerce.generated.resources.paywall_retry
import anatomypro.shared.feature_commerce.generated.resources.paywall_subscribed_body
import anatomypro.shared.feature_commerce.generated.resources.paywall_subscribed_title
import anatomypro.shared.feature_commerce.generated.resources.paywall_title
import com.ptk.anatomypro.core.data.model.SubscriptionPlan
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.IncorrectAmber
import com.ptk.anatomypro.core.designsystem.TextTertiary
import org.jetbrains.compose.resources.stringResource

/**
 * Prototype screen 18: the paywall.
 *
 * It opens by saying what is free, because that is what makes the rest honest: the atlas is
 * not for sale and nothing here may read as if it were (design spec §29).
 */
@Composable
fun PaywallScreen(
    state: PaywallUiState,
    onPurchase: (SubscriptionPlan) -> Unit,
    onRestore: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(Res.string.paywall_back),
            style = MaterialTheme.typography.labelSmall,
            color = Accent,
            modifier = Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onBack).padding(top = 14.dp),
        )
        Text(stringResource(Res.string.paywall_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

        if (state.subscribed) {
            Text(stringResource(Res.string.paywall_subscribed_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(Res.string.paywall_subscribed_body), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
            return@Column
        }

        Text(stringResource(Res.string.paywall_body), style = MaterialTheme.typography.bodyMedium)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("·  " + stringResource(Res.string.paywall_benefit_topics), style = MaterialTheme.typography.bodyMedium)
            Text("·  " + stringResource(Res.string.paywall_benefit_daily), style = MaterialTheme.typography.bodyMedium)
            Text("·  " + stringResource(Res.string.paywall_benefit_board), style = MaterialTheme.typography.bodyMedium)
        }

        // Announced as it arrives: a failed purchase must not be something only sighted users learn.
        Column(modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
            when (state.notice) {
                PaywallNotice.PURCHASE_FAILED ->
                    Text(stringResource(Res.string.paywall_notice_purchase_failed), style = MaterialTheme.typography.bodyMedium, color = IncorrectAmber)
                PaywallNotice.NOTHING_TO_RESTORE ->
                    Text(stringResource(Res.string.paywall_notice_nothing_to_restore), style = MaterialTheme.typography.bodyMedium, color = IncorrectAmber)
                PaywallNotice.PLANS_FAILED ->
                    Text(stringResource(Res.string.paywall_notice_plans_failed), style = MaterialTheme.typography.bodyMedium, color = IncorrectAmber)
                null -> Unit
            }
        }

        when {
            state.isLoading ->
                Text(stringResource(Res.string.paywall_loading), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)

            state.notice == PaywallNotice.PLANS_FAILED ->
                OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(Res.string.paywall_retry))
                }

            else -> for (plan in state.plans) {
                Button(
                    onClick = { onPurchase(plan) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(
                        if (state.purchasing == plan.id) stringResource(Res.string.paywall_buying)
                        else stringResource(Res.string.paywall_plan, plan.title, plan.priceLabel)
                    )
                }
            }
        }

        OutlinedButton(onClick = onRestore, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(if (state.restoring) Res.string.paywall_restoring else Res.string.paywall_restore))
        }
    }
}
```

- [ ] **Step 6: Verify they compile on both targets**

Run: `./gradlew :shared:feature-profile:allTests :shared:feature-commerce:allTests`
Expected: BUILD SUCCESSFUL, every test still passing.

If `LinearProgressIndicator(progress = { … })` does not resolve, this Material 3 takes `progress: Float`; pass the value directly.

- [ ] **Step 7: Commit**

```bash
git add shared/feature-profile shared/feature-commerce
git commit -m "feat: screens 17, 18 and 19"
```

---

### Task 7: The Profile tab, and a run by hand

**Files:**
- Create: `shared/src/commonMain/kotlin/com/ptk/anatomypro/ProfileTab.kt`
- Modify: `shared/src/commonMain/kotlin/com/ptk/anatomypro/App.kt`
- Modify: `shared/src/commonMain/composeResources/values/strings.xml`, `values-pl/strings.xml`
- Modify: `androidApp/src/debug/kotlin/com/ptk/anatomypro/EntryDependencies.kt`
- Modify: `docs/state-of-play.md`, `docs/superpowers/specs/2026-08-29-anatomy-pro-design.md`

**Interfaces:**
- Consumes: everything Tasks 3–6 produce; `ProfileRoute.Profile / Settings / Packs / Paywall`; the existing `SettingsScreen` and `SettingsViewModel`.
- Produces:
  ```kotlin
  @Composable internal fun ProfileHomeRoute(dependencies: AppDependencies, locale: String, navController: NavHostController)
  @Composable internal fun PacksRoute(dependencies: AppDependencies, navController: NavHostController)
  @Composable internal fun PaywallRoute(dependencies: AppDependencies, navController: NavHostController)
  ```

- [ ] **Step 1: Write the routes**

`shared/src/commonMain/kotlin/com/ptk/anatomypro/ProfileTab.kt`:

```kotlin
package com.ptk.anatomypro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.ptk.anatomypro.core.data.AppDependencies
import com.ptk.anatomypro.core.data.model.Entitlements
import com.ptk.anatomypro.feature.commerce.PaywallScreen
import com.ptk.anatomypro.feature.commerce.PaywallViewModel
import com.ptk.anatomypro.feature.commerce.commerceIsBuilt
import com.ptk.anatomypro.feature.profile.PacksScreen
import com.ptk.anatomypro.feature.profile.PacksViewModel
import com.ptk.anatomypro.feature.profile.ProfileScreen
import com.ptk.anatomypro.feature.profile.ProfileViewModel
import com.ptk.anatomypro.navigation.ProfileRoute
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Prototype screen 17. */
@Composable
internal fun ProfileHomeRoute(dependencies: AppDependencies, locale: String, navController: NavHostController) {
    // Keyed on the locale: system titles are loaded once per model.
    val model: ProfileViewModel = viewModel(key = "profile-$locale") {
        ProfileViewModel(
            progress = dependencies.progress,
            packs = dependencies.packs,
            locale = locale,
            // The user's own day, not UTC: the calendar is their habit (all-screens spec §4.2).
            today = { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date },
        )
    }
    val state by model.state.collectAsState()

    // Read here rather than in the ViewModel: it is one flag, and only where there is
    // something to subscribe to.
    val subscribed = if (commerceIsBuilt(dependencies.entitlements)) {
        val held by dependencies.entitlements.entitlements
            .collectAsState(initial = Entitlements(subscribed = false, ownedSystems = emptySet()))
        held.subscribed
    } else {
        false
    }

    ProfileScreen(
        state = state,
        subscribed = subscribed,
        onRetry = model::onRetry,
        onSettings = { navController.navigate(ProfileRoute.Settings) },
        onPacks = { navController.navigate(ProfileRoute.Packs) },
        onSubscription = { navController.navigate(ProfileRoute.Paywall) },
    )
}

/** Prototype screen 19. */
@Composable
internal fun PacksRoute(dependencies: AppDependencies, navController: NavHostController) {
    val model: PacksViewModel = viewModel { PacksViewModel(dependencies.packs) }
    val state by model.state.collectAsState()

    PacksScreen(
        state = state,
        onDownload = model::onDownload,
        onCancel = model::onCancel,
        onDeleteRequested = model::onDeleteRequested,
        onDeleteConfirmed = model::onDeleteConfirmed,
        onDeleteDismissed = model::onDeleteDismissed,
        onBack = { navController.popBackStack() },
    )
}

/** Prototype screen 18. */
@Composable
internal fun PaywallRoute(dependencies: AppDependencies, navController: NavHostController) {
    val model: PaywallViewModel = viewModel { PaywallViewModel(dependencies.entitlements) }
    val state by model.state.collectAsState()

    PaywallScreen(
        state = state,
        onPurchase = model::onPurchase,
        onRestore = model::onRestore,
        onRetry = model::onRetry,
        onBack = { navController.popBackStack() },
    )
}
```

- [ ] **Step 2: Add the paywall's placeholder label, if it is not there**

If the quiz plan has been executed, `screen_paywall` already exists in the shell's strings; skip this step. Otherwise add to `shared/src/commonMain/composeResources/values/strings.xml`:

```xml
    <string name="screen_paywall">SUBSCRIPTION</string>
```

and to `values-pl/strings.xml`:

```xml
    <string name="screen_paywall">SUBSKRYPCJA</string>
```

- [ ] **Step 3: Wire the Profile tab**

In `App.kt`, add the imports:

```kotlin
import anatomypro.shared.generated.resources.screen_paywall
import com.ptk.anatomypro.feature.commerce.commerceIsBuilt
import com.ptk.anatomypro.feature.profile.profileIsBuilt
```

(omit `screen_paywall` if the quiz plan already imported it).

In `MainScaffold`, extract the existing settings call so two routes can show it. Add this local function above the `Column`:

```kotlin
    val settingsScreen: @Composable () -> Unit = {
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
    val profileBuilt = profileIsBuilt(dependencies.progress, dependencies.packs)
```

Replace the whole `composable<ProfileRoute.Profile> { SettingsScreen(…) }` block, with the comment above it, with:

```kotlin
                // Settings live behind the profile, as in the prototype. Production has no
                // profile to show — its repositories refuse — so there the tab opens settings.
                composable<ProfileRoute.Profile> {
                    if (profileBuilt) ProfileHomeRoute(dependencies, locale, navController) else settingsScreen()
                }
                composable<ProfileRoute.Settings> { settingsScreen() }
                composable<ProfileRoute.Packs> {
                    if (profileBuilt) PacksRoute(dependencies, navController)
                }
                // A release build must never present a purchase that cannot happen.
                composable<ProfileRoute.Paywall> {
                    if (commerceIsBuilt(dependencies.entitlements)) PaywallRoute(dependencies, navController)
                    else NotBuilt(Res.string.screen_paywall)
                }
```

If the quiz plan added `composable<ProfileRoute.Paywall> { NotBuilt(Res.string.screen_paywall) }`, delete that line: a route may be declared once.

`SettingsScreen` has no back affordance; on `ProfileRoute.Settings` the system back gesture and the bottom bar's Profile tab both return to the profile. On iOS there is no system back gesture inside the tab, so tapping PROFILE again is the way back (`selectTab` pops a tab to its start). Record that in Step 6 as a gap: settings needs a back affordance now that it is no longer a tab's first screen.

- [ ] **Step 4: Let a debug build make purchases fail**

In `androidApp/src/debug/kotlin/com/ptk/anatomypro/EntryDependencies.kt`, read one more extra beside the others (declaring `val intent = androidx.activity.compose.LocalActivity.current?.intent` if no earlier plan has):

```kotlin
    //   purchaseFails: true makes every purchase on the paywall fail.
    val purchaseFails = intent?.getBooleanExtra("purchaseFails", false) ?: false
```

and make the fake entitlements honour it. If the daily plan has been executed, the function already builds a `FakeEntitlementRepository(initial = …)`; add `purchaseSucceeds = !purchaseFails` to that call and `purchaseFails` to the `remember` keys. Otherwise change the final statement to copy one in:

```kotlin
    return remember(atlas, fakes, purchaseFails) {
        fakes.copy(
            atlas = atlas?.repository,
            entitlements = FakeEntitlementRepository(purchaseSucceeds = !purchaseFails),
        )
    }
```

with `import com.ptk.anatomypro.core.data.fake.FakeEntitlementRepository`.

- [ ] **Step 5: Build, and run it by hand on Android**

Run: `./gradlew :androidApp:assembleDebug :androidApp:assembleRelease`
Expected: BUILD SUCCESSFUL.

Run:

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17' build
```

Expected: `** BUILD SUCCEEDED **`.

`./gradlew :androidApp:installDebug`, open PROFIL. Write down what was seen.

1. **17**: the streak line, five rows of seven glyphs with the legend, two systems with bars and "Poznane struktury: 14 z 19", and three links.
2. "Ustawienia" opens settings; the system back gesture returns to the profile.
3. **19** from "Pakiety": one pack "Zainstalowany" with "Usuń", two "Niepobrany" with "Pobierz", and "Zainstalowano 48,3 MB". Nothing is locked.
4. "Pobierz" runs through to "Zainstalowany" (the fake is instant; a bar may not be seen — say so).
5. "Usuń" turns the row into "Usunąć ten pakiet? …" with "Usuń" and "Zostaw". "Zostaw" restores it; "Usuń" then "Usuń" makes it "Niepobrany".
6. **18** from "Subskrypcja · brak": what is free, three benefits, two plans with prices, "Przywróć zakup". No sentence mentions the atlas, a system or a pack being unlocked.
7. Buying a plan: "Masz subskrypcję". Back on the profile the link reads "Subskrypcja · aktywna".
8. Relaunch (a free user again), "Przywróć zakup": "Nie znaleziono wcześniejszego zakupu dla tego konta."
9. `adb shell am force-stop com.ptk.anatomypro; adb shell am start -n com.ptk.anatomypro/.MainActivity --ez purchaseFails true`, buy a plan: "Zakup się nie powiódł. …", and the plans can be pressed again.
10. In English (Settings → interface English): screens 17, 18 and 19 once each; sizes read "48.3 MB".

**Not seen by hand, and say so:** a download in progress or failed on screen 19, and the unreadable-profile state — the fake has no switch for a slow or failing pack list in a debug build; they are covered only by the ViewModel tests. TalkBack on any of the three.

- [ ] **Step 6: Check production, and record**

On the iOS simulator, PROFIL opens settings exactly as before, and nothing crashes.

In `docs/state-of-play.md`: add 17, 18 and 19 to the screens that are real, with the qualifications — on fakes, on the Android debug build only. With this, list which of the 21 are real and which states remain unseen across all of them.

Append an addendum to the design spec, numbered one above the last, recording: settings sit behind the profile except in production; the paywall sells the learning system and its failure sentence promises no charge, which Phase 4 must make true; restore says when it finds nothing; a delete is confirmed on its row; the fakes now use the atlas's system ids and entitle no pack; `megabytes` moved to the design system; settings has no back affordance of its own; a calendar day is read out as an ISO date; what was and was not seen in Step 5.

- [ ] **Step 7: Run everything once more**

Run: `./gradlew allTests`
Expected: BUILD SUCCESSFUL on both targets. Stop any app running on a simulator first.

- [ ] **Step 8: Commit**

```bash
git add shared/src androidApp/src/debug docs
git commit -m "feat: the Profile tab opens the profile, with packs, settings and the paywall behind it"
```

---

## Spec coverage

| Spec requirement | Where |
|---|---|
| §9 screen 17: streak calendar; mastery by system | Task 3 tests 1–4; Task 6 Step 3; Task 7 Step 5 item 1 |
| §9 / §15.3 screen 18: free; subscribed; purchase failed — selling the learning system, never content | Task 4; Task 6 Steps 2, 5; Task 7 Step 5 items 6, 7, 9 |
| §9 / §15.3 screen 19: installed; available; downloading; delete confirm — nothing locked | Task 5; Task 6 Step 4; Task 7 Step 5 items 3–5 |
| §15.2: packs carry no entitlement | Task 2; Task 6 `PacksScreen` reads none |
| §4.2: the calendar is in the user's days | Task 3 `today`; Task 7 Step 1 `TimeZone.currentSystemDefault()` |
| §4.6: a download UI reports bytes | Tasks 1, 6 |
| §6: refusing repositories are never called; no purchase is presented that cannot happen | `profileIsBuilt`, `commerceIsBuilt`; Task 7 Steps 3, 6 |
| §10: a ViewModel test per screen, both targets | Tasks 3, 4, 5 |
| Design §12: a destructive action is confirmed; state not by colour alone | Task 5 delete tests; Task 6 glyphs and words |
| Design §14: a failed download is resumable | Task 5 `a_download_that_fails…` |
