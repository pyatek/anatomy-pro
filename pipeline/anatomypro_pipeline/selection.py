"""Deciding which Blender objects make up a pack, and which system each belongs to.

Z-Anatomy keeps two overlapping hierarchies. `1: Skeletal system` and its numbered
siblings are flat visibility layers the add-on toggles; `Bonus collection` holds the
anatomical containment tree. An object is linked into both at once, plus into whatever
regional groupings apply — a rib is in `1: Skeletal system`, `Body of rib`, `Thorax` and
`Trunk` simultaneously.

Membership is therefore a set of collection names, not a path, and `SystemId` falls out
of the numbered layer — which is why `setSystemVisibility` need not wait for `core-data`
after all (design spec §20.2 assumed otherwise).
"""
from __future__ import annotations

import re
from dataclasses import dataclass
from typing import AbstractSet, Optional, Tuple

from . import naming

#: Top-level collections are named "1: Skeletal system", "2: Muscular insertions", ...
_NUMBERED_SYSTEM = re.compile(r"^(\d+)\s*:\s*(.+)$")


@dataclass(frozen=True)
class PackSpec:
    pack_id: str
    include: Tuple[str, ...]
    exclude: Tuple[str, ...] = ()
    #: Every name here must be present. A pack is usually a region *and* a system layer
    #: — "the skeletal part of the trunk" — which neither include nor exclude expresses.
    require: Tuple[str, ...] = ()


def is_included(collections: AbstractSet[str], spec: PackSpec) -> bool:
    """An exclusion anywhere in the membership wins, so a subtree can be carved out."""
    if collections & set(spec.exclude):
        return False
    if not set(spec.require).issubset(collections):
        return False
    return bool(collections & set(spec.include)) if spec.include else bool(spec.require)


def system_for(collections: AbstractSet[str]) -> Optional[str]:
    """The lowest-numbered visibility layer the object belongs to.

    Lowest rather than first keeps the answer independent of Blender's iteration order,
    which is not guaranteed stable across versions.
    """
    numbered = []
    for name in collections:
        match = _NUMBERED_SYSTEM.match(name)
        if match:
            numbered.append((int(match.group(1)), match.group(2)))
    if not numbered:
        return None
    return naming.slugify(min(numbered)[1]).replace("_", "-")
