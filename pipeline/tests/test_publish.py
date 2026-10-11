"""Publishing a pack to the bucket the app downloads from (backend spec §9)."""
import hashlib
import json

import pytest

from anatomypro_pipeline import publish

BASE = "https://packs.example.org"
LABELS = {"la": "Systema skeletale", "en": "Skeletal system"}


def make_pack(tmp_path, pack_id="skeletal-body", mesh=b"glb-bytes", structures=2):
    pack = tmp_path / "build" / pack_id
    pack.mkdir(parents=True)
    (pack / "mesh.glb").write_bytes(mesh)
    (pack / "manifest.json").write_text(json.dumps({
        "pack_id": pack_id,
        "structures": [{"structure_id": f"s-{n}"} for n in range(structures)],
    }))
    return pack


def bucket(tmp_path):
    return publish.DirectoryStore(tmp_path / "bucket")


def index_of(store):
    return json.loads(store.get("index.json"))


def test_a_published_pack_is_in_the_bucket_and_in_the_index(tmp_path):
    store = bucket(tmp_path)
    publish.publish(store, make_pack(tmp_path), version=1, labels=LABELS, base_url=BASE)

    assert store.get("packs/skeletal-body/1/pack.glb") == b"glb-bytes"
    entry, = index_of(store)["packs"]
    assert entry["id"] == "skeletal-body"
    assert entry["version"] == 1
    assert entry["labels"] == LABELS
    assert entry["byteSize"] == len(b"glb-bytes")
    assert entry["sha256"] == hashlib.sha256(b"glb-bytes").hexdigest()
    assert entry["meshUrl"] == f"{BASE}/packs/skeletal-body/1/pack.glb"
    assert entry["manifestUrl"] == f"{BASE}/packs/skeletal-body/1/manifest.json"
    assert entry["structureCount"] == 2


def test_a_version_already_published_is_never_overwritten(tmp_path):
    # A client may hold version 1 half downloaded; its bytes must not change under it.
    store = bucket(tmp_path)
    publish.publish(store, make_pack(tmp_path), version=1, labels=LABELS, base_url=BASE)

    changed = make_pack(tmp_path / "again", mesh=b"different")
    with pytest.raises(publish.AlreadyPublished):
        publish.publish(store, changed, version=1, labels=LABELS, base_url=BASE)

    assert store.get("packs/skeletal-body/1/pack.glb") == b"glb-bytes"


def test_a_newer_version_replaces_the_packs_entry_and_leaves_other_packs_alone(tmp_path):
    store = bucket(tmp_path)
    publish.publish(store, make_pack(tmp_path), version=1, labels=LABELS, base_url=BASE)
    publish.publish(store, make_pack(tmp_path / "m", pack_id="muscular-body"), version=1, labels=LABELS, base_url=BASE)
    publish.publish(store, make_pack(tmp_path / "v2", mesh=b"newer"), version=2, labels=LABELS, base_url=BASE)

    packs = {entry["id"]: entry["version"] for entry in index_of(store)["packs"]}
    assert packs == {"skeletal-body": 2, "muscular-body": 1}
    # The old version's files stay: a client may still be fetching them.
    assert store.get("packs/skeletal-body/1/pack.glb") == b"glb-bytes"


def test_an_older_version_cannot_be_published_over_a_newer_one(tmp_path):
    store = bucket(tmp_path)
    publish.publish(store, make_pack(tmp_path), version=2, labels=LABELS, base_url=BASE)

    with pytest.raises(publish.NotNewer):
        publish.publish(store, make_pack(tmp_path / "old"), version=1, labels=LABELS, base_url=BASE)


class FailingStore(publish.DirectoryStore):
    """Fails the upload of one key, as a dropped connection would."""

    def __init__(self, root, fail_on):
        super().__init__(root)
        self.fail_on = fail_on

    def put(self, key, data):
        if key.endswith(self.fail_on):
            raise IOError("connection dropped")
        super().put(key, data)


def test_the_index_never_points_at_a_file_that_did_not_arrive(tmp_path):
    # The index is written last. An upload that fails half way must leave it as it was.
    store = FailingStore(tmp_path / "bucket", fail_on="manifest.json")

    with pytest.raises(IOError):
        publish.publish(store, make_pack(tmp_path), version=1, labels=LABELS, base_url=BASE)

    assert not store.exists("index.json")


class CorruptingStore(publish.DirectoryStore):
    """Stores something other than what it was given."""

    def put(self, key, data):
        super().put(key, data[:-1] if key.endswith("pack.glb") else data)


def test_what_was_uploaded_is_read_back_and_compared_before_the_index_names_it(tmp_path):
    store = CorruptingStore(tmp_path / "bucket")

    with pytest.raises(publish.UploadMismatch):
        publish.publish(store, make_pack(tmp_path), version=1, labels=LABELS, base_url=BASE)

    assert not store.exists("index.json")


def test_the_attribution_the_licence_asks_for_sits_beside_the_packs(tmp_path):
    store = bucket(tmp_path)
    publish.publish(store, make_pack(tmp_path), version=1, labels=LABELS, base_url=BASE)

    attribution = store.get("ATTRIBUTION.md").decode()
    assert "Z-Anatomy" in attribution
    assert "CC BY-SA 4.0" in attribution


def test_a_pack_with_no_mesh_or_no_manifest_is_refused_before_anything_is_uploaded(tmp_path):
    store = bucket(tmp_path)
    pack = make_pack(tmp_path)
    (pack / "mesh.glb").unlink()

    with pytest.raises(FileNotFoundError):
        publish.publish(store, pack, version=1, labels=LABELS, base_url=BASE)

    assert not store.exists("packs/skeletal-body/1/manifest.json")
