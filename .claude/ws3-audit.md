# Workstream 3 — Creator / persona monetization audit

Source of truth for the checklist: [rn_to_native_parity_closure_e9f7d705.plan.md](file:///Users/yingpengwang/.cursor/plans/rn_to_native_parity_closure_e9f7d705.plan.md) §Workstream 3.

**Cross-cutting correction:** Persona paid join is **Stripe Checkout in the system browser**, not PaymentSheet. PaymentSheet (`Core/Payments/*`, `StripePaymentSheets.kt`, RN `usePaymentSheet`) is for saved cards / gigs / invoices. RN explicitly avoids StoreKit/Play Billing for persona subs (`follow.tsx` comments).

---

## 3.1 Subscribe / paid-tier join

| Surface | Status | Files / symbols |
|---|---|---|
| BeaconProfile free Follow → handshake | **REAL** | iOS `BeaconProfileViewModel.follow()` → `showFollowHandshake`; sheet `PrivacyHandshakeWizardView` (`BeaconProfileView.swift` ~61–68). Android `BeaconProfileViewModel.follow()` → `onFollowHandshake` → `PrivacyHandshakeScreen`. |
| Handshake paid tier → Checkout URL | **REAL** | iOS `PrivacyHandshakeViewModel` ~300–301 → `.opensCheckout` → `openURL`. Android `PrivacyHandshakeViewModel` ~314–316 → `Intent.ACTION_VIEW`. |
| Locked broadcast “Subscribe to unlock” | **STUB** | iOS `BeaconProfileView.swift:172` toast `"Subscribe flow coming soon"`; `PublicProfileView.swift:179` same. Android `BeaconProfileViewModel.showSubscribeToast()` / `PublicProfileViewModel.showSubscribeToast()`. |
| Beacon Tiers tab Join CTA | **STUB / display-only** | `BeaconTiersSection` lists price ladder, no join action (`BeaconProfileSections.swift:304+`; Android `BeaconProfileSections.kt` ~304+). |
| PublicProfile Follow | **WRONG WIRE** | `PublicProfileViewModel.follow()` posts `RelationshipsEndpoints.sendRequest` (connection request), not persona handshake (`PublicProfileViewModel.swift:241–265`). Android mirror same pattern. |
| RN reference | **REAL** | `persona/[personaHandle]/follow.tsx` → `followPersonaWithHandshake` → `WebBrowser.openBrowserAsync(subscribeUrl)`. |

**Backend (file:line)**
- `POST /api/personas/:id/follow` — `backend/routes/personas.js:1345` (handshake); paid branch Checkout session `personas.js:1522–1589` via `personaPaymentsService.createCheckoutSession`
- `GET /api/personas/:handle/tiers` — `personas.js:1111`
- `GET /api/personas/:handle/fan-handle-suggestion` — `personas.js:1303`
- Feature gate: `requireFeatureFlag('audience_profile')` on membership router; RN also has `personaPaidMemberships` flag (default off in prod)

**Design frames**
- Beacon/persona: `docs/designs/A21/a21-1-persona-frames.jsx`, `docs/design/new/uploads/a21-1-persona-frames.jsx`, `docs/designs/A03/beacons-frames.jsx`
- Handshake UX mirrored from web/RN; no dedicated native PaymentSheet frame for persona

**Blockers**
1. Unlock CTA must open handshake with **preselected paid tier** (post’s `target_tier_rank`), not toast.
2. PublicProfile Follow must use persona handshake, not relationships.
3. `personaPaidMemberships` / Stripe Connect readiness for creators (`personaPayments.js:56` onboard).
4. Universal-link return after Checkout still ops-dependent (RN comment).

---

## 3.2 Membership billing management

| Action | Native | RN | Backend |
|---|---|---|---|
| Load membership | **REAL** | REAL | `GET …/membership` `personaMembership.js:108` |
| Cancel | **REAL** (API) | REAL | `POST …/cancel` `:204` |
| Cancel nav “Membership cancelled” | **PLACEHOLDER** host route | — | — |
| Change tier | **PLACEHOLDER** `YouTabRoot.swift:978–979` / Android `RootTabScreen.kt:4145` | **REAL** upgrade/downgrade in `audience/membership/[personaId]/index.tsx:111–114` | `POST …/upgrade` `:121`, `…/downgrade` `:162` |
| Update payment | **PLACEHOLDER** | **ABSENT** (no RN UI) | No dedicated portal endpoint in personaMembership |
| Request refund | **PLACEHOLDER** host; SLA banner preview-only | **REAL** `requestRefund` | `POST …/refund-request` `:251` |

**Native API surface incomplete:** `MembershipEndpoints.swift` / `MembershipApi.kt` only expose GET + cancel. No upgrade/downgrade/refund clients.

**Design:** `docs/designs/A10/membership-frames.jsx`, `docs/designs/A10/A10.8 Membership.html`

**Blockers:** Wire native endpoints + tier picker UI from RN; decide Update payment (Stripe Customer Portal vs Settings Payments) — backend gap if portal not built; SLA-missed state needs a real flag (today preview-only).

---

## 3.3 Compose broadcast network wiring

**Verdict: NOT an iOS stub anymore — LIVE on both platforms. Treat as verify/fix.**

| Piece | Status |
|---|---|
| iOS `.live` factory | **REAL** — `ComposeBroadcastSampleData.live` sets `performSend` → `publish()` (`ComposeBroadcastSampleData.swift:95–114`). Hosted via `YouTabRoot` / `HubTabRoot` `.composeBroadcast` → `.live(personaId:)`. |
| Android `load()` | **REAL** — swaps `performSend` → `realPublish` (`ComposeBroadcastViewModel.kt:96–146`). |
| Load persona/channel/stats/history | **REAL** — `GET /personas/me`, `…/membership-stats`, `GET …/broadcast/channels/:id/messages` |
| Publish | **REAL** — `POST …/channels/:id/messages` (`broadcastChannels.js:450`) |
| Schedule / media in wire body | **LOCAL ONLY** — `PublishUpdateBody` is body/visibility/targetTierRank; schedule cleared after send without server schedule; media not uploaded |

**RN reference:** `identity/broadcast.tsx`, `AudienceComposer.tsx` → `api.broadcast.publishBroadcastMessage`; `packages/api/src/endpoints/broadcast.ts`.

**Design:** A22 compose — inventory marks REAL_VIEW; frames in A22 pack / `docs/design/new/uploads/A22.1 Audience.html` + `audience-frames.jsx`.

**Agent work:** Confirm both platforms; optionally DEFER schedule/media or implement if RN supports them. Do not rebuild networking.

---

## 3.4 Broadcast actions Reply / Boost / Pin

| Platform | Status |
|---|---|
| iOS | **PLACEHOLDER** — `YouTabRoot.swift:1519–1529` → `"Broadcast actions"`, `"Reply to broadcast"`, `"Boost broadcast"`, `"Pin broadcast"` |
| Android | **PLACEHOLDER** — `RootTabScreen.kt:3642–3651` same labels |
| UI chrome | **REAL** footer buttons in `BroadcastDetailView.swift` ~453–457 |

**Backend:** No pin/boost/reply routes. `broadcastChannels.js` only list/publish/read; `is_pinned: false` hardcoded on create (`:525`). **Backend gap for Boost/Pin.**

**RN:** No broadcast boost/pin endpoints in `broadcast.ts`.

**Design / nav:** `docs/nav-graph-closure.md:518`; A22 Broadcast detail frames.

**Blockers:** Product + backend for pin/boost; Reply may map to persona DM / chat open if product agrees — otherwise keep DEFER.

---

## 3.5 Creator audience actions; follower detail; inbox settings

| Action | Status | Symbols |
|---|---|---|
| Mute / unmute / approve / decline / remove | **REAL** | `YourAudienceViewModel.perform` → `PATCH /api/personas/me/audience/:membershipId` (`personas.js:753`) |
| Message | **STUB toast** | iOS `message(_:)` `:122–124`; Android `:133–137` — “coming soon”; comment: serializer exposes no user id |
| Change tier (owner→fan) | **STUB toast** | iOS `:128–130`; Android `:140–143` — **RN also has no owner change-tier** (fan self-service only) |
| Share Beacon | **STUB toast** | iOS `YourAudienceView.swift:483`; Android `shareBeacon()` |
| Follower detail | **PLACEHOLDER** | `AudienceProfileView.onOpenFollower` → `"Follower"` (`YouTabRoot.swift:1394–1395`; Android ~3596) |
| Inbox settings | **PLACEHOLDER** | `CreatorInboxView.onOpenSettings` → `"Inbox settings"` (`YouTabRoot.swift:1551–1552`; Android `:3662`) |
| Creator inbox list / thread open | **REAL** (partial) | Routes to `CreatorInboxView` / chat with tier chrome |

**RN audience sheet:** mute/unmute/remove only (`AudienceMemberSheet.tsx`) — no Message/Change tier.

**Persona DMs backend:** `personaDms.js` — `POST …/threads` `:135`, list `:185`, get `:235`, messages `:314`; fan `openThread` consumes quota (`402` `quota_exhausted`).

**Design:** `docs/design/new/Your Audience.html`, `docs/design/new/uploads/A22.1 Audience.html`, `audience-frames.jsx`.

**Blockers:** Creator-initiated DM needs membership_id → thread API (or new endpoint); owner “change tier” may be product-illegal (billing is fan-side); Share can be system share sheet with no backend; Inbox settings needs preferences API (may not exist).

---

## 3.6 Creator-thread quota-exhausted lock

| Piece | Status |
|---|---|
| Quota meter (proportional fill) | **PARTIAL REAL** — `ChatCreatorQuotaMeter` iOS ~1723; Android `CreatorQuotaMeter` ~1000 |
| `maxed` error-red state | **MISSING** — no `maxed` / `quotaExhausted` / `composerLocked` |
| System pill + upgrade-fan card | **MISSING** |
| Locked composer | **MISSING** — `canSend` ignores quota (`ChatConversationViewModel.swift:328–330`) |
| Fan-side quota gate | **EXISTS** — `FanQuotaGate` (different from creator-thread lock) |

**Spec:** `docs/screen-parity-inventory.md:99` and `:382–407`.

**Design:** A15.4 Creator thread secondary frame (`QuotaExhaustedThread`) — pack referenced in inventory; not vendored as a standalone file under `docs/designs/` (A08/A15 original packs removed; use inventory + design HTML if re-vendored).

**Backend:** Quota enforcement on open/send in `personaDms.js` / `personaQuotas.js`; UI must reflect exhausted state client-side when `used >= total`.

**Blockers:** Pure client chrome if quota fields already on creator context; confirm creator-thread mode is used from `CreatorInboxConversation` (currently may open `.person` mode in YouTabRoot `:1556–1558` — verify mode wiring).

---

## 3.7 Persona-owner chrome on public profile

| Piece | Status |
|---|---|
| Owner vs visitor branching on PublicProfile | **MISSING** — branches on `kind == .local` only (`PublicProfileView.swift:106–123`); no `isOwner` on `PublicProfileContent` |
| Owner chrome (analytics, Edit, AnalyticsStrip, settings) | **MISSING** on PublicProfile path |
| Empty broadcasts card | **MOSTLY DONE (P8.6)** — full empty card with “No broadcasts yet” + Follow; design older “Quiet for now” / `bell-plus` superseded per inventory line 98 |
| Owner chrome on BeaconProfile (owner mode) | **REAL** — `BeaconProfileView` has owner compose/edit/analytics path when `payload.isOwner` |

**Design:** A08 Public Beacon Profile / `beacon-frames.jsx` `FramePersonaOwner`; also `docs/designs/A21/a21-1-persona-frames.jsx`.

**Blockers:** Need `viewer.isOwner` (or compare to `/personas/me`) on public persona payload; route Edit → `EditPersonaView`; Analytics may need existing membership-stats endpoint.

---

## 3.8 Identity Center first-run chrome + DEFER sub-details

| Piece | Status |
|---|---|
| Identity cards + setupNeeded badges | **REAL** — `IdentityCenterView.swift:128+` / Android `IdentityCenterScreen.kt` |
| Bridges when non-empty | **REAL** |
| “Nothing to link yet” placeholder when bridges empty | **MISSING** — `if !loaded.bridges.isEmpty` (`IdentityCenterView.swift:111–115`; Android `:236–239`) |
| “Two more profiles to go” info card | **MISSING** — no string match |
| Local / Personal / PublicProfile sub-details | **DEFER PLACEHOLDER** — iOS `YouTabRoot.swift:1373–1374` → `"Identity"`; Android `:3672–3675` → `"Local profile"` / `"Personal"` |
| Professional | **REAL** → `ProfessionalProfileView` |
| View As privacy preview | **REAL** → `.viewAs` |

**Backend:** `GET /api/identity-center` `identityCenter.js:401`; `GET …/view-as` `:489`; `PATCH …/bridges/:personaId` `:516`.

**Design:** `docs/design/new/uploads/Identity Center.html` FRAME 2 FIRST RUN; inventory cites `identity-center-frames.jsx` → `FrameFirstRun`.

**Blockers:** Chrome-only for first-run cards (no new API). Sub-detail screens are DEFER — compose from Form/ContentDetail archetypes if shipped; do not copy RN layouts.

---

## Recommended ship order

1. **3.1** — Unlocks monetization funnel (unlock CTA + PublicProfile handshake). Highest user-visible revenue path.
2. **3.2** — Fan billing after subscribe (endpoints exist; native client thin).
3. **3.5** — Creator ops (message/share/follower); depends on DM model.
4. **3.4** — Reply first if chat-backed; Boost/Pin only after backend.
5. **3.6** — Creator quota lock (design gap, chat polish).
6. **3.7** — Owner chrome on PublicProfile (BeaconProfile owner already works).
7. **3.8** — First-run chrome (pure UI); DEFER identity sub-details last.
8. **3.3** — Verify-only / media-schedule polish — not a greenfield wire.

---

## Concrete agent checklist

### 3.1 Subscribe / paid-tier join
- [ ] iOS: Replace `BeaconProfileView.swift:172` unlock toast with handshake presenting `PrivacyHandshakeViewModel` with **preselected tier** from post `targetTierRank` (extend VM init).
- [ ] Android: Same for `showSubscribeToast()` / `onUnlock` → `ChildRoutes.privacyHandshake(handle)` + tier query/arg.
- [ ] iOS/Android: `PublicProfileViewModel.follow()` — stop using `RelationshipsEndpoints.sendRequest`; open persona handshake (need persona handle on content).
- [ ] Wire Tiers tab Join → handshake with that `tier.rank` (optional but recommended).
- [ ] Do **not** route persona subscribe through PaymentSheet; keep Checkout URL + browser (match RN).
- [ ] Confirm feature flags / Connect onboarding before paid QA.
- [ ] Snapshot + test-tags parity for unlock → handshake.

### 3.2 Membership billing
- [ ] Add native endpoints: upgrade, downgrade, refund-request (mirror `personaMembership.ts`).
- [ ] Replace host placeholders in `YouTabRoot` / `RootTabScreen` with in-screen flows (RN `index.tsx` tier picker + refund alert).
- [ ] Keep cancel API; replace `"Membership cancelled"` placeholder with inline success / status refresh.
- [ ] Update payment: either DEFER with honest copy (“Managed by Stripe”) or add Customer Portal backend + open URL.
- [ ] Wire SLA-missed from real membership/DM SLA fields when available; stop using preview-only seed for live.

### 3.3 Compose broadcast
- [ ] Manual QA: publish from owner Beacon / AudienceProfile on both OS.
- [ ] Document DEFER: schedule + media upload not on wire.
- [ ] No rewrite of `.live` / Android `load()` unless regressions found.

### 3.4 Broadcast actions
- [ ] Inventory product: Reply → open Creator Inbox / fan thread vs comment model.
- [ ] If no backend for Boost/Pin: keep `NotYetAvailable` / DEFER; do not fake.
- [ ] If shipping Pin: add `PATCH`/`POST` broadcast pin route + client; currently hardcoded `is_pinned: false`.

### 3.5 Creator audience
- [ ] Message: open/create persona DM via `membershipId` (extend API if creator cannot initiate today).
- [ ] Share: system share sheet with persona URL (remove toast).
- [ ] Change tier (owner): confirm product — likely **DEFER** (fan owns billing); remove toast or explain.
- [ ] Follower detail: minimal ContentDetail from fan_handle/tier/status (no PII).
- [ ] Inbox settings: find or DEFER preferences endpoint; replace placeholder route.

### 3.6 Quota-exhausted lock
- [ ] When `quota.used >= quota.total`: maxed meter (error fill), system pill, upgrade-fan card, locked composer copy from inventory.
- [ ] Gate `canSend` for `.creatorThread`.
- [ ] Ensure Creator Inbox conversation uses creator-thread mode (check `YouTabRoot` / Android chat mode).
- [ ] Snapshots for secondary frame.

### 3.7 Persona-owner PublicProfile
- [ ] Add `isOwner` to projection from persona `viewer` / `/personas/me`.
- [ ] Owner header: analytics + Edit; hide Follow; optional AnalyticsStrip + settings.
- [ ] Edit → `EditPersonaView` / Android edit route.
- [ ] Keep P8.6 empty card; don’t reintroduce obsolete “Quiet for now” unless design reverts.

### 3.8 Identity Center
- [ ] When `bridges.isEmpty`: render disabled “Nothing to link yet…” card (`link-2-off`).
- [ ] Trailing “Two more profiles to go” info card (copy from FRAME 2).
- [ ] Keep Local/Personal/PublicProfile destinations as DEFER placeholders unless scoped.
- [ ] Snapshot first-run frame both platforms.

### Shared acceptance
- [ ] Same endpoints/query params iOS ↔ Android.
- [ ] Same test tags (`accessibilityIdentifier` ↔ `testTag`).
- [ ] Loading / empty / loaded / error states for every new fetch.
- [ ] Tokens only; no hex in Features/.
- [ ] Update `docs/nav-graph-closure.md` when placeholders retire.