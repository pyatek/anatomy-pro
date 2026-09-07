"""The Python and Kotlin sides must agree exactly on identifiers.

These are the same fixtures as StructureNodeTest in core-model. If the two ever disagree,
the pipeline writes node names the app resolves to a different structure — or to none.
"""
from anatomypro_pipeline import naming


def test_matches_the_kotlin_parser_on_lateral_structures():
    assert naming.structure_id("A02_4_01_001", "scapula", "L") == "a02-4-01-001-scapula-left"
    assert naming.structure_id("A02_4_01_001", "scapula", "R") == "a02-4-01-001-scapula-right"


def test_matches_the_kotlin_parser_on_median_structures():
    assert (
        naming.structure_id("A02_2_00_000", "columna_vertebralis", "M")
        == "a02-2-00-000-columna-vertebralis-median"
    )


def test_matches_the_kotlin_parser_on_unmatched_structures():
    assert naming.structure_id("ZAN", "adductor_minimus", "L") == "zan-adductor-minimus-left"


def test_a_discriminator_does_not_change_the_identifier():
    assert naming.structure_id("1837", "fossa_acetabuli", "M") == "1837-fossa-acetabuli-median"
