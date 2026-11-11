#!/usr/bin/env bash
# Cold-start timing straight from ActivityManager (no test APK needed). Each run force-stops the
# app first. Usage: perf/startup.sh [runs=10] [max_median_ms=900]
cd "$(dirname "$0")" && source ./common.sh
RUNS=${1:-10}; MAX=${2:-900}
echo "Cold start x$RUNS on $SERIAL"
times=()
for i in $(seq "$RUNS"); do
    S am force-stop "$PKG"; sleep 1
    t=$(S am start -W -S -n "$PKG/.MainActivity" | sed -n 's/^TotalTime: //p')
    times+=("$t"); printf '  run %2d: %s ms\n' "$i" "$t"
    sleep 2
done
S input keyevent KEYCODE_HOME
sorted=$(printf '%s\n' "${times[@]}" | sort -n)
median=$(echo "$sorted" | awk '{a[NR]=$1} END {print (NR%2 ? a[(NR+1)/2] : int((a[NR/2]+a[NR/2+1])/2))}')
echo "  min $(echo "$sorted" | head -1) ms, median $median ms, max $(echo "$sorted" | tail -1) ms"
[ "$median" -le "$MAX" ] && pass "median cold start ≤ ${MAX} ms" || fail "median cold start ${median} ms > ${MAX} ms"
finish
