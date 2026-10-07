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


#: The source's `Main divisions` collection. Laterality is a separate field in §5, so the
#: side is stripped: the two hands are one region, which is what §8.1's "same region"
#: distractor tier means.
_BODY_DIVISIONS = {
    "Head": "head",
    "Neck": "neck",
    "Trunk": "trunk",
    "Left upper limb": "upper-limb",
    "Right upper limb": "upper-limb",
    "Left lower limb": "lower-limb",
    "Right lower limb": "lower-limb",
    "Left hand": "hand",
    "Right hand": "hand",
    "Left foot": "foot",
    "Right foot": "foot",
}


def region_for(collections: AbstractSet[str], preferred: Optional[str] = None) -> Optional[str]:
    """The body division an object belongs to, without its side.

    Structures genuinely span divisions - a muscle can run from neck to trunk - while §5
    carries a single regionId. Which one is right depends on the pack doing the asking, so
    a region-scoped pack claims its own; otherwise the choice is alphabetical and
    arbitrary but at least deterministic.
    """
    found = sorted(_BODY_DIVISIONS[name] for name in collections if name in _BODY_DIVISIONS)
    if preferred and preferred in found:
        return preferred
    return found[0] if found else None


def pack_region(spec: PackSpec) -> Optional[str]:
    """The body division a pack scopes itself to, if it names one."""
    for name in tuple(spec.require) + tuple(spec.include):
        if name in _BODY_DIVISIONS:
            return _BODY_DIVISIONS[name]
    return None


def is_layer(name: str) -> bool:
    """Whether a collection is one of the numbered visibility layers.

    A layer is a switch in the add-on's panel, not a thing a structure is part of, so it
    is never a group — even though Z-Anatomy's term table has a row for each of them.
    """
    return bool(_NUMBERED_SYSTEM.match(name))


def taxonomy_home(collections: AbstractSet[str]) -> Optional[str]:
    """The collection an object's own system is rooted at: its layer's name, unnumbered.

    `1: Skeletal system` is the layer; `Skeletal system` is the anatomical tree. The
    lowest-numbered layer is used, as in `system_for`, so the two cannot disagree. A layer
    whose tree goes by another name simply has no group of this name, and the callers fall
    back to the nearest group anywhere.
    """
    numbered = []
    for name in collections:
        match = _NUMBERED_SYSTEM.match(name)
        if match:
            numbered.append((int(match.group(1)), match.group(2)))
    return min(numbered)[1] if numbered else None


def nearest_group(
    collections: AbstractSet[str],
    depth: "dict[str, int]",
    groups: "dict[str, str]",
    own: Optional[str] = None,
    home: Optional[str] = None,
    closure: "Optional[dict[str, AbstractSet[str]]]" = None,
) -> Optional[str]:
    """The structure id of the innermost group collection containing this object.

    §2.3 expected containment to be expressed by object parenting; the source expresses it
    through collections instead, with `.g` objects standing in for them as structures.
    Depth is how many collections enclose a collection, so the deepest match is the
    nearest.

    Three things narrow "innermost", each learned from a pack that came out wrong:

    - [own] is the object's own structure id. An object and a collection of one name are
      one structure, and a structure is not its own parent.
    - [home] is the collection the object's system is rooted at, and [closure] maps a
      collection to itself and everything above it. A group inside the home tree is
      preferred to a deeper one outside it: an object is linked into other systems'
      collections too, and those are not what it is part of.
    - An object in no group at all hangs from its home, if the home is a group.
    """
    candidates = [name for name in collections if name in groups and groups[name] != own]
    if home and closure is not None:
        at_home = [name for name in candidates if home in closure.get(name, {name})]
        if at_home:
            candidates = at_home
    if not candidates:
        fallback = groups.get(home) if home else None
        return fallback if fallback != own else None
    return groups[max((depth.get(name, 0), name) for name in candidates)[1]]


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
