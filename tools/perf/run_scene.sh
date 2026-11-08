#!/bin/bash
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.
#
# Copyright (c) 2026 DFD
#
# Runs one benchmark scene on a device and pulls its performance log.
#
#   tools/perf/run_scene.sh <adb serial> <scene> <out dir> [screenshot times, e.g. "30 60 90"]
#
# Scenes live in tools/perf/scenes/<scene>.txt: a header with "# title:", "# title_id:" and
# "# duration:", then the input script (see LattePerfLog.h). Before each run the game's save is
# deleted (only the emulated save inside the app's own folder), so every run starts the same way.
# The "Record performance log" setting must be on and controller 1 must be a Wii U GamePad.
# PKG selects the app (default dev.dfd.fractal).
set -u
exec </dev/null
DEV=$1; SCENE=$2; OUT=$3; SHOTS=${4:-}
PKG=${PKG:-dev.dfd.fractal}
HERE=$(cd "$(dirname "$0")" && pwd)
FILE="$HERE/scenes/$SCENE.txt"
[ -f "$FILE" ] || { echo "no scene $FILE"; exit 1; }
TITLE=$(sed -n 's/^# title: *//p' "$FILE")
TID=$(sed -n 's/^# title_id: *//p' "$FILE")
DUR=$(sed -n 's/^# duration: *//p' "$FILE")
FILES=/sdcard/Android/data/$PKG/files
A="adb -s $DEV"
mkdir -p "$OUT"

$A shell am force-stop $PKG
$A shell rm -rf "$FILES/mlc01/usr/save/${TID:0:8}/${TID:8:8}"
$A shell mkdir -p "$FILES/perf"
$A push "$FILE" "$FILES/perf/input_$TID.txt" >/dev/null
before=$($A shell ls "$FILES/perf" | tr -d '\r' | grep "_$TID\$" | sort | tail -1)

# open the library and tap the game
$A shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 6
read -r x1 y1 x2 y2 < <($A exec-out uiautomator dump /dev/tty 2>/dev/null | grep -o "text=\"$TITLE\"[^>]*bounds=\"[^\"]*\"" | head -1 | grep -o 'bounds=.*' | grep -o '[0-9]\+' | tr '\n' ' ')
[ -n "${y2:-}" ] || { echo "$TITLE not in the library"; exit 1; }
$A shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
start=$(date +%s)

for t in $SHOTS; do
  now=$(( $(date +%s) - start )); [ $t -gt $now ] && sleep $(( t - now ))
  $A exec-out screencap -p > "$OUT/${SCENE}_${t}s.png"
done
now=$(( $(date +%s) - start )); [ $DUR -gt $now ] && sleep $(( DUR - now ))

alive=$($A shell pidof $PKG | tr -d '\r')
$A shell am force-stop $PKG
session=$($A shell ls "$FILES/perf" | tr -d '\r' | grep "_$TID\$" | sort | tail -1)
if [ -z "$session" ] || [ "$session" = "$before" ]; then echo "$SCENE: no new session (app alive: ${alive:-no})"; exit 1; fi
$A pull "$FILES/perf/$session" "$OUT/" >/dev/null
$A shell rm -f "$FILES/perf/input_$TID.txt"
echo "$SCENE: $OUT/$session (app alive at the end: ${alive:+yes})"
