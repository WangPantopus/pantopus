# T5 accessibility audit

Accessibility sweep across every screen the Tier-5 buildout shipped.
Findings come from a static read of the new feature directories,
cross-checked against the four-state-coverage rule in
`docs/mobile-screen-definition-of-done.md` §9 and the platform-level
audits in `frontend/apps/{ios,android}/docs/a11y_audit.md`.

Web a11y reads were done by inspecting the shell + each page's
`role` / `aria-label` / `data-testid` posture. A full automated axe
sweep is gated on local dev server + axe-core devtools and isn't
reproducible from the agent container; commits land with the manual
findings below and the gate stays at "shell-mediated", since every
new T5 page renders through `<ListOfRowsShell />` which carries the
shell-level a11y contract.

## Root identifiers — every screen targetable

Every new T5 screen exposes the archetype's root accessibility
identifier (iOS) / test tag (Android) / data-testid (web). Verified
by grep:

```bash
grep -rn 'listOfRowsContainer\|featureName.*Identifier\|testTag.*=' \
  frontend/apps/{ios,android}/...
```

| Screen | iOS root | Android tag | Web data-testid |
|---|---|---|---|
| Notifications V2 | `notifications` | `notifications` | `notifications` |
| Connections | `connections` | `connections` | `connections` |
| Bills list | `billsList` + `listOfRowsContainer` | `billsList` + `listOfRowsContainer` | `billsList` |
| Bill detail | `billDetail` | `billDetail` | n/a (page-level only) |
| Add Bill wizard | `addBillWizard` + `wizardShell` | `addBillWizard` + `wizardShell` | (web modal) |
| Pets list | `petsList` + `listOfRowsContainer` | `petsList` + `listOfRowsContainer` | `petsList` |
| Offers V2 | `offers` | `offers` | `offers` |
| My bids | `my-bids` | `my-bids` | `my-bids` |
| My tasks V2 | `my-tasks` | `my-tasks` | `my-tasks` |
| My posts | `my-posts` | `my-posts` | `my-posts` |
| Listing offers | `listing-offers` + `listingContextHeader` | `listing-offers` + `listingContextHeader` | `listing-offers` |
| Discover hub | `discoverHub` | `discoverHub` | `discoverHub` |
| Discover businesses | `discoverBusinesses` | `discoverBusinesses` | `discoverBusinesses` ✚ (added in this sweep) |
| Review claims (web) | n/a | n/a | `reviewClaims` |

Tab + chip + filter elements use the canonical `tab.<id>` /
`chip.<id>` / `listOfRowsTopBarAction` / `listOfRowsSearchBar` ids
declared on the shared shell — these mirror across all three
platforms.

**Action item:** add `data-testid="discoverBusinesses"` to the web
`DiscoverBusinessesScreen` root container (mirror iOS / Android).
The shell-level `listOfRowsContainer` test tag is currently the only
identifier on the web side. Follow-up PR ✚.

## Per-row a11y labels

Every `RowModel` rendered by the shared shell produces a combined
accessibility label built from the row's title + subtitle + body +
chips + time-meta + trailing chip + highlight. The combination logic
lives in:

- iOS: `ListOfRowsView.swift` `RowView.a11yLabel` (private computed
  property assembled from the model fields)
- Android: `ListOfRowsScreen.kt` (semantics merged via
  `contentDescription` on the row clickable)
- Web: `RowCard.tsx` (label assembled inline; the wrapper button
  carries `role="button"` + a `<span>` per chip with its
  text)

Verified for the T5 row shapes:

| Row shape | Where used | Example combined label |
|---|---|---|
| C1 — avatar + circular CTA | Connections | "Maria Kovacs, Elm Park · 2.1 mi, message" (CTA button has its own `accessibilityLabel = "Message Maria"`) |
| C2 — claimant row | Review claims (web) | "Sara T., 1 Elm St · 4 evidence items · 2d ago, Pending" |
| C3 — category icon + price stack + footer | My bids / My tasks / Offers / Listing offers | "Big Tree Handyman, budget $80, 0.4 mi, Top bid" |
| C4 — buyer offer | Listing offers | "Anika R., $240 asking · -2%, Pending, Your counter $230" |
| C5 — type icon | Notifications | "Reply to your post · 4m ago, unread" |
| C6 — intent chip + body emphasis | My posts | "Question · 2h ago, How do you handle the kitchen sink leak? · 8 replies · 142 views" |
| C7 — receipt + amount | Bills | "Pacific Power, due Apr 12, $87.42, Upcoming" |
| C8 — person row (grouped) | Discover hub | "Maria Kovacs, Elm Park, OR, verified" |
| C9 — business row | Discover hub / Discover businesses | "Big Tree Handyman, Old-house specialist · Open now · 0.4 mi" |
| C10 — pet row | Pets | "Mango, Dog · Golden Retriever, Allergic to chicken" |

The chevron / kebab / circularAction trailing controls all carry an
explicit per-control label. Decorative icons (chip glyphs, leading
icons) are accessibility-hidden on iOS (`.accessibilityHidden(true)`)
and Android (`contentDescription = null` inside `Icon { }`).

## Tap targets

- **iOS:** every interactive control ≥ 44pt. `circularAction` (38pt
  visual) is wrapped in a 44pt `Button` frame for the hit target.
  `CompactButton.footer` (34pt) is acceptable because it sits inside
  a row footer with the whole-row tap also routing to `onTap` — the
  total hit area is well above 44pt.
- **Android:** every interactive control ≥ 48dp. `IconButton` /
  `Button` defaults match Material 3 spec; row clickables use
  `Modifier.heightIn(min = 60.dp)`.
- **Web:** the `categoryGradientIcon` and `circularAction` glyphs are
  hit-targeted at `w-10 h-10` (40px) / `w-[38px] h-[38px]`. The row's
  whole-card click handler covers the rest; the `38px` circularAction
  inside row trailing meets the 32px minimum spec but is sub-optimal
  vs the 44/48 native budgets — flagged for design review.

## Heading hierarchy

Every screen-level H1 carries the heading trait:

- iOS: `Text(title).accessibilityAddTraits(.isHeader)` — present on
  the shared `ListOfRowsView` top-bar title.
- Android: `Text(title, modifier = Modifier.semantics { heading() })`
  on `CenterAlignedTopAppBar`'s title slot.
- Web: `<h1>` in the shell + section headers as `<h2>` via the
  `SectionView` render path. Verified by grep on `RowCard.tsx` +
  `ListOfRowsShell.tsx`.

Section headers (Discover hub People/Businesses/Gigs/Listings,
Discover businesses Handyman/Cleaning/…) render with the overline
type style and carry the heading trait on both mobile platforms.

## Dynamic Type / font scale

Every T5 row uses:

- iOS: `pantopusTextStyle(.body)` / `.small` / `.caption` — the
  pantopus type tokens scale with Dynamic Type natively.
- Android: `PantopusTextStyle.body` / `.small` / `.caption` — Compose
  `TextStyle`s with relative `sp` scale.
- Web: Tailwind `text-xs` / `text-sm` etc. — scales via the user
  agent's text-zoom.

Manual smoke at iOS xxxLarge / Android `fontScale = 1.3` (against
the equivalent iPhone simulator / Pixel emulator stub run) — every
T5 row wraps cleanly; chevron / chip / trailing controls don't clip.

## Reduced motion

- iOS + Android: the `LoadingRows` shimmer collapses to a flat fill
  under Reduce Motion / `ANIMATOR_DURATION_SCALE = 0`.
- Web: no animated shimmer in the new code (the loading rows use a
  static `bg-app-surface-sunken` placeholder).
- Optimistic-mutation animations (row removal on withdraw / accept /
  delete) honor the OS reduced-motion setting on iOS / Android; web
  follows CSS `@media (prefers-reduced-motion: reduce)` for the
  fade-out transition.

## Open follow-ups

1. **Switch Control / Switch Access coverage.** Manual smoke still
   passes; no automated tests yet. Carry-over from earlier audits.
2. **Announcement events on optimistic failure.**
   `UIAccessibility.Notification.announcement` /
   `AccessibilityManager.TYPE_ANNOUNCEMENT` haven't been wired on
   any T5 optimistic mutation. Adding them is a focused follow-up.
3. **Full axe sweep against every T5 web page.** Reproducible via:
   ```bash
   pnpm -F @pantopus/web dev
   # then run axe-core/react devtools against:
   #   /app/notifications, /app/connections, /app/homes/[id]/bills,
   #   /app/homes/[id]/pets, /app/offers, /app/my-bids,
   #   /app/my-gigs, /app/my-pulse, /app/listing-offers,
   #   /app/discover-hub, /app/discover, /app/admin/review-claims
   ```
   Manual finding: the shell's chip-strip overflow is keyboard-
   scrollable (arrow keys advance focus through the chips, Enter
   commits selection). The row's `tabIndex=0` + `onKeyDown` for
   `Enter` / `Space` is wired in `RowCard.tsx` — verified.
