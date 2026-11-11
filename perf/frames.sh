#!/usr/bin/env bash
# An idle screen should render nothing. Opens Home, lets entry animations settle, then counts frames
# rendered over a quiet window. Usage: perf/frames.sh [settle_s=8] [window_s=10] [max_frames=2]
cd "$(dirname "$0")" && source ./common.sh
SETTLE=${1:-8}; WINDOW=${2:-10}; MAX=${3:-2}
echo "Idle frames on Home ($SERIAL)"
launch_home; sleep "$SETTLE"
S dumpsys gfxinfo "$PKG" reset >/dev/null
sleep "$WINDOW"
out=$(S dumpsys gfxinfo "$PKG")
frames=$(echo "$out" | sed -n 's/^Total frames rendered: //p' | head -1)
jank=$(echo "$out" | sed -n 's/^Janky frames: //p' | head -1)
echo "  frames rendered in ${WINDOW}s idle: $frames (janky: $jank)"
[ "${frames:-999}" -le "$MAX" ] && pass "idle Home renders ≤ $MAX frames" || fail "idle Home rendered $frames frames"
finish
