# Anatomy Pro — Design Specification

- **Date:** 2026-08-29
- **Status:** Approved for planning
- **Phase:** Feasibility-first. Phase 0 is a go/no-go gate.

---

## 1. Purpose

A mobile application (Android + iOS) presenting an interactive 3D model of the human
body for medical students, with auto-generated quizzes per topic, a daily quiz, and a
global leaderboard.

The learner-facing goal is recall under exam conditions: identifying a structure on a
three-dimensional body and naming it correctly in Latin. Everything in this design
serves that loop.

---

## 2. Locked decisions

| Decision | Choice |
|---|---|
| Anatomical scope (v1) | Full body, all systems |
| Terminology | Latin (Terminologia Anatomica) canonical; Polish + English display; locale-extensible |
| UI framework | Kotlin Multiplatform + Compose Multiplatform |
| 3D renderer | Filament (SceneView on Android, Swift/Obj-C++ shim on iOS) |
| Asset format | glTF 2.0, Draco/meshopt compressed |
| Backend | Ktor + PostgreSQL |
| Model source | To be selected — see `docs/model-sourcing-spec.md` |
| Monetization | Freemium: skeletal system free, other systems by subscription |
| Identity | Anonymous-first, upgradeable to Google/Apple sign-in |
| Daily quiz integrity | Server-authoritative |
| Study features (v1) | Layer peeling and isolation; search and structure detail pages |
| Offline | Small install; on-demand content packs, usable offline once downloaded |

### Deferred (explicitly not v1)

Spaced repetition, cross-section/clipping planes, physiological animation, AR,
text-based MCQs (function, innervation, relations), free-text Latin recall,
cohort/university leaderboards.

Animation and AR are deferred but **not designed out** — the renderer interface must
not foreclose them.

---

## 3. Architecture

Five components, not one app.

| Component | Technology | Responsibility |
|---|---|---|
| Mobile app | KMP + Compose Multiplatform | All UI, all state, all logic |
| Renderer | Filament, hosted per platform | Draws and picks geometry. Nothing else. |
| Backend | Ktor + PostgreSQL | Daily quiz authority, leaderboard, entitlements, pack manifest |
| Asset pipeline | Blender + glTF-transform | Source atlas to compressed packs plus structure database |
| Reviewer tool | Small web app | Medical verification of names and definitions |

The reviewer tool is the one most easily forgotten and is on the critical path. See §7.

### 3.1 Module layout

This is the target layout, not current state. Only a subset exists today — see
§20 for which modules are real and why the rest are deferred.

```
anatomy-pro/
├── androidApp/                  # Android entry point
├── iosApp/                      # Xcode project; hosts the Filament shim
├── shared/
│   ├── core-model/              # Domain types. Pure Kotlin, no dependencies.
│   ├── core-data/               # Repositories, SQLDelight, Ktor client
│   ├── core-designsystem/       # Compose theme, tokens, shared components
│   ├── renderer-api/            # AnatomyRenderer interface + events (common)
│   ├── renderer-filament/       # expect/actual platform implementations
│   ├── feature-atlas/
│   ├── feature-search/
│   ├── feature-quiz/
│   ├── feature-daily/
│   ├── feature-leaderboard/
│   └── feature-profile/
├── ios-renderer/                # Swift + Obj-C++ Filament host
├── backend/                     # Ktor service
├── pipeline/                    # Blender and glTF-transform scripts
├── reviewer/                    # Verification web tool
└── docs/
```

Each feature module owns its screens, view models, and navigation entry. Feature
modules depend on `core-*` and `renderer-api`, never on each other, and never on
`renderer-filament`.

---

## 4. The renderer boundary

The most important architectural line in the system.

**Kotlin owns all state. The renderer is a pure projection of that state.** The
renderer holds no truth the app cannot reconstruct.

```kotlin
interface AnatomyRenderer {
    val events: Flow<RendererEvent>

    suspend fun loadPack(pack: PackId, source: MeshSource)
    suspend fun unloadPack(pack: PackId)

    fun setSystemVisibility(system: SystemId, visible: Boolean)
    fun setOpacity(structures: Set<StructureId>, alpha: Float)
    fun isolate(structure: StructureId?, ghostNeighbours: Boolean)
    fun highlight(structures: Set<StructureId>, style: HighlightStyle)

    fun focusCamera(structure: StructureId, durationMs: Int)
    fun setCameraPose(pose: CameraPose)

    fun setPickingEnabled(enabled: Boolean)
}

sealed interface RendererEvent {
    data object Ready : RendererEvent
    data class Picked(val structure: StructureId?) : RendererEvent
    data class PackLoaded(val pack: PackId) : RendererEvent
    data class LoadProgress(val pack: PackId, val fraction: Float) : RendererEvent
    data class MemoryPressure(val residentBytes: Long) : RendererEvent
    data class Error(val code: String, val message: String) : RendererEvent
}
```

### Why this boundary earns its cost

1. **Recovery.** If the render surface is lost — GPU context loss, backgrounding,
   memory pressure — the app replays its own state. The user sees a flicker, not a
   crash.
2. **Testability.** `FakeAnatomyRenderer` makes every screen testable without a GPU.
   `Picked` events can be injected directly, so the entire quiz loop is unit-testable.
3. **Substitutability.** A WebGL/three.js implementation is a documented fallback
   (§14). Because both Filament and three.js consume glTF, swapping renderers touches
   no content, no quiz logic, and no UI.

### 4.1 Platform hosting

- **Android:** SceneView, Google's Compose-friendly wrapper over Filament, owns the
  surface and scene graph.
- **iOS:** A small Swift/Obj-C++ target built from the Xcode app target. The Kotlin
  framework has **no compile-time dependency on Filament** — it talks to a narrow C
  shim, which makes the graphics provider a link-time choice rather than a code change.

Picking uses Filament's built-in `pick()`, which returns a `PickingQueryResult`
carrying the renderable, depth, and frag coordinates. Renderable entities map back to
`StructureId` through the pack's node-name index.

---

## 5. Data model

```
Structure {
  id            StructureId      stable slug, never reused
  taCode        String?          Terminologia Anatomica code where known
  names         Map<Locale, String>
  synonyms      Map<Locale, List<String>>
  definition    Map<Locale, String>
  parentId      StructureId?     hierarchy: navigation and quiz distractors
  systemId      SystemId
  regionId      RegionId
  laterality    LEFT | RIGHT | MEDIAN
  meshRefs      List<MeshRef>    (packId, nodeName) — one concept may span meshes
  verification  Map<Locale, VerificationState>
  packId        PackId
}

VerificationState = UNVERIFIED | VERIFIED | DISPUTED
```

Three details that cause defects if omitted:

- **Laterality.** Left and right scapula are two meshes but one concept. A quiz must
  accept either mesh when asking for "scapula", and must be able to ask specifically
  for the left one when difficulty demands it.
- **Verification is per-locale.** The Latin name may be correct while the Polish
  translation is wrong. A single boolean would force re-verification of everything
  whenever one language is corrected.
- **`meshRefs` is a list.** Structures such as the vertebral column or a muscle group
  span many nodes. A one-to-one structure-to-mesh assumption breaks on real data.

Stored locally in SQLDelight; shipped as a base dataset with per-pack deltas.

---

## 6. Content pipeline

```
Source atlas (.blend)
  → Blender export script     : per-structure nodes, naming convention, units, axes
  → glTF-transform            : decimation, LOD generation, Draco/meshopt compression
  → Pack assembly             : region/system chunking, node-name index, checksums
  → Structure database        : names, hierarchy, definitions, laterality
  → CDN
```

The pipeline is scripted and version-controlled. Re-running it on a corrected source
must be deterministic — the same input produces byte-identical packs, so pack versions
change only when content genuinely changes.

**Pack granularity** is the unit of download, the unit of entitlement, and the unit of
verification release. These three must align; if they diverge, the paywall and the
verification schedule start fighting each other.

### 6.1 Performance is decided here, not in the renderer

The dominant risk to frame rate is loading thousands of individually pickable meshes at
once. Mitigations belong to the pipeline and the loading strategy, not to engine tuning:

- Load only the active region and system — target a few hundred visible meshes.
- Generate LODs during pipeline runs; never at runtime.
- Merge non-interactive geometry that is never a quiz answer.
- Set an explicit resident-memory budget and evict packs deliberately.

**Budget to validate in Phase 0:** sustained 60fps and under 400 MB resident on a
mid-range Android device and an older iPhone, with a full region loaded.

---

## 7. Verification pipeline

The largest non-engineering risk in the project. A full-body atlas is thousands of
structures; at three locales each, with a single medical reviewer, this is the item
most likely to stall the release.

**Governing rule: only `VERIFIED` structures may be used as quiz answers.**
Unverified structures still render and remain browsable, marked subtly in the UI.

Consequences, all of them deliberate:

- The skeletal pack ships as soon as its structures pass review, while other systems
  are still being checked. Release is staged by pack, not blocked on the whole atlas.
- Quiz quality is bounded by verified content, so a labeling error cannot become a
  question that teaches a student something false.
- `DISPUTED` is a first-class state. The reviewer can reject without needing to supply
  the correct answer immediately.

The reviewer tool shows the highlighted mesh alongside proposed names in all locales,
and records verify/edit/dispute per structure per locale, with an audit trail.

---

## 8. Quiz engine

Both v1 formats generate from the structure graph. No question authoring.

- **Tap the structure.** Prompt with a name in the target locale; validate the pick.
- **Name the highlighted structure.** Highlight one mesh; offer four options.

### 8.1 Difficulty via distractor distance

Difficulty is a single lever: how close the wrong options sit in the structure graph.

| Tier | Distractors drawn from |
|---|---|
| Easy | A different system |
| Medium | The same region, different parent |
| Hard | Siblings under the same parent |

This is the reason auto-generation is viable. Graded difficulty falls out of the
hierarchy at no authoring cost, and it extends automatically to every new pack.

### 8.2 Sessions

Topic quizzes are scoped to a system or region and generated **on-device**, so they
work offline. Question selection is seeded and reproducible for debugging.

---

## 9. Daily quiz, leaderboard, identity

### 9.1 Server authority

The backend derives the day's question set from a seed over the UTC date, so every
user receives an identical set. The flow:

1. `GET /daily/{date}` returns questions **without answer keys**; the server records
   the fetch time as the clock start.
2. The client plays the round locally.
3. `POST /daily/{date}/submit` sends answers. The server scores against its own key,
   computes elapsed time, and returns the result and rank.

Integrity measures for v1:
- Answer keys never leave the server.
- One attempt per user per day, enforced server-side.
- Elapsed time measured server-side from fetch to submit.
- Submissions faster than a plausibility floor are rejected.

A determined attacker can still script the API. That is accepted for v1; attestation
(Play Integrity / App Attest) and outlier detection are v2, justified once the
leaderboard matters to people.

**Connectivity:** the daily quiz requires a network connection to start and to submit.
Offline play is practice mode and is never ranked. This avoids an entire class of
clock-tampering and late-submission ambiguity.

### 9.2 Scoring

Correctness is primary; elapsed time breaks ties.

### 9.3 Identity

Anonymous-first: a device token and a chosen nickname, playable immediately. Optional
upgrade to Google/Apple sign-in carries streaks across devices. Nicknames are
moderated for abuse.

v1 boards: **global daily** and **personal streak**. Cohort and university boards are
deferred because self-declared affiliation requires moderation.

---

## 10. Packs, entitlements, offline

Install size is kept small. The free skeletal pack downloads on first launch.

Pack manifest entries carry id, version, byte size, checksum, URL, and entitlement.
Downloads are resumable, checksum-verified, and swapped atomically — a partial or
corrupt pack is never visible to the renderer.

**The paywall boundary is the pack boundary.** Skeletal is free; other systems require
subscription. Entitlements are resolved server-side when the manifest is issued, so an
unentitled client is never handed a download URL.

---

## 11. Backend

Ktor with PostgreSQL and Flyway migrations. Endpoints:

| Endpoint | Purpose |
|---|---|
| `POST /auth/anonymous` | Issue device token |
| `POST /auth/upgrade` | Link Google/Apple identity |
| `GET /packs/manifest` | Entitlement-filtered pack list |
| `GET /daily/{date}` | Daily questions, no keys |
| `POST /daily/{date}/submit` | Score and rank |
| `GET /leaderboard/daily/{date}` | Rankings |
| `GET /profile` | Streak, history |
| `GET /structures/delta` | Metadata sync since version |

Deployed as a single container. Horizontal scaling is not a v1 concern.

---

## 12. Accessibility

Treated as a requirement, not a polish item.

Compose emits semantics automatically for all 2D UI, which covers most screens. The 3D
canvas is opaque to screen readers and needs an explicit alternative:

- **Tree-navigation mode.** The structure hierarchy is traversable as a list with
  screen-reader announcements; selecting a structure focuses the camera and announces
  the name in the active locale.
- **Highlighting uses outline and luminance, never hue alone** — colour-blind safe.
- **Timers are disableable**, including in the daily quiz (unranked when disabled).
- Minimum 44dp touch targets; dynamic type respected throughout.

---

## 13. Internationalization

Latin (Terminologia Anatomica) is the canonical key; Polish and English are display
locales. Names, synonyms, and definitions are locale maps in the data model, so
**adding a language is a data drop, not a code change.**

UI strings are separately localized. The two are independent: a user may read the UI in
Polish while quizzing in Latin.

---

## 14. Error handling

| Failure | Response |
|---|---|
| Pack download interrupted | Resume; verify checksum; atomic swap on success only |
| Render surface lost | Reinitialize and replay Kotlin-held state; user sees a flicker |
| Memory pressure | Evict least-recently-used packs; emit `MemoryPressure` |
| Daily quiz offline | Block entry with a clear message; offer practice mode |
| Submit fails after play | Retry with backoff; ranked if received within the day |
| Structure unverified | Renders and browsable, marked; never a quiz answer |

**Documented fallback.** If the iOS Filament shim proves unworkable in Phase 0, the
fallback is three.js in a WebView behind the same `AnatomyRenderer` interface. This
carries known costs — WebKit caps a page near 1.4–1.5 GB with no eviction control, and
OS updates have broken WebGL apps in the field — which is why it is the fallback and
not the primary. The interface makes the choice reversible either way.

---

## 15. Testing strategy

- **Quiz generation:** unit tests for distractor tiering, laterality handling, and
  verified-only filtering. A golden test pins daily-set determinism for a fixed seed.
- **Scoring:** unit tests including tie-breaking and rejection thresholds.
- **Pack management:** resume, checksum failure, atomic swap, eviction under budget.
- **Screens:** driven by `FakeAnatomyRenderer`; no GPU required in CI.
- **Renderer shim:** contract tests asserting the same interface behaviour on both
  platforms — load, pick, highlight, unload.
- **Backend:** Testcontainers with real PostgreSQL; endpoint and integrity tests
  including duplicate-submission and fast-submission rejection.

---

## 16. Phasing

### Phase 0 — Feasibility gate (do nothing else until it answers)

Convert one region of the source atlas through Blender to compressed glTF. Get it
loading, picking, and highlighting through Compose Multiplatform on **a real iPhone**
and a mid-range Android device. Measure frame rate and resident memory against the §6.1
budget.

The specific unknown being retired is the **iOS Filament shim**. Android is proven
territory; iOS is not. Exit criteria: budget met on both platforms, or the fallback in
§14 is invoked.

### Phase 1 — Atlas
Skeletal pack, offline download, search, structure detail pages, layer peeling and
isolation. No backend.

### Phase 2 — Quiz engine
Both question formats, topic quizzes, on-device generation, local progress.

### Phase 3 — Backend
Identity, daily quiz, leaderboard, streaks.

### Phase 4 — Commercial
Subscription, entitlements, remaining packs released as they pass verification.

Phases 1 and 2 ship a genuinely useful free product before any backend exists. This is
deliberate: it means the project has value even if it stops early.

---

## 17. Risks

| # | Risk | Severity | Mitigation |
|---|---|---|---|
| 1 | Content verification capacity — thousands of structures × 3 locales, one reviewer | **Highest** | Verified-only quiz rule; staged release per pack; purpose-built reviewer tool |
| 2 | Source model licensing vs. freemium | High | Resolved by model sourcing decision; fallback is to gate the learning system rather than the content |
| 3 | iOS Filament shim unproven | High | Phase 0 gate; documented WebView fallback |
| 4 | Source mesh quality unsuitable for mobile | Medium | Pipeline decimation; budget manual cleanup |
| 5 | Solo capacity | Medium | Phasing designed so each phase ships something usable |

Risk 1 is not a coding problem and cannot be solved by tooling alone. It should drive
scheduling decisions more than anything technical in this document.

---

## 18. Open questions

- Final model source. Decision criteria and acceptance checklist in `docs/model-sourcing-spec.md`; recommendation is to defer until Phase 0 produces real decimation and frame-rate numbers.
- Subscription pricing and trial length.
- Whether Polish and English launch together or English follows.

---

## 19. Companion documents

- `docs/model-sourcing-spec.md` — requirements, candidate sources, licensing analysis, and acceptance checklist for the 3D atlas.
- `docs/design-prompt-prototype-screens.md` — brief for generating the prototype screens.
- **Prototype screens** — 21 screens across five flows, plus a 135% large-type stress test, with a
  design-rationale screen carrying the colour system, type scale, and spacing set:
  <https://claude.ai/code/artifact/5e297318-f3c4-446d-8287-1b6aa185e304>. The token values there are
  authoritative for `core-designsystem`; §12's accessibility rules are realised as measured contrast
  ratios on that screen.

---

## 20. Addendum — 2026-09-06: repository consolidation and the Phase 0 module slice

This section records what §3.1 looks like on disk, and why the gap is deliberate. It
supersedes nothing; §3.1 remains the target.

### 20.1 One repository

Design documents and application code began in two unrelated repositories. They are
consolidated into a single repository rooted at the Gradle build, with `docs/` merged in
using `--allow-unrelated-histories` so the original commits survive rather than being
copied.

The reason is coupling: §4's `AnatomyRenderer` interface is simultaneously a design
decision and a Kotlin file. When it changes, both must change, and a reviewer needs to
see that as one commit. Two repositories make that impossible to enforce and easy to
forget.

`backend/`, `pipeline/`, and `reviewer/` join the same root when their phases begin.
They are separate build systems, not separate repositories.

### 20.2 Modules that exist now

Phase 0 (§16) is a go/no-go gate whose deliverable is an answer, not a product: one
region loading, picking, and highlighting on a real iPhone. Four modules serve that
question. The other seven in §3.1 are boundaries for features that do not yet exist,
and drawing them now would fix those boundaries before any screen has taught us where
they belong.

| Module | Contents | Phase |
|---|---|---|
| `shared/core-model` | Identity types only: `StructureId`, `SystemId`, `RegionId`, `PackId`, `Laterality`, `MeshRef`, `VerificationState` | now |
| `shared/renderer-api` | §4 interface, `RendererEvent`, `FakeAnatomyRenderer` | now |
| `shared/renderer-filament` | `expect`/`actual` factory; both actuals stubbed | now |
| `shared/core-designsystem` | Theme, tokens, highlight styles | now |
| `shared` | Umbrella; assembles `Shared.framework` for iOS | now |
| `ios-renderer/` | Directory and README; Xcode target created in Phase 0 | now |
| `core-data` | SQLDelight, Ktor client, the full §5 `Structure` record | Phase 1 |
| `feature-atlas`, `feature-search` | | Phase 1 |
| `feature-quiz` | | Phase 2 |
| `feature-daily`, `feature-leaderboard`, `feature-profile` | | Phase 3 |

Delivered 2026-09-06. Phase 0 begins from here: the next change to this repository should
be a real `AnatomyRenderer` implementation, not another module.

Note that `core-model` ships identity types only. The full §5 `Structure` record —
names, definitions, verification maps — arrives with `core-data`, because it is
meaningless without persistence to hold it.

Dependency direction is enforced by the graph, not by convention:

```
core-model  ←  renderer-api  ←  renderer-filament
                    ↑
            core-designsystem  ←  shared (umbrella)  ←  androidApp, iosApp
```

Nothing depends on `renderer-filament` except the umbrella and the platform entry
points. This is what makes the §14 three.js fallback a link-time decision.

### 20.3 Two consequences worth stating

**`FakeAnatomyRenderer` lives in `commonMain`, not `commonTest`.** Kotlin Multiplatform
has no working equivalent of Java test fixtures, so a fake confined to a test source set
cannot be consumed by another module's tests. Since §15 makes every screen test depend
on that fake, confining it would defeat the testing strategy. It ships in the release
binary; it is small, and the alternative is worse.

**Both `renderer-filament` actuals are stubs that throw.** Implementing them *is* Phase
0. The module exists now so the socket, the dependency direction, and the fallback seam
are settled before the risky work starts — not to suggest the renderer is underway.

**Value classes do not survive the Objective-C boundary.** `StructureId`, `PackId`, and
their siblings are `@JvmInline value class`, and Kotlin/Native erases them in the generated
framework header: Swift receives `id` — that is, `Any` — with no type information at all,
where `MeshRef` shows `initWithPackId:(id)packId`. Verified against the generated
`Shared.h`, not assumed.

The practical rule: any Kotlin API that Swift calls must take and return `String` for
identifiers, never the value classes, and convert on the Kotlin side. The `FilamentBridge`
in §4.1 already obeys this by passing node names rather than `StructureId`s — a decision
taken for shim narrowness that turns out to be load-bearing for a second reason.

### 20.4 Build configuration

Shared Android and Kotlin Multiplatform configuration lives in a `build-logic/` included
build as precompiled script plugins, rather than being repeated per module.

At four modules this is close to break-even. It is chosen for the twelve-module end
state in §3.1: retrofitting convention plugins later means editing every module written
in the interim. The known cost is that precompiled script plugins cannot resolve the
`libs` version catalog directly and must reach it through `VersionCatalogsExtension`.
`buildSrc` was rejected because any edit to it invalidates the configuration cache,
which this build has enabled.

## 21. Addendum — 2026-09-06: the iOS Filament host

Phase 0's iOS half. §4.1 and §20 remain the target; this records how the shim was actually
built, and the four things that were learned the hard way.

### 21.1 The seam runs Kotlin → C, not Swift → Kotlin

§4.1 says Kotlin "talks to a narrow C shim". The code committed in §20 did the opposite:
`FilamentBridge` was a Kotlin interface that Swift conformed to and injected at startup
through `FilamentBridgeRegistry`. Both files are deleted.

The C direction wins on one argument, and it is decisive. When Kotlin links the shim
itself, an `iosSimulatorArm64Test` binary can link it too — so §15's contract tests run
against the real renderer, headless, from the command line. Under the registry design the
renderer could not exist until a Swift app had booted, which would have left the contract
tests permanently confined to the fake and the gate answerable only by looking at a screen.

`ios-renderer/` is a Gradle project holding `CMakeLists.txt`, `include/anatomy_renderer.h`,
and one Objective-C++ translation unit. It applies no Kotlin plugin and produces no JVM
artifacts.

### 21.2 No callback crosses the boundary

The shim owns a mutex-guarded event queue; Kotlin drains it with `ar_poll_event`. Nothing
calls into Kotlin/Native from Filament's backend thread, which removes the fiddliest part
of the cinterop route rather than solving it.

Frames are pumped by the caller — a coroutine in the app, a loop in the tests — so the
renderer keeps no thread of its own and stays the passive projection §4 requires.

### 21.3 Four things that were not obvious

**Filament's `beginFrame` refuses frames.** With too many frames in flight it returns
false, and a tight render loop gets roughly every other frame drawn. A picking readback
then never completes. `ar_render_frame` returns whether the frame was drawn, and callers
that need frames to land drain the GPU between them.

**Picking callbacks arrive late.** Filament's default `CallbackHandler` dispatches
"opportunistically", which in practice meant a picking result sat undelivered until the
engine was destroyed. The shim supplies a handler that runs the callback inline; this is
safe only because the queue is mutex-guarded and nothing reaches Kotlin from it.

**Offscreen rendering must disable frame pacing.** `Renderer::setDisplayInfo` with
`refreshRate = 0`.

**A Gradle `dependsOn` is not an input.** The Kotlin/Native link task took the staged
libraries as a task dependency only, so a rebuilt `libAnatomyRenderer.a` left the link
UP-TO-DATE and the previous shim silently linked. Two debugging conclusions were drawn
against a stale binary before this surfaced. The staged directory is now a declared input.

### 21.4 Consequences for the renderer interface

**`RendererEvent.PackUnloaded` is new.** `unloadPack` previously had no observable effect
on the interface, so §15's contract test for unload had nothing to assert against.

**`events` must replay.** Documented on `AnatomyRenderer` as part of the contract: a
subscriber attaching after an action still has to see it, which is the same property §4's
recovery-by-replay depends on.

**`StructureNode` lives in `core-model`.** It parses §2.2's `<ta_code>__<latin_slug>__<L|R|M>`
into a `StructureId` and a `Laterality`, so the renderer resolves a picked node without any
content database — `core-data` is a Phase 1 module and Phase 0 must not need it. A node
whose name does not parse is never picked as a structure, so a naming mistake in the
pipeline surfaces as a miss rather than as the wrong answer.

**`AnatomyRendererContract` lives in `commonMain`**, for the reason §20.3 gives for
`FakeAnatomyRenderer`. It carries no `kotlin.test` dependency, which would otherwise follow
the module into the release binary; test source sets supply the `@Test` annotations and the
coroutine builder.

### 21.5 What Phase 0 does not do

Implemented: `loadPack`, `unloadPack`, `highlight`, picking, and `setPickingEnabled` —
§15's four verbs. The camera frames the whole asset on load.

Throwing, each naming its phase: `setSystemVisibility` (§2.2 does not encode `SystemId`, so
this genuinely needs `core-data`), `setOpacity` and `isolate` (transparent material
variants), `focusCamera` and `setCameraPose`.

**Highlighting is colour and luminance only.** `HighlightStyle` carries an outline width
and a solid/dashed channel precisely so that no state is distinguished by hue alone (§12);
Phase 0 honours neither, because outline geometry needs a second render pass. Until it
does, the accessibility guarantee in §12 is not met, and the quiz's correct/wrong
distinction must not be built on highlight styling alone.

### 21.6 Status

Green: the six contract tests and a Metal smoke test, run against real Filament on the iOS
simulator via `./gradlew :shared:renderer-filament:iosSimulatorArm64Test`. The device slice
compiles and links, and `Shared.framework` builds for `iosArm64`.

Not yet done: **§16's exit criterion is unmet.** Nothing has run on a real iPhone, and no
frame rate or resident memory has been measured against §6.1. The gate is not passed — the
iOS shim is merely no longer the reason it might fail.

## 22. Addendum — 2026-09-07: the content pipeline, and what Z-Anatomy actually is

Phase 0's content half. It also answers most of §6.1 — the part that can be answered
without a device.

### 22.1 The source does not match what the sourcing spec assumed

`docs/model-sourcing-spec.md` §2.2 specified node names as
`<ta_code>__<latin_slug>__<L|R|M>` and offered `A02_4_01_001__scapula__L` as the example.
Measured against the actual atlas, every part of that was wrong:

| §2.2 assumed | Z-Anatomy has |
|---|---|
| A TA code on each object | No code at all; `TA2.csv` supplies one, joined on the English name |
| Hierarchical codes like `A02.4.01.001` | Sequential TA2 ids, and `1113*8` for enumerated structures |
| Latin names | English names |
| `__L` / `__R` / `__M` | `.l` / `.r` suffixes, median unsuffixed |

The convention is kept and the pipeline produces it; the source simply has to be
translated into it rather than read from it.

Three further properties of the source, none of them guessable:

**Roughly 2,000 of 7,300 objects are label geometry.** The add-on declares
`label_elements = {"-txt", ".t", ".j"}` — text and leader lines. Ingested blindly they
would become pickable "structures" that are typography.

**There are two overlapping collection hierarchies.** The numbered `1: Skeletal system`
collections are flat visibility layers; `Bonus collection` holds anatomical containment.
An object is linked into both at once plus any regional groupings, so pack membership is
a set of collection names, not a path. The first version of `selection.py` modelled it as
a path and selected nothing.

**Definitions are text datablocks**, keyed by term, not object custom properties.

### 22.2 Identifiers

`<ta2_id>__<latin_slug>__<L|R|M>[__<discriminator>]`, with `*` folded to `_`.

The join reaches **95.2% of the 5,306 non-label objects** across the whole atlas, and
100% on both packs built so far. Unmatched objects get `ZAN` in the code position and
their English slug, so provisional identifiers stay distinguishable by shape and a later
re-join can upgrade them without guessing which were provisional.

The discriminator exists because a structure may be modelled as several objects while
glTF node names must stay unique — which is what `MeshRef` being a list per structure
already meant. It does not enter the `StructureId`.

**`SystemId` is derivable from the source after all.** §20.2 said `setSystemVisibility`
needed `core-data` because §2.2 did not encode a system; the numbered collections encode
it directly. That method can be implemented whenever a pack carries its manifest.

### 22.3 The split, and why it is where it is

Everything except `blender_export.py` is pure Python importing no `bpy`, and is tested
with `pytest` against no Blender at all. `tests/test_structure_id.py` pins the Python
identifier construction against the same fixtures as `StructureNodeTest` in `core-model`,
because those two agreeing is what makes a picked node resolve to the right structure.

That discipline caught less than an end-to-end check did. `ta2.code_for` folds `1113*8`
to `1113_8` and has a unit test proving it; the export path never called it, so 61 of 599
node names shipped with a `*` in them and failed to parse in the app. The unit test
passed throughout. What found it was loading a generated pack through the real renderer
and counting how many node names `StructureNode.parse` accepted.

### 22.4 What the budget looks like

Two packs built from the trunk. `skeletal-trunk` is a plausible shipping pack;
`trunk-all-systems` is not — it is the worst realistic case for one region, built to load
the §6.1 budget rather than a comfortable slice of it.

| | skeletal-trunk | trunk-all-systems | §6.1 / §3 limit |
|---|---|---|---|
| Structures | 86 | 599 | 300–800 visible |
| Triangles | 293,645 | 1,292,999 | ≤ 3,000,000 |
| Draw calls (upper bound) | 86 | 599 | ≤ 800 |
| Mean triangles per structure | 3,415 | 2,159 | 2,000–5,000 |
| glTF size | 4.9 MB | 22 MB | — |
| TA2 join | 100% | 100% | — |

Draw calls are counted as one per node, which is pessimistic: nothing is merged or
instanced yet. LOD generation and merging non-interactive geometry — both §6.1
mitigations — are deliberately not done, and `report.json` names them so the numbers are
read as a baseline rather than a result.

One structure, `Spinal dura`, cannot reach the per-structure target without collapsing
past the point where the shape survives. It is reported by name rather than silently left
oversized.

### 22.5 Status

The whole chain is verified end to end: Z-Anatomy → pipeline → glTF → Filament → pick →
`StructureId`. A 599-structure pack loads on the iOS simulator, all 599 node names parse,
and a pick at the centre of the viewport resolves to `257-regio-epigastrica-left`.

**§16's exit criterion is still unmet.** Triangle count and draw calls fit with room to
spare, but those are the two numbers measurable without hardware. Sustained 60 fps and
resident memory under 400 MB — the two that decide the gate — remain unmeasured on both
a real iPhone and a mid-range Android device.

## 23. Addendum — 2026-09-08: the Android renderer

Phase 0's second platform. §4.1 said Android hosts Filament through SceneView; it does not.

### 23.1 Filament directly, not SceneView

SceneView maintains its own scene graph of nodes. §4's most load-bearing line is that
Kotlin owns all state and the renderer holds no truth the app cannot reconstruct, and a
second graph to keep in sync is exactly the thing that line exists to prevent. SceneView
also pins its own Filament build, which cuts against §14's reason for choosing Filament
over three.js in the first place.

Google's `filament-android`, `gltfio-android` and `filament-utils-android` are used
directly instead. Both platforms are pinned to **1.75.1** — the newest version published
to Maven Central, and therefore the ceiling for matching them. Matching matters more than
being current: §15's contract tests are supposed to prove the same behaviour on both
platforms, and two engine builds would weaken that in a way no test would catch.

The Android implementation is deliberately a mirror of the iOS one — same gltfio loading,
same `View.pick`, same node-name index, same highlight-by-material-swap. Making them
differ only where the language forces them to is the cheapest way to make §15 true rather
than merely intended.

**The contract now passes on three implementations**: `FakeAnatomyRenderer`, iOS Filament
on the simulator, and Android Filament on an emulator. That is what §15 was asking for.

### 23.2 Everything iOS learned transferred, except one thing

Because it is the same engine, §21.3's discoveries carried over unchanged: offscreen
rendering must disable frame pacing, frames refused while others are in flight need the
GPU drained between them, and picking callbacks want dispatching onto the caller's thread.

One did not, and cost a black screen to find. **Drawing to a real surface, Filament paces
against the vsync timestamp.** Given `System.nanoTime()` it renders exactly one frame and
then refuses every subsequent one. Frames must be driven by `Choreographer`, passing the
timestamp it supplies.

This is a property of the engine, not of Android. The iOS host had the same defect —
`ar_render_frame` passed no timestamp and the layer path never set `DisplayInfo` — so the
iOS on-screen path would have failed identically on a real device while passing every
headless test. Both platforms now take a vsync timestamp, and the iOS host drives frames
from a `CADisplayLink`.

**That iOS fix is unverified.** The Android failure is the evidence for its shape, not a
reproduction: nothing has run the iOS on-screen path on hardware. It compiles for both iOS
targets and the headless tests still pass, which is all that can be claimed.

The general lesson is worth keeping: the headless contract tests pass on a code path that
is not the one the app uses. They prove the renderer's *behaviour*, not its *hosting*, and
hosting is where both platforms broke.

### 23.3 What the emulator does not tell us

The Android renderer draws the toy pack, picking resolves to the right `StructureId`, and
the contract passes — on an emulator, whose backend is OpenGL through a translation layer
onto the host Metal driver.

**No frame rate or memory number from it means anything for §16.** The gate asks for
sustained 60 fps and under 400 MB on a mid-range physical device, and an emulator on an M1
is neither mid-range nor a phone. The 599-structure trunk pack draws there at around
55 fps, which says the renderer is ready to be measured — not that it passes.

### 23.5 The harness draws the real pack

`:androidApp` stages the pipeline's `trunk-all-systems` pack into the APK when one has
been generated. It is bundled rather than pushed over adb so the §6.1 measurement can be
taken on a phone from a plain install; `pipeline/build` is not committed, so a checkout
that has never run the pipeline falls back to the toy asset instead of failing.

The harness shows structures loaded and a rolling frame rate, because §16's gate is a
frame rate against a structure count and a harness that displays neither cannot answer it.

Doing this exposed a mistake carried since the first iOS commit. Both hosts disabled
post-processing, with a comment claiming picking needed it. Picking does not — it uses its
own pass, and the contract tests confirm it — but post-processing is what carries tone
mapping, so a physically-lit scene rendered its linear HDR values straight out and every
structure came out pure white. It is on now on both platforms.

### 23.4 Status

Both platforms implement §15's four verbs. §21.5's list of what Phase 0 does not do
applies unchanged to Android, including that highlighting is colour and luminance only and
therefore does not yet meet §12.

**§16's exit criterion remains unmet, and is now purely a hardware question.** Every piece
is built and tested; what is missing is a real phone of each kind to run it on.
