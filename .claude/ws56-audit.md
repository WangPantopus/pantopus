# WS5 + WS6 Read-Only Audit

Scope: native iOS/Android under `/Users/yingpengwang/pantopus/native/pantopus`, with RN at `/Users/yingpengwang/pantopus/reactNative/pantopus/frontend/apps/mobile` as functional reference. Plan source: `rn_to_native_parity_closure_e9f7d705.plan.md`.

**Verdict:** Several WS5/6 items can be fully closed in-repo (drawer rewires, Android home-drawer destinations, Mailbox orphan delete, P6.8 copy). Wallet tax/history screens, GDPR export, Firebase, live secrets, and AI history need credentials or product/backend decisions.

---

## Priority: fully closable in code (do these first)

| Rank | Item | Why |
|---:|---|---|
| 1 | **5.4 Help & Support** | `HelpCenterView` / `HelpCenterScreen` already ship; iOS drawer still stubs |
| 2 | **5.6 quick rewires** | Existing screens already exist; destinations mis-mapped |
| 3 | **6.4 Mailbox orphan cleanup** | Confirmed zero call sites for Drawers/List screens |
| 4 | **6.5 P6.8 error-copy** | Mechanical Android `ifBlank { "Couldn't load X." }` sweep |
| 5 | **5.5 ctx-strip TODO** | Already implemented; stale TODO comments only |
| 6 | **5.1 partial** | “All activity” / history can be ListOfRows over existing `/api/wallet/transactions` |

---

## Workstream 5

### 5.1 Wallet secondary surfaces (history, tax, insights, payout method)

**Current state**
- Wallet chrome is live: balance + transactions from `GET /api/wallet`, `/transactions`, `/pending-release`; withdraw + Stripe Connect onboarding wired (`WalletViewModel.swift`, Android `wallet/*`).
- **Payout method card + tax docs are still sample-filled** in live mapping (`makeContent` / `WalletMapper.kt` copy `WalletSampleData.populated` for `payoutMethod` + `taxDocs`).
- Secondary nav is stubbed on both platforms:
  - `Wallet history` / `Tax documents` / `All activity` → `NotYetAvailableView` / `ChildRoutes.placeholder(...)`
- RN `wallet.tsx` is simpler (balance + flat transaction list + withdraw); **no tax/history/insights screens** there either.
- Backend `backend/routes/wallet.js` has balance / withdraw / transactions / pending-release — **no tax-docs endpoint**.
- “Insights” in code is mostly **business-owner** (`onOpenInsights` → `"Insights"` placeholder), not a Wallet subpage. Beacon insights already has a real Hub route (`beaconInsights`).

**Key files**
- iOS: `Features/Wallet/WalletView.swift`, `WalletViewModel.swift`, `WalletMapper` logic in VM, `Components/TaxDocsRow.swift`, `Components/PayoutMethodCard.swift`
- Android: `ui/screens/wallet/WalletScreen.kt`, `WalletMapper.kt`, `WalletSampleData.kt`
- Wiring: `HubTabRoot.swift` ~2266–2268, `RootTabScreen.kt` ~3887–3893
- RN: `apps/mobile/src/app/wallet.tsx`, `packages/api/src/endpoints/wallet.ts`

| Sub-piece | Agent in-repo? | Needs external/product? |
|---|---|---|
| History / All activity ListOfRows | **Yes** (reuse transactions API; RN has no dedicated screen — use archetypes) | Product: one screen vs two |
| Payout method card from Connect status | **Partial** (status already fetched; map bank last4/brand if API returns it) | May need richer Connect account payload |
| Tax documents | No real screen without API | **Backend + product** (1099 design) |
| Business Insights | WS2 | Product/scope |

---

### 5.2 Verification center “Coming soon” rows

**Current state**
- Screen exists and is wired from Settings (email verified chip + Resend).
- Phone + Home address rows are **display-only** with `subtext: "Coming soon"` / chip `Not started`.
- Photo ID is `Optional` / “Used by business listings only” (not “Coming soon”).
- No dedicated `/me/verification-status`; hydrated from identity-center.

**Files**
- `Features/Settings/Verification/VerificationCenterViewModel.swift`
- `ui/screens/settings/verification/VerificationCenterViewModel.kt`
- Docs: `docs/mobile-parity-audit.md` Verification center row

| Option | Agent? | Needs |
|---|---|---|
| Hide Phone/Home rows until flows exist | **Yes** | Product: hide vs leave Coming soon |
| Wire real phone/address verification | No | **Product + backend flows** (postcard/home verify already separate screens) |

---

### 5.3 Data export (GDPR)

**Current state**
- Settings index row **“Data export”** → placeholder on both (`SettingsView.swift` case `.dataExport`, `RootTabScreen.kt` `SettingsRoute.DataExport`).
- Privacy “Download your data” / “Delete account” chevrons are **explicit no-ops** (`PrivacyViewModel.tapRow` comment: later prompt).
- Backend: account **delete** exists in `users.js`; **no data-export / GDPR ZIP route** found.
- RN: no export screen; privacy copy points users to `privacy@pantopus.com`.

**Files**
- `SettingsView.swift`, `SettingsViewModels.swift` / `.kt`
- `Features/Settings/Privacy/PrivacyViewModel.swift`
- Android privacy rows in `SettingsViewModels.kt` (~805+)

| | |
|---|---|
| Agent alone? | **No** for real export |
| Needs | **Backend job + schema** (parked since P8.5 / Q7); product whether mailto/support is enough for v1 |
| In-repo stopgap | Mailto `privacy@…` or hide row — product call |

---

### 5.4 Help & Support drawer

**Current state**
- **Help center UI already exists** (static FAQ + email CTA):
  - iOS `Features/Settings/Help/HelpCenterView.swift`
  - Android `ui/screens/settings/help/HelpCenterScreen.kt`
- Settings → Help is wired on both.
- You-tab `me.help` → `.helpCenter` (iOS).
- **Drawer gap:** iOS `HubTabRoot.route(forDrawer:)` maps `.helpSupport` → `.placeholder(label: "Help & Support")`.
- Android drawer already maps `HelpSupport` → `ChildRoutes.SETTINGS_HELP`.
- RN hamburger: Help → `/settings` (not a dedicated help screen).
- Email domain drift: Android help mailto `support@pantopus.app`; RN uses `support@pantopus.com`.

| | |
|---|---|
| Agent? | **Yes — fully closable** |
| Fix | Add `HubRoute.helpCenter` (or push existing Settings help), mirror Android; optionally align support email with product domain decision (6.2) |

---

### 5.5 Chat leftovers TODOs

**Current state**

| TODO | Reality |
|---|---|
| `TODO(ai-history)` / “render restored history” | Conversation **id** restored via `GET /api/ai/conversations`; **message bodies not persisted** (backend `ai.js` / `agentService` — provider `previous_response_id` only). Both platforms document this. |
| `TODO(ctx-strip)` in iOS `ChatConversationView.swift:822` | **Stale.** VM loads gig detail → `gigContext`; view renders `gigContextStrip`. Android same (`loadGigContextIfNeeded` + `GigContextStrip`). |

**Files**
- iOS: `ChatConversationViewModel.swift` ~521–527, `ChatConversationView.swift` ~822
- Android: `ChatConversationViewModel.kt` ~324–331, `ChatConversationScreen.kt`

| | |
|---|---|
| ctx-strip comment cleanup | **In-repo** |
| AI history restore | **Backend** (messages endpoint + persistence) — flag, don’t fake |
| Quota lock / fan-thread | Plan WS3.6, not 5.5 |

---

### 5.6 Full placeholder-route sweep (after assuming WS2–4 land)

Sources grepped: `HubTabRoot.swift`, `YouTabRoot.swift`, `TasksTabRoot.swift`, `RootTabScreen.kt`, `NavigationDrawerViewModel.swift` / `NavigationDrawer.kt`.

#### Assumed removed by WS2–4 (not listed as remaining WS5 debt)

**WS2 — Business:** Catalog, Business Chat, Reviews, Business Settings, Report business, Book, Business dashboard, (business) Insights, Messages from business profile, Create-business waitlist stubs.

**WS3 — Creator:** Change tier, Update payment, Membership cancelled, Request refund, Creator profile, Broadcast actions / Reply / Boost / Pin, Follower, Inbox settings, Identity / Local profile / Personal, Discover beacons, Compose/Post (beacon), Claim status (if covered).

**WS4 — Mail/Homes:** Photos, Trusted neighbors, Invite link, Home notifications, Leave home, Cancel claim, Member requests, Request correction, Export documents, Reply in English, Home drawer, mail task/TTS/vault stubs, waiting-room, 12 mail bodies.

#### Still stubbed after WS2–4 (user-reachable labels)

**WS5-owned / settings-wallet**
| Label | Platforms | Notes |
|---|---|---|
| Wallet history | iOS+Android | 5.1 |
| Tax documents | iOS+Android | 5.1 (+ Earn) |
| All activity | iOS+Android | 5.1 |
| Data export | iOS+Android | 5.3 |
| Help & Support | **iOS drawer only** | 5.4 — Android already live |
| Payments (Earn manage/add bank/cash out on **You** tab) | iOS You Earn | Hub Earn already → `paymentsSettings`; You still placeholders |

**Closeable rewires (existing screens)**
| Label | Gap | Target |
|---|---|---|
| Offers & Bids | iOS drawer → placeholder; Android → `OFFERS` | Add `HubRoute.offers` → `OffersView` (You already has it) |
| Discover Neighbors | Android drawer → placeholder; iOS → `.discoverHub` | `ChildRoutes.DISCOVER_HUB` |
| Android Home drawer: Tasks, Issues, Bills, Members, Mailbox, Packages, Documents, Emergency, Settings | All → `"Coming soon"` **even with homeId** | Mirror iOS: `homeTasks`, `homeMaintenance`, `homeBills`, `homeMembers`, `MAILBOX_ROOT`, `homePackages`, `homeDocs`, `homeEmergency`, `homeSettings` |
| jumpBackIn `/app/chat` → Messages | Both | Route to chat tab / inbox |

**Still deferred / product (not just rewires)**
| Label | Platforms | Notes |
|---|---|---|
| Vendors | iOS drawer (+ Android Coming soon) | No native vendors screen |
| Transaction detail | iOS+Android | From listing offers; payments detail; marketplace-adjacent |
| Earn help / Refer a neighbor / Offer a service / All earnings | both | Earn secondary CTAs |
| Support train: Claim a slot, Edit your slot, Send a card, Join as backup, Message host, Message helper, Train analytics, Edit dates, Invite helpers | both | No designed screens; chat shell could absorb Message * |
| Task detail (household) | both | List+edit only by design |
| AI draft: Open Marketplace to create listing / Open Pulse composer… / Draft | Tasks tab | Marketplace out of scope; post composer exists elsewhere |
| Me-tab home tiles without `homeId` | both | Defensive fallthrough — keep |
| Discovery `.unknown` → item.title | both | Defensive — keep |

**Parity note:** Android home drawer is far behind iOS for destinations that already have `ChildRoutes` — highest-value 5.6 code close.

---

## Workstream 6

### 6.1 Android `google-services.json` / FCM

**Current state**
- Committed file is an explicit stub (`project_id: pantopus-placeholder`, `_TODO` header). Comment: FCM will **not** deliver until replaced.
- App code is otherwise ready: `PantopusMessagingService`, `PushTokenSyncer`, `NotificationDispatcher`, `google-services` plugin in Gradle.
- iOS APNs path is wired; `APS_ENVIRONMENT` split in xcconfigs.
- Doc: `docs/push-native-migration.md` §8.1 release blocker.

| | |
|---|---|
| Agent in-repo? | Wire/verify routing only |
| Needs | **Firebase console** real `google-services.json` for `app.pantopus.android` + `.debug`; server FCM credentials |

---

### 6.2 Deep-link matrix + `pantopus.com` vs `pantopus.app`

**Current state**

| Layer | Domain |
|---|---|
| RN associated domains / intent filters | **`pantopus.com` / `www.pantopus.com`** (`app.json`) |
| Native iOS entitlements / `project.yml` | **`pantopus.app` / `www.pantopus.app`** |
| Native Android manifest | **`pantopus.app`** |
| Native `DeepLinkRouter` comments | Accept `pantopus://` + `https://pantopus.app/…` |
| Share URLs mixed | Gig → `.app`; posts/beacon often → `.com` |

**Router coverage (native both, custom scheme)** — strong for gigs/posts/homes/mailbox/wallet/auth/invite/businesses/chat/user/…  

**RN aliases called out in plan — native gaps:**
| Path | Native today |
|---|---|
| `/gigs/[id]`, `/posts/[id]`, `/invite/[token]` | Yes |
| `/u/[username]` | Only `user`/`users` by id — **no `/u/` alias** |
| `/join/[code]` | **Missing** |
| `/persona/[handle]`, `/@handle` | **Missing** (beacons feed exists; handle route not) |
| `/broadcast/[id]` | Treated as post? **No explicit `broadcast` segment** |
| `/b/[username]` | Singular `business/:username` exists; confirm `/b/` alias |

| | |
|---|---|
| Add path aliases in both routers | **In-repo** once canonical domain chosen |
| Domain + AASA / Digital Asset Links hosting | **Product + infra** (pick `.com` vs `.app`, publish apple-app-site-association / assetlinks) |
| Entitlements/intent-filters for both hosts | In-repo after decision |

---

### 6.3 Production secrets `REPLACE_ME`

**Current state**
- iOS Release default: `STRIPE_PUBLISHABLE_KEY = pk_live_REPLACE_ME` (`Config/Pantopus.Release.xcconfig`); Debug/Staging `pk_test_REPLACE_ME`.
- Overlay: `#include? "Secrets.xcconfig"` (CI via `make env-to-xcconfig`). Local `Secrets.xcconfig` may hold a test key + empty `SENTRY_DSN`.
- Android: `envOr("STRIPE_PUBLISHABLE_KEY", "pk_test_REPLACE_ME")`; release guard `-Ppantopus.requireProdConfig=true`.
- PostHog / Maps / Sentry empty unless env-provided.
- Stripe bootstrap skips keys containing `REPLACE_ME`.

| | |
|---|---|
| Agent? | Can verify guards / fail-closed behavior |
| Needs | **CI/console secrets**: live Stripe, Sentry DSN, PostHog, Maps, Match password, etc. Do not commit live keys |

---

### 6.4 Dead-code MailboxDrawers / List orphan cleanup

**Current state**
- Live entry: `MailboxRootView` / `MailboxRootScreen` from Hub/You.
- Orphans (deprecated, ship-readiness + file headers):
  - iOS: `MailboxDrawersView.swift` (`@available(*, deprecated)`), `MailboxListView.swift` (+ Drawers/List ViewModels if unused by Root)
  - Android: `MailboxDrawersScreen.kt`, `MailboxListScreen.kt`, `MailboxDrawersViewModel.kt`
- **Keep** `MailboxListViewModel.makeRow` on Android (and iOS equivalent) — still used by `MailboxRoot*` and search.
- Analytics still defines `screen.mailbox_list/drawers.viewed` — remove with screens.

| | |
|---|---|
| Agent? | **Yes — fully closable** |
| Caution | Delete Screens/Views first; preserve `makeRow` helpers; drop unused analytics cases; update `nav-graph-closure.md` |

---

### 6.5 Error-copy normalization (P6.8)

**Current state**
- Tracked in `docs/cross-platform-diff.md` as `P6.8-followup-error-copy`.
- Pattern: iOS `errorDescription ?? "Couldn't load X."`; Android often raw `result.error.message` with no blank fallback (~30 screens).
- Some true DELTAs remain (offline guards, hardcode vs server message).
- Invite-owner 409 already resolved as P6.8.

| | |
|---|---|
| Agent? | **Yes — fully closable** (mechanical; needs compile/test env) |
| Scope | Prefer Android `message.ifBlank { "Couldn't load X." }` per screen; reconcile remaining DELTA rows in same pass |

---

## Recommended close order (WS5–6 only)

1. **5.4** Help drawer → HelpCenter (iOS)  
2. **5.6 rewires** Offers & Bids (iOS), Discover Neighbors (Android), Android home-drawer → existing `ChildRoutes`, jumpBackIn chat, You Earn → Payments  
3. **6.4** Delete Mailbox Drawers/List orphans (keep `makeRow`)  
4. **6.5** Error-copy sweep  
5. **5.5** Delete stale ctx-strip TODOs; file backend ticket for AI history  
6. **5.1** Build history/all-activity from transactions; improve payout card from Connect; park tax behind API  
7. **5.2** Product: hide Coming soon vs leave  
8. **5.3** Backend GDPR export (or hide/mailto)  
9. **6.2** Domain decision → dual-host entitlements + missing aliases  
10. **6.1 / 6.3** Human: Firebase JSON + CI live secrets  

RN is not the place to implement these native stubs; use it for endpoint/flow parity. For screens with no design frame (export, wallet history), plan says compose from shared archetypes — do not copy RN layouts.