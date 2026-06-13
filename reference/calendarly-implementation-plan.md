# Calendarly — Implementation Plan

> Engineering plan for the Pantopus **Calendarly** feature: personal + family/home + business scheduling and booking, payments included. Pairs with the design prompt suite (`~/Downloads/calendarly-design-prompt-suite.md`, 94 screens) and the companion [design doc](./calendarly-design-doc.md).
>
> Status: planning. No code written yet. All file paths and line numbers below were verified against the current tree (master @ migration 158).

---

## 1. Scope & guiding decisions

- **One owner-polymorphic engine.** A single scheduling engine keyed by `owner_type ∈ {user, home, business}` + `owner_id`. The identity pill (Personal sky / Home green / Business violet) re-scopes the same screens and APIs — no parallel implementations, no sixth bottom tab.
- **Personal availability is the source of truth.** Home and Business scheduling **compose** members' personal availability (collective = intersect required members; round-robin = union + rule). They never copy it. See the [design doc](./calendarly-design-doc.md) for the engine algorithm.
- **Reuse over build.** The feature is mostly composition over existing infrastructure (see §9). The only genuinely new subsystem is the **availability / free-busy engine** (§5). External calendar sync (Google/Outlook/Apple) is deferred — it is the one expensive gap and is **not** required for v1.
- **Surface counts** (from the screen inventory): ~95 surfaces → ~30 navigable screens → **18 MVP**. Phasing in §10.

---

## 2. Architecture at a glance

```
                       ┌──────────────────────────────────────────┐
   identity pill  ───► │  Scheduling engine (owner_type/owner_id)  │
 (Personal/Home/Biz)   │  EventType · AvailabilityEngine · Booking │
                       └───────────────┬──────────────────────────┘
        ┌──────────────────────────────┼───────────────────────────────┐
   Personal (user)                Home (home_id)                Business (business_user_id)
   availability = truth      composes member availability    composes team availability
        │                              │                               │
        ▼                              ▼                               ▼
  /book/[username]            extends HomeCalendarEvent          /b/[username] booking
  (public web flow)           + HomeResource + find-a-time       round-robin via BusinessTeam
```

Reused infra (verified): `HomeCalendarEvent`, `BusinessTeam`/`BusinessSeat`, `HomeOccupancy` (members + `role_base`), `homePermissions.hasPermission`, `notificationService` + `pushService` + Socket.IO, `node-cron` jobs, the full **Stripe Connect** stack (`stripeService.js`), `BusinessInvoice`, `walletService`, public-page + token-invite patterns.

---

## 3. Data model — new tables

Migrations follow the verified convention: a numbered file `backend/database/migrations/159_calendarly_core.sql` (next number after `158_place_county_data.sql`) **plus** a byte-identical timestamped mirror in `supabase/migrations/` (latest is `20260611100000_gig_saved_searches.sql`). Use the standard skeleton: `BEGIN; … COMMIT;`, `CREATE TYPE` in `DO $$ … EXCEPTION WHEN duplicate_object` blocks, `CREATE TABLE IF NOT EXISTS`, idempotent FK `DO` blocks, indexes, `ENABLE ROW LEVEL SECURITY` + policies, `GRANT … TO authenticated, service_role`.

Split across ~3 migrations to keep them reviewable: `159_calendarly_core` (enums, event types, availability), `160_calendarly_bookings` (bookings, attendees, tokens, resources), `161_calendarly_packages_automations` (packages, workflows, connected-calendar stub).

### 3.1 Enums

```sql
CREATE TYPE "public"."scheduling_owner_type" AS ENUM ('user', 'home', 'business');
CREATE TYPE "public"."assignment_mode"      AS ENUM ('one_on_one', 'collective', 'round_robin', 'group');
CREATE TYPE "public"."location_mode"        AS ENUM ('video', 'phone', 'in_person', 'custom', 'ask');
CREATE TYPE "public"."booking_status"       AS ENUM ('pending', 'confirmed', 'cancelled', 'declined', 'completed', 'no_show');
CREATE TYPE "public"."rsvp_status"          AS ENUM ('pending', 'going', 'maybe', 'declined');
CREATE TYPE "public"."booking_token_kind"   AS ENUM ('manage', 'one_off');
```

### 3.2 Public page & event types

| Table | Key columns | Notes |
|---|---|---|
| **BookingPage** | `id`, `owner_type`, `owner_id`, `slug` (unique), `title`, `tagline`, `avatar_url`, `intro`, `confirmation_message`, `is_live` bool, `visibility` ('listed'/'unlisted'), `branding` jsonb, `created_by`, timestamps | One per owner. Drives `/book/[slug]`. Unique partial index on `slug`. |
| **EventType** | `id`, `owner_type`, `owner_id`, `page_id` FK, `name`, `slug`, `description`, `color`, `durations` int[] (minutes), `default_duration`, `location_mode`, `location_detail`, `assignment_mode`, `requires_approval` bool, `visibility` ('public'/'secret'), `buffer_before_min`, `buffer_after_min`, `min_notice_min`, `max_horizon_days`, `slot_interval_min`, `daily_cap`, `per_booker_cap`, `price_cents`, `currency`, `deposit_cents`, `schedule_id` FK (nullable), `is_active`, `sort_order`, timestamps | `schedule_id` set for personal; null for home/business (composed from assignees). |
| **EventTypeAssignee** | `id`, `event_type_id` FK, `subject_type` ('user'/'business_team'), `subject_id`, `weight` int, `priority` int, `is_active` | Round-robin/collective eligibility. For home, `subject_id` → `HomeOccupancy.user_id`; for business → `BusinessTeam.user_id`. |
| **EventTypeQuestion** | `id`, `event_type_id` FK, `label`, `field_type` ('text'/'textarea'/'select'/'multiselect'/'checkbox'/'phone'), `options` jsonb, `required` bool, `sort_order` | Intake form. Name+email are implicit defaults. |

### 3.3 Availability (personal source of truth)

| Table | Key columns | Notes |
|---|---|---|
| **AvailabilitySchedule** | `id`, `user_id` FK, `name`, `timezone`, `is_default` bool, timestamps | User-scoped only. Seed one default per user. |
| **AvailabilityRule** | `id`, `schedule_id` FK, `weekday` smallint (0–6), `start_time` time, `end_time` time | Multiple rows/weekday → multiple blocks per day. |
| **AvailabilityOverride** | `id`, `schedule_id` FK, `date`, `is_unavailable` bool, `start_time` time, `end_time` time | Date overrides + day-off + vacation ranges. |
| **AvailabilityBlock** | `id`, `user_id` FK, `title`, `start_at` timestamptz, `end_at` timestamptz, `recurrence_rule` text | The "block off time" busy override (inverse of a booking). The completeness-pass gap. Feeds free/busy directly. |

### 3.4 Bookings & lifecycle

| Table | Key columns | Notes |
|---|---|---|
| **Booking** | `id`, `event_type_id` FK, `owner_type`, `owner_id`, `page_id`, `host_user_id` (assigned member for round-robin/business), `invitee_user_id` (nullable), `invitee_name`, `invitee_email`, `invitee_phone`, `invitee_timezone`, `start_at`, `end_at`, `status` booking_status, `location_mode`, `location_detail`, `intake_answers` jsonb, `recurrence_group_id` uuid (nullable), `resource_id` FK (nullable), `payment_id` FK→Payment (nullable), `package_credit_id` FK (nullable), `created_via` ('public_link'/'in_app'/'manual'/'one_off'), `cancel_reason`, `cancelled_by`, `created_by`, timestamps | Indexes: `(owner_type, owner_id, start_at DESC)`, `(host_user_id, start_at)`, `(invitee_user_id, start_at)`, `(status)`. A confirmed **home** booking also writes a `HomeCalendarEvent` (event_type `appointment`/`resource_booking`) so it shows on the existing calendar. |
| **BookingAttendee** | `id`, `booking_id` FK, `user_id`, `rsvp_status`, `is_required` bool | Group events, collective required-members, and home RSVP. |
| **BookingToken** | `id`, `booking_id` (nullable), `event_type_id` (nullable), `token_hash`, `kind` booking_token_kind, `expires_at`, `single_use` bool, `consumed_at` | Reuses the verified `businessSeats` pattern: `crypto.randomBytes(32).toString('hex')` → store SHA-256 hash, send raw token. Powers `/manage` links and one-off links. |

### 3.5 Home extensions

| Table | Key columns | Notes |
|---|---|---|
| **HomeResource** | `id`, `home_id` FK, `name`, `resource_type` ('room'/'vehicle'/'tool'/'charger'/'other'), `photo_url`, `who_can_book` ('members'/'specific'/'guests'), `max_duration_min`, `buffer_min`, `requires_approval` bool, `available_hours` jsonb, `created_by`, timestamps | Resource bookings are `Booking` rows with `resource_id` set + `owner_type='home'`. |

Find-a-time, who's-free, and visits do **not** need new tables — they are computed views over member availability + `HomeCalendarEvent` + `Booking`. Poll responses reuse the existing **Polls** module (`HomePoll`) with time-slot options.

### 3.6 Payments, packages, invoicing (mostly reuse — see §9)

| Table | Key columns | Notes |
|---|---|---|
| **BookingPackage** | `id`, `owner_type`, `owner_id`, `name`, `sessions_count`, `price_cents`, `currency`, `event_type_id` (nullable), `is_active`, timestamps | A sellable bundle of sessions. |
| **PackageCredit** | `id`, `package_id` FK, `buyer_user_id`, `total`, `remaining`, `payment_id` FK→Payment, `purchased_at`, `expires_at` | Redeemed by `Booking.package_credit_id`. |

**Invoices** reuse the existing `BusinessInvoice` table (`line_items` jsonb, `subtotal_cents`, `fee_cents`, `total_cents`, `status`, `payment_id`, `stripe_payment_intent_id`). **Payments** reuse the `Payment`/`Refund`/`Payout`/`Wallet`/`WalletTransaction` tables — add `payment_type='booking_payment'` and (optionally) `'package_payment'` enum values; no new payment tables.

### 3.7 Automations & deferred

| Table | Key columns | Notes |
|---|---|---|
| **SchedulingWorkflow** | `id`, `owner_type`, `owner_id`, `event_type_id` (nullable), `name`, `trigger` ('booking_created'/'cancelled'/'rescheduled'/'before_start'/'after_end'), `offset_minutes`, `action` ('email'/'push'/'in_app'/'sms'), `message_template`, `is_active` | v1 ships a "default reminders" preset (1 day + 1 hour before); the full editor is v2. |
| **BookingReminderLog** | `id`, `booking_id` FK, `workflow_id` (nullable), `kind`, `sent_at` | Idempotence for the reminder cron (mirrors `auto_reminder_count`/`last_worker_reminder_at` on gigs). |
| **ConnectedCalendar** *(v2 stub)* | `id`, `user_id`, `provider`, `external_account`, `access_token_enc`, `refresh_token_enc`, `sync_token`, `check_conflicts` bool, `write_target` bool, `last_synced_at`, `status` | Created empty/disabled in v1; the engine reads it only when populated. The deferred external-sync work lands here. |

### 3.8 RLS & access control

Match the existing pattern: **route handlers are the primary gate** (they use `supabaseAdmin`, the `service_role` client that bypasses RLS, after calling `homePermissions.hasPermission(...)`), with **RLS as defense-in-depth**.

- **Home-scoped** rows (`HomeResource`, home `Booking`/`EventType`/`BookingPage`): RLS via `home_has_permission(home_id, 'calendar.view'|'calendar.edit')` + `home_can_see_visibility(...)`, exactly like `HomeCalendarEvent` (`schema.sql:6240`). The `calendar.view`/`calendar.edit` permissions already exist.
- **User-scoped** rows (`AvailabilitySchedule`, `AvailabilityRule/Override/Block`, personal `EventType`/`BookingPage`/`Booking`): RLS via `created_by = auth.uid()` / `user_id = auth.uid()`.
- **Business-scoped** rows: RLS via a `BusinessTeam` membership check (model on existing business policies).
- **Public read**: `/api/public/book/:slug` reads via `supabaseAdmin` and redacts (the `routes/public.js` pattern); no public RLS policy needed.

---

## 4. Backend — routes & services

### 4.1 New files (follow the verified anatomy)

```
backend/routes/scheduling.js          # personal + business host APIs (mount: app.use('/api/scheduling', ...))
backend/routes/schedulingPublic.js    # OR add to routes/public.js — the /book/:slug flow
backend/services/scheduling/
  availabilityService.js              # the free/busy compute engine (§5) — THE new subsystem
  bookingService.js                   # create/confirm/cancel/reschedule + conflict detection
  eventTypeService.js                 # CRUD + assignee/question management
  packageService.js                   # package purchase + credit redemption (wraps stripeService)
  schedulingPaymentsService.js        # thin booking↔Payment orchestration over stripeService
backend/jobs/bookingReminders.js      # node-cron, modeled on jobs/autoRemindWorker.js
```

Home-scoped endpoints mount under the existing home router surface (`/api/homes/:id/scheduling/*`) so they inherit `homePermissions`; personal/business under `/api/scheduling`. Mount in `backend/app.js` near the other feature routers (`app.use('/api/...', require('./routes/...'))`, lines ~305–404), **before** any catch-all `/:id` routes.

All services `require('../../config/supabaseAdmin')`. All write routes use `verifyToken` + `validate(joiSchema)`; public read/booking routes use `optionalAuth` or no auth + `previewLimiter` (the `routes/public.js` pattern).

### 4.2 Endpoint inventory

**Host — authed (`/api/scheduling`, owner context via query/body; home alias `/api/homes/:id/scheduling`):**

| Method | Path | Purpose |
|---|---|---|
| GET/POST | `/event-types` | list / create |
| GET/PUT/DELETE | `/event-types/:id` | read / update / delete |
| PUT | `/event-types/:id/questions` · `/assignees` | intake + round-robin/collective config |
| GET/POST | `/availability` | list / create schedule |
| GET/PUT/DELETE | `/availability/:id` | read / update / delete schedule |
| PUT | `/availability/:id/rules` · `/overrides` | weekly hours + date overrides |
| POST/DELETE | `/availability/blocks` · `/blocks/:id` | block off / unblock time |
| GET/PUT | `/booking-page` · `/booking-page/slug` | page config + slug claim |
| GET | `/bookings?status&scope` | inbox (Upcoming/Pending/Past/Cancelled, scope pill) |
| GET | `/bookings/:id` | detail |
| POST | `/bookings` | manual / on-behalf booking |
| POST | `/bookings/:id/approve` · `/decline` · `/reschedule` · `/cancel` · `/no-show` · `/reassign` | lifecycle |
| GET/POST · GET/PUT | `/packages` · `/packages/:id` | package CRUD |
| GET | `/invoices` · `/invoices/:id` | reuse `BusinessInvoice` |
| POST | `/invoices/:id/send` | reuse |
| GET/POST · PUT/DELETE | `/workflows` · `/workflows/:id` | automations (v2) |
| GET | `/insights` | analytics (v2) |

**Public — no auth (`/api/public`):**

| Method | Path | Purpose |
|---|---|---|
| GET | `/book/:slug` | page + visible event types |
| GET | `/book/:slug/:eventTypeSlug/slots?from&to&tz` | **computed free slots** (calls availabilityService) |
| POST | `/book/:slug/:eventTypeSlug` | create booking (pending or confirmed; payment step if priced) |
| GET | `/booking/:token` | manage view via token |
| POST | `/booking/:token/reschedule` · `/cancel` | invitee-side, policy-gated |
| POST | `/book/poll/:id/vote` | meeting poll vote (reuse Polls) |

**Home find-a-time / resources (`/api/homes/:id/scheduling`):**

| Method | Path | Purpose |
|---|---|---|
| POST | `/find-a-time` | composed slots for {members, mode, duration, window} |
| GET | `/whos-free?from&to` | composed availability grid |
| GET/POST · GET/PUT/DELETE | `/resources` · `/resources/:rid` | resource CRUD |
| POST | `/resources/:rid/book` | book a resource (conflict-checked) |
| POST | `/visits` | vendor/guest visit window + optional link |

### 4.3 Notifications, realtime, reminders (reuse)

- Register `booking_*` templates in `backend/services/notificationTemplateRegistry.js` (`booking_confirmed`, `booking_request`, `booking_cancelled`, `booking_rescheduled`, `booking_reminder_24h`, `booking_reminder_1h`, `rsvp_request`, `find_a_time_proposed`).
- Fire via `notificationService.createNotification({ userId, type, title, body, icon, link, metadata, context: 'personal' })` — same signature used by `autoRemindWorker.js`. In-app + push (`pushService.sendToUser`) + Socket.IO `notification:new` all flow automatically.
- Reminders: `backend/jobs/bookingReminders.js` on `cron.schedule('*/15 * * * *', wrapJob('bookingReminders', fn), { timezone: 'UTC' })`, registered in `backend/jobs/index.js`. Query confirmed bookings in the T-24h / T-1h windows, dedupe via `BookingReminderLog`. Direct port of the `autoRemindWorker.js` pattern.

### 4.4 Payments orchestration (reuse `stripeService.js`)

- **Paid booking**: at booking creation with a priced event type, create a `Payment` (`payment_type='booking_payment'`) via a `createPaymentIntentForBooking` wrapper around `createPaymentIntentForGig` (manual capture). On confirm → `capturePayment`. On cancel within policy → `createSmartRefund` (handles pre-capture cancel, post-capture refund, post-transfer clawback). Settlement → existing `processPendingTransfers` cron + `walletService.creditGigIncome` (add a `booking_income` ledger type).
- **Package**: `packageService.purchase` creates a `Payment` + `PackageCredit`; redemption sets `Booking.package_credit_id` and decrements `remaining` (no charge at redemption).
- **Invoice**: populate `BusinessInvoice.line_items` at confirm, link `payment_id`.
- **Webhooks**: route booking-tagged events through the existing `POST /api/webhooks/stripe` (`stripeWebhooks.js`) — add `metadata.kind='booking'` handling alongside gig handling. No new webhook endpoint.
- **Connect onboarding**: reuse `createConnectAccount` + `createAccountLink`; the "Payments setup" screen links to the existing flow. Fees via `calculateFees` / `getEffectiveFeeRate`.

---

## 5. The availability / free-busy engine (the one new subsystem)

`availabilityService.computeSlots({ ownerType, ownerId, eventType, from, to, viewerTimezone })` → `Slot[]`. Algorithm (full pseudocode and edge cases in the [design doc](./calendarly-design-doc.md §4)):

1. **Resolve the availability set.**
   - `user`: the event type's `AvailabilitySchedule` (rules + overrides).
   - `home`/`business`: the `EventTypeAssignee` set → each member's personal schedule, then **intersect** (collective) or keep per-member (round-robin).
2. **Generate candidate starts** at `slot_interval_min` within working windows across `[from, to]`, in the schedule's timezone.
3. **Subtract busy**: existing `Booking`s (same owner/host), `AvailabilityBlock`s, and — for home — overlapping `HomeCalendarEvent`s; apply `buffer_before/after_min`.
4. **Apply guardrails**: `min_notice_min`, `max_horizon_days`, `daily_cap`, `per_booker_cap`, override-blocked dates.
5. **Round-robin**: for each surviving slot, pick the eligible member by rule (fair rotation / priority / weight); attach `host_user_id`.
6. **Convert** to `viewerTimezone` (DST-safe) and return labelled slots.

This is the highest-risk code (timezones, DST, buffers, intersection); budget real test time. It is also the single shared component reused by the invitee picker, host reschedule, and home find-a-time.

---

## 6. Shared TS packages

- `frontend/packages/types/src/scheduling.ts` — `EventType`, `AvailabilitySchedule`, `Booking`, `BookingPage`, `Slot`, `BookingPackage`, etc. (add to the barrel; mirror the `home.ts` style).
- `frontend/packages/api/src/endpoints/scheduling.ts` — host endpoints using the `get/post/put/del` helpers from `client.ts`; `endpoints/publicBooking.ts` for the `/api/public/book/*` flow (server-fetch friendly).
- `frontend/packages/utils/src/index.ts` — add `buildBookingPath(slug)` → `/book/${slug}` and `buildBookingPageUrl(slug)` → `${APP_WEB_URL}/book/${slug}` (mirrors `buildSupportTrainPath`/`buildSupportTrainShareUrl`).

---

## 7. Web surfaces

- **Public flow**: `frontend/apps/web/src/app/book/[slug]/page.tsx` (+ `book/[slug]/[eventType]/page.tsx`). Server-component fetch via a `fetchPublicBooking` helper in `src/lib/publicShare.ts` (mirrors `fetchPublicSupportTrain`, ISR `revalidate: 60`). No auth — matches `support-trains/[id]`. Add OG metadata for sharing.
- **Host management**: `frontend/apps/web/src/app/(app)/app/profile/schedule/*` (hub, event types, availability, booking page, bookings inbox, packages, invoices).
- **Reuse**: the existing `app/homes/[id]/calendar/page.tsx` and `app/support-trains/[id]/calendar/page.tsx` for home-context and slot-grid patterns.

---

## 8. Native route additions

### 8.1 iOS (`frontend/apps/ios/Pantopus`)

1. **New module** `Features/Scheduling/` (Hub, EventTypeList/Editor, Availability, BookingsInbox, BookingDetail + action sheets), using the shared shells `ListOfRowsView`, `FormShell`, `WizardShell` (it already exposes `WizardIdentity` `.personal/.home/.business`), `ContentDetailShell` (`Features/Shared/...`).
2. **Routes**: add cases to `HubRoute` (`Features/Root/HubTabRoot.swift:13`) for home-scoped scheduling and to `YouRoute` (`Features/Root/YouTabRoot.swift:16`) for personal; wire each in the `destination(for:)` switch (the same place `homeCalendar`/`calendarEventDetail` are wired).
3. **Me entry**: add a `MeActionTile`/`MeSectionRow` with `routeKey = "me.scheduling"` in `Features/Me/MeViewModel.swift` (`homeActionTiles` ~L301, `homeSections` ~L314; add a Personal-pillar tile too), then handle it in `YouTabRoot.handleAction(...)` / `handleSection(...)` (~L521/L608).
4. **Networking**: `Core/Networking/Endpoints/SchedulingEndpoints.swift` (mirror `HomesEndpoints`/`GigsEndpoints`, annotate each with `/// Route backend/routes/scheduling.js:LINE`) + `Core/Networking/Models/Scheduling/*DTOs.swift` (Decodable/Encodable with snake_case `CodingKeys`, like `CalendarEventDTOs.swift`). Calls via `APIClient.shared.request(...)`.
5. **Extend** `Features/Homes/Calendar/` for find-a-time, who's-free, and the FAB create-menu (don't replace `HomeCalendarView`).

### 8.2 Android (`frontend/apps/android/app/.../`)

1. **New package** `ui/screens/scheduling/` using shared shells `shared/list_of_rows`, `shared/form`, `shared/wizard`, `shared/content_detail`.
2. **Routes**: add constants + `fun` builders to `ChildRoutes` in `ui/screens/root/RootTabScreen.kt` (~L307/L407) and `composable(route, arguments=...)` blocks in the NavHost (~L1738+), mirroring `HOME_CALENDAR`.
3. **Me entry**: add a `MeActionTile`/`MeSectionRow` with `routeKey = "me.scheduling"` in `ui/screens/you/me/MeViewModel.kt` (`homeActionTiles` ~L327; add Personal too); wire route handling in `YouScreen.kt`.
4. **Networking**: `data/api/services/SchedulingApi.kt` (Retrofit, annotate `/// Route ...`) + `data/api/models/scheduling/*Dtos.kt` (`@JsonClass(generateAdapter=true)`, `@Json(name="snake_case")`) + a `SchedulingRepository` (`safeApiCall`) + DI in `NetworkModule.kt`. ViewModels `@HiltViewModel` with `StateFlow<…UiState>` sealed states.
5. **Extend** `ui/screens/homes/calendar/` for find-a-time + create menu.

Both platforms already have working Calendar modules and DTOs (`CalendarEventDTO`/`CalendarEventDto`) to copy verbatim as the starting template.

---

## 9. Reuse map (what we do *not* build)

| Need | Reuse |
|---|---|
| Household calendar, recurrence field, multi-assignee | `HomeCalendarEvent` (`schema.sql:6240`; `assigned_to uuid[]`, `recurrence_rule`) |
| Slot grid / reservation visuals | Support Trains UI (web + native) |
| Business "team" for round-robin | `BusinessTeam` (`schema.sql:5243`) + `BusinessSeat` invites |
| Home members + roles + permissions | `HomeOccupancy` (`role_base`), `homePermissions.hasPermission` (`utils/homePermissions.js:53`), `calendar.view`/`calendar.edit` |
| Paid bookings, refunds, transfers, escrow | `stripeService.js` (`createPaymentIntentForGig`, `capturePayment`, `createSmartRefund`, `createTransfer`, `calculateFees`), `paymentStateMachine.js`, `processPendingTransfers` cron |
| Invoices | `BusinessInvoice` table |
| Payouts / earnings | `walletService.js`, `earningsService.js`, `routes/wallet.js`, Wallet/Earn screens |
| Notifications + push + realtime | `notificationService`, `notificationTemplateRegistry`, `pushService`, `chatSocketio` (`notification:new`) |
| Time-based reminders | `jobs/autoRemindWorker.js` pattern + `node-cron` (`jobs/index.js`) |
| Shareable / one-off links | `businessSeats` token pattern (`crypto.randomBytes` + SHA-256 + expiry) |
| Public pages | `routes/public.js`, web `[username]`/`support-trains/[id]`, `lib/publicShare.ts` |
| Polls (find-a-time / meeting polls) | existing `HomePoll` module |
| Native shells | `ListOfRows`/`Form`/`Wizard`/`ContentDetail` (both platforms) |

---

## 10. v1 / v2 phasing

**Phase 0 — Engine + data (foundation).** Migrations 159–161; `availabilityService` + tests; `bookingService` core; shared types. No UI.

**Phase 1 — Personal MVP (the 18-screen happy path).** Event-type list/editor, availability editor + block-off-time, booking-page management + share, public `/book/[slug]` flow (landing → slot picker → details+confirm → confirmed → add-to-calendar), bookings inbox + detail + approve/reschedule/cancel, default reminders. Personal pillar only. **This is the minimum lovable v1.**

**Phase 2 — Family/home.** Extend Home calendar (FAB menu, RSVP), find-a-time (compose member availability), who's-free, resources, schedule-a-visit. Home pillar.

**Phase 3 — Business + payments.** Business pillar (team round-robin via `BusinessTeam`), paid bookings, packages, invoices — all orchestration over existing Stripe infra.

**Phase 4 — Automations + insights.** Workflow editor, message templates, analytics dashboards.

**Deferred (Tier C).** External calendar sync (Google → Outlook → Apple/CalDAV), video integration (Zoom/Meet/Teams), SMS/WhatsApp (Twilio). The `ConnectedCalendar` stub exists so the engine can light up later without a schema change. **Caveat:** until sync ships, availability reflects only in-Pantopus events — communicate this as the known v1 limitation.

---

## 11. Risks & sequencing

1. **Free/busy correctness** (timezones, DST, buffers, intersection) is the top risk — write the engine first, behind a thorough test suite, before any UI.
2. **Recurrence expansion** (RRULE→instances) is needed for recurring bookings and recurring availability blocks; pull in a vetted RRULE library, don't hand-roll.
3. **No external sync in v1** is a product gap, not a bug — be explicit with users that availability only sees Pantopus events until Phase-deferred sync lands.
4. **Migrations must ship in lockstep** with the `supabase/migrations/` mirror (timestamped) or environments drift — this repo has had missing-migration drift before.
5. **Targeted tests only** (per team convention) — test the new scheduling services/screens, not full suites.

---

## 12. Open decisions

- **Owner context in API**: query/body `owner_type`+`owner_id` on `/api/scheduling` vs. a home alias under `/api/homes/:id/scheduling`. Recommend: home under the home router (inherits permissions), personal/business under `/api/scheduling`.
- **Confirmed home bookings**: write-through to `HomeCalendarEvent` (recommended, so they appear on the existing calendar) vs. a calendar query that unions `Booking` + `HomeCalendarEvent`. Recommend write-through.
- **Slug namespace**: `/book/[username]` shares the username namespace; decide collision rules with existing `[username]` public routes (likely a distinct `/book/` prefix avoids conflict — confirmed safe).
- **Package fee override**: extend `getEffectiveFeeRate` to read a per-package override, or inherit business default. Recommend inherit for v1.
