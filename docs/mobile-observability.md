# Mobile Observability — Sentry, Analytics, Offline UX (P15)

Single source of truth for the mobile telemetry surface. Both clients
funnel everything through one `Observability` class so the underlying
vendor (currently Sentry) can be swapped without touching call sites.

## Crash + error reporting

Both platforms initialize Sentry at app start when `SENTRY_DSN` is set
(blank DSN → no-op, useful for CI / local).

- **iOS** — `Pantopus/Core/Observability/Observability.swift`
- **Android** — `app/src/main/java/.../data/observability/Observability.kt`

Each call configures:

| Option | iOS / Android |
|---|---|
| `environment` | `local` / `staging` / `production` (from `PANTOPUS_API_ENV`) |
| `releaseName` | `app.pantopus.ios@1.0.0+1` / `app.pantopus.android@1.0.0+1` |
| `tracesSampleRate` | `1.0` for non-prod, `0.1` for prod |
| `sendDefaultPii` | `false` — never opt into Sentry's automatic PII attachers |
| `attachScreenshot` / `attachViewHierarchy` | both `false` |

### PII scrubbing

A `beforeSend` hook on each platform redacts known-PII fields before any
event leaves the device.

Scrubbed key names (case-insensitive match on `extra` / `data` keys):
`email`, `email_address`, `phone`, `phone_number`, `address`, `street`,
`city`, `state`, `zip`, `postal_code`, `name`, `first_name`,
`last_name`, `password`, `token`, `authorization`, `secret`.

Values that match an email regex (`[\w.+-]+@[\w-]+\.[\w.-]+`) or a
loose phone regex (`\+?\d[\d\s().-]{7,}`) inside any string field are
also replaced with `[redacted]`.

A second `beforeBreadcrumb` hook applies the same scrubbing to
breadcrumb data + messages so analytics events that get mirrored into
Sentry don't leak PII either.

### Identity tagging

Once the user signs in, both platforms call
`Observability.identify(userId:, email:)`. The user id surfaces in
Sentry's grouping; email is stored only when explicitly passed in (and
gets replaced by `[redacted]` if it ends up in a free-form message).

### Platform tags

Every event is tagged with:

- `app_version` — marketing version (e.g. `1.0.0`)
- `os_version` — OS string (e.g. `iOS 17.4.1` / `Android 14`)
- `device_model` — hardware identifier (e.g. `iPhone15,2` /
  `Google Pixel 6`)

## Analytics taxonomy

Both clients ship a closed `AnalyticsEvent` enum / sealed hierarchy
that mirrors the P15 taxonomy 1:1. Adding a new event requires
extending the enum — untyped strings never reach the wire.

| Event | Properties | Where it fires |
|---|---|---|
| `screen.hub.viewed` | — | `HubView`/`HubScreen` `onAppear` / `LaunchedEffect` |
| `screen.mailbox_list.viewed` | — | `MailboxListView`/`MailboxListScreen` |
| `screen.mailbox_drawers.viewed` | — | `MailboxDrawersView`/`MailboxDrawersScreen` |
| `screen.my_homes.viewed` | — | `MyHomesListView`/`MyHomesListScreen` |
| `screen.home_dashboard.viewed` | — | `HomeDashboardView`/`HomeDashboardScreen` |
| `screen.mailbox_item_detail.viewed` | `category`, `trust_level` | Fired in the VM when state moves to `.loaded` |
| `screen.edit_profile.viewed` | — | `EditProfileView` (iOS only this milestone) |
| `screen.add_home_wizard.step_viewed` | `step_number`, `step_name` | Fired on every step transition (initial step from the screen) |
| `cta.hub.action_strip_tapped` | `label` | Wire when wiring the live action strip |
| `cta.hub.pillar_tapped` | `pillar` | Wire when wiring pillar tap routing |
| `cta.mailbox_item.log_received` | — | `MailboxItemDetailViewModel.logAsReceived()` |
| `cta.add_home.submit` | — | `AddHomeWizardViewModel.submit()` |
| `form.edit_profile.submit` | `result` (`success`\|`error`) | iOS: `EditProfileViewModel.save()` |
| `form.edit_profile.validation_error` | `field` | iOS: `EditProfileViewModel.save()` when validation fails |

In Debug builds, every event also lands in the platform log:

- iOS: `print("📊 analytics screen.hub.viewed ...")` → Xcode console.
- Android: `Timber.tag("analytics").i("📊 ...")` → Logcat.

> TODO(analytics): wire `Analytics.track()` to a real vendor SDK
> (Amplitude / Mixpanel / PostHog). Today the production path is a
> Sentry breadcrumb only.

## Offline UX

### Detection

| Platform | Source |
|---|---|
| iOS | `Pantopus/Core/Networking/NetworkMonitor.swift` — `NWPathMonitor` wrapped as `@Observable`, exposes `isOnline: Bool`. Singleton at `NetworkMonitor.shared`. |
| Android | `data/network/NetworkMonitor.kt` — Hilt `@Singleton`, registers a `ConnectivityManager.NetworkCallback`, exposes `isOnline: StateFlow<Boolean>`. |

Both default to `online = true` so first-launch UI doesn't flicker
through a false "offline" state before the OS reports.

### Banner on list screens

Both platforms ship an `OfflineBanner` component that renders a
warning-tinted strip with the copy
**"You're offline. Showing last known data."** + a dismiss X.

- iOS: `OfflineBanner.swift` + the `View.offlineBanner(isOffline:)`
  modifier. Wired into `HubView`, `MyHomesListView`, `MailboxListView`,
  `MailboxDrawersView`, `HomeDashboardView`.
- Android: `OfflineBanner.kt` + the `OfflineBannerHost(isOffline:, content:)`
  composable.

The banner is dismissable, then re-appears on the next offline transition.

> TODO(offline): wire `OfflineBannerHost` into Android's HubScreen,
> MyHomesListScreen, MailboxListScreen, MailboxDrawersScreen, and
> HomeDashboardScreen. The component + NetworkMonitor are in place
> — only the per-screen wrapping is left.

### Empty state when offline + no cache

Per spec: when a list screen is offline AND has no cached data, render
the standard `EmptyState` with `wifi-off` icon, "You're offline"
headline, and a "Retry" CTA.

- The `wifi-off` icon is added to `PantopusIcon` on both platforms
  (iOS → `wifi.slash` SF Symbol; Android → `Icons.Filled.WifiOff`).
- Wiring this into the actual list view models is a follow-up — the
  current empty path falls back to the existing API-error EmptyState.
  Per-screen wiring is mechanical: when `isOffline && cachedRows.isEmpty`,
  render `EmptyState(icon: .wifiOff, headline: "You're offline", …)`.

> TODO(offline): swap the offline-empty path on each list view model.

### Mutations: never silent-queue

For every mutation surface the user can drive offline, the platform
checks `NetworkMonitor.isOnline` before issuing the request and falls
back to an inline error: **"You're offline. Try again when you're
back online."**

| Mutation | Guard |
|---|---|
| `EditProfileViewModel.save()` (iOS) | toast `kind: .error` |
| `AddHomeWizardViewModel.submit()` (both) | inline `errorMessage` banner |
| `MailboxItemDetailViewModel.logAsReceived()` (both) | `ctaFlags.errorToast` |

No silent queueing. The user is always told their action didn't apply.

## Verification

Acceptance criteria from the prompt and how to verify:

1. **Test crash from both platforms on release builds** — fire one in
   `PantopusApp.init` (iOS) / `PantopusApplication.onCreate` (Android)
   gated behind a `-PtestCrash=true` Gradle property / launch arg.
   Once verified in the Sentry dashboard, **remove the trigger**.
2. **Every screen fires its `screen.<X>.viewed` event** — run the iOS
   `EditProfileUITests` / Android `AddHomeWizardScreenTest` with the
   debug log open and grep for the `📊 analytics` line.
3. **Offline banner appears + disappears** — toggle airplane mode on
   the simulator/emulator (or `xcrun simctl status_bar … --override`
   for iOS, `adb shell svc wifi disable` for Android).
4. **Mutations show inline error** — same toggle + tap save / submit
   / log-received.
5. **No PII in scrubbed payloads** — fire a tagged event with
   `email = "alice@example.com"` and inspect the breadcrumb in the
   Sentry preview pane; the value should read `[redacted]`.
