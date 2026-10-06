"""The two documents a pipeline run produces besides the geometry.

`manifest.json` is what Phase 1's `core-data` will ingest. `report.json` is what answers
the Phase 0 question: whether a real region fits the §6.1 budget, measured before any
device is involved.
"""
from __future__ import annotations

import statistics
from typing import Any, Dict, List, Sequence, Tuple

from . import definitions

#: Model sourcing spec §3.
TRIANGLES_PER_STRUCTURE = (2_000, 5_000)
MAX_RESIDENT_TRIANGLES = 3_000_000
MAX_DRAW_CALLS = 800

#: §6.1 mitigations that only pay off across several loaded regions, which Phase 0 does
#: not do. Named in the report so its numbers are read as a baseline, not a final answer.
DEFERRED = (
    "LOD generation",
    "merging non-interactive geometry",
)


def build(
    pack_id: str,
    records: Sequence[Dict[str, Any]],
    groups: Sequence[Dict[str, Any]] = (),
) -> Tuple[Dict, Dict]:
    # A parent outside this pack resolves to nothing rather than to a dangling identifier
    # the app would have to defend against. Groups count as present: they are precisely
    # what leaves are parented to.
    present = {r["structure_id"] for r in records} | {g["structure_id"] for g in groups}

    structures: Dict[str, Dict[str, Any]] = {}
    for r in records:
        entry = structures.setdefault(
            r["structure_id"],
            {
                "structure_id": r["structure_id"],
                "ta2_id": r["ta2_id"],
                "english": r["english"],
                "latin": r["latin"],
                **definitions.fields(r["definition"]),
                "parent_id": None,
                "is_group": False,
                "system": r["system"],
                "region": r["region"],
                "laterality": r["laterality"],
                "nodes": [],
                "triangles": 0,
            },
        )
        entry["nodes"].append(r["node_name"])
        entry["triangles"] += r["triangles"]
        if entry["parent_id"] is None:
            parent = r.get("parent_structure")
            if parent and parent != r["structure_id"] and parent in present:
                entry["parent_id"] = parent

    # Grouping collections are structures without geometry: navigable, readable, and
    # usable as §8.1's parent for grouping siblings, but never drawn.
    drawable = list(structures.values())
    grouped = [
        {
            "structure_id": g["structure_id"],
            "ta2_id": g["ta2_id"],
            "english": g["english"],
            "latin": g["latin"],
            **definitions.fields(g["definition"]),
            "system": g["system"],
            "region": g["region"],
            "laterality": g["laterality"],
            "parent_id": g["parent_id"],
            "is_group": True,
            "nodes": [],
            "triangles": 0,
        }
        for g in groups
    ]

    ordered = drawable
    total_triangles = sum(e["triangles"] for e in ordered)
    matched = sum(1 for e in ordered if e["ta2_id"])
    counts = [e["triangles"] for e in ordered] or [0]

    document = {"pack_id": pack_id, "structures": drawable + grouped}

    report = {
        "pack_id": pack_id,
        "objects": {
            "nodes": len(records),
            "structures": len(ordered),
            "groups": len(grouped),
        },
        "join": {
            "matched": matched,
            "unmatched": len(ordered) - matched,
            "rate": round(100.0 * matched / len(ordered), 1) if ordered else 0.0,
        },
        "budget": {
            "triangles": {
                "total": total_triangles,
                "limit": MAX_RESIDENT_TRIANGLES,
                "within": total_triangles <= MAX_RESIDENT_TRIANGLES,
            },
            # One draw call per node is the pessimistic case: nothing is merged or
            # instanced yet, so this is an upper bound rather than a measurement.
            "draw_calls": {
                "estimate": len(records),
                "limit": MAX_DRAW_CALLS,
                "within": len(records) <= MAX_DRAW_CALLS,
            },
            "per_structure": {
                "mean": round(statistics.mean(counts), 1),
                "median": round(statistics.median(counts), 1),
                "max": max(counts),
                "target": list(TRIANGLES_PER_STRUCTURE),
                "under_minimum": sum(1 for c in counts if c < TRIANGLES_PER_STRUCTURE[0]),
                "over_maximum": sum(1 for c in counts if c > TRIANGLES_PER_STRUCTURE[1]),
            },
        },
        # What the source actually carries, before decimation. A detail view loads this,
        # so its distribution decides whether "undecimated" needs a ceiling.
        "source_triangles": {
            "total": sum(r.get("source_triangles", 0) for r in records),
            "max": max((r.get("source_triangles", 0) for r in records), default=0),
            "median": int(statistics.median([r.get("source_triangles", 0) for r in records] or [0])),
            "over_100k": sum(1 for r in records if r.get("source_triangles", 0) > 100_000),
        },
        "unmatched_terms": sorted(e["english"] for e in ordered if not e["ta2_id"]),
        "deferred": list(DEFERRED),
    }
    return document, report
