"""Assembling the pack manifest and the budget report."""
from anatomypro_pipeline import manifest


def record(node, sid, tris, ta2_id="4205", latin="aorta_abdominalis"):
    return {
        "node_name": node, "structure_id": sid, "ta2_id": ta2_id,
        "english": "Abdominal aorta", "latin": latin, "definition": "A vessel.",
        "system": "skeletal-system", "parent": None, "laterality": "M",
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
