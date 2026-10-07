# Solid outline highlighting — Design Specification

- **Date:** 2026-10-07
- **Status:** Design approved in conversation; awaiting review of this document
- **Companion to:** `2026-08-29-anatomy-pro-design.md` §12 (accessibility), §21.5, §26.7
  (what §12 still needs) and §33 (several highlights at once)
- **Task:** 9BC5

---

## 1. Purpose

Design §12 says highlighting uses outline and luminance, never hue alone. Today a
highlighted structure is tinted and lightened or darkened; no outline is drawn, so §12's
guarantee is unmet and screen 12 tells the expected answer from the chosen one by colour
and darkness only.

This document designs the outline. When it is built:

- every highlighted structure has a solid outline in its style's colour and width, on
  Android and on iOS;
- the outline is the first thing in the project drawn by a material of our own, so the
  build gains a stage that compiles materials;
- a contract test looks at pixels for the first time.

It does not finish screen 12. §8 says what is left.

---

## 2. Locked decisions

| Decision | Choice | Rejected alternative, and why |
|---|---|---|
| Occlusion | The outline shows through whatever is in front | Outline only where the structure is visible: a mostly hidden structure would have almost none, which is what screen 12's hand run already complained of. Per-style choice: a second code path on both renderers before anything needs it |
| Technique | Off-screen mask per style, then a full-screen edge pass | Stencil and inverted hull: depends on smooth normals, which the packs have not been checked for, and needs `AssetLoader.createInstance`, which duplicates every entity of the pack. Rim shading: not an outline |
| Outline style | Solid only; `DASHED` draws solid | Dashes now: §26.7 defers them until a second non-colour channel is needed |
| Width | Constant on screen, `outlineWidthDp` converted with the display's density | Proportional to the model: a thin line on a far structure is the case that most needs to be seen |
| Mask resolution | Half the surface, RGBA8 | Full resolution: about 10 MB per style on a phone against about 2.6 MB |
| `matc` | Fetched from the pinned Filament release by checksum, run by the build | Installing by hand; committing a compiled `.filamat` |
| Fill | Unchanged: the tint and luminance shift of §33 | Reworking fill colours here: they are judged after task 5D17 closes the colour-space divergence |
| Interface | `AnatomyRenderer` unchanged | A separate outline verb: `HighlightStyle` already carries the channels |

---

## 3. What is drawn each frame

Filament draws an entity once per view, with the material it holds. An outline therefore
needs a second look at the highlighted geometry, and that second look is a mask.

**Groups.** The highlight map is grouped by outline: structures whose styles share
`outlineArgb` and `outlineWidthDp` form one group. Two styles that differ only in fill or
in `outlineStyle` are one group. In practice there is one group on the atlas and two on
screen 12.

**Per group, a mask.** Each group has a scene of its own holding only that group's
entities — the same entities the main scene holds, added to a second scene — and a view
that renders it with the main camera into an off-screen colour target at half the surface
size, cleared to transparent. The structures keep the materials they already have; only
the alpha they leave behind is read. Post-processing is off on mask views. The mask view
uses the main view's visible-layer mask, so a hidden structure leaves nothing in it.
The renderer's clear colour has alpha 0 so that a mask starts transparent; the swap chain
is opaque on both platforms, so the screen is unaffected.

**Then the main view**, exactly as today.

**Then the overlay view**, drawn over the main view without clearing. Its scene holds one
full-screen quad per group, each with an instance of the outline material bound to that
group's mask, colour and width in pixels. For each pixel the material samples the mask at
the pixel and at points on a ring of that radius around it; where the pixel itself is
outside the mask and the ring touches it, the pixel gets the outline colour. So the outline
lies outside the silhouette and the fill is untouched. Adjacent structures in one group
share a mask and get one outline around the pair.

**Nothing highlighted, nothing extra.** With an empty highlight map no mask view and no
overlay view is rendered, and no mask target is held. The atlas with no selection costs
what it costs today.

**Showing through** follows from the mask scene holding nothing but the highlighted
entities: nothing in it can be in front.

**Ghosted and highlighted** is drawn as highlighted, as today (§26.4). A blended material
leaves partial alpha in the mask; the material treats any alpha above a small threshold as
inside.

**Picking** uses the main view and is unaffected: the quads are in neither the main scene
nor a mask scene.

**Resize.** Mask targets are sized from the surface, so they are released and made again
when the surface is configured.

---

## 4. Components

### 4.1 `OutlinePlan`, shared Kotlin

In `:shared:renderer-filament` `commonMain`, beside `HighlightPaint`. Given the highlight
map and pixels-per-dp it returns the groups: for each, the structures, the outline colour
as four floats and the width in pixels. Both renderers are handed the same numbers, as
they are for the tint.

### 4.2 `OutlinePass`, one per platform

The outline's Filament objects — the material, mask scenes, views and targets, the overlay
scene, view and quads — live in a unit of their own on each side: `OutlinePass` in
`androidMain`, and its own `.mm` and private header in `ios-renderer`. Neither goes into
`FilamentAnatomyRenderer.android.kt` (605 lines) or `AnatomyRenderer.mm` (737 lines).

Its surface is small: construct with the engine and the compiled material; `resize`;
`setGroups` with entity lists, colours and widths; `render` hooks for before and after the
main view; `destroy`. The renderer calls `setGroups` from the place it already applies
appearance, so visibility, ghosting and highlighting stay decided in one pass.

### 4.3 The C seam

`ar_add_highlight` gains the outline colour as four floats and a width in pixels. The shim
merges groups with the same outline into one mask. `ar_set_outline_material` hands the
compiled material to the shim once, after `ar_create`. `ar_capture_frame` exists for tests:
it renders a frame and copies its pixels out. `ar_attach_layer` does not change: widths
cross the seam in pixels, so the density stays in Kotlin. No callback is added.

### 4.4 Density

`attachSurface` on Android and `attachLayer` on iOS (the Kotlin function, not the seam) take
pixels-per-dp from the host (`resources.displayMetrics.density`; the screen's scale).
Headless attachment uses 1.

### 4.5 `:renderer-materials`, the build stage

A Gradle project with no Kotlin plugin, like `:ios-renderer`.

- **Fetch.** A task downloads the Filament macOS release for the version pinned in
  `libs.versions.toml`, verifies a checksum pinned beside it, and unpacks `bin/matc`. It
  follows `FetchFilament` in `ios-renderer/build.gradle.kts`.
- **Compile.** A task runs `matc` on `outline.mat` for the mobile platform and the OpenGL,
  Vulkan and Metal backends, producing one `outline.filamat`.
- **Generate.** A task writes `OutlineMaterialData.kt`, the compiled material as base64 in
  a Kotlin object, into a generated source directory that `:shared:renderer-filament`
  compiles into `commonMain`. Both platforms read the same bytes. An Android asset would
  need a `Context` the renderer is not constructed with, and the iOS simulator tests have
  no app bundle to read a resource from.

The stage runs on macOS. So does the rest of the build; a Linux job would need the Linux
tools archive and a second checksum.

A compiled material is tied to the Filament version that made it. Because `matc` and both
runtimes are pinned by the one version entry, they move together.

---

## 5. Errors

- The outline material failing to load is reported once as a `RendererEvent.Error` with a
  code of its own, and the renderer goes on without outlines. Tint and luminance still
  draw. A missing outline must not take the model with it.
- A mask target that cannot be created is handled the same way for that frame's groups.
- A highlighted structure the pack lacks forms a group with no entities and draws nothing,
  as it tints nothing today.

---

## 6. Testing

**Shared rule.** `OutlinePlan` has unit tests in `commonTest`, run on both targets:
grouping by colour and width, styles differing only in fill sharing a group, dp to pixels,
an empty map giving no groups.

**Contract, with pixels.** Each real renderer gains a test-only readback of the last frame.
`AnatomyRendererContract` gains an abstract hook that counts pixels matching a colour, and
two cases:

- a frame with a structure highlighted contains pixels of the style's exact outline colour,
  and the same frame with nothing highlighted contains none;
- the same structure with a wider outline gives more such pixels.

The test style's outline colour is one no lit surface produces. `FakeAnatomyRenderer`
answers from the styles it recorded. The existing survival cases stay.

**By hand**, with `skeletal-trunk`, on the iOS simulator and the Android emulator: one
style on the atlas; two styles at once; a highlighted structure behind another; highlight
then hide; highlight then ghost; rotate the surface. Recorded as seen or not seen.

**Not measured.** Frame cost on hardware. No device is attached. It is recorded as
unmeasured and added to task CF82, with §25.4's interleaved method.

---

## 7. Order of work

1. **The gate is the Android pass**, not a throwaway spike: a probe that answers the four
   questions is the same code as the pass. It is built test-first, and the plan says which
   assumption each failing test disproves. If an entity cannot be in two scenes, work stops
   and the technique is reopened with the owner before anything else is built.
2. `:renderer-materials`: fetch, compile, deliver to both platforms.
3. `OutlinePlan` and its tests.
4. Android `OutlinePass`, density, wiring.
5. iOS `OutlinePass`, the seam, density, wiring.
6. Readback hooks and the contract cases.
7. Hand check on both.
8. Documents: `state-of-play.md`, design §21.5, and a new addendum recording what was
   built, seen and not seen.

---

## 8. Out of scope

- **Dashed outlines** (§26.7).
- **Screen 12's "unfinished" marker.** Its camera frames only the expected structure, and
  its colours wait on 5D17. Outlines remove one of its three reasons.
- **The colour-space divergence**, task 5D17. The overlay writes the outline colour without
  tone mapping; whether the two platforms show it alike is checked by the contract's exact
  colour match, and anything it finds about the fill belongs to 5D17.
- **Fill colours**, `fillArgb` stays unused.
- **Hardware measurement**, task CF82.
