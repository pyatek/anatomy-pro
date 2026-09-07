"""The Terminologia Anatomica table Z-Anatomy ships, and the join onto it.

Z-Anatomy names objects in English and carries no anatomical code. TA2.csv supplies both
the code and the Latin term, so the join is what lets pack identifiers be anchored to a
standard rather than to one vendor's English naming (model sourcing spec §2.2).

The join is deliberately conservative. A wrong match would attach a permanent identifier
to the wrong structure; an unmatched object merely gets a `ZAN` identifier that a later
run can upgrade.
"""
from __future__ import annotations

import csv
import re
from dataclasses import dataclass
from typing import Dict, Iterable, List, Optional, Tuple

_PUNCTUATION = re.compile(r"[^a-z0-9]+")
#: TA2 numbers enumerated structures — ribs, vertebrae, teeth — as `1113*8`.
_TA2_ID = re.compile(r"^\d+(?:\*\d+)?$")


@dataclass(frozen=True)
class Ta2Entry:
    ta2_id: str
    english: str
    latin: str


class Ta2Table:
    def __init__(self, entries: Iterable[Ta2Entry]) -> None:
        self._by_term: Dict[str, Ta2Entry] = {}
        self.entries: List[Ta2Entry] = list(entries)
        for entry in self.entries:
            # First occurrence wins: the table is ordered, and later duplicates are
            # regional or sub-entries of the same term.
            self._by_term.setdefault(normalise(entry.english), entry)

    def lookup(self, term: str) -> Optional[Ta2Entry]:
        return self._by_term.get(normalise(term))

    def __len__(self) -> int:
        return len(self.entries)


def normalise(term: str) -> str:
    """Folds spacing, case, and punctuation so `Abdominal-aorta` matches `Abdominal aorta`."""
    return _PUNCTUATION.sub(" ", term.strip().lower()).strip()


def code_for(ta2_id: str) -> str:
    """Folds a TA2 id into the alphabet the node-name convention allows."""
    return ta2_id.replace("*", "_")


def build_table(rows: Iterable[Tuple[str, str, str]]) -> Ta2Table:
    return Ta2Table(Ta2Entry(str(i), e, l) for i, e, l in rows)


def build_rows(rows: Iterable[Tuple[str, str, str]]) -> Ta2Table:
    return Ta2Table(Ta2Entry(i, e, l) for i, e, l in rows)


def load_table(path: str) -> Ta2Table:
    """Reads Z-Anatomy's TA2.csv, whose rows are semicolon-separated inside quotes."""
    entries: List[Ta2Entry] = []
    with open(path, encoding="utf-8-sig", newline="") as handle:
        for row in csv.reader(handle):
            if not row:
                continue
            fields = row[0].split(";")
            if len(fields) < 3 or not _TA2_ID.match(fields[0].strip()):
                continue
            entries.append(
                Ta2Entry(fields[0].strip(), fields[1].strip(), fields[2].strip())
            )
    return Ta2Table(entries)
