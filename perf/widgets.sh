#!/usr/bin/env bash
# Cost of an active nod *with Nod's widgets on the home screen*, while a ride is replayed toward
# the stop with the screen off. Glance renders each widget update in a WorkManager job, so "jobs"
# and job wake-lock time are the widget cost.
#   Usage: perf/widgets.sh <place_id> <lat_from> <lon> <lat_to> [speed_mps=15]
cd "$(dirname "$0")" && source ./common.sh
PLACE=$1; LAT=$2; LON=$3; TO=$4; MPS=${5:-15}
SECS=$(awk -v a="$LAT" -v b="$TO" -v m="$MPS" 'BEGIN { d = (b - a) * 111320; if (d < 0) d = -d; printf "%d", d / m }')
UID_=$(uid_of)
echo "Widgets during a ${SECS}s ride on $SERIAL ($(S dumpsys appwidget | grep -c "provider=.*$PKG/") Nod widgets placed)"
A emu geo fix "$LON" "$LAT" >/dev/null
S am start -n "$PKG/.MainActivity" --el start_place "$PLACE" >/dev/null
sleep 20
trip_active || { echo "Trip didn't start"; exit 2; }
S input keyevent KEYCODE_HOME; sleep 2
S dumpsys batterystats --reset >/dev/null
S dumpsys battery unplug
S input keyevent KEYCODE_SLEEP
./ride.sh "$LAT" "$LON" "$TO" "$MPS"
B=$(batt "$UID_")
JOBWL=$(S dumpsys batterystats --checkin | awk -F, -v u="$UID_" '$2==u && $4=="wl" && $5 ~ /\*job\*/ { t += $12 } END { print t + 0 }')
S dumpsys battery reset
S input keyevent KEYCODE_WAKEUP
echo "  $B job_wakelock_ms=$JOBWL"
finish
