# New-design parity audit — Pantopus mobile

> **Phase 0 deliverable.** Maps every screen in the May 2026 design hand-off
> (A03 / A09 / A10 / A12 / A13 / A14 / A17 / A18 / A21) to its iOS and
> Android implementation files and grades each against the new design.
> Output is a worklist, not code. Phase 1 onward is implementation.
>
> **Sources of truth:**
> - Design HTML/JSX packs at `docs/designs/A{03,09,10,…}/…`
> - iOS: `frontend/apps/ios/Pantopus/Features/…`
> - Android: `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/…`
>
> **Status legend:**
> - **BUILD** — no implementation file exists; net-new screen.
> - **RESHAPE** — file exists but layout / slot vocabulary diverges from
>   the new design enough that we must rebuild substantial regions
>   (not just swap tokens).
> - **POLISH** — file exists, structure matches, but per-state visuals
>   (banners, chips, copy, halo, gradient, locked sub-state) are missing
>   or need refresh.
> - **MATCH** — already at design.
>
> **Path prefixes** (table cells are relative to these):
> - iOS — `frontend/apps/ios/Pantopus/Features/`
> - Android — `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/`

---

## Summary

| Status | Count |
|---|---:|
| BUILD | 14 |
| RESHAPE | 11 |
| POLISH | 13 |
| MATCH | 0 |
| **Total screens audited** | **38** |

iOS and Android sit at strong structural parity, so the status grades
below are symmetric on both platforms unless explicitly called out in
the "Per-frame deltas" cell.

### Shared primitives required before screen work starts

These are the primitives the new designs depend on that don't exist in
`Core/Design/Components/` (iOS) or `ui/components/` (Android) yet. They
unblock multiple screens and must land in Phase 1.

| Primitive | iOS path | Android path | Used by |
|---|---|---|---|
| `HaloCircle` | `Core/Design/Components/HaloCircle.swift` | `ui/components/HaloCircle.kt` | A18.1, A18.2, A18.3 |
| `BalanceHero` | `Core/Design/Components/BalanceHero.swift` | `ui/components/BalanceHero.kt` | A10.10 |
| `FuzzMap` | `Core/Design/Components/FuzzMap.swift` | `ui/components/FuzzMap.kt` | A14.7 |
| `ChannelChip` | `Core/Design/Components/ChannelChip.swift` | `ui/components/ChannelChip.kt` | A14.5 |
| `DateSpan` | `Core/Design/Components/DateSpan.swift` | `ui/components/DateSpan.kt` | A14.8 |
| `PaperStack` | `Core/Design/Components/PaperStack.swift` | `ui/components/PaperStack.kt` | A17.10 |
| `ConfettiSpray` | `Core/Design/Components/ConfettiSpray.swift` | `ui/components/ConfettiSpray.kt` | A17.9 |
| `CodeInput` | `Core/Design/Components/CodeInput.swift` | `ui/components/CodeInput.kt` | A12.7 |
| `SlotCalendar` | `Core/Design/Components/SlotCalendar.swift` | `ui/components/SlotCalendar.kt` | A10.9 |
| `StrengthMeter` | `Core/Design/Components/StrengthMeter.swift` | `ui/components/StrengthMeter.kt` | A13.14 |
| `BeaconBanner` | `Core/Design/Components/BeaconBanner.swift` | `ui/components/BeaconBanner.kt` | A21.1, A21.2 |
| `Postcard` (verification-flavor) | `Core/Design/Components/Postcard.swift` | `ui/components/Postcard.kt` | A12.7 |
| `EnvelopeOcrBox` (clean / dashed-amber) | extends existing `DisambiguateMailFormView` | extends existing `DisambiguateMailFormScreen` | A13.15 |

---

## A03 — Pulse feed (tab archetype)

### A03.1 — Pulse (`feed.tsx`)
- **iOS:** `Feed/FeedView.swift` + `Feed/Pulse/PulsePostCard.swift` + `Feed/PulseFeedViewModel.swift`
- **Android:** `feed/FeedScreen.kt` + `feed/pulse/PulsePostCard.kt` + `feed/pulse/PulseFeedViewModel.kt`
- **Status:** **POLISH** (both platforms)
- **Designed frames:** populated (5 mixed-intent cards · pull-to-refresh peek) · empty (radio glyph + "Create post" + scope footer chip)
- **Per-frame deltas:**
  - Confirm chip row is the 6-intent set in design order: All · Ask · Recommend · Event · Lost & Found · Announce. Each chip uses the tinted bg color from `IntentChip` (amber / success / violet / rose / slate).
  - `PostCard` recipe in design: avatar → name + meta + IntentChip → 3-line clamped body → `ReactionBar` (helpful · going · seen · shared + Reply). Existing iOS card has reactions; verify the reaction-verb matrix exactly matches design (`helpful` / `heart` / `going` / `seen` / `shared`).
  - **Event variant** in design has a "+N going" stacked-avatar row + RSVP pill capped above the reaction bar — verify Event posts get the inline RSVP affordance not the bare body.
  - **Pull-to-refresh peek** (mid-gesture spinner) and FAB position (right 18 / bottom 84 — clears the tab bar) must match.
  - **Empty state** must show: radio-tower glyph in `primary50` circle, "No posts yet" h2, primary "Create post" CTA, *footer chip* showing the active neighborhood scope ("Showing posts within Elm Park · change in filter"). The footer chip is the design's signature — check it exists.

### A03.2 — Beacons (`beacons.tsx` — Beacon Updates tab)
- **iOS:** *no dedicated file* — likely served by a tab inside `Feed/FeedView.swift` or surfaced through `AudienceProfile`. **NEEDS LOCATION CONFIRM** before work starts.
- **Android:** same situation — no dedicated `beacons/` folder under `screens/feed/`.
- **Status:** **BUILD** if no current Beacons tab; **POLISH** if it's a sub-tab inside FeedView.
- **Designed frames:** populated (5 verified beacons w/ broadcasts) · empty ("Follow a beacon" — RSS glyph + "Discover beacons" CTA + "0 beacons followed" footer chip)
- **Per-frame deltas:**
  - Same archetype as A03.1; only divergence is the empty-state icon (`rss` not `radio`), CTA copy ("Discover beacons"), footer chip wording, and the absence of any non-verified posts. All authors must carry the verified check disc.

---

## A09 — Transactional detail (sticky-dock archetype)

All four A09 screens render through the shared `TransactionalDetailShell`
(iOS) / equivalent Android composable. Shell is well-built; gaps are in
**per-state coverage and per-screen slot extensions**.

### A09.1 — Task V2 (`/gig-v2/[id].tsx`)
- **iOS:** `ContentDetail/GigDetailView.swift` + `ContentDetail/GigDetailViewModel.swift` + `ContentDetail/TransactionalDetailShell.swift`
- **Android:** `contentdetail/GigDetailScreen.kt` + `contentdetail/GigDetailViewModel.kt`
- **Status:** **POLISH**
- **Designed frames:** populated (6 bids · low-to-high · fastest-reply / has-van tags) · no-bids ("Be the first to bid" dashed capsule)
- **Per-frame deltas:**
  - Verify status pill copy: amber `Open · 6 bids` (populated) vs `Open · No bids yet` (empty).
  - **Magic Task JSONB modules in this order:** What needs doing → Pickup→Drop-off (two-stop A→B card on muted bg) → When → Photos (3-image strip). The Pickup→Drop-off card's A/B avatar discs use `primary100`/`successBg`. Confirm.
  - **Trust capsule row** (Verified address · 5.0★ rating · 14 jobs done) sits between Photos and the bid list.
  - **Bid list** rows must show: avatar (verified disc) · name · optional tag pill (`fastest reply` / `has van`) · star + "X.X · N jobs" subline · amount in `primary600` 14pt.
  - **No-bids state:** dashed-border capsule with `"Be the first to bid"` + viewer-count pill. Dock CTA stays `Place bid`.

### A09.2 — Gig V1 (`/gig/[id].tsx` legacy)
- **iOS:** same `GigDetailView.swift` (legacy mode through view-model state) or sibling file
- **Android:** same `GigDetailScreen.kt`
- **Status:** **POLISH**
- **Designed frames:** populated (3 bids · plain description) · awarded (green pill · winner highlighted · losing bids dimmed + strike-through · `Bidding closed` lock-CTA)
- **Per-frame deltas:**
  - **Awarded state** specifically: status pill flips to green `Awarded`, inline award banner under hero ("Awarded to Tomás G. · 14 min ago"), bid list gets `closed` subheader, winning row gets green bg + `Winner` pill, losing rows dim to 55% opacity with strikethrough on prices. Right CTA becomes disabled `Bidding closed` with `lock` glyph. The dimming/strike-through visual is the design's signature — verify it renders.
  - V1 body is sparser than V2: no Magic Task modules, no stat strip, no trust capsules, no category chip. Just Description + posted-by line + bid list.

### A09.3 — Listing (`/listing/[id].tsx`)
- **iOS:** `ContentDetail/ListingDetailView.swift`
- **Android:** `contentdetail/ListingDetailScreen.kt`
- **Status:** **POLISH**
- **Designed frames:** populated (Bianchi · $410 · Excellent) · sold (`SOLD` tilted stamp · strike-through asking · "Sold for $385" green tag · "Find similar" dock)
- **Per-frame deltas:**
  - Hero is a 300pt-tall **carousel** with glass overlay nav (translucent back button + glass share + glass bookmark), 4-dot pagination at bottom. The carousel is the design's signature — confirm it's not a static image.
  - $410 price hero must be in `primary600` (listings only — tasks/invoices use `appText` for the price).
  - **Pill row under price:** condition (Excellent) · pickup · 0.8 mi. Pickup pill is identity-tinted (home / personal).
  - **Seller card:** identity chip (Personal sky · Business violet · Home green), star + "X.X (N reviews)" subline, DM icon button on the right.
  - **Similar nearby** horizontal scroll at bottom (120pt-wide tiles with mini gradient bg + name + price in `primary600`).
  - **Sold state:** hero desaturated (grayscale .85), tilted SOLD stamp (`rotate -12°`, 3px red border), red `Sold` pill where price chips were, $410 strike-through next to green "Sold for $385" tag, similar row relabeled "Similar still available", dock pivots to single "Find similar" CTA (no Message / Make offer).

### A09.4 — Invoice (`/invoice/[id].tsx`)
- **iOS:** `ContentDetail/InvoiceDetailView.swift`
- **Android:** `contentdetail/InvoiceDetailScreen.kt`
- **Status:** **POLISH**
- **Designed frames:** due (amber `Due in 7 days` · $642.85 total hero · payer/payee cards · line items + tax + service fee) · paid (`Paid · Dec 14` · green total · Pantopus Pay receipt capsule · totals row relabeled "Paid")
- **Per-frame deltas:**
  - Mono reference line (`INV-00318 · issued Dec 4 · due Dec 18`) sits above the title in `appTextSecondary`/`ui-monospace`. Verify mono font.
  - **Total hero** is `$642.85` in 32pt heavy + "total · USD" caption — same heavy weight as task price but no `primary600` (uses `appText`).
  - **Payer/Payee row:** two cards side-by-side, each with an uppercase "FROM" / "TO" overline, name in fg1 bold, identity-tinted dot + sub line ("Business · Verified" — violet dot; "Personal" — sky dot).
  - **Line items table:** header row in `appSurfaceMuted` with uppercase column captions (Item · Qty · Unit · Total), tabular-nums on all amounts, fees+tax block in `appSurfaceMuted` at bottom, total row with `Total` in 13/700 + `$642.85` in 16/800/`primary600`.
  - **Paid state:** total recolors to `success` green with a check disc, "paid in full" trailing label, Pantopus Pay receipt capsule (txn id, mono timestamp, "4d early" badge), totals row label changes to "Paid", dock pivots to **Share + Download receipt** split (not Pay).

---

## A10 — Detail: content

### A10.9 — Support train detail (`/support-trains/[id].tsx`)
- **iOS:** `SupportTrains/Detail/SupportTrainDetailView.swift` + `SupportTrainDetailViewModel.swift` + `SupportTrainDetailContent.swift` + `SupportTrainDetailSampleData.swift` + `Detail/Components/{RecipientCard,TypeDatesCard,SlotRow}.swift` (P3.1).
- **Android:** `support_trains/detail/SupportTrainDetailScreen.kt` + `SupportTrainDetailViewModel.kt` + `SupportTrainDetailContent.kt` + `SupportTrainDetailSampleData.kt` + `detail/components/{RecipientCard,TypeDatesCard,SlotRow}.kt` (P3.1).
- **Status:** **MATCH** (both platforms, pending baseline PNG commit + backend detail-payload wiring)
- **Designed frames:** populated (12/21 slots covered · 9 open) · fully covered (green celebration banner · "Your commitment" card · `Send a card` + `Join as backup` split dock)
- **Build dependencies:** `SlotCalendar` primitive (see Phase 1 above).
- **Required slots:**
  - **`For` overline + `RecipientCard`** (avatar gradient + household name + Home identity chip + verified disc + address pin + quote block in muted bg with `quote` icon).
  - **`The train` overline + `TypeDatesCard`** (icon tile e.g. `utensils` in homeBg + title "Meal train · dinner for 4" + date range + N-days-left meta + `Open`/`Covered` pill + progress bar with sky gradient + 4-avatar stack with `+N`).
  - **`Slot calendar` overline + `SlotCalendar`** (4-week grid with cell states `past` / `today` (sky) / `filled` (homeBg) / `open` (dashed) / `mine` (sky outline) + legend pinned below).
  - **`Open slots near you` overline (with "See all 9" action) + `SlotRow` stack** — 3 rows with inline `Sign up` pill in sky.
  - **`Already on the train` overline + `SlotRow` stack** showing who + what they're bringing.
  - **`HostedBy` footer.**
  - **`PrimaryCTA` dock:** `Sign up for a slot` (populated) → splits to `Send a card` ghost + `Join as backup` sky-ghost (covered).
- **Routing (post-P3.1):** `DeepLinkRouter` `.supportTrain(id:)` now lands on `SupportTrainDetailView` (iOS) / `SupportTrainDetailScreen` (Android); organizers reach `ReviewSignupsView` via the dock-overflow `Manage signups` action or the new `pantopus://support-trains/:id/manage` deep link (`.supportTrainManage(id:)`). The `SupportTrains` list row tap and the `StartSupportTrainWizard` success path both push the participant detail.

### A10.10 — Wallet (`/wallet.tsx`)
- **iOS:** `Features/Wallet/WalletView.swift` + `WalletViewModel` (P3.2).
- **Android:** `ui/screens/wallet/WalletScreen.kt` + `WalletViewModel`.
- **Status:** **POLISH** (both platforms)
- **Designed frames:** populated ($847.50 available · $186 pending · 22% above October) · payout on hold (bank verification expired · withdraw locked · re-verify card · 1099-ready chip)
- **Build dependencies:** `BalanceHero` primitive.
- **Required slots:**
  - **`BalanceHero`** — dark sky-gradient (`primary800 → primary700 → primary600`), concentric arc decoration (3 circles at 90/60/30 radii, opacity 0.18, right -40 top -50), "Available to withdraw" overline in `primary200`, $44pt heavy amount (with $22pt small dollar-sign baseline-aligned), USD glass chip, glass split-strip below (Pending — clock icon · 3 tasks · clears by Dec 4 · This month — trending-up icon · ▲22% vs Oct).
  - **`Recent activity` overline + tx list** grouped by day. Each row: 36pt category-tinted icon tile (cleaning green `sparkles` · child-care amber `baby` · handyman orange `wrench` · pet-care red `dog` · bank indigo `building-2` · fee grey `receipt`) + label/sub + amount tabular-nums + status chip on right (available / pending / complete · for outbound: $ with leading minus).
  - **`Payout method`** — debit-card-shaped tile (44×30 with `CHASE` text in white 8pt) + "Chase checking •••• 7421" + flash icon "Instant payout · 1–3 minutes" + `Manage` text button (`primary600`). Warn variant: amber-gradient card + alert icon + "Verification expired Nov 30" + dark-amber `Re-verify` button.
  - **`Taxes` overline + `TaxDocsRow`** — file-text icon tile + "Tax documents" + YTD line + chevron. Ready variant: home-green icon bg + `New` chip + "1099-NEC for 2025 ready · $9,847 reported".
  - **`BottomBar` with gradient fade** (linear `app-bg`-alpha 0 → 0.92 → 1) — *not* a solid frosted dock. **`WithdrawCTA`** showing icon + "Withdraw" left + amount tabular-nums right. Hold variant: greyed locked button + "Re-verify your bank above to unlock payouts" footnote in `appTextSecondary` 10.5pt center-aligned.
  - **Hold variant additions:** amber `HoldBanner` at top of scroll (shield-alert icon disc + "Bank verification expired" + body copy + "earnings keep landing — they're safe" reassurance), `BalanceHero` gets `holdTone` (yellow inset banner under split strip).
- **Routing:** `WalletView` (iOS) / `WalletScreen` (Android), reached via the `pantopus://wallet` deep link and the host's wallet tab/route entry. (P5.2 reclaimed the Settings → `Payments & payouts` row for A14.6 Payments — distinct payments-out surface; Wallet still owns the earnings-in flow.)

---

## A12 — Wizard archetype (multi-step)

All A12 wizards use the same chrome: 48pt top bar (back · centered title ·
"X of Y") + segmented progress rail + scroll body + frosted sticky dock.

### A12.4 — Claim ownership · Evidence (`/homes/[id]/claim-owner/evidence.tsx`)
- **iOS:** `Homes/ClaimOwnership/Steps/ClaimUploadStep.swift` + `ClaimOwnershipWizardView.swift`
- **Android:** `homes/claim_ownership/ClaimOwnershipSteps.kt` + `ClaimOwnershipWizardScreen.kt`
- **Status:** **POLISH**
- **Designed frames:** ready-to-submit (both docs uploaded · per-file OCR address-match confirmations · optional statement filled) · mid-upload (one with address-mismatch warning · one with progress bar · CTA disabled with "Waiting for upload to finish" loader)
- **Per-frame deltas:**
  - **`UploadSlot` states** must be: `empty` (dashed border + plus icon + label) · `uploading` (filename + size + progress bar) · `done` (filename + size + green check + "Address matches your account" line in success-fg) · `warn` (filename + amber chip + "Address differs from your profile" body block).
  - **`ClaimStatement` textarea** — 500-char max with live counter, placeholder copy from design ("Add a short statement to help the reviewer (e.g. how long you've owned, anyone else on title)…").
  - **Encryption footer** ("🔒 Encrypted in transit. Visible only to the reviewer assigned to your claim.") in `appTextSecondary` 11.5pt.

### A12.5 — Verify landlord · Start (`/homes/[id]/verify-landlord/index.tsx`)
- **iOS:** **MISSING** — no `VerifyLandlord/` folder anywhere under `Homes/`.
- **Android:** **MISSING** — same.
- **Status:** **BUILD** (both platforms)
- **Designed frames:** canonical start (HomeChip · h2 · `What you'll need` requirements card · `Why we ask` expandable · `Start verification` CTA) · fast-track (verified-landlord notice card · shorter requirements list · "No email to the landlord this time" · same CTA)
- **Required slots:**
  - **`HomeChip`** (home identity chip — green pillar dot + address).
  - **`VRequirementsCard`** — `What you'll need` overline + 3 rows: 22pt round homeBg disc + check icon · 13pt-600 title + 11.5pt sub.
  - **`VWhyWeAskRow`** — `primary50` bg + `primary100` border, shield-check icon in `primary600`, "Why verify your landlord?" + tap-to-expand chevron.
  - **Fast-track variant additions:** success-50 card with badge-check disc + "Landlord already verified for this building" h3 + "2 other tenants in this building have completed verification" body + existing landlord chip (Business violet tile + name + "Verified May 2025 · M. Patel, owner" + green Verified chip). H2 changes to "Join as a verified tenant".

### A12.6 — Verify landlord · Details (`/homes/[id]/verify-landlord/details.tsx`)
- **iOS:** **MISSING**
- **Android:** **MISSING**
- **Status:** **BUILD**
- **Designed frames:** populated (all 3 cards complete: Business info violet-badged · Lease upload with OCR success · PM toggle on + 3 fields) · validation errors (summary banner · email format error · lease unit mismatch · PM toggled off · CTA disabled with "2 fields need attention" footnote)
- **Required slots:**
  - **`DCard` × 3**: Business info (with violet Business badge in DSectionHeader right slot), Lease/deed (DLeaseUpload with state=done|warn), Property manager (DPMToggle + conditionally 3 fields).
  - **Error summary banner** (error50 bg, error100 border, error600 circle + alert-circle icon + "Fix 2 things to submit" + "Email format · Lease unit mismatch" sub).
  - **`DField`** — label + optional · value (or placeholder) · icon · error chip + focused ring · hint line.
  - **Sticky bottom hint** above disabled CTA: "2 fields need attention" in error600 + alert-circle icon.

### A12.7 — Postcard verification (`/homes/[id]/verify-postcard.tsx`)
- **iOS:** **MISSING**
- **Android:** **MISSING**
- **Status:** **BUILD**
- **Designed frames:** delivered (postcard with green `DELIVERED` cancellation stamp · timeline all-green · 6-char mono code filled · Resend / Scan secondaries · Verify CTA active) · in-transit (pristine postcard · timeline middle "In transit" with animated dashed connector · code input locked behind "Code unlocks on delivery" overlay · help block with Resend/Re-address/Email fallback · CTA disabled)
- **Build dependencies:** `Postcard` primitive (postcard hero w/ delivered toggle that adds cancellation stamp) + `CodeInput` primitive (6-character monospace boxes) + `StatusTimeline` (3-stage with pulsing-glow current).
- **Required slots:**
  - **`PHeader`** — top bar with back chevron + centered "Postcard verification" (no step counter — this is outside the 3-step wizard).
  - **`Postcard`** — pristine recipient block (name + street + city/zip in handwriting-flavor) on cream stock; delivered variant adds rotated red "DELIVERED" cancellation stamp + postage marks.
  - **Hero copy**: status chip (`Delivered Oct 12` green-bg in delivered, `In transit` amber-bg + pulsing dot in transit) + h2 ("Enter the code from the card" / "Your card is on the way") + 280-wide subcopy.
  - **`StatusTimeline`** — 3-stage (Sent / In transit / Delivered) with done/current/pending states.
  - **`CodeInput`** — 6 boxes for `4Q2K7B`; in-transit: disabled with white-alpha overlay + lock icon + "Code unlocks on delivery" pill.
  - **Secondary row** (delivered): Resend ↔ divider ↔ Scan code.
  - **Help block** (in-transit only): Resend postcard (disabled until +3 days) · Wrong address? · Try email instead — 3 rows in surface card.
  - **Sticky dock:** `Verify code` primary (delivered active / in-transit disabled) + "You'll be notified the moment it's delivered" hint above when in-transit.

### A12.10 — Create business (`/businesses/new.tsx`)
- **iOS:** **MISSING** — `Features/Businesses/` has only `MyBusinessesView.swift`, `MyBusinessesViewModel.swift`, `BusinessWaitlistView.swift`. No wizard.
- **Android:** **MISSING** — same.
- **Status:** **BUILD**
- **Designed frames:** populated (Home Services tile selected · violet accent ring · `What you'll get` strip · next-steps map · ~6 min meta) · search (focused input · 3 ranked typeahead results with highlighted "tutor" substring · dashed violet "Add custom category" fallback)
- **Build dependencies:** **wizard chrome must be re-themed business-violet** for this wizard (progress rail · identity chip · CTA shadow · selected category accent ring · search ring + caret). Existing `WizardView` is hard-coded `primary600` — must accept an identity param.
- **Required slots:**
  - **`BIdentityChip`** (business pill + verified disc) — sky → violet recolor.
  - **`CategoryGrid`** 2×4 of 8 tiles (Home services · Personal services · Tech · Pets · Childcare · Health · Tutoring · Other) — each tile's icon tinted with its category accent. Selected tile gets accent ring (1.5px) + check disc + `0 6px 16px {accent}22` shadow.
  - **`WhatYouGet`** — business-soft bg, 3 rows (Service listings · 1099/W-9 ready · Insurance hint).
  - **`Step preview` mono-meta**: "Next: legal info · profile · confirm" + "~6 min".
  - **Search frame**: focused 12pt search field with caret animation + `3 matches for "tutor"` header + `Highlighted` substring (violet bg) inside SearchResult rows + dashed violet `Add as custom category` fallback.

### A12.11 — Start a support train (`/support-trains/new.tsx`)
- **iOS:** `SupportTrains/StartTrain/StartSupportTrainWizardView.swift` (~1100 lines) + `StartSupportTrainWizardViewModel.swift` + `StartSupportTrainContent.swift`
- **Android:** `support_trains/start_train/StartSupportTrainWizardScreen.kt` + `StartSupportTrainViewModel.kt` + `StartSupportTrainContent.kt`
- **Status:** **RESHAPE**
- **Designed frames:** verified neighbor (TrainChip warm · recipient card with verified-neighbor shield + mutuals · 6-tile reason picker · short note · invite-only/block-visible toggles · 5-step rail preview) · invite branch (search row · "no verified neighbor" amber warning · invite by phone/email options · invite-only privacy hint · CTA → `Send invite & continue`)
- **Per-frame deltas:**
  - **Wizard chrome re-themed warm-amber** (warm `#b45309` for CTA, warmBg `#fef3c7` for selected reason tile, progress rail in warm accent) — current implementation may be sky-blue.
  - **`TrainChip`** — warm-amber pill (not sky).
  - **`ReasonPicker`** must be 6 tiles in 3×2: meal-train / ride / errand / surgery / baby / loss (icons + label). Selected: warmBg fill + warm border.
  - **`RecipientCard`** must show mutuals strip (2 micro-avatars + "2 mutuals: Marisa, Devon").
  - **`InviteRecipientCard`** (Frame 2): typed search row → no-match amber section (user-search disc + "No verified neighbor by that name") → invite-by-phone row (recommended green chip) + invite-by-email row → privacy hint.
  - **`StepRail` preview** at bottom of body (mini 5-segment rail showing current step 1 of 5).

---

## A13 — Single-screen forms

### A13.3 — Review claim (`/homes/[id]/owners/review-claim.tsx`)
- **iOS:** `ReviewClaims/ReviewClaimDetailView.swift` + `ReviewClaimDetailViewModel.swift` + `ReviewClaimDetailComponents.swift`
- **Android:** `review_claims/ReviewClaimDetailScreen.kt` + `ReviewClaimDetailViewModel.kt`
- **Status:** **RESHAPE**
- **Designed frames:** pending (Rosa Delgado · 25% claimed · ID+phone verified · 4 evidence files · signed statement · 3-button verdict bar) · challenging (challenge composer bottom sheet over dimmed review · 2 reason chips picked · drafted questions · routes-to + 14-day window)
- **Per-frame deltas:**
  - **Verdict vocabulary change**: current uses `Approve` / `Reject` / `Request more info`. New design uses **`Accept` / `Challenge` / `Reject`**. The "Request more info" sheet becomes the **Challenge composer**.
  - **`VerdictBar` layout**: full-width `Accept claim` primary (success-green bg + check-circle-2 icon + sky shadow) on top, then split row below of `Challenge` (warning-tinted ghost: `#fff7ed` bg + `#9a3412` fg + `#fed7aa` border) + `Reject` (error-tinted ghost: surface bg + error fg + errorLight border). Current implementation likely flatten three to one row.
  - **`ClaimantCard`** must show: 52pt avatar with gradient (`linear-gradient(135deg,#fb923c,#c2410c)` for Rosa), name + amber `Pending 3d` chip with clock icon, email line with `at-sign` icon, **claim summary tile** (`Claiming` overline + `25%` in `primary700` mono + "ownership share"), **trust chip row** (`Verified ID` success · `Phone verified` success · `No mutual owners` warn — using `TrustChip` primitive with tone-based bg/fg/border).
  - **`EvidenceStrip`** — 4 evidence thumbnails (synthetic doc / photo / utility / signed-statement). The design uses bespoke `<svg>` drawings — deed has horizontal lines + a sky stamp rect; photo has fall-sunset porch gradient; utility has shimmer-line preview. Verify the synthetic previews are implemented and not just placeholders.
  - **`StatementBlock`** — italic statement in muted bg, prefixed with `"`.
  - **`ChallengeComposerSheet`** must be a bottom sheet (78% max height, scrim + backdrop blur) with: drag-grabber + h2 "Challenge this claim" + reason chips (Identity unclear · Documents look altered · Ownership share disputed · Don't recognize claimant · Other) + question textarea + "Sent to claimant + 2 co-owners" + "14-day window" + primary "Send challenge" CTA. Current implementation uses `ReviewClaimNoteCaptureSheet` — must add reason-chip selector to it.

### A13.4 — Transfer ownership (`/homes/[id]/owners/transfer.tsx`)
- **iOS:** **MISSING** — `Homes/Owners/` has only `OwnersListView.swift`, `OwnersListViewModel.swift`, `OwnerProofPalette.swift`.
- **Android:** **MISSING** — same.
- **Status:** **BUILD**
- **Designed frames:** ready (Maya Fortune picked · verified · 25% slider · preset chips · before/after split diff · "TRANSFER" typed · irreversibility warning) · final confirm (Face ID bottom sheet over dimmed form · from→to with strike-through deltas · legal note · Confirm with Face ID primary)
- **Required slots:**
  - **`HomeStrip`** at top (home identity chip — green pillar dot + address pin + "since 2019" meta).
  - **`Recipient` overline + `SearchField`** ("maya fortune" with caret + focused sky ring) + **`RecipientCard`** (44pt avatar with violet gradient + name + verified disc + meta + 3-stat strip: Connection · Verified · Mutual homes).
  - **`Share to transfer · 25%` overline + `Slider` card** (custom 1–60% slider showing "max 60% (your stake)" + 4 preset chips 10/25/33/50% in mono).
  - **`SplitDiff`** — visual before/after bar showing current ownership split (you 60% sky / Mateo 25% green / Jin 15% rose) → new split (you 35% sky / Maya 25% violet / Mateo 25% green / Jin 15% rose) with smooth transition.
  - **`Confirmation` overline + typed confirmation** — "Type `TRANSFER` to confirm" with mono `TRANSFER` chip inline + `Input state=valid`.
  - **Amber warning block** ("Mateo and Jin will be notified after this transfer. You cannot reclaim the 25% without Maya's signed transfer back.") in warning-bg with info icon.
  - **`StickyCTA`** — full-width `Transfer 25% to Maya` primary CTA.
  - **`ConfirmSheet`** — bottom sheet with Face ID prompt: 56pt rounded-tile with dark gradient + scan-face icon, "Final confirmation" h3, body copy, compact from/to diff (you 60→35% red · Maya 0→25% green tabular-nums), legal copy with mono timestamp, Cancel ghost + `Confirm with Face ID` primary (flex 1.5).

### A13.10 — Edit business page (`/businesses/[id]/page-editor.tsx`)
- **iOS:** **MISSING** — `Features/BusinessProfile/` has only the *public* view, not the editor. `Businesses/` has only the index + waitlist.
- **Android:** **MISSING** — same.
- **Status:** **BUILD**
- **Designed frames:** published (Roost Café · 3 unsaved tweaks · dirty bar) · setup (Patch & Paw · 3 of 7 sections · completion strip · "Save draft / Publish · 4 to go" bar)
- **Required slots:**
  - **`IdentityStrip`** at top (published mode) — business name + "Published · 6 days ago" meta + `Preview` text button on right.
  - **`CompletionStrip`** (setup mode) replaces IdentityStrip — segmented bar showing 3/7 sections complete + "4 sections to fill" meta.
  - **`BannerLogo`** (cover image + circular logo) — empty state: dashed drop targets with `Add banner` / `Add logo` labels; filled with dirty marker (amber rim + "New" chip).
  - **Sections (each = `Section` overline + body):** Name & tagline · Description (markdown supported · char counter 247/600) · Hours (7-row card · `HoursRow` with day · open · close · dirty-dot · "Holiday hours" footer hint) · Services (`ServiceChip` flow row with `Dine-in` · `Takeaway` etc + `AddServiceChip` plus button) · Gallery (3×N grid of `GalleryTile`s, drag-to-reorder hint, fresh-upload amber rim) · Contact (phone +1 prefix · email · website https://prefix · booking link) · Location (address with map-pin trailing icon + `MapPreview` with pin · "Hide exact address until contact" toggle).
  - **`BizLabel`** — field label with `required` asterisk · `dirty` orange dot · optional `hint` italic.
  - **`StickySave`** — two modes: **dirty** ("3 unsaved · Discard outline + Save primary") · **setup** ("Save draft outline + Publish · 4 to go primary"). The "X to go" hint inside the publish button is the design's signature — verify it renders.

### A13.13 — Manage train (`/support-trains/[id]/manage.tsx`)
- **iOS:** `Features/SupportTrains/Manage/ManageTrainView.swift` + `ManageTrainViewModel.swift` + `Components/{TrainContextStrip, StatCellRow, SendUpdateForm, OrganizeSection, CloseTrainSheet}.swift` (P4.3).
- **Android:** `ui/screens/support_trains/manage/ManageTrainScreen.kt` + `ManageTrainViewModel.kt` + `components/{TrainContextStrip, StatCellRow, SendUpdateForm, OrganizeSection, CloseTrainSheet}.kt` (P4.3).
- **Status:** **BUILT** (P4.3) — snapshot baselines pending follow-up commit; wired from `pantopus://support-trains/:id/manage` + new `.manageTrain(trainId:)` route; A10.9 detail dock-overflow's `onOpenManage` callback now pushes the Manage Train route (replacing the pre-P4.3 review-signups stub).
- **Designed frames:** active train mid-edit (Murphy meal train · day 12/21 · 18 slots filled · draft note typed · audience: all helpers · push on · `Send update` CTA enabled) · closing sheet (close confirmation bottom sheet · summary stats · optional thank-you note · destructive "Close & thank" red CTA)
- **Required slots:**
  - **`TrainContextStrip`** — title (`Murphy meal train`) + days-progress (12/21) + audience meta.
  - **`StatCell` row** in a 4-cell card: `18/21 Slots` success-tone · `12 Helpers` neutral · `9d Left` neutral · `1 Dropout` warn-tone.
  - **`SlotPreview`** strip — mini calendar showing the next 3 days with cell state.
  - **`Send an update` form section** — `Message` textarea (108pt tall, 168/500 char counter) + `Audience` chip row (`All helpers 12` selected sky · `Upcoming only 6` · `Family 3`) + push-to-phones row (bell icon + 13pt label + 11pt sub + iOS Toggle).
  - **`Organize` overline + 3-row card**: `Edit dates & slots` (yellow icon tile + `21` meta + sub) · `Invite more helpers` (sky icon tile + sub) · `Analytics` (green icon tile + sub).
  - **`Wind down` overline + red destructive `Close train` row**.
  - **`StickyCTA`** — `Send update` primary with send icon.
  - **`CloseTrainSheet`** — bottom sheet with red archive icon header + 3-cell summary stats (18 meals · 12 neighbors · 12d coverage) + italic recipient testimonial card + thank-you-note textarea (66pt tall) + Cancel ghost + `Close & thank` red primary (flex 1.4).

### A13.14 — Change password (`/settings/password.tsx`)
- **iOS:** `Settings/Password/PasswordChangeView.swift` (114 lines · uses `FormShell` with `rightActionLabel: "Save"`)
- **Android:** `settings/password/PasswordChangeScreen.kt`
- **Status:** **RESHAPE**
- **Designed frames:** ready (current verified green check ring · new password revealed in mono · 4 strength rules met · confirm matches · primary `Update password` CTA inline · "You'll be signed out of other devices" info chip · Cancel link) · error (form-level red banner · current "doesn't match" error · `Email me a reset link instead` shortcut · new password breach-flagged · confirm doesn't match · CTA locked)
- **Per-frame deltas:**
  - **Layout shift:** Save button moves from top-bar to **inline at the bottom of the body** (`UpdateButton` at end of scroll). Top bar gets back chevron + "Change password" title only; no top-right action. Current implementation uses `FormShell.rightActionLabel: "Save"` which puts it in the top bar — must replace with custom shell or extend FormShell with `inlineCommitOnly` mode.
  - **`ContextBand`** at top — surface card with mail icon + "Signed in as `maria@pantopus.app`" + clock icon + "Last changed `84 days ago`". Currently absent.
  - **`Verify it's you` overline + Current password `PasswordField`** with lock leading icon + valid-state green check + reveal toggle.
  - **`Choose a new one` overline + new password `PasswordField`** with **revealed mode** (when valid: shows password in monospace so user can sanity-check) + `StrengthMeter` (4-segment progress bar + 4 rule pills: `12+ characters` · `Mixed case` · `Number` · `Symbol` with check/x).
  - **Confirm password** with `Matches new password.` helper line in success-fg when valid.
  - **Info chip after CTA** (`primary50` bg + `info` icon + "You'll be signed out of other devices after updating.") — primary-700 fg, 11.5pt.
  - **Cancel link** — text button below Update, `primary600`, underlined.
  - **Error state**: **`FormBanner`** at top (error tone) with "Couldn't update password" + sub "Fix the two highlighted fields and try again. Three more attempts before a 15-minute cooldown."
  - **Per-field error state**: red ring on `PasswordField` + error text below + (for current pw) **`Email me a reset link instead`** shortcut (mail icon + sky link).
  - **Breach detection**: new password gets red ring + "Too common — appeared in 2.3M public records." error + StrengthMeter shows `breached` flag (full red bar).
  - **Confirm doesn't match**: "Doesn't match the new password above." error in red.
  - `PasswordField` primitive likely needs extension to support: `revealed: Bool`, `leftIcon: PantopusIcon`, `helper: String`, `error: String`, `state: .valid|.error|.default`.

### A13.15 — Disambiguate (`/mailbox/disambiguate.tsx`)
- **iOS:** `Mailbox/Disambiguate/DisambiguateMailFormView.swift` + `DisambiguateMailFormViewModel.swift`
- **Android:** `mailbox/disambiguate/DisambiguateMailFormScreen.kt` + `DisambiguateMailFormViewModel.kt`
- **Status:** **RESHAPED** (P7.3) — both platforms. Added `Components/{OcrStrip,CandidateRow,MatchBadge,QuickActionChip,FallbackRow}` and a token-pure envelope artwork overlaying the existing `EnvelopeOcrBox` primitive (clean = solid sky · unclear = dashed amber + water-stain). The VM gained OCR-confidence → tone gating (clean ≥ 0.6 auto-picks the strong match; unclear shows inert "best guesses" + a fallback card and disables Confirm with a hint), candidate match scoring (`MailMatchTier` strong/partial/weak), and quick-action handling (`This is me` → personal · `Route to…`). Candidate ranking is sample data (no candidates endpoint); resolve still POSTs `/mailbox/v2/resolve` with the selected candidate's drawer. Added iOS render-smoke tests + Android Paparazzi snapshots for both frames + 4 new icons (`user-check` / `forward` / `keyboard` / `undo-2`). **Follow-up:** run `./gradlew paparazziRecord` to capture the new Disambiguate baselines and refresh the icon-gallery baseline (this sandbox has no Android SDK).
- **Designed frames:** strong match (clean scan · sky OCR bounding box on name · 97% confidence pill · Maria pre-selected with Strong-match green badge · `This is me` quick-action chip · candidate list) · unclear scan (water-stain envelope · dashed amber OCR box · 31% warn pill · all candidates partial/weak · fallback card with Re-scan/Type/Return-to-sender/Mark-as-junk)
- **Per-frame deltas:**
  - **`EnvelopeClean` / `EnvelopeUnclear`** — needs bespoke envelope SVGs with the OCR bounding box overlay: clean = `2px solid primary500` rectangle around the name line; unclear = `2px dashed warning500` rectangle with water-stain texture overlay on the name area.
  - **`OcrStrip`** — pill below envelope card showing `tone=good` (success bg + check + "Maria K. · 412 Elm St" + "97% confidence" mono + "Address matches this household." sub) OR `tone=warn` (amber bg + alert + "M___ K___ · 4__ Elm St" + "31% confidence" + "Smudge on the name line. Try a brighter re-scan for a sharper read.").
  - **`Who is this for?` overline + QuickActionChip row**: `This is me` (user-check, primary sky chip) · `Route to…` (forward icon, neutral).
  - **`Candidate` row** — 44pt avatar with identity gradient + name + role chip (Owner sky-100/sky-700 · Resident homeBg/home · Guest sunken/fg2) + grant line (`Receives mail` / `No mail access`) + **match badge** (`Strong match · 97%` success · `Partial · 41%` amber · `Weak · 22%` neutral) + radio-style selected ring.
  - **`None of these — add new person`** text button (plus icon, primary600).
  - **Unclear frame: `Or resolve another way` overline + fallback card**: 4 rows in surface card — Re-scan envelope (scan-line) · Type recipient name (keyboard) · Return to sender (undo-2) · Mark as junk (trash-2 in error-tinted icon tile).
  - **`StickyConfirm`** — `Confirm recipient` primary (active in strong match, disabled with hint in unclear).

### A13.16 — My Mail Day (`/mailbox/mailday.tsx`)
- **iOS:** `Features/Mailbox/MailDay/MailDayView.swift` (+ `MailDayViewModel.swift`, `MailDayContent.swift`, `MailDaySampleData.swift`, `Components/{DayHeader, ScanMoreCard, UnreviewedItem, ReviewedRow, UndoCountdown, MailboxEmptyHero}.swift`). Pushed via `YouRoute.mailDay(variant:)` / `HubRoute.mailDay(variant:)`. Deep link: `pantopus://mailbox/mailday`.
- **Android:** `ui/screens/mailbox/mail_day/MailDayScreen.kt` (+ `MailDayViewModel.kt`, `MailDayContent.kt`, `MailDaySampleData.kt`, `components/{DayHeader, ScanMoreCard, UnreviewedItem, ReviewedRow, UndoCountdown, MailboxEmptyHero}.kt`). Pushed via `ChildRoutes.MAIL_DAY` (`mailbox/mailday/{variant}`).
- **Status:** **BUILT** (P4.4)
- **Designed frames:** mid-afternoon (8-piece stack · 6 routed · 2 pending · AI-suggested recipients with confidence % · Reviewed list compact · 5-second undo on latest) · empty (mailbox illustration with "0" face · 12-day streak chip · `Scan today's stack` primary · yesterday recap + setup nudges)
- **Required slots:**
  - **`DayHeader`** — date + streak chip + `ProgressRing` (56pt SwiftUI shape — done/total).
  - **`ScanMoreCard`** — scanner icon + "Scanned 22 min ago — drop more in?" + primary `Scan` button.
  - **`Needs a call` overline (with `· 2` muted count) + `UnreviewedItem` rows** — each: 56pt `MailThumb` (kind=bill / postcard / package etc.) + label + sender + AI-suggested-recipient avatar pile + confidence pill (`94%` in success-50) + primary `Route` button + secondary `Other` text.
  - **`Reviewed today · 6` overline + `ReviewedRow` stack** — compact rows in a single Card: kind icon + label + routed-to chip + time-meta. Latest row gets `undoCountdown` (5-sec circular timer next to "1 hr ago"). Action variants: `routed` / `junked` / `returned`.
  - **`Undo all from today`** text button below card (rotate-ccw icon, fg3).
  - **`FinishDay`** sticky footer — full-width disabled-until-empty primary CTA showing "Finish day · 2 remaining" or "Finish day · all done" active.
  - **Empty frame**: hero card with bespoke mailbox illustration (120×96 with shelf + body + flag + sparkles + "0" face in mono), "Nothing new today" h2, "No mail has been scanned since this morning. Drop today's stack on the scanner when you're ready." body, `Scan today's stack` primary CTA, streak chip ("12 days"), yesterday recap (colored stacked bar showing 5 routed / 2 junked / 1 returned), 2 setup-nudge cards ("Add another scanner location" · "Invite a household helper").

---

## A14 — Settings list (8 screens)

The A14 archetype already exists as `GroupedListView` (iOS) and equivalent
Android composable. Existing surface uses generic chevron rows + iOS
toggles. The new designs introduce **per-screen bespoke control vocabulary**
that goes beyond the standard chevron/toggle row.

### A14.1 — Home settings (`/homes/[id]/settings/index.tsx`)
- **iOS:** `Features/Homes/Settings/HomeSettingsView.swift` + `HomeSettingsViewModel.swift` + `HomeSettingsSampleData.swift`. Wired via `HubRoute.homeSettings(homeId:)` from the home dashboard's top-bar `slidersHorizontal` affordance.
- **Android:** `ui/screens/homes/settings/HomeSettingsScreen.kt` + `HomeSettingsViewModel.kt` + `HomeSettingsSampleData.kt`. Wired via `ChildRoutes.HOME_SETTINGS` from `HomeDashboardScreen.onOpenSettings`.
- **Status:** **DONE** (P5.1)
- **Designed frames:** established home (14 Elm Park Lane · address verified · 4 members · codes + trusted neighbors configured · all rows have meaningful subs) · newly claimed (amber `Verifying` chip on address · `Not set` / `Available after verification` subs · `Cancel claim` destructive)
- **Required slots:**
  - **Identity card** at top (home identity chip + address verified chip) — rendered via the shared `GroupedListView`'s new optional `headerView` slot.
  - **`Home identity` overline + 4 chevron rows**: Address (with `Verified` chip) · Property details · Photos · Documents.
  - **`Access` overline + 3 chevron rows**: Access codes (with N count) · Trusted neighbors · Privacy.
  - **`Members` overline + 2 chevron rows**: People (4 members · 1 pending) · Invite link.
  - **`Notifications` overline + 1 chevron row**: Home notifications.
  - **`Wind down` overline + destructive row**: Leave this home (or `Cancel claim` in newly-claimed variant).
- **Routing:** `HomeDashboardView` / `HomeDashboardScreen` take an `onOpenSettings` callback wired to the `ContentDetailShell` top-bar trailing action.

### A14.2 — Home security (`/homes/[id]/settings/security.tsx`)
- **iOS:** `Features/Homes/Settings/Security/HomeSecurityView.swift` + `HomeSecurityViewModel.swift`. Reached from the per-home Settings `Privacy` row.
- **Android:** `ui/screens/homes/settings/security/HomeSecurityScreen.kt` + `HomeSecurityViewModel.kt`. Reached from the Settings `Privacy` row.
- **Status:** **DONE** (P5.1)
- **Designed frames:** balanced (5 of 9 toggles on · helper text per card) · strict lockdown (all 9 on · helper text shifts to consequence language)
- **Required slots:**
  - **3 groups × 3 toggles**: Access control (Guest approval · Member name visibility · Address precision) · Privacy (Activity visibility · Map opt-out · Notification previews) · Documents (Doc lock · Photo blur · Vault auto-lock).
  - **Helper line per card** that **changes based on which toggles are active** — "Guest approval is on, so guests need an owner-tap to enter" (balanced) vs "Guest approval is off — anyone with a code is in. Tighten this if you're away." (off) vs "All guest activity requires your explicit approval. Names and street precision are hidden from outsiders." (all-on consequence). iOS+Android keep these strings word-for-word identical via `HomeSecurityViewModel` helpers + the `HomeSecurityHelpers` Kotlin object.
  - No chevrons, no destructive action — pure switchgear. Toggle persistence is stubbed to local state pending the backend `home_security_settings` table.

### A14.3 — Settings index (`/settings.tsx`)
- **iOS:** `Settings/SettingsView.swift` + `Settings/SettingsViewModels.swift::SettingsIndexViewModel`
- **Android:** `settings/SettingsScreens.kt` + `settings/SettingsViewModels.kt`
- **Status:** **POLISH**
- **Designed frames:** settled (all 4 verifications green · Stripe connected · tax info on file · 6 groups read calm) · mid-onboarding (2 of 4 verifications green · 2 amber `Verifying` · profile `Incomplete` chip · Payments `Connect` CTA chip)
- **Per-frame deltas:**
  - **`MeCard` at top** with avatar + name + handle + identity-chip row (Persona Verified · Local Verified). Verify the 11pt mono caption for "Member since Mar 24" is in design.
  - **`Verification` group** — 4 rows, each carrying a status chip in trailing position: ID (green check) · Phone (green check) · Email (green check) · Address (green check or amber Verifying).
  - **`Profile & privacy` group** chevron row.
  - **`Notifications` group** chevron row.
  - **`Payments` group**: in settled mode shows "Stripe · Connected" + "Chase ••••7421"; in onboarding mode shows `Connect` primary chip in trailing slot.
  - **`Account & security` group**: Password · Verification center · Sign-in history (new?).
  - **`Help & legal` group**: Help center · Privacy policy · Terms · About.
  - **Destructive `Sign out`** in its own card at bottom.
  - **Mono footer**: "Pantopus 3.4.1 (build 2241) · iOS 18.2" in `MonoFooter`.

### A14.4 — Blocked users (`/settings/blocked-users.tsx`)
- **iOS:** `Settings/Blocks/BlockedUsersView.swift` (42 lines · thin wrapper)
- **Android:** `settings/blocks/BlockedUsersScreen.kt`
- **Status:** **POLISH**
- **Designed frames:** 5 blocked (mixed-tenure list · source-context line · Unblock pill) · empty (centered grey user-x circle · "No one blocked" h2 · 2-sentence body · reassurance about silence)
- **Per-frame deltas:**
  - **Row recipe**: leading 36pt avatar (Avatar primitive) · `Blocked Aug 14 · From Pulse` source context line in fg3 · `Unblock` `PillButton` in neutral tone (border-grey, fg2).
  - **Helper line below card**: "Blocked users can't see your posts, message you, or appear in your search results. They aren't notified."
  - **Empty state** uses `EmptyState` primitive: user-x icon · "No one blocked" · "When you block someone, they show up here. Blocking is silent — they won't know."

### A14.5 — Notifications (`/settings/notification-preferences.tsx`)
- **iOS:** `Settings/SettingsViewModels.swift::NotificationSettingsViewModel` + `GroupedListView`
- **Android:** equivalent in `settings/SettingsViewModels.kt`
- **Status:** **RESHAPE**
- **Designed frames:** real mix (Tasks: push for replies · email for receipts · Pulse quiet · Home gets everything · security locked) · paused 2hr (amber countdown banner replaces master card · category cards dimmed beneath)
- **Per-frame deltas:**
  - **Three-channel matrix per row** (P / E / S as 22pt mono letter chips — Push/Email/SMS) — current `GroupedListView.Row.toggleRight` uses a single iOS toggle. **Must extend `RowTrailing` with a `channelTriad` variant.**
  - **`ChannelHeader` band** at top of each card showing the P/E/S column headers in `appSurfaceMuted` row above the first toggle row.
  - **Helper lines per card** that contextualize the active pattern ("Push only for things that need a fast reply. Receipts go to email so they're searchable.").
  - **5 category groups**: Tasks · Pulse · Marketplace · Home & Mailbox · Account & security.
  - **Locked-push for emergency alerts**: in `Home & Mailbox > Emergency alerts`, the P chip is sky-bg + lock icon overlay (can't be turned off); helper line below card: "Emergency alerts can't be muted on push."
  - **Master pause control** at top — "Pause notifications" with iOS toggle + chevron-right for duration sheet.
  - **Paused secondary**: master card replaced with `PauseBanner` — warm amber bg + warning-tinted bell-off icon + "Paused for 1h 24m left" + `Resume` neutral pill. Category cards stay rendered at 0.5 opacity.

### A14.6 — Payments (`/settings/payments.tsx`)
- **iOS:** `Features/Settings/Payments/PaymentsView.swift` + `PaymentsViewModel` (P5.2 — fixture-driven; Stripe Connect onboarding deep-link is a follow-up).
- **Android:** `ui/screens/settings/payments/PaymentsScreen.kt` + `PaymentsViewModel`.
- **Status:** **POLISH** (note: overlaps with A10.10 Wallet — different surface for different intent: this is settings-side payments, Wallet is earnings-side).
- **Designed frames:** populated (balance hero · 3 methods · Stripe connected · payouts to Chase weekly · YTD activity) · empty (no balance hero · inline empty in Methods card · Stripe `Connect` chip · gated payout method + tax rows)
- **Required slots:**
  - **Balance hero** (same `BalanceHero` primitive as A10.10, but with `next-payout-date` + frequency pill chip on right of the available amount instead of the "USD" chip).
  - **`Payment methods` overline + Card** of methods:
    - Visa ····4523 (default · Mar 24 expiry · Default chip)
    - Mastercard ····7892 (Apr 25 expiry)
    - Apple Pay (active · 1 default)
    - **`Add payment method`** as a final **blue row** inside the same card (sky `primary600` text + plus icon · iOS convention).
  - **`Payouts` overline + Card**: Stripe (`Connected · Verified` chip OR `Connect` primary chip in empty) · Payout method (Chase ••••7421 · Weekly) · Tax info (W-9 on file).
  - **`Activity` overline + Card**: 3 rows (Lifetime · $9,847 · YTD · $3,184 · Last payout · Nov 28).
  - **Empty state**: no balance hero (nothing to surface); Methods card shows inline empty + Add row; Stripe row shows `Connect` chip; Payout method and Tax info rows render with grey `lock` glyph and "Available after Stripe connect" sub.

### A14.7 — Privacy (`/settings/privacy.tsx`)
- **iOS:** `Settings/SettingsViewModels.swift::PrivacySettingsViewModel` + `GroupedListView`
- **Android:** equivalent
- **Status:** **RESHAPE**
- **Designed frames:** everyday defaults (verified-only profile · street name on profile · "Block" location fuzz · balanced activity) · stealth (dark stealth banner · profile hidden · address hidden · fuzz at Neighborhood biggest blue circle · every activity off · helper consequence language)
- **Per-frame deltas:**
  - **`Profile visibility` group — RadioCard** with 3 options: `Anyone` · `Verified neighbors only` (default) · `Hidden` (stealth) — radio buttons (`Radio` primitive) on right. Helper line below: "Verified neighbors see your full profile. Others see only your name and verified badges."
  - **`Address` group — RadioCard** with 3 options: `Street + apt` · `Street only` (default) · `Hide entirely`.
  - **`Location fuzz` group — `FuzzMap` slider** — 5 stops: Exact · Building · Block · **Block (default)** · Neighborhood. Each step grows the concentric circle on the faint street-grid map preview above the slider (`primary600` 1.5px ring + `primary600` 0.18 alpha fill + sky-blue center pin). Stop label shows in mono corner tag ("BLOCK"). **The map preview is the design's signature primitive — must render live as user drags.**
  - **`Activity` group — toggles**: Hide from search · Show online status · Show last active · Show read receipts · Share home check-ins.
  - **`Data` group — chevron rows**: Download my data · Delete my account (destructive).
  - **Stealth banner** in stealth mode: dark `#0b1220` bg, sky-tinted eye-off icon disc, "Stealth mode is on" + "Your profile is hidden from search. Existing connections still see you." — pinned above the radios.

### A14.8 — Vacation hold (`/mailbox/vacation.tsx`)
- **iOS:** `Features/Mailbox/Vacation/` (P5.3).
- **Android:** `ui/screens/mailbox/vacation/` (P5.3).
- **Status:** **POLISH** (P5.3 BUILD landed)
- **Designed frames:** scheduling (13-day range · `DateSpan` strip · scope toggles all on · civic locked · forwarding · emergency contact · `Save` in top bar primary-600) · active (sky-gradient `HoldStatusHero` · 5 days left · 3-cell stats · ledger of held items · top bar swaps Save for neutral `End hold`)
- **Build dependencies:** `DateSpan` primitive + `HoldStatusHero` primitive.
- **Required slots:**
  - **Scheduling frame:**
    - **`When` card**: From date picker · `DateSpan` mini-timeline strip showing "13 days" pill on dashed sky line + "MON · WED" weekday labels · To date picker.
    - **`What to hold` toggles card**: Mail · Packages · Magic Task delivery · Civic notices (`locked` chip — civic always hold). Each toggle has a sub line describing what stops.
    - **`Forwarding` card** (optional · 1 chevron row): "Forward to Mom's place · 1456 Cedar Pkwy".
    - **`Emergency contact` card** (1 chevron row): "Sam (brother) · (415) 555-0188".
    - Top bar: back + "Vacation hold" + `Save` text button in `primary600` (disabled when not valid).
  - **Active frame:**
    - **`HoldStatusHero`** — `linear-gradient(140deg, primary600 → primary800)` dark card, "Hold active" pill with pulsing sky-300 dot + "until Dec 12" mono · `5 days left` huge 32pt + 18pt secondary sub · 3-cell stats grid (4 letters held · 1 package · 2 forwarded).
    - **`What's holding` overline + `HeldList`** — rows showing each held item type with count.
    - **`Forwarding` + `Emergency contact`** cards still rendered, both read-only.
    - Top bar: back + "Vacation hold" + neutral `End hold` text button in `appText`.

---

## A17 — Mailbox detail variants (10 mail types)

All A17 variants plug into `MailItemDetailShell` (iOS) / `MailboxItemDetailShell.kt` (Android). 3 of 10 have bespoke iOS layouts (Booklet, Certified, Community). Android has 6 of 10 body files (Booklet, Certified, Community, Coupon, Gig, Memory) — Android is ahead on body coverage.

### A17.1 — Generic mail item
- **iOS:** falls through to `GenericMailDetailLayout` in `MailDetail/MailDetailView.swift:131`
- **Android:** `mailbox/item_detail/bodies/CategoryBodies.kt` (generic fallback)
- **Status:** **POLISH**
- **Per-frame deltas:** verify hero card (accent strip + trust chip + category chip + sender uppercase + title + reference mono + optional acknowledged banner), ElfStrip ("Pantopus read this for you" + 3 bullets), KeyFacts (icon + label/value/note rows), BodyCard, Attachments, SenderCard, BottomTabBar all render.

### A17.2 — Booklet
- **iOS:** `Mailbox/MailDetail/Variants/BookletDetailLayout.swift`
- **Android:** `mailbox/item_detail/bodies/BookletBody.kt`
- **Status:** **POLISH**
- **Per-frame deltas:** verify multi-page swiper, polaroid frame for cover.

### A17.3 — Certified mail
- **iOS:** `Mailbox/MailDetail/Variants/CertifiedDetailLayout.swift`
- **Android:** `mailbox/item_detail/bodies/CertifiedBody.kt`
- **Status:** **POLISH**

### A17.4 — Community mail
- **iOS:** `Mailbox/MailDetail/Variants/CommunityDetailLayout.swift`
- **Android:** `mailbox/item_detail/bodies/CommunityBody.kt`
- **Status:** **POLISH**

### A17.5 — Coupon
- **iOS:** `Mailbox/MailDetail/Variants/CouponDetailLayout.swift` (composes `MailItemDetailShell` with `Mailbox/ItemDetail/Bodies/CouponBody.swift` + `Bodies/Components/CouponHero.swift` + `Bodies/Components/BarcodeView.swift`).
- **Android:** `mailbox/mail_detail/variants/CouponDetailLayout.kt` (composes the parity shell with `mailbox/item_detail/bodies/CouponBody.kt` + `bodies/components/CouponHero.kt` + `bodies/components/BarcodeView.kt`).
- **Status:** **POLISH** (BUILD landed in PR #138 — A17.5–A17.8 ceremonial detail variants; dispatch wired through `MailDetailView` / `MailDetailScreen` and `MailDetailContent.couponDetail`).
- **Per-frame deltas:** verify ticket-style `CouponHero` (amber gradient + brand chip + 42pt discount + dashed code capsule + ticket-stub barcode), `KeyFacts` (Merchant · Code · Min. spend · Expires), `FinePrintCard` (bulleted terms + fine print), `StoreBarcodeCard` with show-in-store expand toggle, redemption-state CTA (`Mark redeemed` primary → `Already redeemed` success pill → `This offer has expired` terminal). Sample fixtures live in `MailItemSampleData.coupon{Unused,Redeemed,Expired}`; snapshot coverage in `CouponBodySnapshotTests` (4 cases) + `CeremonialVariantsSnapshotTests` (layout unused / redeemed).

### A17.6 — Gig mail
- **iOS:** **MISSING** — falls through to GenericMailDetailLayout.
- **Android:** has `mailbox/item_detail/bodies/GigBody.kt` + `components/GigCard.kt` + `components/BidCard.kt`.
- **Status:** **BUILD** (iOS) · **POLISH** (Android)
- **Required slots (iOS only):** Gig hero (job summary + estimated payout), KeyFacts (location · when · category), other bids strip, Accept/Decline split dock.

### A17.7 — Memory
- **iOS:** `Mailbox/MailDetail/Variants/MemoryDetailLayout.swift` (composes `MemoryBody` + `PolaroidFrame`).
- **Android:** `mailbox/mail_detail/variants/MemoryDetailLayout.kt` (composes `MemoryBody` + `PolaroidFrame`) + `MemorySampleData.kt`.
- **Status:** **POLISH**
- **Per-frame deltas:** verify hero card (polaroid frame + caption + date-from line) and the Save-to-Vault / saved-pill action swap.

### A17.8 — Package
- **iOS:** `Mailbox/MailDetail/Variants/PackageDetailLayout.swift` (ceremonial) + `Mailbox/ItemDetail/Bodies/CategoryBodies.swift` `PackageBody` (shared body). New components: `Variants/Components/CarrierBadge.swift`, `Variants/Components/PackageTrackingTimeline.swift`.
- **Android:** `mailbox/item_detail/bodies/PackageBody.kt` (shared body, extracted from `CategoryBodies.kt`) + `mailbox/mail_detail/variants/PackageDetailLayout.kt` (ceremonial). New components: `bodies/components/CarrierBadge.kt`, `bodies/components/PackageTrackingTimeline.kt`.
- **Status:** **POLISH**
- **Slots delivered:** Package hero (`CarrierBadge` + tracking number mono + status pill), `PackageTrackingTimeline` (Shipped → In transit → Out for delivery → Delivered), KeyFacts (carrier · service · dimensions · weight · tracking · ETA), proof photo (front-door snapshot when delivered), split dock `Track on carrier` (opens carrier URL) + `Confirm pickup` primary.
- **Fixtures:** USPS (`packageInTransit` / `packageOutForDelivery` / `packageDelivered`) + UPS (`packageUpsInTransit` / `packageUpsDelivered`) in both `MailItemSampleData`. `PackageBodyContent` carries `service` / `dimensions` / `weight` / `trackingUrl`; decoders on both platforms project them from the backend payload.

### A17.9 — Party (event invite)
- **iOS:** **MISSING**
- **Android:** **MISSING**
- **Status:** **BUILD** (both platforms)
- **Build dependencies:** `ConfettiSpray` primitive (rose-magenta dot pattern over hero), polaroid host avatar.
- **Required slots:**
  - **PartyNav** — mailbox back + Party eyebrow chip (rose `#db2777` dot) + bookmark/more.
  - **PartyHero** — festive title + date pill + location pill + confetti dot pattern overlay + state-specific copy (open shows "Saturday May 24" headline · going shows "You're going on May 24" green check).
  - **PartyElf strip** — 3 AI bullets ("5 friends going" · "71° clear evening" · "Saturday is clear").
  - **HostCard** — Priya, polaroid-flavor avatar with rose ring + "Friend · neighbor".
  - **EventDetails** — `DateTile` (rose tile with SAT / MAY / 24 stacked) + VibeRows (dress · kids · weather · map).
  - **GoingStrip** — overlapping avatar pile + +N stat + your-avatar prepended in going state.
  - **PartyNote** — handwriting-feel personal note + "Priya x" signature.
  - **PotluckList** — 4 BRING rows (item + emoji + claimedBy avatar + claim button on right).
  - **RsvpCluster** — 3-way Going / Maybe / Can't make it (Going = rose primary in open state, green check in going state) + PlusOneStepper visible only in going state.

### A17.10 — Records
- **iOS:** **MISSING**
- **Android:** **MISSING**
- **Status:** **BUILD** (both platforms)
- **Build dependencies:** `PaperStack` primitive (multi-page tilted sheets — 3 PaperSheets at z-stacked rotations, slate `#475569` accent).
- **Required slots:**
  - **RecordsNav** — slate `#475569` dot in eyebrow chip.
  - **RecordsHero** — `HeroCard` variant with slate accent strip + "Q1 2026 Investment Statement — Roth IRA" + reference mono ("Statement MWM-2026-Q1-9981842 · 4 pages · PDF + structured data") + green `Filed in Vault` stamp when filed.
  - **RecordsElf strip** — "Pantopus opened this for you" with DKIM-verified · +4.2% · suggested filing-path bullets.
  - **IssuerCard** — institution avatar (slate gradient) + dept + CRD#/FINRA identifier mono + "Sender domain DKIM-verified" trust note.
  - **`PaperStack`** — multi-page paper hero with stacked tilted PaperSheet primitives showing the doc preview (shim-lines for content).
  - **KeyFacts grid** — Account (mono ····4421) · Period covered · Ending balance ($84,237.16 emphasis) · Net change (+$3,419.08 success tone emphasis) · Statement date. Filed variant prepends a `Status · Filed in Vault` row.
  - **RecordsBody** — excerpt from the statement.
  - **VaultDestination breadcrumb** — Mailbox › Vault › Finance › Statements › 2026 (with current highlighted).
  - **RelatedRecords** strip — only in filed state — Q4/Q3/Q2 2025 quarterlies with amounts and filed dates.
  - **RecordsActions** — primary `File in vault` (open) → in filed state: retention timer ("Stored for 7 years · auto-delete prompt Apr 2033") + secondary actions (Open PDF · Share · Download structured JSON).

---

## A18 — Status / waiting / preview

All A18 screens share the same shape: TopBar + centered body (HaloCircle 96pt → h2 → sub + status pill + optional 3-step Timeline) + StickyDock. Already partly built via `StatusWaitingView`.

### A18.1 — Verify email sent (`/(auth)/verify-email-sent.tsx`)
- **iOS:** `Status/StatusWaitingView.swift` (used via `.checkYourEmail(email:)` factory)
- **Android:** `status/StatusWaitingScreen.kt` (equivalent)
- **Status:** **POLISH**
- **Designed frames:** populated (mail-check halo · "Check your email" h2 · email-bolded body · neutral "Waiting for link click…" pill with spinning hourglass · Open Mail / Resend / Use different email stack · spam-hint footer) · just resent (success "New link sent · just now" pill replaces waiting · Resend disabled with "Resend in 0:42" timer · footer "Still nothing? Double-check spelling")
- **Per-frame deltas:**
  - **`HaloCircle`** at 96pt with success-bg ring outside primary disc — verify the exact halo structure (current `StatusWaitingView` uses a generic illustration; check it matches the 96pt double-ring composition).
  - **No timeline** for this screen — verify the StatusWaitingView omits timeline when timeline array is empty.
  - **Waiting pill** with `hourglass` icon + `spin 4s linear infinite` animation.
  - **3-button stack** at bottom (not split): Open Mail app primary 50pt with sky shadow · Resend email outline 46pt with border · Use a different email underline-link 36pt.
  - **Spam hint footer** in `appTextSecondary` 10.5pt with info icon, copy: "Can't find it? Check spam or your 'Promotions' tab." → "Still nothing? Double-check the spelling, or use a different email." (cooldown).
  - **Resent state**: success-bg green pill replaces neutral waiting pill, Resend swaps to disabled grey button with `timer` icon + "Resend in 0:42" countdown, primary Open Mail unchanged.

### A18.2 — Claim submitted (`/homes/[id]/claim-owner/submitted.tsx`)
- **iOS:** `Status/StatusWaitingView.swift` (used via `.claimSubmitted(homeName:)` factory)
- **Android:** equivalent
- **Status:** **POLISH**
- **Designed frames:** fresh submission (green check halo · "Claim submitted" h2 · "We'll review within 3 business days" body · address chip · timeline Submitted-done/Under review-pending/Decision-pending · success-bg ETA pill "Decision expected by Oct 17" · View status / Back to home dock) · approved (badge-check halo · "You're the owner" h2 · timeline all-done with real dates · ETA pill becomes "Approved · 3 days ago" green · dock pivots to "Open your home" / "See your Home badge")
- **Per-frame deltas:**
  - **HaloCircle icon swap** between states: `check` (submitted) → `badge-check` (approved).
  - **Address chip** in muted bg + home-pin sky icon — verify rendered.
  - **TimelineCard** with 3 steps · animated done/current/pending states · success-green line for all-done.
  - **ETA pill** swaps copy + tone: amber "Decision expected by Oct 17" → green "Approved · 3 days ago" with check-circle-2 icon.
  - **Dock label swap** on approval: primary "View status" → "Open your home"; secondary "Back to home" → "See your Home badge".

### A18.3 — Verification submitted (`/homes/[id]/verify-landlord/submitted.tsx`)
- **iOS:** **NOT YET BUILT** as a dedicated StatusWaitingContent factory — `Status/StatusWaitingContent.swift` has `claimSubmitted` and `checkYourEmail` but no `landlordSubmitted` / `verificationSubmitted`.
- **Android:** same.
- **Status:** **BUILD** (factory only — view reuses existing StatusWaitingView)
- **Designed frames:** waiting on landlord (check halo · "We sent your verification" h2 · "Mira at mira@elmstholdings.com confirms next" body · address chip · timeline Lease+ID done / Landlord confirms pending / Verified pending · "Most landlords confirm in 1–2 days" ETA · primary `Back to home` · secondary "View status") · landlord confirmed (`user-check` halo · "Landlord confirmed" h2 · "Mira Patel signed off on Apr 6" body · middle step flips to done · "Verified" step current with pulsing glow · sky primary ETA "Just one more step · Pantopus reviewing now")
- **Per-frame deltas:**
  - **Primary CTA inverts** vs A18.2: design uses `Back to home` as the primary (user can't speed it up, so no "View status" upsell as primary).
  - Halo icon swap on landlord-confirmed: `check` → `user-check`.
  - Timeline middle step references *the landlord*, not Pantopus.
  - ETA tone shifts: amber "Most landlords confirm in 1–2 days" → primary sky "Just one more step · Pantopus reviewing now".

---

## A21 — Public Beacon profile

### A21.1 — Persona profile (`/persona/@handle`)
- **iOS:** `AudienceProfile/AudienceProfileView.swift` (1412 lines) + `AudienceProfileContent.swift` + `AudienceProfileViewModel.swift`
- **Android:** `audience_profile/AudienceProfileScreen.kt` + content + viewmodel
- **Status:** **POLISH**
- **Designed frames:** populated visitor (sky banner · 4 category chips · 3 broadcasts including 1 Bronze+ locked · Follow primary + share kebab) · empty (Otis Park new · 0 broadcasts · radio-tower empty + Follow CTA)
- **Per-frame deltas:**
  - **`Banner`** must be sky-personal identity-tinted (`primary600`) hero band ~120pt tall, then **`IdentityBlock`** with `-40pt` top margin overlapping the banner.
  - **`IdentityBlock`** must have: 72pt circular avatar at `-36pt` (overlapping banner) with `VerifDot` corner badge in `primary600`; name 22/700 + handle 12/fg3 + **tier chip** ("Persona · Verified" in gold-bg crown chip); bio 3-line clamp; divider; 3 `StatCell` row (Beacons · Broadcasts · Member-since).
  - **Action area for visitor:** share kebab ghost + Follow primary CTA with plus icon. (For owner: analytics ghost + Edit ghost. For current_member: share + gold Bronze TierBtn.)
  - **`CategoryChips`** — flow row of category accent chips (Illustration · Watercolor · Sketchbook · Brooklyn).
  - **`TabStrip`** — Broadcasts (with count) · About · Tiers (with count). Active tab has sky underline.
  - **`BroadcastCard`** feed: each card has time + visibility pill (`Free` neutral / `Bronze+` gold) + body + optional media gradient + reactions/replies. **Locked variant** (Bronze+ post visited by Free user) shows blurred preview + lock overlay + "Unlock with Bronze" upsell card with gold CTA.
  - **Empty state** (per `screen-parity-inventory.md` line ~74, this is the documented gap): full `EmptyState` with 72pt `primary50` circle + `radio-tower` icon + "Quiet for now" h2 + body + `Notify when live` primary CTA with `bell-plus` icon. **Confirmed missing** in current iOS `PublicProfilePostsFeed:396` and Android `PublicProfileChrome.kt:387` which render only a single line "No broadcasts yet — check back soon." This is the PRIMARY POLISH item for A21.1.

### A21.2 — Local profile (`/local/@handle`)
- **iOS:** `Profile/PublicProfileView.swift` + `PublicProfileNeighbor.swift` + `PublicProfileChrome.swift` (~1700 lines combined)
- **Android:** equivalent
- **Status:** **POLISH**
- **Designed frames:** populated visitor (green Home banner · verified-neighbor shield chip · locality "Chestnut Hill" · 3 posts with Pulse intent chips Offer/Alert/Event · Message primary + Connect ghost · no Tiers tab) · empty (Priya new neighbor · 0 posts · home-tinted "Quiet for now" with `Send a message` CTA)
- **Per-frame deltas:**
  - **`Banner` identity-tinted home green** (`#16A34A`).
  - **`IdentityBlock` action area for visitor**: `Connect` ghost (user-plus icon) + `Message` primary (message-square icon). **No Tiers tab.**
  - **Verified-neighbor shield chip** in `homeBg`/`home` with shield-check icon.
  - **Locality pin chip** (map-pin + "Chestnut Hill").
  - **`LocalPostCard` feed** with Pulse-style intent chips: Offer (home green · hand icon) · Alert (warning amber · triangle-alert icon) · Event (personal sky · calendar icon).
  - **Empty state** — same `EmptyState` primitive: home-tinted icon disc + "Quiet for now" + "No posts yet — Priya just moved in. Say hi or send a message to break the ice." + `Send a message` primary CTA with message-square icon.

---

## Phase order recommendation (revised)

Given the audit above, here's a concrete sequencing:

**Phase 1 — primitives** (1 PR, both platforms, no screen changes yet)
Add 13 primitives listed in the Summary table to `Core/Design/Components/` (iOS) and `ui/components/` (Android). Each ships with previews and isolated snapshot tests.

**Phase 2 — BUILD batch A (wizards)** (2 PRs each — wizard + sample data + view-model + routing):
1. A12.5 Verify Landlord Start + A12.6 Verify Landlord Details + A12.7 Postcard Verification (3 screens but share infrastructure — one `Homes/VerifyLandlord/` folder).
2. A12.10 Create Business wizard (`Businesses/CreateBusiness/`) — **the wizard chrome must accept an `identity: .business` param so the violet accent flows through**. This is a small but cross-cutting `WizardView` change.

**Phase 3 — BUILD batch B (details)** (per-screen PRs):
1. A10.9 Support Train detail (`SupportTrains/Detail/`) — depends on `SlotCalendar`.
2. A10.10 Wallet (`Wallet/`) — depends on `BalanceHero`.

**Phase 4 — BUILD batch C (forms)** (per-screen PRs):
1. A13.4 Transfer Ownership (`Homes/Owners/Transfer/`).
2. A13.10 Edit Business Page (`Businesses/PageEditor/`).
3. A13.13 Manage Train (`SupportTrains/Manage/`).
4. A13.16 My Mail Day (`Mailbox/MailDay/`).

**Phase 5 — BUILD batch D (settings)** (1 PR per screen, batchable):
1. A14.1 Home settings + A14.2 Home security (`Homes/Settings/`).
2. A14.6 Payments (`Settings/Payments/`).
3. A14.8 Vacation hold (`Mailbox/Vacation/`).

**Phase 6 — BUILD batch E (mail variants)** (one PR per variant):
1. iOS: A17.5 Coupon, A17.6 Gig mail, A17.7 Memory, ~~A17.8 Package (iOS+Android)~~ ✅ done, A17.9 Party (iOS+Android), A17.10 Records (iOS+Android).
2. Android: ~~A17.8 Package~~ ✅ done, A17.9 Party, A17.10 Records (Android catches up).

**Phase 7 — RESHAPE batch** (per-screen PRs):
1. A13.14 Change Password (move Save inline, add strength meter, breach detection, context band).
2. A13.3 Review Claim (Accept/Challenge/Reject vocabulary swap, evidence thumbnails, ChallengeComposerSheet with reason chips).
3. A13.15 Disambiguate (OCR bounding boxes, OcrStrip, candidate match badges, fallback row vocabulary).
4. A12.11 Start Support Train (warm-amber re-theme, ReasonPicker tiles, InviteRecipientCard, StepRail).
5. A14.5 Notifications (3-channel P/E/S matrix — extends `GroupedListView` row trailing).
6. A14.7 Privacy (RadioCard + FuzzMap slider + Stealth banner — extends `GroupedListView` row variants).

**Phase 8 — POLISH batch** (one PR per cluster):
1. A03.1 Pulse + A03.2 Beacons (verify chip row + reaction matrix + Event RSVP variant + footer chip).
2. A09 (all 4 — verify per-state coverage, Magic Task modules, hero carousel, identity dots, Sold/Paid/Awarded variants).
3. A12.4 Claim Evidence (UploadSlot states + ClaimStatement + encryption footer).
4. A14.1 / A14.3 / A14.4 (settings index polish + blocked users empty state).
5. A18.1 / A18.2 / A18.3 (HaloCircle composition + timeline polish + dock-label swaps + new A18.3 factory).
6. A21.1 / A21.2 (empty-state full card with primary CTA — confirmed gap in existing parity inventory).

**Phase 9 — Snapshot lockfile** — add `__snapshots__/new-designs/` PNGs + `T7ScreensSnapshotTests.swift` tripwire + Android paparazzi equivalent.

---

## Open questions for product / design

1. **A21.1 Persona "owner" mode** — the existing parity inventory flags this as a missing frame too (analytics + Edit chrome). Design pack didn't include the owner frame in this hand-off. Confirm: is owner-mode in scope here, or deferred?
2. **A14.6 Payments vs A10.10 Wallet** — these are two distinct surfaces for adjacent intents (payments-out vs earnings-in). Confirm they should both exist, with the Wallet hosted under the `Wallet` tab/route and Payments hosted under Settings.
3. **A12.10 Create Business** — the new design adds a typeahead/search frame with "Add as custom category" fallback. Confirm the back-end supports custom-category submissions (review-required) or whether we should hide the fallback until backend is ready.
4. **A17.9 Party + A17.10 Records** — design uses `db2777` rose-magenta + `475569` slate accents that are **not** currently in `Colors.swift` / `Color.kt`. Confirm we extend the token system with `category-party` and `category-records`, or use raw hex (currently 158 hex literals already in allowed exceptions for category palettes).
5. **A18.3 Verification submitted** primary CTA — design uses "Back to home" as primary. Confirm this isn't a typo and we shouldn't surface "View status" as primary.

---

*End of audit. Next: Phase 1 primitives PR.*
