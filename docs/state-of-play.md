# State of play — 2026-10-06

Read this first. The design lives in `docs/superpowers/specs/2026-08-29-anatomy-pro-design.md`;
sections 20–29 there are the running record of what was actually built and why. This file is
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

**Seven of the prototype's 21 screens are real**: 01 language selection, 04 atlas viewer,
05 structure detail, 06 search, 07 layer panel, 20 settings, 21 structure tree mode. Three
bottom-bar tabs are honest placeholders.

The renderer can now frame things: `focusCamera` and `frameAll` exist on both platforms and
share their framing maths. `AtlasSceneViewModel` owns what the renderer shows. Android
hide/ghost is implemented and verified on the emulator.

**239 tests pass on the iOS simulator and 207 on the JVM host** — common tests run on both, so
those figures overlap rather than sum. Android's renderer contract and
database wiring have their own instrumented tests, which need a device; 24 pass on the API 36
emulator.

## What is verified, and what is not

Verified on a Pixel 10: both renderers, picking to the right `StructureId`, and the §6.1
budget at region scope — `skeletal-trunk` 8.3 ms, `muscular-trunk` ~13 ms, `skeletal-body`
16.8 ms against a 16.7 ms frame.

Not verified, in rough order of how much it matters:

1. **Nothing has run on iOS hardware.** The `CADisplayLink` frame driver was written from
   the Android failure rather than from a reproduction (§23.2). It now paces correctly on the
   simulator, and everything the device run needs is in place (§28): packs in the bundle, a
   per-second pacing and memory log, and `iosApp/measure-packs.py`. What is missing is an
   iPhone and a `TEAM_ID` in `iosApp/Configuration/Config.xcconfig`.
2. **No mid-range Android device**, which is what §16 actually specifies. A flagship is the
   only hardware this has seen.
3. **Screens 01, 05, 06, 20 and the bottom bar have never been run at all.**
4. **Screen-reader speech on screen 21 was never heard.** TalkBack queued the announcement on
   the emulator, but its speech engine was not ready. The live region and custom action are
   wired, not heard.
5. **iOS has drawn real packs only on the simulator, and only since 2026-10-06.** Before that
   its on-screen canvas drew nothing at all: `UIKitView.onResize` had become a no-op, so the
   Metal layer was never attached (§28.1). Screens 07 and 21 have not been exercised on iOS
   against a real pack, picking has not been tapped there, and the iOS shim still creates the
   new swap chain before destroying the old one, the order that failed on Android.

What is verified since: screen 07 was hand-checked on the emulator with the real pack, and
screen 21 with `skeletal-trunk`. A ghost reads as a pale shell, not a dark smear — on the
emulator; hardware is still unchecked. The chain taxonomy → `Isolation` → renderer → shim runs
end to end on Android.

## What to do next

**Outline highlighting, §12 — solid outlines first.** Transparency is done on both platforms:
`setVisibility` and `setOpacity` pass the shared contract on the iOS simulator and the Android
emulator, `IsolationPolicy` has its production caller, and screen 07 exists. What it has not
had is a hardware measurement; §25.4's interleaved-variants method is how that will happen.
§12 needs shaders, plus `matc`, which is not on this machine.

Dashed waits for Phase 2 to need it. Until §12 lands,
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
- **The bundled default pack is stale.** `trunk-all-systems` (2026-09-07) predates group
  synthesis: all 599 structures are parentless, so on it the atlas tree is flat, isolation
  ghosts no neighbours and tree mode has one level. `skeletal-trunk` is fine. Regenerating it
  is tracked separately.
- **Focusing a group in tree mode moves no camera**, because groups draw nothing.
- **The eight Latin system names in `SystemNames.kt` are unverified.** They go on the
  reviewer's list.
- **UI strings are Polish literals**, not extracted for localisation. §13's mechanism does
  not exist.

## Decisions that are yours, not the code's

- ~~The model source (§18).~~ Decided 2026-10-06 (§29): Z-Anatomy, with the subscription on
  the learning system and the whole atlas free. Packs are published under CC BY-SA. Still
  open from it: where packs are published, where the free tier ends inside the learning
  system, and FIPAT's answer on the TA2 licence.
- **Verification capacity (§17, the highest risk in the spec).** The pipeline now makes the
  arithmetic concrete: ~3,300 leaf structures plus ~458 groups across the atlas, at three
  locales each. That is roughly 16,000 review decisions for one reviewer.

## Environment notes

- `./gradlew --stop` between long sessions. Daemons accumulate and this machine runs out of
  memory; the Android emulator has been killed by it more than once.
- Measure performance by **interleaving variants inside one session and repeating the pair**.
  The device swings ~45% with temperature, which is larger than any effect worth measuring —
  two conclusions were reversed by learning this the hard way (§25.4).
