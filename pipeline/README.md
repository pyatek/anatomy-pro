# pipeline

Converts the [Z-Anatomy](https://www.z-anatomy.com/) Blender atlas into Anatomy Pro
content packs. A separate build system in the same repository (design spec §20.1).

## Running

```sh
brew install --cask blender

blender --background /path/to/Z-Anatomy/Startup.blend \
  --python anatomypro_pipeline/blender_export.py -- \
  --pack packs/skeletal-trunk.json \
  --ta2 /path/to/TA2.csv \
  --out build/packs
```

Each run writes `mesh.glb`, `manifest.json`, and `report.json` under
`build/packs/<pack-id>/`. Read `report.json` first: it carries the join rate and the
measurement against the §6.1 budget.

`report.json` also has a `taxonomy` block. A pack of one system should list that system as
its only root, and `parentless_leaves` should be empty; anything else is a fault in the
grouping rules, to be looked at before the pack is used.

The source is not in this repository and its location is not recorded: `Startup.blend` is
Z-Anatomy's Blender file and `TA2.csv` its term table. Note the version you run against;
structure ids are permanent.

## Testing

```sh
python3 -m pytest
```

Everything except `blender_export.py` is pure Python and imports no `bpy`, so the rules
that decide identifiers run without Blender installed. That split is the point: a
`StructureId` is never reused once assigned (spec §5), so a naming bug is permanent in a
way a rendering bug is not.

`tests/test_structure_id.py` pins the Python side against the same fixtures as
`StructureNodeTest` in `core-model`. If those two ever disagree, the pipeline writes node
names the app resolves to a different structure, or to none.

## What the source actually looks like

Worth knowing before changing anything here, because none of it matches what the model
sourcing spec assumed:

- Objects are named in **English**, not Latin, with laterality as a `.l` / `.r` suffix.
  Median structures have no suffix.
- Roughly **2,000 of 7,300 objects are label geometry**, not anatomy — the add-on
  declares them as `label_elements = {"-txt", ".t", ".j"}`.
- There are **two overlapping collection hierarchies**: the numbered `1: Skeletal system`
  layers are flat visibility groups, while `Bonus collection` holds anatomical
  containment. One object belongs to both at once, plus to regional groupings, so
  membership is a set rather than a path.
- **The term table has rows for the numbered layers** (`1: Skeletal system` resolves), so
  "the collection's name resolves to a term" is not enough to make it a group.
- **An object is linked into other systems' collections too.** The thyroid cartilage is
  under `Cartilages` and under the larynx; a muscle is in the collections of the nerves that
  supply it. A structure is parented inside its own system's tree first, and a group that
  ends up with nothing drawable under it is left out of the manifest.
- **Some names are an object and a collection at once** (`Mandible`). They are one structure.
- **Definitions are text datablocks** (`bpy.data.texts`) keyed by the term, not object
  custom properties.
- `TA2.csv` numbers enumerated structures — ribs, vertebrae, teeth — as `1113*8`. The
  `*` is folded to `_` for node names.

## Licensing

Z-Anatomy is CC BY-SA 4.0. Generated packs are derivatives of it and must be published
under the same licence with attribution; the application around them is a separate work.
See `docs/model-sourcing-spec.md` §4.2.
