# State of play — 2026-10-06

Read this first. The design lives in `docs/superpowers/specs/2026-08-29-anatomy-pro-design.md`;
sections 20–32 there are the running record of what was actually built and why. This file is
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

**251 tests pass on the iOS simulator and 222 on the JVM host** — common tests run on both, so
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
3. ~~Screens 01, 05, 06, 20 and the bottom bar have never been run at all.~~ Run on the
   Android emulator and, on 2026-10-06, on the iOS simulator in both languages (§32).
4. **Screen-reader speech on screen 21 was never heard.** TalkBack queued the announcement on
   the emulator, but its speech engine was not ready. The live region and custom action are
   wired, not heard.
5. **iOS has drawn real packs only on the simulator, and only since 2026-10-06.** Before that
   its on-screen canvas drew nothing at all (§28.1) and could not be tapped (§32.2); both are
   fixed and seen working there. Screen 07 has not been exercised on iOS against a real
   pack, and the iOS shim still creates the new swap chain before destroying the old one, the
   order that failed on Android.

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

**The remaining screens are planned, not built.** `docs/superpowers/plans/` holds one plan
per step of the all-screens spec, written 2026-10-06 and none of them executed:

1. `…-onboarding-goals-and-first-download.md` — screens 02 and 03.
2. `…-renderer-several-highlights.md` — two highlight styles at once; the quiz needs it.
3. `…-quiz-flow.md` — screens 08 to 13, after the renderer plan.
4. `…-daily-and-leaderboard.md` — screens 14 to 16, after the quiz plan.
5. `…-profile-paywall-and-packs.md` — screens 17 to 19.

All of them run on fakes: in production the Test, Today and Ranking tabs keep their
placeholders and the Profile tab keeps opening settings, because the repositories behind
them refuse until Phase 3 builds them.

**Not yet:** Home, Daily and Leaderboard need Phase 3's backend. Pack manager needs downloads,
and packs are currently bundled in the APK.

## Known defects

- **Vessels and most peripheral nerves are missing from every pack.** They are Blender curve
  objects and the export loop selects meshes only. The source has ~950 of them; the pipeline
  drops them silently. Fixing it will move the performance numbers for those systems.
- **The atlas has roots that are not anatomy** — `1: Skeletal system` beside `Skeletal system`,
  and `Visceral systems` in a skeletal pack. The pipeline promotes Z-Anatomy's numbered
  visibility layers to structures (§32.3).
- **Left and right look identical in search and on the detail screen.**
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
- ~~Verification capacity (§17).~~ Planned 2026-10-06 in `docs/verification-plan.md`: one
  reviewer at 10+ hours a week, skeleton first, every shipped locale verified before a
  structure is a quiz answer. The workload is about 2,400 terms, not 16,000 decisions. The
  change it depended on is made (§31): a verification covers the name, and definitions are
  separate, attributed extracts.

## Environment notes

- `./gradlew --stop` between long sessions. Daemons accumulate and this machine runs out of
  memory; the Android emulator has been killed by it more than once.
- Measure performance by **interleaving variants inside one session and repeating the pair**.
  The device swings ~45% with temperature, which is larger than any effect worth measuring —
  two conclusions were reversed by learning this the hard way (§25.4).
