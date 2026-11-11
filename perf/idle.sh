#!/usr/bin/env bash
# "No nod set" must mean zero background cost. After using the app and leaving it, verifies that Nod
# holds no services, location requests, wake locks or wakeup alarms, then measures what it consumed
# with the screen off and the device unplugged.   Usage: perf/idle.sh [seconds=120]
cd "$(dirname "$0")" && source ./common.sh
SECS=${1:-120}
echo "Idle check on $SERIAL for ${SECS}s"
trip_active && { echo "A nod is active. End it first."; exit 2; }
UID_=$(uid_of)

launch_home; sleep 4
S input keyevent KEYCODE_HOME; sleep 3

info "Right after leaving the app"
svc=$(S dumpsys activity services "$PKG" | grep -c "ServiceRecord")
[ "$svc" -eq 0 ] && pass "no running services" || fail "$svc running service(s)"
# Live registrations only: skip the timestamped history log at the end of the dump.
live_loc() { S dumpsys location | grep -vE "^\s+[0-9]{2}-[0-9]{2} [0-9:.]+" | grep "$PKG"; }
loc=$(live_loc | wc -l)
[ "$loc" -eq 0 ] && pass "no location requests" || fail "location requests registered: $(live_loc | head -3)"
# Only the live table ("Wake Locks: size=N" up to the next blank line), not the history log.
wl=$(S dumpsys power | awk '/^Wake Locks: size=/{f=1; next} f && /^$/{f=0} f' | grep -cE "$PKG|$UID_")
[ "$wl" -eq 0 ] && pass "no wake locks" || fail "wake locks held"
al=$(S dumpsys alarm | grep -E "^\s+(RTC|ELAPSED)_WAKEUP.*$PKG" | wc -l)
[ "$al" -eq 0 ] && pass "no wakeup alarms" || fail "$al wakeup alarm(s) pending"
jobs=$(S dumpsys jobscheduler | grep -E "JOB #[0-9]+/[0-9]+: [0-9a-f]+ $PKG/" | wc -l)
[ "$jobs" -le 1 ] && pass "scheduled jobs: $jobs (≤1 allowed: Glance's dormant keep-alive)" || fail "$jobs scheduled jobs"

info "Screen off, unplugged, ${SECS}s"
S dumpsys batterystats --reset >/dev/null
S dumpsys battery unplug
S input keyevent KEYCODE_SLEEP
sleep "$SECS"
read -r CPU WL ALARMS JOBS GPS <<<"$(batt "$UID_" | sed 's/[a-z_]*=//g')"
S dumpsys battery reset
S input keyevent KEYCODE_WAKEUP
echo "  measured: cpu ${CPU}ms, wakelock ${WL}ms, wakeup alarms ${ALARMS}, jobs ${JOBS}, gps ${GPS}ms"
[ "$CPU" -le 50 ] && pass "CPU while idle ≤ 50 ms" || fail "CPU while idle ${CPU} ms"
[ "$WL" -eq 0 ] && pass "no wake lock time" || fail "wake lock time ${WL} ms"
[ "$ALARMS" -eq 0 ] && pass "no wakeups" || fail "${ALARMS} wakeups"
[ "$JOBS" -eq 0 ] && pass "no jobs ran" || fail "${JOBS} jobs ran"
[ "$GPS" -eq 0 ] && pass "no GPS" || fail "GPS on for ${GPS} ms"
finish
