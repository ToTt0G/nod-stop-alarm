# Privacy policy for Nod

Last updated: 2026-11-11

Nod is a free, open-source stop alarm for Android, published at https://github.com/ToTt0G/nod-stop-alarm. This policy explains what the app does with your data. The short version: Nod collects nothing about you.

## What Nod does not do

- No account or sign-in.
- No ads.
- No analytics, crash reporting or usage tracking.
- No third-party SDKs that collect data.
- No data is sent to us. We have no servers.

## Location

Nod needs your location to wake you before your stop. Location is used only while a trip is active, and only on your phone:

- Location is read through the Android location service and compared with the stop you chose.
- It is never uploaded, logged or shared with anyone, including us.
- When no trip is set, Nod does not request location at all.

Nod asks for the location permission and runs a foreground service with a visible notification while a trip is active, so you can always see that it is running and end it.

## Data stored on your phone

Saved places, trip history and settings are stored only on your device. You can delete them in the app or by clearing the app's data or uninstalling it. Nothing is backed up to any service we operate.

## Network use

Nod uses the network for two things only:

1. **Place search.** When you type a station, stop or city name, the text you typed is sent to the Photon geocoder (https://photon.komoot.io, OpenStreetMap data) so it can return matching places. Your current location is not sent with the search.
2. **Map tiles.** The map loads tiles from OpenFreeMap (https://openfreemap.org). Like any map server, it receives requests for the tiles of the area you are viewing.

Both services receive your IP address as part of any ordinary web request. Neither request includes any identifier for you or your device beyond that, and Nod does not add any. See their own policies for how they handle requests.

## Permissions

- **Location (fine and coarse):** to measure your distance to the stop during a trip.
- **Foreground service (location):** to keep checking location while a trip is active, with a visible notification.
- **Notifications and full-screen intent:** to show trip progress and the wake-up alarm over the lock screen.
- **Vibrate and wake lock:** to ring the alarm reliably.
- **Internet and network state:** for place search and map tiles only.
- **Ignore battery optimizations (optional):** so a manufacturer's battery saver does not stop the alarm mid-trip.

## Children

Nod does not collect personal data from anyone, including children.

## Changes

If this policy changes, the new version will be published at the same address with a new date.

## Contact

Open an issue at https://github.com/ToTt0G/nod-stop-alarm/issues.
