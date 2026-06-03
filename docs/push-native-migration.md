# Push Native Migration

Tracker for the move off `expo-server-sdk` toward a native push stack
(APNs on iOS, FCM on Android) backed by a single `POST
/api/notifications/register` endpoint.

The migration runs in three lanes — backend, iOS client, Android client —
that all converge on the same `{ token, platform }` payload shape. Each
lane is itemised below with a status marker:

  - `[x]` shipped
  - `[ ]` open
  - `[~]` in progress

## 1. Backend

  - [x] `POST /api/notifications/register` accepting
        `{ token, platform?: "ios" | "android", provider?: "apns" | "fcm"
        | "expo" }`. Persists the token (with platform + provider) against
        the authenticated user and enables the push preference, mirroring
        the legacy `/push-token` route so dual-write works during the
        cutover. `platform`/`provider` each derive from the other when one
        is omitted; Expo-formatted tokens are auto-tagged `expo`. Route:
        `backend/routes/notifications.js`.
  - [x] Send messages via the platform transports directly — APNs over
        HTTP/2 with token-based `.p8` auth (iOS) and FCM HTTP v1 (Android).
        `services/pushService.js` is now a thin DB-bound orchestrator over
        `services/push/*`; see §4 and §5.
  - [x] Migration `152_push_token_platform_provider.sql` adds the
        `platform` + `provider` columns (nullable, backfilled to `expo`
        for existing rows) and a `provider` index.
  - [ ] Drop `expo-server-sdk` from `backend/package.json`. Held open
        deliberately: the Expo leg stays live behind `PUSH_EXPO_ENABLED`
        until the dual-write window closes (§4).

## 2. iOS client

  - [x] `AppDelegate.application(_:didRegisterForRemoteNotificationsWithDeviceToken:)`
        POSTs the APNs token to `/api/notifications/register` via
        `APIClient.shared.registerPushToken(_:platform:)` (platform=ios;
        the backend tags it provider=apns).
  - [x] `requestNotificationPermission()` prompts the user and triggers
        `UIApplication.shared.registerForRemoteNotifications()`.
  - [x] `userNotificationCenter(_:willPresent:)` shows banners in the
        foreground; `userNotificationCenter(_:didReceive:)` routes a tap.
        It reads `userInfo["link"]` (the unified payload key the backend
        sends), falling back to `deepLink`, and forwards it through
        `DeepLinkRouter.shared.handle(path:)` — which normalises a path
        like `/chat/42` to the `pantopus://` scheme, matching Android.

## 3. Android client

  - [x] `firebase-bom` + `firebase-messaging` in the version catalog and
        `com.google.gms.google-services` applied on `:app`.
  - [x] `PantopusMessagingService` extends `FirebaseMessagingService`.
        `onNewToken` calls
        `NotificationsRepository.registerPushToken(token, "android")`
        (→ `/api/notifications/register`) and `onMessageReceived`
        delegates to `NotificationDispatcher`. `PushTokenSyncer` re-syncs
        on app open when the server ACK is missing/stale.
  - [x] `NotificationDispatcher` splits payloads onto four channels
        (chat / mail / gig bid / system) and forwards `data["link"]` /
        `data["deepLink"]` to `DeepLinkRouter`, so the same backend `link`
        opens the same destination on iOS and Android. The backend sends
        FCM **data-only** messages (title/body/type/link in `data`) so the
        service fires in every app state and owns the tap intent.
  - [x] Service registered against `com.google.firebase.MESSAGING_EVENT`;
        `MainActivity` requests `POST_NOTIFICATIONS` at runtime on
        Android 13+.
  - [ ] **Release blocker:** `app/google-services.json` is still the
        committed placeholder. Pull the real file from the Firebase
        Console before release — FCM will not deliver until then. (This is
        the only Android gap; all client code is wired and tested.)

## 4. Backend message-send swap — shipped

The clients register against `/api/notifications/register`; the send path
now routes each stored token to its transport. Implemented:

  1. [x] `pushService.sendToUser/sendToUsers` fetch the user's tokens
         (`token, platform, provider`) and hand them to
         `push/dispatch.js`, which groups by provider and calls the
         matching sender. The `provider` column decides the transport;
         legacy rows with no provider are sniffed from the token format
         and default to Expo. Tokens a provider reports as permanently
         dead are pruned in one delete.
  2. [x] APNs HTTP/2 sender (`push/apnsClient.js`) and FCM HTTP v1 sender
         (`push/fcmClient.js`), each reading credentials from `.env`
         (§6). The Expo leg (`push/expoClient.js`) stays live behind
         `PUSH_EXPO_ENABLED` so we can roll back.

Remaining, to close the window:

  3. [ ] Backfill / dual-write monitoring: while a user has both an Expo
         token and an APNs/FCM token, both legs fire (current behaviour).
         Trim the Expo row once N consecutive days pass with successful
         native deliveries. (A scheduled job; not yet built.)
  4. [ ] Once native delivery is validated, set `PUSH_EXPO_ENABLED=false`,
         then remove `expo-server-sdk` and `push/expoClient.js`.

The Android and iOS clients won't change for §4 — they already speak the
unified contract.

## 5. Transport implementation note

The senders are built on Node's built-in `http2` (APNs) and global
`fetch` (FCM), with `jsonwebtoken` — already a backend dependency — doing
the crypto: an ES256 provider JWT for APNs token auth and an RS256
service-account assertion exchanged for an OAuth2 access token for FCM.

This deliberately replaces the `apns2` / `firebase-admin` libraries the
earlier plan named. Same wire protocols (APNs HTTP/2 token auth; FCM
HTTP v1), but **zero new npm dependencies** — so CI's
`pnpm install --frozen-lockfile` needs no lockfile churn or registry
access, and the network layer is trivially stubbed in unit tests. The
deviation is purely the transport library, not the protocol.

Module layout under `backend/services/push/`:

  - `tokenRouting.js` — pure provider classification (registration-time
    and send-time), with Expo-format detection.
  - `apnsClient.js` — `isConfigured` / `buildPayload` / `sendMany`, a
    cached ES256 provider JWT, and a reused HTTP/2 session.
  - `fcmClient.js` — `isConfigured` / `buildMessage` / `sendMany`, a
    cached OAuth2 access token; sends data-only messages.
  - `expoClient.js` — legacy Expo leg, `expo-server-sdk` required lazily,
    gated by `PUSH_EXPO_ENABLED`.
  - `dispatch.js` — pure group-by-provider + fan-out with injected
    senders; one provider failing or being unconfigured never blocks the
    others.

A provider with no credentials reports `isConfigured() === false` and is
skipped, so the backend runs (and tests pass) without any push secrets.

## 6. Environment variables

See `backend/.env.example` for the annotated template.

| Variable | Purpose |
|----------|---------|
| `PUSH_EXPO_ENABLED` | Keep the Expo leg live during dual-write (default on; set `false` to disable). |
| `APNS_KEY_ID` | 10-char Key ID for the `.p8` key. |
| `APNS_TEAM_ID` | Apple Developer Team ID. |
| `APNS_BUNDLE_ID` | App bundle id, used as the APNs topic. |
| `APNS_PRIVATE_KEY` | `.p8` PEM contents (literal `\n` accepted). |
| `APNS_PRIVATE_KEY_BASE64` | Alternative: base64 of the `.p8` file. |
| `APNS_PRODUCTION` | `true` → `api.push.apple.com`, else sandbox. |
| `FCM_PROJECT_ID` / `FCM_CLIENT_EMAIL` / `FCM_PRIVATE_KEY` | Service-account fields (preferred). |
| `FCM_SERVICE_ACCOUNT_JSON` | Alternative: the whole service-account JSON inline. |
| `GOOGLE_APPLICATION_CREDENTIALS` | Alternative: path to the service-account JSON file. |

Android additionally needs the real `app/google-services.json` (§3) for
the client SDK; the FCM **server** credentials above are independent of
that file.

## 7. Verification

  - `backend/tests/unit/push/*` covers token routing, dispatch fan-out +
    invalid-token aggregation, the APNs `.p8` ES256 JWT mint and payload,
    and the FCM OAuth2 exchange + data-only message build (a mocked
    round-trip per platform — no live credentials or network).
  - A test push round-trips end-to-end only with real credentials wired;
    the unit suite mocks the transport, as the plan allows.
