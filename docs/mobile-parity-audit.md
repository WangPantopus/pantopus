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
  `archived` / `muted`), and `footer` (1–3 in-card 34pt buttons).
  T5.3.3 adds `headerChips[]` (chip row above the body, sharing the
  trailing kebab line), `engagement` (hairline-separated engagement
  strip with display-only items + optional trailing text-button CTA),
  and `bodyEmphasis` (`secondary` default / `primary` for My posts
  where the body IS the row's headline). All four are additive and
  default to `nil`/`undefined`.
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
| Login (T6.1b) | n/a (form-only) · inline error banner on submit failure · per-field error highlight | `loginEmailField` / `loginPasswordField` / `loginPasswordVisibilityToggle` / `loginForgotPasswordLink` / `loginCreateAccountLink` / `loginSubmitButton` / `loginErrorBanner` | `loginScreen` + same field / link / submit / banner tags | `POST /api/users/login` | Redesigned against `auth-frames.jsx` frame 1 (default) + frame 6 (inline error banner). Per Q3 the v1 surface is email-only — no phone field, no SSO row. iOS + Android use a shared `LoginViewModel` shape — typed `AuthError?` + `canSubmit` + `clearError`. Forgot-password / create-account links push `AuthRoute.forgotPassword` / `.signUp` via the `AuthNavHost` (Android) / `NavigationStack(path:)` (iOS). Existing sign-in semantics preserved (still hits `auth.signIn` → `AuthManager` / `AuthRepository`). |
| Create account (T6.1b) | per-field error (live after first submit) · top-level `AuthError` banner · bottom sticky CTA disabled until form valid + terms accepted · per-platform native date picker (DOB) with 18+ enforcement | `signUpEmailField` / `signUpPasswordField` / `signUpConfirmPasswordField` / `signUpUsernameField` / `signUpFirstNameField` / `signUpMiddleNameField` / `signUpLastNameField` / `signUpDateOfBirthField` / `signUpPhoneField` / `signUpAddressField` / `signUpCityField` / `signUpStateField` / `signUpZipField` / `signUpAccountTypePicker` / `signUpInviteCodeField` / `signUpTermsCheckbox` / `signUpPasswordStrengthMeter` / `signUpErrorBanner` / `formBottomCommitButton` | `signUpScreen` + same per-field / picker / checkbox / strength / banner tags + `formBottomCommitButton` | `POST /api/users/register` | New surface — extends `FormShell` with an additive `bottomActionLabel` slot (sticky full-width primary CTA below the scroll area). 14 fields (`AccountType` segmented Personal / Business; client-side 18+ + E.164 + password strength bucket validators in `AuthValidation`). Success transitions to `AuthRoute.verifyEmail` per the backend hard-gate behaviour (see `docs/mobile/auth-backend-contracts.md` § Backend gap discovered). Web register page re-skinned only — keeps its existing field set + OAuth flow. |
| Auth error (T6.1b) | Headline + body sourced from `AuthErrorViewModel.copy(_:)`; `Try again` rendered only for transient errors (`networkError` / `rateLimited` / `serverError` / `unknown`); `Go back` always available | `authErrorScreen` / `authErrorRetryButton` / `authErrorBackButton` | same | (no endpoints — pure UI surface) | New full-screen destination for non-form auth errors (`AuthRoute.error(AuthError)`). Inline banner on Login / SignUp uses the same `AuthError` taxonomy but renders as a top-of-form `ErrorBanner` row (matches `auth-frames.jsx` frame 6). Copy intentionally avoids leaking raw backend strings — covered by `AuthErrorViewModelTests` `ServerError does not leak raw message`. |
| Forgot password (T6.1c) | Form (back chevron + email field + "Send reset link" CTA) → Status / Wait `Check your email` (resetLinkSent factory) with `Resend` primary CTA + `Back to login` ghost CTA. Local 30s resend cooldown on top of the backend's `forgotPasswordLimiter`. | `forgotPasswordScreen` / `forgotPasswordBackButton` / `forgotPasswordEmailField` / `forgotPasswordSubmitButton` / `forgotPasswordErrorBanner` / `forgotPasswordSentStatus` (+ inner `statusPrimaryCta` / `statusSecondaryCta`) | same | `POST /api/users/forgot-password` | Renders `auth-frames.jsx` frame 3. The `Sent` phase reuses the shared `StatusWaitingView` (iOS) / `StatusWaitingScreen` (Android) archetype with new `resetLinkSent(email:)` factory — same shell as the homes-claim flows so the visual contract is consistent. View-model holds a wall-clock-driven cooldown so a frustrated double-tap is a silent no-op (`ForgotPasswordViewModelTests`/`ForgotPasswordViewModelTest`). Backend always returns generic 200 to prevent enumeration; client surfaces "Check your email" regardless. Web `(auth)/forgot-password` re-skinned with kicker + h2 + same body copy. |
| Reset password (T6.1c) | Form (X close + new password + confirm + 3-band strength meter + `Set password` CTA) → Status / Wait `Password reset` (passwordReset factory) with `Back to login` primary CTA + 3 s auto-redirect to login. | `resetPasswordScreen` / `resetPasswordCloseButton` / `resetPasswordPasswordField` / `resetPasswordConfirmField` / `resetPasswordStrengthMeter` / `resetPasswordSubmitButton` / `resetPasswordErrorBanner` / `resetPasswordSuccessStatus` | same | `POST /api/users/reset-password` | Deep-linked from the password-reset email via `pantopus://auth/reset-password?token=…`. `token` is the hashed Supabase recovery token; the view-model carries it from the deep-link route arg into the request body. Submit is gated on both passwords matching AND passing the same client-side strength rules as signup (≥ 8 chars, ≥ 1 letter, ≥ 1 number). Auto-redirect timer is replaceable in tests via the `redirectDelay`/`redirectDelayMs` parameter. Web `(auth)/reset-password` re-skinned with kicker + h2 + updated CTA label. |
| Verify email (T6.1c) | Big mail icon (80pt, primary500) + headline + body + sticky action stack: `Open mail app` (mailto:) primary, `Resend email` ghost (rate-limited 30 s client + backend `resendVerificationLimiter`), `I'll do this later` tertiary (soft-gate only), `Wrong email? Change it` ghost. When reached with a token (deep-link), auto-verifies on appear with a status banner. | `verifyEmailScreen` / `verifyEmailIllustration` / `verifyEmailOpenMailButton` / `verifyEmailResendButton` / `verifyEmailDoLaterButton` / `verifyEmailChangeEmailButton` / `verifyEmailBanner` (+ iOS-only `verifyEmailChange*` sheet ids) | same | `POST /api/users/verify-email` · `POST /api/users/resend-verification` | Frame 5. Q4 = soft-gate is now first-class on the client (tertiary "I'll do this later" appears in the action stack); the backend hard-gate of `/login` on `email_confirmed_at` is still tracked as a backend gap in `docs/mobile/auth-backend-contracts.md` §"Backend gap discovered". Deep-link entry: `pantopus://auth/verify-email?token=…&email=…` — the screen POSTs the token to `/verify-email` on appear, shows the success banner, then auto-pops back to login. iOS "Open mail app" wires `UIApplication.shared.open("mailto:")`; Android wires `Intent.ACTION_VIEW` against `Uri.parse("mailto:")` (system picker surfaces installed mail apps). Web `(auth)/verify-email-sent` re-skinned with the same big-mail-icon illustration and updated CTA labels. |
| Root tab | n/a | `tab.<tabName>` | `PantopusBottomBar` (no explicit tag; selection driven by `Role.Tab`) | n/a | Tab bar a11y semantics handled by platform widgets. Inbox badge wired both sides. |

### Tier 1 — Hub + Home pillars + Mailbox

| Screen | States | iOS id | Android tag | Endpoints | Notes |
|---|---|---|---|---|---|
| Hub (T6.2a) | Skeleton / FirstRun / Populated / Error · 11 sections (TopBar · ActionStrip · SetupBanner · TodayCard · PillarGrid · DiscoveryRail · JumpBackIn · RecentActivity + FirstRunHero + FloatingProgress + Skeleton) | `hubScreen` + `hubBellButton` + `hubMenuButton` + `hubSetupBanner` + `hubSetupBannerStartButton` + `hubTodayCard` + `hub.pillar.<pulse/marketplace/gigs/mail>` + `hubDiscoveryRail.seeAll` + `hubRecentActivity.seeAll` + `hubFirstRunStartButton` + `hubFloatingProgressContinue` | same tags | `GET /api/hub/overview · /today · /discovery` | T6.2a surgical re-skin to match `hub-frames.jsx`. Cutover is direct (no flag) per Q12. Per-pixel deltas captured in the PR changelog (TopBar 36pt buttons + identity-tinted ring; SetupBanner 32pt leading disc + pill CTA; TodayCard 40pt primary-gradient disc + inline AQI/commute + chevron-right; PillarGrid 32pt disc + tinted chip + caption + setup-mode opacity; DiscoveryRail 80pt colored top half + tinted chip; JumpBackIn 34pt disc + kicker + progress; RecentActivity 30pt disc + hairlines; VerifyHero gradient card + sparkles pill + envelope + white CTA; FloatingProgress 36pt conic ring + Continue pill). First-run frame now also renders PillarGrid (setup mode) + DiscoveryRail. **Wiring gaps documented**: today-card tap routes to `HubNavigationIntent.openToday` which is a no-op until P11 ships the home calendar; "See all" on Recent activity is rendered off (the design shows it but `MeRoute.recentActivity` does not exist — flip on once landed). Setup-banner Start CTA wires to `addHome` wizard (existing). |
| Notifications V2 (T5.1) | Loading / Loaded / Empty / Error · 2 tabs (All / Unread) · Today/Earlier date sections · 7 type chips (reply · mention · claim · gig · listing · safety · system) · unread-tint row highlight | `notifications` + `tab.all` / `tab.unread` + `listOfRowsTopBarAction` | `notifications` + `tab.all` / `tab.unread` + `listOfRowsTopBarAction` | `GET /api/notifications?limit=&offset=&unread=true` · `PATCH /api/notifications/:id/read` · `POST /api/notifications/read-all` | Full parity. Web drops its T4.1-era 3-way `read` filter and `personal/business` context filter — not in the design (documented divergence per F2/F8 in `docs/t5-buildout-plan.md`). All three platforms project the same `RowModel` shape via the shared archetype: 40pt `typeIcon` leading, body, status chip, time-meta, `.unread` highlight. Hub bell-tap lands on the real screen on both iOS and Android. **Known visual divergence:** iOS + Android shell renders the tab strip as shrink-to-fit (horizontally scrollable, shared with Mailbox + future 3-/4-tab screens); the design + web render the 2 tabs as `flex: 1` equal-width. Behaviour, data, and a11y are identical — only the tab strip width distribution differs. Flagged as a T5.0 shell follow-up. |
| Connections (T5.2.3) | Loading / Loaded / Empty / Error · 3 tabs (All / Neighbors / Pending) · search bar above tabs · 44pt avatar with verified-check overlay (accepted) or unverified (pending) · per-row 38pt circular `message-circle` CTA on All / Neighbors → opens chat conversation · vertical Accept (primary 30pt) / Ignore (ghost 28pt) on Pending · `secondaryCreate` 52pt FAB | `connections` + `tab.all` / `tab.neighbors` / `tab.pending` + `listOfRowsTopBarAction` + `listOfRowsSearchBar` | `connections` + `tab.all` / `tab.neighbors` / `tab.pending` + `listOfRowsTopBarAction` + `listOfRowsSearchBar` | `GET /api/relationships?status=accepted` · `GET /api/relationships/requests/pending` · `POST /api/relationships/:id/accept` · `POST /api/relationships/:id/reject` | Full parity. Two GETs fire in parallel on first load; subsequent tab switches segment over the cached payload (no extra fetch). Accept / Reject are optimistic with rollback — accepting bumps `All` count and removes the pending row in the same frame; failures restore both. Neighbors filter is client-side: relationships whose `other_user.city` is non-empty (the backend doesn't expose a richer "verified neighbor" signal yet — locality presence is the honest stand-in). Web drops the previous `Sent` and `Blocked` tabs to match the mobile design's 3-tab contract; those flows remain reachable from the Settings index → "Blocked users" row (existing). Message-CTA opens `HubRoute.chatConversation` (iOS) / `ChildRoutes.CHAT_CONVERSATION` (Android), reusing the existing `ChatConversationView` / `ChatConversationHost`. Deep link `pantopus://connections` lands on the screen on both platforms (Android via `DeepLinkRouter.Destination.Connections`; iOS via `RootTabView` switching to Hub + `HubTabRoot` consuming the pending destination). |
| MyHomes list | Loading / Loaded / Empty / Error | `listOfRowsContainer` (archetype) | `listOfRowsContainer` ✚ | `GET /api/homes/my-homes` | Archetype tag unified ✚ (was `listOfRows` on Android). |
| MyClaims list | Loading / Loaded / Empty / Error | `listOfRowsContainer` | `listOfRowsContainer` | `GET /api/homes/my-claims` | Parity. |
| Home dashboard | Loading / Loaded / Error | `homeDashboard` ✚ + `contentDetailShell` | `contentDetailShell` | `GET /api/homes/:id` (with public-profile fallback) | iOS added a feature-level id ✚; both reach `contentDetailShell` via archetype. Dashboard now exposes a `pets` quick-action tile that pushes onto the Pets list via the host stack (iOS `HubRoute.homePets(homeId:)`, Android `ChildRoutes.HOME_PETS`). |
| Pets list (T5.2.1) | Loading / Loaded / Empty / Error · 52pt secondary-create FAB · no tabs | `petsList` + `listOfRowsContainer` + `addPetWizard` | `petsList` + `listOfRowsContainer` + `addPetWizard` | `GET /api/homes/:id/pets` · `POST /api/homes/:id/pets` · `PUT /api/homes/:id/pets/:petId` · `DELETE /api/homes/:id/pets/:petId` | Full three-platform parity on the new shape **E** row (64pt thumbnail leading + inline species chip + breed subtitle + notes body + kebab trailing) and the species-tinted gradient palette in `Core/Design/SpeciesPalette.swift` / `ui/theme/SpeciesPalette.kt` / `(app)/app/homes/[id]/pets/species-palette.ts`. iOS + Android present the Add / Edit pet flow as the shared Wizard archetype (3 steps: species, basics, details); web keeps the existing single-page edit/delete UX as a modal because the design didn't specify a multi-step shape for the desktop. Optimistic delete + rollback on all three platforms. |
| Owners list (P15 / T6.3g) | Loading / Loaded / Empty / Error · no tabs · 52pt `FabVariant.secondaryCreate` user-plus FAB tinted `.home` · avatar-first row (40pt `avatarWithBadge(.medium)` with verified-check overlay for verified rows / unverified for pending) + name title + role subtitle ("Sole owner" / "Primary owner" / "Co-owner" / "Invited · awaiting verification") with `shield` icon prefix + verbose proof body ("Deed on file" / "Title on file" / "Document on file" / "Pending review") with per-tone glyph prefix + optional "You" inline chip (sky tint) on the viewer's own row + kebab trailing → Remove confirm | `ownersList` + `ownersList_removeConfirm` | `ownersList` + `ownersList_removeConfirm` | `GET /api/homes/:id/owners` (homeOwnership.js:1381) · `POST /api/homes/:id/owners/invite` (homeOwnership.js:1434) · `DELETE /api/homes/:id/owners/:ownerId` (homeOwnership.js:1614) | Full three-platform parity on the avatar-first row shape and the per-feature proof palette (`Features/Homes/Owners/OwnerProofPalette.swift` / `ui/screens/homes/owners/OwnerProofPalette.kt` / inline `PROOF_TONES` table on web). Proof bucket resolved from `(owner_status, verification_tier)`: status precedence wins — `pending` → `Pending`, `disputed` / `revoked` → `Document`, otherwise tier maps `legal` / `strong` → `Deed`, `standard` → `Title`, `weak` (or unknown) → `Document`. Role subtitle is derived from `is_primary_owner` + roster size with no fake numbers — the backend `HomeOwner` row carries no `share_percentage` today, so the design's per-owner share % (50% / 30% / 20%) is omitted; tracked as a backend follow-up (introduce `share_percentage numeric` on `HomeOwner` + plumb through the `/owners` enriched row to display the percentage). Avatar tone cycles by row position: home-green for owner 1 (matches the home identity), sky for 2, amber for 3, business-violet for 4 (wraps thereafter). Kebab opens a Remove confirm; the backend may return a `quorum_action_id` when removal needs co-owner approval, in which case the row is dropped optimistically and a "Removal pending — needs co-owner approval" toast surfaces (the optimistic-remove rollback only fires on 4xx / 5xx, not on a quorum response). "View claim" and per-owner "Edit" deferred — no `claim_id` is surfaced on the owner row today and the backend exposes no per-owner edit endpoint (only re-invite via `POST /:id/owners/invite`). "Resident" chipMeta from the design is also deferred — the `/owners` endpoint doesn't currently join `HomeMember` / occupant residency; the inline-chip slot currently only carries the "You" badge. iOS + Android open the existing `InviteOwnerForm` shell from the FAB; web routes to the existing `/app/homes/[id]/owners/invite` page. The pre-T6 bespoke web page (with hex-literal `TIER_META` / `STATUS_BADGE` palettes + transfer-ownership footer button) is replaced by the shared shell on web; the Transfer Ownership footer link remains reachable from the home dashboard. Wired from `Me` (`me.owners` Household-section row, home-pillar identity) via the resolved primary home id. **Known visual divergence on web:** the shared `<ListOfRowsShell />` doesn't render `bodyIcon` / `subtitleIcon` yet (iOS + Android do), so the per-row proof glyph and shield prefix only show on the mobile sides. The verbose body label ("Deed on file" / "Pending review") still shows on web. |
| Members list (T6.3a / P9) | Loading / Loaded / Empty / Error · 3 tabs (`tab.members` / `tab.guests` / `tab.pending`) · 52pt secondary-create FAB tinted home-green · per-tab empty state | `membersList` + `listOfRowsContainer` + `inviteMemberWizard` + `membersList_removeConfirm` + `inviteMember_email` + `inviteMember_message` + `inviteMember_role_member` + `inviteMember_role_guest` + `inviteMemberErrorBanner` | `membersList` + `listOfRowsContainer` + `inviteMemberWizard` + `membersList_removeConfirm` + `inviteMember_email` + `inviteMember_message` + `inviteMember_role_member` + `inviteMember_role_guest` + `inviteMemberErrorBanner` | `GET /api/homes/:id/occupants` (returns `{ occupants, pendingInvites }`; client-side bucketing) · `POST /api/homes/:id/invite` · `DELETE /api/homes/:id/members/:userId` | iOS: `Features/Homes/Members/MembersListView.swift` · Android: `ui/screens/homes/members/MembersListScreen.kt` · Web: `app/(app)/app/homes/[id]/members/page.tsx`. Full three-platform parity on the design's shape-F-derivative row: 40pt `avatarWithBadge` leading (verified check on active members; dashed-ring fallback on pending), role-icon-prefixed subtitle, joined-at body, role inline-chip with the `MemberRolePalette` background/foreground pair (`Features/Homes/Members/MemberRolePalette.swift` / `ui/screens/homes/members/MemberRolePalette.kt` — documented feature-palette exception; web inlines the same hex values straight from `members-frames.jsx:57-64`). Trailing is kebab on Members/Guests (opens Remove confirm) and stacked Resend / Cancel on Pending. Invite flow ships as the shared Wizard archetype on iOS + Android (3 steps: Role · Identify · Review) and a same-shape slide-panel modal on web — all three POST `relationship = member / guest / admin / …`; backend maps via `mapLegacyRole` → `proposed_role_base`. Optimistic remove + cancel-invite with rollback on iOS + Android; web uses toast feedback + refetch. Pending cancel reuses `DELETE …/members/:userId` when the invitee has a resolved user id; for open invites without a user id, the row is dropped optimistically and the backend reconciles via expiry. Dashboard "Members" / "Add member" quick-actions navigate to this list on both iOS (`HubRoute.homeMembers(homeId:)` / `YouRoute.homeMembers`) and Android (`ChildRoutes.HOME_MEMBERS`). Web's prior `Audit Log` / `Requests` tabs were out of design scope and removed from this surface — access-request review stays reachable from the household-claim review flow (`/app/admin/review-claims`). |
| Add Home wizard | per-step | `wizardShell` | `wizardShell` | `POST /api/homes/check-address` · `POST /api/homes` | Wizard archetype identical. |
| Claim Ownership wizard | per-step | `wizardShell` | `wizardShell` | `POST /api/homes/:id/claim` | Parity. |
| Invite Owner form | n/a (form-only) | `formShell` | `formShell` | `POST /api/homes/:id/invite-owner` | Parity. |
| Mailbox list (All/Unread/Starred tabs) | Loading / Loaded / Empty / Error | `listOfRowsContainer` | `listOfRowsContainer` ✚ | `GET /api/mailbox?type=&viewed=&starred=&limit=&offset=` | Tabs (`tab.all`, `tab.unread`, `tab.starred`) carry the same id strings. |
| Mailbox drawers | Loading / Loaded / Empty / Error | `listOfRowsContainer` | `listOfRowsContainer` | `GET /api/mailbox/v2/drawers` | Parity. |
| Mailbox item detail | Loading / Loaded / Error | `mailboxItemDetailShell` ✚ | `mailboxItemDetailShell` ✚ | `GET /api/mailbox/:id` · `PATCH /api/mailbox/:id/ack` | Both renamed for parity ✚. |
| Disambiguate Mail | n/a (form-only) | `formShell` + `disambiguateRow_<drawer>` rows | `formShell` + `disambiguateRow_${drawer}` rows | `POST /api/mailbox/v2/resolve` | Parity. |
| Public profile | Loading / Loaded / Error | `publicProfile` ✚ + `contentDetailShell` | `contentDetailShell` | `GET /api/users/:id · /:id/audience-stats · /:id/follow/status` · `POST /api/users/:id/block` | iOS feature id added ✚. Android relies on archetype + per-action tags. |
| Edit Profile | Loading / Loaded / Error | `editProfileShell` (via FormShell) | `formShell` (Edit Profile is iOS-only this milestone — see Android a11y_audit.md §EditProfile) | `PATCH /api/users/profile` | Documented platform asymmetry — Edit Profile ships iOS-only this milestone. |
| Bills list (T6.0a re-skin of T5.2.2 / P13) | Loading / Loaded / Empty / Error · 3 tabs (Upcoming / Paid / All) · `amountWithChip` trailing · **6 chip statuses** (due · dueSoon · overdue · scheduled · paid · cancelled) · **8 utility-tinted category tiles + generic fallback** (electric · gas · water · internet · hoa · insurance · trash · phone) via `UtilityCategoryPalette` · category client-derived from payee string (per T6 Q2 decision) · **summary banner** above the Upcoming list (home-tinted) showing 30-day total + overdue count + `Pay all` CTA · optional **`Auto-pay` inline chip** next to the title on scheduled rows · optional **`RowModel.splitWith` 18pt avatar stack** at the right edge of the chip row when split (shell field is wired; backend `/api/homes/:id/bills` doesn't yet surface split membership on rows so today's production rows leave it `nil` — tracked as a backend follow-up) · `RowHighlight.muted` on cancelled rows · **56pt `canonicalCreate` FAB tinted `.home`** (was 52pt `secondaryCreate` sky pre-T6) · `topBarAction: nil` (design's filter glyph not wired to a real sheet yet) | `billsList` + `tab.upcoming` / `tab.paid` / `tab.all` | `billsList` + `tab.upcoming` / `tab.paid` / `tab.all` | `GET /api/homes/:id/bills` · `POST /api/homes/:id/bills` · `PUT /api/homes/:id/bills/:billId` · `GET /api/homes/:id/bills/:billId/splits` | T6.0a re-skin lands on top of the T5.2.2 wiring with **no backend changes** — per the locked Q2 decision the 8 utility categories are inferred client-side from `bill.provider_name` via a shared `payeeToCategory()` pattern table on all three platforms. **Shell extensions T6.0a (all additive)**: `RowModel.splitWith: SplitStackData?`, `BannerConfig.cta: BannerCTA?` + `BannerConfig.tint: BannerCTATint`, `FabTint` enum + `FABAction.tint` field. The 6-state chip derivation: `cancelled` when status='cancelled', `paid` when status='paid', `scheduled` when status='scheduled', `overdue` when due_date < now, `dueSoon` when due_date <= now+7d, `due` otherwise. **Backend gaps carried over** (still tracked for a follow-up PR): (1) no `DELETE /api/homes/:id/bills/:billId` — detail screen soft-deletes via `PUT { status: 'cancelled' }`; (2) no `POST` / `PATCH` / `DELETE` for `:billId/splits` — splits remain read-only on the detail screen; (3) bill list endpoint doesn't surface `split_members` on rows so `RowModel.splitWith` stays `nil` in production today. Pre-T6 screenshot `parity-bills.png` is superseded by `bills-v2-{ios,android,web}.png`. |
| Bill detail (T6.0a re-skin of T5.2.2) | Loading / Loaded / Error · "Mark paid" + "Remove bill" actions · **utility-tinted 48pt header tile** (uses `UtilityCategory.from(payee:)` palette) · **`Auto-pay` chip** beside the payee on scheduled bills · **Category** + **Auto-pay** rows in the detail grid (in addition to Status / Due / Paid / Currency) | `billDetail` + `billDetail_markPaid` / `billDetail_remove` | `billDetail` + `billDetail_markPaid` / `billDetail_remove` | `GET /api/homes/:id/bills` (filter by id — no GET-by-id today) · `GET /api/homes/:id/bills/:billId/splits` · `PUT /api/homes/:id/bills/:billId` | Built on `ContentDetailShell`. No GET-by-id route on the backend; the VM fetches the parent list and finds the row by id (cheap on typical < 100-bill lists). Splits render read-only. Category inferred from `provider_name` via the shared payee pattern table (per T6 Q2 decision) — same value the list row uses, so header + row stay in sync without a backend join. |
| Add Bill wizard (T5.2.2) | per-step (Details / Schedule / Review / Success) | `addBillWizard` + `wizardShell` + `addBill_payee` / `addBill_amount` / `addBill_dueDate` / `addBill_schedule_*` | `addBillWizard` + `wizardShell` + `addBill_payee` / `addBill_amount` / `addBill_dueDate` / `addBill_schedule_*` | `POST /api/homes/:id/bills` | 3-step wizard built on the shared `WizardShell`. `schedule` (one-time / monthly / quarterly / yearly) is persisted on the bill's `details` JSON. Step 3 surfaces the splits gap (see Bills list row) as informational copy until the backend ships split-write endpoints. |
| Maintenance list (T6.3b / P10) — iOS `Features/Homes/Maintenance/MaintenanceListView`, Android `ui/screens/homes/maintenance/MaintenanceListScreen`, web `(app)/app/homes/[id]/maintenance/page.tsx` | Loading / Loaded / Empty / Error · 3 tabs (Scheduled / Completed / All) · `amountWithChip` trailing · **6 chip statuses** (scheduled · dueSoon · overdue · inProgress · completed · cancelled) · **12 task-category tiles + generic fallback** (hvac · plumbing · electrical · roof · gutter · appliance · pest · landscape · cleaning · painting · safety · chimney) via `MaintenanceCategoryPalette` · category client-derived from task title via shared `MaintenanceCategory.from(task:)` pattern table · **summary banner** above the Scheduled list (home-tinted) showing scheduled count + next-up + overdue count + YTD spend · optional **due-date inline chip** next to status (warning on dueSoon, info on scheduled, error on overdue) · `RowHighlight.muted` on cancelled rows · **60pt `canonicalCreate` FAB tinted `.home`** · `topBarAction: nil` (mirrors Bills) · cost `0` renders as `DIY`, missing vendor renders as `Self-managed` | `maintenanceList` + `tab.scheduled` / `tab.completed` / `tab.all` | `maintenanceList` + `tab.scheduled` / `tab.completed` / `tab.all` | `GET /api/homes/:id/maintenance` · `POST /api/homes/:id/maintenance` · `PUT /api/homes/:id/maintenance/:taskId` · `DELETE /api/homes/:id/maintenance/:taskId` | **New backend prep landed in the same PR**: migration `151_home_maintenance_tasks.sql` extends `HomeMaintenanceLog` with `task / vendor / cost / recurrence / due_date / status / updated_at / created_by` + `HomeMaintenance_status_chk` + `HomeMaintenance_recurrence_chk` constraints (additive — every existing performed-log row stays valid via defaults). 4 routes mirror the Bills shape: list/get require home membership, create/update/delete require `home.edit` (matches `homeIam` matrix). Tests: `backend/tests/homeMaintenance.test.js` covers CRUD + authz across owner/member/outsider (16 tests). The 6-state chip derivation: `cancelled` when status='cancelled', `completed` when status='completed', `inProgress` when status='in_progress', `overdue` when status='scheduled' + due_date < now, `dueSoon` when status='scheduled' + due_date <= now+7d, `scheduled` otherwise. Wired from the Me tab `me.maintenance` action tile (home-context) and the Activity section row. Detail tap → placeholder until a follow-up PR adds the detail surface. **Icon catalog**: 8 new `PantopusIcon` tokens added — `wrench / fan / cloudRain / refrigerator / bug / trees / paintRoller / bellRing` (parity additions on iOS + Android). Web page replaces the pre-T6 stub that piggy-backed on `HomeIssue`. |

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
| My bids (T5.3.1) | Loading / Loaded / Empty / Error · 4 tabs (Active / Accepted / Rejected / Done) · Shape C rows (40pt categoryGradient leading + priceStack trailing "budget $X" sublabel + 11-variant status chip + per-tab footer with 1-2 34pt CompactButton.footer actions) · `RowHighlight.muted` (0.78 opacity) on terminal rows · `BannerConfig` above the Active tab summarises Top-bid + Closing-soon counts · top-bar `filter` icon · 48pt `FabVariant.extendedNav` "Browse tasks" pill | `my-bids` + `tab.active` / `tab.accepted` / `tab.rejected` / `tab.done` + `listOfRowsTopBarAction` + `withdraw-cancel` / `withdraw-confirm` / `withdraw-reason-<reason>` sheet tags | `my-bids` + same tab + sheet tags | `GET /api/gigs/my-bids` (gigs.js:1253) · `PUT /api/gigs/:gigId/bids/:bidId` (edit, gigs.js:3971) · `DELETE /api/gigs/:gigId/bids/:bidId` (withdraw, gigs.js:5245) · `POST /api/gigs/:gigId/mark-completed` (gigs.js:5926) · `POST /api/reviews` (reviews.js:35) | Full mobile parity. iOS + Android project the same `RowModel` via the shared archetype: 4-tab grouping is canonical (see docs/t5-buildout-plan.md "My bids implementation notes"). Withdraw is optimistic — row leaves Active immediately and rolls back on failure; same shape for mark-complete (Accepted → Done). The new `RowHighlight.muted` case (additive, ships in this PR) drives the 0.78 terminal opacity on iOS / Android / web shells. Web rebuckets its legacy 7-status filter into the 4 design tabs; full row-card visual reskin is a follow-up. P3 backend prep (`shortlisted`, `your_rank`, `top_price` fields) is still pending — `Top bid` / `Shortlisted` / `Outbid` chips degrade to `Pending` until those fields ship; optional decoders are already in place. |
| My tasks V2 (T5.3.2 → T6.0b) | Loading / Loaded / Empty / Error · 4 tabs (Open / Active / Done / Closed) · **Magic Task rows** use 44pt `RowLeading.magicArchetypeTile` (lavender-or-archetype gradient + 18pt sparkles disc overlay top-right) with `RowModel.archetypeOverline` (uppercase magic-violet, 10pt) above the title; non-magic rows keep 40pt `categoryGradientIcon` · trailing `priceStack` "$N" or "$N/hr" · 9-variant status chip + optional engagement-mode badge after the status chip (`in_person` / `drop_off` / `remote` / `hybrid`, neutral-tinted custom chip with `map-pin` / `package` / `monitor` / `shuffle` icon) · inline BidderStack on the chip line · per-status footer with 1-2 34pt CompactButton.footer actions · `RowHighlight.muted` on cancelled / expired rows · `BannerConfig` above the Open tab summarises new-bids + closing-soon counts · top-bar `filter` icon · **60pt `FabVariant.magicCreate` "Post a task with Magic Task"** (gradient primary600 → primary700 + 18pt sparkles disc top-right) · Open-tab empty state reframed as "No tasks posted yet — try Magic Task" with `Try Magic Task` primary CTA (web also renders three quick-prompt buttons that pre-fill the Magic Task sheet) | `my-tasks` + `tab.open` / `tab.active` / `tab.done` / `tab.closed` + `listOfRowsTopBarAction` + `rowArchetypeOverline` | `my-tasks` + same tab tags + `rowArchetypeOverline` | `GET /api/gigs/my-gigs` (gigs.js:1169 — GIG_LIST now includes `source_flow`, `task_archetype`, `task_format`) · `POST /api/gigs/:gigId/boost` (T5.3.2) · `POST /api/gigs/:gigId/complete` (poster confirmation, gigs.js:6170) · `POST /api/gigs/:gigId/cancel` (gigs.js:6233) · `POST /api/gigs` (new task, gigs.js:749 — accepts `task_format`) · `POST /api/gigs/magic-post` (magicTask.js:397 — accepts `task_format`) | Full mobile parity. Inverse of My bids — the poster's side. **T6.0b adds:** four shell extensions land additively — `RowLeading.magicArchetypeTile` (44pt rounded gradient tile with a clipped sparkles disc overlay, separate from `categoryGradientIcon` to make the magic signal scannable), `RowModel.archetypeOverline: String?` (truncated at 24 chars), `FabVariant.magicCreate` (60pt gradient FAB with sparkles disc), and an additive backend column `Gig.task_format` (per T6 Q13 the design's `engagement_mode` concept is renamed to `task_format` to avoid colliding with the existing offer-acceptance `engagement_mode` enum). The Magic Task FAB tapping invokes the same `onPostTask` callback the screen already wires; the destination route is responsible for opening the Magic Task draft flow (Magic Task plumbing lands in T6.0d). New magic-task lavender quartet of theme tokens (`magic` / `magicBg` / `magicBgSoft` / `magicBorder`) added to `@pantopus/theme`, iOS asset catalog, and Android `Color.kt`. New icons (`tv` / `laptop` / `monitor` / `shuffle` / `wand-sparkles` / `arrow-up-right`) added to both `PantopusIcon` enums. Empty-state copy on the Open tab reframes "Post a task" as "Try Magic Task" with `Sparkles` illustration; the canonical 60pt magic FAB stays visible underneath so manual posters can still tap it. **Backend prep:** T6.0c-equivalent additions folded into this PR — `task_format` enum + column + index in migration `150_gig_task_format.sql`; `source_flow` and `task_format` exposed via `GIG_LIST`; Joi validation extended on `POST /api/gigs` and `POST /api/gigs/magic-post`. Web `/app/my-gigs` reskin matches mobile (gradient tile + sparkles disc + overline + mode badge + 60px gradient FAB rendered in CSS). All existing call sites of `RowLeading`, `RowModel`, and `FABAction` compile unchanged because every addition is a new enum case or an optional defaulted field. |
| My posts (T5.3.3) | Loading / Loaded / Empty / Error · 2 tabs (Active / Archived) · Shape C6 rows (no leading; intent chip in `headerChips` above the body; primary-emphasis body line; kebab trailing → action sheet for Archive/Restore + Delete; engagement strip footer with intent-aware counters + trailing Edit / Restore link; archived rows render at 0.78 opacity with an extra `ARCHIVED` chip) · top-bar `filter` icon · 52pt `FabVariant.SecondaryCreate` "Write a post" pencil FAB | `my-posts` + `tab.active` / `tab.archived` + `listOfRowsTopBarAction` + `kebab-archive` / `kebab-restore` / `kebab-delete` / `kebab-cancel` sheet tags + `delete-confirm` / `delete-cancel` alert tags + `rowEngagementCTA` | `my-posts` + same tab + sheet tags + alert tags + `rowEngagementCTA` | `GET /api/posts/user/:userId` (posts.js:3016, active set) · `DELETE /api/posts/:id` (posts.js:2483) | Full mobile + web parity on the row shape. Three new additive shell fields ship with this PR: `RowModel.headerChips` (chips above the body in the header row, sharing the kebab line), `RowModel.engagement` (hairline-separated engagement strip with display-only items + optional trailing CTA), and `RowModel.bodyEmphasis = .primary` (renders the body at the row's headline size + colour). Intent chip mapping mirrors the Pulse feed (`ask · recommend · event · lost · announce`). Per-intent engagement labels: ask/announce → `replies` + `likes`; event → `going` + `replies`; recommend → `helpful` + `replies`; lost → `replies` + `seen`. **Backend gap (out of scope per product call):** there is no `GET /api/posts/me`, `POST /api/posts/:id/archive`, or `POST /api/posts/:id/unarchive` route today — Archive / Restore are local-only optimistic mutations on all three platforms (state survives within the session; a refresh discards local overrides and the wire-only Active set returns). Delete uses the real `DELETE /api/posts/:id` and rolls back on failure. The MyPostDTO / MyPostDto already carries an optional `archived_at` field so the future `GET /api/posts/me?status=archived` route will start populating the Archived tab without any client change. Compose FAB pushes the typed `composePost` route (today renders `NotYetAvailableView` — the real compose flow is a separate work item). |
| Listing offers (T5.3.4) | Loading / Loaded / Empty / Error · NO tabs · NO FAB · `ListingContextConfig` hero card (64pt category-gradient thumbnail + listing title + ask price + posted-meta + status chip) + sort strip ("N offers · Highest first") · Shape C rows (44pt buyer `avatarWithBadge` leading + priceStack trailing "asking $X" sublabel + 7-state status chip + optional `Your counter $X` pill + optional italic note + per-status footer) · `RowHighlight.leading` (amber border + `LEADING` badge) on the highest-amount pending row · top-bar share icon · top-bar subtitle = `{listingTitle}` | `listing-offers` + `listingContextHeader` + `counter-amount` / `counter-message` / `counter-cancel` / `counter-confirm` sheet tags | `listing-offers` + `listingContextHeader` + same sheet tags | `GET /api/listings/:listingId/offers` (listingOffers.js:78) · `POST …/offers/:offerId/accept` (listingOffers.js:163) · `POST …/offers/:offerId/decline` (listingOffers.js:180) · `POST …/offers/:offerId/counter` (listingOffers.js:144) · `GET /api/listings/:id` (listings.js:1375) for header context | Full parity. iOS + Android + web project the same `RowModel`; the new `listingContext: ListingContextConfig?` slot is additive on the shared shell and rendered above the first row. Offers fetched in parallel with listing detail; rows sorted by amount descending, the top pending offer wins the `LEADING` highlight. Accept / Decline / Counter mutations are optimistic with rollback on failure. Footer per status follows the T5.3.4 contract verbatim (pending → Counter + Accept; countered → Withdraw counter + Send counter; accepted → View transaction; declined → no footer). Entry from listing detail dock swaps the primary button to "View offers" when the viewer owns the listing. Subtitle line on the top bar shows `{listingTitle}` to match the design header. The "Withdraw counter" action maps to `/decline` since the backend exposes no `withdraw-counter` route; documented here as an accepted-difference. |
| Discover hub (T5.4.1 / P11) | Loading / Loaded / Empty / Error · 4-chip filter strip (Nearby / New today / Verified / Free / wanted; Trending hidden — no engagement signal in `/api/hub/discovery` today) · 4 typed `SectionStyle.card` sections (People · Businesses · Gigs · Listings) — design-spec order, hidden when empty, whole-screen `compass` empty state when all four are empty · per-section `count` + `See all` CTA via `RowSection.onSeeAll` · 36pt `avatarWithBadge(.small)` for People, 36pt `categoryGradientIcon` for Businesses + Gigs, 56pt `thumbnail(.medium)` for Listings · `priceStack` trailing on Gigs + Listings · top-bar `sliders-horizontal` icon · no FAB · no tabs | `discoverHub` + `chip.nearby` / `chip.new-today` / `chip.verified` / `chip.free-or-wanted` + `listOfRowsTopBarAction` + `hubDiscoveryRail.seeAll` (Hub-rail entry point) | `discoverHub` + same chip + `listOfRowsTopBarAction` + `hubDiscoveryRail.seeAll` | `GET /api/hub/discovery?filter={people,businesses,gigs,listings}&since=&verified=&freeOrWanted=` (hub.js:757; `listings` filter case + `subtitle` / `price` / `verified` / `isFree` / `isWanted` / `createdAt` per-item fields added in this PR — additive, legacy `meta` field preserved) | First real consumer of P1's `RowSection.onSeeAll` + `SectionStyle.card` + `AvatarBadgeSize.Small`. Four parallel fan-out fetches per chip selection — no composite `/api/discover/hub` endpoint exists today; tracked as a follow-up if list latency becomes a concern. See-all targets: People → `connections`, Businesses → `discoverBusinesses` (T5.4.2 / P12), Gigs → `gigsFeed`, Listings → `marketplace` (web `/app/marketplace`, iOS `HubRoute.marketplace`, Android `ChildRoutes.MARKETPLACE`). Hub home's "Discover nearby" rail header now carries a `See all` CTA (`hubDiscoveryRail.seeAll`) that pushes the screen on both mobile platforms. Deep link `pantopus://discover-hub` lands on the screen on both platforms (Android via `DeepLinkRouter.Destination.DiscoverHub`; iOS via `RootTabView` switching to Hub + `HubTabRoot` consuming the pending destination). Per-section empty handling: section disappears when zero items; whole-screen empty state renders verbatim from `discoverhub-frames.jsx` Frame 2 (compass icon + "Nothing to discover yet" + supporting copy; the design's two CTAs — Invite neighbors / Widen radius — are intentionally omitted because neither has a real backend wiring today). |
| Discover businesses (T5.4.2 / P12) | Loading / Loaded / Empty / Error · 9-chip category filter strip (All / Handyman / Cleaning / Pet Care / Plumbing / Tutoring / Childcare / Moving / Lawn Care) — first consumer of `chipStrip` for category filtering (Discover hub uses it for cross-cutting filters) · search bar above the chip strip (`searchBar` slot, 300ms debounce) · category-grouped `RowSection.card` sections in chip order when "All" is selected; single section when a specific chip is selected · 40pt `categoryGradientIcon` leading (token-only gradients keyed off the category) + `chevron` trailing · subtitle composed from `description · open-now · distance_miles` · top-bar `sliders-horizontal` icon · no FAB (matches the rendered frame; the buildout-plan verification line mentioning an "Add my business / Suggest a business" FAB is overridden by §5 convention — visual frame wins) · no tabs · two distinct empty states: no-results (compass + "Invite a business" CTA) and no-location 400 (map-pin + "Widen radius" CTA) | `discoverBusinesses` + `chip.all` / `chip.handyman` / `chip.cleaning` / `chip.pet-care` / `chip.plumbing` / `chip.tutoring` / `chip.childcare` / `chip.moving` / `chip.lawn-care` + `listOfRowsTopBarAction` + `listOfRowsSearchBar` | `discoverBusinesses` + same chip ids + `listOfRowsTopBarAction` + `listOfRowsSearchBar` | `GET /api/businesses/search?q=&categories=&page=&page_size=` (businessDiscovery.js:436 — mounted at `/api/businesses` in `backend/app.js:339`; viewer home resolved server-side, no explicit lat/lng required) | iOS + Android + web all map the backend `formatSearchResult` response into the same `RowModel` via a shared category projection. The category chip-id doubles as the backend `categories=` filter value (omitted when "All" is selected). Category projection is canonical and pure: `primaryCategoryKey(categories[])` normalises raw category strings (`"Pet Care"` / `"pet_care"` → `"pet-care"`) and returns the first match in the chip-order set, falling back to `"other"`. Unrecognised categories collect into a single `"Other"` section appended after the known ones. Two divergences from the prompt's design contract, both deliberate: (1) top-bar trailing is the design's `sliders-horizontal` icon (filter), not the prompt's "search" — the prompt has a separate search bar slot directly below, which IS rendered; (2) no FAB — the design has no FAB on either populated or empty frame, and the "Invite a business" affordance lives in the no-results empty-state CTA per the design. iOS / Android push `discoverBusinesses` via the typed `HubRoute.discoverBusinesses` / `ChildRoutes.DISCOVER_BUSINESSES` route already wired from Discover hub's "See all Businesses". Web reskin replaces `(app)/app/discover/page.tsx` with the new shell; the legacy rich map/list view remains accessible at `(app)/app/map` for power-user workflows. Tapping a row pushes to the business profile route (`HubRoute.placeholder(name+id)` on mobile until the typed business-profile screen ships, `/app/businesses/{username}` on web). |
| Chat list (Inbox tab) | Skeleton / Loaded / Empty / Error | `chatList` | `chatList` | `GET /api/chats?filter=&offset=` · `POST /api/chats/:id/read` | Parity. |
| Chat conversation | Loading / Loaded / Empty / AiWelcome | `chatConversation` | `chatConversation` | `GET /api/chats/:id/messages?offset=` · `POST /api/chats/:id/messages` · WS | Parity. iOS only has the `aiWelcome` state today; Android falls through Loaded with an empty-message render — equivalent UX. |
| Nearby map | Loading / Loaded / Error | `nearbyMap` | `nearbyMap` | `GET /api/nearby/map?lat=&lng=&radius=` | Parity. |
| Me / You (T6.2b re-skin) | Loading / Loaded / Error · 2 designed identity frames (Personal sky, Home green) + unbound Business · header is a **3-stop gradient card** (sky `primary600→primary500→primary700`; home/business accent-ramped) carrying the **identity-switcher pill row at the top** → 72pt **verification-ring avatar** (white outer ring + identity-tinted 22pt verified badge on white) + name + handle + map-pin locality + tagline · **3-tile stats card** (Personal `Activity / Trust / Reputation`; Home `Bills due / Open tasks / Members`) · **2×3 action grid** (Personal `My posts · My bids · My tasks · Offers · Listings · Connections`; Home `Bills · Pets · Members · Polls · Calendar · Documents`) · **3 section groups** (Personal `Profile & Privacy / Activity / Help & Legal`; Home `Household / Activity / Help & Legal`) · destructive sign-out card · DEBUG-build only Debug section appended for the deep-link affordances | `meScreen` + `meHeader_<identity>` + `meIdentityPill_<key>` + `meVerificationBadge` + `meStatsRow` + `meActionTile_<id>` + `meSectionRow_<sectionId>_<rowId>` + `meDestructiveCard_<identity>` | `meScreen` + same set | `GET /api/users/profile` · `GET /api/users/:id/stats` · `GET /api/homes/my-homes` | T6.2b — re-skin only. No new state machinery; same VM, identity-switcher rebinds in place (no refetch). Wired-route table: `me.posts→MyPosts`, `me.bids→MyBids`, `me.gigs→MyTasks`, `me.offers→Offers`, `me.connections→Connections`, `me.bills→BillsList` (with primary `homeId` baked into `routeArgs`), `me.pets→PetsList`, `me.identityCenter→IdentityCenter`, `me.audience→AudienceProfile`, `me.editProfile→EditProfile`, `me.settings→Settings` (refresh in P8), `me.mail→Mailbox`. Remaining tiles (`me.members→P9`, `me.tasks→P11`, `me.packages→P12`, `me.polls→P13`, `me.calendar→P18`, `me.docs→P17`, `me.homes/me.listings/me.businesses→P14`, `me.access→P16`, `me.owners→P15`, `me.emergency→P17`, `me.legal→P26`) land on the labelled `placeholder(label:)` route — each will be flipped to its real destination by its T6 sub-PR per `docs/t6-buildout-plan.md`. Snapshot baselines under `docs/screenshots/me-{personal,home}-{ios,android}.png`. |

### Tier 3 — Settings + Identity surfaces + Privacy + Status + Ceremonial

| Screen | States | iOS id | Android tag | Endpoints | Notes |
|---|---|---|---|---|---|
| Settings index | n/a (grouped list) | `groupedList` | `groupedList` | `GET /api/users/me` (for footer) · `GET /api/privacy/blocks` (for the Blocked-users row subtitle count) | Routes off-stage to its sub-screens. After P8 / T6.2c, six of the eight sub-routes are real screens; two stay placeholders (Data export, Payments & payouts — see Tier 3 "Parked Settings sub-routes" below). |
| Notification settings | Loading / Loaded / Error | `groupedList` | `groupedList` | `GET /api/users/preferences/notifications` · `PATCH /api/users/preferences/notifications` | Parity. |
| Privacy settings | Loading / Loaded / Error | `groupedList` | `groupedList` | `GET /api/users/preferences/privacy` · `PATCH /api/users/preferences/privacy` | Parity. |
| Identity Center | Loading / Loaded / Error | `identityCenter` | `identityCenter` | `GET /api/identity-center` | Parity. |
| Audience Profile | Loading / Loaded / Empty / Error | `audienceProfile` + `audienceProfileContent` | `audienceProfile` | `GET /api/personas/me` · `/posts` · `/tiers` · `/membership-stats` · `/dms/threads` | Parity. Tab pills `audienceProfileTab_<id>` ↔ `audienceProfileTab_${id}`. |
| Privacy Handshake | per-step | `privacyHandshake` (`wizardShell`) | `privacyHandshake` (`wizardShell`) | `GET /api/personas/:handle` · `/:handle/tiers` · `POST /api/personas/:handle/follow` | Parity. |
| Token Accept | n/a | `tokenAccept` + `tokenAcceptOffer` | `tokenAccept` | `GET /api/homes/invitations/token/:t` · `POST /api/homes/invitations/token/:t/accept` | Parity. |
| Status / Waiting | n/a | `statusWaiting` | `statusWaiting` | n/a (presentational) | Parity. |
| Ceremonial Mail Compose | per-step | `ceremonialMail` (`wizardShell`) | `ceremonialMail` (`wizardShell`) | `GET /api/mailbox/compose/recipients` · `GET /api/mailbox/compose/home-context/:id` · `POST /api/mailbox/send` | All wizard step tags identical. |
| Ceremonial Mail Open | Loading / Loaded / Error | `ceremonialMailOpen` | `ceremonialMailOpen` | `GET /api/mailbox/v2/item/:id` | Parity through every phase (Sealed / Breaking / Open / Replying). |
| Blocked users (P8 / T6.2c) | Loading / Loaded / Empty / Error · `ListOfRows` shell · 40pt `avatarWithBadge(medium)` leading + kebab trailing (Unblock) · row subtitle prefers `reason` then falls back to scope label ("Hidden from search" / "Blocked in business contexts" / "Blocked") · optimistic unblock w/ rollback on failure | `blockedUsers` (+ `listOfRowsContainer`) | `listOfRowsContainer` (BlockedUsersScreen wraps `ListOfRowsScreen`) | `GET /api/privacy/blocks` (privacy.js:154) · `DELETE /api/privacy/blocks/:blockId` (privacy.js:251) | Full parity. iOS + Android both project the join row from `privacy.js` into the same `RowModel`. `PrivacyBlock` / `PrivacyBlockDto` were extended in this PR with `block_scope`, `reason`, and the nested `blocked` summary so the row subtitle has real content. Web: not reconciled — `frontend/apps/web/src/app/(app)/app/settings` does not surface a Blocked users page today; tracked as a web follow-up. |
| Password change (P8 / T6.2c) | Loading (auth-methods discovery) / Ready / Saving / Error | `passwordChange` (`formShell`) · field tags `field_current` / `field_new` / `field_confirm` · `passwordChangeToast` | `formShell` (PasswordChangeScreen wraps `FormShell`) · field tags `field_current` / `field_new` / `field_confirm` | `GET /api/users/auth-methods` (users.js:1739) · `POST /api/users/password` (users.js:1771) | Full parity. The form gates whether the Current password field renders on `hasPassword` (OAuth-only accounts setting an initial password skip the field). Body shape was fixed in this PR — backend's `updatePasswordSchema` uses **camelCase** (`currentPassword` / `newPassword`); both iOS `PasswordUpdateBody` and Android `PasswordUpdateBody` previously serialised snake_case keys that the Joi schema would have silently dropped. 401 from `/password` lands as an inline error on the Current password field; 429 surfaces a toast. Android also got an incidental bugfix to `PantopusTextField.isSecure` (the visualTransformation branch returned `VisualTransformation.None` on both sides — fixed to `PasswordVisualTransformation()`). |
| Verification center (P8 / T6.2c) | Loading / Loaded / Error | `verificationCenter` (`groupedList`) | `groupedList` (VerificationCenterScreen wraps `GroupedListScreen`) | `GET /api/identity-center` (identityCenter.js:401, reads `private_account.verified`) · `POST /api/users/resend-verification` (users.js:3049) | Full parity. 4-group status grid (Email · Phone · Home address · Photo ID). Email shows a Verified/Unverified chip; when unverified, a Resend row appears that posts the resend-verification email. Phone / Home / Photo ID rows are read-only "Coming soon" until those flows ship — documented in-source, not placeholder views. **Backend gap:** there is no dedicated `/me/verification-status` endpoint today; we hydrate from identity-center because that's where `private_account.verified` already surfaces. If phone / home / ID verification ships, replace the identity-center call with a richer status endpoint. |
| Help center (P8 / T6.2c) | n/a (static) | `helpCenter` (`contentDetail`) + `helpCenterContactCTA` | `contentDetail` (`helpCenter` test tag) + `helpCenterContactCTA` | none — static FAQ bundled with the binary | Full parity. ContentDetail shell with 3 sections (Getting started · Mail & messages · Account & safety) × 2 Q/A cards each. Bottom CTA opens a `mailto:support@pantopus.app` intent on both platforms. Per Q7's "Static FAQ + contact CTA. No backend." — if FAQ content grows, lift into a CMS / `/api/help/faq` endpoint. |
| Legal index (P8 / T6.2c) | n/a (static) | `legalIndex` (`groupedList`) | `groupedList` (LegalIndexScreen wraps `GroupedListScreen`) | none — TOC is static | Full parity. Two groups (Policies · Credits). Rows: Terms, Privacy, Acceptable use, Cookies, Open-source licenses. Tap pushes [LegalContent]. Document IDs differ in case between platforms (iOS: `acceptableUse` / `openSource`; Android: `acceptableuse` / `opensource` — both derived from the enum case name's natural lowercase); each platform routes locally so the difference doesn't cross the boundary. |
| Legal content (P8 / T6.2c) | n/a (static) | `legalContent.<doc>` (`contentDetail`) | `legalContent.<doc>` (`contentDetail`) | none — long-form text bundled | Full parity. 5 documents (Terms, Privacy, Acceptable use, Cookies, Open-source licenses) rendered as paragraph / heading / bullet blocks. Both platforms ship the same versioned `2026-05-01` text. **Backend gap:** no CMS — updating policy text requires a code change + binary release. Tracked as a follow-up if the legal team needs rotation cadence < monthly. |
| About (P8 / T6.2c) | n/a (static) | `aboutScreen` (`contentDetail`) + `aboutVersion` | `aboutScreen` + `aboutVersion` (`contentDetail`) | none — `Bundle.main`'s version on iOS, `BuildConfig.VERSION_NAME/CODE` on Android | Full parity. Centered 96pt brand mark + version + build, two info cards (Mission · Built by), and a pointer to Settings → Legal → Open-source licenses for the full attributions list. Copyright year derived locally. |

### Tier 3 — Parked Settings sub-routes (P8.5)

Two of the eight Settings sub-routes still render `NotYetAvailableView`
per Q7's "park 2" decision (`docs/t6-open-questions-decisions.md`
§Q7). Each remains discoverable from the Settings index, so the user
sees the lay of the land — but tapping in lands on the canonical
placeholder, not a real screen.

| Sub-route | Why it's parked | Tracker |
|---|---|---|
| Data export | Requires a multi-step Wizard flow + a new backend job runner. No `POST /api/users/data-export` endpoint exists today; the schema for the export bundle isn't defined. | **P8.5** — separate PR once the export job design lands. |
| Payments & payouts | Hooks into the Stripe Connect wallet surface (`wallet.js`). The wallet UX is the canonical container; settling that surface first avoids two Stripe-tinted screens diverging. | **P8.5** — separate PR once the wallet UX is signed off. |

### Tier 5 — Web-only screens (no mobile parity by design)

Listed for completeness. These screens ship on web but are intentionally
out of scope on iOS / Android per `docs/mobile/pantopus-t5-notes.md`
§1.8 — the mobile codebase has no admin role, so the parity column
collapses to `(web only)` until that infra ships.

| Screen | States | Web route | Endpoints | Notes |
|---|---|---|---|---|
| Review claims (T5.4.3 / P16) | Loading / Loaded / Empty / Error · 3 tabs (Pending / Approved / Rejected) with counts · `BannerConfig` above the Pending tab summarising "N claims awaiting review · Oldest in queue: …d" · Shape C row (40pt gradient `avatarWithBadge` leading + claimant name title + address subtitle + status chip + `paperclip` evidence chip + submitted-ago meta + 34pt `RowFooter` "Review claim" primary footer button) · row tap or footer button opens the claim detail overlay (Approve / Reject / Request Info) | `/app/admin/review-claims` | `GET /api/admin/claims?bucket=&limit=&offset=` (new) · `GET /api/admin/claims/counts` (new) · `GET /api/admin/claims/:claimId` · `POST /api/admin/claims/:claimId/review` | **Web only.** Mobile deferred per §1.8 of `pantopus-t5-notes.md` and F9 of `t5-buildout-plan.md`: there is no admin tier on iOS / Android today (no `me.is_admin` field, no role guard). The web page now uses the shared `<ListOfRowsShell />` mirror — same tab/banner/empty-state contract iOS and Android use for non-admin queues. Backend `/api/admin/claims` was extended with a `bucket` enum (`pending` ↔ `submitted, pending_review, needs_more_info, disputed`; `approved`; `rejected`) and per-bucket enrichment (`home`, `claimant`, `evidence_count`); `/api/admin/claims/counts` returns the three tab badges in one call. Approve / Reject / Request Info still hit the existing `POST /api/admin/claims/:claimId/review` with admin auth via `requireAdmin`. Revisit mobile when an admin role lands. |

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
