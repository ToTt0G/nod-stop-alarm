#!/usr/bin/env bash
# Background cost of an active nod. Start a trip first (in the app, a widget or a shortcut) and put
# the device where you want to measure (e.g. far from the stop). Reports the location request Nod
# holds and what it consumed with the screen off.   Usage: perf/trip.sh [seconds=300]
cd "$(dirname "$0")" && source ./common.sh
SECS=${1:-300}
trip_active || { echo "No active nod. Start one first."; exit 2; }
UID_=$(uid_of)
S input keyevent KEYCODE_HOME; sleep 3
info "Location request held by $PKG"
S dumpsys location | grep -A1 -E "$PKG|$UID_/" | grep -E "Request\[|interval|PRIORITY|HIGH_ACCURACY|BALANCED|LOW_POWER|@[0-9]" | sort -u | head -6 | sed 's/^/    /'
S dumpsys batterystats --reset >/dev/null
S dumpsys battery unplug
S input keyevent KEYCODE_SLEEP
sleep "$SECS"
B=$(batt "$UID_")
S dumpsys battery reset
S input keyevent KEYCODE_WAKEUP
echo "  over ${SECS}s screen off: $B"
finish
