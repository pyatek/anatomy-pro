#!/bin/sh
# Copies generated content packs into the app bundle, where the harness looks for them.
#
# Run by the "Compile Kotlin Framework" build phase. Packs are bundled rather than pushed to
# the device so the §6.1 measurement can be taken from a plain install, and several are
# bundled at once so one install can interleave them (§25.4). `pipeline/build` is not
# committed, so a pack that was never generated is skipped and the app falls back to the toy.
set -eu

PACKS="${ANATOMYPRO_PACKS:-skeletal-trunk muscular-trunk skeletal-body}"
SOURCE="$SRCROOT/../pipeline/build/packs"
DESTINATION="$TARGET_BUILD_DIR/$UNLOCALIZED_RESOURCES_FOLDER_PATH/packs"

rm -rf "$DESTINATION"
for pack in $PACKS; do
  if [ ! -f "$SOURCE/$pack/mesh.glb" ]; then
    echo "warning: pack '$pack' has not been generated; skipping"
    continue
  fi
  mkdir -p "$DESTINATION/$pack"
  cp "$SOURCE/$pack/mesh.glb" "$DESTINATION/$pack/mesh.glb"
  if [ -f "$SOURCE/$pack/manifest.json" ]; then
    cp "$SOURCE/$pack/manifest.json" "$DESTINATION/$pack/manifest.json"
  fi
  echo "Bundled pack '$pack'"
done
