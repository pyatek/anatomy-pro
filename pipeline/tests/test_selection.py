"""Which objects belong to a pack, and which system they belong to.

Z-Anatomy links one object into several collections at once — a rib is simultaneously in
the flat `1: Skeletal system` visibility layer, in `Body of rib`, and in the anatomical
`Thorax` and `Trunk` groupings. Membership is therefore a set, not a path.
"""
from anatomypro_pipeline import selection

SPEC = selection.PackSpec(
    pack_id="skeletal-thorax",
    include=("Bones of thorax",),
    exclude=("Costal cartilages",),
)


def test_includes_an_object_belonging_to_an_included_collection():
    assert selection.is_included(
        {"1: Skeletal system", "Body of rib", "Bones of thorax", "Trunk"}, SPEC
    )


def test_excludes_an_object_in_no_included_collection():
    assert not selection.is_included({"1: Skeletal system", "Bones of cranium"}, SPEC)


def test_an_exclusion_beats_an_inclusion_on_the_same_object():
    assert not selection.is_included(
        {"Bones of thorax", "Costal cartilages"}, SPEC
    )


def test_reads_the_system_from_the_numbered_visibility_layer():
    assert selection.system_for({"Body of rib", "1: Skeletal system"}) == "skeletal-system"
    assert (
        selection.system_for({"7: Nervous system & Sense organs"})
        == "nervous-system-sense-organs"
    )


def test_has_no_system_when_the_object_is_in_no_numbered_layer():
    assert selection.system_for({"Bonus collection", "Stray"}) is None


def test_system_is_stable_when_an_object_sits_in_two_numbered_layers():
    # Picking the lowest-numbered layer keeps the answer deterministic rather than
    # dependent on Blender's collection ordering.
    assert selection.system_for({"4: Muscular system", "1: Skeletal system"}) == "skeletal-system"


def test_a_pack_can_require_a_region_and_a_system_together():
    spec = selection.PackSpec(
        pack_id="skeletal-trunk",
        include=(),
        require=("Trunk", "1: Skeletal system"),
    )
    assert selection.is_included({"Trunk", "1: Skeletal system", "Body of rib"}, spec)
    # A trunk muscle is in the region but not the system.
    assert not selection.is_included({"Trunk", "4: Muscular system"}, spec)
    # A skeletal object elsewhere in the body is in the system but not the region.
    assert not selection.is_included({"Bones of cranium", "1: Skeletal system"}, spec)


def test_reads_the_region_from_the_body_division():
    assert selection.region_for({"Trunk", "Body of rib", "1: Skeletal system"}) == "trunk"
    assert selection.region_for({"Head", "Cranium"}) == "head"


def test_a_region_does_not_carry_laterality():
    # RegionId and Laterality are separate fields in §5, and for §8.1's "same region"
    # distractor tier the two hands are one region, not two.
    assert selection.region_for({"Left hand", "Bones of hand"}) == "hand"
    assert selection.region_for({"Right upper limb"}) == "upper-limb"


def test_has_no_region_when_the_object_is_in_no_body_division():
    assert selection.region_for({"1: Skeletal system", "Bonus collection"}) is None


def test_a_structure_spanning_regions_prefers_the_pack_s_own_region():
    # A trunk pack must not label a rib "neck" merely because the object is also linked
    # into the neck division. Which region a shared structure belongs to depends on the
    # pack asking, so the pack's own division wins when the object is in it.
    spanning = {"Trunk", "Neck", "1: Skeletal system"}
    assert selection.region_for(spanning, preferred="trunk") == "trunk"
    assert selection.region_for(spanning, preferred="head") in {"neck", "trunk"}


def test_the_pack_region_is_read_from_its_own_specification():
    assert selection.pack_region(selection.PackSpec("p", include=(), require=("Trunk", "1: Skeletal system"))) == "trunk"
    assert selection.pack_region(selection.PackSpec("p", include=("Bones of thorax",))) is None


def test_the_nearest_enclosing_group_becomes_the_parent():
    # Z-Anatomy expresses containment through collections, not object parenting, and the
    # `.g` group objects stand in for those collections as structures. The nearest one
    # wins, measured by how deeply nested the collection is.
    groups = {"Bones of thorax": "1105-costae-median", "Skeletal system": "9-skeleton-median"}
    depth = {"Bones of thorax": 4, "Skeletal system": 2}
    assert selection.nearest_group({"Bones of thorax", "Skeletal system"}, depth, groups) == "1105-costae-median"


def test_an_object_in_no_known_group_has_no_parent():
    assert selection.nearest_group({"Stray"}, {}, {"Bones of thorax": "x"}) is None


def test_a_numbered_visibility_layer_is_not_taxonomy():
    # `1: Skeletal system` is a switch in the add-on's layer panel, not a thing a bone is
    # part of. Z-Anatomy's own term table has rows for the layers, so "does the name
    # resolve to a term" let them through as structures: the atlas grew a second, empty
    # "1: Systema skeletale" beside the real one.
    assert selection.is_layer("1: Skeletal system")
    assert selection.is_layer("7: Nervous system & Sense organs")
    assert not selection.is_layer("Skeletal system")
    assert not selection.is_layer("Vertebra C1")


def test_the_home_of_an_object_is_its_layer_s_name_without_the_number():
    assert selection.taxonomy_home({"1: Skeletal system", "Ribs", "Trunk"}) == "Skeletal system"
    # Lowest-numbered, as system_for chooses, so the two cannot disagree.
    assert selection.taxonomy_home({"4: Muscular system", "1: Skeletal system"}) == "Skeletal system"
    assert selection.taxonomy_home({"Bonus collection"}) is None


CLOSURE = {
    "Skeletal system": {"Skeletal system"},
    "Cartilages": {"Cartilages", "Skeletal system"},
    "Visceral systems": {"Visceral systems"},
    "Digestive system": {"Digestive system", "Visceral systems"},
    "Larynx": {"Larynx", "Digestive system", "Visceral systems"},
}
GROUPS = {
    "Skeletal system": "352-systema-skeletale-median",
    "Cartilages": "7217-cartilagines-median",
    "Visceral systems": "2772-systemata-visceralia-median",
    "Digestive system": "2773-systema-digestorium-median",
    "Larynx": "3000-larynx-median",
}
DEPTH = {name: len(above) for name, above in CLOSURE.items()}


def test_a_structure_is_parented_inside_its_own_system_even_when_another_nests_deeper():
    # The thyroid cartilage is linked under Cartilages and under Larynx. Larynx is the
    # deeper collection, so "the deepest group wins" hung a cartilage of the skeletal
    # layer under Digestive system and made Visceral systems a root of a skeletal pack.
    thyroid = {"1: Skeletal system", "Cartilages", "Skeletal system", "Larynx", "Digestive system", "Visceral systems"}
    assert (
        selection.nearest_group(thyroid, DEPTH, GROUPS, home="Skeletal system", closure=CLOSURE)
        == "7217-cartilagines-median"
    )


def test_a_structure_with_no_group_in_its_own_system_takes_the_nearest_one_anywhere():
    stray = {"1: Skeletal system", "Larynx", "Digestive system", "Visceral systems"}
    groups = {name: sid for name, sid in GROUPS.items() if name not in {"Skeletal system", "Cartilages"}}
    assert selection.nearest_group(stray, DEPTH, groups, home="Skeletal system", closure=CLOSURE) == "3000-larynx-median"


def test_a_structure_in_no_group_at_all_hangs_from_its_system():
    # The ethmoid cells are linked into the skeletal layer and nothing else. They are
    # still part of the skeleton.
    ethmoid_cells = {"1: Skeletal system", "Head"}
    assert (
        selection.nearest_group(ethmoid_cells, DEPTH, GROUPS, home="Skeletal system", closure=CLOSURE)
        == "352-systema-skeletale-median"
    )


def test_a_structure_is_never_its_own_parent():
    # The mandible is an object and a collection of the same name, so both resolve to one
    # structure id. Parenting the object to "its nearest group" parented it to itself,
    # which was then dropped: a parentless bone with the lower teeth hanging from it.
    closure = {"Mandible": {"Mandible", "Skeletal system"}, "Skeletal system": {"Skeletal system"}}
    groups = {"Mandible": "835-mandibula-median", "Skeletal system": "352-systema-skeletale-median"}
    depth = {"Mandible": 2, "Skeletal system": 1}
    assert (
        selection.nearest_group(
            {"1: Skeletal system", "Mandible", "Skeletal system"}, depth, groups,
            own="835-mandibula-median", home="Skeletal system", closure=closure,
        )
        == "352-systema-skeletale-median"
    )
