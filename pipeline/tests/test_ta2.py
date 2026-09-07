"""Joining Z-Anatomy's English object names to Terminologia Anatomica."""
from anatomypro_pipeline import ta2

TABLE = ta2.build_table([
    ("4205", "Abdominal aorta", "Aorta abdominalis"),
    ("361", "Pectoral girdle", "Cingulum pectorale"),
    ("1837", "Acetabular fossa", "Fossa acetabuli"),
])


def test_matches_on_the_english_term():
    assert TABLE.lookup("Abdominal aorta").ta2_id == "4205"
    assert TABLE.lookup("Abdominal aorta").latin == "Aorta abdominalis"


def test_matching_ignores_case_and_surrounding_whitespace():
    assert TABLE.lookup("  abdominal AORTA ").ta2_id == "4205"


def test_matching_tolerates_hyphen_and_spacing_differences():
    assert TABLE.lookup("Abdominal-aorta").ta2_id == "4205"


def test_reports_a_miss_rather_than_guessing():
    # A wrong match is worse than no match: it would attach a permanent identifier to
    # the wrong structure. Unmatched objects get a ZAN identifier instead.
    assert TABLE.lookup("Adductor minimus") is None


def test_accepts_enumerated_terminologia_ids():
    """TA2 numbers enumerated structures as `1113*8`, not as a plain integer.

    Ribs, vertebrae and teeth are all numbered this way, so rejecting the form drops
    exactly the structures a thoracic pack is made of.
    """
    table = ta2.build_rows([
        ("1113*8", "Eighth rib", "Octava costa"),
        ("1107", "First rib", "Costa prima"),
    ])
    assert table.lookup("Eighth rib").ta2_id == "1113*8"
    assert table.lookup("First rib").ta2_id == "1107"


def test_folds_an_enumerated_id_into_the_node_name_alphabet():
    # `*` is not in the [A-Za-z0-9_] the Kotlin parser accepts for the code position.
    assert ta2.code_for("1113*8") == "1113_8"
    assert ta2.code_for("1107") == "1107"
