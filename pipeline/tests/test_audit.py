"""Every object a pack selects is either exported or named as skipped, with a reason.

The first pipeline looked only at meshes. Vessels and most peripheral nerves are curves, so
951 structures — two whole systems — were left out of every pack without a word. Nothing
may be dropped silently again.
"""
import pytest

from anatomypro_pipeline import audit


def test_meshes_and_curves_are_exported():
    assert audit.reason_to_skip("MESH", "Femur.l") is None
    assert audit.reason_to_skip("CURVE", "Abdominal aorta") is None
    assert audit.reason_to_skip("CURVE", "(Ulnar recurrent artery).r") is None


def test_label_geometry_is_skipped_whatever_its_type():
    assert audit.reason_to_skip("MESH", "Femur.t") == "label"
    assert audit.reason_to_skip("FONT", "Femur-txt") == "label"
    assert audit.reason_to_skip("CURVE", "Femur.j") == "label"


def test_text_objects_are_skipped_as_text():
    # Z-Anatomy's group and surface-landmark captions: `.g` and `.s` text objects.
    assert audit.reason_to_skip("FONT", "Abdominal lymph nodes.g") == "text"
    assert audit.reason_to_skip("FONT", "Acetabular fossa.s") == "text"


def test_other_object_types_are_skipped_and_say_which():
    assert audit.reason_to_skip("LIGHT", "Sun") == "type:light"
    assert audit.reason_to_skip("EMPTY", "Anchor") == "type:empty"


@pytest.mark.parametrize(
    "name",
    ["BezierCircle'", "BezierCurve.001", "NurbsPath", "Medulla-path", "Oesophagus-profile", "Zonular fibers-curve.r"],
)
def test_modelling_helpers_are_skipped(name):
    # What a tube was swept along or lathed from. Geometry, but not a structure.
    assert audit.reason_to_skip("CURVE", name) == "helper"


@pytest.mark.parametrize("name", ["????????", "?x.l", "?x.r", "..."])
def test_an_object_nobody_named_is_skipped(name):
    # An id is made from the name. There is none to make one from.
    assert audit.reason_to_skip("CURVE", name) == "unnamed"


def test_a_real_vessel_the_term_table_lacks_is_still_exported():
    # 28 of the curves are real and simply missing from TA2.csv. They get a zan- id, as
    # unmatched meshes always have.
    assert audit.reason_to_skip("CURVE", "Middle genicular artery.l") is None
    assert audit.reason_to_skip("CURVE", "Inferior vein of left ventricle (//Posterior '')") is None


def test_the_check_passes_when_everything_is_accounted_for():
    audit.check(
        considered=["a", "b", "c"],
        exported=["a"],
        skipped={"label": ["b"], "empty": ["c"]},
    )


def test_the_check_names_what_was_dropped_without_a_reason():
    with pytest.raises(audit.Unaccounted) as failure:
        audit.check(considered=["a", "b", "c"], exported=["a"], skipped={"label": ["b"]})
    assert "c" in str(failure.value)


def test_the_check_refuses_an_object_counted_twice():
    with pytest.raises(audit.Unaccounted):
        audit.check(considered=["a"], exported=["a"], skipped={"label": ["a"]})


def test_the_summary_counts_each_reason_and_keeps_the_names():
    summary = audit.summary({"label": ["b", "a"], "helper": ["h"], "empty": []})
    assert summary["counts"] == {"helper": 1, "label": 2}
    assert summary["names"]["label"] == ["a", "b"]
    assert "empty" not in summary["names"]


def test_a_curve_takes_the_finest_resolution_that_fits_the_target():
    # Triangles by (bevel resolution, resolution along the curve). Cutting detail at the
    # curve keeps a tube round and regular; collapsing its mesh afterwards does not.
    cost = {(2, 6): 5760, (1, 4): 2880, (1, 2): 1100, (0, 2): 700}
    assert audit.fitting_resolution(cost.__getitem__, target=1200) == ((1, 2), 1100)
    assert audit.fitting_resolution(cost.__getitem__, target=6000) == ((2, 6), 5760)


def test_a_curve_no_resolution_fits_takes_the_coarsest():
    # The mesh decimation that follows deals with what is left.
    cost = {(2, 6): 90000, (1, 4): 40000, (1, 2): 20000, (0, 2): 11520}
    assert audit.fitting_resolution(cost.__getitem__, target=1200) == ((0, 2), 11520)
