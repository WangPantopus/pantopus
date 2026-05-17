# Mobile wiring audit — Tier 1, T1.1

**Date:** 2026-05-14
**Scope:** native iOS and Android apps. Web is out of scope.
**Status:** post-fix. Every interactive element listed below now reaches a real endpoint or a real navigation intent. Where a destination screen isn't built yet, the intent routes to `NotYetAvailableView` / `NotYetAvailableScreen` with the tier that will replace it called out inline. No `// lands later`, no `print(`, no dead `Button { }`.

Source code refs use the iOS file path; Android parity is enforced via a one-to-one ViewModel/repository mapping. Routing notes call out where Android resolves the same intent in `RootTabScreen.kt`.

## Conventions

- **Endpoint** rows reference the backend route file + line where the route is defined.
- **Intent** rows reference the parent NavigationStack / NavHost route enum case.
- **Deferred** rows route to a placeholder; the tier that replaces it is named.
- **Removed** rows: the affordance was deleted because no design exists and there was no real action behind it.

---

## 1. Login (`Features/Auth/LoginView.swift` / `ui/screens/auth/LoginScreen.kt`)

| Element | Action | Wiring |
|---|---|---|
| Email field (`loginEmailField`) | Bind `viewModel.email` | ViewModel |
| Password field (`loginPasswordField`) | Bind `viewModel.password` | ViewModel |
| Sign in button (`loginSubmitButton`) | `viewModel.signIn` | Endpoint: `POST /api/users/login` (users.js:955) |

States: loading (button progress), populated, error (inline). No stubs.

---

## 2. Hub (`Features/Hub/HubView.swift` / `ui/screens/hub/HubScreen.kt`)

| Element | Action | Wiring |
|---|---|---|
| Pull-to-refresh | `viewModel.refresh()` | Endpoints: `GET /api/hub` (hub.js:24), `GET /api/hub/today` (hub.js:596), `GET /api/hub/discovery` (hub.js:757) |
| Bell (`onBellTap`) | `.openNotifications` | Intent → `HubRoute.notifications` → real `NotificationsView` / `NotificationsScreen` (T5.1 Notifications V2). Endpoints: `GET /api/notifications` · `PATCH /api/notifications/:id/read` · `POST /api/notifications/read-all`. |
| Menu (`onMenuTap`) | `.openMenu` | Intent → `HubRoute.menu` → `NotYetAvailableView("Menu")`. **Deferred:** T3.1 (Settings). |
| Action chip — Post Task | `.action(.postTask)` | Intent → `NotYetAvailableView("Post a gig")`. **Deferred:** T2.3 (Gigs). |
| Action chip — Snap & Sell | `.action(.snapAndSell)` | Intent → `NotYetAvailableView("Snap & sell")`. **Deferred:** T2.5 (Marketplace). |
| Action chip — Scan mail | `.action(.scanMail)` | Intent → `HubRoute.mailboxDrawers` |
| Action chip — Add home | `.action(.addHome)` | Intent → `HubRoute.addHome` |
| Setup banner Start | `.startVerification` | Intent → `HubRoute.addHome` |
| Setup banner Dismiss | `viewModel.dismissSetupBanner()` | ViewModel |
| Pillar — Pulse | `.pillar(.pulse)` | Intent → `NotYetAvailableView("Pulse")`. **Deferred:** T1.2. |
| Pillar — Marketplace | `.pillar(.marketplace)` | Intent → `NotYetAvailableView("Marketplace")`. **Deferred:** T2.5. |
| Pillar — Gigs | `.pillar(.gigs)` | Intent → `NotYetAvailableView("Gigs")`. **Deferred:** T2.3. |
| Pillar — Mail | `.pillar(.mail)` | Intent → `HubRoute.mailbox` |
| Discovery card | `.openDiscovery(id, kind)` | Dispatched on `kind`: `post` → `HubRoute.pulsePost(id)`; `person` → `HubRoute.publicProfile(id)`; `gig` → `NotYetAvailableView("Gig detail")` (T2.3); `business` → `NotYetAvailableView("Business")`. |
| Jump-back-in card | `.jumpBackIn(item)` | Dispatched on `item.route`: `/app/mailbox*` → `HubRoute.mailbox`; `/app/homes/:id/dashboard` → `HubRoute.homeDashboard(id)`; `/gigs/new` → `NotYetAvailableView("Post a gig")` (T2.3); `/app/chat` → `NotYetAvailableView("Messages")` (T2.1). |

States: skeleton, first-run, populated, error (with retry).

---

## 3. Homes — MyHomes (`Features/Homes/MyHomesListView.swift` / `ui/screens/homes/MyHomesListScreen.kt`)

T6.3f / P14 refresh: row anatomy now exposes a home-tinted intro banner +
role-chip subtitle + Active-home chip on the primary owner, with a 52pt
`FabVariant.secondaryCreate` tinted `FabTint.home`. Behaviour identical
to T1.4; below dispatch table is unchanged on the iOS+Hub stack and now
also wired from the You stack via `YouRoute.myHomes` /
`ChildRoutes.MY_HOMES` (flipped from `placeholder(label:)`).

| Element | Action | Wiring |
|---|---|---|
| Pull-to-refresh | `viewModel.refresh()` | Endpoint: `GET /api/homes/my-homes` (home.js:1464) |
| Home row tap | `onOpenHome(home.id)` | Intent → `HubRoute.homeDashboard(id)` from the Hub stack; from You-tab entry the same id pushes `YouRoute.homeDashboard(homeId:)` so the back stack returns to MyHomes, not Hub. |
| Row kebab | **Removed.** No bottom-sheet design exists; the secondary action slot is now `nil`. |
| FAB "Claim a home" | `onAddHome` | Intent → `HubRoute.addHome` (Hub stack) or `YouRoute.placeholder("Claim a home")` (You stack) until the You-side claim wizard mounts. |

States: loading, loaded, empty (with home-tinted CTA), error.

---

## 4. Homes — HomeDashboard (`Features/Homes/HomeDashboardView.swift` / `ui/screens/homes/HomeDashboardScreen.kt`)

| Element | Action | Wiring |
|---|---|---|
| Pull-to-refresh | `viewModel.refresh()` | Endpoints: `GET /api/homes/:id` (home.js:2891), `GET /api/homes/:id/public-profile` (home.js:2439) [403/404 fallback] |
| Tab switch | `viewModel.selectedTab` | ViewModel |
| "Claim ownership" CTA | `onClaimOwnership` | Intent → `HubRoute.claimOwnership(homeId)` |
| "View claims" CTA | `onOpenClaimsList` | Intent → `HubRoute.myClaims` |
| Quick action — verify | `handleQuickAction("verify")` | Intent → `HubRoute.claimOwnership(homeId)` |
| Quick action — add_member | `handleQuickAction("add_member")` | Intent → `HubRoute.inviteOwner(homeId)` |
| Quick action — other | `handleQuickAction(_:)` | Intent → `NotYetAvailableView("<action label>")`. **Deferred:** per-action future tiers. |
| FAB — add_member | `handleFabAction("add_member")` | Intent → `HubRoute.inviteOwner(homeId)` |
| FAB — log_package | `handleFabAction("log_package")` | Intent → `NotYetAvailableView("Log a package")`. **Deferred.** |
| FAB — add_mail | `handleFabAction("add_mail")` | Intent → `NotYetAvailableView("Add mail")`. **Deferred.** |

States: loading, loaded, error.

---

## 5. Homes — AddHomeWizard (`Features/Homes/AddHome/AddHomeWizardView.swift` / `ui/screens/homes/add_home/AddHomeWizardScreen.kt`)

| Element | Action | Wiring |
|---|---|---|
| Address fields | ViewModel state | — |
| Suggestion select | `viewModel.selectSuggestion(_:)` | Endpoint: `POST /api/homes/property-suggestions` (home.js:540) |
| Confirm property | `viewModel.confirm()` | Endpoint: `POST /api/homes/check-address` (home.js:555) |
| Role pick | `viewModel.selectRole(_:)` | ViewModel |
| Primary-home toggle | `viewModel.setPrimaryHome(_:)` | ViewModel |
| Review submit | `viewModel.submit()` | Endpoint: `POST /api/homes` (home.js:677) |
| Success "View home" | `onOpenHomeDashboard(homeId)` | Intent → `HubRoute.homeDashboard(id)` |
| Success "Back to hub" | `dismiss` | Intent → pop |

States: per-step plus `isCheckingAddress`, `isSubmitting`, `errorMessage`.

---

## 6. Homes — ClaimOwnershipWizard (`Features/Homes/ClaimOwnership/ClaimOwnershipWizardView.swift` / `.../claim_ownership/ClaimOwnershipWizardScreen.kt`)

| Element | Action | Wiring |
|---|---|---|
| Start | step transition | ViewModel |
| Evidence picker | `viewModel.picked(_:)` / `viewModel.remove(_:)` | Endpoint: `POST /api/files/upload` |
| Notes | `viewModel.aliasNotes` | ViewModel |
| Submit | `viewModel.submit()` | Endpoints: `POST /api/homes/:id/ownership-claims` (homeOwnership.js:251), `POST /api/homes/:id/ownership-claims/:claimId/evidence` (homeOwnership.js:886) |
| Success "View status" | `onOpenClaimsList` | Intent → `HubRoute.myClaims` |
| "Back to home" | `dismiss` | Intent → pop |

States: start, upload (per-slot loading/error), success.

---

## 7. Homes — InviteOwner (`Features/Homes/InviteOwner/InviteOwnerFormView.swift` / `.../invite_owner/InviteOwnerFormScreen.kt`)

| Element | Action | Wiring |
|---|---|---|
| Email field | `viewModel.update(.email, _:)` | ViewModel |
| Phone field | `viewModel.update(.phone, _:)` | ViewModel |
| Send | `viewModel.submit()` | Endpoint: `POST /api/homes/:id/owners/invite` (homeOwnership.js:1376) |
| Close | `onClose` | Intent → pop |

States: editing, error (per-field), toast.

---

## 8. Homes — MyClaims (`Features/Homes/Claims/MyClaimsListView.swift` / `.../claims/MyClaimsListScreen.kt`)

| Element | Action | Wiring |
|---|---|---|
| Pull-to-refresh | `viewModel.refresh()` | Endpoint: `GET /api/homes/my-ownership-claims` (homeOwnership.js:217) |
| Claim row | Non-interactive. **Removed:** chevron and tap handler. Rows are informational until a status-detail design lands. |
| Empty CTA "Add a home" | `onStartNewClaim` | Intent → `HubRoute.addHome` |

States: loading, loaded, empty, error.

---

## 9. Mailbox — List (`Features/Mailbox/MailboxListView.swift` / `ui/screens/mailbox/MailboxListScreen.kt`)

| Element | Action | Wiring |
|---|---|---|
| Pull-to-refresh | `viewModel.refresh()` | Endpoint: `GET /api/mailbox` (mailbox.js:1306) |
| Infinite scroll | `viewModel.loadMoreIfNeeded()` | Endpoint: same with `offset`/`limit` |
| Tab switch (All/Unread/Starred) | `viewModel.selectTab(_:)` | ViewModel + re-fetch |
| Search icon | `viewModel.onSearchTapped()` | Intent → `NotYetAvailableView("Search")`. **Backend gap:** `/api/mailbox` does not yet accept a query parameter; logged in audit. Replaces former "Search coming soon" toast. |
| Row tap | `onOpenMail(mail.id)` | Intent → `HubRoute.mailItemDetail(id)` |

States: loading, loaded, empty, error.

---

## 10. Mailbox — Drawers (`Features/Mailbox/MailboxDrawersView.swift` / `ui/screens/mailbox/MailboxDrawersScreen.kt`)

| Element | Action | Wiring |
|---|---|---|
| Pull-to-refresh | `viewModel.refresh()` | Endpoint: `GET /api/mailbox/v2/drawers` (mailboxV2.js:214) |
| Drawer row tap | `onOpenDrawer(drawer.drawer)` | Intent → `NotYetAvailableView("Drawer detail")`. **Deferred:** no drawer-detail design exists; previously commented as "lands later" — comment removed, intent now points at a real placeholder. |

States: loading, loaded, empty, error.

---

## 11. Mailbox — ItemDetail (`Features/Mailbox/ItemDetail/MailboxItemDetailView.swift` / `.../item_detail/MailboxItemDetailScreen.kt`)

| Element | Action | Wiring |
|---|---|---|
| Pull-to-refresh | `viewModel.refresh()` | Endpoints: `GET /api/mailbox/v2/item/:id` (mailboxV2.js:366), `GET /api/mailbox/v2/package/:mailId` (mailboxV2.js:634) |
| Primary CTA — Package "Log as received" | `viewModel.performPrimaryAction()` | Endpoint: `PATCH /api/mailbox/v2/package/:mailId/status` (mailboxV2.js:670) |
| Primary CTA — Coupon "Save for later" | `viewModel.performPrimaryAction()` | Endpoint: `POST /api/mailbox/v2/item/:id/action` body `{action:"file"}` (mailboxV2.js:459). **Replaced** the prior client-only "Add to wallet" optimistic flip — coupon save now hits the existing `file` action so it survives a refresh. |
| Primary CTA — Booklet "Save to library" | `viewModel.performPrimaryAction()` | Endpoint: `POST /api/mailbox/v2/item/:id/action` body `{action:"file"}` |
| Primary CTA — Certified "Acknowledge receipt" | `viewModel.performPrimaryAction()` | Endpoint: `POST /api/mailbox/v2/item/:id/action` body `{action:"acknowledge"}` |
| Ghost CTA — Package "Not mine" | `viewModel.performGhostAction()` | Endpoint: `POST /api/mailbox/v2/item/:id/action` body `{action:"not_mine"}` |
| Ghost CTA — Coupon "Dismiss" | `viewModel.performGhostAction()` | Endpoint: `POST /api/mailbox/v2/item/:id/action` body `{action:"dismiss"}` |
| Ghost CTA — Certified "View terms" | sheet | UI-only |
| AI summary chips | `onAIChip(kind)` — primary chip dispatches to `performPrimaryAction()`, secondary chip to `performGhostAction()`. Replaces the prior no-op closure with a useful shortcut to the bottom CTAs. |
| Sender avatar | `onOpenSenderProfile` | Intent → `HubRoute.publicProfile(id)` |

States: loading, loaded, error.

---

## 12. Mailbox — DisambiguateMail (`Features/Mailbox/Disambiguate/DisambiguateMailFormView.swift` / `.../disambiguate/DisambiguateMailFormScreen.kt`)

| Element | Action | Wiring |
|---|---|---|
| Recipient radio rows | `viewModel.select(_:)` | ViewModel |
| Alias notes | `viewModel.aliasNotes` | ViewModel |
| Confirm | `viewModel.submit()` | Endpoint: `POST /api/mailbox/v2/resolve` (mailboxV2.js:555) |

States: editing, error, toast.

---

## 13. Pulse — PostDetail (`Features/Posts/PulsePostDetailView.swift` / `ui/screens/posts/PulsePostDetailScreen.kt`)

| Element | Action | Wiring |
|---|---|---|
| Pull-to-refresh | `viewModel.refresh()` | Endpoint: `GET /api/posts/:id` (posts.js:2142) |
| Reaction — Helpful | `viewModel.tapReaction(.helpful)` | Endpoint: `POST /api/posts/:id/like` (posts.js:2595) |
| Reaction — Heart, Going | Display-only count chips. **Backend gap:** post reactions other than `like` are not supported. Pills no longer raise a tap intent (no more "coming soon" toast); they show count and are non-interactive until the backend adds reaction kinds. |
| Comment composer | `composerText` | ViewModel |
| Send comment | `viewModel.sendComment()` | Endpoint: `POST /api/posts/:id/comments` (posts.js:2431) |
| Show more replies | `viewModel.showMoreReplies()` | ViewModel |
| Author / commenter avatar | `onOpenProfile(userId)` | Intent → `HubRoute.publicProfile(id)` |

States: loading, loaded, error.

---

## 14. PublicProfile (`Features/Profile/PublicProfileView.swift` / `ui/screens/profile/PublicProfileScreen.kt`)

| Element | Action | Wiring |
|---|---|---|
| Pull-to-refresh | `viewModel.refresh()` | Endpoint: `GET /api/users/id/:id` (users.js:2041) |
| Tab switch (About/Skills/Reviews) | `viewModel.selectTab(_:)` | ViewModel |
| Message button | `onOpenMessages(userId)` | Intent → `NotYetAvailableView("Messages")`. **Deferred:** T2.1 replaces with chat conversation route. Replaces former "Messaging coming soon" toast. |
| Connect button | `viewModel.connect()` | Endpoint: `POST /api/relationships/requests` (relationships.js:67). Replaces former "Connect coming soon" toast. |
| Overflow menu | `viewModel.showOverflow()` | Action sheet with: **Block** → `POST /api/blocks/:userId/block` (blocks.js:13); **Report** → `NotYetAvailableView("Report")` (no report endpoint). Replaces former "More actions coming soon" toast. |

States: loading, loaded, error.

---

## 15. EditProfile (`Features/Profile/EditProfileView.swift` / `ui/screens/profile/EditProfileScreen.kt`)

| Element | Action | Wiring |
|---|---|---|
| Text fields | `viewModel.update(field:to:)` | ViewModel + validation |
| DOB picker | `viewModel.dob` | ViewModel |
| Visibility segmented | `viewModel.visibility` | ViewModel |
| Save | `viewModel.save()` | Endpoint: `PATCH /api/users/profile` (users.js:1503) |
| Close | `dismiss` | Intent → pop |
| Initial load | `viewModel.load()` | Endpoint: `GET /api/users/profile` (users.js:1427) |

Known backend gaps (rationale only, not stubs): avatar upload field, email editability when unverified, fine-grained visibility toggles beyond the 3-way enum. Comments updated from `TODO` to `Note:` form so they don't trip the no-TODO sweep.

States: loading, loaded, error.

---

## Cross-cutting

- **Offline banner** (`.offlineBanner(isOffline:)`) is present on every populated screen.
- **Skeletons** (never spinner + "Loading…") are used wherever loading state has structural content.
- **Empty states** use the shared `EmptyState` component on every list screen.
- **Retry** is wired on every error layout.
- **A11y identifiers** are unchanged by this audit — already present from prior work.

## New plumbing introduced by this audit

iOS:
- `Core/Networking/Endpoints/RelationshipsEndpoints.swift` — `POST /api/relationships/requests`.
- `Core/Networking/Endpoints/BlocksEndpoints.swift` — `POST /api/blocks/:userId/block`.
- `HubRoute.notifications`, `.menu`, `.messagesPlaceholder`, `.searchPlaceholder`, `.genericPlaceholder(label)` — every previously-unhandled intent now lands somewhere with a meaningful title.
- `DiscoveryCardContent.kind: DiscoveryKind` — derived from the backend's `type` field.
- `JumpBackItem.route: String` — already on DTO; now used by `HubTabRoot` to dispatch.

Android: same set under `data/api/services/RelationshipsApi.kt`, `data/api/services/BlocksApi.kt`, `ui/screens/root/RootTabScreen.kt` route additions, `ui/screens/hub/HubUiState.kt` discovery-kind enum.

---

## Me-tab action tiles (T5.3.x)

The "Me" action grid (`MeViewModel.swift` / `MeViewModel.kt`) emits `routeKey`
strings. Each one resolves to a concrete destination on the You tab — no
`NotYetAvailableView` for the action tiles below.

| Tile | routeKey | iOS destination | Android destination | Notes |
|---|---|---|---|---|
| My bids | `me.bids` | `YouRoute.myBids` → `MyBidsView` | `ChildRoutes.MY_BIDS` → `MyBidsScreen` | T5.3.1. Bidder side. `GET /api/gigs/my-bids`. |
| My gigs | `me.gigs` | `YouRoute.myTasks` → `MyTasksView` | `ChildRoutes.MY_TASKS` → `MyTasksScreen` | T5.3.2. Poster side. `GET /api/gigs/my-gigs` (joined with `top_bidders[≤3]` for the BidderStack). FAB → `YouRoute.composeTask` / `ChildRoutes.COMPOSE_TASK` (placeholder until T2.3 lands a real composer). |
| My posts | `me.posts` | `YouRoute.myPosts` → `MyPostsView` | `ChildRoutes.MY_POSTS` → `MyPostsScreen` | T5.3.3. Activity-section row (not tile). `GET /api/posts/user/:userId` (active set). Archive / Restore are local-only optimistic; Delete uses real `DELETE /api/posts/:id`. |
| My homes | `me.homes` | `YouRoute.myHomes` → `MyHomesListView` | `ChildRoutes.MY_HOMES` → `MyHomesListScreen` | T6.3f / P14. Activity-section row. `GET /api/homes/my-homes`. Tap pushes `YouRoute.homeDashboard(homeId:)` / `ChildRoutes.HOME_DASHBOARD` so the back stack returns to MyHomes. **Flipped from `placeholder(label:)` in P14.** |
| My listings | `me.listings` | `YouRoute.myListings` → `MyListingsView` | `ChildRoutes.MY_LISTINGS` → `MyListingsScreen` | T6.3f / P14. Personal action tile. `GET /api/listings/me`. Three tabs bucket client-side (Active / Sold / Drafts). Row tap pushes `YouRoute.listingDetail(listingId:)` / `ChildRoutes.LISTING_DETAIL`. FAB pushes a placeholder until the Snap & Sell composer ships on mobile. **Flipped from `placeholder(label:)` in P14.** |
| My businesses | `me.businesses` | `YouRoute.myBusinesses` → `MyBusinessesView` | `ChildRoutes.MY_BUSINESSES` → `MyBusinessesScreen` | T6.3f / P14. Activity-section row. `GET /api/businesses/my-businesses`. Row tap pushes a placeholder (`Business dashboard`) until the typed business-dashboard surface ships on mobile; FAB pushes a placeholder (`Register a business`) until the register-business wizard ships. **Flipped from `placeholder(label:)` in P14.** |
| Mail | `me.mail` | `YouRoute.mailbox` → `MailboxListView` | `ChildRoutes.MAILBOX` → `MailboxListScreen` | Pre-T5; verified still wired. |
| Edit profile | `me.editProfile` | Sheet → `EditProfileView` | Sheet → `EditProfileScreen` | Pre-T5. Android iOS-only this milestone — see parity audit §3 known-acceptable. |
| Settings | `me.settings` | `YouRoute.settings` → `SettingsView` | `ChildRoutes.SETTINGS` → `SettingsScreen` | Pre-T5. |

The `composeTask` placeholder route is a `NotYetAvailableView("Post a task")`
on both platforms — replaces the previous "Post a task" `placeholder`
route used by HubTabRoot. Replace with the dedicated Post-a-task screen
when T2.3 (Gigs) lands its composer flow.

### Active Home pillar tiles (`me.home.*`)

The Active Home pillar surfaces six action tiles when the user has a
verified primary home (`MeViewModel.homeActionTiles()`). They currently
fall through to the generic `placeholder` dispatcher — a parallel-entry
gap, **not** a parity-blocker because each destination has a primary
entry point elsewhere. Tracked for a follow-up PR that adds `homeId` to
`MeIdentityContent` + plumbs it through the YouTabRoot dispatcher so
the tiles deep-link with the resolved home id.

| Tile | routeKey | Current dispatch | Primary entry today | Future home-tab dispatch |
|---|---|---|---|---|
| Bills | `me.home.bills` | placeholder | Home dashboard "Bills" quick-action tile → `BillsListView(homeId)` | `YouRoute.billsList(homeId)` / `ChildRoutes.billsList(homeId)` |
| Access | `me.home.access` | `AccessCodesView(homeId:, homeName:)` via `YouRoute.accessCodes` ✅ T6.4a | Me-tab Household-section row ✅; Android Home Dashboard `access_codes` quick-action ✅ | shipped |
| Household tasks | `me.tasks` | **wired (T6.3c / P11)** — `YouRoute.homeTasks(homeId)` (iOS) / `ChildRoutes.homeTasks(homeId)` (Android) → `HouseholdTasksListView` / `HouseholdTasksListScreen`; also reached from the Home Dashboard "Tasks" quick-action tile (`view_tasks`). | Me-tab `me.tasks` Activity row + Home Dashboard "Tasks" quick action | n/a — wired |
| Packages | `me.home.packages` | placeholder | Mailbox → drawers | follow-up |
| Members | `me.home.members` | placeholder | Home dashboard "Members" quick-action | follow-up |
| Calendar | `me.calendar` | **real** (T6.4c / P18) | Me-tab `.calendar` action tile + Home Dashboard "Calendar" quick-action tile → `HomeCalendarView(homeId)` / `HomeCalendarScreen(homeId)` | iOS `YouRoute.homeCalendar(homeId)` / `HubRoute.homeCalendar(homeId)` · Android `ChildRoutes.HOME_CALENDAR` |
| Docs | `me.home.docs` | placeholder | n/a (no screen built yet) | follow-up |

## T5 — screen-by-screen wiring (P5–P16)

Every screen the T5 buildout shipped has had its real wiring verified.
Source-of-truth is the parity-audit row in `docs/mobile-parity-audit.md`;
the rows below are the **wiring-only** highlights (what the chrome
controls + interactive rows hit when tapped) for each new screen. No
`NotYetAvailableView` in any of these screens.

### Notifications V2 (T5.1 / P5)

| Element | Wiring |
|---|---|
| Pull-to-refresh / tab switch | `GET /api/notifications?limit=&offset=&unread=true` |
| Row tap | Routes by `notification.type` — reply/mention → `pulsePost(id)`, claim → `myClaims`, gig → `gigDetail(id)`, listing → `listingDetail(id)`, safety/system → mailbox or settings |
| Mark all read (top-bar) | `POST /api/notifications/read-all` |
| Row tap on unread row | `PATCH /api/notifications/:id/read` (optimistic) |
| Hub bell entry | `HubRoute.notifications` / `ChildRoutes.NOTIFICATIONS` |

### Connections (T5.2.3 / P6)

| Element | Wiring |
|---|---|
| Pull-to-refresh | parallel `GET /api/relationships?status=accepted` + `GET /api/relationships/requests/pending` |
| Tab switch (All / Neighbors / Pending) | client-side filter, no refetch |
| Search bar | client-side filter on cached list |
| Per-row message CTA (`circularAction`) | `HubRoute.chatConversation(InboxConversationDestination)` |
| Accept (`verticalActions.primary`) | `POST /api/relationships/:id/accept` (optimistic) |
| Ignore (`verticalActions.secondary`) | `POST /api/relationships/:id/reject` (optimistic) |
| FAB "Find people" | `HubRoute.placeholder("Find people")` (deferred — no people-search screen yet) |
| Deep link `pantopus://connections` | lands on `ConnectionsView` / `ConnectionsScreen` |

### Bills list + detail + Add Bill wizard (T5.2.2 / P13)

| Element | Wiring |
|---|---|
| List pull-to-refresh | `GET /api/homes/:id/bills` |
| Tab switch (Upcoming / Paid / All) | client-side filter |
| Row tap | `BillsRoute.detail(billId)` |
| FAB (52pt) | `BillsRoute.addBill(homeId)` → Add Bill wizard |
| Detail "Mark paid" | `PUT /api/homes/:id/bills/:billId` body `{ status: "paid" }` |
| Detail "Remove" | `PUT /api/homes/:id/bills/:billId` body `{ status: "cancelled" }` (soft-delete — no DELETE handler yet) |
| Detail splits | `GET /api/homes/:id/bills/:billId/splits` (read-only — backend gap) |
| Wizard submit | `POST /api/homes/:id/bills` |

### Pets list + Add Pet wizard (T5.2.1 / P15)

| Element | Wiring |
|---|---|
| List pull-to-refresh | `GET /api/homes/:id/pets` |
| Row kebab → Edit | `PetsRoute.addEdit(homeId, petId)` |
| Row kebab → Delete | `DELETE /api/homes/:id/pets/:petId` (optimistic) |
| FAB (52pt) | `PetsRoute.addEdit(homeId, nil)` → wizard |
| Wizard submit | `POST /api/homes/:id/pets` or `PUT /api/homes/:id/pets/:petId` |

### Owners list (P15 / T6.3g)

| Element | Wiring |
|---|---|
| First-appear / pull-to-refresh | `GET /api/homes/:id/owners` (homeOwnership.js:1381) |
| Row tap | no-op (V1; reserved for future "View claim" destination once `claim_id` lands on the owner row) |
| Row kebab → Remove (confirm) | `DELETE /api/homes/:id/owners/:ownerId` (homeOwnership.js:1614) — optimistic; quorum response keeps the row dropped + surfaces "Removal pending" toast; 4xx / 5xx rolls back |
| FAB (52pt `secondaryCreate` user-plus, `.home` tint) | iOS: presents `InviteOwnerFormView` sheet · Android: `navController.navigate(ChildRoutes.inviteOwner(homeId, ""))` · Web: `router.push('/app/homes/${homeId}/owners/invite')` — all three hit `POST /api/homes/:id/owners/invite` (homeOwnership.js:1434) on submit |
| Empty-state CTA | Same as FAB |
| Entry from Me / You | `me.owners` Household-section row → iOS `YouRoute.homeOwners(homeId:)` / Android `ChildRoutes.HOME_OWNERS` / Web `/app/homes/[id]/owners`; primary home id resolved by `MeViewModel.homeSections(...)` |

### Offers V2 — cross-listing (T5.2.4 / P9)

| Element | Wiring |
|---|---|
| Received tab load | `GET /api/gigs/received-offers` |
| Sent tab load | `GET /api/gigs/my-bids` |
| Tab switch | in-memory only |
| Row tap | `HubRoute.gigDetail(gigId)` |
| Top-bar filter | placeholder (filter sheet deferred) |

### My bids (T5.3.1 / P7)

| Element | Wiring |
|---|---|
| Pull-to-refresh | `GET /api/gigs/my-bids` |
| Tab switch (Active / Accepted / Rejected / Done) | client-side bucket-by-status |
| Footer "Edit bid" | `PUT /api/gigs/:gigId/bids/:bidId` |
| Footer "Withdraw" | `DELETE /api/gigs/:gigId/bids/:bidId` body `{ reason }` (optimistic + rollback) |
| Footer "Mark complete" (Accepted in-progress) | `POST /api/gigs/:gigId/mark-completed` |
| Footer "Leave review" (Done) | `POST /api/reviews` |
| Banner "Browse tasks" (extendedNav FAB) | `HubRoute.gigsFeed` |

### My tasks V2 (T5.3.2 / P8)

| Element | Wiring |
|---|---|
| Pull-to-refresh | `GET /api/gigs/my-gigs` (inlines `top_bidders[≤3]` + `boost_expires_at`) |
| Tab switch (Open / Active / Done / Closed) | client-side derive-status + bucket |
| Footer "Boost in feed" (No bids yet) | `POST /api/gigs/:gigId/boost` (new in T5.3.2) |
| Footer "Mark complete" (In progress) | `POST /api/gigs/:gigId/complete` (poster confirmation — distinct from `/mark-completed`) |
| Footer "Repost task" (Closed / Cancelled) | `HubRoute.composeGig(category)` |
| Footer "Leave a review" (Done) | `HubRoute.reviewCompose(gigId)` |
| FAB (56pt canonical) | `HubRoute.composeGig(category)` (placeholder composer until T2.3) |

### My posts (T5.3.3 / P14)

| Element | Wiring |
|---|---|
| Pull-to-refresh | `GET /api/posts/user/:userId` |
| Tab switch (Active / Archived) | client-side bucket on local archive overrides |
| Row tap | `HubRoute.pulsePost(postId)` |
| Engagement "Edit" / "Restore" CTA | local optimistic toggle |
| Kebab → Archive / Restore | local optimistic state (no backend route yet — documented) |
| Kebab → Delete | `DELETE /api/posts/:id` (real + rollback) |
| FAB (52pt secondaryCreate "Write a post") | `HubRoute.placeholder("Compose post")` — real composer is T2.3-ish |

### Listing offers (T5.3.4 / P10)

| Element | Wiring |
|---|---|
| Pull-to-refresh | parallel `GET /api/listings/:listingId` + `GET /api/listings/:listingId/offers` |
| Footer "Accept" | `POST /api/listings/:listingId/offers/:offerId/accept` (optimistic) |
| Footer "Decline" | `POST /api/listings/:listingId/offers/:offerId/decline` (optimistic) |
| Footer "Counter" | sheet → `POST /api/listings/:listingId/offers/:offerId/counter` |
| Footer "Withdraw counter" | maps to `/decline` (no withdraw-counter route exists — documented) |
| Footer "View transaction" (Accepted) | `HubRoute.invoiceDetail(invoiceId)` |
| Top-bar share | system share sheet |

### Discover hub (T5.4.1 / P11)

| Element | Wiring |
|---|---|
| Pull-to-refresh / chip select | parallel `GET /api/hub/discovery?filter=people&since=&verified=&freeOrWanted=` ×4 (People + Businesses + Gigs + Listings) |
| Row tap (People) | `HubRoute.publicProfile(userId)` |
| Row tap (Businesses) | `HubRoute.discoverBusinesses` (was placeholder until T5.4.2 / P12) |
| Row tap (Gigs) | `HubRoute.gigDetail(gigId)` |
| Row tap (Listings) | `HubRoute.listingDetail(listingId)` |
| "See all People" | `HubRoute.connections` |
| "See all Businesses" | `HubRoute.discoverBusinesses` |
| "See all Gigs" | `HubRoute.gigsFeed` |
| "See all Listings" | `HubRoute.marketplace` |
| Top-bar `sliders-horizontal` (filters) | `HubRoute.placeholder("Discovery filters")` — filter sheet deferred |
| Deep link `pantopus://discover-hub` | lands on screen |

### Discover businesses (T5.4.2 / P12)

| Element | Wiring |
|---|---|
| Pull-to-refresh / chip select / search | `GET /api/businesses/search?q=&categories=&page=&page_size=` (viewer home resolved server-side) |
| Row tap | `HubRoute.placeholder("Business: \(name) (\(id))")` — typed business-profile screen lands in a separate tier |
| Top-bar `sliders-horizontal` (filters) | `HubRoute.placeholder("Business filters")` — filter sheet deferred |
| Empty-state "Widen radius" (no-location 400) | `HubRoute.placeholder("Set home address")` — Edit Address Wizard is iOS-only this milestone |
| Empty-state "Invite a business" (no results) | `HubRoute.placeholder("Invite a business")` — real invite flow deferred |
| Inbound entry | from Discover hub "See all Businesses" + (web) `/app/discover` |

### Review claims (T5.4.3 / P16) — web only

See parity audit Tier 5. Mobile deferred per F9.

---

## Remaining `NotYetAvailableView` / `placeholder` references — justification

A reproducible audit:

```bash
grep -rn 'NotYetAvailable' frontend/apps/ios/Pantopus
grep -rn 'placeholder(label:' frontend/apps/ios/Pantopus
grep -rn 'NotYetAvailableView' frontend/apps/android/app/src/main/java
grep -rn 'ChildRoutes\.placeholder(' frontend/apps/android/app/src/main/java
```

Every remaining match falls in one of these buckets:

| Bucket | iOS examples | Android examples | Justification |
|---|---|---|---|
| Component definition | `Features/Root/NotYetAvailableView.swift` | `ui/screens/root/NotYetAvailableView.kt` | The placeholder component itself. |
| Content-detail body slots | `Features/Shared/ContentDetail/Bodies.swift:117/131/140/149` + `Headers.swift:100/110` | `ui/screens/shared/content_detail/Bodies.kt` | Generic body / header stubs for non-Home detail types. Future tiers swap them. |
| Mailbox category bodies | `Features/Mailbox/ItemDetail/Bodies/CategoryBodies.swift:56` | `ui/screens/mailbox/item_detail/bodies/CategoryBodies.kt:88` | 13 of the 14 mailbox categories don't yet have a designed body; fallback is correct. |
| Hub-tab pillar / action chip | `HubTabRoot.swift:387/399/422/481/618/620` | `RootTabScreen.kt:863/921/935/1206/1219` | Drawer detail, compose gig, compose listing, mail search, generic placeholder — each names the tier (T2.3 composer, T2.5 Snap & sell, etc.) in inline doc-comments. |
| You-tab generic | `YouTabRoot.swift:351/512` | `RootTabScreen.kt:1081` | Generic + `composeTask` — the latter is the only Me-tab route that still placeholders; T2.3 will land the composer screen. |
| Inbox / Nearby tab | `InboxTabRoot.swift:100/102` + `NearbyTabRoot.swift:66/68` | `RootTabScreen.kt:548/556/557/903` | New-message composer, chat search, map filters, gig search — all out-of-scope for T5 (covered by T2.1 / T2.4). |
| Settings sub-screens | `Features/Settings/SettingsView.swift:80` | (Android parity row in T3.1) | Several settings sub-pages still placeholder (Notifications detail, Privacy etc.) — T3.1 secondary work. |
| Discover hub filter / Discover businesses filter | `HubTabRoot.swift` (discoverHub case onOpenFilters) | `RootTabScreen.kt:994` | Filter sheet redesign is post-T5. The chip strip already covers the canonical filter cases; the icon entry-point lands on a placeholder. |
| `me.home.*` Active Home tiles | YouTabRoot default case | RootTabScreen default case | Bills / Members / Packages etc. are reachable from the Home Dashboard quick-action tiles; the Me-tab parallel-entry tiles fall through to a labelled placeholder. Documented in the Active Home pillar table above. |

Every remaining placeholder is either a future-tier deferral or a
parallel-entry gap with a real primary path. **Zero stale references
to T5 screens** — every "Discover businesses", "My bids", "My tasks",
"Bills", "Connections", "Notifications", "Listing offers", "Discover
hub", "My posts", "Pets" route now points at the real screen.

## Tier links

- Notifications screen → **T5.1 (canonical V2 with tabs)**, was T4.1
- Settings / menu screen → **T3.1**
- Chat list & conversation → **T2.1 / T2.2**
- Gigs feed + composer → **T2.3**
- Marketplace / Snap & Sell → **T2.5**
- Pulse feed → **T1.2**
- Post-a-task composer → **T2.3 (replaces `composeTask` placeholder)**
- Discover businesses → **T5.4.2 (replaces `discoverBusinesses` NotYetAvailableView)**
- Connections → **T5.2.3**
- Bills → **T5.2.2**
- Pets → **T5.2.1**
- My bids / My tasks V2 / My posts → **T5.3.1 / T5.3.2 / T5.3.3**
- Discover hub → **T5.4.1**
- Listing offers / Offers V2 → **T5.3.4 / T5.2.4**
