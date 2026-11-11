#!/usr/bin/env bash
# Opening Quick Settings (with Nod's tile added) must not start Nod.   Usage: perf/qs.sh
cd "$(dirname "$0")" && source ./common.sh
echo "Quick Settings tile on $SERIAL"
S cmd statusbar add-tile "$PKG/$PKG.tile.NodTileService" >/dev/null 2>&1
S cmd statusbar collapse; sleep 1
S am force-stop "$PKG"; sleep 2
for i in 1 2 3; do
    S cmd statusbar expand-settings; sleep 3
    S cmd statusbar collapse; sleep 1
done
[ -z "$(S pidof "$PKG")" ] && pass "shade opened 3x, Nod never started" || fail "opening Quick Settings started Nod's process"
finish
