# Onboarding: Goal Setting (Screen 02) and First Pack Download (Screen 03) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build prototype screen 02 (choose which body systems you are studying) and screen 03 (the first pack downloads, with progress, failure and resume), and turn onboarding from one screen into a three-step flow.

**Architecture:** A new `:shared:feature-onboarding` module holds one ViewModel and one screen per step. `GoalsViewModel` reads the systems the installed atlas has and writes the choice to the existing `AppSettings.studiedSystems`. `FirstDownloadViewModel` observes one pack in `PackRepository`, starts its download once, and maps `PackStatus` to a small `DownloadPhase` through a pure function. `:shared` hosts the three steps in their own `NavHost` over the existing `OnboardingRoute`, and ends onboarding by setting `onboarded`. Production has no `PackRepository` yet, so there the flow is two steps and screen 03 is never constructed.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform 1.11.1, navigation-compose 2.9.2, lifecycle 2.11.0-beta01, kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-09-24-all-screens-mocked-design.md` §4.6 (`PackRepository`), §4.8 (screen 02 gets no repository), §6 (refusing repositories), §8 (strings), §9 (screens 02 and 03: states that must exist), §10, §11 step 7. Design spec `docs/superpowers/specs/2026-08-29-anatomy-pro-design.md` §12 (accessibility), §14 (downloads are resumable), §29 (packs are not entitled; the whole atlas is free). Brief: `docs/design-prompt-prototype-screens.md`, "Onboarding".

## Global Constraints

- **Repository root is `~/StudioProjects/AnatomyPro`.** Never edit `~/Projekty/anatomy pro`.
- **Tests run on two targets.** `./gradlew :shared:<module>:allTests` runs the JVM host and the iOS simulator. A test that passes on one and not the other is a failure.
- **Do not run `allTests` while an app is running on a simulator.** The iOS GPU contract test `reports_a_pick` times out under that contention.
- `./gradlew --stop` between long sessions. This machine runs out of memory.
- **No `Co-Authored-By` or `Claude-Session` trailers on commits.**
- **UI strings are always resources** (all-screens spec §8). English in `values/`, Polish in `values-pl/`. Key names are `<screen>_<purpose>` in lower snake case. Glyphs, locale codes and each language's own name are the only literals.
- **ViewModels never produce user-visible text.** They expose kinds and values; screens pick the resource. A pack's `label` is data from the repository and is shown as given.
- **State that must survive an interface-language change lives above `ProvideAppLocale`** — it rebuilds everything beneath it. The onboarding `NavController` is such state.
- **§12:** touch targets at least 44 dp; never distinguish state by colour alone — a chosen system carries a text label, and a failed download says so in words.
- **Colours come from `core-designsystem`** (`Accent`, `TextTertiary`, `Hairline`, `IncorrectAmber`). Never introduce a colour literal.
- **No Compose UI tests** (all-screens spec §10, decided). Each screen is checked by hand in Task 7.
- System ids in real data look like `skeletal-system`, `muscular-system`.
- **Out of scope:** a real `PackRepository` (downloads, checksums, resume — Phase 1's own task); choosing *which* packs to download from the goals; cancelling a download from screen 03; remembering the onboarding step across process death (a relaunch starts at step 1 with every choice already made kept).

## Decisions this plan makes

The spec leaves these open. Each is stated here so a reviewer can reject it.

1. **Screen 02 offers the systems the installed atlas has** (`AtlasRepository.systems()`), as spec §9 says, not a fixed catalogue. With one bundled pack that is a short list, and that is honest: a system nothing can show yet is not something to study in this build.
2. **Continuing with nothing selected is allowed.** "None selected" is a required state, and a student who does not want to say must not be stuck. The button reads "Skip for now" until something is chosen.
3. **The first pack is `skeletal-body`**, a constant in the onboarding module. The brief names the skeletal system, and the verification plan (`docs/verification-plan.md` §6) verifies the skeleton first.
4. **A failed download can be skipped.** §14 makes failure a state to resume from; being offline must not trap someone in onboarding.
5. **Production onboarding is two steps.** `NotBuiltPackRepository` throws on first use (spec §6), and the pack is bundled in the app today. Until a real `PackRepository` exists, production goes 01 → 02 → done and says "of 2". Removing that branch is the one-line change spec §6 describes.
6. **`feature-onboarding` depends on `feature-atlas`** for `systemNames`. A feature depending on a feature is not the pattern here, but the eight system names are unverified medical content (state-of-play, known defects) and must exist in exactly one place.

## Review Focus

Inputs the spec implies and a person will meet. Each has a test in the task named.

1. **The atlas fails to list its systems** (a broken install): screen 02 shows an empty list and can still be continued, not a spinner for ever. — Task 2.
2. **The download request itself throws** (offline at the moment of asking, not half way): screen 03 shows a resumable failure, not a crash. — Task 4.
3. **The repository has no pack with the expected id**: screen 03 says there is nothing to download and lets the user finish. — Task 4.
4. **Screen 03 is left and re-entered while the download runs** (back to goals, forward again): no second download is requested. — Task 4.
5. **A stored goal the atlas no longer offers** (a pack was removed): it is kept when another system is toggled, not silently dropped. — Task 2.

## File Structure

| File | Responsibility |
|---|---|
| `shared/core-data-fake/.../FakePackRepository.kt` | Modify. Start with a chosen set installed; fail a chosen number of attempts; count download requests. |
| `shared/core-data-fake/src/commonTest/.../FakeCommerceTest.kt` | Modify. Tests for the three additions. |
| `settings.gradle.kts`, `shared/build.gradle.kts` | Modify. Register and consume the new module. |
| `shared/feature-onboarding/build.gradle.kts` | Create. |
| `shared/feature-onboarding/.../GoalsViewModel.kt` | Create. Screen 02's state and its two events. |
| `shared/feature-onboarding/.../GoalsScreen.kt` | Create. Screen 02. |
| `shared/feature-onboarding/.../FirstDownloadViewModel.kt` | Create. `DownloadPhase`, `phaseOf`, screen 03's ViewModel. |
| `shared/feature-onboarding/.../Megabytes.kt` | Create. Byte counts as text, without a platform formatter. |
| `shared/feature-onboarding/.../FirstDownloadScreen.kt` | Create. Screen 03. |
| `shared/feature-onboarding/src/commonMain/composeResources/values{,-pl}/strings.xml` | Create. |
| `shared/feature-onboarding/src/commonTest/...` | Create. `GoalsViewModelTest`, `FirstDownloadViewModelTest`, `MegabytesTest`, `OnboardingStepsTest`. |
| `shared/feature-settings/.../LanguageSelectionScreen.kt` and its two `strings.xml` | Modify. The step label takes the step count. |
| `shared/feature-onboarding/.../OnboardingSteps.kt` | Create. `onboardingHasDownloadStep` — whether there is anything to download with. |
| `shared/src/commonMain/.../Onboarding.kt` | Create. The onboarding `NavHost`. |
| `shared/src/commonMain/.../App.kt` | Modify. Show the flow instead of screen 01 alone. |
| `androidApp/src/debug/.../EntryDependencies.kt` | Modify. An intent extra that starts a debug build in onboarding, on fakes. |
| `docs/state-of-play.md` | Modify. Nine screens real. |

All Kotlin paths below abbreviate `src/commonMain/kotlin/com/ptk/anatomypro/feature/onboarding/` as `.../onboarding/`.

---

### Task 1: A fake pack repository that can start empty, recover, and be counted

Screen 03's four states cannot be reached on the fake as it is: `skeletal-body` starts `Installed`, and a configured failure fails every attempt, so a resume can never succeed.

**Files:**
- Modify: `shared/core-data-fake/src/commonMain/kotlin/com/ptk/anatomypro/core/data/fake/FakePackRepository.kt`
- Test: `shared/core-data-fake/src/commonTest/kotlin/com/ptk/anatomypro/core/data/fake/FakeCommerceTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces:
  ```kotlin
  class FakePackRepository(
      behaviour: FakeBehaviour = FakeBehaviour(),
      downloadFailure: PackFailure? = null,
      failingAttempts: Int = Int.MAX_VALUE,
      initiallyInstalled: Set<PackId> = setOf(PackId("skeletal-body")),
  ) : PackRepository {
      val downloadRequests: Int
  }
  ```
  Defaults keep every existing caller's behaviour.

- [ ] **Step 1: Write the failing tests**

Append inside `class FakeCommerceTest`, before its closing brace:

```kotlin
    @Test
    fun a_fake_can_start_with_nothing_installed_so_a_first_download_has_something_to_do() = runTest {
        val packs = FakePackRepository(initiallyInstalled = emptySet()).packs.first()

        assertIs<PackStatus.Available>(packs.first { it.id == PackId("skeletal-body") }.status)
    }

    @Test
    fun a_download_that_failed_once_can_succeed_when_asked_again() = runTest {
        // Screen 03 offers a resume. A fake that failed every attempt could never show it working.
        val repository = FakePackRepository(downloadFailure = PackFailure.NETWORK, failingAttempts = 1)

        repository.download(PackId("muscular-body"))
        assertIs<PackStatus.Failed>(repository.packs.first().first { it.id == PackId("muscular-body") }.status)

        repository.download(PackId("muscular-body"))
        assertIs<PackStatus.Installed>(repository.packs.first().first { it.id == PackId("muscular-body") }.status)
    }

    @Test
    fun download_requests_are_counted_so_a_test_can_tell_one_from_two() = runTest {
        val repository = FakePackRepository()
        assertEquals(0, repository.downloadRequests)

        repository.download(PackId("muscular-body"))
        repository.download(PackId("nervous-body"))

        assertEquals(2, repository.downloadRequests)
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :shared:core-data-fake:allTests`
Expected: compilation FAILS with `No parameter with name 'initiallyInstalled'`, `No parameter with name 'failingAttempts'` and `Unresolved reference 'downloadRequests'`.

- [ ] **Step 3: Implement**

In `FakePackRepository.kt`, replace the constructor and the class's first lines down to and including the `private val _packs = MutableStateFlow(` list with:

```kotlin
class FakePackRepository(
    private val behaviour: FakeBehaviour = FakeBehaviour(),
    private val downloadFailure: PackFailure? = null,
    /** How many download attempts [downloadFailure] spoils before one is allowed through. */
    private val failingAttempts: Int = Int.MAX_VALUE,
    /** Screen 03 needs the first pack absent; screens 17 and 19 want it present. */
    initiallyInstalled: Set<PackId> = setOf(PackId("skeletal-body")),
) : PackRepository {

    /** How many times [download] has been asked for, whatever came of it. */
    var downloadRequests: Int = 0
        private set

    private fun initialStatus(id: PackId): PackStatus =
        if (id in initiallyInstalled) PackStatus.Installed else PackStatus.Available

    private val _packs = MutableStateFlow(
        listOf(
            PackState(
                id = PackId("skeletal-body"),
                version = 1,
                label = "Układ kostny",
                byteSize = 48_300_000,
                checksum = "sha256:2f1c0a",
                entitlement = null,
                status = initialStatus(PackId("skeletal-body")),
            ),
            PackState(
                id = PackId("muscular-body"),
                version = 1,
                label = "Układ mięśniowy",
                byteSize = 112_700_000,
                checksum = "sha256:9b4e17",
                entitlement = SystemId("muscular"),
                status = initialStatus(PackId("muscular-body")),
            ),
            PackState(
                id = PackId("nervous-body"),
                version = 1,
                label = "Układ nerwowy",
                byteSize = 67_400_000,
                checksum = "sha256:c30d82",
                entitlement = SystemId("nervous"),
                status = initialStatus(PackId("nervous-body")),
            ),
        ),
    )
```

Then replace the whole `download` function with:

```kotlin
    override suspend fun download(id: PackId) {
        downloadRequests++
        val spoiled = downloadFailure != null && downloadRequests <= failingAttempts
        behaviour.respond {
            val pack = _packs.value.first { it.id == id }
            setStatus(id, PackStatus.Queued)
            yield()
            for (step in 1..DOWNLOAD_STEPS) {
                if (spoiled && step > DOWNLOAD_STEPS / 2) {
                    val reason = downloadFailure!!
                    setStatus(id, PackStatus.Failed(reason, resumable = reason == PackFailure.NETWORK))
                    return@respond
                }
                setStatus(id, PackStatus.Downloading(pack.byteSize * step / DOWNLOAD_STEPS, pack.byteSize))
                yield()
                // cancel() and delete() run while this loop is suspended; without this check
                // the next step would overwrite them and the download would finish anyway.
                if (_packs.value.first { it.id == id }.status !is PackStatus.Downloading) return@respond
            }
            setStatus(id, PackStatus.Installed)
        }
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :shared:core-data-fake:allTests`
Expected: BUILD SUCCESSFUL, the three new tests and every existing one passing on both targets.

- [ ] **Step 5: Commit**

```bash
git add shared/core-data-fake
git commit -m "test(fake): a pack fake that can start empty, recover from a failure and be counted"
```

---

### Task 2: The onboarding module and screen 02's ViewModel

**Files:**
- Modify: `settings.gradle.kts` (after the `include(":shared:feature-settings")` line)
- Modify: `shared/build.gradle.kts` (after `api(project(":shared:feature-settings"))`)
- Create: `shared/feature-onboarding/build.gradle.kts`
- Create: `shared/feature-onboarding/.../onboarding/GoalsViewModel.kt`
- Test: `shared/feature-onboarding/src/commonTest/kotlin/com/ptk/anatomypro/feature/onboarding/GoalsViewModelTest.kt`

**Interfaces:**
- Consumes: `SettingsRepository.settings` / `update`, `AppSettings.studiedSystems: Set<String>`, `AtlasRepository.systems(): List<SystemId>`, `FakeSettingsRepository(initial)`, `FakeAtlasRepository(behaviour)`, `FakeBehaviour(failure = …)`.
- Produces:
  ```kotlin
  data class GoalsUiState(
      val systems: List<SystemId> = emptyList(),
      val selected: Set<String> = emptySet(),
      val isLoading: Boolean = true,
  )
  class GoalsViewModel(settings: SettingsRepository, atlas: AtlasRepository) : ViewModel() {
      val state: StateFlow<GoalsUiState>
      fun onToggle(system: SystemId)
  }
  ```

- [ ] **Step 1: Create the module**

`settings.gradle.kts`, add:

```kotlin
include(":shared:feature-onboarding")
```

`shared/feature-onboarding/build.gradle.kts`:

```kotlin
plugins { id("anatomypro.kmp.compose") }

kotlin {
    android { namespace = "com.ptk.anatomypro.feature.onboarding" }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.components.resources)
            api(project(":shared:core-data"))
            implementation(project(":shared:core-designsystem"))
            // For systemNames: the system names are unverified medical content and are
            // written down once, in feature-atlas.
            implementation(project(":shared:feature-atlas"))
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

`shared/build.gradle.kts`, in `commonMain.dependencies`, add:

```kotlin
            api(project(":shared:feature-onboarding"))
```

- [ ] **Step 2: Write the failing tests**

`GoalsViewModelTest.kt`:

```kotlin
package com.ptk.anatomypro.feature.onboarding

import com.ptk.anatomypro.core.data.fake.FakeAtlasRepository
import com.ptk.anatomypro.core.data.fake.FakeBehaviour
import com.ptk.anatomypro.core.data.fake.FakeSettingsRepository
import com.ptk.anatomypro.core.data.model.AppSettings
import com.ptk.anatomypro.core.model.SystemId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertTrue

class GoalsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val skeletal = SystemId("skeletal-system")
    private val muscular = SystemId("muscular-system")

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun nothing_is_selected_until_the_student_chooses() = runTest(dispatcher) {
        // §9: "none selected" is a state the screen must have, not a moment before loading ends.
        val model = GoalsViewModel(FakeSettingsRepository(), FakeAtlasRepository())
        advanceUntilIdle()

        assertFalse(model.state.value.isLoading)
        assertEquals(emptySet(), model.state.value.selected)
    }

    @Test
    fun the_systems_offered_are_the_ones_the_atlas_has() = runTest(dispatcher) {
        val model = GoalsViewModel(FakeSettingsRepository(), FakeAtlasRepository())
        advanceUntilIdle()

        assertEquals(setOf(skeletal, muscular), model.state.value.systems.toSet())
    }

    @Test
    fun choosing_a_system_selects_it_and_writes_it_to_settings() = runTest(dispatcher) {
        // §4.8: the choice is a preference. It is saved as it is made, not on Continue, so
        // leaving the screen by any route keeps it.
        val settings = FakeSettingsRepository()
        val model = GoalsViewModel(settings, FakeAtlasRepository())
        advanceUntilIdle()

        model.onToggle(skeletal)
        advanceUntilIdle()

        assertEquals(setOf("skeletal-system"), model.state.value.selected)
        assertEquals(setOf("skeletal-system"), settings.settings.first().studiedSystems)
    }

    @Test
    fun choosing_a_selected_system_again_deselects_it() = runTest(dispatcher) {
        val model = GoalsViewModel(FakeSettingsRepository(), FakeAtlasRepository())
        advanceUntilIdle()

        model.onToggle(skeletal)
        model.onToggle(muscular)
        model.onToggle(skeletal)
        advanceUntilIdle()

        assertEquals(setOf("muscular-system"), model.state.value.selected)
    }

    @Test
    fun an_earlier_choice_is_shown_when_the_screen_is_opened_again() = runTest(dispatcher) {
        val settings = FakeSettingsRepository(AppSettings(studiedSystems = setOf("muscular-system")))

        val model = GoalsViewModel(settings, FakeAtlasRepository())
        advanceUntilIdle()

        assertEquals(setOf("muscular-system"), model.state.value.selected)
    }

    @Test
    fun an_atlas_that_cannot_list_its_systems_leaves_an_empty_list_not_a_spinner() = runTest(dispatcher) {
        // Review Focus 1. A broken install must not hold a new user on step two for ever.
        val broken = FakeAtlasRepository(FakeBehaviour(failure = { IllegalStateException("no atlas") }))

        val model = GoalsViewModel(FakeSettingsRepository(), broken)
        advanceUntilIdle()

        assertFalse(model.state.value.isLoading)
        assertTrue(model.state.value.systems.isEmpty())
    }

    @Test
    fun a_stored_system_the_atlas_no_longer_offers_is_kept_when_another_is_toggled() = runTest(dispatcher) {
        // Review Focus 5. A pack can be removed; the goal it carried is the student's, not ours to drop.
        val settings = FakeSettingsRepository(AppSettings(studiedSystems = setOf("nervous-system")))
        val model = GoalsViewModel(settings, FakeAtlasRepository())
        advanceUntilIdle()

        model.onToggle(skeletal)
        advanceUntilIdle()

        assertEquals(setOf("nervous-system", "skeletal-system"), settings.settings.first().studiedSystems)
    }
}
```

- [ ] **Step 3: Run them to verify they fail**

Run: `./gradlew :shared:feature-onboarding:allTests`
Expected: compilation FAILS with `Unresolved reference 'GoalsViewModel'`.

- [ ] **Step 4: Implement**

`.../onboarding/GoalsViewModel.kt`:

```kotlin
package com.ptk.anatomypro.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptk.anatomypro.core.data.repository.AtlasRepository
import com.ptk.anatomypro.core.data.repository.SettingsRepository
import com.ptk.anatomypro.core.model.SystemId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Prototype screen 02: which systems the student is studying.
 *
 * [selected] is everything stored, including a system [systems] does not offer: the choice
 * is the student's and survives a pack being removed.
 */
data class GoalsUiState(
    val systems: List<SystemId> = emptyList(),
    val selected: Set<String> = emptySet(),
    val isLoading: Boolean = true,
)

class GoalsViewModel(
    private val settings: SettingsRepository,
    private val atlas: AtlasRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GoalsUiState())
    val state: StateFlow<GoalsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // A failed listing is an empty list. The step can still be passed, which matters
            // more on a first run than explaining what went wrong with the install.
            val systems = try {
                atlas.systems()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            _state.update { it.copy(systems = systems, isLoading = false) }
        }
        viewModelScope.launch {
            settings.settings.collect { stored ->
                _state.update { it.copy(selected = stored.studiedSystems) }
            }
        }
    }

    /** Saved as it is made rather than on Continue, so no way of leaving the screen loses it. */
    fun onToggle(system: SystemId) {
        viewModelScope.launch {
            settings.update { current ->
                val id = system.value
                val next = if (id in current.studiedSystems) current.studiedSystems - id else current.studiedSystems + id
                current.copy(studiedSystems = next)
            }
        }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :shared:feature-onboarding:allTests`
Expected: BUILD SUCCESSFUL, seven tests passing on the JVM host and on the iOS simulator.

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts shared/build.gradle.kts shared/feature-onboarding
git commit -m "feat(onboarding): the goals a student sets are read from the atlas and kept in settings"
```

---

### Task 3: Screen 02

No test: Compose UI tests are out of scope. The deliverable is a screen that compiles on both targets and is looked at in Task 7.

**Files:**
- Create: `shared/feature-onboarding/src/commonMain/composeResources/values/strings.xml`
- Create: `shared/feature-onboarding/src/commonMain/composeResources/values-pl/strings.xml`
- Create: `shared/feature-onboarding/.../onboarding/GoalsScreen.kt`

**Interfaces:**
- Consumes: `GoalsUiState` (Task 2); `systemNames(system: SystemId): SystemName` with `latin` and `local`, from `com.ptk.anatomypro.feature.atlas.layers`.
- Produces:
  ```kotlin
  @Composable fun GoalsScreen(
      state: GoalsUiState, step: Int, stepCount: Int,
      onToggle: (SystemId) -> Unit, onBack: () -> Unit, onContinue: () -> Unit,
      modifier: Modifier = Modifier,
  )
  ```
  and the string keys `onboarding_step`, `onboarding_back`, which Task 5 also uses.

- [ ] **Step 1: Write the English strings**

`values/strings.xml`:

```xml
<resources>
    <string name="onboarding_step">STEP %1$d OF %2$d</string>
    <string name="onboarding_back">Back</string>
    <string name="goals_title">What are you studying?</string>
    <string name="goals_body">Choose the systems you are working on now. It can be changed later.</string>
    <string name="goals_loading">Reading the atlas…</string>
    <string name="goals_empty">The atlas has no systems to choose from yet.</string>
    <string name="goals_chosen">CHOSEN</string>
    <string name="goals_continue">Continue</string>
    <string name="goals_skip">Skip for now</string>
    <plurals name="goals_count">
        <item quantity="one">%1$d system chosen</item>
        <item quantity="other">%1$d systems chosen</item>
    </plurals>
</resources>
```

- [ ] **Step 2: Write the Polish strings**

`values-pl/strings.xml`:

```xml
<resources>
    <string name="onboarding_step">KROK %1$d Z %2$d</string>
    <string name="onboarding_back">Wstecz</string>
    <string name="goals_title">Czego się uczysz?</string>
    <string name="goals_body">Wybierz układy, nad którymi teraz pracujesz. Można to później zmienić.</string>
    <string name="goals_loading">Wczytywanie atlasu…</string>
    <string name="goals_empty">Atlas nie ma jeszcze układów do wyboru.</string>
    <string name="goals_chosen">WYBRANE</string>
    <string name="goals_continue">Dalej</string>
    <string name="goals_skip">Pomiń na razie</string>
    <plurals name="goals_count">
        <item quantity="one">Wybrano %1$d układ</item>
        <item quantity="few">Wybrano %1$d układy</item>
        <item quantity="many">Wybrano %1$d układów</item>
        <item quantity="other">Wybrano %1$d układu</item>
    </plurals>
</resources>
```

- [ ] **Step 3: Write the screen**

`.../onboarding/GoalsScreen.kt`:

```kotlin
package com.ptk.anatomypro.feature.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import anatomypro.shared.feature_onboarding.generated.resources.Res
import anatomypro.shared.feature_onboarding.generated.resources.goals_body
import anatomypro.shared.feature_onboarding.generated.resources.goals_chosen
import anatomypro.shared.feature_onboarding.generated.resources.goals_continue
import anatomypro.shared.feature_onboarding.generated.resources.goals_count
import anatomypro.shared.feature_onboarding.generated.resources.goals_empty
import anatomypro.shared.feature_onboarding.generated.resources.goals_loading
import anatomypro.shared.feature_onboarding.generated.resources.goals_skip
import anatomypro.shared.feature_onboarding.generated.resources.goals_title
import anatomypro.shared.feature_onboarding.generated.resources.onboarding_back
import anatomypro.shared.feature_onboarding.generated.resources.onboarding_step
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.TextTertiary
import com.ptk.anatomypro.core.model.SystemId
import com.ptk.anatomypro.feature.atlas.layers.systemNames
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Prototype screen 02: goal setting.
 *
 * A chosen row says CHOSEN in words as well as in colour (§12), and the button changes its
 * wording rather than disabling: passing the step with nothing chosen is allowed.
 */
@Composable
fun GoalsScreen(
    state: GoalsUiState,
    step: Int,
    stepCount: Int,
    onToggle: (SystemId) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Counted against what is offered, so a goal kept for a pack that is gone is not announced.
    val chosen = state.systems.count { it.value in state.selected }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.onboarding_step, step, stepCount),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
            Text(
                stringResource(Res.string.onboarding_back),
                style = MaterialTheme.typography.labelSmall,
                color = Accent,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(start = 16.dp, top = 14.dp),
            )
        }
        Text(
            stringResource(Res.string.goals_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(stringResource(Res.string.goals_body), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when {
                state.isLoading ->
                    Text(stringResource(Res.string.goals_loading), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)

                state.systems.isEmpty() ->
                    Text(stringResource(Res.string.goals_empty), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)

                else -> for (system in state.systems) {
                    val isChosen = system.value in state.selected
                    val names = systemNames(system)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .toggleable(value = isChosen, role = Role.Checkbox, onValueChange = { onToggle(system) }),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(names.local, style = MaterialTheme.typography.bodyMedium)
                            Text(names.latin, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                        }
                        if (isChosen) {
                            Text(stringResource(Res.string.goals_chosen), style = MaterialTheme.typography.labelSmall, color = Accent)
                        }
                    }
                }
            }
        }

        Column {
            if (chosen > 0) {
                Text(
                    pluralStringResource(Res.plurals.goals_count, chosen, chosen),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(if (chosen > 0) Res.string.goals_continue else Res.string.goals_skip))
            }
        }
    }
}
```

- [ ] **Step 4: Verify it compiles on both targets**

Run: `./gradlew :shared:feature-onboarding:allTests`
Expected: BUILD SUCCESSFUL (the test tasks compile `commonMain` for the JVM host and the iOS simulator), seven tests still passing.

- [ ] **Step 5: Commit**

```bash
git add shared/feature-onboarding
git commit -m "feat(onboarding): screen 02, goal setting"
```

---

### Task 4: Screen 03's ViewModel

**Files:**
- Create: `shared/feature-onboarding/.../onboarding/FirstDownloadViewModel.kt`
- Test: `shared/feature-onboarding/src/commonTest/kotlin/com/ptk/anatomypro/feature/onboarding/FirstDownloadViewModelTest.kt`

**Interfaces:**
- Consumes: `PackRepository.packs` / `download`, `PackState`, `PackStatus`, `PackFailure`; `FakePackRepository(behaviour, downloadFailure, failingAttempts, initiallyInstalled)` and its `downloadRequests` (Task 1).
- Produces:
  ```kotlin
  val FIRST_PACK: PackId   // PackId("skeletal-body")

  sealed interface DownloadPhase {
      data object Queued : DownloadPhase
      data class Progress(val bytesDone: Long, val bytesTotal: Long) : DownloadPhase { val fraction: Float }
      data class Failed(val reason: PackFailure, val resumable: Boolean) : DownloadPhase
      data object Done : DownloadPhase
      data object Unavailable : DownloadPhase
  }
  fun phaseOf(status: PackStatus?, requestFailed: Boolean): DownloadPhase

  data class FirstDownloadUiState(val label: String? = null, val byteSize: Long = 0, val phase: DownloadPhase = DownloadPhase.Queued) {
      val canContinue: Boolean   // Done or Unavailable
      val canSkip: Boolean       // Failed
  }
  class FirstDownloadViewModel(packs: PackRepository, pack: PackId = FIRST_PACK) : ViewModel() {
      val state: StateFlow<FirstDownloadUiState>
      fun onRetry()
  }
  ```

- [ ] **Step 1: Write the failing tests**

`FirstDownloadViewModelTest.kt`:

```kotlin
package com.ptk.anatomypro.feature.onboarding

import com.ptk.anatomypro.core.data.fake.FakeBehaviour
import com.ptk.anatomypro.core.data.fake.FakePackRepository
import com.ptk.anatomypro.core.data.model.PackFailure
import com.ptk.anatomypro.core.data.model.PackStatus
import com.ptk.anatomypro.core.model.PackId
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FirstDownloadViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    // --- §9's four states, as the mapping the screen draws from ----------------------

    @Test
    fun a_pack_asked_for_and_not_yet_transferring_is_queued() {
        assertEquals(DownloadPhase.Queued, phaseOf(PackStatus.Queued, requestFailed = false))
        // Available is the instant before the request goes out; showing "queued" then is
        // truer than flashing some fifth state.
        assertEquals(DownloadPhase.Queued, phaseOf(PackStatus.Available, requestFailed = false))
    }

    @Test
    fun a_transferring_pack_reports_how_far_it_has_got() {
        val phase = phaseOf(PackStatus.Downloading(bytesDone = 12_000_000, bytesTotal = 48_000_000), requestFailed = false)

        assertEquals(DownloadPhase.Progress(12_000_000, 48_000_000), phase)
        assertEquals(0.25f, (phase as DownloadPhase.Progress).fraction)
    }

    @Test
    fun a_network_failure_is_one_to_resume_from() {
        val phase = phaseOf(PackStatus.Failed(PackFailure.NETWORK, resumable = true), requestFailed = false)

        assertEquals(DownloadPhase.Failed(PackFailure.NETWORK, resumable = true), phase)
    }

    @Test
    fun a_checksum_failure_is_not_offered_as_a_resume() {
        // The bytes held are wrong, so there is nothing to resume: the screen offers to start over.
        val phase = phaseOf(PackStatus.Failed(PackFailure.CHECKSUM, resumable = false), requestFailed = false)

        assertEquals(DownloadPhase.Failed(PackFailure.CHECKSUM, resumable = false), phase)
    }

    @Test
    fun an_installed_pack_is_done() {
        assertEquals(DownloadPhase.Done, phaseOf(PackStatus.Installed, requestFailed = false))
    }

    @Test
    fun a_request_that_threw_reads_as_a_resumable_failure_until_the_repository_says_otherwise() {
        assertEquals(DownloadPhase.Failed(PackFailure.NETWORK, resumable = true), phaseOf(PackStatus.Available, requestFailed = true))
        // Once bytes are moving the earlier failure is history.
        assertIs<DownloadPhase.Progress>(phaseOf(PackStatus.Downloading(1, 2), requestFailed = true))
    }

    @Test
    fun a_pack_the_repository_does_not_have_is_unavailable() {
        assertEquals(DownloadPhase.Unavailable, phaseOf(null, requestFailed = false))
    }

    // --- the ViewModel ---------------------------------------------------------------

    @Test
    fun the_download_starts_by_itself_and_ends_done() = runTest(dispatcher) {
        val repository = FakePackRepository(initiallyInstalled = emptySet())

        val model = FirstDownloadViewModel(repository)
        advanceUntilIdle()

        assertEquals(DownloadPhase.Done, model.state.value.phase)
        assertTrue(model.state.value.canContinue)
        assertEquals(1, repository.downloadRequests)
    }

    @Test
    fun the_state_names_the_pack_and_its_size() = runTest(dispatcher) {
        val model = FirstDownloadViewModel(FakePackRepository(initiallyInstalled = emptySet()))
        advanceUntilIdle()

        assertEquals("Układ kostny", model.state.value.label)
        assertEquals(48_300_000L, model.state.value.byteSize)
    }

    @Test
    fun a_pack_already_installed_is_done_without_a_download() = runTest(dispatcher) {
        // A reinstall over existing data, or a pack bundled with the app.
        val repository = FakePackRepository()

        val model = FirstDownloadViewModel(repository)
        advanceUntilIdle()

        assertEquals(DownloadPhase.Done, model.state.value.phase)
        assertEquals(0, repository.downloadRequests)
    }

    @Test
    fun a_download_that_fails_can_be_skipped_but_not_continued_as_if_done() = runTest(dispatcher) {
        val model = FirstDownloadViewModel(
            FakePackRepository(downloadFailure = PackFailure.NETWORK, initiallyInstalled = emptySet()),
        )
        advanceUntilIdle()

        assertEquals(DownloadPhase.Failed(PackFailure.NETWORK, resumable = true), model.state.value.phase)
        assertTrue(model.state.value.canSkip)
        assertFalse(model.state.value.canContinue)
    }

    @Test
    fun retrying_a_failed_download_finishes_it() = runTest(dispatcher) {
        val repository = FakePackRepository(
            downloadFailure = PackFailure.NETWORK,
            failingAttempts = 1,
            initiallyInstalled = emptySet(),
        )
        val model = FirstDownloadViewModel(repository)
        advanceUntilIdle()
        assertIs<DownloadPhase.Failed>(model.state.value.phase)

        model.onRetry()
        advanceUntilIdle()

        assertEquals(DownloadPhase.Done, model.state.value.phase)
        assertEquals(2, repository.downloadRequests)
    }

    @Test
    fun a_request_that_throws_is_a_resumable_failure_not_a_crash() = runTest(dispatcher) {
        // Review Focus 2. Offline at the moment of asking: download() throws before any
        // status is written.
        val offline = FakePackRepository(
            behaviour = FakeBehaviour(failure = { IllegalStateException("offline") }),
            initiallyInstalled = emptySet(),
        )

        val model = FirstDownloadViewModel(offline)
        advanceUntilIdle()

        assertEquals(DownloadPhase.Failed(PackFailure.NETWORK, resumable = true), model.state.value.phase)
        assertTrue(model.state.value.canSkip)
    }

    @Test
    fun a_pack_the_repository_does_not_have_can_be_passed() = runTest(dispatcher) {
        // Review Focus 3.
        val repository = FakePackRepository(initiallyInstalled = emptySet())

        val model = FirstDownloadViewModel(repository, pack = PackId("no-such-pack"))
        advanceUntilIdle()

        assertEquals(DownloadPhase.Unavailable, model.state.value.phase)
        assertTrue(model.state.value.canContinue)
        assertEquals(0, repository.downloadRequests)
    }

    @Test
    fun opening_the_screen_again_after_it_finished_asks_for_nothing_more() = runTest(dispatcher) {
        // Review Focus 4. Back to the goals and forward again builds a second ViewModel over
        // the same repository; only a pack that is still Available is ever requested.
        val repository = FakePackRepository(initiallyInstalled = emptySet())
        FirstDownloadViewModel(repository)
        advanceUntilIdle()

        FirstDownloadViewModel(repository)
        advanceUntilIdle()

        assertEquals(1, repository.downloadRequests)
    }

    @Test
    fun a_failure_is_not_retried_just_because_the_screen_was_reopened() = runTest(dispatcher) {
        // Reopening must not turn into a retry loop against a network that is down.
        val repository = FakePackRepository(downloadFailure = PackFailure.NETWORK, initiallyInstalled = emptySet())
        FirstDownloadViewModel(repository)
        advanceUntilIdle()

        val reopened = FirstDownloadViewModel(repository)
        advanceUntilIdle()

        assertIs<DownloadPhase.Failed>(reopened.state.value.phase)
        assertEquals(1, repository.downloadRequests)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :shared:feature-onboarding:allTests`
Expected: compilation FAILS with `Unresolved reference 'FirstDownloadViewModel'`, `'DownloadPhase'` and `'phaseOf'`.

- [ ] **Step 3: Implement**

`.../onboarding/FirstDownloadViewModel.kt`:

```kotlin
package com.ptk.anatomypro.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptk.anatomypro.core.data.model.PackFailure
import com.ptk.anatomypro.core.data.model.PackState
import com.ptk.anatomypro.core.data.model.PackStatus
import com.ptk.anatomypro.core.data.repository.PackRepository
import com.ptk.anatomypro.core.model.PackId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** The pack onboarding fetches: the skeleton, which the brief names and review starts with. */
val FIRST_PACK = PackId("skeletal-body")

/** What screen 03 is showing. Spec §9's four states, and one for a pack that is not there. */
sealed interface DownloadPhase {
    /** Asked for; nothing transferring yet. */
    data object Queued : DownloadPhase

    data class Progress(val bytesDone: Long, val bytesTotal: Long) : DownloadPhase {
        /** 0 before the total is known, rather than a division by zero. */
        val fraction: Float get() = if (bytesTotal <= 0L) 0f else bytesDone.toFloat() / bytesTotal
    }

    /** [resumable] decides the wording: resume what is held, or start over. */
    data class Failed(val reason: PackFailure, val resumable: Boolean) : DownloadPhase

    data object Done : DownloadPhase

    /** The repository has no such pack. Nothing to wait for, so the step can be passed. */
    data object Unavailable : DownloadPhase
}

/**
 * What to show for a pack's status.
 *
 * [requestFailed] covers the failure a status cannot: `download()` throwing before the
 * repository has written anything, as it does when asked while offline. It only speaks
 * while the pack is still untouched — once bytes move, or the repository reports its own
 * failure, the status is the better witness.
 */
fun phaseOf(status: PackStatus?, requestFailed: Boolean): DownloadPhase = when (status) {
    null -> DownloadPhase.Unavailable
    PackStatus.Available, PackStatus.Queued ->
        if (requestFailed) DownloadPhase.Failed(PackFailure.NETWORK, resumable = true) else DownloadPhase.Queued
    is PackStatus.Downloading -> DownloadPhase.Progress(status.bytesDone, status.bytesTotal)
    PackStatus.Installed -> DownloadPhase.Done
    is PackStatus.Failed -> DownloadPhase.Failed(status.reason, status.resumable)
}

data class FirstDownloadUiState(
    val label: String? = null,
    val byteSize: Long = 0,
    val phase: DownloadPhase = DownloadPhase.Queued,
) {
    val canContinue: Boolean get() = phase == DownloadPhase.Done || phase == DownloadPhase.Unavailable

    /** A failure can be walked past: being offline must not hold anyone in onboarding (§14). */
    val canSkip: Boolean get() = phase is DownloadPhase.Failed
}

/**
 * Prototype screen 03: the first pack downloads.
 *
 * The repository owns the download; this only asks for it and reports. It asks once, and
 * only for a pack that is still `Available` — so reopening the screen while a download
 * runs, or after one failed, requests nothing. A failure waits for [onRetry].
 */
class FirstDownloadViewModel(
    private val packs: PackRepository,
    private val pack: PackId = FIRST_PACK,
) : ViewModel() {

    private val _state = MutableStateFlow(FirstDownloadUiState())
    val state: StateFlow<FirstDownloadUiState> = _state.asStateFlow()

    private var latest: PackState? = null
    private var requestFailed = false
    private var asked = false

    init {
        viewModelScope.launch {
            packs.packs
                // A listing that cannot be read is a pack that cannot be fetched.
                .catch { emit(emptyList()) }
                .collect { all ->
                    latest = all.firstOrNull { it.id == pack }
                    publish()
                    if (latest?.status == PackStatus.Available && !asked) request()
                }
        }
    }

    fun onRetry() {
        if (_state.value.phase !is DownloadPhase.Failed) return
        request()
    }

    private fun request() {
        asked = true
        requestFailed = false
        publish()
        viewModelScope.launch {
            try {
                packs.download(pack)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                requestFailed = true
                publish()
            }
        }
    }

    private fun publish() {
        val current = latest
        _state.value = FirstDownloadUiState(
            label = current?.label,
            byteSize = current?.byteSize ?: 0,
            phase = phaseOf(current?.status, requestFailed),
        )
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :shared:feature-onboarding:allTests`
Expected: BUILD SUCCESSFUL; `FirstDownloadViewModelTest` has sixteen tests passing on each target.

If `a_failure_is_not_retried_just_because_the_screen_was_reopened` fails with two requests: the second ViewModel saw `Failed`, not `Available`, so it must not have asked — check that `request()` is reached only from the `Available` branch and from `onRetry`.

- [ ] **Step 5: Commit**

```bash
git add shared/feature-onboarding
git commit -m "feat(onboarding): the first download is asked for once and reported through four states"
```

---

### Task 5: Screen 03

**Files:**
- Create: `shared/feature-onboarding/.../onboarding/Megabytes.kt`
- Test: `shared/feature-onboarding/src/commonTest/kotlin/com/ptk/anatomypro/feature/onboarding/MegabytesTest.kt`
- Modify: both `strings.xml` files in `shared/feature-onboarding`
- Create: `shared/feature-onboarding/.../onboarding/FirstDownloadScreen.kt`

**Interfaces:**
- Consumes: `FirstDownloadUiState`, `DownloadPhase` (Task 4); `onboarding_step`, `onboarding_back` (Task 3).
- Produces:
  ```kotlin
  fun megabytes(bytes: Long, decimalSeparator: String): String
  @Composable fun FirstDownloadScreen(
      state: FirstDownloadUiState, step: Int, stepCount: Int,
      onRetry: () -> Unit, onBack: () -> Unit, onFinish: () -> Unit,
      modifier: Modifier = Modifier,
  )
  ```

- [ ] **Step 1: Write the failing test for the size text**

Common Kotlin has no number formatter, and Polish writes "48,3" where English writes "48.3", so the size is formatted here and the separator is a string resource.

`MegabytesTest.kt`:

```kotlin
package com.ptk.anatomypro.feature.onboarding

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
    fun a_negative_count_is_shown_as_nothing_transferred() {
        assertEquals("0.0", megabytes(-5, "."))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :shared:feature-onboarding:allTests`
Expected: compilation FAILS with `Unresolved reference 'megabytes'`.

- [ ] **Step 3: Implement the size text**

`.../onboarding/Megabytes.kt`:

```kotlin
package com.ptk.anatomypro.feature.onboarding

/**
 * A byte count in megabytes to one decimal place, truncated.
 *
 * Decimal megabytes, as download sizes are quoted. Truncated rather than rounded so the
 * figure never claims bytes that have not arrived. [decimalSeparator] comes from a string
 * resource: common Kotlin has no locale-aware number formatter.
 */
fun megabytes(bytes: Long, decimalSeparator: String): String {
    val tenths = bytes.coerceAtLeast(0) / 100_000
    return "${tenths / 10}$decimalSeparator${tenths % 10}"
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :shared:feature-onboarding:allTests`
Expected: BUILD SUCCESSFUL, `MegabytesTest` five passing per target.

- [ ] **Step 5: Add the strings**

In `values/strings.xml`, before `</resources>`:

```xml
    <string name="decimal_separator">.</string>
    <string name="download_title">Your first pack</string>
    <string name="download_body">The model is downloaded once and then works offline.</string>
    <string name="download_size">%1$s MB</string>
    <string name="download_queued">Waiting to start…</string>
    <string name="download_progress">%1$s of %2$s MB · %3$d %</string>
    <string name="download_done">Downloaded. Ready to use offline.</string>
    <string name="download_unavailable">There is nothing to download in this build.</string>
    <string name="download_failed_network">The connection dropped.</string>
    <string name="download_failed_checksum">The file arrived damaged.</string>
    <string name="download_failed_space">There is not enough free space on this device.</string>
    <string name="download_failed_entitlement">This pack is not available to this account.</string>
    <string name="download_failed_kept">What was downloaded is kept.</string>
    <string name="download_resume">Resume download</string>
    <string name="download_restart">Start again</string>
    <string name="download_skip">Continue without it</string>
    <string name="download_finish">Open the atlas</string>
    <string name="download_progress_description">Download progress, %1$d percent</string>
```

In `values-pl/strings.xml`, before `</resources>`:

```xml
    <string name="decimal_separator">,</string>
    <string name="download_title">Twój pierwszy pakiet</string>
    <string name="download_body">Model pobiera się raz, a potem działa bez internetu.</string>
    <string name="download_size">%1$s MB</string>
    <string name="download_queued">Oczekiwanie na rozpoczęcie…</string>
    <string name="download_progress">%1$s z %2$s MB · %3$d %</string>
    <string name="download_done">Pobrano. Gotowe do pracy bez internetu.</string>
    <string name="download_unavailable">W tej wersji nie ma nic do pobrania.</string>
    <string name="download_failed_network">Połączenie zostało przerwane.</string>
    <string name="download_failed_checksum">Plik dotarł uszkodzony.</string>
    <string name="download_failed_space">Na urządzeniu brakuje wolnego miejsca.</string>
    <string name="download_failed_entitlement">Ten pakiet nie jest dostępny dla tego konta.</string>
    <string name="download_failed_kept">Pobrana część została zachowana.</string>
    <string name="download_resume">Wznów pobieranie</string>
    <string name="download_restart">Zacznij od nowa</string>
    <string name="download_skip">Kontynuuj bez pakietu</string>
    <string name="download_finish">Otwórz atlas</string>
    <string name="download_progress_description">Postęp pobierania, %1$d procent</string>
```

A literal percent sign is written as a single `%`: compose-resources does not unescape `%%` (design spec §27.5).

- [ ] **Step 6: Write the screen**

`.../onboarding/FirstDownloadScreen.kt`:

```kotlin
package com.ptk.anatomypro.feature.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import anatomypro.shared.feature_onboarding.generated.resources.Res
import anatomypro.shared.feature_onboarding.generated.resources.decimal_separator
import anatomypro.shared.feature_onboarding.generated.resources.download_body
import anatomypro.shared.feature_onboarding.generated.resources.download_done
import anatomypro.shared.feature_onboarding.generated.resources.download_failed_checksum
import anatomypro.shared.feature_onboarding.generated.resources.download_failed_entitlement
import anatomypro.shared.feature_onboarding.generated.resources.download_failed_kept
import anatomypro.shared.feature_onboarding.generated.resources.download_failed_network
import anatomypro.shared.feature_onboarding.generated.resources.download_failed_space
import anatomypro.shared.feature_onboarding.generated.resources.download_finish
import anatomypro.shared.feature_onboarding.generated.resources.download_progress
import anatomypro.shared.feature_onboarding.generated.resources.download_progress_description
import anatomypro.shared.feature_onboarding.generated.resources.download_queued
import anatomypro.shared.feature_onboarding.generated.resources.download_restart
import anatomypro.shared.feature_onboarding.generated.resources.download_resume
import anatomypro.shared.feature_onboarding.generated.resources.download_size
import anatomypro.shared.feature_onboarding.generated.resources.download_skip
import anatomypro.shared.feature_onboarding.generated.resources.download_title
import anatomypro.shared.feature_onboarding.generated.resources.download_unavailable
import anatomypro.shared.feature_onboarding.generated.resources.onboarding_back
import anatomypro.shared.feature_onboarding.generated.resources.onboarding_step
import com.ptk.anatomypro.core.data.model.PackFailure
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.Hairline
import com.ptk.anatomypro.core.designsystem.IncorrectAmber
import com.ptk.anatomypro.core.designsystem.TextTertiary
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Prototype screen 03: the first pack downloads.
 *
 * Progress is a bar and a sentence, because a bar alone says nothing to a screen reader and
 * nothing about size (spec §4.6: "a download UI that cannot report bytes is a spinner"). A
 * failure is named in words, never by colour alone (§12).
 */
@Composable
fun FirstDownloadScreen(
    state: FirstDownloadUiState,
    step: Int,
    stepCount: Int,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val separator = stringResource(Res.string.decimal_separator)

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.onboarding_step, step, stepCount),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
            Text(
                stringResource(Res.string.onboarding_back),
                style = MaterialTheme.typography.labelSmall,
                color = Accent,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(start = 16.dp, top = 14.dp),
            )
        }
        Text(
            stringResource(Res.string.download_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(stringResource(Res.string.download_body), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)

        state.label?.let { label ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(Res.string.download_size, megabytes(state.byteSize, separator)),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
            }
        }

        // Announced as it changes: someone who cannot see the bar hears each state arrive.
        Column(
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (val phase = state.phase) {
                DownloadPhase.Queued -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Accent, trackColor = Hairline)
                    Text(stringResource(Res.string.download_queued), style = MaterialTheme.typography.bodyMedium)
                }

                is DownloadPhase.Progress -> {
                    val percent = (phase.fraction * 100).toInt()
                    val description = stringResource(Res.string.download_progress_description, percent)
                    LinearProgressIndicator(
                        progress = { phase.fraction },
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
                        color = Accent,
                        trackColor = Hairline,
                    )
                    Text(
                        stringResource(
                            Res.string.download_progress,
                            megabytes(phase.bytesDone, separator),
                            megabytes(phase.bytesTotal, separator),
                            percent,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is DownloadPhase.Failed -> {
                    Text(stringResource(failureText(phase.reason)), style = MaterialTheme.typography.bodyMedium, color = IncorrectAmber)
                    if (phase.resumable) {
                        Text(
                            stringResource(Res.string.download_failed_kept),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary,
                        )
                    }
                }

                DownloadPhase.Done ->
                    Text(stringResource(Res.string.download_done), style = MaterialTheme.typography.bodyMedium)

                DownloadPhase.Unavailable ->
                    Text(stringResource(Res.string.download_unavailable), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val phase = state.phase
            if (phase is DownloadPhase.Failed) {
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(if (phase.resumable) Res.string.download_resume else Res.string.download_restart))
                }
                OutlinedButton(onClick = onFinish, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(Res.string.download_skip))
                }
            } else {
                Button(
                    onClick = onFinish,
                    enabled = state.canContinue,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(stringResource(Res.string.download_finish))
                }
            }
        }
    }
}

private fun failureText(reason: PackFailure): StringResource = when (reason) {
    PackFailure.NETWORK -> Res.string.download_failed_network
    PackFailure.CHECKSUM -> Res.string.download_failed_checksum
    PackFailure.OUT_OF_SPACE -> Res.string.download_failed_space
    PackFailure.NOT_ENTITLED -> Res.string.download_failed_entitlement
}
```

- [ ] **Step 7: Verify it compiles on both targets**

Run: `./gradlew :shared:feature-onboarding:allTests`
Expected: BUILD SUCCESSFUL, every test still passing.

If `LinearProgressIndicator(progress = { … })` does not resolve, the Material 3 in this Compose Multiplatform version takes `progress: Float`; pass `phase.fraction` directly.

- [ ] **Step 8: Commit**

```bash
git add shared/feature-onboarding
git commit -m "feat(onboarding): screen 03, the first pack download"
```

---

### Task 6: Onboarding becomes a flow

Today `App()` shows screen 01 until `onboarded` is set, and screen 01's button sets it. The button now moves to the next step; the last step sets `onboarded`.

The rule for whether there is a download step lives in `feature-onboarding`, not in `:shared`, because `:shared` has no test source set that has ever run: a test there would be the first to link the whole app, Filament included, on the simulator. The rule is one line and belongs beside the screen it governs.

**Files:**
- Create: `shared/feature-onboarding/.../onboarding/OnboardingSteps.kt`
- Test: `shared/feature-onboarding/src/commonTest/kotlin/com/ptk/anatomypro/feature/onboarding/OnboardingStepsTest.kt`
- Modify: `shared/feature-settings/src/commonMain/composeResources/values/strings.xml`
- Modify: `shared/feature-settings/src/commonMain/composeResources/values-pl/strings.xml`
- Modify: `shared/feature-settings/src/commonMain/kotlin/com/ptk/anatomypro/feature/settings/LanguageSelectionScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/ptk/anatomypro/Onboarding.kt`
- Modify: `shared/src/commonMain/kotlin/com/ptk/anatomypro/App.kt`

**Interfaces:**
- Consumes: `GoalsViewModel`, `GoalsScreen` (Tasks 2–3); `FirstDownloadViewModel`, `FirstDownloadScreen` (Tasks 4–5); `OnboardingRoute.Language` / `.Goals` / `.FirstDownload` from `com.ptk.anatomypro.navigation`; `NotBuiltPackRepository` from `com.ptk.anatomypro.core.data`; `SettingsViewModel.onOnboardingComplete()`.
- Produces:
  ```kotlin
  fun onboardingHasDownloadStep(packs: PackRepository): Boolean      // feature-onboarding
  @Composable internal fun OnboardingFlow(                            // :shared
      dependencies: AppDependencies, state: SettingsUiState, model: SettingsViewModel,
      navController: NavHostController,
  )
  ```
  and `LanguageSelectionScreen` gains `stepCount: Int` after `state`.

- [ ] **Step 1: Write the failing test**

`OnboardingStepsTest.kt`:

```kotlin
package com.ptk.anatomypro.feature.onboarding

import com.ptk.anatomypro.core.data.NotBuiltPackRepository
import com.ptk.anatomypro.core.data.fake.FakePackRepository
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingStepsTest {

    @Test
    fun with_a_pack_repository_there_is_a_download_step() {
        assertTrue(onboardingHasDownloadStep(FakePackRepository()))
    }

    @Test
    fun without_one_the_download_step_is_left_out_rather_than_left_to_crash() {
        // Production has no PackRepository yet: NotBuiltPackRepository throws on first use
        // (all-screens spec §6), and the pack is bundled in the app. Showing screen 03 there
        // would crash a first run.
        assertFalse(onboardingHasDownloadStep(NotBuiltPackRepository))
    }
}
```

- [ ] **Step 2: Run it to verify it fails, then implement**

Run: `./gradlew :shared:feature-onboarding:allTests`
Expected: compilation FAILS with `Unresolved reference 'onboardingHasDownloadStep'`.

`.../onboarding/OnboardingSteps.kt`:

```kotlin
package com.ptk.anatomypro.feature.onboarding

import com.ptk.anatomypro.core.data.NotBuiltPackRepository
import com.ptk.anatomypro.core.data.repository.PackRepository

/**
 * Whether onboarding has a download step.
 *
 * It does only where there is something to download with. Production has no PackRepository
 * yet — the one it is given throws on first use, by design (all-screens spec §6) — and the
 * pack ships inside the app, so there onboarding is two steps. When a real PackRepository
 * is built this returns true everywhere and can be deleted.
 */
fun onboardingHasDownloadStep(packs: PackRepository): Boolean = packs !is NotBuiltPackRepository
```

Run: `./gradlew :shared:feature-onboarding:allTests`
Expected: BUILD SUCCESSFUL, `OnboardingStepsTest` two passing per target.

- [ ] **Step 3: Give screen 01 a step count**

In both `shared/feature-settings/.../strings.xml` files, replace the `language_step` line.

`values/strings.xml`:

```xml
    <string name="language_step">STEP 1 OF %1$d</string>
```

`values-pl/strings.xml`:

```xml
    <string name="language_step">KROK 1 Z %1$d</string>
```

In `LanguageSelectionScreen.kt`, change the signature:

```kotlin
fun LanguageSelectionScreen(
    state: SettingsUiState,
    stepCount: Int,
    onInterfaceLocale: (String) -> Unit,
    onExaminationLocale: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
```

and the step label inside it:

```kotlin
        Text(stringResource(Res.string.language_step, stepCount), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
```

- [ ] **Step 4: Write the flow**

`shared/src/commonMain/kotlin/com/ptk/anatomypro/Onboarding.kt`:

```kotlin
package com.ptk.anatomypro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.ptk.anatomypro.core.data.AppDependencies
import com.ptk.anatomypro.feature.onboarding.FirstDownloadScreen
import com.ptk.anatomypro.feature.onboarding.FirstDownloadViewModel
import com.ptk.anatomypro.feature.onboarding.GoalsScreen
import com.ptk.anatomypro.feature.onboarding.GoalsUiState
import com.ptk.anatomypro.feature.onboarding.GoalsViewModel
import com.ptk.anatomypro.feature.onboarding.onboardingHasDownloadStep
import com.ptk.anatomypro.feature.settings.LanguageSelectionScreen
import com.ptk.anatomypro.feature.settings.SettingsUiState
import com.ptk.anatomypro.feature.settings.SettingsViewModel
import com.ptk.anatomypro.navigation.OnboardingRoute

/**
 * Prototype screens 01 to 03.
 *
 * [navController] is passed in because it must be remembered above ProvideAppLocale:
 * choosing a language on screen 01 rebuilds everything beneath it.
 *
 * Nothing here records which step was reached. Each choice is saved as it is made, so a
 * relaunch that starts again at step 1 shows every earlier answer already given.
 */
@Composable
internal fun OnboardingFlow(
    dependencies: AppDependencies,
    state: SettingsUiState,
    model: SettingsViewModel,
    navController: NavHostController,
) {
    val hasDownload = remember(dependencies.packs) { onboardingHasDownloadStep(dependencies.packs) }
    val stepCount = if (hasDownload) 3 else 2

    NavHost(navController = navController, startDestination = OnboardingRoute.Language) {
        composable<OnboardingRoute.Language> {
            LanguageSelectionScreen(
                state = state,
                stepCount = stepCount,
                onInterfaceLocale = model::onInterfaceLocale,
                onExaminationLocale = model::onExaminationLocale,
                onContinue = { navController.navigate(OnboardingRoute.Goals) },
            )
        }

        composable<OnboardingRoute.Goals> {
            val onContinue: () -> Unit = {
                if (hasDownload) navController.navigate(OnboardingRoute.FirstDownload) else model.onOnboardingComplete()
            }
            val atlas = dependencies.atlas
            if (atlas == null) {
                // The bundled pack is still installing. The step is shown, and can be passed.
                GoalsScreen(
                    state = GoalsUiState(),
                    step = 2,
                    stepCount = stepCount,
                    onToggle = {},
                    onBack = { navController.popBackStack() },
                    onContinue = onContinue,
                )
            } else {
                val goals: GoalsViewModel = viewModel(key = "goals") { GoalsViewModel(dependencies.settings, atlas) }
                val goalsState by goals.state.collectAsState()
                GoalsScreen(
                    state = goalsState,
                    step = 2,
                    stepCount = stepCount,
                    onToggle = goals::onToggle,
                    onBack = { navController.popBackStack() },
                    onContinue = onContinue,
                )
            }
        }

        composable<OnboardingRoute.FirstDownload> {
            val download: FirstDownloadViewModel = viewModel { FirstDownloadViewModel(dependencies.packs) }
            val downloadState by download.state.collectAsState()
            FirstDownloadScreen(
                state = downloadState,
                step = 3,
                stepCount = stepCount,
                onRetry = download::onRetry,
                onBack = { navController.popBackStack() },
                onFinish = model::onOnboardingComplete,
            )
        }
    }
}
```

- [ ] **Step 5: Show the flow from `App()`**

In `App.kt`, beside the existing `val navController = rememberNavController()`, add:

```kotlin
        // Its own controller, and up here for the same reason as the one above: choosing a
        // language on screen 01 rebuilds everything beneath ProvideAppLocale.
        val onboardingNavController = rememberNavController()
```

Replace the `!settingsState.settings.onboarded -> LanguageSelectionScreen(…)` branch, comment included, with:

```kotlin
                        // Prototype screens 01 to 03. Shown until the last step is passed,
                        // not until a setting differs from its default: accepting the
                        // defaults is a decision too, and it has to be recorded as one.
                        !settingsState.settings.onboarded -> OnboardingFlow(
                            dependencies = dependencies,
                            state = settingsState,
                            model = settingsModel,
                            navController = onboardingNavController,
                        )
```

Remove the now-unused `import com.ptk.anatomypro.feature.settings.LanguageSelectionScreen` from `App.kt`.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :shared:feature-settings:allTests :shared:feature-onboarding:allTests`
Expected: BUILD SUCCESSFUL, nothing changed since Step 2. (`:shared` itself is compiled by the app builds in the next step.)

- [ ] **Step 7: Build both apps**

Run: `./gradlew :androidApp:assembleDebug :androidApp:assembleRelease`
Expected: BUILD SUCCESSFUL. (`assembleRelease` proves the release source set compiles without the fake module.)

Run:

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17' build
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 8: Commit**

```bash
git add shared/src shared/feature-settings
git commit -m "feat(onboarding): three steps where there is a pack to fetch, two where there is not"
```

---

### Task 7: Run it, on fakes and for real

Debug builds start onboarded (`fakeAppDependencies()` sets `onboarded = true`), so the flow is unreachable there, and production skips screen 03. An intent extra gives the debug build a way in.

**Files:**
- Modify: `androidApp/src/debug/kotlin/com/ptk/anatomypro/EntryDependencies.kt`
- Modify: `docs/state-of-play.md`

**Interfaces:**
- Consumes: `fakeAppDependencies()`, `FakeSettingsRepository`, `FakePackRepository(downloadFailure, failingAttempts, initiallyInstalled)`, `FakeBehaviour(delay)`.
- Produces: nothing other code relies on.

- [ ] **Step 1: Let a debug build start in onboarding**

Replace the body of `androidApp/src/debug/kotlin/com/ptk/anatomypro/EntryDependencies.kt` below the package line with:

```kotlin
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ptk.anatomypro.core.data.AppDependencies
import com.ptk.anatomypro.core.data.fake.FakeBehaviour
import com.ptk.anatomypro.core.data.fake.FakePackRepository
import com.ptk.anatomypro.core.data.fake.FakeSettingsRepository
import com.ptk.anatomypro.core.data.fake.fakeAppDependencies
import com.ptk.anatomypro.core.data.model.AppSettings
import com.ptk.anatomypro.core.data.model.PackFailure
import kotlin.time.Duration.Companion.milliseconds

/**
 * Debug builds run on fakes for everything except the atlas.
 *
 * The atlas is the real one, Room over the bundled pack, because the 3D canvas always draws
 * that pack: a fake atlas beside it would show a tree that does not match the model, and
 * picking a structure on the model would select nothing, since the pack's ids are not in the
 * fixture. The fake atlas stays what feature tests use.
 *
 * Onboarding is skipped unless asked for, because fake settings forget everything and a
 * debug build that opened on screen 01 every launch would be in the way:
 *
 *     adb shell am start -n com.ptk.anatomypro/.MainActivity --ez onboarding true
 *     adb shell am start -n com.ptk.anatomypro/.MainActivity --ez onboarding true --es packFailure NETWORK
 *
 * `packFailure` takes a PackFailure name and fails the first attempt only, so the resume
 * can be seen working.
 */
@Composable
internal fun appDependencies(): AppDependencies {
    val atlas = rememberAtlas()
    val intent = LocalActivity.current?.intent
    val onboarding = intent?.getBooleanExtra("onboarding", false) ?: false
    val packFailure = intent?.getStringExtra("packFailure")
        ?.let { name -> PackFailure.entries.firstOrNull { it.name == name } }

    val fakes = remember(onboarding, packFailure) {
        val base = fakeAppDependencies()
        if (!onboarding) {
            base
        } else {
            base.copy(
                settings = FakeSettingsRepository(AppSettings(onboarded = false)),
                // Slow enough to watch: the fake's steps are otherwise instantaneous.
                packs = FakePackRepository(
                    behaviour = FakeBehaviour(delay = 600.milliseconds),
                    downloadFailure = packFailure,
                    failingAttempts = 1,
                    initiallyInstalled = emptySet(),
                ),
            )
        }
    }
    return remember(atlas, fakes) { fakes.copy(atlas = atlas?.repository) }
}
```

If `androidx.activity.compose.LocalActivity` does not resolve, the activity-compose version predates it; use `LocalContext.current as? android.app.Activity` instead.

`FakeBehaviour.delay` is applied once, before the download begins, so the bar will sit on "queued" for 600 ms and then run through its steps at once. That is enough to see the queued state; the progress state is seen in Step 3 by the failure, which stops it half way.

- [ ] **Step 2: Build and install**

Run: `./gradlew :androidApp:installDebug` with the emulator `Medium_Phone_API_36.1` running.
Expected: BUILD SUCCESSFUL, `Installed on 1 device`.

- [ ] **Step 3: Walk the flow on Android, on fakes**

```bash
adb shell am force-stop com.ptk.anatomypro
adb shell am start -n com.ptk.anatomypro/.MainActivity --ez onboarding true
```

Check, and write down what was seen for each:

1. Screen 01 says "KROK 1 Z 3". Continue goes to screen 02.
2. Screen 02 lists the installed atlas's systems, each with its local and Latin name. With nothing chosen the button reads "Pomiń na razie".
3. Tapping a system shows "WYBRANE" on its row, the count line appears, and the button reads "Dalej". Tapping it again undoes all three.
4. "Wstecz" and the system back gesture both return to screen 01, and the choice is still there on coming forward.
5. Screen 03 says "KROK 3 Z 3", names "Układ kostny" and "48,3 MB", shows "Oczekiwanie na rozpoczęcie…" and then "Pobrano." The button is disabled until then.
6. "Otwórz atlas" lands on the atlas with the bottom bar.

Then the failure:

```bash
adb shell am force-stop com.ptk.anatomypro
adb shell am start -n com.ptk.anatomypro/.MainActivity --ez onboarding true --es packFailure NETWORK
```

7. Screen 03 stops with "Połączenie zostało przerwane." and "Pobrana część została zachowana.", offering "Wznów pobieranie" and "Kontynuuj bez pakietu".
8. "Wznów pobieranie" finishes the download.
9. Repeat with `--es packFailure CHECKSUM`: the message is "Plik dotarł uszkodzony.", there is no "kept" line, and the button reads "Zacznij od nowa".
10. On screen 01 choose English, go forward: screens 02 and 03 are in English, "48.3 MB" has a point.
11. With TalkBack on, screen 02's rows announce as checkboxes with their state, and screen 03 announces each change of state.

- [ ] **Step 4: Walk the flow on iOS, for real**

Production dependencies, so two steps. Use the simulator's database to return to onboarding (Appium's XCUITest driver is installed, so `mcp__mobile__input` can tap; coordinates are in the space of the last `mcp__mobile__screen` capture):

```bash
U=$(xcrun simctl list devices booted | grep -o '[0-9A-F-]\{36\}' | head -1)
DB="$(xcrun simctl get_app_container $U com.ptk.anatomypro.AnatomyPro data)/Documents/anatomy.db"
xcrun simctl terminate $U com.ptk.anatomypro.AnatomyPro
sqlite3 "$DB" "insert or replace into preference values('onboarded','false');"
xcrun simctl launch $U com.ptk.anatomypro.AnatomyPro
```

Check:

1. Screen 01 says "KROK 1 Z 2".
2. Screen 02 says "KROK 2 Z 2" and lists the systems of the bundled pack.
3. Choosing one and continuing lands on the atlas. No screen 03 appears, and nothing crashes.
4. `sqlite3 "$DB" "select * from preference where key in ('onboarded','study.systems');"` shows `onboarded|true` and the chosen system id.

- [ ] **Step 5: Record it**

In `docs/state-of-play.md`, in "What exists", change the screen count and list to include 02 and 03, and add under it:

```markdown
Onboarding is three steps on fakes and two in production: screen 03 needs a `PackRepository`,
and production's refuses until downloads are built. `adb shell am start -n
com.ptk.anatomypro/.MainActivity --ez onboarding true` starts a debug build in onboarding.
```

Under "What is verified, and what is not", add what Steps 3 and 4 found, including anything that was not checked (TalkBack speech has not been heard on this emulator before; say so if it still is not).

- [ ] **Step 6: Run everything once more**

Run: `./gradlew allTests`
Expected: BUILD SUCCESSFUL on both targets. Stop any app running on a simulator first.

- [ ] **Step 7: Commit**

```bash
git add androidApp/src/debug docs/state-of-play.md
git commit -m "feat(onboarding): a debug build can start in onboarding; screens 02 and 03 run"
```

---

## Spec coverage

| Spec requirement | Where |
|---|---|
| §9 screen 02: none selected; some selected | Task 2 tests 1, 3, 4; Task 7 Step 3 items 2–3 |
| §9 screen 03: queued; progress; failed-resumable; done | Task 4 mapping tests; Task 7 Step 3 items 5, 7, 8 |
| §4.8: `studiedSystems` on `AppSettings`, no new repository | Task 2 |
| §4.6: a download UI reports bytes | Task 5 |
| §6: production never constructs a fake; refusing repositories are not called | Task 6 `onboardingHasDownloadStep`; Task 7 Step 4 |
| §8: every string a resource, English base, Polish beside it | Tasks 3, 5, 6 |
| §10: a ViewModel test per screen, both targets; no Compose UI tests | Tasks 2, 4 |
| Design §12: 44 dp targets, state not by colour alone, announcements | Tasks 3, 5; Task 7 Step 3 item 11 |
| Design §14: a failed download is resumable | Task 4 retry test; Task 7 Step 3 item 8 |
