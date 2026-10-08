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
import subprocess
import sys

import bpy  # noqa: F401  (only available inside Blender)

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from anatomypro_pipeline import audit, manifest, naming, selection, ta2  # noqa: E402


def parse_args(argv):
    if "--" in argv:
        argv = argv[argv.index("--") + 1:]
    parser = argparse.ArgumentParser()
    parser.add_argument("--pack", required=True)
    parser.add_argument("--ta2", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--draco", action="store_true")
    parser.add_argument(
        "--no-optimise",
        action="store_true",
        help="Skip the gltfpack vertex cache pass. For measuring what it is worth.",
    )
    parser.add_argument(
        "--detail",
        action="store_true",
        help="Also emit one undecimated glTF per structure for close inspection.",
    )
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

#: A detail mesh is the source geometry. The ceiling only guards against outliers in
#: systems that have not been profiled; nothing in the skeletal or muscular packs comes
#: close, where the densest single structure is about 41k triangles.
DETAIL_TRIANGLE_CEILING = 150_000


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


def tube(obj, target):
    """Replaces a bevelled curve by the mesh of its tube, and returns that mesh object.

    Z-Anatomy draws vessels, most peripheral nerves and the bronchi as curves with a bevel,
    so the tube is already described; evaluating the curve gives it, at the radii its
    author chose. As authored the 951 of them cost 5.0 million triangles, so detail is cut
    at the curve first — the finest resolution that fits the pack's target — and only what
    still does not fit is left to the mesh decimation every structure goes through.

    Returns the new object, the triangles as authored, and the resolution used. The new
    object takes the curve's name, so everything downstream reads names as before.
    """
    data = obj.data
    authored = triangle_count(obj)

    def triangles_at(step):
        data.bevel_resolution, data.resolution_u = step
        return triangle_count(obj)

    step, _ = audit.fitting_resolution(triangles_at, target)
    data.bevel_resolution, data.resolution_u = step
    bpy.context.view_layer.update()
    evaluated = obj.evaluated_get(bpy.context.evaluated_depsgraph_get())
    mesh = bpy.data.meshes.new_from_object(evaluated)

    name = obj.name
    obj.name = name + "~curve"
    replacement = bpy.data.objects.new(name, mesh)
    replacement.matrix_world = obj.matrix_world.copy()
    # In the scene's own collection, so the exporter can select it whatever the visibility
    # of the collections the curve sat in.
    bpy.context.scene.collection.objects.link(replacement)
    return replacement, authored, step


#: Collections describing where a structure is rather than what it is part of. Region is
#: already its own field in §5, and letting it double as the taxonomy parent buries good
#: groupings — a vertebra's useful siblings are the other vertebrae, not the 37 unrelated
#: things that also happen to be in the thorax.
_REGION_AXIS = {"Regions of human body", "9: Regions of human body", "Main divisions"}


def collection_groups(table, closure, present_collections):
    """Grouping collections that are themselves structures.

    A collection qualifies when it holds geometry in this pack and its name resolves to a
    Terminologia term. That join doubles as the filter: `Bonus collection` and
    `Cross section planes` do not resolve, so they never become structures, and their
    children attach to the next collection that does. It is not the whole filter: the
    numbered visibility layers do resolve, and are excluded by name.
    """
    groups = {}
    for name in present_collections:
        # A numbered layer resolves — the term table has rows for them — and is not taxonomy.
        if selection.is_layer(name):
            continue
        if closure.get(name, {name}) & _REGION_AXIS:
            continue
        entry = table.lookup(name)
        if not entry:
            continue
        groups[name] = naming.structure_id(
            ta2.code_for(entry.ta2_id), naming.slugify(entry.latin), "M"
        )
    return groups


def export_detail_meshes(chosen, structure_of_object, out_directory):
    """One glTF per structure, at source density, for close inspection.

    Written before decimation runs, because after it the source geometry is gone. A
    structure modelled as several objects gets them all in one file.
    """
    directory = os.path.join(out_directory, "detail")
    os.makedirs(directory, exist_ok=True)

    by_structure = {}
    for obj, _, _ in chosen:
        by_structure.setdefault(structure_of_object[obj.name], []).append(obj)

    for structure, objects in by_structure.items():
        triangles = sum(triangle_count(o) for o in objects)
        if triangles == 0 or triangles > DETAIL_TRIANGLE_CEILING:
            continue
        bpy.ops.object.select_all(action="DESELECT")
        for o in objects:
            o.select_set(True)
        bpy.ops.export_scene.gltf(
            filepath=os.path.join(directory, f"{structure}.glb"),
            export_format="GLB",
            use_selection=True,
            export_apply=True,
            export_yup=True,
        )
    print(f"[pipeline] wrote {len(by_structure)} detail meshes")


#: gltfpack reorders indices for the GPU's post-transform vertex cache. Blender's exporter
#: does not, and the Decimate modifier actively destroys whatever locality the source had
#: - measured at twice the frame time for a third of the triangles (spec §25.4).
#:
#: `-kn` is not optional: without it gltfpack merges meshes and drops node names, and node
#: names are the entire mapping from geometry to StructureId.
GLTFPACK = ["npx", "--yes", "gltfpack@0.24.0"]


def optimise_vertex_cache(path):
    """Reorders geometry for the vertex cache, in place.

    Returns the node names before and after so the caller can refuse to ship a mesh whose
    names gltfpack changed.
    """
    before = node_names(path)
    packed = path + ".packed.glb"
    result = subprocess.run(
        GLTFPACK + ["-i", path, "-o", packed, "-kn", "-km", "-ke", "-noq"],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0 or not os.path.isfile(packed):
        raise SystemExit(
            "gltfpack failed; it is required for the vertex cache pass.\n"
            + result.stderr.strip()
        )

    after = node_names(packed)
    if after != before:
        os.remove(packed)
        lost = sorted(before - after)[:5]
        raise SystemExit(
            f"gltfpack changed {len(before - after)} node names, e.g. {lost}. "
            "Node names are the mapping to StructureId and must survive."
        )

    os.replace(packed, path)
    return before


def node_names(path):
    """The `name` of every node in a .glb, read from its JSON chunk."""
    import struct

    with open(path, "rb") as handle:
        data = handle.read()
    length = struct.unpack("<I", data[12:16])[0]
    document = json.loads(data[20 : 20 + length].decode("utf-8"))
    return {n["name"] for n in document.get("nodes", []) if "name" in n}


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
    # How many collections enclose each collection; used to find the nearest group.
    depth = {name: len(parents) for name, parents in closure.items()}
    region = selection.pack_region(spec)

    # Every object the pack selects is exported or listed with the reason it was not
    # (audit.check, at the end). Looking only at meshes dropped two whole systems in silence.
    considered = []
    skipped = {}
    selected = []
    for obj in list(bpy.data.objects):
        collections = membership(obj, closure)
        if not selection.is_included(collections, spec):
            continue
        considered.append(obj.name)
        reason = audit.reason_to_skip(obj.type, obj.name)
        if reason:
            skipped.setdefault(reason, []).append(obj.name)
            continue
        selected.append((obj, naming.parse_object(obj.name), collections))

    chosen = []
    curve_steps = {}
    authored_triangles = {}
    for obj, parsed, collections in selected:
        if obj.type == "CURVE":
            name = obj.name
            obj, authored, step = tube(obj, target)
            authored_triangles[name] = authored
            curve_steps[step] = curve_steps.get(step, 0) + 1
        chosen.append((obj, parsed, collections))

    print(
        f"[pipeline] {len(chosen)} objects selected for {spec.pack_id}, "
        f"{len(authored_triangles)} of them curves"
    )

    # The taxonomy comes from collections, not from object parenting, which the source
    # does not use, nor from `.g` objects, which are too sparse to parent a whole pack.
    present_collections = set()
    for _, _, collections in chosen:
        present_collections |= collections
    group_ids = collection_groups(table, closure, present_collections)

    provisional = []
    for obj, parsed, collections in chosen:
        entry = table.lookup(parsed.core)
        code = ta2.code_for(entry.ta2_id) if entry else "ZAN"
        slug = naming.slugify(entry.latin if entry else parsed.core)
        provisional.append(naming.node_name(code, slug, parsed.laterality, parsed.discriminator))
    unique = naming.deduplicate(provisional)
    structure_of_object = {
        obj.name: naming.structure_id(
            ta2.code_for(table.lookup(parsed.core).ta2_id) if table.lookup(parsed.core) else "ZAN",
            naming.slugify(table.lookup(parsed.core).latin if table.lookup(parsed.core) else parsed.core),
            parsed.laterality,
        )
        for (obj, parsed, _), _ in zip(chosen, unique)
    }

    if args.detail:
        export_detail_meshes(chosen, structure_of_object, os.path.join(args.out, spec.pack_id))

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
        # A tube's source is the curve as authored, not the resolution it was cut to.
        source_triangles = authored_triangles.get(obj.name, source_triangles)
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
            "region": selection.region_for(collections, preferred=region),
            "parent_structure": selection.nearest_group(
                collections, depth, group_ids,
                own=naming.structure_id(code, slug, parsed.laterality),
                home=selection.taxonomy_home(collections),
                closure=closure,
            ),
            "laterality": parsed.laterality,
            "discriminator": parsed.discriminator,
            "triangles": triangles,
            "source_triangles": source_triangles,
            "source_object": obj.name,
            "source_type": "CURVE" if obj.name in authored_triangles else "MESH",
        })
        obj.name = node
        obj.select_set(True)

    out = os.path.join(args.out, spec.pack_id)
    os.makedirs(out, exist_ok=True)

    mesh_path = os.path.join(out, "mesh.glb")
    bpy.ops.export_scene.gltf(
        filepath=mesh_path,
        export_format="GLB",
        use_selection=True,
        export_apply=True,
        export_draco_mesh_compression_enable=bool(args.draco),
        export_yup=True,
    )
    if not args.no_optimise:
        names = optimise_vertex_cache(mesh_path)
        print(f"[pipeline] vertex cache pass kept {len(names)} node names")

    # A group's own parent is the nearest matched collection above it, so the taxonomy
    # nests rather than flattening onto whatever matched first.
    group_rows = []
    for name, structure in group_ids.items():
        entry = table.lookup(name)
        parent = selection.nearest_group(
            closure.get(name, {name}) - {name}, depth, group_ids, own=structure
        )
        group_rows.append({
            "structure_id": structure,
            "ta2_id": entry.ta2_id,
            "english": entry.english,
            "latin": entry.latin,
            "definition": definition_of(name),
            "system": None,
            "region": region,
            "laterality": "M",
            "parent_id": parent if parent != structure else None,
        })

    document, report = manifest.build(spec.pack_id, records, groups=group_rows)
    if empty:
        skipped["empty"] = empty
    audit.check(considered, [r["source_object"] for r in records], skipped)
    report["skipped"] = audit.summary(skipped)
    report["curves"] = {
        "exported": sum(1 for r in records if r["source_type"] == "CURVE"),
        # How many curves were cut to each (bevel resolution, resolution along the curve).
        "resolutions": {f"{bevel},{along}": n for (bevel, along), n in sorted(curve_steps.items())},
        "authored_triangles": sum(authored_triangles.values()),
    }
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
