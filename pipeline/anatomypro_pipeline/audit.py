"""Accounting for every object a pack selects.

The first pipeline looked only at meshes. Z-Anatomy draws vessels, most peripheral nerves
and the bronchi as bevelled curves, so 951 structures were left out of every pack and no
report said so. The rule since: an object the pack selects is either exported or named in
the report with the reason it was not, and a run that cannot account for one fails.

Pure Python, like the naming rules, so it is tested without Blender.
"""
from __future__ import annotations

import re
from typing import Callable, Dict, Iterable, List, Mapping, Optional, Sequence, Tuple

from . import naming

#: Object types that carry a structure's geometry. A curve is exported as the tube its own
#: bevel describes.
EXPORTABLE = frozenset({"MESH", "CURVE"})

#: Blender's own names for a new curve, which nobody renamed.
_DEFAULT_NAME = re.compile(r"^(bezier|nurbs)(curve|circle|path)\b", re.IGNORECASE)

#: What a tube was swept along or lathed from: `Medulla-path`, `Oesophagus-profile`.
_HELPER_SUFFIX = re.compile(r"-(path|profile|curve)$", re.IGNORECASE)

#: Curve detail from finest to coarsest, as (bevel resolution, resolution along the curve).
#: The source is authored at (4, 12): 5.0 million triangles for its 951 curves.
CURVE_RESOLUTIONS: Tuple[Tuple[int, int], ...] = ((2, 6), (1, 4), (1, 2), (0, 2))


class Unaccounted(Exception):
    """An object the pack selected is neither exported nor listed as skipped, or is both."""


def reason_to_skip(obj_type: str, name: str) -> Optional[str]:
    """Why an object is not exported, or None when it is.

    Labels first, whatever their type: Z-Anatomy's label geometry is meshes, text and
    curves alike.
    """
    parsed = naming.parse_object(name)
    if parsed.is_label:
        return "label"
    if obj_type == "FONT":
        return "text"
    if obj_type not in EXPORTABLE:
        return "type:" + obj_type.lower()
    core = parsed.core.strip()
    if core.startswith("?") or not naming.slugify(core):
        return "unnamed"
    if _DEFAULT_NAME.match(core) or _HELPER_SUFFIX.search(core):
        return "helper"
    return None


def check(
    considered: Iterable[str],
    exported: Iterable[str],
    skipped: Mapping[str, Sequence[str]],
) -> None:
    """Fails unless every considered object is exported or skipped, and none is both."""
    exported = set(exported)
    listed = {name for names in skipped.values() for name in names}
    dropped = sorted(set(considered) - exported - listed)
    twice = sorted(exported & listed)
    if dropped or twice:
        parts = []
        if dropped:
            parts.append(f"{len(dropped)} dropped without a reason: {dropped[:10]}")
        if twice:
            parts.append(f"{len(twice)} both exported and skipped: {twice[:10]}")
        raise Unaccounted("; ".join(parts))


def summary(skipped: Mapping[str, Sequence[str]]) -> Dict[str, Dict]:
    """The report's `skipped` block: a count per reason, and the names behind it."""
    kept = {reason: sorted(names) for reason, names in skipped.items() if names}
    return {
        "counts": {reason: len(names) for reason, names in sorted(kept.items())},
        "names": dict(sorted(kept.items())),
    }


def fitting_resolution(
    triangles_at: Callable[[Tuple[int, int]], int],
    target: int,
    steps: Sequence[Tuple[int, int]] = CURVE_RESOLUTIONS,
) -> Tuple[Tuple[int, int], int]:
    """The finest curve resolution whose tube fits the triangle target, and its count.

    Cutting detail at the curve keeps a tube round and evenly divided; collapsing its mesh
    afterwards does not. When even the coarsest step is over, that step is returned and the
    mesh decimation that follows deals with what is left.
    """
    step, count = steps[0], 0
    for step in steps:
        count = triangles_at(step)
        if count <= target:
            break
    return step, count
