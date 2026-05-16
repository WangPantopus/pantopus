# Pantopus — Release notes

Newest first. Each tier (T1–T5+) batches a coherent slice of feature work
across iOS, Android, and web. Per-PR detail lives under
`docs/t5-prs/T5-summary.md` (and its T1–T4 predecessors).

---

## Tier 5 — 12 list screens on the shared archetype · 2026-05-16

**Theme: parity at scale.** Twelve newly designed list screens shipped on
iOS, Android, and web, all on top of the shared `ListOfRows` archetype.
The archetype itself was extended additively (`T5.0`) to make the geometry
expressible without breaking any v1 call site. Web kept its 12 routes;
iOS and Android added 9 of the 12 screens that didn't exist on mobile.

### Screens (12)

| # | Screen | iOS path | Android path | Web path |
|---|---|---|---|---|
| 1 | Notifications V2 | `Features/Notifications/NotificationsView.swift` | `ui/screens/notifications/NotificationsScreen.kt` | `(app)/app/notifications` |
| 2 | Bills (list + detail + Add wizard) | `Features/Homes/Bills/BillsListView.swift` | `ui/screens/homes/bills/BillsListScreen.kt` | `(app)/app/homes/[id]/bills` |
| 3 | Pets | `Features/Homes/Pets/PetsListView.swift` | `ui/screens/homes/pets/PetsListScreen.kt` | `(app)/app/homes/[id]/pets` |
| 4 | Connections | `Features/Connections/ConnectionsView.swift` | `ui/screens/connections/ConnectionsScreen.kt` | `(app)/app/connections` |
| 5 | Offers (cross-listing) | `Features/Offers/OffersView.swift` | `ui/screens/offers/OffersScreen.kt` | `(app)/app/offers` |
| 6 | My bids | `Features/MyBids/MyBidsView.swift` | `ui/screens/my_bids/MyBidsScreen.kt` | `(app)/app/my-bids` |
| 7 | My tasks V2 | `Features/MyTasks/MyTasksView.swift` | `ui/screens/my_tasks/MyTasksScreen.kt` | `(app)/app/my-gigs` |
| 8 | My pulse (My posts) | `Features/MyPosts/MyPostsView.swift` | `ui/screens/my_posts/MyPostsScreen.kt` | `(app)/app/my-pulse` |
| 9 | Listing offers (per-listing) | `Features/ListingOffers/ListingOffersView.swift` | `ui/screens/listing_offers/ListingOffersScreen.kt` | `(app)/app/listing-offers` |
| 10 | Discover hub | `Features/DiscoverHub/DiscoverHubView.swift` | `ui/screens/discoverhub/DiscoverHubScreen.kt` | `(app)/app/discover-hub` |
| 11 | Discover businesses | `Features/DiscoverBusinesses/DiscoverBusinessesView.swift` | `ui/screens/discoverbusinesses/DiscoverBusinessesScreen.kt` | `(app)/app/discover` |
| 12 | Review claims | _(web only — admin tier)_ | _(web only — admin tier)_ | `(app)/app/admin/review-claims` |

### Archetype evolution (T5.0)

The shared `ListOfRows` archetype gained — additively — 8 leading
variants, 5 trailing variants, a chip-strip slot, a banner slot, a
listing-context hero slot, two FAB variants beyond the 56pt default
(`secondaryCreate` 52pt, `extendedNav` 48pt pill), a section-style
discriminator (`card` for grouped sections in Discover hub), and a new
`primary25 = #f8fbff` design token wired into `@pantopus/theme`,
`Theme.Color.*` (iOS), and `PantopusColors.*` (Android). Every existing
v1 call site (Notifications v1 / MyHomes / MyClaims / Mailbox) compiles
unchanged.

The contract is documented per-platform in:

- `frontend/apps/ios/Pantopus/Features/Shared/ListOfRows/README.md`
- `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/shared/list_of_rows/README.md`
- `frontend/apps/web/src/components/list-of-rows/README.md`

### Parity gains

The 12 designs landed in lockstep across iOS + Android + web. Each screen
exposes identical render states, carries identical accessibility / test
identifiers (`testTag` ↔ `accessibilityIdentifier` ↔ `data-testid`), hits
the same backend endpoint with the same query params, and renders the
same row geometry (within platform conventions for status bars / FAB
positioning).

**Documented divergences** (all visual-only or pre-existing platform
conventions):

1. **Notifications tab strip** — iOS / Android use shrink-to-fit chip-row
   tabs; web stretches tabs equal-width (`flex: 1`). Same data, same a11y.
2. **Connections / Pets Add flow** — mobile ships a 3-step Wizard; web
   keeps a single-page modal (fewer screens on web).
3. **Notifications context filter** — web has a legacy
   `all / personal / business` segment; mobile ships the design's 2-tab
   set (All / Unread) without it (per F2).
4. **Discover businesses top-bar action** — sliders icon on iOS / Android;
   web has the same icon but inside a button with an explicit label on
   wide viewports.
5. **Review claims** — web only. Mobile admin tier is a T6 candidate (F9).

Full parity matrix at [`docs/mobile-parity-audit.md`](docs/mobile-parity-audit.md).

### Snapshot lockfile

For every new screen, a design-reference snapshot PNG is checked in:

- iOS: `frontend/apps/ios/PantopusTests/__Snapshots__/t5/<screen>-ios.png`
- Android: `frontend/apps/android/app/src/test/snapshots/t5/<screen>-android.png`
- Web: `frontend/apps/web/tests/visual-regression/__snapshots__/t5/<screen>-web.png`

These are the **visual contract** — generated from the design package via
the static HTML harness at `tools/t5-screenshots/` (kept in `/tmp`, regenerable).
Platform-rendered baselines (the bytes the Mac CI / Android Studio CI / Playwright
visual-regression suite produces) live alongside, named per the framework's
convention, and are committed on first record-CI run for iOS/Android and
already-blessed for web. Drift on any platform fails the snapshot suite on the
next PR.

Side-by-side parity composites for all 12 screens:
`docs/screenshots/parity-<screen>.png`.

### Soak tests (regression hardening)

Per [`docs/soak-tests-t5.md`](docs/soak-tests-t5.md):

- **Web (real run, 60s/screen):** Playwright soak harness scrolls + filters
  + pull-to-refreshes each new page for 60 seconds while sampling Chromium
  Heap (`performance.memory.usedJSHeapSize`) and DOM-node counters every
  500 ms. Recorded into `docs/soak-tests-t5/<screen>-web.csv`. Pass
  criterion: **heap delta ≤ 8 MB and DOM-node delta ≤ 200** across the
  full 60 s.
- **iOS (procedure):** XCTest UI test harness drives the same interaction
  loop in the simulator with the Address Sanitizer + Strict-Concurrency
  diagnostics enabled. Wired in CI but executed on macOS runners only.
  Captured under `docs/soak-tests-t5/<screen>-ios.csv` after each CI run.
- **Android (procedure):** Espresso macrobenchmark + LeakCanary in
  instrumented mode drives the same loop on the Pixel 5 emulator profile.
  CI runs the harness on every PR to `master`; LeakCanary's report is
  attached as an artifact, and the soak run captures
  `am dumpsys meminfo` deltas into `docs/soak-tests-t5/<screen>-android.csv`.

### Lighthouse audits (web)

For each new web page, a Lighthouse audit is recorded under
`docs/lighthouse-t5/<screen>.json` / `.html`. Target scores
(P75 across 3 runs): Performance ≥ 85 · Accessibility ≥ 95 ·
Best Practices ≥ 95 · SEO ≥ 90. See
[`docs/lighthouse-t5/README.md`](docs/lighthouse-t5/README.md) for the
run protocol and the latest summary.

### PR sequence

13 merged PRs (T5.0 prereq + 12 features). Detailed inventory at
[`docs/t5-prs/T5-summary.md`](docs/t5-prs/T5-summary.md).

### Known gaps (out of scope; T6 candidates)

1. **Mobile admin role** — Review claims ships web-only. Mobile lands
   when `me.is_admin` + role-guard infrastructure is added.
2. **`shortlisted` / `your_rank` / `top_price` on bid DTO** — My bids
   `Top bid` / `Shortlisted` / `Outbid` chips degrade to `Pending` until
   the backend prep PR lands (optional decoders already in place; no
   mobile change needed when fields appear).
3. **Posts archive endpoints** — My posts Archive / Restore are
   local-only optimistic. `POST /api/posts/:id/archive` + `/unarchive`
   + `GET /api/posts/me?status=archived` tracked separately.
4. **Bills splits write endpoints + DELETE handler** — splits are
   read-only; detail screen soft-deletes via `PUT { status: "cancelled" }`.
5. **`@pantopus/theme` category accents** — web `discover-businesses/categories.ts`
   inlines 6–7 hex constants for category accent palettes.
   `colors.category.<name>` extension tracked separately.
6. **`me.home.bills` Me-tab tile wiring** — Bills is reachable from the
   Home Dashboard tile; the Me-tab tile falls through to the placeholder.
   Parallel-entry-point cleanup for T6.

— Pantopus engineering · 2026-05-16
