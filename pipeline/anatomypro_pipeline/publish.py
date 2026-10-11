"""Publishing a pack to the bucket the app downloads from (backend spec §9).

    <bucket>/index.json                          every pack on offer
    <bucket>/ATTRIBUTION.md
    <bucket>/packs/<pack-id>/<version>/pack.glb
    <bucket>/packs/<pack-id>/<version>/manifest.json

A version's files are never rewritten: a client may hold one half downloaded. The index is
the only file that changes and the last thing a publish writes, so it never names a file
that is not there.
"""
import argparse
import hashlib
import json
from pathlib import Path

INDEX_KEY = "index.json"
ATTRIBUTION_KEY = "ATTRIBUTION.md"

ATTRIBUTION = """# Attribution

The anatomical models and structure names in these packs are derived from
[Z-Anatomy](https://www.z-anatomy.com/), licensed under CC BY-SA 4.0
(https://creativecommons.org/licenses/by-sa/4.0/). They have been selected, renamed,
simplified and repackaged for Anatomy Pro.

Structure definitions are extracts from Wikipedia, licensed under CC BY-SA 3.0
(https://creativecommons.org/licenses/by-sa/3.0/).

These packs are distributed under CC BY-SA 4.0.
"""


class AlreadyPublished(Exception):
    """This version of the pack is in the bucket already, and is never overwritten."""


class NotNewer(Exception):
    """The index already offers this version of the pack or a later one."""


class UploadMismatch(Exception):
    """What the bucket holds is not what was sent."""


class DirectoryStore:
    """A bucket that is a directory: what the tests publish to, and a dry run."""

    def __init__(self, root):
        self.root = Path(root)

    def put(self, key, data):
        path = self.root / key
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)

    def get(self, key):
        return (self.root / key).read_bytes()

    def exists(self, key):
        return (self.root / key).is_file()


class S3Store:
    """Any S3-compatible bucket (R2, B2, S3). Credentials come from the environment."""

    def __init__(self, bucket, endpoint_url=None):
        import boto3  # only needed for a real publish

        self.bucket = bucket
        self.client = boto3.client("s3", endpoint_url=endpoint_url)

    def put(self, key, data):
        self.client.put_object(Bucket=self.bucket, Key=key, Body=data, ContentType=_content_type(key))

    def get(self, key):
        return self.client.get_object(Bucket=self.bucket, Key=key)["Body"].read()

    def exists(self, key):
        from botocore.exceptions import ClientError

        try:
            self.client.head_object(Bucket=self.bucket, Key=key)
            return True
        except ClientError as error:
            if error.response["Error"]["Code"] in ("404", "NoSuchKey", "NotFound"):
                return False
            raise


def _content_type(key):
    if key.endswith(".json"):
        return "application/json"
    if key.endswith(".glb"):
        return "model/gltf-binary"
    if key.endswith(".md"):
        return "text/markdown; charset=utf-8"
    return "application/octet-stream"


def _upload(store, key, data):
    store.put(key, data)
    if store.get(key) != data:
        raise UploadMismatch(key)


def publish(store, pack_dir, version, labels, base_url):
    """Uploads one version of the pack in [pack_dir] and returns its index entry."""
    pack_dir = Path(pack_dir)
    # Both are read before anything is sent: a pack missing either is not a pack.
    mesh = (pack_dir / "mesh.glb").read_bytes()
    manifest = (pack_dir / "manifest.json").read_bytes()
    document = json.loads(manifest)
    pack_id = document["pack_id"]

    prefix = f"packs/{pack_id}/{version}"
    mesh_key, manifest_key = f"{prefix}/pack.glb", f"{prefix}/manifest.json"
    index = json.loads(store.get(INDEX_KEY)) if store.exists(INDEX_KEY) else {"packs": []}
    current = next((entry for entry in index["packs"] if entry["id"] == pack_id), None)

    if store.exists(mesh_key) or store.exists(manifest_key):
        raise AlreadyPublished(f"{pack_id} version {version}")
    if current is not None and current["version"] >= version:
        raise NotNewer(f"{pack_id} is at version {current['version']}; {version} is not newer")

    _upload(store, mesh_key, mesh)
    _upload(store, manifest_key, manifest)
    _upload(store, ATTRIBUTION_KEY, ATTRIBUTION.encode())

    base = base_url.rstrip("/")
    entry = {
        "id": pack_id,
        "version": version,
        "labels": labels,
        "byteSize": len(mesh),
        "sha256": hashlib.sha256(mesh).hexdigest(),
        "manifestSha256": hashlib.sha256(manifest).hexdigest(),
        "meshUrl": f"{base}/{mesh_key}",
        "manifestUrl": f"{base}/{manifest_key}",
        "structureCount": len(document["structures"]),
    }
    index["packs"] = sorted(
        [other for other in index["packs"] if other["id"] != pack_id] + [entry],
        key=lambda other: other["id"],
    )
    _upload(store, INDEX_KEY, json.dumps(index, ensure_ascii=False, indent=2).encode())
    return entry


def main(argv=None):
    parser = argparse.ArgumentParser(description="Publish a built pack to the bucket the app downloads from.")
    parser.add_argument("pack_dir", help="e.g. build/packs/skeletal-body")
    parser.add_argument("--version", type=int, required=True)
    parser.add_argument("--label", action="append", required=True, metavar="LOCALE=NAME", help="repeatable")
    parser.add_argument("--base-url", required=True, help="the public URL the bucket is served at")
    target = parser.add_mutually_exclusive_group(required=True)
    target.add_argument("--bucket")
    target.add_argument("--directory", help="publish to a local directory instead of a bucket")
    parser.add_argument("--endpoint-url", help="for S3-compatible storage that is not AWS")
    args = parser.parse_args(argv)

    labels = dict(label.split("=", 1) for label in args.label)
    store = DirectoryStore(args.directory) if args.directory else S3Store(args.bucket, args.endpoint_url)
    entry = publish(store, args.pack_dir, args.version, labels, args.base_url)
    print(json.dumps(entry, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
