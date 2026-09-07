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
