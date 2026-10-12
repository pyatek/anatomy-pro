# State of play — 2026-10-10

Read this first. The design lives in `docs/superpowers/specs/2026-08-29-anatomy-pro-design.md`;
sections 20–40 there are the running record of what was actually built and why. This file is
the shorter question: where things stand and what to do next.




## 2026-10-12 — a server that runs (backend plan, stage 3)

- **`backend/`** (new, Kotlin/JVM): Ktor on Netty, HikariCP, Flyway, plain JDBC. Run its
  tests with `./gradlew :backend:test` — `allTests` does not include them.
- **What it does:** `GET /health`; `POST /v1/auth/anonymous` (256-bit device token, stored
  only as its SHA-256; a generated `student-NNNN` nickname); `GET /v1/account`;
  `PUT /v1/account/nickname` (3 to 20 letters, digits, `_`, `-`; unique whatever the case;
  blocklist; the 30-day lock is honoured, nothing sets it yet); `DELETE /v1/account`. Every
  refusal is an `ApiError`. Account creation is limited per address and everything else per
  token. Request logs carry method, path and status, never a header.
- **Tests run against a real PostgreSQL 18, but not through Testcontainers.** This machine
  has no container runtime (the `docker` CLI is installed, no daemon behind it), so the
  tests use `io.zonky.test:embedded-postgres`, which downloads PostgreSQL's own binaries and
  runs them as a process. Each test gets a schema migrated from empty. This departs from the
  plan's wording and keeps its reason (design §15: no in-memory stand-in); `TestDatabase.kt`
  is the one place to change if Testcontainers is wanted later.
- **Never run:** `backend/Dockerfile`, `compose.yaml` and `Caddyfile` are written and have
  not been built or started, for the same reason; so the plan's "done when" (`docker compose
  up` serving `/health` over TLS, an account made with `curl`) is not met. `main()` itself
  (reading the environment, starting Netty) is not covered by a test. The image copies a
  distribution built on the host (`./gradlew :backend:installDist`), because building inside
  it would need the Android SDK for the shared wire types.
- **Left out on purpose:** the nickname report table (§5.2) until there is an endpoint that
  writes to it; sign-out (plan 6.2).

## 2026-10-12 — what both ends share (backend plan, stage 2)

- **`shared/core-quiz`**: `QuizNode`, `QuizGraph` and the quiz model types (`Quiz.kt`,
  `StructureSummary.kt`), moved out of `core-data` with their packages unchanged; `core-data`
  re-exports it. It and `core-model` now also build for the JVM, so the server can generate
  the daily with the app's own code. The golden test passes on JVM, Android host and the iOS
  simulator.
- **`shared/core-quiz-fixtures`**: the sample atlas the generator's tests use, as a module,
  because `core-data`'s tests use it too and the server's will.
- **`shared/api-contract`**: every request, response, error code and path of backend spec §5
  to §8, `@Serializable`, on all three targets. `ApiContractTest` pins version 1 of each
  message as JSON: a field added without a default, or a wire name changed, fails it. An
  enum value from a newer server reads as that enum's "unknown". Nothing uses these types
  yet; stage 3 (the server) and stage 5 (the client) do.

## 2026-10-11 — packs from a bucket (backend plan, stage 1; ticket C010)

- **The pipeline publishes.** `python -m anatomypro_pipeline.publish <pack dir> --version N
  --label la=… --base-url … (--bucket … [--endpoint-url …] | --directory …)` uploads a
  version's mesh and manifest, reads them back, and rewrites `index.json` last. A version is
  never overwritten. The S3 path (`boto3`) has never been run: there is no bucket yet.
- **`shared/core-network`** (new): `PackDownloader` (Range resume, SHA-256, `.part` then an
  atomic move) and `CdnPackRepository` (the `PackRepository` contract over the index; a
  download belongs to the repository, not to the screen that asked). Ktor 3.6.0 and Okio.
  Android uses Ktor's `Android` engine: the OkHttp engine pulls an OkHttp that wants compile
  SDK 37.
- **A build is given an index, or it is not.** Gradle property `anatomypro.packs.indexUrl`
  (Android; then no pack is staged into the APK) or `AnatomyProPackIndexURL` in Info.plist
  (iOS). With one, `rememberDownloadedApp` supplies the real pack repository, onboarding has
  its third step, and every canvas draws the installed `skeletal-body` through
  `LocalDrawnPack`. Without one nothing has changed: the shipped pack, two-step onboarding.
  Debug Android also takes `--es packIndex <url>`, and allows plain HTTP for it.
- **Seen on the Android emulator** against a local range server: a fresh install with nothing
  shipped fetched the skeleton and drew it; a download cut at 983 040 bytes resumed with
  `Range: bytes=983040-`; a relaunch with the server down opened the atlas; the pack manager
  listed, deleted and offered the pack again.
- **Two dead ends in download mode, closed the same day.** Where the download is what
  brings the atlas, onboarding runs language, download, goals (`onboardingSteps`), so the
  goals have systems to offer; a download walked past ends onboarding there. With no pack
  installed the Atlas tab shows the download screen without its step header
  (`NoPackRoute`): the pack is offered, not fetched, because it may have been removed on
  purpose. A list of packs that cannot be read is now a failure to retry, not "nothing to
  download in this version". Seen on the emulator: the new order, delete then download from
  the Atlas tab, and an offline first launch recovered from the Atlas tab. The prototype has
  no screen for either case; both reuse screen 03.
- **Not done, not seen:**
  - iOS downloading was built and unit-tested, never run in the app (ATS needs HTTPS).
  - Only `skeletal-body` is drawn. Other packs can be downloaded and deleted, not shown.
  - An installed pack older than the index's is listed as installed; no update is offered.
  - Running out of space is reported as a network failure. Eviction under memory pressure
    (plan 1.4) is not built: one pack is resident at a time.
  - Pack names are in the interface language as it was when the list was first read.

## The one thing to decide first

Nothing, for now. The merge this section used to demand is done: `main` carries all 26
commits, `phase0/ios-filament-host` holds nothing `main` lacks, and `origin/main` agrees.

## 2026-10-10: the P1 batch (branch `feature/p1-batch`, not merged)

Everything below is on the branch, 24 commits ahead of `main`. The sections after this one
were written before it and are not yet reconciled with it; where they disagree, this is newer.

**All 21 prototype screens now exist.** Added: 02 goals and 03 first download
(`feature-onboarding`); 14 home, 15 daily lobby, 16 leaderboard (`feature-daily`); 17 profile
and 19 pack manager (`feature-profile`); 18 paywall (`feature-commerce`). All on fakes, in the
Android debug build. Production and iOS keep the placeholders, onboarding there is two steps,
and PROFIL still opens settings: the repositories behind the new screens refuse.

**The quiz has its real generator** (`core-data/quiz`): topics and questions from the structure
graph, distractors by distance (easy: another system; medium: same system and region, another
parent; hard: siblings), one answer per name whichever side is drawn, seeded and pinned by a
golden test, and gated on verification — an unverified name is never an answer and never an
option. Debug builds run it over `UnreviewedAtlas`, a harness that switches the gate off.
Production keeps `NotBuiltQuizRepository`: nothing is verified yet, and there is no progress
store. Topic mastery is always 0.

**Screen 12 frames both answers**, through `AnatomyRenderer.focusCamera(Set)`. Its colours
are still unjudged. **A tap question frames the session's targets** instead of the whole
model. **Back** is swallowed on a question and leaves the summary. **The paywall stays under
the tab that opened it.**

Debug extras: `--ez onboarding true`, `--es packFailure NETWORK|CHECKSUM`, `--ez subscribed
true`, `--es daily offline|attempted`, `--ez unranked true`, `--ez purchaseFails true`.

Tests: 506 on the iOS simulator and 466 on the JVM host, none failing (`./gradlew allTests`).

**Seen by hand on the Android emulator, in Polish:** onboarding with both failures and in
English; the daily from lobby to result; both boards; attempted, offline with practice,
unranked; profile, settings behind it, packs with a confirmed delete; the paywall's restore,
purchase and failed purchase; the generator's topic grid on the real atlas; the paywall under
TEST; back on feedback.

**Not seen:** the tap question's framing on a drawn model; back on the summary; the daily's
three failures; a download in progress on screen 19; screens 14 to 19 in English; TalkBack;
anything on iOS or on a device.

**Found by that run, open:**

- **The app hangs for good when a 3D screen is left while its pack is still loading.** The
  main thread sits in `Engine.flushAndWait` under `releaseSwapChain` ← `surfaceDestroyed`.
  Emulator only so far.
- **On `trunk-all-systems` the body surface covers what a question asks about**; only the
  outline shows through. The quiz needs to hide what is in front.
- **The question timer runs while the canvas is still black** (about 20 s on the emulator),
  so a session's first question usually times out there.
- **The ranking opened from the lobby stays on top of Today's stack**, so DZIŚ returns to it.
- The design-spec addenda the three screen plans ask for are not written.

## 2026-10-11: progress is kept, and the quiz is real in release builds (branch `feature/progress-store`, not merged)

**Release builds now have the quiz and a progress store.** `rememberAppDependencies()` supplies
`GeneratedQuizRepository` over the installed atlas and `RoomProgressRepository`. Four
repositories still refuse there: the daily, the leaderboard, entitlements and packs.

- **The quiz in a release build asks only about verified names (§7), and nothing is verified
  yet**, so its topic grid is empty and says so. That is the honest state until the reviewer
  tool produces verifications. Debug builds still ask about everything, through
  `UnreviewedAtlas`.
- **Entitlements refuse in a release build**; the topic grid reads that as "nothing bought":
  skeletal topics open, the rest locked, and a locked topic opens the paywall's placeholder.
- **Progress** is two tables added by migration 3 → 4: `quiz_session` (one row a finished
  session, with the user's own day) and `structure_progress` (right and wrong counts per
  structure). The streak, the calendar and mastery are read from them; nothing is held in
  memory. A quiz summary now says which structure each question was about and whether it was
  answered rightly (`QuizSummary.answered`), which is what progress is kept from.
- **Mastery** is "answered rightly at least once". A topic's is the share of its names; a
  system's is counted over every drawn structure in it, verified or not, both sides — so the
  two are not the same measure, and a system's total is larger than what can be asked.
- **Not known yet:** whether a day's daily quiz was completed (`dailyCompleted` is always
  false; the server's to say, Phase 3), and system names in the progress contract — the
  profile takes them from `systemNames` instead.
- Each of `rememberAtlas`, `rememberSettingsRepository` and now the progress store opens its
  own connection to the one database file. It works; it is three where one would do.

Debug extras added: `--ez production true` runs a debug build on exactly the release
dependencies; `--ez realProgress true` keeps sessions in the database instead of showing the
fixture's history.

**Seen on the Android emulator:** with `realProgress`, an empty streak; a session of seven
with two right; then a streak of 1, today marked on the calendar, 2 of 86 skeletal structures,
and Costae at 28 % — all still there after the app was restarted. With `production`: two-step
onboarding, an empty Test grid, the Today and Ranking placeholders, PROFIL opening settings.
**Not seen:** anything on iOS beyond the simulator tests; a real release APK installed; a
streak across midnight.

## 2026-10-11: the prototype's design (branch `feature/prototype-design`, on top of the P1 batch)

Until now every screen carried the prototype's colours and states and none of its look: no
fonts were bundled, and screens were plain text rows. The design system now has the
prototype's three typefaces (Source Serif 4 for Latin and headings, Montserrat for the
interface, IBM Plex Mono for labels and figures; OFL, licences in
`shared/core-designsystem/licences`), its type scale (`LocalAnatomyType`), the control
colours read off it, and its recurring pieces (`Components.kt`: step header, choice card,
buttons, panels, top bar, segmented tabs, progress ring).

**Every screen is now drawn as the prototype draws it**, and all but one seen on the Android
emulator: 01 to 20. Tree mode (21) is restyled and compiles but was not seen: the setting
did not switch from the test script. Structure detail (05) and settings (20) are the two
light screens. The atlas viewer (04) keeps its own structure — canvas, selection, list — in
the prototype's dress; the prototype's bottom sheet, layer chips over the canvas and "quiz me"
button are behaviour that does not exist yet.

**Fixed 2026-10-11, found by the hand runs:**

- **A native crash, on both platforms** (9854 and what looked like a hang): an outline's
  material named a mask texture that was destroyed before its changes reached the GPU —
  a resize and a cleared highlight in the same instant, which is what leaving a quiz
  question does. Filament aborts on a freed texture. A slot's material is now pointed at a
  placeholder before its mask goes. Reproduced by a test on each platform; 56 instrumented
  tests pass on the emulator. Yesterday's "hang" scenario was replayed three times on the
  fixed build without a hang, a crash or an ANR; that it was this same defect is an
  inference from that, not something seen in a trace.
- **A question's structure is no longer behind other systems** (9685): a session's canvas
  hides every system but the ones it asks about.
- **A question's time starts when the model is on screen** (9685, 2890), on both platforms:
  the canvas reports ready after the backend has finished the first frame holding the pack
  (`packOnScreen`; on iOS through `ar_pack_on_screen` in the shim). On the Android emulator
  that is 20 s after "pack loaded", which is where a first question used to run out. A test
  on each platform checks the signal is false at "loaded" and true after frames. How long
  iOS's first frame takes has still not been timed, and the iOS side was never watched in a
  running quiz.
- **A session's hidden systems are found in one query** (2890): `AtlasRepository.systemsOf`
  replaces a structure's whole detail read once per question.
- **A "find the structure" question was seen framing its region** on a drawn skeleton.

Where the prototype shows something the app has no data for, it is left out rather than
invented, and each screen's comment says what: structure counts per system (02), network,
time remaining and pause (03), the greeting, mastery tile and "continue" rows (14), the
day's topic and time limit (15), schools and the university board (16), name and school
(17), device storage and the Wi-Fi switch (19). The paywall (18) keeps the prototype's shape
but not its words: it was drawn when packs were to be sold, before design §29.

The prototype is read with `DesignSync get_file` on project
`c29fe5bc-0471-48c8-ab45-33e7849b4ab7`, file `Anatomy Pro Screens.dc.html`, after
`/design-login`.

## What exists

Twelve modules. `core-model`, `core-data` (Room), `core-designsystem`, `renderer-api`,
`renderer-filament`, `feature-atlas`, `feature-search`, `feature-settings`, `feature-quiz`, the `shared`
umbrella, `androidApp`, and `ios-renderer` (the Objective-C++ Filament host).

Plus `renderer-materials`, a build-only project: it fetches the pinned Filament macOS release
for `matc`, compiles the outline material, and writes it as Kotlin for `renderer-filament`
(§35.2). macOS only.

Plus `pipeline/`, a separate Python build that converts the Z-Anatomy Blender atlas into
content packs. It needs Blender installed and reaches `gltfpack` through `npx`. Its source is
fetched, pinned and checksummed, by `pipeline/fetch-source.sh` (§37).

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
- The default bundled Android pack, `trunk-all-systems`, has groups since it was regenerated
  (§37), so the Test tab lists topics on it, most of them locked. For the free skeletal
  topics alone, run with `./gradlew :androidApp:installDebug -Panatomypro.pack=skeletal-trunk`.
- Screen 12 (wrong answer) is unfinished. It has outlines (§35) and the two platforms now
  draw a style in the same colour (§36); what remains is that the camera frames only one of
  the two structures, and that its colours have not been judged.

The renderer can now frame things: `focusCamera` and `frameAll` exist on both platforms and
share their framing maths. `AtlasSceneViewModel` owns what the renderer shows. Android
hide/ghost is implemented and verified on the emulator.

**360 tests pass on the iOS simulator and 321 on the JVM host, none failing** (`./gradlew
allTests` at commit 4ac8ed3) — common tests run on both, so those figures overlap rather than
sum. The quiz module has 53 per target, on both the JVM host and the iOS simulator: 8 for the
topic grid, 32 for the session, 13 for the canvas rules. The fake module has 78 per target,
five of them added with the mirror-side answer (§34.3). Android's renderer contract and
database wiring have their own instrumented tests, which need a device. The
`:shared:renderer-filament` module's instrumented suite is 53 tests, measured on the API 36
emulator at commit 4ac8ed3.

## What is verified, and what is not

Verified on a Pixel 10: both renderers, picking to the right `StructureId`, and the §6.1
budget at region scope — `skeletal-trunk` 8.3 ms, `muscular-trunk` ~13 ms, `skeletal-body`
16.8 ms against a 16.7 ms frame.

**Those three figures are for packs that are no longer the ones on disk.** They were measured
on the 5,000-triangle builds. §25.4 decided the target is 1,200, and the packs regenerated on
2026-10-07 are at 1,200 (§37): about a third of the triangles. Only `skeletal-trunk` has a
figure at 1,200 (7.6 ms, §25.4). The other two have not been measured as they now are.

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
its colours are judged. They can be now: both platforms read a style's colour alike (§36).

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

- **The ghost is dimmer on Android than when it was approved.** Its "pale shell" look was
  judged on a colour Android read wrongly; read as sRGB, as on iOS, it is a dim grey shell at
  30 % (§36). Decided 2026-10-07: it stays as it is.
- **Vessels and nerves are thin, and hard to tap.** They are exported now (§38), as tubes at
  Z-Anatomy's own radii: half a millimetre for most, a pixel or two on a phone. Picking them
  has not been looked at.
- **`trunk-all-systems` is over the draw-call budget**: 952 drawn structures against 800,
  since the vessels and nerves joined it (§38.3). It is the default bundled Android pack.
- **`nervous-trunk` ran at 37 frames a second on the emulator**, with 54 structures. Not
  understood; emulator only (§38.4).
- **The all-systems pack still has 114 structures with no parent**: the body-surface regions
  and some lymph nodes (§37.4). The single-system packs have one root each and none.
- **`joints-trunk` has five roots**, the five joint groups; the source has no one group above
  them that resolves to a term.
- **A small mesh named "Skeletal system" is drawn and can be tapped** in `skeletal-body`: one
  of Z-Anatomy's `.g` group objects, 450 triangles (§37.4).
- **The iOS simulator's database holds rows from packs installed in earlier sessions**, so
  its atlas still lists roots the regenerated packs no longer have. Seen 2026-10-08 (§39); a
  clean install is needed before the new tree can be judged there.
- **No Polish names.** The pipeline emits Latin and English. The schema holds more; nothing
  fills it. The settings picker lists only what packs can render.
- **The selection's outline is hard to see against bone.** Pale pink (`FFD3CB`) on ivory,
  where the selected bone is surrounded by others (§35.5). A token change.
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
