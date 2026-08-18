# Workstream 4 — Mailbox & homes stragglers (read-only audit)

Native lives under `/Users/yingpengwang/pantopus/native/pantopus/frontend/apps/{ios,android}`. RN reference: `/Users/yingpengwang/pantopus/reactNative/pantopus/frontend/apps/mobile`. Design refs under `docs/designs/A17|A18|A13` and `docs/new-design-parity.md`.

---

## 4.1 Stamps purchase — Stripe wiring

### Status
**Stubbed on native.** Buy CTAs mutate local fixture state only. **No postage-purchase Stripe path exists** (backend or clients). RN stamps is a different product (achievement gallery).

### Exact files
| Layer | Path |
|---|---|
| Design | `/Users/yingpengwang/pantopus/native/pantopus/docs/designs/A17/stamps.jsx`, `A17.11 Stamps.html` |
| iOS VM | `…/ios/Pantopus/Features/Mailbox/Stamps/StampsViewModel.swift` (`buyMore`, `purchaseStarterBook`) |
| iOS UI | `StampsView.swift`, `StampsContent.swift`, `Components/StampSheet.swift`, `StampBookHero.swift` |
| Android VM | `…/android/.../mailbox/stamps/StampsViewModel.kt` |
| Android UI | `StampsScreen.kt`, `StampsContent.kt`, `components/*` |
| Sample | `StampsSampleData.swift` / `.kt` |
| RN (gallery, not wallet) | `…/mobile/src/app/mailbox/stamps.tsx` |
| Stripe reuse (elsewhere) | iOS `Features/Settings/Payments/PaymentsViewModel.swift` + `StripePaymentSheetPresenter`; Android `PaymentsScreen.kt` + `StripePaymentSheets`; invoice/gig PaymentSheet |

### Stubs
- `buyMore()` → sets `used = 0` on featured book (no network).
- `purchaseStarterBook()` → flips to `StampsSampleData.populated`.
- `load()` → fixtures only; comments note RN/web `getStamps()` is an **achievement gallery**, not postage wallet.

### Endpoints
| Exists | Missing |
|---|---|
| `GET /api/mailbox/v2/p3/stamps` — gallery (`backend/routes/mailboxV2Phase3.js` ~1203) | Postage wallet balance / books |
| `GET/POST …/themes` | **Purchase**: PaymentIntent/Checkout for stamp book |
| Payments: `PaymentsEndpoints.intent` (gigs/marketplace) | Stamp SKU + fulfillment after Stripe success |

### Design / archetype
- A17.11 postage wallet: book hero, sheet grid, other-stamps rail, usage history, **Buy more** dock, empty + starter book (`stamps.jsx`).
- Reuse existing Stripe PaymentSheet pattern (SetupIntent/PaymentIntent), not Connect onboarding.

### Checklist
1. Product: confirm postage SKUs (starter book vs refill) vs achievement gallery (keep RN gallery separate).
2. Backend: `POST …/stamps/purchase` (or wallet-specific) → Stripe PaymentIntent + grant stamps.
3. Native: replace stubs in both VMs; present PaymentSheet (mirror Payments/Invoice).
4. Refresh wallet from new GET after success; keep fixture seed for previews/snapshots.
5. Deep link `pantopus://mailbox/stamps` already wired — no route work.

---

## 4.2 Mail task dock, translation Listen TTS, mail file/vault stubs

### A — Mail task dock

**Files:**  
iOS `Features/Mailbox/MailTask/{MailTaskView,MailTaskViewModel,MailTaskContent}.swift`  
Android `ui/screens/mailbox/mail_task/{MailTaskScreen,MailTaskViewModel}.kt`  
RN list (not A17 dock): `mobile/src/app/mailbox/tasks.tsx`

| Affordance | Status | Notes |
|---|---|---|
| Mark done / Reopen | **Live** | `PATCH /api/mailbox/v2/p3/tasks/:id` |
| Load | **Live** | `GET /api/mailbox/v2/p3/tasks` (no detail-by-id; filter client-side) |
| Snooze / snoozeFromDock | Toast stub | |
| Add step | Toast stub | |
| Calendar | Toast stub | |
| View confirmation | Toast stub | |
| Archive | Toast stub | |
| Delegate | Opens sheet only | `showsDelegateSheet`; host may push “Home drawer” placeholder |
| Subtasks / AI elf / next-up | Hidden on live | No backend fields; sample-only |

**Design:** A17.12 task detail (open dock: Mark done · Snooze · Delegate · Calendar; done: View confirmation · Archive).

**Checklist:** Persist snooze/archive (or drop UI); EventKit/Calendar intents; confirmation deep-link to source mail/ack; wire Delegate → real members/home drawer; optional detail-by-id endpoint.

---

### B — Translation Listen TTS

**Files:**  
iOS `Features/Mailbox/Translation/MailTranslationViewModel.swift` (`listen` → toast), `Components/SideBySide.swift` (`ListenButton`), `TranslatorNotes.swift`  
Android `translation/MailTranslationViewModel.kt`, `components/SideBySide.kt`  
RN: `mobile/src/app/mailbox/translation.tsx` — **no Listen UI**; calls `translateMail` live

| Piece | Status |
|---|---|
| Confirm translation | Hits `POST /api/mailbox/v2/p3/translate` (optimistic) |
| Letter body | Sample-driven (`MailTranslationSampleData`) |
| Listen | Toast only (“Playing the … aloud…”) — B2.3 out of scope |

**Checklist:** `AVSpeechSynthesizer` (iOS) / `TextToSpeech` (Android) on original vs translated columns; optional RN `expo-speech`; keep confirm endpoint.

---

### C — Mail file / vault actions

| Action | Status | Endpoint |
|---|---|---|
| Save to vault (picker) | **Live** | `GET …/p2/vault/folders`, `POST …/p2/vault/file` (`MailboxVaultEndpoints` / `MailboxVaultApi`) |
| Records **File in vault** | **Stub** | `fileRecordToVault()` optimistic `isFiled` + toast; no POST |

**Files:**  
iOS `MailDetailViewModel.swift` (`fileRecordToVault` ~332, `saveToVault` ~385), `Variants/RecordsDetailLayout.swift`  
Android `MailDetailViewModel.kt` (`fileRecordToVault` ~540), `variants/RecordsDetailLayout.kt`

**Checklist:** Point Records CTA at same `POST …/vault/file` (or records-specific route) with suggested folder; keep optimistic UI + rollback; align filed-at label with server.

---

## 4.3 Twelve categories → placeholder — generic body via `MailItemDetailShell`

### Important split (two detail systems)

| Path | Shell | Fallthrough today |
|---|---|---|
| **MailDetail (A17 ceremonial)** | `MailItemDetailShell` — iOS `Features/Shared/MailItemDetail/MailItemDetailShell.swift`; Android `ui/screens/shared/mail_item_detail/MailItemDetailShell.kt` | **`GenericMailDetailLayout`** already (iOS+Android) — body paragraphs / key facts / ack |
| **MailboxItemDetail (older)** | `MailboxItemDetailShell` — iOS `ItemDetail/MailboxItemDetailShell.swift`; Android `item_detail/MailboxItemDetailShell.kt` | **iOS: `GenericMailBody`**; **Android: still `MailItemPlaceholderBody` → `NotYetAvailableView`** |

### Twelve categories (ship-readiness / enum)
`notice`, `bill`, `statement`, `insurance`, `tax`, `subscription`, `legal`, `healthcare`, `membership`, `delivery`, `social`, `general`  
(Enum also has bespoke: package/coupon/booklet/certified/community/gig/memory/party/records.)

### Exact files for remaining work
- Android placeholder: `item_detail/bodies/CategoryBodies.kt` (`MailItemPlaceholderBody`)
- Android dispatch: `MailboxItemDetailScreen.kt` ~283–284
- iOS reference body: `ItemDetail/Bodies/CategoryBodies.swift` (`GenericMailBody` + explainers)
- iOS projection: `MailboxItemDetailViewModel.genericBody(for:category:)` ~903
- MailDetail generic (already done): `MailDetail/Variants/GenericMailDetailLayout.{swift,kt}`
- Docs stale: `docs/ship-readiness.md` still says 12 → `MailItemPlaceholderBody` (true for Android ItemDetail; iOS ItemDetail + both MailDetail paths moved on)

### Design / archetype
- Prefer **`MailItemDetailShell` + `GenericMailDetailLayout`** (A17.1 in `docs/new-design-parity.md`) as canonical.
- ItemDetail `GenericMailBody` is the readable-document pattern (category chip, paragraphs, attachments, tags, action pill).

### Checklist
1. Port iOS `GenericMailBody` → Android ItemDetail; delete/stop calling `MailItemPlaceholderBody` for the 12.
2. Project paragraphs/attachments/tags from wire (mirror iOS `applyItem` / `genericBody`).
3. Decide: retire duplicate ItemDetail route in favor of MailDetail, or keep both in parity.
4. Update ship-readiness / parity docs once Android ItemDetail matches.
5. Snapshots for a few of the 12 (notice/bill/general).

---

## 4.4 Waiting-room actions no-ops

### Status
Chrome **DONE** (B5.1); **all actions log-only**. Fixture-seeded; no review polling.

### Files
| | |
|---|---|
| Design | `docs/designs/A18/waiting-room-frames.jsx` |
| iOS | `Features/Status/WaitingRoom/{WaitingRoomView,ViewModel,Content,Components}.swift` |
| Android | `ui/screens/status/waiting_room/{WaitingRoomScreen,ViewModel,Content}.kt` |
| Routes | `pantopus://homes/:id/waiting-room` — `DeepLinkRouter` + `ChildRoutes.WAITING_ROOM` |
| RN | `mobile/src/app/homes/[id]/waiting-room.tsx` — **different** “Verification Center” (live `useHomeAccess`, move-out, postcard) — not A18.4 |

### Stubbed action keys
`bell`, `update_evidence`, `cancel_claim`, `view_claim`, `back_to_home` (VM `log` / `Log.i` only). Back chevron is real nav.

### Endpoints
No native waiting-room review API in repo (VM comments: backend removed). RN uses home access / move-out / claim-evidence routes instead.

### Checklist
1. Restore or define claim review-status API; poll/subscribe in VM.
2. Wire: Update evidence → claim evidence upload; Cancel claim → cancel API + confirm; View claim → claim detail; Back to home → `homeDashboard`; Bell → notifications.
3. Align RN verification-center vs native A18.4 (product decision).
4. Seed from live homeId/address/claimRef instead of `CLM-4F2A` fixtures.

---

## 4.5 Home settings placeholders

### Row inventory (A14.1 — native)
Wired from `HomeSettingsViewModel` / `HomeSettingsRoute`:

| Row id | Label | Navigation today |
|---|---|---|
| address / propertyDetails | Address · Property details | **Live** → `PropertyDetails` |
| photos | Photos | **Placeholder** `"Photos"` |
| documents | Documents | **Live** → home docs |
| accessCodes | Access codes | **Live** |
| trustedNeighbors | Trusted neighbors | **Placeholder** |
| privacy | Privacy | **Live** → Home security |
| people | People | **Live** → members |
| inviteLink | Invite link | **Placeholder** |
| homeNotifications | Home notifications | **Placeholder** |
| leaveHome | Leave this home | **Placeholder** |
| cancelClaim | Cancel claim | **Placeholder** |

**Handlers:**  
iOS `HubTabRoot.handleHomeSettingsRoute` ~440–464  
Android `RootTabScreen.kt` ~2673–2694  

### Related stragglers (user list)
| Item | Status |
|---|---|
| **Member requests** | Android deep link → `placeholder("Member requests · {id}")` (`RootTabScreen` ~1618). iOS deep link → **People** (`homeMembers`) — partial. RN members has pending requests via `getHouseholdAccessRequests`. |
| **Dispute** | Not a Home settings row. RN `homes/[id]/dispute.tsx` **Redirect** (“Ownership dispute center disabled”). No native home-dispute screen. |

### RN settings contrast
`mobile/src/app/homes/[id]/settings/index.tsx`: Leave Home **live** (`api.homes.detachFromHome`); notif toggles **local-only**; no Photos / Trusted neighbors / Invite link / Cancel claim rows matching A14.1.

### Design
`docs/new-design-parity.md` A14.1; note JSX vocabulary mismatch (Photo vs Photos, etc.).

### Backend hooks (examples)
- Leave: `POST /api/homes/:id/detach` / IAM remove (`home.js`, `homeIam.js`)
- Cancel claim / invite link / trusted neighbors / home-notif prefs: need product + API inventory before UI

### Checklist
1. Photos — gallery/upload screen or reuse home media.
2. Trusted neighbors — list/approve (or hide row until designed).
3. Invite link — create/share/revoke (RN has Guest Passes / share as partial analog).
4. Home notifications — prefs screen (RN toggles are local stubs).
5. Leave home — confirm + detach (port RN).
6. Cancel claim — confirm + cancel-claim API; share with waiting-room.
7. Member requests — Android deep link → members pending tab (match iOS); keep RN pending list.
8. Dispute — product: revive RN center or remove route; do not invent under settings without design.

---

## 4.6 Property Request correction flow placeholder

### Status
Property details **built**; correction CTA → **generic placeholder** only. No correction wizard/form.

### Files
| | |
|---|---|
| Design | `docs/designs/A13/property-details-frames.jsx` (clean inline link + mismatch sticky CTA) |
| iOS | `Features/Homes/PropertyDetails/{PropertyDetailsView,Content,ViewModel}.swift` — `StickyCorrectionBar` |
| Android | `property_details/PropertyDetailsScreen.kt` — `StickyCorrectionButton` |
| Nav stub | iOS `HubTabRoot` ~2171–2173; Android `RootTabScreen` ~3904–3906 → `placeholder("Request correction")` |
| Deferred | `docs/nav-graph-closure.md` Group B |
| RN | `property-details.tsx` — ATTOM display; **no** Request correction CTA |

### Endpoints
No dedicated “request property correction” route found. Home address mismatch handling exists in `home.js` (canonical mismatch / revalidation) but not a user correction ticket flow.

### Checklist
1. Spec flow from frames: pick conflicting field, choose county vs owner, submit note.
2. Backend ticket/PATCH for owner-confirmed corrections.
3. Replace placeholder nav with wizard/form (Form/Wizard archetype).
4. Wire clean-state inline “Request a correction” + mismatch sticky CTA to same flow.
5. Optional RN parity CTA.

---

## Cross-cutting implementable order

| Pri | Item | Effort signal |
|---|---|---|
| 1 | **4.3** Android ItemDetail `GenericMailBody` (MailDetail already OK) | Medium, UI-only |
| 2 | **4.2C** Records `fileRecordToVault` → existing vault POST | Small |
| 3 | **4.5** Leave home + Member requests deep-link parity | Small–medium (APIs exist) |
| 4 | **4.2B** Listen TTS | Medium, client-only |
| 5 | **4.2A** Mail task dock persistence | Medium; may need APIs |
| 6 | **4.6** Correction flow | Medium–large (needs backend) |
| 7 | **4.4** Waiting-room actions | Large (review backend) |
| 8 | **4.1** Stamps Stripe | Large (new product + Stripe + wallet API) |
| 9 | **4.5** Photos / Trusted neighbors / Invite / Home notifs / Cancel claim / Dispute | Depends on product |

### Stripe pattern to copy for 4.1
- iOS: `PaymentSheetPresenting` / `StripePaymentSheetPresenter` in Payments  
- Android: `rememberPaymentSheet` + `StripePaymentSheets` in `PaymentsScreen` / `InvoiceDetailScreen`  
- Backend shape: `PaymentsEndpoints.intent` / gig checkout PaymentIntent — extend with stamp SKU metadata, not Connect.

### RN vs native product deltas (don’t assume 1:1)
- Stamps: RN gallery ≠ native postage wallet  
- Waiting room: RN verification center ≠ A18.4  
- Translation: RN live translate, no Listen  
- Tasks: RN list CRUD; native A17 detail dock stubs  
- Settings: RN Leave live; native A14.1 rows mostly placeholders  
- Dispute: RN redirected off; native absent