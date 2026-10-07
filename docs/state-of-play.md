# State of play — 2026-10-07

Read this first. The design lives in `docs/superpowers/specs/2026-08-29-anatomy-pro-design.md`;
sections 20–35 there are the running record of what was actually built and why. This file is
the shorter question: where things stand and what to do next.

## The one thing to decide first

Nothing, for now. The merge this section used to demand is done: `main` carries all 26
commits, `phase0/ios-filament-host` holds nothing `main` lacks, and `origin/main` agrees.

## What exists

Twelve modules. `core-model`, `core-data` (Room), `core-designsystem`, `renderer-api`,
`renderer-filament`, `feature-atlas`, `feature-search`, `feature-settings`, `feature-quiz`, the `shared`
umbrella, `androidApp`, and `ios-renderer` (the Objective-C++ Filament host).

Plus `renderer-materials`, a build-only project: it fetches the pinned Filament macOS release
for `matc`, compiles the outline material, and writes it as Kotlin for `renderer-filament`
(§35.2). macOS only.

Plus `pipeline/`, a separate Python build that converts the Z-Anatomy Blender atlas into
content packs. It needs Blender installed and reaches `gltfpack` through `npx`.

**Seven of the prototype's 21 screens are real everywhere**: 01 language selection, 04 atlas
viewer, 05 structure detail, 06 search, 07 layer panel, 20 settings, 21 structure tree mode.
Three bottom-bar tabs are honest placeholders.

**Screens 08 to 13 are real, with qualifications** (§34): topic selection, a question in two
formats (name the structure, find the structure), right-answer and wrong-answer feedback, and
the session summary. They run in `:shared:feature-quiz`.

- The questions are canned: a fake repository draws them from the installed atlas. There is
  no question generator.
- Android debug build only. Production and iOS keep the "not built" placeholder, because no
  quiz repository stands behind them.
- The default bundled Android pack, `trunk-all-systems`, has no groups, so the Test tab is
  empty on it. Run with `./gradlew :androidApp:installDebug -Panatomypro.pack=skeletal-trunk`.
- Screen 12 (wrong answer) is unfinished. It now has outlines (§35); what remains is that
  the camera frames only one of the two structures, and that its colours wait on 5D17.

The renderer can now frame things: `focusCamera` and `frameAll` exist on both platforms and
share their framing maths. `AtlasSceneViewModel` owns what the renderer shows. Android
hide/ghost is implemented and verified on the emulator.

**352 tests pass on the iOS simulator and 317 on the JVM host, none failing** (`./gradlew
allTests` at commit 87cebe0) — common tests run on both, so those figures overlap rather than
sum. The quiz module has 53 per target, on both the JVM host and the iOS simulator: 8 for the
topic grid, 32 for the session, 13 for the canvas rules. The fake module has 78 per target,
five of them added with the mirror-side answer (§34.3). Android's renderer contract and
database wiring have their own instrumented tests, which need a device. The
`:shared:renderer-filament` module's instrumented suite is 48 tests, measured on the API 36
emulator at commit 87cebe0.

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
5. **Outlines have not been measured on hardware.** Each outline group adds a half-size pass
   and a full-screen pass (§35.5). Unmeasured on any device; it goes with the transparency
   measurement.
6. **iOS has drawn real packs only on the simulator, and only since 2026-10-06.** Before that
   its on-screen canvas drew nothing at all (§28.1) and could not be tapped (§32.2); both are
   fixed and seen working there. Screen 07 has not been exercised on iOS against a real
   pack, and the iOS shim still creates the new swap chain before destroying the old one, the
   order that failed on Android.

**The quiz, by hand, on the Android emulator with `skeletal-trunk`** (§34.4). Seen: five
topics, both formats, right and wrong feedback, a timeout, the summary, the timer turned off,
the examination language, the interface language, "End session", system back on a question,
and a tab switch that kept the question. Not seen: a locked topic and its route to the
paywall placeholder (the pack has only free skeletal topics; a unit test covers it); the
perfect-score summary; anything on a physical device; anything with a screen reader.

Two highlight styles at once are accepted by both renderers (the contract passes on the fake,
the iOS simulator and the Android emulator) and were not seen when that was written; screen 12 now
asks for them, and the hand run saw the two colours, with the defects below.

What is verified since: screen 07 was hand-checked on the emulator with the real pack, and
screen 21 with `skeletal-trunk`. A ghost reads as a pale shell, not a dark smear — on the
emulator; hardware is still unchecked. The chain taxonomy → `Isolation` → renderer → shim runs
end to end on Android.

## What to do next

**Solid outlines are drawn, on both platforms (§35).** A highlighted structure has a line in
its style's colour and width, which shows through whatever is in front. The renderer
contract checks it by reading pixels back. What §12 still lacks is the dashed outline, which
waits until something needs a second non-colour channel. What the outline costs on hardware
is unmeasured.

**The contract's fixture now has the packs' kind of material.** A fault that drew no outline
on real packs passed every test on the bare toy material (§35.3). Anything new in the
renderers should be seen on a real pack before it is believed.

The quiz exists; screen 12 stays unfinished until its camera frames both structures and
5D17 closes the colour-space divergence.

**The quiz needs its real question generator.** Design §8.1's generator, and §7's
verified-only gate in front of it, are what turn the canned harness into a product. Until
then the debug quiz asks about whatever the installed atlas holds, verified or not.

**The daily plan must take the same clock fix before it is run.** Its `DailyViewModel` has
the never-ending tick loop that hung the quiz's first tests; the quiz replaced it by reading
elapsed time from a clock (§34.3).

**The quiz's data.** The data is ready. §8.1's three difficulty tiers are each one
predicate over one indexed column, and the synthesised taxonomy gives the hard tier real
siblings — `Thoracic vertebrae` (12), `Cervical vertebrae` (7), `Ribs` (14).

**The remaining screens are planned, not built.** `docs/superpowers/plans/` holds one plan
per step of the all-screens spec, written 2026-10-06 and the renderer and quiz plans have since been executed:

1. `…-onboarding-goals-and-first-download.md` — screens 02 and 03.
2. `…-renderer-several-highlights.md` — two highlight styles at once; done (§33).
3. `…-quiz-flow.md` — screens 08 to 13. Executed 2026-10-07 on canned questions (§34).
4. `…-daily-and-leaderboard.md` — screens 14 to 16, after the quiz plan.
5. `…-profile-paywall-and-packs.md` — screens 17 to 19.

All of them run on fakes: in production the Test, Today and Ranking tabs keep their
placeholders and the Profile tab keeps opening settings, because the repositories behind
them refuse until Phase 3 builds them.

**Not yet:** Home, Daily and Leaderboard need Phase 3's backend. Pack manager needs downloads,
and packs are currently bundled in the APK.

## Known defects

- **The two renderers read a highlight's colour differently.** iOS sets the base colour as
  sRGB; Android passes it through unconverted. The same style, and the darkening of a negative
  luminance shift in particular, is not identical on the two. The ghost material differs the
  same way. This predates the several-highlights change and has to be closed before screen
  12's colours are judged.
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
- **The selection's outline is hard to see against bone.** Pale pink (`FFD3CB`) on ivory,
  where the selected bone is surrounded by others (§35.5). A token change, after 5D17.
- **Focusing a group in tree mode moves no camera**, because groups draw nothing.
- **The eight Latin system names in `SystemNames.kt` are unverified.** They go on the
  reviewer's list.
- **Some fake data is still Polish literals**: the plan names and prices, the payment-declined
  message, and the system and pack labels in `FakeEntitlementRepository`, `FakePackRepository`
  and `FakeProgressRepository`. Screen strings are resources (`values` and `values-pl` in each
  feature module); this is data that real repositories will supply.

### Found by the quiz hand run (§34.4)

- **Screen 12 frames only the expected structure**, so the chosen one is often mostly out of
  frame. (Both are outlined since §35.)
- **The question highlight is faint** (mauve on ivory bone) with no visible frame.
- **"Find the structure" draws the whole model small**; ribs are hard to tap.
- **The canvas stayed black for 10 to 20 s** at a session's first question and after
  returning from another tab. Emulator only; not confirmed on a device.
- **System back on a question recreates the canvas** (the session survives).
- **System back on the summary cannot leave it**; only "Back to topics" does.
- **The paywall placeholder is pushed on the Test tab's stack while the bar shows Profile.**

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
