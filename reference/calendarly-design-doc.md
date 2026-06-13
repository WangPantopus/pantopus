# Calendarly — Design Doc

> Product & architecture design for **Calendarly**, Pantopus's scheduling/booking layer (a Calendly/Cal.com analog scoped to a neighborhood/home app). Companion to the [implementation plan](./calendarly-implementation-plan.md) (tables, endpoints, phasing) and the design prompt suite (`~/Downloads/calendarly-design-prompt-suite.md`, 94 screens). This doc covers the *why* and the engine *algorithm*.

---

## 1. Problem & shape

Pantopus already has an internal shared **household calendar** (`HomeCalendarEvent`) and a slot/reservation primitive (**Support Trains**). What it lacks is **bookable availability** — the Calendly idea: publish when you're free, share a link, let someone pick a slot, and have it land on your calendar without back-and-forth.

Calendarly adds that booking layer across three contexts that already exist as the app's identity pillars:

- **Personal** (sky) — "book time with me" (a neighbor, a tutor, a handyman, a consultation).
- **Family/Home** (green) — coordinate across a household: find a time everyone's free, book the guest room, schedule a vendor when someone's home.
- **Business** (violet) — book a service; distribute across a team.

The product bet: most of this is *composition* over infrastructure Pantopus already has. The literal Cal.com superset (CRM routing, dev platform, enterprise SSO, self-hosting) is out of scope — it doesn't fit a neighborhood app. We build the slice that fits.

---

## 2. Core model — one engine, owner-polymorphic

A single scheduling engine keyed by `(owner_type, owner_id)` where `owner_type ∈ {user, home, business}`. The same screens, APIs, and engine serve all three; the **identity pill re-scopes** the data. This is idiomatic to the codebase — `MeView` already splits into Personal/Home/Business pillars, and `notificationService` already has personal/audience/platform contexts.

Why one engine, not three features:

- **No duplication.** Event types, availability, bookings, reminders, payments are structurally identical across contexts; only the *owner* and the *availability source* differ.
- **Composability.** Home and Business scheduling are defined *in terms of* Personal availability (next section). Three separate features couldn't share that cleanly.
- **One mental model for users.** Switching the pill re-scopes the screen — it doesn't open a different app.

### The central invariant: personal availability is the source of truth

A user's availability (working hours + overrides + busy blocks + existing bookings) lives in **exactly one place** — their personal `AvailabilitySchedule`. Home and Business never store a copy. They **compose** it:

- **Collective** ("when is the whole household / the required staff free?") → **intersect** the required members' free time.
- **Round-robin** ("any one of us can take it") → **union** the members' free time; assign each slot to an eligible member by rule.

This is the principled meaning of "family references personal": a member edits their hours once, and every context they participate in stays correct. It's Calendly's *collective* and *round-robin* event types, repurposed for a household and a small business.

```
Personal availability (atomic)
   ├── Home "find a time"  = ∩ required members   (collective)
   │                       = ∪ members + rule      (round-robin)
   └── Business service    = ∪ team seats + rule   (round-robin)
                           = ∩ required staff       (collective)
```

---

## 3. Entities (conceptual)

- **BookingPage** — the public face of an owner (`/book/[slug]`): who they are, which event types are visible.
- **EventType** — a bookable template: duration(s), location, price, buffers, limits, approval, assignment mode, intake questions. (Calendly "event type"; for business, a "service".)
- **AvailabilitySchedule** (+ rules, overrides, blocks) — *personal only*. Weekly hours, date overrides, and ad-hoc busy blocks. The free/busy source.
- **Booking** — a confirmed/pending slot with an invitee. For home it write-throughs to `HomeCalendarEvent` so it shows on the existing calendar.
- **EventTypeAssignee** — for home/business, which members are eligible (with weight/priority) — the bridge to composition.
- **Package / PackageCredit**, **Invoice** (reused `BusinessInvoice`), **Payment** (reused) — monetization.

Full schema in the [implementation plan §3](./calendarly-implementation-plan.md).

---

## 4. The availability engine (the one new subsystem)

`computeSlots({ ownerType, ownerId, eventType, from, to, viewerTimezone }) → Slot[]`. This is the only genuinely new, non-trivial code; everything else is CRUD + reuse. Pseudocode:

```
function computeSlots(ownerType, ownerId, eventType, from, to, viewerTz):
    # 1. Resolve whose availability matters
    if ownerType == 'user':
        members = [ownerId]
        schedules = { ownerId: eventType.schedule }      # personal source of truth
    else:                                                 # home or business
        members = activeAssignees(eventType)              # HomeOccupancy / BusinessTeam
        schedules = { m: defaultSchedule(m) for m in members }

    # 2. Per member, build free intervals in [from, to]
    freeByMember = {}
    for m in members:
        windows = weeklyWindows(schedules[m], from, to)   # AvailabilityRule, in schedule tz
        windows = applyOverrides(windows, schedules[m])   # AvailabilityOverride (day off / special)
        busy    = bookings(m) ∪ blocks(m)                 # Booking + AvailabilityBlock
        if ownerType == 'home':
            busy = busy ∪ homeCalendarEvents(ownerId, m)  # existing shared calendar = busy
        free = subtract(windows, expand(busy, eventType.buffers))   # buffers padding
        freeByMember[m] = free

    # 3. Combine per assignment mode
    if mode == 'collective':
        combined = intersectAll(freeByMember.values())    # all required free
    elif mode == 'round_robin':
        combined = unionWithOwner(freeByMember)            # any one; remember who
    else:                                                  # one_on_one / group
        combined = freeByMember[members[0]]

    # 4. Discretize into candidate starts and apply guardrails
    slots = gridStarts(combined, eventType.slot_interval_min, eventType.default_duration)
    slots = filter(slots, minNotice, maxHorizon, dailyCap, perBookerCap)

    # 5. Round-robin assignment per slot
    if mode == 'round_robin':
        for s in slots: s.hostUserId = pickByRule(eligibleAt(s), rule)   # fair / priority / weight

    # 6. Present in the viewer's timezone (DST-safe)
    return [toTz(s, viewerTz) for s in slots]
```

The same function backs three surfaces: the invitee slot picker, the host reschedule sheet, and the home "find a time" flow.

### Edge cases that must be handled (the bug-prone ones)

- **Timezones & DST.** Store schedule times as wall-clock `time` + schedule `timezone`; compute in that tz; convert to viewer tz at the end. Never store local times as UTC. A 9:00 rule must stay 9:00 across a DST boundary.
- **Empty intersection is normal.** Collective composition frequently yields *zero* slots in a window — that's an expected state with a "try a wider range / relax members" affordance, not an error.
- **Buffer math.** Buffers pad *busy* intervals before subtraction, not the candidate starts.
- **Recurrence.** Both recurring availability blocks and recurring bookings need RRULE expansion — use a vetted library; the `recurrence_rule` field already exists on `HomeCalendarEvent`.
- **Round-robin fairness.** Track last-assigned per member to rotate fairly; priority/weight are overrides on top.
- **Race on booking.** Two invitees can select the same slot; the create path must re-check and 409 ("slot taken") atomically.

---

## 5. Booking lifecycle

```
            ┌────────── requires_approval ──────────┐
            ▼                                        │
  (book) → pending ──approve──► confirmed ──start──► completed
            │  └──decline──► declined        │ └──no-show──► no_show
            │                                ├──reschedule──► confirmed (new time)
   auto-confirm ─────────────► confirmed     └──cancel──► cancelled (+ refund per policy)
```

- **Approval** is an event-type setting; auto-confirm skips `pending`.
- **Reschedule** can be host-forced or invitee-proposed (the propose-vs-force toggle).
- **Cancel** runs the policy: within free-window → full refund; otherwise per cancellation policy; deposits may be non-refundable. Refund path reuses `stripeService.createSmartRefund` (pre-capture cancel / post-capture refund / post-transfer clawback).
- Every transition fires a notification (in-app + push + realtime) via the existing `notificationService`/`pushService`/Socket.IO.

---

## 6. Reuse philosophy

The design deliberately maximizes reuse so the surface area of new, risky code is small:

| Concern | Decision |
|---|---|
| Household calendar | Confirmed home bookings **write through** to `HomeCalendarEvent` — one calendar, not two. |
| Payments | **Zero new payment tables.** Bookings/packages create `Payment` rows and ride the existing Stripe Connect → escrow → wallet → payout pipeline. Invoices reuse `BusinessInvoice`. |
| Team | Round-robin uses the existing `BusinessTeam`/`BusinessSeat` model — no new membership concept. |
| Reminders | A cron job cloned from `autoRemindWorker.js`; templates added to the existing registry. |
| Links | Token links reuse the `businessSeats` crypto+hash+expiry pattern. |
| Slot UI | Native + web reuse the Support Trains slot grid; home context extends the existing calendar module. |

The result: the *only* substantial new subsystem is the availability engine (§4). Everything else is schema + CRUD + wiring.

---

## 7. What we deliberately don't build (and why)

- **External calendar sync** (Google/Outlook/Apple/CalDAV) — the most expensive piece (OAuth per provider, token refresh, free/busy read, write-back, change webhooks). Deferred. The `ConnectedCalendar` table is stubbed so it can light up without a schema change. **Known v1 limitation:** availability reflects only in-Pantopus events until this ships. This is the single most important thing to communicate to users.
- **Video integration** (Zoom/Meet/Teams) — per-provider OAuth; deferred. First-party video — never.
- **Enterprise/dev-platform features** — CRM/ATS routing, public REST API, OAuth provider, SAML/SCIM, self-hosting, seat-based org billing, browser extensions. Out of scope: these serve B2B SaaS, not a neighborhood app.
- **SMS/WhatsApp** — push + in-app cover v1; Twilio is a later channel.

---

## 8. Decision log

| Decision | Choice | Rationale |
|---|---|---|
| One engine vs. three features | **One**, owner-polymorphic | No duplication; composition; one user model. |
| Availability storage | **Personal-only**, composed for home/business | "Family references personal"; edit-once correctness. |
| Home bookings | **Write through to `HomeCalendarEvent`** | Appear on the existing calendar; one source. |
| Payments | **Reuse Stripe stack**, no new tables | `stripeService` + `BusinessInvoice` + wallet already do escrow/refund/payout. |
| Public flow host | **Web `/book/[slug]`** | Public pages live on web; mirrors `support-trains/[id]`; native handles host + in-app invitee. |
| Navigation | **No new bottom tab**; under You/Me pillars + Home calendar | Explicit anti-goal; keeps the app's 5-tab shell. |
| External sync | **Deferred** (stub table) | Most expensive; not required for the core loop; lights up later without migration. |
| Build order | **Engine → Personal → Home → Business/payments → automations** | Front-load the risky engine; ship the 18-screen personal MVP first. |

---

## 9. Success criteria for v1

A first user can, in under ~2 minutes: claim a `/book/[username]` link, accept default hours, share it, and receive + approve a booking — all without leaving the app, and with a reminder that fires before the meeting. The home pillar can "find a time" that correctly intersects two members' availability. No external calendar sync required to be useful, with that limitation clearly surfaced.
