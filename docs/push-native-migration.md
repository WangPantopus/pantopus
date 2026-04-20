# Push Notifications — Native Migration Plan

> Status: **planned**, not yet implemented. The backend still uses `expo-server-sdk` because the old React Native app relied on Expo's push service. As part of the native iOS/Android rollout, migrate to direct APNs and FCM.

## Current state

- **Backend:** `backend/services/pushService.js` uses `expo-server-sdk`. It:
  - Stores device tokens in a DB column keyed on `token` (assumed to be an Expo push token, validated via `Expo.isExpoPushToken`).
  - Sends notifications through Expo's push service, which routes to APNs/FCM on our behalf.
  - Reads delivery receipts from Expo and prunes dead tokens.
- **Clients:** The deleted RN/Expo mobile app registered an Expo push token with the backend. The new native iOS and Android apps will register **raw** APNs / FCM tokens instead — they do not go through Expo.

## Target state

Two delivery paths, directly to the vendors:

- **iOS → APNs** via HTTP/2, using a `.p8` auth key (token-based, not certificate-based).
- **Android → FCM HTTP v1** via a service-account JSON (legacy server keys are deprecated).

A `platform` column on the device-token table discriminates which sender to use.

## Schema change

Add a `platform` column (enum-like string: `ios` | `android` | `expo`) to whichever table stores push tokens. During migration:

1. Add the column as nullable.
2. Backfill existing rows to `expo` (they were all Expo tokens).
3. In the new client's registration handler, require `platform` in the request body.
4. Eventually flip the column to `NOT NULL` once no new Expo tokens are being inserted.
5. Once the RN app is fully deprecated and no `platform = 'expo'` rows remain in the wild, drop the `expo-server-sdk` code path.

The token itself stays as a globally unique string — just now it's an APNs token (64 hex chars) or FCM token (longer, base64-ish).

## Backend changes

### New dependencies

```
# package.json
"@parse/node-apn": "^6.x",   # or "apn" — both are maintained
"firebase-admin": "^12.x"
```

Drop `expo-server-sdk` once the migration is complete.

### New service layout

```
backend/services/
├── pushService.js                  # Façade — dispatches by platform
├── pushProviders/
│   ├── apnsProvider.js             # node-apn, HTTP/2, .p8 key
│   ├── fcmProvider.js              # firebase-admin messaging
│   └── expoProvider.js             # Existing code, kept during migration
```

`pushService.send(userId, payload)` looks up all tokens for the user, groups them by `platform`, and dispatches to the right provider. Each provider reports back which tokens were rejected so the façade can prune them.

### Secrets

- **APNs:** `.p8` file + key ID + team ID + bundle ID (`app.pantopus.ios`). Store the `.p8` in AWS Secrets Manager (or equivalent) and load it into memory at boot. Token-based auth means the JWT is short-lived (~1h) — generate it on demand and cache.
- **FCM:** Service-account JSON from the Firebase console. Same storage approach.

Add to `backend/config/env.js`:
```
APNS_KEY_ID, APNS_TEAM_ID, APNS_BUNDLE_ID, APNS_KEY_P8  // base64-encoded .p8 contents
FCM_SERVICE_ACCOUNT_JSON                                 // base64-encoded JSON
```

### Topic / silent / rich notifications

- **APNs:** `apns-topic` header is the bundle ID. `apns-push-type` = `alert` (default) or `background` for silent pushes.
- **FCM:** Use `data`-only messages for background delivery; `notification` + `data` for user-visible.

Claim a `thread-id` on iOS and a `tag` on Android so conversation notifications coalesce in the system tray.

## Client changes

### iOS

Already wired — `AppDelegate.didRegisterForRemoteNotificationsWithDeviceToken` calls `APIClient.registerPushToken(token, platform: "ios")`. Nothing further required client-side until rich-notification service extensions or notification actions are added.

### Android

Needs FCM wiring, which is **not** in the current scaffold:

1. Add Firebase project in the Firebase console; download `google-services.json` to `frontend/apps/android/app/`.
2. Add to `gradle/libs.versions.toml`:
   ```
   googleServices = "4.4.2"
   firebaseBom = "33.5.1"
   firebase-messaging = { group = "com.google.firebase", name = "firebase-messaging" }
   googleServices-plugin = { id = "com.google.gms.google-services", version.ref = "googleServices" }
   ```
3. Apply the plugin in `build.gradle.kts` and the dependency in `app/build.gradle.kts`.
4. Add a `FirebaseMessagingService` subclass that:
   - Overrides `onNewToken` → calls the registration endpoint with `platform: "android"`.
   - Overrides `onMessageReceived` → parses the `data` payload and shows a notification via `NotificationManagerCompat`.
5. Register the service in `AndroidManifest.xml`.
6. Never commit `google-services.json` — add it to `.gitignore` (already covered by the `*.json` patterns — double-check).

## Rollout order

1. Ship the backend changes (new columns, new providers, façade) with the Expo path still active. Nothing breaks for existing RN users.
2. Ship the native iOS app to TestFlight. It registers APNs tokens with `platform: "ios"`.
3. Ship the native Android app to an internal track. It registers FCM tokens with `platform: "android"`.
4. Wait for crash-free install counts to stabilise; then begin sunsetting the RN app.
5. Once the RN app is fully retired from the stores, stop accepting new Expo token registrations; background-prune the remaining `platform = 'expo'` rows; delete `expoProvider.js` and the dependency.

## Open questions (answer before starting)

- Do we want notification categories / actions (reply from tray, mark-read)? If yes, scope a notification service extension (iOS) and `RemoteInput` (Android).
- Rich media (images) in notifications — P0 or P1? APNs needs a service extension; FCM just takes a URL.
- Localisation — do we send pre-localised strings from the backend, or send `loc_key`s that the client resolves? The current Expo setup sends pre-localised; easiest to keep that.

---

*This doc is a plan, not code. Update it as decisions are made and delete it when the migration is complete.*
