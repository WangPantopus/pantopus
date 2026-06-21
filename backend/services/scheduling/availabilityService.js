// ============================================================
// Calendarly — availability / free-busy engine (the one genuinely new subsystem).
//
// computeSlots({ ownerType, ownerId, eventType, from, to, viewerTimezone }) -> Slot[]
//
// Design (see reference/calendarly-design-doc.md §4):
//   - Personal availability (schedule + rules + overrides + blocks) is the source of truth.
//   - Home/Business compose members' personal availability:
//       collective  = intersect required members' free time
//       round_robin = union of members; per-slot we return the eligible host SET
//                     (the fair-rotation PICK happens at booking-create time, not here).
//   - Wall-clock rules ("9:00") are resolved to instants in the schedule's timezone via luxon,
//     so a 9:00 rule stays 9:00 across a DST boundary. Slots are returned as UTC instants plus
//     a viewer-timezone rendering.
//   - Busy = existing bookings ∪ availability blocks ∪ (home) shared-calendar events. Recurring
//     busy is RRULE-expanded across the window regardless of anchor date (a naive start_at-window
//     filter would silently drop a series anchored before `from` and cause double-booking).
//   - Buffers pad BUSY intervals (not candidate starts): a candidate [s, s+D] must fit in free
//     space where busy has been padded by [busy.start - buffer_after, busy.end + buffer_before].
//
// The pure core (interval math) takes already-fetched data and is unit-tested without a DB.
// The DB wrapper batches queries (one per source, ANY(member_ids)) to avoid N+1.
// ============================================================

const { DateTime } = require('luxon');
const { rrulestr } = require('rrule');
const supabaseAdmin = require('../../config/supabaseAdmin');
const logger = require('../../utils/logger');

const MIN_MS = 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;
const MAX_RECURRENCE_INSTANCES = 1000; // safety bound on RRULE expansion per series

// ------------------------------------------------------------
// Interval math — intervals are { start, end } in epoch ms, half-open [start, end).
// ------------------------------------------------------------

/** Merge a list of intervals into a sorted, non-overlapping set. */
function mergeIntervals(intervals) {
  const valid = intervals.filter((i) => i && i.end > i.start).sort((a, b) => a.start - b.start);
  const out = [];
  for (const iv of valid) {
    const last = out[out.length - 1];
    if (last && iv.start <= last.end) {
      last.end = Math.max(last.end, iv.end);
    } else {
      out.push({ start: iv.start, end: iv.end });
    }
  }
  return out;
}

/** base minus blockers (both lists of intervals). Returns the free remainder. */
function subtractIntervals(base, blockers) {
  const merged = mergeIntervals(blockers);
  let result = mergeIntervals(base);
  for (const block of merged) {
    const next = [];
    for (const iv of result) {
      if (block.end <= iv.start || block.start >= iv.end) {
        next.push(iv); // no overlap
        continue;
      }
      if (block.start > iv.start) next.push({ start: iv.start, end: block.start });
      if (block.end < iv.end) next.push({ start: block.end, end: iv.end });
    }
    result = next;
  }
  return result.filter((i) => i.end > i.start);
}

/** Intersection of two interval lists. */
function intersectTwo(a, b) {
  const A = mergeIntervals(a);
  const B = mergeIntervals(b);
  const out = [];
  let i = 0;
  let j = 0;
  while (i < A.length && j < B.length) {
    const start = Math.max(A[i].start, B[j].start);
    const end = Math.min(A[i].end, B[j].end);
    if (end > start) out.push({ start, end });
    if (A[i].end < B[j].end) i++;
    else j++;
  }
  return out;
}

/** Intersection across many interval lists. Empty input -> empty (no availability). */
function intersectAll(lists) {
  if (!lists.length) return [];
  let acc = mergeIntervals(lists[0]);
  for (let k = 1; k < lists.length; k++) {
    acc = intersectTwo(acc, lists[k]);
    if (!acc.length) break;
  }
  return acc;
}

/** True if some interval in `intervals` fully covers [s, e]. */
function intervalsCover(intervals, s, e) {
  return intervals.some((iv) => iv.start <= s && iv.end >= e);
}

// ------------------------------------------------------------
// Recurrence — expand a recurring busy series across [fromMs, toMs].
// ------------------------------------------------------------

/**
 * Expand a recurrence_rule into busy instances overlapping [fromMs, toMs].
 * `anchorMs`/`durationMs` describe the series' first occurrence. Falls back to the single
 * stored interval if the rule is absent or unparseable (so we never drop busy).
 */
function expandRecurrence(rule, anchorMs, durationMs, fromMs, toMs) {
  if (!rule || typeof rule !== 'string' || !rule.trim()) {
    return [{ start: anchorMs, end: anchorMs + durationMs }];
  }
  try {
    const dtstart = new Date(anchorMs);
    const ruleObj = rrulestr(rule.trim(), { dtstart });
    // Search window padded back by one duration so an occurrence that STARTS before `from`
    // but ends inside the window is still captured.
    const after = new Date(Math.max(0, fromMs - durationMs - DAY_MS));
    const before = new Date(toMs + DAY_MS);
    const occurrences = ruleObj.between(after, before, true).slice(0, MAX_RECURRENCE_INSTANCES);
    const out = [];
    for (const occ of occurrences) {
      const s = occ.getTime();
      const e = s + durationMs;
      if (e > fromMs && s < toMs) out.push({ start: s, end: e });
    }
    // Always include the anchor occurrence itself if it overlaps the window.
    if (anchorMs + durationMs > fromMs && anchorMs < toMs) {
      out.push({ start: anchorMs, end: anchorMs + durationMs });
    }
    return mergeIntervals(out);
  } catch (err) {
    logger.warn('[availabilityService] recurrence parse failed; treating as single occurrence', {
      rule,
      error: err.message,
    });
    return [{ start: anchorMs, end: anchorMs + durationMs }];
  }
}

// ------------------------------------------------------------
// Schedule -> free working windows in [fromMs, toMs], DST-correct via luxon.
// weekday convention: 0=Sunday .. 6=Saturday (JS getDay()).
// ------------------------------------------------------------

function parseHm(timeStr) {
  // "HH:MM:SS" or "HH:MM"
  const [h, m] = String(timeStr).split(':');
  return { hour: parseInt(h, 10) || 0, minute: parseInt(m, 10) || 0 };
}

/**
 * @param {{timezone, rules:[{weekday,start_time,end_time}], overrides:[{date,is_unavailable,start_time,end_time}]}} schedule
 * @returns {Array<{start,end}>} working windows (ms) clipped to [fromMs, toMs]
 */
function buildWeeklyWindows(schedule, fromMs, toMs) {
  const tz = schedule.timezone || 'UTC';
  const rules = schedule.rules || [];
  const overridesByDate = new Map();
  for (const ov of schedule.overrides || []) {
    overridesByDate.set(ov.date, ov);
  }

  const windows = [];
  let day = DateTime.fromMillis(fromMs, { zone: tz }).startOf('day');
  const lastDay = DateTime.fromMillis(toMs, { zone: tz }).endOf('day');

  // Bound the loop (defensive — horizons are clamped upstream, but never loop unbounded).
  let guard = 0;
  while (day <= lastDay && guard < 800) {
    guard += 1;
    const dateKey = day.toFormat('yyyy-MM-dd');
    const jsWeekday = day.weekday % 7; // luxon Mon=1..Sun=7 -> Sun=0..Sat=6
    const override = overridesByDate.get(dateKey);

    let dayWindows = [];
    if (override) {
      if (override.is_unavailable) {
        dayWindows = []; // whole day off
      } else if (override.start_time && override.end_time) {
        dayWindows = [{ start_time: override.start_time, end_time: override.end_time }];
      }
    } else {
      dayWindows = rules
        .filter((r) => Number(r.weekday) === jsWeekday)
        .map((r) => ({ start_time: r.start_time, end_time: r.end_time }));
    }

    for (const w of dayWindows) {
      const s = parseHm(w.start_time);
      const e = parseHm(w.end_time);
      const winStart = day.set({ hour: s.hour, minute: s.minute, second: 0, millisecond: 0 });
      const winEnd = day.set({ hour: e.hour, minute: e.minute, second: 0, millisecond: 0 });
      if (!winStart.isValid || !winEnd.isValid) continue;
      const startMs = Math.max(winStart.toMillis(), fromMs);
      const endMs = Math.min(winEnd.toMillis(), toMs);
      if (endMs > startMs) windows.push({ start: startMs, end: endMs });
    }
    day = day.plus({ days: 1 });
  }
  return mergeIntervals(windows);
}

// ------------------------------------------------------------
// Per-member free intervals = windows − padded busy.
// ------------------------------------------------------------

function memberFreeIntervals({ schedule, busy, eventType }, fromMs, toMs) {
  const windows = buildWeeklyWindows(schedule, fromMs, toMs);
  const bufBefore = (eventType.buffer_before_min || 0) * MIN_MS;
  const bufAfter = (eventType.buffer_after_min || 0) * MIN_MS;
  const paddedBusy = (busy || []).map((b) => ({
    start: b.start - bufAfter, // gap required AFTER the new meeting, BEFORE existing busy
    end: b.end + bufBefore, // gap required BEFORE the new meeting, AFTER existing busy
  }));
  return subtractIntervals(windows, paddedBusy);
}

// ------------------------------------------------------------
// Gridding — candidate starts aligned to slot_interval within free space, in a reference tz.
// ------------------------------------------------------------

function gridStartsInInterval(iv, intervalMin, durationMin, refTz, out) {
  const durMs = durationMin * MIN_MS;
  const stepMs = intervalMin * MIN_MS;
  // Align the first candidate to the next slot_interval boundary from midnight (refTz).
  const startDt = DateTime.fromMillis(iv.start, { zone: refTz });
  const midnight = startDt.startOf('day');
  const minutesIn = (startDt.toMillis() - midnight.toMillis()) / MIN_MS;
  const alignedMinutes = Math.ceil(minutesIn / intervalMin) * intervalMin;
  let cursor = midnight.plus({ minutes: alignedMinutes }).toMillis();
  // (midnight+alignedMinutes is >= iv.start by construction)
  let guard = 0;
  while (cursor + durMs <= iv.end && guard < 5000) {
    guard += 1;
    out.push(cursor);
    cursor += stepMs;
  }
}

// ------------------------------------------------------------
// Pure core — given each member's free intervals, produce slots.
// ------------------------------------------------------------

/**
 * @param {Object} args
 * @param {Object<string, Array<{start,end}>>} args.membersFree  free intervals per member id
 * @param {string[]} args.memberIds            all participating member ids (ordered)
 * @param {string[]} args.requiredMemberIds    required members (collective); defaults to memberIds
 * @param {string}   args.mode                 one_on_one | collective | round_robin | group
 * @param {Object}   args.eventType            { default_duration, slot_interval_min, min_notice_min, max_horizon_days }
 * @param {string}   args.refTz                timezone used to align the candidate grid
 * @param {number}   args.fromMs
 * @param {number}   args.toMs
 * @param {number}   args.nowMs
 * @returns {Array<{startMs,endMs,eligibleHosts:string[]}>}
 */
function computeSlotsCore({ membersFree, memberIds, requiredMemberIds, mode, eventType, refTz, fromMs, toMs, nowMs }) {
  const D = eventType.default_duration;
  const I = eventType.slot_interval_min;
  const durMs = D * MIN_MS;
  const minNoticeMs = (eventType.min_notice_min || 0) * MIN_MS;
  const maxHorizonMs = nowMs + (eventType.max_horizon_days || 60) * DAY_MS;
  const effectiveFrom = Math.max(fromMs, nowMs + minNoticeMs);
  const effectiveTo = Math.min(toMs, maxHorizonMs);
  if (!memberIds.length || effectiveFrom >= effectiveTo) return [];

  const required = (requiredMemberIds && requiredMemberIds.length ? requiredMemberIds : memberIds);

  let candidateSource;
  if (mode === 'collective') {
    candidateSource = intersectAll(required.map((id) => membersFree[id] || []));
  } else if (mode === 'round_robin') {
    candidateSource = mergeIntervals(memberIds.flatMap((id) => membersFree[id] || []));
  } else {
    // one_on_one / group — the owner is members[0]
    candidateSource = membersFree[memberIds[0]] || [];
  }

  const rawStarts = [];
  for (const iv of candidateSource) {
    gridStartsInInterval(iv, I, D, refTz, rawStarts);
  }

  const seen = new Set();
  const slots = [];
  for (const s of rawStarts.sort((a, b) => a - b)) {
    const e = s + durMs;
    if (s < effectiveFrom || s > effectiveTo || e > toMs) continue;
    if (seen.has(s)) continue;
    seen.add(s);

    let eligibleHosts;
    if (mode === 'round_robin') {
      eligibleHosts = memberIds.filter((id) => intervalsCover(membersFree[id] || [], s, e));
      if (!eligibleHosts.length) continue;
    } else if (mode === 'collective') {
      // by construction every required member is free across this slot
      eligibleHosts = required.slice();
    } else {
      eligibleHosts = [memberIds[0]];
    }
    slots.push({ startMs: s, endMs: e, eligibleHosts });
  }
  return slots;
}

// ------------------------------------------------------------
// DB layer — resolve members, batch-fetch busy, then call the pure core.
// ------------------------------------------------------------

async function loadScheduleForUser(userId, scheduleId) {
  let schedule = null;
  if (scheduleId) {
    const { data } = await supabaseAdmin.from('AvailabilitySchedule').select('*').eq('id', scheduleId).maybeSingle();
    schedule = data || null;
  }
  if (!schedule) {
    const { data } = await supabaseAdmin
      .from('AvailabilitySchedule')
      .select('*')
      .eq('user_id', userId)
      .eq('is_default', true)
      .maybeSingle();
    schedule = data || null;
  }
  if (!schedule) return null; // member has not set hours -> contributes nothing
  const [{ data: rules }, { data: overrides }] = await Promise.all([
    supabaseAdmin.from('AvailabilityRule').select('weekday, start_time, end_time').eq('schedule_id', schedule.id),
    supabaseAdmin.from('AvailabilityOverride').select('date, is_unavailable, start_time, end_time').eq('schedule_id', schedule.id),
  ]);
  return { id: schedule.id, timezone: schedule.timezone, rules: rules || [], overrides: overrides || [] };
}

async function resolveMembers(ownerType, ownerId, eventType, memberOverride) {
  if (ownerType === 'user') {
    const schedule = await loadScheduleForUser(ownerId, eventType.schedule_id);
    return { memberIds: [ownerId], schedules: { [ownerId]: schedule }, requiredMemberIds: [ownerId] };
  }
  // home / business — explicit members (ephemeral find-a-time) or active assignees of the event type.
  let memberIds;
  if (memberOverride && memberOverride.length) {
    memberIds = [...new Set(memberOverride)];
  } else {
    const { data: assignees } = await supabaseAdmin
      .from('EventTypeAssignee')
      .select('subject_id')
      .eq('event_type_id', eventType.id)
      .eq('is_active', true);
    memberIds = [...new Set((assignees || []).map((a) => a.subject_id))];
  }
  const schedules = {};
  await Promise.all(
    memberIds.map(async (id) => {
      schedules[id] = await loadScheduleForUser(id, null);
    })
  );
  return { memberIds, schedules, requiredMemberIds: memberIds };
}

async function fetchBusyByMember({ memberIds, ownerType, ownerId, fromMs, toMs, excludeBookingId }) {
  const busy = {};
  for (const id of memberIds) busy[id] = [];
  if (!memberIds.length) return busy;

  const fromIso = new Date(fromMs).toISOString();
  const toIso = new Date(toMs).toISOString();

  // 1. Bookings where these members are host (active statuses), overlapping the window.
  //    Expand each by the booking's OWN buffers so the engine's offered slots match the DB
  //    exclusion constraint (which guards each booking's buffer-padded occupied range).
  let bookingsQuery = supabaseAdmin
    .from('Booking')
    .select('id, host_user_id, start_at, end_at, buffer_before_min, buffer_after_min')
    .in('host_user_id', memberIds)
    .in('status', ['pending', 'confirmed'])
    .lt('start_at', toIso)
    .gt('end_at', fromIso);
  // When rescheduling, ignore the booking being moved so it doesn't block its own new time.
  if (excludeBookingId) bookingsQuery = bookingsQuery.neq('id', excludeBookingId);
  const { data: bookings } = await bookingsQuery;
  for (const b of bookings || []) {
    if (!busy[b.host_user_id]) continue;
    busy[b.host_user_id].push({
      start: Date.parse(b.start_at) - (b.buffer_before_min || 0) * MIN_MS,
      end: Date.parse(b.end_at) + (b.buffer_after_min || 0) * MIN_MS,
    });
  }

  // 2. Availability blocks. Split into two queries (avoids a fragile nested .or() and the NULL
  //    three-valued-logic trap): non-recurring rows overlapping the window + all recurring rows.
  const { data: blocksOnce } = await supabaseAdmin
    .from('AvailabilityBlock')
    .select('user_id, start_at, end_at')
    .in('user_id', memberIds)
    .is('recurrence_rule', null)
    .lt('start_at', toIso)
    .gt('end_at', fromIso);
  for (const blk of blocksOnce || []) {
    if (busy[blk.user_id]) busy[blk.user_id].push({ start: Date.parse(blk.start_at), end: Date.parse(blk.end_at) });
  }
  const { data: blocksRec } = await supabaseAdmin
    .from('AvailabilityBlock')
    .select('user_id, start_at, end_at, recurrence_rule')
    .in('user_id', memberIds)
    .not('recurrence_rule', 'is', null);
  for (const blk of blocksRec || []) {
    if (!busy[blk.user_id]) continue;
    const startMs = Date.parse(blk.start_at);
    busy[blk.user_id].push(...expandRecurrence(blk.recurrence_rule, startMs, Date.parse(blk.end_at) - startMs, fromMs, toMs));
  }

  // 3. Home shared calendar — busy for whole-home (assigned_to null) or assigned members.
  //    Non-recurring query INCLUDES open-ended events (end_at IS NULL), which a single
  //    `end_at.gt` filter would silently drop via NULL three-valued logic.
  if (ownerType === 'home') {
    const { data: eventsOnce } = await supabaseAdmin
      .from('HomeCalendarEvent')
      .select('start_at, end_at, assigned_to')
      .eq('home_id', ownerId)
      .is('recurrence_rule', null)
      .lt('start_at', toIso)
      .or(`end_at.gt.${fromIso},end_at.is.null`);
    const { data: eventsRec } = await supabaseAdmin
      .from('HomeCalendarEvent')
      .select('start_at, end_at, recurrence_rule, assigned_to')
      .eq('home_id', ownerId)
      .not('recurrence_rule', 'is', null);
    for (const ev of [...(eventsOnce || []), ...(eventsRec || [])]) {
      const startMs = Date.parse(ev.start_at);
      const endMs = ev.end_at ? Date.parse(ev.end_at) : startMs + DAY_MS; // open-ended event -> all-day (24h) busy
      const duration = endMs - startMs;
      const instances = ev.recurrence_rule
        ? expandRecurrence(ev.recurrence_rule, startMs, duration, fromMs, toMs)
        : [{ start: startMs, end: endMs }];
      const targets = ev.assigned_to && ev.assigned_to.length ? ev.assigned_to : memberIds; // null -> whole home
      for (const id of targets) {
        if (busy[id]) busy[id].push(...instances);
      }
    }
  }

  return busy;
}

/**
 * Main entry. Returns slots in the viewer's timezone plus UTC instants.
 * @returns {Promise<Array<{ start: string, end: string, startLocal: string, eligibleHosts: string[] }>>}
 */
async function computeSlots({ ownerType, ownerId, eventType, from, to, viewerTimezone, now, memberOverride, excludeBookingId }) {
  const nowMs = now ? new Date(now).getTime() : Date.now();
  const fromMs = new Date(from).getTime();
  // Hard server-side clamp on the horizon so an unauthenticated caller can't request years.
  const horizonMs = nowMs + (eventType.max_horizon_days || 60) * DAY_MS;
  const toMs = Math.min(new Date(to).getTime(), horizonMs);
  if (!Number.isFinite(fromMs) || !Number.isFinite(toMs) || fromMs >= toMs) return [];

  const { memberIds, schedules, requiredMemberIds } = await resolveMembers(ownerType, ownerId, eventType, memberOverride);
  const activeMemberIds = memberIds.filter((id) => schedules[id]); // members without a schedule contribute nothing
  if (!activeMemberIds.length) return [];

  const busy = await fetchBusyByMember({ memberIds: activeMemberIds, ownerType, ownerId, fromMs, toMs, excludeBookingId });

  const membersFree = {};
  for (const id of activeMemberIds) {
    membersFree[id] = memberFreeIntervals({ schedule: schedules[id], busy: busy[id] || [], eventType }, fromMs, toMs);
  }

  // Reference tz for the candidate grid: single-owner -> their schedule tz; multi -> first member's tz.
  const refTz = (schedules[activeMemberIds[0]] && schedules[activeMemberIds[0]].timezone) || viewerTimezone || 'UTC';

  const slots = computeSlotsCore({
    membersFree,
    memberIds: activeMemberIds,
    requiredMemberIds: requiredMemberIds.filter((id) => schedules[id]),
    mode: eventType.assignment_mode || 'one_on_one',
    eventType,
    refTz,
    fromMs,
    toMs,
    nowMs,
  });

  const vtz = viewerTimezone || refTz;
  return slots.map((s) => ({
    start: new Date(s.startMs).toISOString(),
    end: new Date(s.endMs).toISOString(),
    startLocal: DateTime.fromMillis(s.startMs, { zone: vtz }).toISO(),
    eligibleHosts: s.eligibleHosts,
  }));
}

/**
 * Is a specific [startIso, endIso] still bookable for this event type/host? Used as a
 * cheap pre-check by bookingService before relying on the DB exclusion constraint.
 */
async function isSlotAvailable({ ownerType, ownerId, eventType, startIso, endIso, hostUserId, viewerTimezone, excludeBookingId }) {
  const startMs = new Date(startIso).getTime();
  const endMs = new Date(endIso).getTime();
  const slots = await computeSlots({
    ownerType,
    ownerId,
    eventType,
    from: new Date(startMs - 1).toISOString(),
    to: new Date(endMs + 1).toISOString(),
    viewerTimezone,
    excludeBookingId,
  });
  const match = slots.find((s) => new Date(s.start).getTime() === startMs && new Date(s.end).getTime() === endMs);
  if (!match) return { available: false, eligibleHosts: [] };
  if (hostUserId && !match.eligibleHosts.includes(hostUserId)) {
    return { available: false, eligibleHosts: match.eligibleHosts };
  }
  return { available: true, eligibleHosts: match.eligibleHosts };
}

module.exports = {
  computeSlots,
  isSlotAvailable,
  // exported for unit tests:
  _internal: {
    mergeIntervals,
    subtractIntervals,
    intersectTwo,
    intersectAll,
    intervalsCover,
    expandRecurrence,
    buildWeeklyWindows,
    memberFreeIntervals,
    gridStartsInInterval,
    computeSlotsCore,
  },
};
