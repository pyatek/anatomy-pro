#!/bin/sh
# Fetches the Z-Anatomy source the pipeline reads, pinned and checked.
#
# The source is not committed (300 MB, CC BY-SA 4.0, someone else's repository), and for a
# month nothing recorded where it had come from. Structure ids are permanent, so the pin
# matters: these two files were byte-identical upstream from 2026-09-06 to 2026-10-06, and
# regenerating skeletal-trunk from them with the code of that time reproduced the existing
# manifest row for row (design spec §37).
set -eu

COMMIT=c7010a903b75a2fd24a13b1c2c4c3546a9223780
BASE="https://raw.githubusercontent.com/Z-Anatomy/Models-of-human-anatomy/$COMMIT"
HERE="$(cd "$(dirname "$0")" && pwd)/source/z-anatomy"
mkdir -p "$HERE"

fetch() {
  name="$1"; want="$2"
  if [ ! -f "$HERE/$name" ] || [ "$(shasum -a 256 "$HERE/$name" | cut -d' ' -f1)" != "$want" ]; then
    echo "Downloading $name"
    curl -fsSL -o "$HERE/$name" "$BASE/$name"
  fi
  got="$(shasum -a 256 "$HERE/$name" | cut -d' ' -f1)"
  if [ "$got" != "$want" ]; then
    echo "checksum mismatch for $name: expected $want, got $got" >&2
    exit 1
  fi
}

fetch TA2.csv 0f9092a328b27dcd15d696d9f9a4087deb229a1aad21b75876657622de835974
fetch Z-Anatomy.zip e029688545627bd0214b269e1063143abb580aad72b2c2445d6d8a9a0d9da736

# Only the Blender file is unpacked; the template's Python is not needed and is not run.
unzip -o -q -j "$HERE/Z-Anatomy.zip" "Z-Anatomy/Startup.blend" -d "$HERE"
echo "Source ready in $HERE"
