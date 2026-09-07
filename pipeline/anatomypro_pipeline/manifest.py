"""The two documents a pipeline run produces besides the geometry.

`manifest.json` is what Phase 1's `core-data` will ingest. `report.json` is what answers
the Phase 0 question: whether a real region fits the §6.1 budget, measured before any
device is involved.
"""
from __future__ import annotations

import statistics
from typing import Any, Dict, List, Sequence, Tuple

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


def build(pack_id: str, records: Sequence[Dict[str, Any]]) -> Tuple[Dict, Dict]:
    structures: Dict[str, Dict[str, Any]] = {}
    for r in records:
        entry = structures.setdefault(
            r["structure_id"],
            {
                "structure_id": r["structure_id"],
                "ta2_id": r["ta2_id"],
                "english": r["english"],
                "latin": r["latin"],
                "definition": r["definition"],
                "system": r["system"],
                "laterality": r["laterality"],
                "nodes": [],
                "triangles": 0,
            },
        )
        entry["nodes"].append(r["node_name"])
        entry["triangles"] += r["triangles"]

    ordered = list(structures.values())
    total_triangles = sum(e["triangles"] for e in ordered)
    matched = sum(1 for e in ordered if e["ta2_id"])
    counts = [e["triangles"] for e in ordered] or [0]

    document = {"pack_id": pack_id, "structures": ordered}

    report = {
        "pack_id": pack_id,
        "objects": {"nodes": len(records), "structures": len(ordered)},
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
        "unmatched_terms": sorted(e["english"] for e in ordered if not e["ta2_id"]),
        "deferred": list(DEFERRED),
    }
    return document, report
