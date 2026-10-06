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
| Model source | Z-Anatomy, CC BY-SA 4.0 — decided 2026-10-06, see §29 |
| Monetization | Freemium: the atlas is free for every system; the learning system is by subscription — decided 2026-10-06, see §29 |
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

> Superseded 2026-10-06 by §29: packs are no longer entitled. What follows is the original
> design.

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

- ~~Final model source.~~ Decided 2026-10-06: Z-Anatomy, with the paywall on the learning
  system rather than on content. Rationale and compliance plan in §29.
- Whether a TA2-keyed, translated term database needs FIPAT's permission (§29.4).
- Exactly where the free tier ends inside the learning system (§29.2).
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

### 23.6 Why the all-systems pack looks like a blank torso

Two causes, neither of them a defect.

**You are looking at the outside of a body.** `trunk-all-systems` loads all 599 structures
at once, so fascia, superficial muscles and the skin-surface region patches enclose
everything inside them. Every pick lands on a `regio-*` for the same reason. That pack was
built to load the §6.1 budget, not to look at. Layer peeling and isolation are what make
it viewable, and `setSystemVisibility`, `isolate` and `setOpacity` are all still Phase 1.
The same build with `-Panatomypro.pack=skeletal-trunk` draws a recognisable ribcage,
spine, scapulae and pelvis at 60 fps.

**Nearly every material exports white.** Z-Anatomy colours structures through Blender
shader node graphs and custom properties (`muscle_color`, `comic_shader`), which have no
glTF PBR equivalent: 63 of the 73 exported materials come out `baseColorFactor` pure
white. Model sourcing spec §2.5 anticipated this — "the app shades structures procedurally
and recolours them for highlighting" — so white input is what was planned for, not a
regression.

What is junk is the metallic channel: the exporter emits values around 0.5 on a third of
the materials, which is meaningless for tissue and gives the render a plastic sheen. The
pipeline should normalise materials rather than pass that through.

### 23.7 Measuring the frame rate, rather than the assumption

A first measurement on a Pixel 10 reported 40 fps. Before treating that as a rendering
cost, note what the code was doing: the Android surface path never called `setDisplayInfo`,
so Filament paced against its default 60 Hz while a 120 Hz panel's Choreographer delivered
frames at 120. 120 ÷ 3 = 40, which is a suspicious coincidence.

Both hosts now take the display's real refresh rate, and the harness reports the GPU's own
frame time beside the frame rate. That distinction is the whole point: a frame rate alone
cannot separate three quite different situations —

- **paced**: GPU time well under budget, frame rate a clean fraction of the refresh rate;
- **GPU-bound**: GPU time at or above the frame budget;
- **CPU-bound**: GPU time under budget, frame rate not a fraction of anything.

Each has a different remedy, and §6.1's deferred mitigations are not interchangeable
between them. LOD generation addresses triangles; merging non-interactive geometry
addresses draw calls, of which a 599-structure pack issues one per structure with nothing
merged or instanced. Optimising before the number says which limit is binding would be
guesswork.

### 23.8 The visible load is one system per region

Decided 2026-09-08: the app shows one system at a time within a region, not several
together. That is already the pack boundary §10 defines — "skeletal is free; other systems
require subscription" — so showing one system means loading one pack, which the renderer
does today. `setSystemVisibility` is a convenience, not a prerequisite.

It also reframes what has been measured. `trunk-all-systems` at 599 structures is a stress
case, not the target; the realistic ceiling is the largest single system in a region.

| Pack | Structures | Triangles | Draw calls | glTF |
|---|---|---|---|---|
| `visceral-trunk` | 50 | 158,650 | 50 | 3.1 MB |
| `skeletal-trunk` | 86 | 293,645 | 86 | 4.9 MB |
| `joints-trunk` | 105 | 168,279 | 105 | 3.6 MB |
| `muscular-trunk` | **205** | **516,287** | **205** | 7.0 MB |
| `trunk-all-systems` | 599 | 1,292,999 | 599 | 22.4 MB |
| §3 limit | 300–800 visible | ≤ 3,000,000 | ≤ 800 | — |

`muscular-trunk` is the worst realistic case: a quarter of the draw-call limit and a sixth
of the triangle budget. On that evidence §6.1's deferred mitigations — LOD generation and
merging non-interactive geometry — are not needed for Phase 1 and should stay deferred
rather than being built speculatively.

One caution about reading emulator numbers: GPU frame time barely moved between 205
structures (16.0 ms) and 599 (17.9 ms). Cost that does not scale with scene complexity is
the emulator's translation layer, not the content, which is precisely why the gate names a
physical device.

### 23.9 Real-device measurements

Pixel 10, 120 Hz, debug build. Medians of 21 steady-state samples, logged once per second
with the first ten discarded for shader compilation and thermal settling. GPU time is
Filament's own `denoisedGpuFrameDuration`.

| Pack | Structures | Triangles | fps | GPU median | GPU p90 |
|---|---|---|---|---|---|
| toy cubes | 3 | ~72 | 119 | ~4 ms | — |
| `skeletal-trunk` | 86 | 294k | 116 | 7.8 ms | 8.3 ms |
| `muscular-trunk-lod` | 205 | 249k | 89 | 11.1 ms | 12.7 ms |
| `muscular-trunk` | 205 | 516k | 75 | 12.9 ms | 14.2 ms |
| `skeletal-body` | 277 | 523k | 62 | 16.6 ms | 17.3 ms |
| `trunk-all-systems` | 599 | 1,293k | 43 | 23.3 ms | 25.4 ms |

**Method matters more than it looks.** An earlier pass took one screenshot per
configuration and reported 11.3 ms for `skeletal-trunk` and 14.2 ms for `muscular-trunk`.
Both were caught during warm-up, and the spread across repeated samples of an identical
configuration was 6–7 ms — larger than the differences being attributed to changes. Single
samples cannot support conclusions at this granularity, and two drawn from them here were
wrong.

**Per-structure cost dominates per-triangle cost.** The two 205-structure packs differ
only in geometry: 2.1× the triangles costs 1.8 ms, about 16%. Against that, going from 86
structures at 294k triangles to 205 structures at *fewer* triangles (249k) costs 3.3 ms,
about 42%. Fitting the four points gives roughly 0.03 ms per structure against 0.007 ms
per thousand triangles, so at these scene sizes the structure count is the lever and
decimation is a secondary one.

**Where that leaves the budget**, against 16.7 ms for 60 fps:

- `skeletal-trunk` at 7.8 ms has better than 2× headroom. This is the free pack of §10 and
  it is comfortable, plausibly including on mid-range hardware.
- `muscular-trunk` at 12.9 ms passes on a flagship with about 23% to spare. §16 specifies
  a mid-range device; at half this throughput it lands near 26 ms, or about 38 fps.
- `trunk-all-systems` at 23.3 ms fails everywhere, as expected of a stress case.

**A whole-body view sits exactly on the limit.** Every pack above is scoped to the trunk,
because §16 asks for one region; the source divides the body into head, neck, trunk, both
limbs, both hands and both feet. A whole-body skeleton is 277 structures and renders at
16.6 ms — level with the 16.7 ms a 60 fps frame allows, on a flagship. That makes §6.1's
"load only the active region and system" a requirement rather than a preference: a
full-body overview is affordable on current high-end hardware and not on the device class
§16 specifies.

Worth noting for content work: the whole-body skeletal selection is 622 objects but only
277 structures, because 344 of them are the zero-geometry `.i` anchors that pair with the
`.s` landmark surfaces. They are dropped because nothing without triangles can be drawn or
picked.

So the Android half of the gate is comfortable for the skeletal pack and unproven for the
largest single system. Neither LOD generation nor merging is clearly indicated yet: the
lever the numbers point at is how many structures a single view puts on screen, which is a
content and navigation decision before it is a rendering one.

### 23.10 Phase 0 verdict — 2026-09-09

**Answered. Filament stands; §14's three.js fallback is not invoked.**

§16 states its exit criteria as numbers but also states its purpose: "the specific unknown
being retired is the iOS Filament shim". That unknown is retired. One `AnatomyRenderer`
interface has three implementations passing one contract suite — the fake, Filament on
iOS, Filament on Android — and the pipeline feeds them real anatomy that loads, draws and
picks to the right `StructureId`.

The numeric criteria are met on a flagship at region scope and remain unconfirmed
elsewhere. That gap is deliberately not treated as blocking, because nothing it could
reveal would change the architectural decision the gate exists to make: a slower device
sends us to §6.1's mitigations, which is rendering work, not a change of graphics stack.

Carried forward as tracked work rather than open questions:

- No measurement on a mid-range Android device, which is what §16 actually specifies.
- Nothing has run on iOS hardware. The `CADisplayLink` frame driver is written from the
  Android failure rather than from a reproduction, and is unverified.
- Vessels and most peripheral nerves are absent from every pack: they are Blender curve
  objects and the export loop selects meshes only. The source has them; the pipeline
  drops them.
- Highlighting is colour and luminance only, so §12 is not met.

Phase 1 begins.

### 23.4 Status

Both platforms implement §15's four verbs. §21.5's list of what Phase 0 does not do
applies unchanged to Android, including that highlighting is colour and luminance only and
therefore does not yet meet §12.

**§16's exit criterion remains unmet, and is now purely a hardware question.** Every piece
is built and tested; what is missing is a real phone of each kind to run it on.


## 24. Addendum — 2026-09-09: core-data

Phase 1's foundation. §5 said SQLDelight; this is Room, which now publishes real Kotlin
Multiplatform artifacts (2.8.4, verified by resolving `room-runtime-iosarm64`) and brings
KSP-checked queries and exported schemas that make a migration a reviewable diff.

### 24.1 Content and review state are separate tables

§5 models names, synonyms, definitions and verification alike, as per-locale maps. They
behave differently: content is replaced wholesale when a pack updates, while verification
is human review effort that §17 calls the project's scarcest resource.

`structure_text` holds content. `structure_verification` holds review state and records a
hash of the exact text that was approved. A pack update replaces text freely; a
verification whose hash no longer matches stops satisfying the query that authorises quiz
answers, so it reads as stale rather than as either lost or — worse — still valid. §7
makes VERIFIED the gate on quiz answers, and a stale approval surviving an edit is exactly
how a wrong name becomes a question that teaches something false.

> Amended 2026-10-06 (§31): the hash covers the **name** only, and definitions live in their
> own table. "The exact text that was approved" above now means the name.

The hash is FNV-1a written out in `PackIngest`, not `hashCode`, so its value is defined by
this project rather than by a compiler that is free to change it.

### 24.2 §8.1 needs no hierarchy machinery

Each difficulty tier is one predicate over one indexed column: easy is a different
`systemId`, medium is the same `regionId` under a different `parentId`, hard is the same
`parentId`. Neither a closure table nor recursive CTEs are warranted.

### 24.3 Metadata outlives meshes

§11 syncs structure metadata independently of packs and §14 evicts packs under memory
pressure, so eviction clears the `pack` row's install state and mesh files while every
`structure` row stays. A structure remains findable when its geometry is not resident,
which is also what §10's paywall wants.

### 24.4 What the source could not supply

**Region was wrong before it was right.** Structures span body divisions — a muscle runs
from neck to trunk — while §5 carries a single `regionId`. Picking alphabetically gave a
trunk pack structures labelled `head` and `neck`. A region-scoped pack now claims its own
division, which is deterministic and meaningful; an unscoped pack still picks arbitrarily.

**The hierarchy is not where §2.3 expected it.** That section assumed nodes are parented
to reflect containment. Blender object parenting is unused in the source; containment is
expressed through collections. `.g` group objects stand in for some collections as
structures, so parents can be derived where one exists — but they are sparse, and the
skeletal trunk pack contains none at all. **`parentId` is therefore empty for all 86
structures in it, and §8.1's hard tier has nothing to group on.** Resolving this needs a
decision, recorded in §18 rather than settled here.

**Resolved 2026-09-09 — see §25.** Grouping collections are now synthesised as
structures, which supplies the hierarchy.

**No Polish, no synonyms.** Latin and English come from TA2; the schema holds the rest and
nothing fills it.

### 24.6 The database is opened, not just compiled

Every query in the DAO is checked by KSP at build time and none of that proves SQLite will
accept it. Twice in this project a tested unit sat behind a path nothing executed — the
renderer contract tests ran on a hosting path the app did not use, and `ta2.code_for` had a
passing unit test while the export never called it. So the database is opened on both
platforms and the queries run against real rows.

`BundledSQLiteDriver` ships SQLite with the app rather than using the system's, so both
platforms run identical SQL. That matters more than the binary size: the two otherwise
differ in SQLite version and eventually in whether a feature is compiled in at all.

Six tests cover behaviour on iOS against a real file — install, read back, walk the
taxonomy from leaf to group, find the siblings §8.1's hard tier draws from, prefix search
per locale, and reinstall without duplicating rows. The one that matters most asserts that
editing a definition makes an existing verification stop authorising quiz answers while
leaving the reviewer's row intact.

Android runs a narrower instrumented test, because what only a device can show is that
Room's Android builder, the bundled driver and the app's database directory work together.
The behaviour is covered once, against the SQLite build both platforms share.

### 24.5 Deferred

Search is a plain indexed table queried with `LIKE` over accent-folded terms rather than
FTS4: Room's `@Fts4` fails to resolve its own default `contentEntity` under KSP on Kotlin
Multiplatform. At a few thousand rows this is not the bottleneck, and keeping search in
its own table makes adopting FTS a contained migration.

No verification audit trail. §7 wants edit history for the reviewer tool, which has no UI
yet; `verifiedAt` plus the hash covers staleness, and history is an additive migration.


## 25. Addendum — 2026-09-09: group structures and detail meshes

Two changes that turned out to depend on each other.

### 25.1 Grouping collections become structures

§24.4 left `parentId` empty because containment lives in collections and the `.g` group
objects that stand in for some of them are too sparse to parent a pack. A collection is
now promoted to a structure when it holds geometry in the pack **and** its name resolves
to a Terminologia term.

That join doubles as the filter. `Bonus collection`, `Cross section planes` and
`Main divisions` do not resolve and never become structures; their children attach to the
next collection that does. Across the whole atlas 1,944 collections reduce to 458 holding
real geometry, of which 421 (92%) resolve.

Collections on the **region axis are excluded** even when they resolve. Region is already
its own field in §5, and letting it double as the taxonomy parent buries the useful
groupings: with `Thorax` eligible, 37 unrelated bones became siblings; without it, the
sibling sets are `Costal cartilages` (20), `Ribs` (14), `Thoracic vertebrae` (12),
`Cervical vertebrae` (7). §8.1's hard tier wants the second kind.

A group carries no `meshRefs`. Its geometry is its descendants', and duplicating that
would produce `mesh_ref` rows corresponding to no node. `core-data` marks it with an
explicit `isGroup` column rather than inferring it from having no refs, which an evicted
pack would also look like.

Skeletal trunk now reads, for every one of its 86 leaves:
`Atlas (C1) → Cervical vertebrae → Bones of vertebral column → Vertebral column →
Skeletal system`.

This is also the encyclopedia's spine: a group is a thing to read about, not merely a
grouping.

### 25.2 Detail meshes, and why they let the overview get cheaper

Selecting a structure loads `detail/<structure_id>.glb` — that structure alone, at source
density — into a second renderer. No renderer change was needed: a detail view is
`loadPack` with a one-structure `MeshSource`.

Measuring the source first changed the design. Z-Anatomy's meshes are already modest:
median 3,309 triangles for skeletal and 1,980 for muscular, the densest single structure
41,410, and **nothing over 100k**. So a detail mesh needs no real ceiling — 150k guards
only against systems not yet profiled.

It also showed that decimation at 5,000 was barely doing anything: most structures were
already under the cap and passed through untouched, which made a detail mesh nearly
identical to its overview counterpart and therefore pointless.

The overview target was dropped from 5,000 to 1,200 triangles on the reasoning that close
inspection no longer depends on the overview mesh. **That was measured and reverted — see
§25.4.** Detail meshes stand on their own; they do not currently buy a cheaper overview.

Detail generation is behind `--detail` so an ordinary run does not emit thousands of files
while the design is still moving.

### 25.3 Measured

Pixel 10, medians of 20 steady-state samples, decimation target back at 5,000:

| Pack | Leaves | Groups | Triangles | fps | GPU median |
|---|---|---|---|---|---|
| `skeletal-trunk` | 86 | 29 | 294k | 85 | 12.3 ms |
| `muscular-trunk` | 205 | 42 | 516k | 70 | 13.9 ms |
| `skeletal-body` | 278 | 69 | 523k | 61 | 16.8 ms |

### 25.4 Two wrong conclusions about decimation, and why

The overview target moved 5,000 → 1,200 → 5,000 → 1,200 across one day. Both reversals
came from measurements taken while the device was in an uncontrolled thermal state.

The middle result claimed 1,200 triangles rendered in 24.5 ms against 12.9 ms at 5,000 —
a third of the geometry for twice the frame time, reproduced across three runs. It was
wrong. Re-measured on a cool device, interleaving the two builds within one session and
repeating:

| Target | Triangles | Run 1 | Run 2 |
|---|---|---|---|
| 5,000 | 293,645 | 8.3 ms / 114 fps | 8.5 ms / 112 fps |
| 1,200 | 100,747 | **7.6 ms / 119 fps** | **7.4 ms / 119 fps** |

1,200 is consistently the faster of the two, by about 12%. The target is 1,200.

**The device swings ~45% with temperature.** The identical 5,000 pack measured 12.3 ms
during a hot stretch and 8.3 ms cool. That is larger than every effect being measured, so
any comparison between runs taken at different times is meaningless no matter how many
samples each contains.

§23.9 already established that single samples cannot support conclusions and switched to
medians. That was necessary and insufficient: medians remove sampling noise *within* a run
and do nothing about drift *between* runs. The method that works is interleaving the
variants inside one session and repeating the pair — cheap, and it would have prevented
both reversals.

### 25.5 The vertex cache pass buys size, not speed

`gltfpack` reorders indices for the GPU's post-transform vertex cache, which Blender's
exporter does not do. It runs after export with `-kn`, which is not optional: without it
gltfpack merges meshes and drops node names, and node names are the entire mapping from
geometry to `StructureId`. The pipeline verifies every name survives and refuses the output
otherwise.

Measured with the interleaved method, it makes no difference to frame time — 8.6 ms against
8.8 ms on the skeletal trunk. It is kept anyway because it makes `skeletal-trunk` 30%
smaller, 4.9 MB to 3.4 MB, which §10 cares about for download size. `--no-optimise` skips
it.

That also disposes of §25.4's proposed mechanism: vertex cache locality was a plausible
explanation for a slowdown that was not real.

---

## 26. Addendum — 2026-09-10: transparency, and the interface it removes

`docs/state-of-play.md` proposed transparent material variants and §12's outline
highlighting as one piece of shader work, on the argument that both are material changes
and doing them together is cheaper than either alone. That was wrong about the first half.
This section records the design that replaces it, and the measurements of the toolchain
that forced the split.

This is a design, not a record of built work. §26.8 says what exists.

### 26.1 The blended variant needs no shader

Filament does bake blending into the material, so a second material is genuinely required.
But the second material is already in the box: `gltfio`'s `MaterialProvider.MaterialKey`
carries `alphaMode` — `0 = OPAQUE, 1 = MASK, 2 = BLEND` — and `createMaterialInstance` is
public on both platforms. The `UbershaderProvider` the renderer already constructs vends a
blended variant on request. Verified in the shipped `gltfio-android-1.75.1` sources and in
`gltfio/MaterialProvider.h` for the iOS side.

So transparency is a Kotlin and Objective-C++ change with no new build artifact.

§12 is the opposite. Outline geometry needs custom `.mat` files and a `matc` step, and
`matc` is not present: `ios-renderer/build/filament/` is the iOS binary release, `include`
and `lib` only, with no host tools. That is a new tool dependency and a new build stage on
top of the shader work itself. Bundling it with transparency would hold screen 07 hostage
to it.

The two are therefore split. Transparency ships first and alone.

### 26.2 The interface loses two verbs

Isolation is now specified as: **the selected structure opaque, its immediate context
ghosted, everything else hidden.** Not per-system opacity, and not a low-alpha veil over
the whole region. The reason is §23.9 — `skeletal-body` already measures 16.8 ms against a
16.7 ms frame, and blended geometry does not get the depth-prepass rejection that opaque
geometry does. Bounding the blended set is the difference between this being free and this
being the thing that breaks the budget. Hiding the remainder *removes* draws.

That definition is a policy, and the taxonomy it needs — §25.1's parent groups — lives in
`core-data`. §4 gives the renderer geometry and picking and nothing else, so the policy
belongs in `feature-atlas` and the renderer is told only the resulting sets:

```kotlin
// removed: isolate(structure, ghostNeighbours)   — composed in feature-atlas
// removed: setSystemVisibility(system, visible)  — core-data knows a system's structures
fun setVisibility(structures: Set<StructureId>, visible: Boolean)
fun setOpacity(structures: Set<StructureId>, alpha: Float)
fun highlight(structures: Set<StructureId>, style: HighlightStyle)   // unchanged
```

Three of §21.5's throwing methods become two implemented verbs, and `SystemId` leaves
`renderer-api` altogether. Note what this does to §21.5's claim that `setSystemVisibility`
"genuinely needs `core-data`" because node names do not encode a `SystemId`: true, and
irrelevant once the caller resolves the set. **Screen 07 is unblocked whole**, per-system
toggles included, without the pack manifest system index it was said to be waiting on.

The general lesson is worth keeping: a renderer verb that names a domain concept is a verb
in the wrong module.

### 26.3 A ghost is one shared material instance

The obvious implementation — duplicate each primitive's instance and lower its alpha —
cannot be written. Filament's Java `MaterialInstance` exposes parameter setters and no
getters, so a duplicate cannot read the source structure's colour in order to fade it.

This is the right answer rather than a limitation. Anatomical context should read as a
uniform pale shell, not as a washed-out copy of each structure's own colour, and a uniform
shell needs no per-primitive state at all: **one** `BLEND`-keyed instance, created once per
pack, referenced by every ghosted primitive. No per-primitive allocation, and the ghost
count stops mattering to CPU cost.

Blended materials do not write depth, so ghost shells do not occlude one another. For a
faint context shell that is the desired look, and it removes sort order from the problem.

If the uniform ghost reads badly against real anatomy, the fallback is to carry each
structure's base colour in Kotlin from load time — which §4 prefers anyway — rather than to
try to recover it from Filament.

A ghost must stay **pickable**, and that is not free: Filament disables transparent picking
by default, so moving a primitive into the blended bucket silently removes it from
`View::pick`. `setTransparentPickingEnabled(true)` is therefore load-bearing rather than
optional — without it, ghosted and hidden become indistinguishable to a tap, and §26.2's
ghosted set is precisely the neighbours the learner taps to navigate. The cost is one extra
depth pass.

Hiding touches no material: `RenderableManager.setLayerMask` with `View.setVisibleLayers`.
That path needs no such flag — the layer mask gates the picking pass as well as the colour
pass, so a hidden structure stops being pickable for free, which is what §26.6 asserts.

### 26.4 One owner for the material slot

Visibility, opacity and highlight all contend for one `setMaterialInstanceAt` per primitive.
Today `highlight` owns that slot alone and unwinds itself through `swapped` and
`highlightInstances`; a second feature written the same way would mean two unwind stacks
racing, and whichever wrote last would win.

Instead the renderer keeps the three declared sets and resolves them to a single desired
material per primitive — `base | ghost | highlight`. Highlight beats ghost,
deterministically and regardless of call order. `hidden` is not a fourth material state: a
hidden primitive can still carry a ghost or highlight material underneath, and that is
harmless and correctly unwound, because the layer mask that hides it (§26.3) gates the draw
call itself. Hidden wins at the draw call, not at the material slot.

The resolution is clear-and-reapply, not a diff against what is already applied: every
change unwinds all three features and re-derives the whole result. An earlier draft of this
section specified a diff, and it was dropped deliberately — §26.2 bounds the ghosted set, so
the diff would be machinery bought before anything needs it. One consequence is worth
stating because it is easy to reintroduce: both apply loops must read a primitive's original
material *before* the pass installs anything, or a set containing the same node twice records
an override as the original and the unwind restores the wrong instance.

The renderer still holds no truth the app cannot reconstruct: the three sets are exactly
what the app last declared, so §4's recovery-by-replay after a lost surface is unaffected.

### 26.5 The C seam

Following the pairing `ar_set_highlight`/`ar_clear_highlight` established in
`anatomy_renderer.h`, and its rule that node names cross the boundary rather than
`StructureId`s:

```c
void ar_set_opacity(ar_renderer_ref, const char* const* names, size_t count, float alpha);
void ar_clear_opacity(ar_renderer_ref);
void ar_set_hidden(ar_renderer_ref, const char* const* names, size_t count);
void ar_clear_hidden(ar_renderer_ref);
```

The header is a frozen ABI, so it is designed alongside §26.2 rather than discovered a
second time while implementing the iOS half.

### 26.6 What the contract has to prove

§15 makes `AnatomyRendererContract` the shared definition of correct, so the new behaviour
is specified there and runs on both platforms. Four cases carry the design:

- ghost, then clear, returns every primitive to its original material instance;
- hide, then show, does the same, and leaves no material instance behind;
- **a hidden structure is not pickable** — visibility is enforced where picking reads it,
  not merely where drawing does, so a peeled-away layer cannot answer a quiz question;
- highlight and ghost applied to the same structure resolve identically in either order.

`FakeAnatomyRenderer` records the three declared sets, which is what lets the screen tests
for the layer panel assert against intent rather than against pixels.

### 26.7 What §12 still needs

Split out, and reduced. When it lands it is **solid outlines only**.
`HighlightStyle.outlineStyle` keeps carrying `DASHED` unhonoured, as it already carries the
outline channels unhonoured today, until Phase 2's correct/wrong feedback is the thing that
needs a second non-colour channel. Adding a dash pattern to a shader that already draws an
outline is a smaller change than writing the outline pass.

The route is available at the pinned version: `View::setStencilBufferEnabled` and the full
`MaterialInstance` stencil surface — compare function, reference value, read and write
masks, and the three operations — are present in the vendored 1.75.1 headers. What is
missing is `matc` and a build stage that runs it for two platforms.

Until then §21.5 stands unchanged: highlighting is colour and luminance only, §12's
guarantee is unmet, and the quiz must not be built on highlight styling alone.

### 26.8 Status

`setVisibility` and `setOpacity` are implemented on both platforms. On iOS they are verified
against real Filament on the simulator, including that a hidden structure is not pickable and
that a **ghosted** structure stays pickable. On Android (commit 017761a: hide by layer mask,
one shared ghost material, a held `ghostAlpha`) they pass the same instrumented contract on
the API 36 emulator; they have not been measured on hardware. `isolate` and
`setSystemVisibility` are gone from the interface; `IsolationPolicy` in `feature-atlas`
replaces them.

Nothing has been measured against the budget. §25.4's method — interleaving the variants
inside one session and repeating the pair — is how it will be.

Two gaps recorded here earlier are now closed, with the limits stated:

- `IsolationPolicy` now has its production caller: screen 07, through `AtlasSceneViewModel`
  (§27). The chain taxonomy → `Isolation` → renderer → shim runs end to end on Android on the
  emulator, and under the contract on the iOS simulator.
- The ghost reads as a pale shell, not a dark smear. That was checked by eye on the emulator
  with the real pack. It is still unconfirmed on hardware.

`SystemId` in `core-model` has a caller again: the layer panel.

---

## 27. Addendum — 2026-10-05: camera focus, the scene, and what the first real run found

§26 designed transparency and removed two verbs from the renderer interface. This section
records what was built on top of it: camera focus, a single owner for what the renderer
shows, screens 07 and 21, and three renderer defects that only appeared once real meshes went
through the whole path. §27.5 says what is verified and what is not.

### 27.1 Camera verbs and shared framing

The renderer interface gained `focusCamera`, which frames one structure, and `frameAll`,
which returns the camera to the whole model. The framing maths lives once, in common code
(`CameraFraming.kt`), and is used by iOS and Android. Each real renderer only supplies the
bounds of a set of nodes and applies a camera pose. The fake does no framing: it records
which structure it was last asked to frame (`focused`), or null for the whole model. On iOS that is two new
functions in the C seam, `ar_nodes_bounds` and `ar_set_camera`. A `FocusRequest` with a null
structure means the whole model: the camera returns to the body on reset, when isolation is
turned off, and when the selection is cleared while isolated.

### 27.2 The scene owns what the renderer shows

`AtlasSceneViewModel` is the single owner of layer state, isolation, selection and focus.
The selection is two values. `selected` is what the user chose on any screen — a tap on the
model, a row of the atlas list, a row focused in tree mode — and may be a group. `focus` is
the part of it that has geometry: the same id for a structure, nothing for a group, because a
group draws nothing. The atlas and the layer panel both highlight `focus` and isolate around
it, and the atlas names `selected`, looking the name up in the interface language. So
selecting a group names it and highlights nothing. `AtlasViewModel` holds the list's rows and
which are expanded, and no selection. The scene is app-scoped and is not rebuilt by a language
change, so the selection survives one.

The scene catches repository failures: a failed load leaves an empty panel, and a failed
isolation lookup leaves what was drawn as it was.

It produces a `RenderState`; a `SceneResolver` turns the taxonomy and the layer state into
concrete per-structure visibility and opacity, and `applyRenderState` applies that to the
renderer as a diff against what was last applied, so unchanged structures are not touched. If
a newer state arrives while a slower resolve is running, the latest state wins. The
`AtlasRepository` gained `systems()`, `structuresIn()` and `allStructures()` to feed it.
`AnatomyCanvas` takes a highlighted structure, a `RenderState` and a `FocusRequest` and
applies all three only after `PackLoaded`.

### 27.3 Three renderer defects the first end-to-end run exposed

The first run of real meshes through the full path found three defects that the contract and
the toy pack could not.

- **Stale pixels.** The canvas was never cleared between frames. Hiding structures left old
  frames on screen, so an isolated structure was drawn over what had been hidden. Fixed by
  clearing before every frame in both renderers (7a2d93f). The iOS change has never been
  seen on screen.
- **A late surface reset the camera.** When the surface attached after a focus request, it
  reset the camera and discarded the pose; the pending focus had survived only by accident.
  A late-attaching surface no longer resets the camera (9e1803c).
- **Swap-chain re-creation failed on Android.** A cold-start resize created the new swap
  chain before the old one was gone and failed with `EGL_BAD_ALLOC`, leaving the atlas
  off-centre. Android now releases the old swap chain before replacing it (4a549ee). The iOS
  C shim still creates the new swap chain before destroying the old one, which is the order
  that failed on Android; it has not been changed or tested.

### 27.4 Screens 07 and 21

Screen 07, the layer panel, sets each system to on, ghost or off, isolates the selection,
sets ghost opacity from 10 to 60 percent (default 30) and resets. Screen 21, structure tree
mode, walks the hierarchy one level at a time, announces focus and level through a polite
live region, and offers a custom "go deeper" accessibility action. It is shown instead of the
canvas when the `structureTreeMode` setting is on. Its path survives a language change:
there is one `StructureTreeViewModel` per tree, not one per language, and `onLocale` loads
the same level and the same focused row again in the new language. The path is also kept as
saved state above `ProvideAppLocale`, which is read only when a model is created, so a
recreated process reopens at the same level. The last navigation wins. A focused row becomes
the scene's selection, so leaving tree mode shows the model framed on it and highlighted.
The count line says that the arrow goes deeper; there is no swipe gesture on this screen, and
under a screen reader a swipe already means "next element". Two things are out of scope: the spatial-relations row and per-row
descriptor on screen 21 have no data source, and focusing a **group** moves no camera,
because groups draw nothing.

### 27.5 Status

Verified:

- The renderer contract: 12 cases on the fake, the iOS simulator and the Android emulator,
  including that focus centres a structure and `frameAll` returns to the whole model, plus
  four surface tests per real renderer. The Android instrumented total is 24, all passing.
- Screen 07 was checked by hand on the emulator with the real pack (seven checks).
- Screen 21 was checked by hand on the emulator with the `skeletal-trunk` pack: levels, focus
  badge, opening a structure, leaving tree mode (the canvas is framed on the last focused
  structure), and a language switch keeping the level.

Not verified, or known gaps:

- **Screen-reader speech was not heard.** TalkBack was enabled on the emulator and queued the
  announcement, but its TTS engine was not ready. The live region, the selected state and the
  custom action are wired and reviewed in code; none was heard.
- **Nothing has run on iOS hardware.** On iOS the canvas still draws the three-cube toy pack,
  so screens 07 and 21 are exercised there only against three cubes.
- **The bundled default pack is stale.** `trunk-all-systems` (generated 2026-09-07) predates
  group synthesis (b8c6d1e, 2026-09-09): all 599 structures are parentless and there are no
  groups. On it the atlas tree is flat, isolation ghosts no neighbours and tree mode has one
  level. `skeletal-trunk` has groups and behaves correctly. Regenerating the pack is tracked
  separately.
- **Focusing a group in tree mode moves no camera.** Framing a group would mean framing the
  union of its descendants.
- **The eight Latin system names in `SystemNames.kt` are unverified medical content.** They
  go on the reviewer's list (§7).
- **Polish copy for screens 07 and 21** has not been checked by a native speaker beyond
  grammar-agreement fixes.
- **No hardware measurement of hide/ghost cost.** §25.4's method is still to do.
- compose-resources does not unescape `%%`; a literal percent is written as `%`.

## 28. Addendum — 2026-10-06: preparing the iPhone run, and what the simulator found first

§16's Phase 0 named "a real iPhone" and §23.10 deferred it. This section records the work
done to make that run possible, and one defect found on the way. **No iPhone was attached and
no signing team is configured, so nothing here is a hardware result.** §28.5 lists what the
hardware session still has to do.

### 28.1 The iOS canvas drew nothing

Launched on the simulator, the atlas showed a blank canvas: the pack loaded (86 structures),
the tree filled in, and every frame was refused — 60 display-link callbacks a second, 60
refusals, 0 fps.

The cause was in the log. Compose's deprecated `UIKitView` no longer calls `onResize`; it
prints a warning and does nothing. `onResize` was the only place the `CAMetalLayer` was sized
and handed to the renderer, so `attachLayer` never ran, no swap chain existed, and
`ar_render_frame` returned false for ever. The contract tests could not see this because they
draw headless.

The layer now belongs to a `UIView` subclass that sizes and attaches it in `layoutSubviews`,
and attaches again only when the pixel size changes. With that, the simulator draws
`skeletal-trunk` at 60 fps. §27.3 said of the clear-before-frame change that "the iOS change
has never been seen on screen"; that is now explained, and it has now been seen on the
simulator.

When this broke is not known. The iOS shim still creates the new swap chain before destroying
the old one (§27.3); a first attach works, and a re-creation after a real resize is still
untested.

### 28.2 iOS draws real packs

The Xcode build phase now runs `iosApp/stage-packs.sh`, which copies `skeletal-trunk`,
`muscular-trunk` and `skeletal-body` from `pipeline/build/packs` into the app bundle
(`ANATOMYPRO_PACKS` overrides the list). A pack that was never generated is skipped and the
app falls back to the toy, as on Android.

Which pack is drawn is a launch argument, `-anatomypro.pack <id>`, defaulting to
`skeletal-trunk`. It is an argument rather than a build property so that one install can
alternate packs, which is what §25.4's method requires. The same resolution feeds the canvas
and `PackInstaller`, so the atlas tree on iOS is no longer empty.

### 28.3 What the harness reports

The iOS canvas logs one `AnatomyPerf` line a second, as Android does, with pacing and memory
beside the frame rate:

- `linkHz` — how often the display link fired; `grantedHz` — the interval the system granted
  it (`targetTimestamp − timestamp`); `refreshHz` — what Filament was told. On a ProMotion
  phone these can disagree: `CADisableMinimumFrameDurationOnPhone` is set, but the link does
  not ask for a frame-rate range, so it may be granted 60 Hz while Filament paces against 120.
  That is §23.7's mistake mirrored, and these three fields are how the device will show it.
- `maxGapMs` — the longest gap between two callbacks in the second; `refused` — callbacks
  Filament declined to draw. Together they separate a dropped vsync from a refused frame.
- `residentMb` and `footprintMb`, from two new functions in the C seam, `ar_resident_bytes`
  and `ar_footprint_bytes`. Resident size is what §6.1 is written against; the physical
  footprint is what iOS terminates on.

`iosApp/measure-packs.py` runs the protocol: launch per pack, drop ten seconds, keep 21, and
repeat the interleaved set, printing per-launch and pooled medians as a table.

### 28.4 Simulator figures, which are not the budget

Two interleaved rounds on the iPhone 17 simulator, to prove the plumbing. §23.8 already says
why emulated GPU time says little about content.

| Pack | Structures | fps | GPU median | GPU p90 | Refused | Resident | Footprint |
|---|---|---|---|---|---|---|---|
| `skeletal-trunk` | 86 | 60 | 5.4 ms | 5.9 ms | 1 | 260 MB | 99 MB |
| `muscular-trunk` | 205 | 60 | 6.8 ms | 7.3 ms | 0 | 287 MB | 125 MB |
| `skeletal-body` | 277 | 60 | 5.5 ms | 5.7 ms | 0 | 327 MB | 165 MB |

Two things to watch on hardware rather than conclude from here: `skeletal-body` measured
358 MB resident in one launch and 312 MB in the other, and the footprint of a single launch
rose by about 1 MB a second over its first ten seconds. Neither was investigated.

### 28.5 Status

Verified: the device slice compiles and links unsigned for `generic/platform=iOS`; the three
packs are in the bundle; the simulator draws each of them; 239 iOS simulator tests pass.

Not done, and needing an iPhone:

- Signing. `TEAM_ID` in `iosApp/Configuration/Config.xcconfig` is empty.
- Load, pick and highlight on the device. Picking was not exercised on the simulator either:
  nothing here can tap it.
- Pacing on hardware: whether `linkHz`, `grantedHz` and `refreshHz` agree, and whether
  `refused` stays at zero after start-up. The first two seconds of a launch refuse nearly
  every frame on the simulator; whether that is the load or the first attach is not known.
- The §6.1 figures for the three packs, by `measure-packs.py --device`.

The app opens on screen 01 until onboarding is finished, and the log only runs while the atlas
is on screen, so the device needs one pass through onboarding by hand before measuring.

## 29. Addendum — 2026-10-06: the model source, and where the paywall goes

§18's first open question is closed. **The atlas is built on Z-Anatomy, and the subscription
gates the learning system, not the content.** This was the owner's decision; this section
records it, why, and what it obliges.

### 29.1 Why not gate the packs

§10 put the paywall on the pack boundary. Under CC BY-SA that gate is legal and does not
hold. Creative Commons' own reading of the 4.0 licences is that limiting access to a set of
users is permitted, because it does not stop a recipient exercising the licence — and one of
the rights a recipient keeps is redistribution. One subscriber could republish every paid
pack, lawfully. A paywall whose contents anyone may give away is a convenience fee.

The two alternatives that keep meshes exclusive were a licensed commercial atlas and a
commissioned one. Both cost thousands to five figures before any revenue, and a licensed one
adds dependence on a vendor's naming stability (sourcing spec §1). Neither was chosen.

### 29.2 What is free and what is paid

- **Free:** browsing the atlas, for every system — the model, picking, layers, isolation,
  search, structure detail.
- **Paid:** the learning system — quizzes, progress, the daily quiz and the leaderboard.

The paid surface contains none of the licensed work, which is what makes the gate
enforceable. Where exactly the free tier ends inside the learning system — whether some
quizzing is free as a taste — is not decided, and joins the open product questions.

Consequences for what is already written:

- §10's entitlement rule is superseded. Pack manifests need no entitlement field and the
  server need not withhold download URLs; packs remain versioned, checksummed and swapped
  atomically.
- Risk 2 in §17 is resolved by its own stated fallback.
- Code that treats the skeletal system as the free tier is now wrong in intent, not only in
  the name it compares against.
- Screen 18's paywall sells the learning system, and screen 19's pack manager has nothing to
  lock.

### 29.3 What share-alike obliges

Attribution has three layers, because Z-Anatomy is itself a derivative:

| Work | Licence | Used for |
|---|---|---|
| Z-Anatomy (Gauthier Kervyn) | CC BY-SA 4.0 | Meshes, hierarchy, English names |
| BodyParts3D (Database Center for Life Science) | CC BY-SA 2.1 Japan | The meshes Z-Anatomy is built on |
| Wikipedia | CC BY-SA 3.0 | The definitions Z-Anatomy carries |

The compliance plan:

1. **Packs are published.** Every generated pack is available at a public, unauthenticated
   location under CC BY-SA 4.0, with the three attributions and a statement of what was
   changed (decimation, re-chunking, renaming, conversion to glTF). Where — a public
   repository's releases or the pack CDN itself — is not yet chosen.
2. **Each pack carries its licence.** The manifest gains licence and attribution fields, so
   a pack separated from the app still says what it is.
3. **The app has an attribution screen**, reachable from settings, naming the three works,
   their licences and the changes made.
4. **Definitions taken from Z-Anatomy stay marked as such.** Text shown from that source is
   share-alike and is attributed where it is shown.

No technical measure may stop a recipient copying a pack out. Packs are not encrypted.

### 29.4 Keeping the source replaceable

The decision is cheap to make now because verification has not started; §17 puts it at
roughly 16,000 review decisions. Two rules keep that work from being tied to these meshes:

- Verification is keyed to `StructureId`, which derives from the TA code, never to a mesh or
  a node name.
- Names and translations authored or verified for this project are stored apart from text
  inherited from Z-Anatomy, with their origin recorded, so it is always clear which rows are
  share-alike and which are not.

With both, replacing the meshes later is a pipeline change.

One licence question remains open and is independent of the model. Terminologia Anatomica
(TA2) is published under CC BY-ND 4.0. FIPAT states that the individual terms are in the
public domain, and also that translations and works "that might be considered derivative"
need its permission. The pipeline joins on `TA2.csv` and the product is a Latin, Polish and
English term database keyed by TA ids. Whether that needs permission is being asked of FIPAT.

### 29.5 Status

Decided: the source, and the paywall boundary. Not done: the manifest fields, the
attribution screen, the choice of where packs are published, and the answer from FIPAT. None
of this is legal advice; whether an app that bundles share-alike packs is a collection or an
adaptation was not reviewed by a lawyer.

## 30. Addendum — 2026-10-06: verification capacity, planned

§17 called verification capacity the highest risk and said it should drive scheduling. The
plan is `docs/verification-plan.md`; this section records what it decided and what it found.

Decided by the owner: one reviewer at ten or more hours a week; the whole-body skeleton
first; a structure is a quiz answer only when **every locale the app ships names in** is
verified, which is Latin and English at first release, with Polish names following pack by
pack already verified.

Found while counting:

- **The workload is terms, not structures × locales.** Left and right share a name. The
  whole-body skeleton is 347 structures and 215 terms; the atlas extrapolates to roughly
  2,400 terms, not 16,000 decisions. Only the skeleton is counted for the whole body.
- **A verification currently approves a Wikipedia article.** `PackIngest.contentHash` covers
  the name and the definition, and the definitions are whole articles — 116,040 words across
  the skeleton, 2,001 for the femur. §24.1's rule that an edited definition invalidates the
  approval was deliberate; with these definitions it makes verifying a name mean reading an
  article. The plan assumes verification is narrowed to identity and name, with definitions
  as separately-stated, attributed, trimmed content. That change was made the same day; see
  §31.

No throughput is measured. The plan's dates rest on an assumed minute per card and are to be
replaced by timing the first 50.

## 31. Addendum — 2026-10-06: definitions leave the verification

§30 found that approving a name meant approving an encyclopedia article. This section records
the change that undoes it.

### 31.1 What a verification covers

A name, in one locale, for one structure — and through it the claim that this mesh is that
term. `PackIngest.contentHash` now takes the name alone. Editing a definition leaves a
verification valid; editing a name makes it stale, as before. §24.1's invariant is unchanged
in kind and narrower in scope.

### 31.2 Definitions are their own content

`structure_definition` holds one row per structure per language the text is **written in**,
with the text, the address it came from and its licence. The source writes definitions in
English only, so that is the one locale filled. Before this the English text was copied onto
the Latin row too and offered as the Latin definition.

A pack update deletes the pack's definitions before writing the new ones, so a definition a
new version drops does not linger under a source that no longer says it.

Definitions carry no review state. Nobody reviews them under the verification plan; a state
column with one possible value would be a claim the schema could not back. The detail screen
says so instead: under a definition it prints the source, the licence and "not reviewed".

### 31.3 The pipeline cuts articles to their lead

`definitions.summarise` drops the capitalised title (which runs to a second line for 24 of
the 521 definitions seen), lifts a trailing address out into
`definition_source`, stops at the first `== Section ==` heading, and keeps whole paragraphs
up to 120 words — always at least the first. Text ending in a Wikipedia address is marked
CC BY-SA 3.0; text with no address is Z-Anatomy's own and marked CC BY-SA 4.0.

The source breaks nearly every sentence into its own paragraph, so "the lead paragraph" would
have been one sentence; a word limit over whole paragraphs is what gives a usable extract.

On the whole-body skeleton: 144 distinct definitions and 116,040 words became 128 and 9,632,
with a median of 80 words. Of the sixteen that vanished, ten were a title with nothing under it
and six became identical to another once cut.

`python3 -m anatomypro_pipeline.definitions <manifest>…` applies the same rule to a manifest
generated earlier. The ten packs in `pipeline/build` were rewritten that way, because the
Z-Anatomy source was not on the machine to regenerate them; their meshes are untouched and
the originals are kept beside them in `pipeline/build/manifest-backup-2026-10-06`.

### 31.4 Migration

Schema version 3. The migration creates the table, moves each English row's definition into
it, drops the column, and recomputes every name's hash in Kotlin — FNV-1a is not something
SQL can reproduce. Verifications recorded against an old hash stop matching and read as
stale. None exist outside tests.

### 31.5 Status

Verified: 251 tests on the iOS simulator and 222 on the JVM host, and 56 in the pipeline.
Among them: a definition edit leaves the hash and a stored verification alone, a name edit
does not, a dropped definition is gone, a version 2 database file opens as version 3 with
its data moved, and an older manifest without source fields still ingests.

Not verified:

- **The migration has not run on Android.** Its test builds a version 2 file by hand, which
  is written for iOS; both platforms run the same bundled SQLite and the same Kotlin.
  Android's device tests compile and were not run.
- **The detail screen's credit line has not been looked at.** Its wording is tested as a
  function; the screen was not opened.
- **No pack has been regenerated from the atlas.** The summariser has only met definitions
  through the rewrite of existing manifests.
- The extracts keep Wikipedia's artefacts, such as the femur's "(, pl. femurs or femora )".
