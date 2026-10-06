"""Turning the source's definitions into something an app can show.

Z-Anatomy keeps a definition per term as a text datablock, and most of them are whole
Wikipedia articles: a shouted title, the article with its `== Section ==` headings, and the
article's address on the last line. The femur's is 2,001 words and reaches arthropod legs.

The app shows a short extract beside a structure and has to say where it came from, so the
text is cut to its lead and the address is lifted out into a field of its own (design spec
§29.3, §30).

    python3 -m anatomypro_pipeline.definitions build/packs/*/manifest.json

rewrites manifests generated before this existed, without needing Blender or the atlas.
"""
from __future__ import annotations

import json
import re
import sys
from typing import Any, Dict, NamedTuple, Optional

#: Enough for a lead of a few sentences; the source breaks nearly every sentence into its
#: own paragraph, so a paragraph count would say little.
MAX_WORDS = 120

WIKIPEDIA_LICENCE = "CC BY-SA 3.0"
#: Text with no address is Z-Anatomy's own writing and carries the atlas's licence.
ATLAS_LICENCE = "CC BY-SA 4.0"

_SECTION = re.compile(r"^\s*==")
_PARAGRAPH_BREAK = re.compile(r"\n\s*\n")


class Summary(NamedTuple):
    text: str
    source: Optional[str]
    licence: Optional[str]


def summarise(raw: Optional[str]) -> Optional[Summary]:
    """The lead of a definition and where it came from, or None when there is no text."""
    lines = (raw or "").strip().splitlines()
    if not lines:
        return None

    source = None
    if lines[-1].strip().startswith("http"):
        source = lines.pop().strip()

    # The title repeats the term, in capitals.
    if lines and any(c.isalpha() for c in lines[0]) and lines[0] == lines[0].upper():
        lines.pop(0)

    lead = []
    for line in lines:
        if _SECTION.match(line):
            break
        lead.append(line)

    paragraphs = [
        "\n".join(line.strip() for line in block.strip().splitlines())
        for block in _PARAGRAPH_BREAK.split("\n".join(lead))
        if block.strip()
    ]
    if not paragraphs:
        return None

    kept = [paragraphs[0]]
    words = len(paragraphs[0].split())
    for paragraph in paragraphs[1:]:
        words += len(paragraph.split())
        if words > MAX_WORDS:
            break
        kept.append(paragraph)

    if source is None:
        licence = ATLAS_LICENCE
    elif "wikipedia.org" in source:
        licence = WIKIPEDIA_LICENCE
    else:
        licence = None
    return Summary("\n\n".join(kept), source, licence)


def fields(raw: Optional[str]) -> Dict[str, Optional[str]]:
    """The three manifest keys a definition occupies."""
    summary = summarise(raw)
    return {
        "definition": summary.text if summary else None,
        "definition_source": summary.source if summary else None,
        "definition_licence": summary.licence if summary else None,
    }


def rewrite(document: Dict[str, Any]) -> int:
    """Summarises a manifest's definitions in place; returns how many were changed.

    A structure that already records a source key has been through this, and is left
    alone: its text no longer ends in the address that would be read as its source.
    """
    changed = 0
    for structure in document["structures"]:
        if "definition_source" in structure:
            continue
        structure.update(fields(structure.get("definition")))
        changed += 1
    return changed


def main(paths) -> None:
    for path in paths:
        with open(path, encoding="utf-8") as handle:
            document = json.load(handle)
        changed = rewrite(document)
        with open(path, "w", encoding="utf-8") as handle:
            json.dump(document, handle, indent=2, ensure_ascii=False)
        words = sum(len((s.get("definition") or "").split()) for s in document["structures"])
        print(f"{path}: {changed} structures rewritten, {words} definition words")


if __name__ == "__main__":
    main(sys.argv[1:])
