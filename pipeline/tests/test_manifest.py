"""Assembling the pack manifest and the budget report."""
from anatomypro_pipeline import manifest


def record(node, sid, tris, ta2_id="4205", latin="aorta_abdominalis"):
    return {
        "node_name": node, "structure_id": sid, "ta2_id": ta2_id,
        "english": "Abdominal aorta", "latin": latin, "definition": "A vessel.",
        "system": "skeletal-system", "region": "trunk", "parent_structure": None, "laterality": "M",
        "discriminator": None, "triangles": tris, "source_object": node,
    }


def test_groups_several_nodes_under_one_structure():
    # This is the whole reason MeshRef is a list per structure (spec §5).
    doc, _ = manifest.build("p", [
        record("1837__fossa__M__s", "1837-fossa-median", 100),
        record("1837__fossa__M__i", "1837-fossa-median", 150),
    ])
    assert len(doc["structures"]) == 1
    assert doc["structures"][0]["nodes"] == ["1837__fossa__M__s", "1837__fossa__M__i"]
    assert doc["structures"][0]["triangles"] == 250


def test_reports_the_join_rate():
    _, report = manifest.build("p", [
        record("a__b__M", "a-b-median", 10),
        record("ZAN__c__M", "zan-c-median", 10, ta2_id=None),
    ])
    assert report["join"]["matched"] == 1
    assert report["join"]["unmatched"] == 1
    assert report["join"]["rate"] == 50.0


def test_measures_the_total_against_the_resident_triangle_budget():
    _, report = manifest.build("p", [record("a__b__M", "a-b-median", 4_000_000)])
    assert report["budget"]["triangles"]["total"] == 4_000_000
    assert report["budget"]["triangles"]["limit"] == 3_000_000
    assert report["budget"]["triangles"]["within"] is False


def test_counts_structures_outside_the_per_structure_triangle_range():
    # Sourcing spec §3 wants 2,000-5,000 per structure after decimation.
    _, report = manifest.build("p", [
        record("a__b__M", "a-b-median", 500),
        record("c__d__M", "c-d-median", 3_000),
        record("e__f__M", "e-f-median", 9_000),
    ])
    assert report["budget"]["per_structure"]["under_minimum"] == 1
    assert report["budget"]["per_structure"]["over_maximum"] == 1


def test_names_what_was_deliberately_not_done():
    # Without this the numbers get read as a final answer rather than a Phase 0 baseline.
    _, report = manifest.build("p", [record("a__b__M", "a-b-median", 10)])
    assert report["deferred"]


def test_resolves_a_parent_node_to_the_parent_structure():
    """§5 hierarchy is between structures, not nodes.

    The exporter records a parent by node name because that is what Blender has; the
    manifest has to translate it, or §8.1's sibling tier has nothing to group on.
    """
    child = record("child__x__M", "child-x-median", 10)
    child["parent_structure"] = "parent-y-median"
    parent = record("parent__y__M", "parent-y-median", 10)

    doc, _ = manifest.build("p", [parent, child])
    by_id = {s["structure_id"]: s for s in doc["structures"]}
    assert by_id["child-x-median"]["parent_id"] == "parent-y-median"
    assert by_id["parent-y-median"]["parent_id"] is None


def test_a_parent_outside_the_pack_is_dropped_rather_than_dangling():
    child = record("child__x__M", "child-x-median", 10)
    child["parent_structure"] = "somewhere-else-median"
    doc, _ = manifest.build("p", [child])
    assert doc["structures"][0]["parent_id"] is None


def group(sid, english, parent=None):
    return {
        "structure_id": sid, "ta2_id": "1105", "english": english,
        "latin": "Costae", "definition": None, "system": "skeletal-system",
        "region": "trunk", "parent_id": parent, "laterality": "M",
    }


def test_group_structures_appear_with_no_geometry_of_their_own():
    """A grouping collection is a structure you can read about and navigate to.

    It carries no mesh refs: its geometry is its descendants', and duplicating that here
    would make mesh_ref rows that do not correspond to any node (spec §5).
    """
    rib = record("1107__costa_prima__L", "1107-costa-prima-left", 900)
    rib["parent_structure"] = "1105-costae-median"
    doc, _ = manifest.build("p", [rib], groups=[group("1105-costae-median", "Ribs")])
    by_id = {s["structure_id"]: s for s in doc["structures"]}
    ribs = by_id["1105-costae-median"]
    assert ribs["nodes"] == []
    assert ribs["triangles"] == 0
    assert ribs["is_group"] is True
    assert by_id["1107-costa-prima-left"]["is_group"] is False


def test_groups_are_counted_apart_from_drawable_structures():
    # The §6.1 budget is about what renders; a group draws nothing and must not inflate
    # the draw-call estimate.
    leaf = record("a__b__M", "a-b-median", 900)
    leaf["parent_structure"] = "1105-costae-median"
    _, report = manifest.build("p", [leaf], groups=[group("1105-costae-median", "Ribs")])
    assert report["objects"]["structures"] == 1
    assert report["objects"]["groups"] == 1
    assert report["budget"]["draw_calls"]["estimate"] == 1


def test_a_leaf_may_be_parented_to_a_group():
    # The whole point of synthesising groups: they are what leaves hang from. Validating
    # a parent only against other leaves silently discards every one of those links.
    child = record("1107__costa_prima__L", "1107-costa-prima-left", 900)
    child["parent_structure"] = "1105-costae-median"
    doc, _ = manifest.build("p", [child], groups=[group("1105-costae-median", "Ribs")])
    leaf = next(s for s in doc["structures"] if s["structure_id"] == "1107-costa-prima-left")
    assert leaf["parent_id"] == "1105-costae-median"


def test_a_group_nothing_drawable_hangs_from_is_left_out():
    """A group is there to hold what can be seen.

    Z-Anatomy links a muscle into the collections of the nerves that supply it, so a
    muscular pack matched Nervous system, Cranial nerves, Brachial plexus and more as
    groups, with no structure of the pack under any of them. Each was a root or a dead
    branch in the atlas, a search hit, and a card for the reviewer.
    """
    muscle = record("m__x__L", "m-x-left", 900)
    muscle["parent_structure"] = "muscles-median"
    doc, report = manifest.build(
        "p",
        [muscle],
        groups=[
            group("muscles-median", "Muscles"),
            group("nervous-median", "Nervous system"),
            group("plexus-median", "Brachial plexus", parent="nervous-median"),
        ],
    )
    assert {s["structure_id"] for s in doc["structures"]} == {"m-x-left", "muscles-median"}
    assert report["objects"]["groups"] == 1
    assert report["taxonomy"]["pruned_groups"] == ["nervous-median", "plexus-median"]


def test_a_group_is_kept_for_what_hangs_from_the_groups_below_it():
    rib = record("r__x__L", "r-x-left", 900)
    rib["parent_structure"] = "ribs-median"
    doc, _ = manifest.build(
        "p",
        [rib],
        groups=[group("skeleton-median", "Skeleton"), group("ribs-median", "Ribs", parent="skeleton-median")],
    )
    assert {s["structure_id"] for s in doc["structures"]} == {"r-x-left", "ribs-median", "skeleton-median"}


def test_an_object_and_a_collection_of_one_name_are_one_structure():
    """The mandible is a mesh, and a collection holding the lower teeth.

    Both resolve to the same id. Written out twice, the manifest had two rows with one
    identifier; written once, the bone keeps its mesh, takes the collection's place in the
    tree, and the teeth hang from it.
    """
    mandible = record("835__mandibula__M", "835-mandibula-median", 900)
    mandible["parent_structure"] = "head-median"
    tooth = record("t__x__L", "t-x-left", 300)
    tooth["parent_structure"] = "835-mandibula-median"
    doc, report = manifest.build(
        "p",
        [mandible, tooth],
        groups=[group("head-median", "Bones of head"), group("835-mandibula-median", "Mandible", parent="head-median")],
    )
    ids = [s["structure_id"] for s in doc["structures"]]
    assert ids.count("835-mandibula-median") == 1
    by_id = {s["structure_id"]: s for s in doc["structures"]}
    assert by_id["835-mandibula-median"]["nodes"] == ["835__mandibula__M"]
    assert by_id["835-mandibula-median"]["parent_id"] == "head-median"
    assert by_id["t-x-left"]["parent_id"] == "835-mandibula-median"
    assert report["objects"]["groups"] == 1


def test_the_report_names_the_roots_and_any_leaf_left_without_a_parent():
    # Read report.json first: a pack of one system should have that system as its one
    # root, and a parentless leaf is a fault in the rules, not a fact about anatomy.
    rib = record("r__x__L", "r-x-left", 900)
    rib["parent_structure"] = "skeleton-median"
    stray = record("s__x__M", "s-x-median", 900)
    _, report = manifest.build("p", [rib, stray], groups=[group("skeleton-median", "Skeleton")])
    assert report["taxonomy"]["roots"] == ["s-x-median", "skeleton-median"]
    assert report["taxonomy"]["parentless_leaves"] == ["s-x-median"]


def test_a_root_that_has_a_mesh_of_its_own_is_not_a_stray_leaf():
    # Z-Anatomy has a small `.g` object named for a whole system. It resolves to the
    # system's id, so the root is drawable; it is still the root, with everything under it.
    root = record("352__systema_skeletale__M__g", "skeleton-median", 450)
    rib = record("r__x__L", "r-x-left", 900)
    rib["parent_structure"] = "ribs-median"
    _, report = manifest.build(
        "p",
        [root, rib],
        groups=[group("skeleton-median", "Skeleton"), group("ribs-median", "Ribs", parent="skeleton-median")],
    )
    assert report["taxonomy"]["roots"] == ["skeleton-median"]
    assert report["taxonomy"]["parentless_leaves"] == []


def test_a_definition_goes_out_as_its_lead_with_its_source_beside_it():
    article = "AORTA\n\n\nThe main artery.\n\n\n== History ==\n\nLong.\n\n\nhttps://en.wikipedia.org/wiki/Aorta"
    entry = dict(record("a__b__M", "a-b-median", 10), definition=article)
    group = {
        "structure_id": "g-median", "ta2_id": "1", "english": "Vessels", "latin": "Vasa",
        "definition": article, "system": None, "region": None, "laterality": "M", "parent_id": None,
    }

    doc, _ = manifest.build("p", [entry], [group])

    for structure in doc["structures"]:
        assert structure["definition"] == "The main artery."
        assert structure["definition_source"] == "https://en.wikipedia.org/wiki/Aorta"
        assert structure["definition_licence"] == "CC BY-SA 3.0"
