# Publishing Nod

Four channels, in the order they become useful: GitHub Releases today, IzzyOnDroid within days, F-Droid within weeks, Google Play once the test period is done.

Before any of them, back up the release key (`app/nod-release.jks` plus `keystore.properties`, both git-ignored) somewhere safe. Every future update must be signed with it. Then tag the release (`git tag v1.0.0 && git push --tags`). Every channel below keys off that tag.

## 1. GitHub Releases

1. Build the APKs: `./gradlew :app:assemblePlayRelease` and `./gradlew :app:assembleFossRelease`.
2. Create a release for the tag at https://github.com/ToTt0G/nod-stop-alarm/releases/new.
3. Attach the `foss` universal APK as `Nod-1.0.0-foss.apk` and the `play` arm64 APK as `Nod-1.0.0.apk`. Keep file names stable across versions so update links keep working.
4. Paste `fastlane/metadata/android/en-US/changelogs/1.txt` as the release notes.

The README's download link points at `releases/latest`, so nothing else needs updating.

## 2. IzzyOnDroid (fastest)

IzzyOnDroid pulls APKs straight from GitHub Releases, so there is no build to pass. Typical turnaround is a few days.

1. Make sure the release has a signed APK attached (the `foss` one) and the repo has `fastlane/metadata/android/` with title, descriptions and screenshots. Izzy reads these for the listing.
2. Open a request at https://gitlab.com/IzzyOnDroid/repo/-/issues/new with the repo URL, package name `app.nod`, and which APK asset to take.
3. Reply to any review notes (common ones: non-free dependencies, missing license file, APK over the 30 MB limit per app; the arm64 APK is well under).

Later releases are picked up automatically from new GitHub Releases.

## 3. F-Droid

F-Droid builds from source on its own servers, so only the `foss` flavor is eligible.

1. Fork https://gitlab.com/fdroid/fdroiddata and add `metadata/app.nod.yml`. Start from the draft at [`docs/fdroid/app.nod.yml`](fdroid/app.nod.yml): Categories, `License: MIT`, SourceCode and IssueTracker URLs, `AutoName: Nod`, one `Builds` entry with `gradle: [foss]` and `versionCode: 1`, and `AutoUpdateMode: Version` / `UpdateCheckMode: Tags` so new tags are picked up.
2. Check the build locally with fdroidserver: `fdroid readmeta`, `fdroid lint app.nod`, then `fdroid build -v -l app.nod` (Docker image `registry.gitlab.com/fdroid/fdroidserver:buildserver` is the easiest way).
3. Open a merge request. The bot will build it; fix anything flagged (binary files in the tree, non-FOSS Maven artifacts).
4. Optional: reproducible builds. If the APK you publish on GitHub Releases is built bit-for-bit the same way, add `Binaries:` with the release URL and F-Droid will ship your signature instead of its own. Not needed for a first release.

Fastlane metadata in the repo (`fastlane/metadata/android/<locale>/`) feeds the F-Droid listing automatically, including screenshots and changelogs per version code.

## 4. Google Play

1. **Developer account.** One-time $25 at https://play.google.com/console. Identity verification takes a day or two.
2. **Closed test first.** New personal accounts must run a closed test with at least 12 testers opted in for 14 continuous days before production is unlocked. Create the closed track, add testers by email or a Google Group, share the opt-in link, and apply for production access after the 14 days.
3. **Build.** Upload the `play` flavor as an AAB: `./gradlew :app:bundlePlayRelease` produces `app/build/outputs/bundle/playRelease/app-play-release.aab`. Enroll in Play App Signing when prompted. Target API is 36, which meets the current target-level requirement.
4. **Store listing.** Fields from [`STORE_LISTING.md`](STORE_LISTING.md), screenshots from `fastlane/metadata/android/en-US/images/phoneScreenshots/`, feature graphic `docs/images/feature.png`, 512×512 icon. Add the sv-SE listing as a second language.
5. **Data safety form.** Declare that no data is collected or shared. Location is processed on-device only, which does not count as collection, and no data is encrypted in transit because nothing is sent. Link the privacy policy.
6. **Privacy policy URL.** https://github.com/ToTt0G/nod-stop-alarm/blob/main/PRIVACY.md (or the Pages site if you mirror it there).
7. **Permission declarations.** In App content:
   - *Foreground service (location):* describe the use ("Checks the user's distance to their chosen stop during an active trip, started by the user, with a visible notification") and attach a short screen recording showing: start a trip, the notification appears, the alarm fires, end trip.
   - *Full-screen intent:* select the alarm/alarm-clock use case. Nod is an alarm app, which is an allowed core use.
   - *Location:* background location is not requested; the foreground service is the only in-background use. Say so in the form.
8. **Content rating, ads, target audience.** IARC questionnaire (Everyone), no ads, not designed for children.
9. **Release.** Roll out to the closed track, then production. Paste the changelog from `fastlane/metadata/android/*/changelogs/1.txt` as release notes.

## Each new version

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Add `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt`.
3. Tag, build both flavors, publish the GitHub Release. IzzyOnDroid and F-Droid follow the tag; Play needs a new AAB upload.
