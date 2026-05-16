# Screenshots

Visual artifacts captured during the T5.x mobile UI/UX work. Three
categories live here:

## T5 cross-platform parity composites (12)

Side-by-side iOS + Android + web renders of every new T5 list screen.
One composite per screen, generated from the static HTML harness at
`tools/t5-screenshots/` (kept in `/tmp` — regenerable; the harness
mirrors the design package verbatim). Each frame is rendered at the
canonical mobile viewport (iOS 390pt / Android 411dp / Web 400px).

| File | Screen | Caption — intentional platform-specific differences |
|---|---|---|
| `parity-notifications.png` | Notifications V2 | Web has a legacy `personal / business` context filter (mobile drops it per F2); iOS+Android tab strip is shrink-to-fit, web is `flex: 1`. Same primary-tinted unread tint + date sections on all three. |
| `parity-bills.png` | Bills | No platform divergence. `amountWithChip` trailing variant, 4-chip status set, 52pt `secondaryCreate` FAB. |
| `parity-pets.png` | Pets | Mobile ships a 3-step Add wizard; web keeps a single-page modal. Same row + species chip + kebab. |
| `parity-connections.png` | Connections | No data / a11y divergence. Same row geometry; pending tab switches the trailing CTA shape on all three. |
| `parity-offers.png` | Offers (cross-listing) | No platform divergence; T5.2.4 reskinned the web on top of `<ListOfRowsShell />`. |
| `parity-my-bids.png` | My bids | 48pt `extendedNav` "Browse tasks" pill FAB is identical on all three (not a create — F1). |
| `parity-my-tasks.png` | My tasks V2 | 56pt `canonicalCreate` FAB (largest variant) is identical; `BidderStack` leading is identical. |
| `parity-my-pulse.png` | My posts | No platform divergence on the row shape. Archive/Restore is local-only optimistic on all three (backend route lands T6). |
| `parity-listing-offers.png` | Listing offers | No platform divergence. Same `ListingContextConfig` hero card on all three. |
| `parity-discover-hub.png` | Discover hub | No platform divergence. Same 4-chip filter strip + 4 typed sections. |
| `parity-discover-businesses.png` | Discover businesses | Top-bar action: sliders icon on iOS/Android; on wide-viewport web the same icon sits inside a button with an explicit label. |
| `parity-review-claims.png` | Review claims | Web only — mobile placeholder rendered for visual contract continuity. Mobile admin tier is a T6 candidate. |

The per-platform PNGs that compose each parity image live under each
app's `__snapshots__/t5/` directory — see the T5 snapshot lockfile
section in `RELEASES.md`.

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

### T6.0a Bills V2 re-skin (design-reference)

Re-skin of the T5.2.2 Bills list — utility-tinted leading + 6-status
chips + summary banner with home-tinted "Pay all" CTA + optional
split-payer 18pt avatar stack + 56pt `canonicalCreate` FAB tinted
home-green. Rendered via `tools/t5-screenshots/bills-v2.mjs` (sister
script to `render.mjs`, kept separate so T5 baselines don't churn).

| File | Frame | Notes |
|---|---|---|
| `bills-v2-ios.png` | Bills · Upcoming tab (6 bills) | Visual ground truth for iOS / Android / web. Confirms: 6 status chips render in design colors (Overdue red, Due soon amber, Due amber, Scheduled blue, Paid green, Cancelled neutral); 8 utility category tiles (electric · gas · water · internet · hoa · insurance · trash · phone) plus `generic` fallback all source bg+fg from `UtilityCategoryPalette`; summary banner shows total $ due + overdue count + home-tinted "Pay all" CTA; auto-pay rows render an `Auto-pay` `inlineChip` next to the title; split rows render "Split N ways" + 18pt avatar stack at the right edge of the chip row. |
| `bills-v2-android.png` | Same | Mirrors iOS. Android status bar at the top differs by platform convention. |
| `bills-v2-web.png` | Same | Mirrors iOS. Status bar omitted in web frame (same convention as T5 web reference shots). |

### Planned (T5.2.4 Offers)

To be captured in CI on the Mac / Android Studio runs that ship with
this PR. Stubbed entries so the docs stay aligned with the parity
audit; updated when the PNGs land.

| File | Frame | Notes |
|---|---|---|
| `offers-v2-ios.png` | Received tab, 5 offers | Shape C rows — category gradient leading + priceStack ("$220" / "asking $240") + status chip. Hits `/api/gigs/received-offers`. |
| `offers-v2-android.png` | Received tab, 5 offers | Mirrors iOS. Hits the same endpoint. |
| `offers-v2-web.png` | Received tab, 5 offers | Production web via Playwright vs `/app/offers` (now backed by `<ListOfRowsShell />`). |
| `offers-v2-ios-sent.png` | Sent tab, populated | Same row shape, subtitle reads "Your offer · {time}" — no bidder identity. |
| `offers-v2-android-sent.png` | Sent tab | Mirrors iOS. |
| `offers-v2-web-sent.png` | Sent tab | Mirrors iOS. |

## Why the design-reference shots exist

The acceptance gate calls for "one screenshot per platform" showing
each implementation matches the design 1:1. In a sandbox with no
Xcode, no Android SDK Manager access to `dl.google.com`, and no
network reach to `unpkg.com`, the closest local approximation is the
design's visual ground truth + the production-rendered web shot. The
real per-platform simulator screenshots will be captured in CI on
Mac/Android Studio runs and replace the design-reference iOS/Android
PNGs.
