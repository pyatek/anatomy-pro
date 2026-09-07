"""The only module that imports `bpy`.

Everything it decides is delegated to the pure modules beside it, so this file stays a
walk-decimate-export loop with no rules of its own worth testing in isolation.

    blender --background Startup.blend \
        --python anatomypro_pipeline/blender_export.py -- \
        --pack packs/skeletal-thorax.json --ta2 TA2.csv --out build/packs
"""
from __future__ import annotations

import argparse
import json
import os
import sys

import bpy  # noqa: F401  (only available inside Blender)

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from anatomypro_pipeline import manifest, naming, selection, ta2  # noqa: E402


def parse_args(argv):
    if "--" in argv:
        argv = argv[argv.index("--") + 1:]
    parser = argparse.ArgumentParser()
    parser.add_argument("--pack", required=True)
    parser.add_argument("--ta2", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--draco", action="store_true")
    return parser.parse_args(argv)


def ancestors():
    """Maps every collection to itself plus all collections above it.

    Membership has to be transitive: an object linked directly into `Ribs` also belongs
    to `Bones of thorax` above it, and a pack is defined in terms of the higher name.
    """
    parents = {}
    for parent in bpy.data.collections:
        for child in parent.children:
            parents.setdefault(child.name, []).append(parent.name)

    resolved = {}

    def climb(name, seen):
        if name in resolved:
            return resolved[name]
        if name in seen:
            return set()
        seen = seen | {name}
        result = {name}
        for parent in parents.get(name, ()):
            result |= climb(parent, seen)
        resolved[name] = result
        return result

    return {c.name: climb(c.name, set()) for c in bpy.data.collections}


def membership(obj, closure):
    """Every collection an object belongs to, directly or by containment."""
    names = set()
    for collection in obj.users_collection:
        names |= closure.get(collection.name, {collection.name})
    return names


def definition_of(core_name):
    """Z-Anatomy keeps its 3,500+ definitions as text datablocks named after the term.

    Not as object custom properties, which carry only add-on state (cross-section flags,
    shader colours). The lookup is by the object's core name, parentheses included, since
    that is how the datablocks are keyed.
    """
    for key in (core_name, f"({core_name})"):
        block = bpy.data.texts.get(key)
        if block:
            text = block.as_string().strip()
            if text:
                return text
    return None


#: Below this a collapse tends to destroy the shape rather than simplify it.
DECIMATE_RATIO_FLOOR = 0.002


def triangle_count(obj):
    """Triangles after modifiers, which is what actually reaches the exporter."""
    # The depsgraph is stale immediately after a modifier is added in background mode.
    bpy.context.view_layer.update()
    evaluated = obj.evaluated_get(bpy.context.evaluated_depsgraph_get())
    mesh = evaluated.to_mesh()
    if mesh is None:
        return 0
    try:
        mesh.calc_loop_triangles()
        return len(mesh.loop_triangles)
    finally:
        evaluated.to_mesh_clear()


def decimate(obj, target):
    """Collapses to the per-structure triangle target, never subdividing upward.

    Returns the count before and after, so the report can show what decimation actually
    achieved rather than only what was asked for.
    """
    before = triangle_count(obj)
    if before <= target:
        return before, before
    modifier = obj.modifiers.new(name="anatomypro-decimate", type="DECIMATE")
    modifier.decimate_type = "COLLAPSE"
    # The floor keeps a collapse from degenerating the mesh entirely. A structure that
    # cannot reach the target within it is reported rather than silently left oversized.
    modifier.ratio = max(DECIMATE_RATIO_FLOOR, target / float(before))
    return before, triangle_count(obj)


def main():
    args = parse_args(sys.argv)

    with open(args.pack, encoding="utf-8") as handle:
        pack = json.load(handle)
    spec = selection.PackSpec(
        pack_id=pack["pack_id"],
        include=tuple(pack.get("include", ())),
        exclude=tuple(pack.get("exclude", ())),
        require=tuple(pack.get("require", ())),
    )
    target = int(pack.get("max_triangles_per_structure", 5000))
    table = ta2.load_table(args.ta2)
    closure = ancestors()

    chosen = []
    for obj in bpy.data.objects:
        if obj.type != "MESH":
            continue
        collections = membership(obj, closure)
        if not selection.is_included(collections, spec):
            continue
        parsed = naming.parse_object(obj.name)
        if parsed.is_label:
            continue
        chosen.append((obj, parsed, collections))

    print(f"[pipeline] {len(chosen)} mesh objects selected for {spec.pack_id}")

    provisional = []
    for obj, parsed, collections in chosen:
        entry = table.lookup(parsed.core)
        code = ta2.code_for(entry.ta2_id) if entry else "ZAN"
        slug = naming.slugify(entry.latin if entry else parsed.core)
        provisional.append(naming.node_name(code, slug, parsed.laterality, parsed.discriminator))
    unique = naming.deduplicate(provisional)

    bpy.ops.object.select_all(action="DESELECT")
    records = []
    # Objects that carry no triangles cannot be drawn or picked. Z-Anatomy has a few —
    # they appear to be anchor geometry for labels — and letting them through would put
    # unpickable entries in the manifest that look like real structures.
    empty = []
    for (obj, parsed, collections), node in zip(chosen, unique):
        entry = table.lookup(parsed.core)
        code = ta2.code_for(entry.ta2_id) if entry else "ZAN"
        slug = naming.slugify(entry.latin if entry else parsed.core)

        source_triangles, triangles = decimate(obj, target)
        if triangles == 0:
            empty.append(obj.name)
            continue
        records.append({
            "node_name": node,
            "structure_id": naming.structure_id(code, slug, parsed.laterality),
            "ta2_id": entry.ta2_id if entry else None,
            "english": entry.english if entry else parsed.core,
            "latin": entry.latin if entry else None,
            "definition": definition_of(parsed.core),
            "system": selection.system_for(collections),
            "parent": obj.parent.name if obj.parent else None,
            "laterality": parsed.laterality,
            "discriminator": parsed.discriminator,
            "triangles": triangles,
            "source_triangles": source_triangles,
            "source_object": obj.name,
        })
        obj.name = node
        obj.select_set(True)

    # Renaming happens after every lookup so parents can be resolved to node names.
    renamed = {r["source_object"]: r["node_name"] for r in records}
    for record in records:
        record["parent"] = renamed.get(record["parent"])

    out = os.path.join(args.out, spec.pack_id)
    os.makedirs(out, exist_ok=True)

    bpy.ops.export_scene.gltf(
        filepath=os.path.join(out, "mesh.glb"),
        export_format="GLB",
        use_selection=True,
        export_apply=True,
        export_draco_mesh_compression_enable=bool(args.draco),
        export_yup=True,
    )

    document, report = manifest.build(spec.pack_id, records)
    report["skipped_empty"] = sorted(empty)
    report["decimation_floor_reached"] = sorted(
        {r["english"] for r in records if r["triangles"] > target}
    )
    for name, payload in (("manifest.json", document), ("report.json", report)):
        with open(os.path.join(out, name), "w", encoding="utf-8") as handle:
            json.dump(payload, handle, indent=2, ensure_ascii=False)

    print(f"[pipeline] wrote {out}")
    print(json.dumps(report["budget"], indent=2))


if __name__ == "__main__":
    main()
