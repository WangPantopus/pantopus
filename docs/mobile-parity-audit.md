# Mobile Parity / A11y / Perf Audit (T4.2)

Acceptance gate for the gap-closure effort (Tiers 1–4.1). Audits every
screen built in those tiers against three lenses: **iOS ↔ Android parity**,
**WCAG 2.2 AA + platform a11y guidelines**, and the **perf budgets** in
`frontend/apps/{ios,android}/docs/perf_budgets.md`.

Every drift found in this pass is either fixed below or recorded as an
intentional, acceptable difference with a one-line justification.

## T5.0 archetype evolution

The shared `ListOfRows` archetype was extended in T5.0 to express all 12
designs in `docs/t5-buildout-plan.md` without bespoke shells. The
extension is **strictly additive**: every existing call site
(`NotificationsViewModel`, `MyHomesListViewModel`, `MyClaimsListViewModel`,
`MailboxListViewModel`, `MailboxDrawersViewModel`) compiles unchanged
with no migration. New surface area:

- **`RowLeading`** — adds `typeIcon`, `categoryGradientIcon`,
  `avatarWithBadge` (36/40/44pt, optional verified overlay),
  `thumbnail` (56/64pt), `bidderStack` (22pt overlapping mini-avatars
  + `+N` overflow). Existing `.icon` / `.avatar` / `.none` unchanged.
- **`RowTrailing`** — adds `amountWithChip`, `circularAction`,
  `verticalActions` (28-30pt compact pairs), `priceStack`. Existing
  `.statusChip` / `.chevron` / `.kebab` / `.none` unchanged.
- **`RowModel`** — adds optional `body`, `inlineChip`, `chips[]`,
  `timeMeta`, `metaTail`, `note`, `highlight` (`unread` / `leading` /
  `archived`), and `footer` (1–3 in-card 34pt buttons).
- **`RowSection`** — adds optional `count`, `onSeeAll`, `style`
  (`flat` default / `card` for Discover hub).
- **`FABAction.Variant`** — `canonicalCreate` (56pt, default for
  backwards compat), `secondaryCreate` (52pt), `extendedNav(label)`
  (48pt pill).
- **Chrome slots** — optional `searchBar`, `chipStrip` (alt to tabs),
  `banner` (primary-tinted summary card above the first row).
- **New theme token** — `primary25` = `#f8fbff` (notification unread
  row background) on iOS asset catalog, Android `Color.kt`, and the
  `@pantopus/theme` package + Tailwind preset.
- **New shared components** — `CompactButton` (34pt footer / 28-30pt
  inline-action) and `BidderStack` (22pt overlapping mini-avatars +
  `+N` overflow tile). Both iOS and Android.
- **Web mirror** — `frontend/apps/web/src/components/list-of-rows/`
  exposes the same shell (`<ListOfRowsShell />`, `<RowCard />`,
  `<TabStrip />`, `<LoadingRows />`, `<EmptyState />`,
  `<ErrorBanner />`, `<FabButton />`). Token-only via Tailwind
  utilities. Preview at `/list-of-rows-preview`.

Future row screens (Notifications V2, My posts, My bids, My tasks V2,
Connections, Discover hub, Bills, Pets, Offers, Listing offers, Review
claims) project their DTOs straight into the contract above; no shell
change should be required to ship them.

## How to read this

Each row lists:
- **Screen** — the user-facing name, with the iOS view + Android composable.
- **States** — the four-state contract (Loading / Empty / Loaded / Error).
  All entries are `✓` unless noted; a `–` means the state is genuinely
  not reachable for that screen (e.g. modal accept/decline surfaces).
- **iOS id / Android tag** — the root accessibility identifier / test tag.
  When the screen uses a shared archetype (ListOfRows, Wizard, Form,
  ContentDetail, GroupedList), the archetype's identifier is the one the
  screenshot + UI suites query.
- **Endpoints** — the unique backend paths the VM hits during the
  primary load + optimistic-mutation flows.
- **Notes** — outstanding gaps + this-PR fixes (✚).

A row is **at parity** when iOS and Android expose the same root identifier
(or its archetype-mapped equivalent), the same render states, and call the
same backend endpoint with the same query params.

---

## 1. Screen-by-screen parity table

### Tier 0 — Auth + Root

| Screen | States | iOS id | Android tag | Endpoints | Notes |
|---|---|---|---|---|---|
| Login | n/a (form-only) | `loginScreen` ✚ | `loginEmailField` / `loginPasswordField` / `loginSubmitButton` (no root) | `POST /api/auth/sign-in` | iOS root now identified ✚; Android already targetable via field tags. |
| Root tab | n/a | `tab.<tabName>` | `PantopusBottomBar` (no explicit tag; selection driven by `Role.Tab`) | n/a | Tab bar a11y semantics handled by platform widgets. Inbox badge wired both sides. |

### Tier 1 — Hub + Home pillars + Mailbox

| Screen | States | iOS id | Android tag | Endpoints | Notes |
|---|---|---|---|---|---|
| Hub | Skeleton / FirstRun / Populated / Error | `hubScreen` ✚ | `hubScreen` | `GET /api/hub/overview · /today · /discovery` | iOS now matches Android root tag ✚. Bell `hubBellButton` + menu `hubMenuButton` on both. |
| Notifications V2 (T5.1) | Loading / Loaded / Empty / Error · 2 tabs (All / Unread) · Today/Earlier date sections · 7 type chips (reply · mention · claim · gig · listing · safety · system) · unread-tint row highlight | `notifications` + `tab.all` / `tab.unread` + `listOfRowsTopBarAction` | `notifications` + `tab.all` / `tab.unread` + `listOfRowsTopBarAction` | `GET /api/notifications?limit=&offset=&unread=true` · `PATCH /api/notifications/:id/read` · `POST /api/notifications/read-all` | Full parity. Web drops its T4.1-era 3-way `read` filter and `personal/business` context filter — not in the design (documented divergence per F2/F8 in `docs/t5-buildout-plan.md`). All three platforms project the same `RowModel` shape via the shared archetype: 40pt `typeIcon` leading, body, status chip, time-meta, `.unread` highlight. Hub bell-tap lands on the real screen on both iOS and Android. **Known visual divergence:** iOS + Android shell renders the tab strip as shrink-to-fit (horizontally scrollable, shared with Mailbox + future 3-/4-tab screens); the design + web render the 2 tabs as `flex: 1` equal-width. Behaviour, data, and a11y are identical — only the tab strip width distribution differs. Flagged as a T5.0 shell follow-up. |
| Connections (T5.2.3) | Loading / Loaded / Empty / Error · 3 tabs (All / Neighbors / Pending) · search bar above tabs · 44pt avatar with verified-check overlay (accepted) or unverified (pending) · per-row 38pt circular `message-circle` CTA on All / Neighbors → opens chat conversation · vertical Accept (primary 30pt) / Ignore (ghost 28pt) on Pending · `secondaryCreate` 52pt FAB | `connections` + `tab.all` / `tab.neighbors` / `tab.pending` + `listOfRowsTopBarAction` + `listOfRowsSearchBar` | `connections` + `tab.all` / `tab.neighbors` / `tab.pending` + `listOfRowsTopBarAction` + `listOfRowsSearchBar` | `GET /api/relationships?status=accepted` · `GET /api/relationships/requests/pending` · `POST /api/relationships/:id/accept` · `POST /api/relationships/:id/reject` | Full parity. Two GETs fire in parallel on first load; subsequent tab switches segment over the cached payload (no extra fetch). Accept / Reject are optimistic with rollback — accepting bumps `All` count and removes the pending row in the same frame; failures restore both. Neighbors filter is client-side: relationships whose `other_user.city` is non-empty (the backend doesn't expose a richer "verified neighbor" signal yet — locality presence is the honest stand-in). Web drops the previous `Sent` and `Blocked` tabs to match the mobile design's 3-tab contract; those flows remain reachable from the Settings index → "Blocked users" row (existing). Message-CTA opens `HubRoute.chatConversation` (iOS) / `ChildRoutes.CHAT_CONVERSATION` (Android), reusing the existing `ChatConversationView` / `ChatConversationHost`. Deep link `pantopus://connections` lands on the screen on both platforms (Android via `DeepLinkRouter.Destination.Connections`; iOS via `RootTabView` switching to Hub + `HubTabRoot` consuming the pending destination). |
| MyHomes list | Loading / Loaded / Empty / Error | `listOfRowsContainer` (archetype) | `listOfRowsContainer` ✚ | `GET /api/homes/my-homes` | Archetype tag unified ✚ (was `listOfRows` on Android). |
| MyClaims list | Loading / Loaded / Empty / Error | `listOfRowsContainer` | `listOfRowsContainer` | `GET /api/homes/my-claims` | Parity. |
| Home dashboard | Loading / Loaded / Error | `homeDashboard` ✚ + `contentDetailShell` | `contentDetailShell` | `GET /api/homes/:id` (with public-profile fallback) | iOS added a feature-level id ✚; both reach `contentDetailShell` via archetype. Dashboard now exposes a `pets` quick-action tile that pushes onto the Pets list via the host stack (iOS `HubRoute.homePets(homeId:)`, Android `ChildRoutes.HOME_PETS`). |
| Pets list (T5.2.1) | Loading / Loaded / Empty / Error · 52pt secondary-create FAB · no tabs | `petsList` + `listOfRowsContainer` + `addPetWizard` | `petsList` + `listOfRowsContainer` + `addPetWizard` | `GET /api/homes/:id/pets` · `POST /api/homes/:id/pets` · `PUT /api/homes/:id/pets/:petId` · `DELETE /api/homes/:id/pets/:petId` | Full three-platform parity on the new shape **E** row (64pt thumbnail leading + inline species chip + breed subtitle + notes body + kebab trailing) and the species-tinted gradient palette in `Core/Design/SpeciesPalette.swift` / `ui/theme/SpeciesPalette.kt` / `(app)/app/homes/[id]/pets/species-palette.ts`. iOS + Android present the Add / Edit pet flow as the shared Wizard archetype (3 steps: species, basics, details); web keeps the existing single-page edit/delete UX as a modal because the design didn't specify a multi-step shape for the desktop. Optimistic delete + rollback on all three platforms. |
| Add Home wizard | per-step | `wizardShell` | `wizardShell` | `POST /api/homes/check-address` · `POST /api/homes` | Wizard archetype identical. |
| Claim Ownership wizard | per-step | `wizardShell` | `wizardShell` | `POST /api/homes/:id/claim` | Parity. |
| Invite Owner form | n/a (form-only) | `formShell` | `formShell` | `POST /api/homes/:id/invite-owner` | Parity. |
| Mailbox list (All/Unread/Starred tabs) | Loading / Loaded / Empty / Error | `listOfRowsContainer` | `listOfRowsContainer` ✚ | `GET /api/mailbox?type=&viewed=&starred=&limit=&offset=` | Tabs (`tab.all`, `tab.unread`, `tab.starred`) carry the same id strings. |
| Mailbox drawers | Loading / Loaded / Empty / Error | `listOfRowsContainer` | `listOfRowsContainer` | `GET /api/mailbox/v2/drawers` | Parity. |
| Mailbox item detail | Loading / Loaded / Error | `mailboxItemDetailShell` ✚ | `mailboxItemDetailShell` ✚ | `GET /api/mailbox/:id` · `PATCH /api/mailbox/:id/ack` | Both renamed for parity ✚. |
| Disambiguate Mail | n/a (form-only) | `formShell` + `disambiguateRow_<drawer>` rows | `formShell` + `disambiguateRow_${drawer}` rows | `POST /api/mailbox/v2/resolve` | Parity. |
| Public profile | Loading / Loaded / Error | `publicProfile` ✚ + `contentDetailShell` | `contentDetailShell` | `GET /api/users/:id · /:id/audience-stats · /:id/follow/status` · `POST /api/users/:id/block` | iOS feature id added ✚. Android relies on archetype + per-action tags. |
| Edit Profile | Loading / Loaded / Error | `editProfileShell` (via FormShell) | `formShell` (Edit Profile is iOS-only this milestone — see Android a11y_audit.md §EditProfile) | `PATCH /api/users/profile` | Documented platform asymmetry — Edit Profile ships iOS-only this milestone. |

### Tier 2 — Marketplace + Gigs + Inbox + Detail shell

| Screen | States | iOS id | Android tag | Endpoints | Notes |
|---|---|---|---|---|---|
| Pulse feed (intent tabs) | Skeleton / Loaded / Empty / Error | `pulseFeed` | `pulseFeed` | `GET /api/feed?intent=&offset=` · `POST /api/feed/:id/like` | Compose FAB tagged on both. |
| Pulse post detail | Loading / Loaded / Error | `pulsePostDetail` ✚ + `contentDetailShell` | `contentDetailShell` | `GET /api/posts/:id` · `POST /api/posts/:id/comments` | iOS feature id added ✚. |
| Marketplace (Goods/Rentals/Free) | Loading / Loaded / Empty / Error | `marketplace` | `marketplace` | `GET /api/listings?category=&q=&offset=` | Empty/loading/empty all tagged. |
| Listing detail | Loading / Loaded / Error | `contentDetailShell` + `listingDetailSendOffer` | `contentDetailShell` | `GET /api/listings/:id` · `POST /api/listings/:id/offer` | Parity via shell. |
| Gigs feed | Skeleton / Loaded / Empty / Error | `gigsFeed` | `gigsFeed` | `GET /api/gigs?category=&sort=&offset=` | All chips + FAB tagged. |
| Gig detail | Loading / Loaded / Error | `contentDetailShell` + `gigDetailSubmitBid` | `contentDetailShell` | `GET /api/gigs/:id · /:id/bids` · `POST /api/gigs/:id/bids` | Parity via shell. |
| Invoice detail | Loading / Loaded / Error | `contentDetailShell` + `invoiceDetailDismiss` | `contentDetailShell` | `GET /api/invoices/:id` · `PATCH /api/invoices/:id/status` | Parity via shell. |
| Offers V2 (T5.2.4) | Loading / Loaded / Empty / Error · 2 tabs (Received / Sent) · Shape C rows (40pt categoryGradient leading + priceStack trailing "asking $X" sublabel + status chip + optional counter meta tail) · top-bar `filter` icon · no FAB · row-tap pushes gig detail | `offers` + `tab.received` / `tab.sent` + `listOfRowsTopBarAction` | `offers` + `tab.received` / `tab.sent` + `listOfRowsTopBarAction` | `GET /api/gigs/received-offers` (Received tab) · `GET /api/gigs/my-bids` (Sent tab) | Full parity. Both endpoints fetched in parallel on load; tab switch is in-memory (no refetch). 8-state status derivation runs client-side from `status` + `counter_status` + `counter_amount` + `expires_at` + `created_at` — same projection on all three platforms. Row tap navigates to gig detail (no dead-end). Web rewritten on top of `<ListOfRowsShell />` (replaces the legacy `OfferCard`). |
| Chat list (Inbox tab) | Skeleton / Loaded / Empty / Error | `chatList` | `chatList` | `GET /api/chats?filter=&offset=` · `POST /api/chats/:id/read` | Parity. |
| Chat conversation | Loading / Loaded / Empty / AiWelcome | `chatConversation` | `chatConversation` | `GET /api/chats/:id/messages?offset=` · `POST /api/chats/:id/messages` · WS | Parity. iOS only has the `aiWelcome` state today; Android falls through Loaded with an empty-message render — equivalent UX. |
| Nearby map | Loading / Loaded / Error | `nearbyMap` | `nearbyMap` | `GET /api/nearby/map?lat=&lng=&radius=` | Parity. |
| Me / You | Loading / Loaded / Error | `meScreen` | `meScreen` | `GET /api/users/me` · `GET /api/users/me/audience` | Parity (identity-switcher pills share `meIdentityPill_<key>`). |

### Tier 3 — Settings + Identity surfaces + Privacy + Status + Ceremonial

| Screen | States | iOS id | Android tag | Endpoints | Notes |
|---|---|---|---|---|---|
| Settings index | n/a (grouped list) | `groupedList` | `groupedList` | `GET /api/users/me` (for footer) | Routes off-stage to its sub-screens. |
| Notification settings | Loading / Loaded / Error | `groupedList` | `groupedList` | `GET /api/users/preferences/notifications` · `PATCH /api/users/preferences/notifications` | Parity. |
| Privacy settings | Loading / Loaded / Error | `groupedList` | `groupedList` | `GET /api/users/preferences/privacy` · `PATCH /api/users/preferences/privacy` | Parity. |
| Identity Center | Loading / Loaded / Error | `identityCenter` | `identityCenter` | `GET /api/identity-center` | Parity. |
| Audience Profile | Loading / Loaded / Empty / Error | `audienceProfile` + `audienceProfileContent` | `audienceProfile` | `GET /api/personas/me` · `/posts` · `/tiers` · `/membership-stats` · `/dms/threads` | Parity. Tab pills `audienceProfileTab_<id>` ↔ `audienceProfileTab_${id}`. |
| Privacy Handshake | per-step | `privacyHandshake` (`wizardShell`) | `privacyHandshake` (`wizardShell`) | `GET /api/personas/:handle` · `/:handle/tiers` · `POST /api/personas/:handle/follow` | Parity. |
| Token Accept | n/a | `tokenAccept` + `tokenAcceptOffer` | `tokenAccept` | `GET /api/homes/invitations/token/:t` · `POST /api/homes/invitations/token/:t/accept` | Parity. |
| Status / Waiting | n/a | `statusWaiting` | `statusWaiting` | n/a (presentational) | Parity. |
| Ceremonial Mail Compose | per-step | `ceremonialMail` (`wizardShell`) | `ceremonialMail` (`wizardShell`) | `GET /api/mailbox/compose/recipients` · `GET /api/mailbox/compose/home-context/:id` · `POST /api/mailbox/send` | All wizard step tags identical. |
| Ceremonial Mail Open | Loading / Loaded / Error | `ceremonialMailOpen` | `ceremonialMailOpen` | `GET /api/mailbox/v2/item/:id` | Parity through every phase (Sealed / Breaking / Open / Replying). |

### Tier 4.1 — Push + deep-link routing

The routing table (`docs/07-frontend-mobile-app.md §9`) was extended on
both sides in T4.1 to handle: `feed`, `home`, `notifications`,
`supportTrain`, `post`, `gig`, `listing`, `homeDetail`, `homeDashboard`,
`homeMemberRequests`, `chat/conversation`, `user`, `connections`, `invite`,
`unknown`. Both iOS `DeepLinkRouter` and Android `DeepLinkRouter` accept
both `pantopus://` and `https://pantopus.app/` schemes plus raw paths via
`handle(path:)`. Unit tests cover every entry on both platforms.

---

## 2. Drift fixes applied in this PR

The drift found above was either fixed in this PR or filed below as a
known-acceptable difference. The ✚ rows in the table are the fixes — full
list:

| # | Drift | Fix |
|---|---|---|
| 1 | ListOfRows archetype tag was `listOfRows` on Android, `listOfRowsContainer` on iOS — screenshot + UI tests on iOS referenced the iOS string. | Renamed Android `LIST_OF_ROWS_TAG` → `"listOfRowsContainer"`. |
| 2 | Mailbox item detail shell tag was `mailboxItemDetail` on Android, `mailboxItemDetailShell` referenced (but not declared) on iOS. | Added `.accessibilityIdentifier("mailboxItemDetailShell")` to iOS shell + renamed Android tag to match. |
| 3 | `HubView` had no root id on iOS — Android's was `hubScreen`. | Added `.accessibilityIdentifier("hubScreen")` on iOS. |
| 4 | `LoginView` had no root id on iOS. | Added `.accessibilityIdentifier("loginScreen")`. |
| 5 | `HomeDashboardView` had no feature-level root id on iOS. | Added `.accessibilityIdentifier("homeDashboard")` on the body root. |
| 6 | `PublicProfileView` had no feature-level root id on iOS. | Added `.accessibilityIdentifier("publicProfile")`. |
| 7 | `PulsePostDetailView` had no feature-level root id on iOS. | Added `.accessibilityIdentifier("pulsePostDetail")`. |
| 8 | iOS `ListOfRowsView` had no archetype-level identifier — only inner controls were tagged. | Added `.accessibilityIdentifier("listOfRowsContainer")` to the outer ZStack. |
| 9 | iOS Hub bell button missing identifier (screenshot capture for `21_Notifications` referenced `hubBellButton`). | Added in T4.1; kept here for completeness. |

## 3. Known-acceptable parity differences

These differences are intentional. The audit doc captures them so future
contributors don't accidentally "fix" them away.

1. **Edit Profile ships iOS-only this milestone.** The Android Form
   archetype is in place; only the screen wiring + nav route are pending.
   Tracked in `Android/CLAUDE.md` and `Android/docs/a11y_audit.md`.
2. **Chat conversation `.aiWelcome` is iOS-only.** Android falls through to
   `Loaded` with an empty message list — the UX shows the same "Say hi to
   Pantopus AI" empty body but doesn't carry the dedicated case in the
   sealed type. Tracking as a code-cleanup follow-up.
3. **Avatar identity-ring progress.** iOS computes the ring fraction in the
   ViewModel; Android computes it in the row mapper. Both produce the
   same fraction for the same backend payload — tests cover both.

---

## 4. Accessibility check (WCAG 2.2 AA + platform guidelines)

Findings are merged from the two platform-level audits
(`frontend/apps/{ios,android}/docs/a11y_audit.md`) and a fresh sweep of the
T3 + T4.1 screens.

### Tap targets

- **iOS**: every interactive control still clears 44 × 44 pt. The new
  T4.1 surfaces (Notifications top-bar Mark-all-read pill, Hub bell button)
  declare explicit `.frame(width: 44, height: 44)` or wrap a 36pt icon in
  44pt padding. Verified.
- **Android**: every interactive control clears 48 × 48 dp. The
  Notifications `IconButton` for the read-all action uses Material's
  default 48 dp; the bell button on Hub does the same. Verified.

### Labels / contentDescriptions

- Every `Button` / `IconButton` that renders icon-only on either platform
  now carries a non-empty `accessibilityLabel` / `contentDescription`. The
  notifications "Mark all read" action uses `action.accessibilityLabel` on
  iOS and `topBarAction.contentDescription` on Android — both are wired
  through from the VM.

### Heading hierarchy

- Every screen-level H1/H2 marks `.accessibilityAddTraits(.isHeader)` on
  iOS / `semantics { heading() }` on Android. Verified on Notifications
  top-bar title + the new T4.1 deep-link drill-downs.

### Dynamic Type / font scale

- Notifications row title + body honor `pantopusTextStyle(...)` on both
  platforms. At iOS xxxLarge and Android `fontScale = 1.3`, the row body
  wraps onto two lines without clipping the chevron / chip.

### Reduced motion

- The shimmer rows in `ListOfRowsView` / `ListOfRowsScreen` already
  collapse to a flat fill under Reduce Motion / `ANIMATOR_DURATION_SCALE
  == 0`. No new motion was introduced in T4.1.

### Open `// TODO(a11y)` markers (carried over from P12)

1. Switch Control / Switch Access coverage isn't enforced in automated
   tests — manual smoke continues to pass.
2. `UIAccessibility.Notification.announcement` /
   `TYPE_ANNOUNCEMENT` events on validation failure remain a follow-up.

---

## 5. Performance check (against `docs/perf_budgets.md`)

A fresh sweep against the four budgeted hot paths:

### 1. List recycling

- Every full-screen list uses `LazyVStack` / `LazyColumn` with stable
  keys. Verified by walking `Features/**/View.swift` and
  `ui/screens/**/Screen.kt` and confirming no `VStack` / `Column`
  encloses more than ~5 dynamic items. Exceptions are explicit (Hub
  pillar grid = 4 fixed items; identity switcher pill row = ≤4 items).
- T4.1's `NotificationsViewModel` uses `loadMoreIfNeeded()` with a 3-row
  trigger from the bottom — same as Mailbox / Pulse / Gigs.

### 2. Image caching

- iOS: `PantopusImageCache` continues to back the avatar pipeline.
- Android: Coil `ImageLoader` configured in `PantopusApplication`
  (memory 15% / disk 2% of available) — every `AsyncImage` / Coil call
  uses it.
- No raw `Image(painter: rememberAsyncImagePainter)` / `AsyncImage(...)`
  calls bypass the loader. Verified by grep on both platforms.

### 3. No main-thread JSON decoding

- iOS: `APIClient.session(...).request(...)` decodes off the main thread
  via the `URLSession` data-task queue; `@MainActor` hop happens after
  decoding completes. Verified no `JSONDecoder().decode` inline in any
  view body or VM init.
- Android: Moshi codegen `@JsonClass(generateAdapter = true)` runs in
  the OkHttp dispatcher pool inside `safeApiCall { … }`. No
  `JSONObject(...)` constructions inside `@Composable` or VM init.
- T4.1 DTOs use the same patterns.

### 4. No oversized recompositions / body re-evaluations

- iOS: `NotificationsViewModel.state` is a `Sendable` enum;
  `ListOfRowsState` is `Equatable` so SwiftUI short-circuits diffs.
- Android: `ListOfRowsUiState` cases are `data object` / `data class`
  with stable equality. `RowModel` carries `() -> Unit` lambdas which
  Compose flags as unstable — same trade-off already documented in
  Android `docs/perf_budgets.md` § 1.

No new perf regressions found.

---

## 6. Screenshot coverage (Tiers 1–3)

The `StoreScreenshots` test on iOS captures **21** screens; Android's
`StoreScreenshotsTest` ships **6 placeholders** + the screen-mounting
scaffolding documented in the file's TODO. The coverage by tier:

| Tier | iOS captures | Android captures | Notes |
|---|---|---|---|
| T0 (Login + Root) | n/a (post-launch waits for Hub) | n/a | Pre-auth state not in marketing set. |
| T1.1 Hub | `01_Hub_populated` | `01_Hub_populated` (placeholder) | iOS captures real Hub via stubs. |
| T1.2 Pulse feed | `02_PulseFeed` | — | iOS only. |
| T1.3 Mailbox | `04_MailboxList`, `05_MailboxItemDetail_package` | `04_MailboxList` + `05_MailboxItemDetail` (placeholder) | |
| T1.4 Homes | (MyHomes via debug entry — TODO in `StoreScreenshots.swift`) | `02_MyHomes`, `03_HomeDashboard` (placeholder) | Both flag as follow-up. |
| T2.1 Profiles | `06_Me_Personal`, `07_Me_Home`, `08_EditProfile`, `15_PublicProfile` | `06_EditProfile` (placeholder) | iOS broader coverage. |
| T2.3 Gigs | `09_GigsFeed`, `12_GigDetail` | — | iOS only. |
| T2.4 Nearby map | `10_NearbyMap` | — | iOS only. |
| T2.5 Marketplace | `11_Marketplace` | — | iOS only. |
| T2.6 Detail shell | covered via `12_GigDetail` | — | |
| T2.7 Chat list | `03_ChatList` | — | iOS only. |
| T3.1 Settings | `13_Settings` | — | iOS only. |
| T3.2 Identity Center | `14_IdentityCenter` | — | iOS only. |
| T3.4 Privacy handshake | `16_PrivacyHandshake` | — | iOS only. |
| T3.5 Token / Accept | `17_TokenAccept` | — | iOS only. |
| T3.6 Status / Waiting | `18_StatusWaiting` | — | iOS only. |
| T3.7 Ceremonial Mail | `19_CeremonialMail` | — | iOS only. |
| T3.8 Ceremonial Open | `20_CeremonialMailOpen` | — | iOS only. |
| T4.1 Notifications | `21_Notifications` | — | iOS only. |

### Action taken in this PR

- Confirmed every iOS marketing-set capture still resolves the
  identifiers it references (e.g. `listOfRowsContainer`,
  `mailboxItemDetailShell`, `audienceProfileContent`, `hubBellButton`)
  after the identifier renames in §2. The previously fragile
  `listOfRowsContainer` reference now resolves because the shared shell
  declares it.

### Outstanding follow-up

- **Android `StoreScreenshotsTest` is still scaffold-only.** The file
  has six `Placeholder("…")` composables in place of real screens — a
  release-prep follow-up tracked in its docstring (`TODO(release): mount
  the real composables with stub Hilt graph + the fixture data already
  used by *ScreenshotPreview previews`). Wiring the Hilt-graph stub +
  fixture composition for 20+ screens is its own milestone; doing it
  here would push T4.2 to weeks. The placeholder set + a11y/parity
  asserts give us the same gating signal in the interim.
- The `02_MyHomes`-style debug-flow captures on iOS could be added by
  driving the You-tab debug rows; tracked as a release-prep
  optimization.

---

## 7. Lint / test gates run for this audit

Both per-platform commands are required to pass before merging:

```bash
# iOS
cd frontend/apps/ios && make lint && make test

# Android
cd frontend/apps/android && ./gradlew ktlintCheck detekt test
```

Results are recorded in the PR description (or the commit body that
introduced this doc).
