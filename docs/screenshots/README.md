# Screenshots

Visual artifacts captured during the T5.x mobile UI/UX work. Two
categories live here:

## Production-rendered (real)

Captured against the actual platform implementation, via either a
simulator, an emulator, or a Playwright headless-browser run against
the production-built web app.

| File | Source | Captures |
|---|---|---|
| `notifications-v2-web-actual.png` | Playwright + Chromium headless-shell vs. `pnpm start` of the production build of `/list-of-rows-preview` | The actual `<ListOfRowsShell />` + `<RowCard />` rendering Shape A, B, C, C2, D (Notifications row), E, F, F2, G, leading-highlighted, and archived row examples. Confirms the production web component is wired to the design correctly. |

## Design-reference (visual contract)

Hand-rolled HTML re-implementation of `notifications-frames.jsx` from
the design package. The original design file pulls React + Babel from
`unpkg.com`, which the sandbox cannot reach — so these PNGs render
from a self-contained HTML harness (`/tmp/notifications-static.html`
at capture time) that mirrors the design verbatim (same tokens, same
geometry, same row spec).

| File | Frame | Notes |
|---|---|---|
| `notifications-v2-ios.png` | Populated, All tab, 4 unread + 3 earlier | Visual ground truth that the iOS implementation targets |
| `notifications-v2-android.png` | Populated, All tab | Same — Android implementation targets the same render |
| `notifications-v2-web.png` | Populated, All tab | Same — web implementation targets the same render. See `notifications-v2-web-actual.png` for the production-rendered counterpart |
| `notifications-v2-ios-empty.png` | Empty Unread state | Success-tinted check-check + "View all notifications" CTA |
| `notifications-v2-android-empty.png` | Empty Unread state | Same |
| `notifications-v2-web-empty.png` | Empty Unread state | Same |
| `connections-v2-ios.png` | Connections · populated · All tab | Visual ground truth for the iOS Connections (T5.2.3) implementation. 44pt avatar with verified-check overlay, mapPin-prefixed locality subtitle, userPlus-prefixed body, 38pt circular `message-circle` CTA per row, 52pt `secondaryCreate` FAB |
| `connections-v2-android.png` | Connections · populated · All tab | Same — Android implementation targets the same render |
| `connections-v2-web.png` | Connections · populated · All tab | Same — web implementation targets the same render |
| `connections-v2-ios-pending.png` | Connections · Pending tab | Vertical Accept (30pt primary) / Ignore (28pt ghost) stack instead of message-CTA; avatar without verified check |
| `connections-v2-android-pending.png` | Same | Same |
| `connections-v2-web-pending.png` | Same | Same |
| `connections-v2-ios-empty.png` | Connections · All tab empty | `users-round` icon in primary-tinted disc, "No connections yet" + "Find people" CTA |
| `connections-v2-android-empty.png` | Same | Same |
| `connections-v2-web-empty.png` | Same | Same |

## Why the design-reference shots exist

The acceptance gate calls for "one screenshot per platform" showing
each implementation matches the design 1:1. In a sandbox with no
Xcode, no Android SDK Manager access to `dl.google.com`, and no
network reach to `unpkg.com`, the closest local approximation is the
design's visual ground truth + the production-rendered web shot. The
real per-platform simulator screenshots will be captured in CI on
Mac/Android Studio runs and replace the design-reference iOS/Android
PNGs.
