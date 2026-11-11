# Shared helpers for Nod's on-device performance checks. Source, don't run.
set -uo pipefail
SERIAL=${SERIAL:-$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')}
PKG=${PKG:-app.nod}
[ -n "$SERIAL" ] || { echo "No device connected (set SERIAL=...)"; exit 2; }
FAILS=0
A() { timeout 90 adb -s "$SERIAL" "$@"; }
S() { A shell "$@" | tr -d '\r'; }
pass() { printf '  \033[32mPASS\033[0m %s\n' "$*"; }
fail() { printf '  \033[31mFAIL\033[0m %s\n' "$*"; FAILS=$((FAILS + 1)); }
info() { printf '  ---- %s\n' "$*"; }
uid_of() { S cmd package list packages -U "$PKG" | sed -n "s/^package:$PKG uid:\([0-9]*\).*/\1/p" | head -1; }
trip_active() { [ "$(S dumpsys activity services "$PKG" | grep -c "trip.TripService")" -gt 0 ]; }
launch_home() { S am start -W -n "$PKG/.MainActivity" >/dev/null; }
finish() { [ "$FAILS" -eq 0 ] && { echo "OK"; exit 0; } || { echo "$FAILS check(s) failed"; exit 1; }; }

# Battery stats for one uid from the checkin dump, collected since the last reset.
#   cpu_ms    user+system CPU time
#   wl_ms     partial wake lock time held
#   alarms    wakeup alarms fired
#   jobs      jobs run (count)
#   gps_ms    GPS sensor time
batt() {
    local uid=$1
    S dumpsys batterystats --checkin | awk -F, -v u="$uid" '
        $2==u && $4=="cpu" { cpu += $5 + $6 }
        $2==u && $4=="wl"  { wl += $12 }
        $2==u && $4=="wua" { wua += $6 }
        $2==u && $4=="jb"  { jb += $7 }
        $2==u && $4=="sr" && $5=="-10000" { gps += $6 }
        END { printf "cpu_ms=%d wl_ms=%d alarms=%d jobs=%d gps_ms=%d\n", cpu, wl, wua, jb, gps }'
}
