from anatomypro_pipeline import definitions

FEMUR = """FEMUR


The femur (, pl. femurs or femora ), or thigh bone, is the proximal bone of the hindlimb in tetrapod vertebrates.

The head of the femur articulates with the acetabulum in the pelvic bone forming the hip joint.

By most measures the two femurs are the strongest bones of the body.


== Structure ==

The femur is the only bone in the upper leg.


https://en.wikipedia.org/wiki/Femur"""


def test_drops_the_shouted_title_the_source_puts_first():
    assert not definitions.summarise(FEMUR).text.startswith("FEMUR")
    assert definitions.summarise(FEMUR).text.startswith("The femur")


def test_stops_at_the_first_section_heading():
    # The source carries whole encyclopedia articles; the femur's runs to arthropod legs.
    summary = definitions.summarise(FEMUR)
    assert "strongest bones" in summary.text
    assert "==" not in summary.text
    assert "only bone in the upper leg" not in summary.text


def test_lifts_the_source_link_out_of_the_text():
    summary = definitions.summarise(FEMUR)
    assert summary.source == "https://en.wikipedia.org/wiki/Femur"
    assert "wikipedia.org" not in summary.text


def test_a_wikipedia_extract_carries_wikipedias_licence():
    assert definitions.summarise(FEMUR).licence == "CC BY-SA 3.0"


def test_text_without_a_link_is_the_atlas_own_and_carries_its_licence():
    summary = definitions.summarise("SACRAL REGION\n\n\nCovering the sacrum.")
    assert summary.text == "Covering the sacrum."
    assert summary.source is None
    assert summary.licence == "CC BY-SA 4.0"


def test_a_long_lead_is_cut_at_a_paragraph_not_mid_sentence():
    paragraphs = [f"Sentence {n} " + "word " * 40 + "end." for n in range(6)]
    summary = definitions.summarise("TITLE\n\n\n" + "\n\n".join(paragraphs))
    assert summary.text.endswith("end.")
    assert len(summary.text.split()) <= definitions.MAX_WORDS
    assert summary.text.startswith("Sentence 0")


def test_a_first_paragraph_longer_than_the_limit_is_kept_whole():
    # Better one long paragraph than none, or half of one.
    long_paragraph = "word " * 300 + "end."
    assert definitions.summarise("TITLE\n\n\n" + long_paragraph).text == long_paragraph


def test_a_list_keeps_its_lines():
    summary = definitions.summarise("EXTRACRANIAL BONES OF HEAD\n\n\n-Mandible\n-Hyoid bone")
    assert summary.text == "-Mandible\n-Hyoid bone"


def test_a_title_with_nothing_under_it_is_no_definition():
    assert definitions.summarise("MEDIAL EXTERNAL ILIAC NODES") is None


def test_nothing_in_is_nothing_out():
    assert definitions.summarise(None) is None
    assert definitions.summarise("   ") is None


def test_rewriting_an_old_manifest_summarises_it_once():
    document = {"structures": [{"structure_id": "femur", "definition": FEMUR}]}

    assert definitions.rewrite(document) == 1
    first = dict(document["structures"][0])
    assert first["definition_source"] == "https://en.wikipedia.org/wiki/Femur"

    # A second pass must not read the already-lifted text as having no source.
    assert definitions.rewrite(document) == 0
    assert document["structures"][0] == first


def test_a_title_spread_over_two_lines_is_dropped_whole():
    # Twenty-four of the source's titles give an alternative term on a second line.
    summary = definitions.summarise("SKELETAL SYSTEM/\nSKELETON\n\nHuman:\n\nThe skeleton consists of bones.")
    assert summary.text == "Human:\n\nThe skeleton consists of bones."
