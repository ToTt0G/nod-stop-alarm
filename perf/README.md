# Performance and battery checks

Nod has one rule: **no nod set means no work**. While a nod is set, it does as little as it safely can.
Every claim below has a check you can rerun.

## On a device or emulator (`adb` connected, release build installed)

| Script | What it proves | Pass criteria |
|---|---|---|
| `perf/idle.sh [secs]` | No background cost when no nod is set. Run it after you've used the app and left it. | No services, location requests, wake locks, wakeup alarms or scheduled jobs. Screen off on battery: CPU ≤ 50 ms, 0 wakeups, 0 jobs run, 0 wake lock time, 0 GPS. |
| `perf/qs.sh` | Opening Quick Settings with Nod's tile added doesn't start Nod. | Process never starts across 3 shade opens. |
| `perf/frames.sh` | An idle screen renders nothing (no endless animations). | ≤ 2 frames in 10 s on Home. |
| `perf/startup.sh [runs] [max_ms]` | Cold start time (ActivityManager `TotalTime`). | Median ≤ 900 ms on the reference emulator. |
| `perf/trip.sh [secs]` | Background cost of an active nod. Start one first. | Reports CPU, wakeups, wake lock and GPS time with the screen off. |
| `perf/widgets.sh <place_id> <lat_from> <lon> <lat_to> [mps]` | Widget cost during a trip. Starts a nod to the saved place, then replays a ride (`perf/ride.sh`, emulator GPS) with the screen off. | Reports CPU, wake lock, jobs and job wake-lock time. With the lean renderer: 0 jobs. |

## JVM unit tests (`./gradlew :app:testPlayDebugUnitTest`)

`CadenceTest` simulates whole journeys. Each location fix arrives only after the interval the engine
asked for, so the battery saving and its safety are tested together:

- **Safety:** the alarm fires inside the zone within 6 s of entering it, and before the stop. This
  holds for buses, trams, metros, regional trains, 300 km/h trains and 150 m custom circles, starting
  3 to 250 km away. A sudden 5 → 100 m/s burst is caught too. The heads-up always gives ≥ 60 s warning.
- **Cost:** a 100 km train ride takes 3.5× fewer location fixes and 4.7× fewer satellite fixes
  than the previous fixed schedule.

## Macrobenchmarks (`:baselineprofile`)

- `./gradlew :app:generateBaselineProfile` regenerates the Baseline Profile (recorded on the `play`
  flavor) in `app/src/main/generated/baselineProfiles`, which both the `play` and `foss` release
  builds ship as AOT-compiled startup and journey code.
- `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest` measures cold start with and
  without the profile (`timeToInitialDisplayMs`, `timeToFullDisplayMs`).

## What makes it cheap

- **Idle:** the app schedules nothing at all: no alarms, no jobs, no boot receiver, no periodic
  widget updates (`updatePeriodMillis=0`). Widgets and shortcuts are only redrawn when something
  visible changed, and that's remembered across restarts. The Quick Settings tile is an *active*
  tile, so it's bound only when Nod asks or you tap it.
- **Widgets:** every render (app updates, trips, placement, boot, resize, app upgrade) is composed
  in-process and handed to the launcher. Glance's default path keeps a WorkManager job alive ~45 s
  per update, so the phone stays awake for most of a trip; Nod's widgets never start one. The ring
  is drawn only at the sizes on screen, capped at 420 px, using opaque 16-bit pixels on Android 12+.
- **Process start:** the map engine, notification channels and WorkManager are created on first use.
  The emoji font download is removed.
- **During a trip:** each location check may cover at most ¼ of the remaining distance at a
  generous assumed speed (≥ 90 km/h, or 1.5× the measured speed). Far away that means one check
  every 5 minutes using Wi-Fi/cell positioning. Within ~12 km, checks switch to GPS and tighten
  smoothly to every 3 s at the stop. If network positioning isn't available, Nod uses GPS instead.
  The notification is only reposted when something visible changed.
- **On screen:** no endless animations. The logo breathes three times and rests, and the live dot
  pulses with pauses that render no frames.
