"""Turning a Z-Anatomy object name into a node name the Kotlin parser accepts.

Z-Anatomy names objects in English with a suffix that carries several different things
at once — laterality, label geometry, muscular attachment areas, and sub-parts of a
single structure. This module is the one place that vocabulary is interpreted.

The output shape is `<ta_code>__<latin_slug>__<L|R|M>[__<discriminator>]`, which is what
`StructureNode.parse` in `core-model` consumes.
"""
from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass
from typing import Optional

#: Declared by the Z-Anatomy add-on as `label_elements = {"-txt", ".t", ".j"}`.
#: These are label text and leader lines, not anatomy, and must never become nodes.
LABEL_SUFFIXES = frozenset({"t", "j", "st"})

_DUPLICATE_NUMBERING = re.compile(r"\.\d{3}$")
_SUFFIX = re.compile(r"\.([a-z][a-z0-9]{0,3})$")
#: A suffix that ends in l/r carries laterality plus a marker, e.g. `.ol`, `.e1r`.
_LATERAL_SUFFIX = re.compile(r"^([a-z][a-z0-9]*?)([lr])$")


@dataclass(frozen=True)
class ParsedObject:
    """A Z-Anatomy object name, decomposed."""

    raw: str
    core: str
    laterality: str
    discriminator: Optional[str]
    is_label: bool
    parenthesised: bool


def parse_object(name: str) -> ParsedObject:
    base = _DUPLICATE_NUMBERING.sub("", name.strip())

    if base.endswith("-txt"):
        return ParsedObject(name, base[: -len("-txt")].strip(), "M", None, True, False)

    match = _SUFFIX.search(base)
    suffix = match.group(1) if match else None
    core = base[: match.start()] if match else base

    if suffix in LABEL_SUFFIXES:
        return ParsedObject(name, core.strip(), "M", None, True, False)

    laterality, discriminator = _interpret(suffix)

    parenthesised = core.startswith("(") and core.endswith(")")
    if parenthesised:
        core = core[1:-1]

    return ParsedObject(name, core.strip(), laterality, discriminator, False, parenthesised)


def _interpret(suffix: Optional[str]) -> tuple[str, Optional[str]]:
    """Splits a suffix into laterality and whatever else it was carrying."""
    if suffix is None:
        return "M", None
    if suffix == "l":
        return "L", None
    if suffix == "r":
        return "R", None

    lateral = _LATERAL_SUFFIX.match(suffix)
    if lateral:
        return lateral.group(2).upper(), lateral.group(1)

    return "M", suffix


def slugify(text: str) -> str:
    """Folds a term to `[a-z0-9_]`, which is all `StructureId` will accept."""
    ascii_only = (
        unicodedata.normalize("NFKD", text)
        .encode("ascii", "ignore")
        .decode("ascii")
    )
    return re.sub(r"_+", "_", re.sub(r"[^a-z0-9]+", "_", ascii_only.lower())).strip("_")


_LATERALITY_WORD = {"L": "left", "R": "right", "M": "median"}


def structure_id(ta_code: str, latin_slug: str, laterality: str) -> str:
    """Mirrors `StructureNode.parse` in core-model. The two must never diverge.

    The discriminator is deliberately absent: several nodes of one structure share an
    identifier, which is what makes `MeshRef` a list per structure (spec §5).
    """
    return "-".join(
        (
            ta_code.lower().replace("_", "-"),
            latin_slug.replace("_", "-"),
            _LATERALITY_WORD[laterality],
        )
    )


def node_name(
    ta_code: str,
    latin_slug: str,
    laterality: str,
    discriminator: Optional[str],
) -> str:
    parts = [ta_code, latin_slug, laterality]
    if discriminator:
        parts.append(discriminator)
    return "__".join(parts)


def deduplicate(names: "list[str]") -> "list[str]":
    """Makes node names unique, since glTF requires it and a handful of objects collide.

    Four of ~4,900 matched objects produce an identical name: distinct meshes that share a
    Terminologia term, a side, and a discriminator. They are still one structure, so the
    ordinal goes in the discriminator slot, where `StructureNode.parse` ignores it.
    """
    seen: "dict[str, int]" = {}
    result = []
    for name in names:
        count = seen.get(name, 0) + 1
        seen[name] = count
        result.append(name if count == 1 else f"{name}__{count}" if name.count("__") == 2 else f"{name}{count}")
    return result
