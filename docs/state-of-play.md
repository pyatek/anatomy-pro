# State of play — 2026-09-10

Read this first. The design lives in `docs/superpowers/specs/2026-08-29-anatomy-pro-design.md`;
sections 20–26 there are the running record of what was actually built and why. This file is
the shorter question: where things stand and what to do next.

## The one thing to decide first

Nothing, for now. The merge this section used to demand is done: `main` carries all 26
commits, `phase0/ios-filament-host` holds nothing `main` lacks, and `origin/main` agrees.

## What exists

Eleven modules. `core-model`, `core-data` (Room), `core-designsystem`, `renderer-api`,
`renderer-filament`, `feature-atlas`, `feature-search`, `feature-settings`, the `shared`
umbrella, `androidApp`, and `ios-renderer` (the Objective-C++ Filament host).

Plus `pipeline/`, a separate Python build that converts the Z-Anatomy Blender atlas into
content packs. It needs Blender installed and reaches `gltfpack` through `npx`.

**Five of the prototype's 21 screens are real**: 01 language selection, 04 atlas viewer,
05 structure detail, 06 search, 20 settings. Three bottom-bar tabs are honest placeholders.

**83 tests pass on the iOS simulator and 65 on the JVM host** — common tests run on both, so
those figures overlap rather than sum. Android's renderer contract and
database wiring have their own instrumented tests, which need a device.

## What is verified, and what is not

Verified on a Pixel 10: both renderers, picking to the right `StructureId`, and the §6.1
budget at region scope — `skeletal-trunk` 8.3 ms, `muscular-trunk` ~13 ms, `skeletal-body`
16.8 ms against a 16.7 ms frame.

Not verified, in rough order of how much it matters:

1. **Nothing has run on iOS hardware.** The `CADisplayLink` frame driver was written from
   the Android failure rather than from a reproduction (§23.2). It compiles; that is all.
2. **No mid-range Android device**, which is what §16 actually specifies. A flagship is the
   only hardware this has seen.
3. **Screens 01, 05, 06, 20 and the bottom bar have never been run at all.**

## What to do next

**Transparent material variants — §26, built on iOS, not yet on Android or measured.**
`setVisibility` and `setOpacity` are implemented and pass ten contract tests against real
Filament on the simulator, including that a hidden structure is not pickable and a ghosted
one stays pickable. `isolate` and `setSystemVisibility` are gone from the interface, replaced
by `IsolationPolicy` in `feature-atlas`; that is what unblocks the layer/system panel
(prototype screen 07, the last Phase 1 atlas feature) once something calls it.

§26 splits transparency from §12's outline highlighting, which this file previously bundled
with it. That bundling was wrong: the blended variant needs no shader at all — `gltfio`'s
`MaterialProvider.MaterialKey` carries `alphaMode`, and the `UbershaderProvider` already in
use vends a `BLEND` variant on request. §12 does need shaders, plus `matc`, which is not on
this machine. Holding screen 07 behind that is the cost the bundle was hiding.

What remains: Android's `setVisibility`/`setOpacity` still throw `TODO()` — deferred until a
device is available, not a bug. Nothing has been measured against the §23.9 budget yet;
§25.4's interleaved-variants method is how that will happen. And `IsolationPolicy` has no
production caller yet, so screen 07 itself is still the next thing to build, not a checked-off
item — the chain from taxonomy to renderer to shim has never run end to end.

**Then §12, solid outlines only.** Dashed waits for Phase 2 to need it. Until §12 lands,
highlighting is colour and luminance only and the quiz must not be built on it.

**Then the quiz (Phase 2).** The data is ready. §8.1's three difficulty tiers are each one
predicate over one indexed column, and the synthesised taxonomy gives the hard tier real
siblings — `Thoracic vertebrae` (12), `Cervical vertebrae` (7), `Ribs` (14).

**Not yet:** Home, Daily and Leaderboard need Phase 3's backend. Pack manager needs downloads,
and packs are currently bundled in the APK.

## Known defects

- **Vessels and most peripheral nerves are missing from every pack.** They are Blender curve
  objects and the export loop selects meshes only. The source has ~950 of them; the pipeline
  drops them silently. Fixing it will move the performance numbers for those systems.
- **No Polish names.** The pipeline emits Latin and English. The schema holds more; nothing
  fills it. The settings picker lists only what packs can render.
- **UI strings are Polish literals**, not extracted for localisation. §13's mechanism does
  not exist.

## Decisions that are yours, not the code's

- **The model source (§18).** Z-Anatomy decimates well and hits the budget, so this is now
  purely the CC BY-SA question: generated packs must ship under share-alike with attribution,
  while the app around them stays yours. §4.2 of the sourcing spec calls it a business
  judgement, and it is the last thing gating a commitment to the content source.
- **Verification capacity (§17, the highest risk in the spec).** The pipeline now makes the
  arithmetic concrete: ~3,300 leaf structures plus ~458 groups across the atlas, at three
  locales each. That is roughly 16,000 review decisions for one reviewer.

## Environment notes

- `./gradlew --stop` between long sessions. Daemons accumulate and this machine runs out of
  memory; the Android emulator has been killed by it more than once.
- Measure performance by **interleaving variants inside one session and repeating the pair**.
  The device swings ~45% with temperature, which is larger than any effect worth measuring —
  two conclusions were reversed by learning this the hard way (§25.4).
