#!/bin/bash
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.
#
# Copyright (c) 2026 DFD
#
# Runs every benchmark scene (or the ones given) on a device and prints one table row per scene,
# measured over the scene's "# window:".
#
#   tools/perf/baseline.sh <adb serial> "<label>" <out dir> [scene...]
set -u
DEV=$1; LABEL=$2; OUT=$3; shift 3
HERE=$(cd "$(dirname "$0")" && pwd)
SCENES=${*:-sm3dw mk8 bayo2 xcx botw}
mkdir -p "$OUT"
header=""
for scene in $SCENES; do
  file="$HERE/scenes/$scene.txt"
  read -r from to < <(sed -n 's/^# window: *//p' "$file")
  dur=$(sed -n 's/^# duration: *//p' "$file")
  echo ">> $scene (${dur}s, window ${from}-${to}s)" >&2
  line=$("$HERE/run_scene.sh" "$DEV" "$scene" "$OUT/$scene" "$to" | tail -1)
  echo "$line" >&2
  session=$(ls -d "$OUT/$scene"/20[0-9][0-9]* 2>/dev/null | sort | tail -1)
  [ -n "$session" ] || continue
  python3 "$HERE/summarize.py" "$session" --from "$from" --to "$to" --label "$LABEL" $header | tee -a "$OUT/table.md"
  header="--no-header"
done
