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

| Element | Action | Wiring |
|---|---|---|
| Pull-to-refresh | `viewModel.refresh()` | Endpoint: `GET /api/homes/my-homes` (home.js:1464) |
| Home row tap | `onOpenHome(home.id)` | Intent → `HubRoute.homeDashboard(id)` |
| Row kebab | **Removed.** No bottom-sheet design exists; the secondary action slot is now `nil`. |
| FAB "Claim a home" | `onAddHome` | Intent → `HubRoute.addHome` |

States: loading, loaded, empty, error.

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
| My gigs | `me.gigs` | `YouRoute.myTasks` → `MyTasksView` | `ChildRoutes.MY_TASKS` → `MyTasksScreen` | **T5.3.2 (this PR).** Poster side. `GET /api/gigs/my-gigs` (now joined with `top_bidders[≤3]` for the BidderStack). FAB → `YouRoute.composeTask` / `ChildRoutes.COMPOSE_TASK` (placeholder until T2.3 lands a real composer). |

The `composeTask` placeholder route is a `NotYetAvailableView("Post a task")`
on both platforms — replaces the previous "Post a task" `placeholder`
route used by HubTabRoot. Replace with the dedicated Post-a-task screen
when T2.3 (Gigs) lands its composer flow.

## Tier links

- Notifications screen → **T4.1**
- Settings / menu screen → **T3.1**
- Chat list & conversation → **T2.1 / T2.2**
- Gigs → **T2.3**
- Marketplace / Snap & Sell → **T2.5**
- Pulse feed → **T1.2**
- Post-a-task composer → **T2.3 (replaces `composeTask` placeholder)**
