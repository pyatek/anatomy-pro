"""The naming rules decide StructureIds, and §5 says those are never reused.

A bug here is permanent in a way a rendering bug is not, which is why every rule is
tested off-Blender rather than only exercised by an export run.
"""
from anatomypro_pipeline import naming


def test_reads_laterality_from_the_z_anatomy_suffix():
    assert naming.parse_object("Scapula.l").laterality == "L"
    assert naming.parse_object("Scapula.r").laterality == "R"


def test_treats_an_unsuffixed_object_as_median():
    assert naming.parse_object("Sternum").laterality == "M"


def test_strips_blender_duplicate_numbering():
    assert naming.parse_object("Rib I.l.001").core == "Rib I"
    assert naming.parse_object("Rib I.l.001").laterality == "L"


def test_identifies_label_geometry():
    # The add-on declares these: label_elements = {"-txt", ".t", ".j"}
    assert naming.parse_object("Scapula.t").is_label
    assert naming.parse_object("Scapula.j").is_label
    assert naming.parse_object("Scapula.st").is_label
    assert naming.parse_object("Scapula-txt").is_label
    assert not naming.parse_object("Scapula.l").is_label


def test_keeps_a_non_laterality_suffix_as_a_discriminator():
    # One structure modelled as several objects; the discriminator keeps node names
    # unique without splitting the structure.
    surface = naming.parse_object("Acetabular fossa.s")
    assert surface.laterality == "M"
    assert surface.discriminator == "s"


def test_reads_laterality_out_of_a_compound_insertion_suffix():
    # Muscular insertions encode origin/end *and* side: .ol, .er, .e1l
    assert naming.parse_object("Pectoralis major.ol").laterality == "L"
    assert naming.parse_object("Pectoralis major.ol").discriminator == "o"
    assert naming.parse_object("Adductor pollicis.e1r").laterality == "R"
    assert naming.parse_object("Adductor pollicis.e1r").discriminator == "e1"


def test_strips_the_parentheses_z_anatomy_puts_round_some_terms():
    # These do resolve against TA2 once punctuation is folded, so the parentheses are an
    # annotation rather than a marker of a non-standard term.
    parsed = naming.parse_object("(Adductor minimus).l")
    assert parsed.core == "Adductor minimus"
    assert parsed.parenthesised


def test_makes_colliding_node_names_unique_without_splitting_the_structure():
    # Distinct meshes sharing a term, a side, and a discriminator. The ordinal lands in
    # the discriminator slot, which the Kotlin parser drops when forming a StructureId.
    assert naming.deduplicate(["5361__nodus__L", "5361__nodus__L", "9__alia__M"]) == [
        "5361__nodus__L",
        "5361__nodus__L__2",
        "9__alia__M",
    ]
    assert naming.deduplicate(["1__a__M__s", "1__a__M__s"]) == ["1__a__M__s", "1__a__M__s2"]


def test_slugifies_latin_terms_to_the_convention():
    assert naming.slugify("Aorta abdominalis") == "aorta_abdominalis"
    assert naming.slugify("Musculus biceps brachii") == "musculus_biceps_brachii"
    # StructureId only accepts [a-z0-9-]; anything else has to be folded away here.
    assert naming.slugify("Ligamentum cruciatum anterius (genus)") == "ligamentum_cruciatum_anterius_genus"


def test_builds_a_node_name_the_kotlin_parser_accepts():
    assert naming.node_name("4205", "aorta_abdominalis", "M", None) == "4205__aorta_abdominalis__M"
    assert naming.node_name("1837", "fossa_acetabuli", "M", "s") == "1837__fossa_acetabuli__M__s"
    assert naming.node_name("ZAN", "adductor_minimus", "L", None) == "ZAN__adductor_minimus__L"
