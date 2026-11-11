#!/usr/bin/env bash
# Replays a straight ride on an emulator's GPS, one fix every 3 s (emulator console `geo fix`).
#   Usage: perf/ride.sh <lat_from> <lon> <lat_to> [speed_mps=15]
cd "$(dirname "$0")" && source ./common.sh
LAT=$1; LON=$2; TO=$3; MPS=${4:-15}
STEP=$(awk -v m="$MPS" 'BEGIN { printf "%.7f", m * 3 / 111320 }')
DIR=$(awk -v a="$LAT" -v b="$TO" 'BEGIN { print (b > a) ? 1 : -1 }')
while awk -v a="$LAT" -v b="$TO" -v d="$DIR" 'BEGIN { exit !((d > 0) ? a < b : a > b) }'; do
    A emu geo fix "$LON" "$LAT" >/dev/null
    LAT=$(awk -v a="$LAT" -v s="$STEP" -v d="$DIR" 'BEGIN { printf "%.7f", a + d * s }')
    sleep 3
done
