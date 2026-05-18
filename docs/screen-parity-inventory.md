# Screen parity inventory — A08 per-screen batch 1

> **Source of truth:** every `.html` file in `A08 — per-screen batch 1/` at
> the repo root (61 designed screens). Each row pairs the designed screen
> with its iOS and Android implementation file, marks whether the route
> stack reaches a real View / `@Composable` (not `NotYetAvailableView` /
> `placeholder`), and lists which designed frames the current code does
> not render.
>
> **Generated:** 2026-05-18. Hand-audited by walking every screen's
> implementation files against the design pack's `label-num` /
> `label-title` (or `DCArtboard label="…"` for A17 mail variants).

## Summary

The inventory has **66 rows** covering all **61 designed `.html` files** —
some HTMLs map to multiple rows (e.g. `Content Detail.html` describes a
shell plus Pulse-post / Public-Beacon-profile / Home-dashboard concrete
screens; `Transactional Detail.html` covers a shell plus Gig / Listing /
Invoice). Six rows are shared shells (`List of Rows`, `Content Detail`,
`Form`, `Wizard`, `Transactional Detail`, `Map+List Hybrid (print)`)
which are not directly navigable; their reachability columns are `n/a`.

| Status (per platform) | iOS | Android |
|---|---:|---:|
| Fully shipped (reachable + all designed frames rendered) | 48 | 51 |
| Partially shipped (reachable, ≥1 designed frame missing or wrong target) | 6 | 6 |
| Reachable only in DEBUG (not production-routable) | 4 | 0 |
| Missing (no implementation or wired to `NotYetAvailableView`) | 2 | 3 |
| Shared shells / documentation rows (n/a) | 6 | 6 |
| **Total rows** | **66** | **66** |

Notes on the deltas:

- **iOS DEBUG-only reachability (4):** `Ceremonial Mail Compose`,
  `Ceremonial Mail Open`, `Privacy Handshake`, `Status / Waiting` exist
  as `View`s and compile, but the `YouRoute` cases that push them are
  fenced inside `#if DEBUG`. Android wires the first three as
  production routes (`CEREMONIAL_MAIL`, `CEREMONIAL_MAIL_OPEN`,
  `PRIVACY_HANDSHAKE`); `Status / Waiting` is the lone outlier — its
  composable exists but is not referenced from `RootTabScreen.kt`, so
  it is **missing** on Android.
- **Partially shipped (both platforms, same six rows):**
  - `Mailbox Item Detail` (A17 archetype) — newer A17 shell consumer
    exists but the `.mailItemDetail` route still resolves to the older
    `MailDetailView` / `MailDetailScreen`; `Coupon` body is unused.
  - `A17.1 Mail item (generic)` — minor: acknowledged-state visual
    treatment lacks the "porch" treatment from the design.
  - `A17.2 Booklet` — minor: grid view lacks the page-jump shortcut.
  - `Chat Conversation` — `FRAME 3 · AI ASSISTANT` is not rendered on
    either platform (the `aiAssistant` mode is declared in
    `ChatThreadMode` but has no UI entry point; backend SSE wiring
    `/api/ai/chat` is also pending).
  - `Creator Audience` — `FRAME 4 · BROADCAST DETAIL` is missing on
    both platforms (no `BroadcastDetailView` / `BroadcastDetailScreen`
    and no `/identity/broadcast/:id` route).
  - `Legal Static` — Android's `SETTINGS_LEGAL_CONTENT` falls back to
    `NotYetAvailableView` when a document key doesn't match an enum
    case (rather than a graceful "not found" state).
- **Missing on both (2):** `Creator inbox` and `Review claims` are
  designed but unbuilt; the existing `Creator Audience` Threads tab
  partially overlaps the former but does not implement the dedicated
  archetype, and `MyClaims` covers the claimant — not reviewer —
  perspective.
- **Android-only missing (1):** `Status / Waiting` — composable exists
  but no `ChildRoutes` constant references it; iOS at least has the
  DEBUG route.

Frame-coverage gaps (rendered in the "Missing states" column) call out
either:

1. A **designed variant** (e.g. Chat Conversation FRAME 3 · AI ASSISTANT,
   Creator Audience FRAME 4 · BROADCAST DETAIL) that the code has no
   render path for, OR
2. A **state the Definition of Done requires** (loading / empty / error)
   that the screen's `state` enum visibly omits.

Where the design pack only documents `POPULATED` + `EMPTY` but the
implementation also renders the four required DoD states (loading,
empty, populated, error), the "Missing states" cell is empty (the
implementation is a superset of the design).

## Inventory

Sorted by archetype, then by screen name.

| Screen | Archetype | Designed states | iOS path | iOS reachable | Android path | Android reachable | Missing states |
|---|---|---|---|---|---|---|---|
| Hub | A02 — Hub tab archetype | FRAME 1 POPULATED · FRAME 2 FIRST-RUN · FRAME 3 SKELETON | `frontend/apps/ios/Pantopus/Features/Hub/HubView.swift` | YES (Hub tab root) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/hub/HubScreen.kt` | YES (Hub tab root) | — |
| Auth | A05 — Auth archetype | FRAME 1 LOG IN · FRAME 2 CREATE ACCOUNT · FRAME 3 FORGOT PASSWORD · FRAME 4 RESET PASSWORD · FRAME 5 VERIFY EMAIL · FRAME 6 INPUT ERROR | `frontend/apps/ios/Pantopus/Features/Auth/LoginView.swift` + `Auth/Screens/{SignUpView,ForgotPasswordView,ResetPasswordView,VerifyEmailView,AuthErrorView}.swift` | YES (`AuthRouter.swift` pre-sign-in stack) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/auth/LoginScreen.kt` + `auth/{sign_up,forgot_password,reset_password,verify_email,auth_error}/…Screen.kt` | YES (`AuthNavHost.kt` pre-sign-in graph) | — |
| List of Rows (shell) | A08 — List-of-rows archetype | FRAME 1 PRIMARY · FRAME 2 VARIANT (category-grouped) · FRAME 3 VARIANT (avatar-first) · FRAME 4 EMPTY | `frontend/apps/ios/Pantopus/Features/Shared/ListOfRows/ListOfRowsView.swift` | n/a (shared shell, not a navigable screen) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/shared/list_of_rows/ListOfRowsScreen.kt` | n/a (shared shell) | — |
| Access codes | A08 — List of rows | FRAME 1 POPULATED · FRAME 2 EMPTY | `frontend/apps/ios/Pantopus/Features/Homes/AccessCodes/AccessCodesView.swift` | YES (`YouRoute.accessCodes(homeId:homeName:)`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/homes/accesscodes/AccessCodesScreen.kt` | YES (`ChildRoutes.ACCESS_CODES`) | — |
| Bills | A08 — List of rows | FRAME 1 POPULATED · FRAME 2 EMPTY | `frontend/apps/ios/Pantopus/Features/Homes/Bills/BillsListView.swift` | YES (`HubRoute.homeBills` / `YouRoute.homeBills`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/homes/bills/BillsListScreen.kt` | YES (`ChildRoutes.HOME_BILLS`) | — |
| Chat List | A08 — List of rows | FRAME 1 POPULATED · FRAME 2 EMPTY (verified-only) · FRAME 3 LOADING | `frontend/apps/ios/Pantopus/Features/Chat/ChatListView.swift` | YES (Inbox tab root) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/inbox/chat/ChatListScreen.kt` | YES (Inbox tab via `InboxScreen.kt`) | — |
| Connections | A08 — List of rows | FRAME 1 POPULATED (All tab · 5) · FRAME 2 EMPTY | `frontend/apps/ios/Pantopus/Features/Connections/ConnectionsView.swift` | YES (`HubRoute.connections` / `YouRoute.connections`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/connections/ConnectionsScreen.kt` | YES (`ChildRoutes.CONNECTIONS`) | — |
| Creator inbox | A08 — List of rows | FRAME 1 POPULATED (7 threads · 3 unread · 2 flagged) · FRAME 2 EMPTY | MISSING | NO | MISSING | NO | All frames — no `CreatorInbox` view/screen exists; the AudienceProfile Threads tab partially overlaps but does not implement the dedicated creator inbox archetype. |
| Discover businesses | A08 — List of rows | FRAME 1 POPULATED (3 categories · 7 businesses) · FRAME 2 EMPTY | `frontend/apps/ios/Pantopus/Features/DiscoverBusinesses/DiscoverBusinessesView.swift` | YES (`HubRoute.discoverBusinesses`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/discoverbusinesses/DiscoverBusinessesScreen.kt` | YES (`ChildRoutes.DISCOVER_BUSINESSES`) | — |
| Discover hub | A08 — List of rows | FRAME 1 POPULATED (4 sections) · FRAME 2 EMPTY | `frontend/apps/ios/Pantopus/Features/DiscoverHub/DiscoverHubView.swift` | YES (`HubRoute.discoverHub`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/discoverhub/DiscoverHubScreen.kt` | YES (`ChildRoutes.DISCOVER_HUB`) | — |
| Documents | A08 — List of rows | FRAME 1 POPULATED (All · 14 docs) · FRAME 2 EMPTY | `frontend/apps/ios/Pantopus/Features/Homes/Documents/DocumentsView.swift` | YES (`HubRoute.homeDocs` / `YouRoute.homeDocs`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/homes/documents/DocumentsScreen.kt` | YES (`ChildRoutes.HOME_DOCS`) | — |
| Emergency info | A08 — List of rows | FRAME 1 POPULATED (17 items · 1 needs review) · FRAME 2 EMPTY | `frontend/apps/ios/Pantopus/Features/Homes/Emergency/EmergencyInfoView.swift` | YES (`HubRoute.homeEmergency` / `YouRoute.homeEmergency`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/homes/emergency/EmergencyInfoScreen.kt` | YES (`ChildRoutes.HOME_EMERGENCY`) | — |
| Gigs | A08 — List of rows | FRAME 1 POPULATED (four-row category mix) · FRAME 2 EMPTY · FRAME 3 LOADING | `frontend/apps/ios/Pantopus/Features/Gigs/GigsFeedView.swift` | YES (`HubRoute.gigsFeed`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/gigs/GigsFeedScreen.kt` | YES (`ChildRoutes.GIGS_FEED`) | — |
| Home calendar | A08 — List of rows | FRAME 1 POPULATED (Week of Oct 12 · 10 events) · FRAME 2 EMPTY | `frontend/apps/ios/Pantopus/Features/Homes/Calendar/HomeCalendarView.swift` | YES (`HubRoute.homeCalendar` / `YouRoute.homeCalendar`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/homes/calendar/HomeCalendarScreen.kt` | YES (`ChildRoutes.HOME_CALENDAR`) | — |
| Household tasks | A08 — List of rows | FRAME 1 POPULATED (Active tab · 6 chores, day-grouped) · FRAME 2 EMPTY | `frontend/apps/ios/Pantopus/Features/Homes/Tasks/HouseholdTasksListView.swift` | YES (`HubRoute.homeTasks` / `YouRoute.homeTasks`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/homes/tasks/HouseholdTasksListScreen.kt` | YES (`ChildRoutes.HOME_TASKS`) | — |
| Listing offers | A08 — List of rows | FRAME 1 POPULATED (Credenza · 5 offers, leading $240) · FRAME 2 EMPTY | `frontend/apps/ios/Pantopus/Features/ListingOffers/ListingOffersView.swift` | YES (`HubRoute.listingOffers` / `YouRoute.listingOffers`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/listing_offers/ListingOffersScreen.kt` | YES (`ChildRoutes.LISTING_OFFERS`) | — |
| Mailbox Mobile | A08 — List of rows | FRAME 01 ME DRAWER (Incoming) · FRAME 02 BIZ DRAWER (Counter) · FRAME 03 EARN DRAWER (Empty) | `frontend/apps/ios/Pantopus/Features/Mailbox/MailboxDrawersView.swift` + `Mailbox/MailboxListView.swift` | YES (`HubRoute.mailboxDrawers` / `HubRoute.mailbox`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/mailbox/MailboxDrawersScreen.kt` + `mailbox/MailboxListScreen.kt` | YES (`ChildRoutes.MAILBOX_DRAWERS` / `MAILBOX_LIST`) | — |
| Maintenance | A08 — List of rows | FRAME 1 POPULATED (Scheduled tab · 6 tasks) · FRAME 2 EMPTY | `frontend/apps/ios/Pantopus/Features/Homes/Maintenance/MaintenanceListView.swift` | YES (`HubRoute.homeMaintenance` / `YouRoute.homeMaintenance`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/homes/maintenance/MaintenanceListScreen.kt` | YES (`ChildRoutes.HOME_MAINTENANCE`) | — |
| Marketplace | A08 — List of rows | FRAME 1 POPULATED (6-card 2-col grid) · FRAME 2 EMPTY · FRAME 3 LOADING | `frontend/apps/ios/Pantopus/Features/Marketplace/MarketplaceView.swift` | YES (`HubRoute.marketplace`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/marketplace/MarketplaceScreen.kt` | YES (`ChildRoutes.MARKETPLACE`) | — |
| Members | A08 — List of rows | FRAME 1 POPULATED (Members tab · 5 verified, 2 pending) · FRAME 2 EMPTY | `frontend/apps/ios/Pantopus/Features/Homes/Members/MembersListView.swift` | YES (`HubRoute.homeMembers` / `YouRoute.homeMembers`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/homes/members/MembersListScreen.kt` | YES (`ChildRoutes.HOME_MEMBERS`) | — |
| My bids | A08 — List of rows | FRAME 1 POPULATED (Active · 5 bids) · FRAME 2 EMPTY | `frontend/apps/ios/Pantopus/Features/MyBids/MyBidsView.swift` | YES (`HubRoute.myBids` / `YouRoute.myBids`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/my_bids/MyBidsScreen.kt` | YES (`ChildRoutes.MY_BIDS`) | — |
| My businesses | A08 — List of rows | FRAME 1 POPULATED (4 businesses · 1 primary · 1 pending) · FRAME 2 EMPTY | `frontend/apps/ios/Pantopus/Features/Businesses/MyBusinessesView.swift` | YES (`YouRoute.myBusinesses`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/businesses/MyBusinessesScreen.kt` | YES (`ChildRoutes.MY_BUSINESSES`) | — |
| My homes | A08 — List of rows | FRAME 1 POPULATED (4 homes across role spectrum) · FRAME 2 EMPTY | `frontend/apps/ios/Pantopus/Features/Homes/MyHomesListView.swift` | YES (`HubRoute.myHomes` / `YouRoute.myHomes`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/homes/MyHomesListScreen.kt` | YES (`ChildRoutes.MY_HOMES`) | — |
| My listings | A08 — List of rows | FRAME 1 POPULATED (Active · 5 listings) · FRAME 2 EMPTY | `frontend/apps/ios/Pantopus/Features/Listings/MyListingsView.swift` | YES (`YouRoute.myListings`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/listings/MyListingsScreen.kt` | YES (`ChildRoutes.MY_LISTINGS`) | — |
| My posts | A08 — List of rows | FRAME 1 POPULATED (Active · 4 posts) · FRAME 2 EMPTY | `frontend/apps/ios/Pantopus/Features/MyPosts/MyPostsView.swift` | YES (`YouRoute.myPosts`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/my_posts/MyPostsScreen.kt` | YES (`ChildRoutes.MY_POSTS`) | — |
| My tasks | A08 — List of rows | FRAME 1 POPULATED (Open · 5 tasks) · FRAME 2 EMPTY | `frontend/apps/ios/Pantopus/Features/MyTasks/MyTasksView.swift` | YES (`YouRoute.myTasks`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/my_tasks/MyTasksScreen.kt` | YES (`ChildRoutes.MY_TASKS`) | — |
| New message | A08 — List of rows | FRAME 1 POPULATED (3 sections · 215 contacts) · FRAME 2 EMPTY | `frontend/apps/ios/Pantopus/Features/Chat/NewMessage/NewMessageView.swift` | YES (`InboxRoute.compose`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/inbox/newmessage/NewMessageScreen.kt` | YES (`ChildRoutes.NEW_MESSAGE`) | — |
| Notifications | A08 — List of rows | FRAME 1 POPULATED (All · 4 unread, 3 earlier) · FRAME 2 EMPTY | `frontend/apps/ios/Pantopus/Features/Notifications/NotificationsView.swift` | YES (`HubRoute.notifications`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/notifications/NotificationsScreen.kt` | YES (`ChildRoutes.NOTIFICATIONS`) | — |
| Offers | A08 — List of rows | FRAME 1 POPULATED (Received · 5 offers) · FRAME 2 EMPTY | `frontend/apps/ios/Pantopus/Features/Offers/OffersView.swift` | YES (`YouRoute.offers`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/offers/OffersScreen.kt` | YES (`ChildRoutes.OFFERS`) | — |
| Owners | A08 — List of rows | FRAME 1 POPULATED (3 owners · 80% verified) · FRAME 2 EMPTY | `frontend/apps/ios/Pantopus/Features/Homes/Owners/OwnersListView.swift` | YES (`YouRoute.homeOwners`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/homes/owners/OwnersListScreen.kt` | YES (`ChildRoutes.HOME_OWNERS`) | — |
| Packages | A08 — List of rows | FRAME 1 POPULATED (Expected · 6 packages) · FRAME 2 EMPTY | `frontend/apps/ios/Pantopus/Features/Homes/Packages/PackagesListView.swift` | YES (`HubRoute.homePackages` / `YouRoute.homePackages`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/homes/packages/PackagesListScreen.kt` | YES (`ChildRoutes.HOME_PACKAGES`) | — |
| Pets | A08 — List of rows | FRAME 1 POPULATED (3 pets · dog/cat/bird) · FRAME 2 EMPTY | `frontend/apps/ios/Pantopus/Features/Homes/Pets/PetsListView.swift` | YES (`HubRoute.homePets` / `YouRoute.homePets`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/homes/pets/PetsListScreen.kt` | YES (`ChildRoutes.HOME_PETS`) | — |
| Polls | A08 — List of rows | FRAME 1 POPULATED (Active · 4 polls) · FRAME 2 EMPTY | `frontend/apps/ios/Pantopus/Features/Homes/Polls/PollsListView.swift` | YES (`HubRoute.homePolls` / `YouRoute.homePolls`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/homes/polls/PollsListScreen.kt` | YES (`ChildRoutes.HOME_POLLS`) | — |
| Pulse | A08 — List of rows | FRAME 1 POPULATED (mixed-intent feed) · FRAME 2 EMPTY · FRAME 3 LOADING | `frontend/apps/ios/Pantopus/Features/Feed/FeedView.swift` | YES (`HubRoute.pulseFeed`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/feed/FeedScreen.kt` | YES (`ChildRoutes.PULSE_FEED`) | — |
| Review claims | A08 — List of rows | FRAME 1 POPULATED (Pending · 4 claims) · FRAME 2 EMPTY | MISSING (only `MyClaimsListView.swift` exists — that is the *claimant* perspective; the *reviewer* queue is not built) | NO | MISSING (only `MyClaimsListScreen.kt` exists; no reviewer composable) | NO | All frames — needs a new HOA/board reviewer queue using ListOfRows. |
| Review signups | A08 — List of rows | FRAME 1 POPULATED (Chen meal train · 14 of 18 · 3 awaiting review) · FRAME 2 EMPTY | `frontend/apps/ios/Pantopus/Features/ReviewSignups/ReviewSignupsView.swift` | YES (`HubRoute.reviewSignups` / `YouRoute.reviewSignups` + deep link) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/review_signups/ReviewSignupsScreen.kt` | YES (`ChildRoutes.REVIEW_SIGNUPS`) | — |
| Support trains | A08 — List of rows | FRAME 1 POPULATED (My trains · 4 trains) · FRAME 2 EMPTY | `frontend/apps/ios/Pantopus/Features/SupportTrains/SupportTrainsView.swift` | YES (`HubRoute.supportTrains` / `YouRoute.supportTrains` + deep link) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/support_trains/SupportTrainsScreen.kt` | YES (`ChildRoutes.SUPPORT_TRAINS`) | — |
| Vault | A08 — List of rows | FRAME 1 POPULATED (5 folders · 38 items) · FRAME 2 EMPTY | `frontend/apps/ios/Pantopus/Features/Mailbox/Vault/VaultListView.swift` | YES (`HubRoute.mailboxVault`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/mailbox/vault/VaultListScreen.kt` | YES (`ChildRoutes.MAILBOX_VAULT`) | — |
| Content Detail (shell) | A10 — Content Detail archetype | FRAME 1 POST DETAIL · FRAME 2 PUBLIC PROFILE · FRAME 3 HOME DASHBOARD | `frontend/apps/ios/Pantopus/Features/Shared/ContentDetail/` (shell + Bodies/CTAs/Headers slots) | n/a (shared shell) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/shared/content_detail/` (matching layout) | n/a (shared shell) | — |
| Public Beacon Profile | A10 — Content Detail | FRAME 1 PERSONA · VISITOR · FRAME 2 PERSONA · OWNER · FRAME 3 LOCAL · VISITOR · FRAME 4 EMPTY (new persona) | `frontend/apps/ios/Pantopus/Features/Profile/PublicProfileView.swift` | YES (`HubRoute.publicProfile(userId:)` + DEBUG entry in YouRoute) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/profile/PublicProfileScreen.kt` | YES (`ChildRoutes.PUBLIC_PROFILE`) | — |
| Pulse post (Content Detail · post) | A10 — Content Detail | (covered as Content Detail FRAME 1) | `frontend/apps/ios/Pantopus/Features/Posts/PulsePostDetailView.swift` | YES (`HubRoute.pulsePost(postId:)`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/posts/PulsePostDetailScreen.kt` | YES (`ChildRoutes.PULSE_POST`) | — |
| Home dashboard (Content Detail · home) | A10 — Content Detail | (covered as Content Detail FRAME 3) | `frontend/apps/ios/Pantopus/Features/Homes/HomeDashboardView.swift` | YES (`HubRoute.homeDashboard(homeId:)` / `YouRoute.homeDashboard`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/homes/HomeDashboardScreen.kt` | YES (`ChildRoutes.HOME_DASHBOARD`) | — |
| Mailbox Item Detail (archetype) | A17 — Mail Item Detail | FRAME 1 PACKAGE · FRAME 2 COUPON · FRAME 3 BOOKLET · FRAME 4 CERTIFIED | `frontend/apps/ios/Pantopus/Features/Shared/MailItemDetail/MailItemDetailShell.swift` + `Features/Mailbox/ItemDetail/MailboxItemDetailView.swift` (uses shell; **not yet wired** into `.mailItemDetail` routes — the older `MailDetailView` still owns the route) | PARTIAL — shell + consumer exist but route still points at the older `Features/Mailbox/MailDetail/MailDetailView.swift`. Switching the route is required to use the A17 shell. | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/shared/mail_item_detail/MailItemDetailShell.kt` + `screens/mailbox/item_detail/MailboxItemDetailScreen.kt` (same status as iOS) | PARTIAL — same: shell exists, A17 consumer composable exists, but `ChildRoutes.MAILBOX_ITEM_DETAIL` still resolves to `MailDetailScreen.kt`. | Coupon body (`CouponBody.swift` / `CouponBody.kt`) is implemented but not exercised by the live route. Switching `.mailItemDetail` to `MailboxItemDetailView` unblocks all 4 frames. |
| A17.1 Mail item (generic) | A17 — Mail Item Detail variant | 01 OPEN (pre-acknowledgment) · 02 ACKNOWLEDGED (post-action) | `frontend/apps/ios/Pantopus/Features/Mailbox/MailDetail/MailDetailView.swift` (default generic body) | YES (`HubRoute.mailItemDetail` / `YouRoute.mailItemDetail`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/mailbox/mail_detail/MailDetailScreen.kt` | YES (`ChildRoutes.MAILBOX_ITEM_DETAIL`) | Acknowledged-state post-action affordance is rendered but lacks the distinct "porch" visual treatment from the design. |
| A17.2 Booklet | A17 — Mail Item Detail variant | 01 PAGE VIEW (cover, p.1 of 28) · 02 GRID VIEW (jump to page) | `frontend/apps/ios/Pantopus/Features/Mailbox/MailDetail/Variants/BookletDetailLayout.swift` + `Components/BookletPager.swift` | YES (mail detail route, category=booklet) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/mailbox/mail_detail/variants/BookletDetailLayout.kt` + `components/BookletPager.kt` | YES (mail detail route, category=booklet) | Grid view (FRAME 02) is rendered but lacks the page-jump shortcut affordance the design specifies. |
| A17.3 Certified mail | A17 — Mail Item Detail variant | 01 OPEN · 02 ACKNOWLEDGED (receipt added to chain) | `frontend/apps/ios/Pantopus/Features/Mailbox/MailDetail/Variants/CertifiedDetailLayout.swift` + `Components/CertifiedStampBadge.swift` | YES (mail detail route, category=certified) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/mailbox/mail_detail/variants/CertifiedDetailLayout.kt` + `components/CertifiedComponents.kt` | YES (mail detail route, category=certified) | — |
| A17.4 Community mail | A17 — Mail Item Detail variant | 01 OPEN (pre-RSVP) · 02 YOU'RE GOING (post-RSVP) | `frontend/apps/ios/Pantopus/Features/Mailbox/MailDetail/Variants/CommunityDetailLayout.swift` | YES (mail detail route, category=community) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/mailbox/mail_detail/variants/CommunityDetailLayout.kt` | YES (mail detail route, category=community) | — |
| Chat Conversation | Bespoke (Chat) | FRAME 1 POPULATED DM · FRAME 2 EMPTY (3 quick-start chips) · FRAME 3 AI ASSISTANT (Ask Pantopus · Beta) | `frontend/apps/ios/Pantopus/Features/Chat/Conversation/ChatConversationView.swift` (`ChatConversationViewModel` exposes a `ChatThreadMode.aiAssistant` case) | PARTIAL — DM mode reachable from Inbox tab; `aiAssistant` mode declared but no UI entry point or render path | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/inbox/conversation/ChatConversationScreen.kt` | PARTIAL — same as iOS; DM mode reachable, AI assistant mode unimplemented | FRAME 3 · AI ASSISTANT (no rendering on either platform — backend SSE wiring `/api/ai/chat` is also deferred per code comment) |
| Creator Audience | Bespoke (Profile tabs) | FRAME 1 UPDATES TAB · FRAME 2 FANS TAB · FRAME 3 INBOX TAB · FRAME 4 BROADCAST DETAIL | `frontend/apps/ios/Pantopus/Features/AudienceProfile/AudienceProfileView.swift` (3 tabs: Updates / Followers / Threads) | PARTIAL — top-3 tabs render; broadcast-detail sub-route not implemented | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/audience_profile/AudienceProfileScreen.kt` (3 tabs) | PARTIAL — same as iOS | FRAME 4 · BROADCAST DETAIL (no `BroadcastDetailView` / `BroadcastDetailScreen`; no `/identity/broadcast/:id` route) |
| Ceremonial Mail Compose | Wizard | FRAME 1 PORCH CALL · FRAME 2 ADDRESS IT · FRAME 3 WRITE IT · FRAME 4 SEAL & SEND | `frontend/apps/ios/Pantopus/Features/CeremonialMail/CeremonialMailWizardView.swift` | DEBUG-only on iOS (`YouRoute.ceremonialMail` lives behind `#if DEBUG`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/ceremonial_mail/CeremonialMailWizardScreen.kt` | YES (`ChildRoutes.CEREMONIAL_MAIL`) | iOS: not production-routable. |
| Ceremonial Mail Open | Wizard (animated) | FRAME 1 PORCH ARRIVAL · FRAME 2 OPENING · FRAME 3 READING · FRAME 4 REPLY | `frontend/apps/ios/Pantopus/Features/CeremonialMailOpen/CeremonialMailOpenView.swift` | DEBUG-only (`YouRoute.ceremonialMailOpen(mailId:)` is `#if DEBUG`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/ceremonial_mail_open/CeremonialMailOpenScreen.kt` | YES (`ChildRoutes.CEREMONIAL_MAIL_OPEN`) | iOS: not production-routable. |
| Form (shell) | Form archetype | FRAME 1 SIMPLE (Send invite) · FRAME 2 MULTI-SECTION (Edit profile) · FRAME 3 FIELD-HEAVY (Disambiguate mail) | `frontend/apps/ios/Pantopus/Features/Shared/Form/FormShell.swift` + `FormState.swift` | n/a (shared shell) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/shared/form/` | n/a (shared shell) | — |
| Identity Center | Form / hub composite | FRAME 1 POPULATED (all four identities) · FRAME 2 FIRST RUN · FRAME 3 SWITCHER (bottom sheet) | `frontend/apps/ios/Pantopus/Features/IdentityCenter/IdentityCenterView.swift` | YES (`YouRoute.identityCenter`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/identity_center/IdentityCenterScreen.kt` | YES (`ChildRoutes.IDENTITY_CENTER`) | — |
| Me | Bespoke (You tab) | FRAME 1 PERSONAL (default) · FRAME 2 HOME (identity-switched — 412 Birch Ln) | `frontend/apps/ios/Pantopus/Features/Me/MeView.swift` | YES (You tab root) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/you/YouScreen.kt` | YES (You tab root) | — |
| Settings | Grouped list | FRAME 1 MAIN INDEX · FRAME 2 TOGGLES (Notification prefs) · FRAME 3 MIXED (Privacy controls) | `frontend/apps/ios/Pantopus/Features/Settings/SettingsView.swift` + `Settings/SettingsViewModels.swift` (notification + privacy panels inline) | YES (`HubRoute.menu` / `YouRoute.settings`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/settings/SettingsScreens.kt` + per-screen Composables (Notifications, Privacy, Blocked, Password, Verification, Help, Legal, About) | YES (`ChildRoutes.MENU` + the `SETTINGS_*` children) | — |
| Legal Static | Static content | FRAME 1 LONG-FORM LEGAL DOC (Privacy Policy v3.2) | `frontend/apps/ios/Pantopus/Features/Settings/Legal/LegalContentView.swift` + `Legal/LegalIndexView.swift` | YES (reached from Settings → Legal) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/settings/legal/LegalScreens.kt` | YES (`ChildRoutes.SETTINGS_LEGAL` + `SETTINGS_LEGAL_CONTENT`) — note: `SETTINGS_LEGAL_CONTENT` falls back to `NotYetAvailableView` when the document key doesn't match an enum case | Doc-key fallback path on Android is `NotYetAvailableView` rather than a graceful "not found" state — acceptable but worth noting. |
| Map+List Hybrid | Map+List Hybrid archetype | FRAME 1 DEFAULT 40% (Rail) · FRAME 2 COLLAPSED 20% (Peek) · FRAME 3 EXPANDED 70% (List) | `frontend/apps/ios/Pantopus/Features/Shared/MapListHybrid/` (shared shell) + `Features/Nearby/NearbyMapView.swift` (consumer) | YES (`HubRoute.nearbyMapForGigs(categoryKey:)`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/shared/map_list_hybrid/` + `screens/nearby/map/NearbyMapScreen.kt` | YES (`ChildRoutes.NEARBY_MAP_FOR_GIGS`) | — |
| Map+List Hybrid (print) | Print variant | Same three sheet positions | (same files as above; print is a documentation variant for the design pack) | n/a (documentation only) | (same files as above) | n/a (documentation only) | — |
| Privacy Handshake | Wizard | FRAME 1 CHOOSE HANDLE · FRAME 2 TIER SELECT · FRAME 3 STRIPE HANDOFF · FRAME 4 ALREADY FOLLOWING | `frontend/apps/ios/Pantopus/Features/PrivacyHandshake/PrivacyHandshakeWizardView.swift` | DEBUG-only on iOS (`YouRoute.privacyHandshake(personaHandle:)` is `#if DEBUG`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/handshake/PrivacyHandshakeScreen.kt` | YES (`ChildRoutes.PRIVACY_HANDSHAKE`) | iOS: not production-routable from a normal tab. |
| Status / Waiting | Status archetype | FRAME 1 POST-SUBMIT (Claim submitted) · FRAME 2 PERSISTENT WAITING (Under review) · FRAME 3 VERIFY EMAIL | `frontend/apps/ios/Pantopus/Features/Status/StatusWaitingView.swift` | DEBUG-only (`YouRoute.statusWaiting` is `#if DEBUG`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/status/StatusWaitingScreen.kt` (the composable exists but **no `ChildRoutes` constant references it**, so it is not navigable from `RootTabScreen.kt`) | NO | iOS: not production-routable. Android: composable exists but no NavHost wiring. |
| Token / Accept | Token Accept archetype | FRAME 1 HOME INVITE · FRAME 2 BUSINESS SEAT · FRAME 3 GUEST PASS | `frontend/apps/ios/Pantopus/Features/TokenAccept/TokenAcceptView.swift` | YES — surfaced via `RootTabView.swift` fullScreenCover on deep-link `pantopus://invite/:token` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/token_accept/TokenAcceptScreen.kt` | YES (`ChildRoutes.TOKEN_ACCEPT`) | — |
| Transactional Detail (shell) | Transactional Detail archetype | FRAME 1 GIG DETAIL · FRAME 2 LISTING DETAIL · FRAME 3 INVOICE DETAIL | `frontend/apps/ios/Pantopus/Features/ContentDetail/ContentDetailShell.swift` (T2.6 bespoke shell) | n/a (shared shell) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/contentdetail/` | n/a (shared shell) | — |
| Gig detail (Transactional · gig) | Transactional Detail | (covered as TX FRAME 1) | `frontend/apps/ios/Pantopus/Features/ContentDetail/GigDetailView.swift` | YES (`HubRoute.gigDetail(gigId:)` / `YouRoute.gigDetail`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/contentdetail/GigDetailScreen.kt` | YES (`ChildRoutes.GIG_DETAIL`) | — |
| Listing detail (Transactional · listing) | Transactional Detail | (covered as TX FRAME 2) | `frontend/apps/ios/Pantopus/Features/ContentDetail/ListingDetailView.swift` | YES (`HubRoute.listingDetail(listingId:)` / `YouRoute.listingDetail`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/contentdetail/ListingDetailScreen.kt` | YES (`ChildRoutes.LISTING_DETAIL`) | — |
| Invoice detail (Transactional · invoice) | Transactional Detail | (covered as TX FRAME 3) | `frontend/apps/ios/Pantopus/Features/ContentDetail/InvoiceDetailView.swift` | YES (`HubRoute.invoiceDetail(invoiceId:)`) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/contentdetail/InvoiceDetailScreen.kt` | YES (`ChildRoutes.INVOICE_DETAIL`) | — |
| Wizard (shell) | Wizard archetype | FRAME 1 STEP 1 OF 3 · FRAME 2 STEP 2 OF 3 · FRAME 3 SUCCESS | `frontend/apps/ios/Pantopus/Features/Shared/Wizard/WizardShell.swift` + `Wizard/Blocks/` | n/a (shared shell) | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/shared/wizard/` (+ `wizard/blocks/`) | n/a (shared shell) | — |

## Spot-check audit notes

The following rows were opened and manually confirmed during the audit:

1. **Hub** — `frontend/apps/ios/Pantopus/Features/Hub/HubView.swift`
   exists; `HubTabRoot.swift` line 192 calls `HubView { intent in … }`
   and dispatches every action shown in `mobile-wiring-audit.md` §2.
   Android `HubScreen.kt` imported into `RootTabScreen.kt` line ~640.
2. **My homes** — both files exist; the iOS `MyHomesListView.swift`
   uses `ListOfRowsView` via `MyHomesDataSource`; reached from
   `HubRoute.myHomes` and `YouRoute.myHomes` (post T6.3f / P14).
3. **Mailbox Item Detail (A17 archetype)** — both the older
   `MailDetailView.swift` (wired into `.mailItemDetail`) and the newer
   `MailboxItemDetailView.swift` (T6.5a P19 shell consumer) exist.
   Route currently resolves to the older view; switching is required
   to use the A17 shell variants.
4. **Token / Accept** — `TokenAcceptView.swift` is mounted via
   `RootTabView.swift` line 91 as a `fullScreenCover` keyed on
   `pendingInviteToken`. Android wires it via
   `ChildRoutes.TOKEN_ACCEPT`.
5. **Chat Conversation** — `ChatConversationViewModel.swift` line ~23
   declares `enum ChatThreadMode { case room(id:); case aiAssistant }`
   with a code comment noting "no backend wiring today — SSE
   streaming via `/api/ai/chat` lands later". No View case renders
   `aiAssistant`; confirmed missing on both platforms.

## How to maintain this doc

When a new screen lands:

1. Add a row matching the design HTML's archetype.
2. Set iOS / Android paths to the new files.
3. Update `iOS reachable` / `Android reachable` based on whether the
   route enum / `ChildRoutes` reaches a real View / Composable (not
   `NotYetAvailableView`).
4. Recount the four summary numbers.
5. Cross-check `docs/mobile-wiring-audit.md` and
   `docs/mobile-parity-audit.md` so the three documents stay in sync.
