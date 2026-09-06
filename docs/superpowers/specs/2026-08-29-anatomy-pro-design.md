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

### 20.4 Build configuration

Shared Android and Kotlin Multiplatform configuration lives in a `build-logic/` included
build as precompiled script plugins, rather than being repeated per module.

At four modules this is close to break-even. It is chosen for the twelve-module end
state in §3.1: retrofitting convention plugins later means editing every module written
in the interim. The known cost is that precompiled script plugins cannot resolve the
`libs` version catalog directly and must reach it through `VersionCatalogsExtension`.
`buildSrc` was rejected because any edit to it invalidates the configuration cache,
which this build has enabled.
