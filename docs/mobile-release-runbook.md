# Mobile Release Runbook (P16)

Step-by-step instructions for shipping Pantopus to **TestFlight** and
the **Play Internal track**, plus the promote-to-production paths. Read
once before your first ship, then keep this file open during the run.

## Prerequisites (one-time)

### iOS
- Xcode ≥ 15.4, command-line tools active
  (`sudo xcode-select -switch /Applications/Xcode_15.4.app`).
- Ruby + bundler from `frontend/apps/ios/Gemfile`.
- `match` configured against your team's signing repo
  (`fastlane match init` then `match appstore`).
- `frontend/apps/ios/.env` populated with the production
  `STRIPE_PUBLISHABLE_KEY` and `SENTRY_DSN`.
- App Store Connect API key exported as `APP_STORE_CONNECT_API_KEY_PATH`
  (or use Apple ID + 2FA via `pilot`).

### Android
- JDK 17 on `PATH`.
- Release keystore in a private location plus the four
  `~/.gradle/gradle.properties` keys:
  ```
  PANTOPUS_KEYSTORE_FILE=/absolute/path/to/pantopus.jks
  PANTOPUS_KEYSTORE_PASSWORD=…
  PANTOPUS_KEY_ALIAS=pantopus
  PANTOPUS_KEY_PASSWORD=…
  ```
- Play service account JSON downloaded from the Play Console (Setup →
  API access). Path lives in `~/.gradle/gradle.properties` as
  `PANTOPUS_PLAY_SERVICE_ACCOUNT_JSON=/abs/path/play-service.json`.
- `frontend/apps/android/.env` populated with the production
  `PANTOPUS_API_BASE_URL`, `STRIPE_PUBLISHABLE_KEY`, `SENTRY_DSN`.

## TestFlight (iOS)

```bash
cd frontend/apps/ios
bundle install                 # one-time
bundle exec fastlane beta
```

What runs:
1. `xcodegen` regenerates `Pantopus.xcodeproj` from `project.yml`.
2. `match appstore` pulls signing certs (read-only in CI).
3. Build number bumped to `latest_testflight_build_number + 1`.
4. `build_app` produces a Release `.ipa` with
   `xcargs: PANTOPUS_API_ENV=production` so the binary points at
   `https://api.pantopus.app`.
5. `pilot` uploads to TestFlight; the changelog defaults to the latest
   git commit message.

After a few minutes the build appears in TestFlight under
**App Store Connect → My Apps → Pantopus → TestFlight**. Add testers
(internal or external groups) and announce the build.

## App Store production submit (iOS)

```bash
cd frontend/apps/ios
bundle exec fastlane release
```

Same build flow, then `deliver` uploads:
- `fastlane/metadata/en-US/*.txt` (name, subtitle, description, keywords,
  promotional text, release notes, support URL).
- Screenshots from `fastlane/screenshots/<lang>/<device>/` (regenerate
  via `bundle exec fastlane screenshots`).
- Submits the build for review. `automatic_release: false` keeps the
  rollout paused so a human flips the release switch in App Store
  Connect once Apple approves.

## Play Internal (Android)

```bash
cd frontend/apps/android
./gradlew publishReleaseBundle --dry-run    # smoke check
./gradlew publishReleaseBundle              # actually ship
```

What runs:
1. Gradle assembles a signed release `.aab` using the credentials in
   `~/.gradle/gradle.properties`.
2. Gradle Play Publisher reads the service-account JSON, picks track
   `internal`, and uploads the bundle as **draft**.
3. The Play Console surfaces the draft under **Testing → Internal
   testing** for a human to set live.

Smoke-check the dry-run first — it validates credentials and the AAB
structure without uploading.

### Alternate fastlane path

```bash
cd frontend/apps/android
bundle exec fastlane beta
```

Wraps the same Gradle commands plus `upload_to_play_store` against the
service-account JSON. Use whichever feels more natural.

## Play production promote (Android)

```bash
cd frontend/apps/android
bundle exec fastlane release
```

Promotes the latest internal build to the production track without
re-uploading the AAB. Rollout percentage stays at the default in the
Play Console.

## Screenshot regeneration

| Platform | Command | Output |
|---|---|---|
| iOS | `bundle exec fastlane screenshots` | `fastlane/screenshots/<lang>/<device>/<NN_Name>.png` |
| Android | `./gradlew :app:captureStoreScreenshots` (with a connected device/emulator) | `fastlane/metadata/android/en-US/images/phoneScreenshots/*.png` |

Both pipelines target the **six hero screens**: Hub populated, MyHomes,
HomeDashboard, MailboxList, MailboxItemDetail (package), EditProfile.

iOS captures at three sizes (iPhone 15 Pro Max / 15 / SE 3rd gen) so
the App Store has a screenshot per device class.

## Listing copy voice checklist

Re-run before approving any metadata change:

- Sentence case for headlines and CTAs; Title Case for nav-style items.
- Verbs first on CTAs ("Verify your home", not "Home verification").
- Second person; Pantopus is the doer, never "we".
- Trust motif present somewhere in the lede ("verified",
  "address-proven", "real people", "private by default").
- No exclamation points.
- No em-dash AI cadence ("not just X, but Y").
- No buzzword salad: avoid "revolutionary", "seamless", "reimagine",
  "unlock".

A grep that surfaces banned terms before commit:

```bash
grep -rEi 'seamless|reimagine|revolutionary|unlock|!|\bnot just\b' \
    frontend/apps/ios/fastlane/metadata \
    frontend/apps/android/fastlane/metadata
```

## What to do when a release fails review

| Symptom | Fix |
|---|---|
| Apple rejects metadata for non-spec terms | Update the offending `.txt` file, run `bundle exec fastlane deliver` (skip build). |
| Play console flags policy issue | Address in the Console UI; re-upload only if the AAB needs to change. |
| TestFlight build crashes on launch | Check Sentry for the stack trace; if it's a release-only path, reproduce by setting `PANTOPUS_API_ENV=production` locally. |
| Signing failure on CI | Confirm `match` credentials + `MATCH_PASSWORD` are present; rerun the lane. |
| Play Publisher `Service account not authorised` | Re-grant the service account `Release manager` permissions in the Play Console. |

## Known gaps

- The iOS `screenshots` lane drives `PantopusUITests` via fastlane
  `snapshot` but the canonical test method only covers four of the
  six hero screens reliably under the current navigation surface
  (`Hub populated`, `MailboxList`, `MailboxItemDetail`, `EditProfile`).
  `MyHomes` and `HomeDashboard` come once the production navigation
  surfaces a stable signed-in entry — TODO(release) marker in
  `StoreScreenshots.swift`.
- The Android `captureStoreScreenshots` test currently mounts
  placeholder composables — the real screen previews land alongside a
  Hilt-aware test runner. The pipeline (Gradle task + adb pull +
  fastlane folder layout) is wired so flipping the placeholder for the
  live screens is mechanical.
