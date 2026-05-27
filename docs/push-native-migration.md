# Push Native Migration

Tracker for the move off `expo-server-sdk` toward a native push stack
(APNs on iOS, FCM on Android) backed by a single `POST
/api/notifications/register` endpoint.

The migration runs in three lanes — backend, iOS client, Android client —
that can land independently because they all converge on the same
`{ token, platform }` payload shape. Each lane is itemised below with a
status marker:

  - `[x]` shipped
  - `[ ]` open
  - `[~]` in progress

## 1. Backend

  - [x] Add `POST /api/notifications/register` accepting
        `{ token: string, platform: "ios" | "android" }`. Persists the
        token against the authenticated user, mirroring the rows the
        legacy `/push-token` route writes so dual-write works during the
        cutover.
  - [ ] Send messages via the platform-native SDKs (APNs HTTP/2 + FCM
        HTTP v1) directly. Today `services/pushService.js` still wraps
        `expo-server-sdk`. **Next unblocked item** — see §4.
  - [ ] Drop `expo-server-sdk` from `backend/package.json` once §4 lands.

## 2. iOS client

  - [x] `AppDelegate.application(_:didRegisterForRemoteNotificationsWithDeviceToken:)`
        POSTs the APNs token to `/api/notifications/register` via
        `APIClient.shared.registerPushToken(_:platform:)`.
  - [x] `requestNotificationPermission()` prompts the user and triggers
        `UIApplication.shared.registerForRemoteNotifications()`.
  - [x] `userNotificationCenter(_:didReceive:)` forwards the
        `userInfo["deepLink"]` value into `DeepLinkRouter.shared`.

## 3. Android client

  - [x] Add `firebase-bom` + `firebase-messaging` to the version catalog
        and apply `com.google.gms.google-services` on `:app`. Placeholder
        `app/google-services.json` committed; real file pulled from the
        Firebase Console before release.
  - [x] `PantopusMessagingService` (under `app/.../push/`) extends
        `FirebaseMessagingService`. `onNewToken` calls
        `NotificationsRepository.registerPushToken(token, "android")`
        and `onMessageReceived` delegates to `NotificationDispatcher`.
  - [x] `NotificationDispatcher` splits payloads onto four
        notification channels (chat / mail / gig bid / system) and
        forwards `link` / `deepLink` to the existing `DeepLinkRouter`,
        so the same backend `link` opens the same destination on iOS
        and Android.
  - [x] Service registered in `AndroidManifest.xml` against
        `com.google.firebase.MESSAGING_EVENT`.
  - [x] `MainActivity` requests `POST_NOTIFICATIONS` at runtime on
        Android 13+, mirroring iOS `requestNotificationPermission()`.
  - [x] `PushTokenSyncer` retries token registration on app open when
        the last server-side ACK is missing or stale. Backed by
        `PushTokenAckStore` (SharedPreferences).
  - [x] Unit test for `NotificationDispatcher` payload routing;
        instrumented test for the runtime permission flow.

## 4. Next unblocked item — backend message-send swap

The iOS and Android clients are both registering tokens against
`/api/notifications/register`, but `services/pushService.js` still
delivers via `expo-server-sdk`. The remaining work, in order:

  1. Branch `pushService.sendNotification(userId, payload)` on the
     platform of the stored token: `apns:<…>` for APNs, `fcm:<…>` for
     FCM, `ExponentPushToken[…]` for the legacy Expo path. Keep the
     Expo path live during the dual-write window so we can roll back.
  2. Add an APNs HTTP/2 sender (Apple's `apns2` Node lib) and an FCM
     HTTP v1 sender (`firebase-admin`). Both should read credentials
     from the same `.env` slot the rest of the backend uses.
  3. Backfill: re-publish the next two batches of notifications to both
     channels for the same user when both an Expo token and an APNs/FCM
     token exist. Trim the Expo row once 7 consecutive days pass with
     successful native deliveries.
  4. Remove `expo-server-sdk` from `backend/package.json` and the
     `services/pushService.js` import once the dual-write window
     closes.

The Android and iOS clients won't change for §4 — they already speak
the unified contract.
