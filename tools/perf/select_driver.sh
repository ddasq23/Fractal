#!/bin/bash
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.
#
# Copyright (c) 2026 DFD
#
# Selects the global GPU driver through Settings > Drivers, installing it first if needed.
#
#   tools/perf/select_driver.sh <adb serial> "<driver name as listed>"   e.g. "System driver", "Turnip_v26.0.0_R8"
#
# Prints the custom_driver_path saved in settings.xml (empty = system driver). Changing the driver
# clears the pipeline cache, so run a warm-up pass before measuring.
set -u
exec </dev/null
DEV=$1; NAME=$2; A="adb -s $DEV"
dump(){ $A exec-out uiautomator dump /dev/tty 2>/dev/null | grep -o 'text="[^"]\+"[^>]*bounds="[^"]*"' | sed -E 's/^text="([^"]*)".*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/\1|\2|\3|\4|\5/'; }
tapt(){ local l; l=$(dump | grep "^$1|" | head -1); [ -n "$l" ] || return 1; IFS='|' read -r _ x1 y1 x2 y2 <<<"$l"; $A shell input tap $(( (x1+x2)/2 )) $(( (y1+y2)/2 )); }
read -r W H < <($A shell wm size | grep -o "[0-9]*x[0-9]*" | tail -1 | tr x " "); SX=$((W/2)); SY1=$((H*8/10)); SY2=$((H*5/10))
$A shell am force-stop dev.dfd.fractal
$A shell monkey -p dev.dfd.fractal -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 5
l=$($A exec-out uiautomator dump /dev/tty 2>/dev/null | grep -o 'content-desc="Configurações"[^>]*bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' ')
read -r x1 y1 x2 y2 <<<"$l"; [ -n "${y2:-}" ] || { echo "no settings button"; exit 1; }
$A shell input tap $(( (x1+x2)/2 )) $(( (y1+y2)/2 )); sleep 2
tapt "Drivers" || { echo "no Drivers entry"; exit 1; }; sleep 6
for try in $(seq 1 12); do
  row=$(dump | grep "^$NAME|" | head -1)
  if [ -n "$row" ]; then
    IFS='|' read -r _ _ ry1 _ ry2 <<<"$row"
    btn=$(dump | awk -F'|' -v a=$ry1 -v b=$((ry2+40)) '($1=="Install"||$1=="Use"||$1=="In use") && $3>=a-10 && $3<=b {print; exit}')
    [ -n "$btn" ] && break
  fi
  $A shell input swipe $SX $SY1 $SX $SY2 300; sleep 1
done
[ -n "${btn:-}" ] || { echo "driver $NAME not found"; exit 1; }
IFS='|' read -r label x1 y1 x2 y2 <<<"$btn"
if [ "$label" = "Install" ]; then $A shell input tap $(( (x1+x2)/2 )) $(( (y1+y2)/2 )); sleep 12; fi
row=$(dump | grep "^$NAME|" | head -1); IFS='|' read -r _ _ ry1 _ ry2 <<<"$row"
btn=$(dump | awk -F'|' -v a=$ry1 -v b=$((ry2+40)) '($1=="Use"||$1=="In use") && $3>=a-10 && $3<=b {print; exit}')
IFS='|' read -r label x1 y1 x2 y2 <<<"$btn"
[ "$label" = "Use" ] && { $A shell input tap $(( (x1+x2)/2 )) $(( (y1+y2)/2 )); sleep 3; }
$A shell am force-stop dev.dfd.fractal; sleep 1
cur=$($A shell "grep custom_driver_path /sdcard/Android/data/dev.dfd.fractal/files/settings.xml" | sed 's/.*_path>//; s/<.*//' | tr -d '\r')
echo "$NAME -> custom_driver_path: ${cur:-(system driver)}"
