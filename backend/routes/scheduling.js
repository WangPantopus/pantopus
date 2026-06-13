// ============================================================
// Calendarly — host-authed scheduling APIs.
// Mounted at BOTH /api/scheduling (personal/business via owner_type) and
// /api/homes/:id/scheduling (home via :id; router uses mergeParams). Availability schedules are
// always personal (req.user) — the source of truth that home/business compose.
// ============================================================

const express = require('express');
const Joi = require('joi');

const router = express.Router({ mergeParams: true });
const supabaseAdmin = require('../config/supabaseAdmin');
const verifyToken = require('../middleware/verifyToken');
const validate = require('../middleware/validate');
const { asyncHandler } = require('../errorHandler');
const logger = require('../utils/logger');
const availabilityService = require('../services/scheduling/availabilityService');
const bookingService = require('../services/scheduling/bookingService');
const { resolveOwner, assertCanManageOwner, ownerColumns, normalizeEmail } = require('../services/scheduling/schedulingShared');

router.use(verifyToken);

// ---------- owner context middleware ----------
function withOwner(level) {
  return asyncHandler(async (req, _res, next) => {
    const { ownerType, ownerId } = resolveOwner(req);
    await assertCanManageOwner(ownerType, ownerId, req.user.id, level);
    req.scheduling = { ownerType, ownerId, oc: ownerColumns(ownerType, ownerId) };
    next();
  });
}

// ---------- helpers ----------
async function ensureDefaultSchedule(userId) {
  const { data: existing } = await supabaseAdmin
    .from('AvailabilitySchedule')
    .select('id')
    .eq('user_id', userId)
    .eq('is_default', true)
    .maybeSingle();
  if (existing) return existing.id;
  const { data: sched } = await supabaseAdmin
    .from('AvailabilitySchedule')
    .insert({ user_id: userId, name: 'Working hours', timezone: 'America/New_York', is_default: true })
    .select('id')
    .single();
  await supabaseAdmin
    .from('AvailabilityRule')
    .insert([1, 2, 3, 4, 5].map((wd) => ({ schedule_id: sched.id, weekday: wd, start_time: '09:00:00', end_time: '17:00:00' })));
  return sched.id;
}

async function ensurePage({ ownerType, ownerId, oc }, userId) {
  const { data: page } = await supabaseAdmin
    .from('BookingPage')
    .select('*')
    .eq('owner_type', ownerType)
    .eq('owner_id', ownerId)
    .maybeSingle();
  if (page) return page;
  const baseSlug = `${ownerType}-${String(ownerId).slice(0, 8)}`;
  const { data: created, error } = await supabaseAdmin
    .from('BookingPage')
    .insert({ ...oc, slug: baseSlug, is_live: false, created_by: userId })
    .select('*')
    .single();
  if (error) {
    // Slug collision on the auto base — append a suffix.
    const { data: retry } = await supabaseAdmin
      .from('BookingPage')
      .insert({ ...oc, slug: `${baseSlug}-${Date.now().toString(36)}`, is_live: false, created_by: userId })
      .select('*')
      .single();
    return retry;
  }
  return created;
}

function uniqueViolation(error) {
  return error && (error.code === '23505' || /duplicate key|unique/i.test(error.message || ''));
}

// ============================================================
// BOOKING PAGE
// ============================================================

router.get('/booking-page', withOwner('view'), asyncHandler(async (req, res) => {
  const page = await ensurePage(req.scheduling, req.user.id);
  res.json({ page });
}));

const pageUpdateSchema = Joi.object({
  owner_type: Joi.string().valid('user', 'home', 'business'),
  owner_id: Joi.string(),
  title: Joi.string().allow('', null).max(200),
  tagline: Joi.string().allow('', null).max(300),
  avatar_url: Joi.string().allow('', null).max(1000),
  intro: Joi.string().allow('', null).max(2000),
  confirmation_message: Joi.string().allow('', null).max(2000),
  timezone: Joi.string().max(64),
  is_live: Joi.boolean(),
  visibility: Joi.string().valid('listed', 'unlisted'),
  branding: Joi.object().unknown(true),
});

router.put('/booking-page', withOwner('edit'), validate(pageUpdateSchema), asyncHandler(async (req, res) => {
  const page = await ensurePage(req.scheduling, req.user.id);
  const patch = { ...req.body, updated_at: new Date().toISOString() };
  delete patch.owner_type;
  delete patch.owner_id;
  const { data, error } = await supabaseAdmin.from('BookingPage').update(patch).eq('id', page.id).select('*').single();
  if (error) throw error;
  res.json({ page: data });
}));

const slugSchema = Joi.object({
  owner_type: Joi.string().valid('user', 'home', 'business'),
  owner_id: Joi.string(),
  slug: Joi.string().trim().lowercase().pattern(/^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$/).required(),
});

router.put('/booking-page/slug', withOwner('edit'), validate(slugSchema), asyncHandler(async (req, res) => {
  const page = await ensurePage(req.scheduling, req.user.id);
  const { data, error } = await supabaseAdmin
    .from('BookingPage')
    .update({ slug: req.body.slug, updated_at: new Date().toISOString() })
    .eq('id', page.id)
    .select('*')
    .single();
  if (uniqueViolation(error)) return res.status(409).json({ error: 'SLUG_TAKEN', message: 'That link is already taken.' });
  if (error) throw error;
  res.json({ page: data });
}));

// ============================================================
// EVENT TYPES
// ============================================================

router.get('/event-types', withOwner('view'), asyncHandler(async (req, res) => {
  const { ownerType, ownerId } = req.scheduling;
  const { data, error } = await supabaseAdmin
    .from('EventType')
    .select('*')
    .eq('owner_type', ownerType)
    .eq('owner_id', ownerId)
    .order('sort_order', { ascending: true })
    .order('created_at', { ascending: true });
  if (error) throw error;
  res.json({ eventTypes: data || [] });
}));

const eventTypeSchema = Joi.object({
  owner_type: Joi.string().valid('user', 'home', 'business'),
  owner_id: Joi.string(),
  name: Joi.string().trim().min(1).max(200).required(),
  slug: Joi.string().trim().lowercase().pattern(/^[a-z0-9][a-z0-9-]{0,60}$/).required(),
  description: Joi.string().allow('', null).max(2000),
  color: Joi.string().allow('', null).max(20),
  durations: Joi.array().items(Joi.number().integer().min(5).max(1440)).min(1).default([30]),
  default_duration: Joi.number().integer().min(5).max(1440).default(30),
  location_mode: Joi.string().valid('video', 'phone', 'in_person', 'custom', 'ask').default('video'),
  location_detail: Joi.string().allow('', null).max(500),
  assignment_mode: Joi.string().valid('one_on_one', 'collective', 'round_robin', 'group').default('one_on_one'),
  requires_approval: Joi.boolean().default(false),
  visibility: Joi.string().valid('public', 'secret').default('public'),
  buffer_before_min: Joi.number().integer().min(0).max(720).default(0),
  buffer_after_min: Joi.number().integer().min(0).max(720).default(0),
  min_notice_min: Joi.number().integer().min(0).max(525600).default(0),
  max_horizon_days: Joi.number().integer().min(1).max(730).default(60),
  slot_interval_min: Joi.number().integer().min(5).max(240).default(15),
  daily_cap: Joi.number().integer().min(0).allow(null),
  per_booker_cap: Joi.number().integer().min(0).allow(null),
  seat_cap: Joi.number().integer().min(1).max(1000).default(1),
  price_cents: Joi.number().integer().min(0).default(0),
  currency: Joi.string().length(3).uppercase().default('USD'),
  deposit_cents: Joi.number().integer().min(0).default(0),
  deposit_refundable: Joi.boolean().default(true),
  cancellation_window_min: Joi.number().integer().min(0).default(0),
  reschedule_cutoff_min: Joi.number().integer().min(0).default(0),
  no_show_fee_cents: Joi.number().integer().min(0).default(0),
  refund_policy: Joi.string().valid('full', 'partial', 'none', 'deposit_only').default('full'),
  allow_invitee_cancel: Joi.boolean().default(true),
  allow_invitee_reschedule: Joi.boolean().default(true),
  schedule_id: Joi.string().uuid().allow(null),
  is_active: Joi.boolean().default(true),
  sort_order: Joi.number().integer().default(0),
});

router.post('/event-types', withOwner('edit'), validate(eventTypeSchema), asyncHandler(async (req, res) => {
  const { ownerType, oc } = req.scheduling;
  const page = await ensurePage(req.scheduling, req.user.id);
  const body = { ...req.body };
  delete body.owner_type;
  delete body.owner_id;

  // Ensure default_duration is one of durations.
  if (!body.durations.includes(body.default_duration)) body.durations.unshift(body.default_duration);

  // Personal event types attach to the owner's default schedule unless one is provided.
  if (ownerType === 'user' && !body.schedule_id) {
    body.schedule_id = await ensureDefaultSchedule(req.user.id);
  }

  const { data, error } = await supabaseAdmin
    .from('EventType')
    .insert({ ...oc, page_id: page.id, ...body })
    .select('*')
    .single();
  if (uniqueViolation(error)) return res.status(409).json({ error: 'SLUG_TAKEN', message: 'An event type with that link already exists.' });
  if (error) throw error;
  res.status(201).json({ eventType: data });
}));

async function loadOwnedEventType(req) {
  const { data } = await supabaseAdmin.from('EventType').select('*').eq('id', req.params.id).maybeSingle();
  if (!data) {
    const err = new Error('Event type not found.');
    err.statusCode = 404;
    throw err;
  }
  await assertCanManageOwner(data.owner_type, data.owner_id, req.user.id, 'view');
  return data;
}

// NB: these :id routes live under a distinct prefix to avoid clashing with /homes/:id mounts —
// they are matched relative to this router's mount point.
router.get('/event-types/:id', asyncHandler(async (req, res) => {
  const et = await loadOwnedEventType(req);
  const [{ data: assignees }, { data: questions }] = await Promise.all([
    supabaseAdmin.from('EventTypeAssignee').select('*').eq('event_type_id', et.id),
    supabaseAdmin.from('EventTypeQuestion').select('*').eq('event_type_id', et.id).order('sort_order'),
  ]);
  res.json({ eventType: et, assignees: assignees || [], questions: questions || [] });
}));

// Partial-update schema: every field optional, NO defaults (so omitted fields are left untouched).
const eventTypePatchSchema = Joi.object({
  owner_type: Joi.string().valid('user', 'home', 'business'),
  owner_id: Joi.string(),
  name: Joi.string().trim().min(1).max(200),
  slug: Joi.string().trim().lowercase().pattern(/^[a-z0-9][a-z0-9-]{0,60}$/),
  description: Joi.string().allow('', null).max(2000),
  color: Joi.string().allow('', null).max(20),
  durations: Joi.array().items(Joi.number().integer().min(5).max(1440)).min(1),
  default_duration: Joi.number().integer().min(5).max(1440),
  location_mode: Joi.string().valid('video', 'phone', 'in_person', 'custom', 'ask'),
  location_detail: Joi.string().allow('', null).max(500),
  assignment_mode: Joi.string().valid('one_on_one', 'collective', 'round_robin', 'group'),
  requires_approval: Joi.boolean(),
  visibility: Joi.string().valid('public', 'secret'),
  buffer_before_min: Joi.number().integer().min(0).max(720),
  buffer_after_min: Joi.number().integer().min(0).max(720),
  min_notice_min: Joi.number().integer().min(0).max(525600),
  max_horizon_days: Joi.number().integer().min(1).max(730),
  slot_interval_min: Joi.number().integer().min(5).max(240),
  daily_cap: Joi.number().integer().min(0).allow(null),
  per_booker_cap: Joi.number().integer().min(0).allow(null),
  seat_cap: Joi.number().integer().min(1).max(1000),
  price_cents: Joi.number().integer().min(0),
  currency: Joi.string().length(3).uppercase(),
  deposit_cents: Joi.number().integer().min(0),
  deposit_refundable: Joi.boolean(),
  cancellation_window_min: Joi.number().integer().min(0),
  reschedule_cutoff_min: Joi.number().integer().min(0),
  no_show_fee_cents: Joi.number().integer().min(0),
  refund_policy: Joi.string().valid('full', 'partial', 'none', 'deposit_only'),
  allow_invitee_cancel: Joi.boolean(),
  allow_invitee_reschedule: Joi.boolean(),
  schedule_id: Joi.string().uuid().allow(null),
  is_active: Joi.boolean(),
  sort_order: Joi.number().integer(),
}).min(1);

router.put('/event-types/:id', validate(eventTypePatchSchema), asyncHandler(async (req, res) => {
  const et = await loadOwnedEventType(req);
  await assertCanManageOwner(et.owner_type, et.owner_id, req.user.id, 'edit');
  const body = { ...req.body };
  delete body.owner_type;
  delete body.owner_id;
  if (body.durations && body.default_duration && !body.durations.includes(body.default_duration)) {
    body.durations.unshift(body.default_duration);
  }
  const { data, error } = await supabaseAdmin
    .from('EventType')
    .update({ ...body, updated_at: new Date().toISOString() })
    .eq('id', et.id)
    .select('*')
    .single();
  if (uniqueViolation(error)) return res.status(409).json({ error: 'SLUG_TAKEN' });
  if (error) throw error;
  res.json({ eventType: data });
}));

router.delete('/event-types/:id', asyncHandler(async (req, res) => {
  const et = await loadOwnedEventType(req);
  await assertCanManageOwner(et.owner_type, et.owner_id, req.user.id, 'edit');
  // Refuse to delete with active upcoming bookings (preserve history / avoid orphaning).
  const { count } = await supabaseAdmin
    .from('Booking')
    .select('id', { count: 'exact', head: true })
    .eq('event_type_id', et.id)
    .in('status', ['pending', 'confirmed'])
    .gte('start_at', new Date().toISOString());
  if ((count || 0) > 0) {
    return res.status(409).json({ error: 'HAS_UPCOMING_BOOKINGS', message: 'Cancel upcoming bookings before deleting, or deactivate instead.' });
  }
  await supabaseAdmin.from('EventType').delete().eq('id', et.id);
  res.json({ ok: true });
}));

const assigneesSchema = Joi.object({
  assignees: Joi.array()
    .items(Joi.object({
      subject_id: Joi.string().uuid().required(),
      subject_type: Joi.string().valid('user', 'business_team').default('user'),
      weight: Joi.number().integer().min(0).default(1),
      priority: Joi.number().integer().default(0),
      is_active: Joi.boolean().default(true),
    }))
    .required(),
});

router.put('/event-types/:id/assignees', validate(assigneesSchema), asyncHandler(async (req, res) => {
  const et = await loadOwnedEventType(req);
  await assertCanManageOwner(et.owner_type, et.owner_id, req.user.id, 'edit');
  // Replace the assignee set.
  await supabaseAdmin.from('EventTypeAssignee').delete().eq('event_type_id', et.id);
  const rows = req.body.assignees.map((a) => ({ ...a, event_type_id: et.id }));
  const { data, error } = await supabaseAdmin.from('EventTypeAssignee').insert(rows).select('*');
  if (error) throw error;
  res.json({ assignees: data });
}));

const questionsSchema = Joi.object({
  questions: Joi.array()
    .items(Joi.object({
      label: Joi.string().trim().min(1).max(300).required(),
      field_type: Joi.string().valid('text', 'textarea', 'select', 'multiselect', 'checkbox', 'phone').default('text'),
      options: Joi.array().items(Joi.string()).default([]),
      required: Joi.boolean().default(false),
      sort_order: Joi.number().integer().default(0),
    }))
    .required(),
});

router.put('/event-types/:id/questions', validate(questionsSchema), asyncHandler(async (req, res) => {
  const et = await loadOwnedEventType(req);
  await assertCanManageOwner(et.owner_type, et.owner_id, req.user.id, 'edit');
  await supabaseAdmin.from('EventTypeQuestion').delete().eq('event_type_id', et.id);
  const rows = req.body.questions.map((q, i) => ({ ...q, options: q.options || [], event_type_id: et.id, sort_order: q.sort_order || i }));
  const { data, error } = await supabaseAdmin.from('EventTypeQuestion').insert(rows).select('*');
  if (error) throw error;
  res.json({ questions: data });
}));

// ============================================================
// AVAILABILITY (always personal — req.user)
// ============================================================

router.get('/availability', asyncHandler(async (req, res) => {
  await ensureDefaultSchedule(req.user.id);
  const { data: schedules } = await supabaseAdmin
    .from('AvailabilitySchedule')
    .select('*')
    .eq('user_id', req.user.id)
    .order('is_default', { ascending: false });
  const ids = (schedules || []).map((s) => s.id);
  let rules = [];
  let overrides = [];
  if (ids.length) {
    [{ data: rules }, { data: overrides }] = await Promise.all([
      supabaseAdmin.from('AvailabilityRule').select('*').in('schedule_id', ids),
      supabaseAdmin.from('AvailabilityOverride').select('*').in('schedule_id', ids),
    ]);
  }
  res.json({ schedules: schedules || [], rules: rules || [], overrides: overrides || [] });
}));

const scheduleSchema = Joi.object({
  name: Joi.string().trim().min(1).max(120).default('Working hours'),
  timezone: Joi.string().max(64).required(),
  is_default: Joi.boolean().default(false),
});

router.post('/availability', validate(scheduleSchema), asyncHandler(async (req, res) => {
  if (req.body.is_default) {
    await supabaseAdmin.from('AvailabilitySchedule').update({ is_default: false }).eq('user_id', req.user.id).eq('is_default', true);
  }
  const { data, error } = await supabaseAdmin
    .from('AvailabilitySchedule')
    .insert({ ...req.body, user_id: req.user.id })
    .select('*')
    .single();
  if (error) throw error;
  res.status(201).json({ schedule: data });
}));

async function loadOwnedSchedule(req) {
  const { data } = await supabaseAdmin.from('AvailabilitySchedule').select('*').eq('id', req.params.id).maybeSingle();
  if (!data || data.user_id !== req.user.id) {
    const err = new Error('Schedule not found.');
    err.statusCode = 404;
    throw err;
  }
  return data;
}

const schedulePatchSchema = Joi.object({
  name: Joi.string().trim().min(1).max(120),
  timezone: Joi.string().max(64),
  is_default: Joi.boolean(),
}).min(1);

router.put('/availability/:id', validate(schedulePatchSchema), asyncHandler(async (req, res) => {
  const sched = await loadOwnedSchedule(req);
  if (req.body.is_default) {
    await supabaseAdmin.from('AvailabilitySchedule').update({ is_default: false }).eq('user_id', req.user.id).eq('is_default', true);
  }
  const { data, error } = await supabaseAdmin
    .from('AvailabilitySchedule')
    .update({ ...req.body, updated_at: new Date().toISOString() })
    .eq('id', sched.id)
    .select('*')
    .single();
  if (error) throw error;
  res.json({ schedule: data });
}));

router.delete('/availability/:id', asyncHandler(async (req, res) => {
  const sched = await loadOwnedSchedule(req);
  if (sched.is_default) return res.status(409).json({ error: 'CANNOT_DELETE_DEFAULT', message: 'Set another schedule as default first.' });
  await supabaseAdmin.from('AvailabilitySchedule').delete().eq('id', sched.id);
  res.json({ ok: true });
}));

const rulesSchema = Joi.object({
  rules: Joi.array()
    .items(Joi.object({
      weekday: Joi.number().integer().min(0).max(6).required(),
      start_time: Joi.string().pattern(/^\d{2}:\d{2}(:\d{2})?$/).required(),
      end_time: Joi.string().pattern(/^\d{2}:\d{2}(:\d{2})?$/).required(),
    }))
    .required(),
});

router.put('/availability/:id/rules', validate(rulesSchema), asyncHandler(async (req, res) => {
  const sched = await loadOwnedSchedule(req);
  await supabaseAdmin.from('AvailabilityRule').delete().eq('schedule_id', sched.id);
  const rows = req.body.rules.map((r) => ({ ...r, schedule_id: sched.id }));
  const { data, error } = await supabaseAdmin.from('AvailabilityRule').insert(rows).select('*');
  if (error) throw error;
  res.json({ rules: data });
}));

const overridesSchema = Joi.object({
  overrides: Joi.array()
    .items(Joi.object({
      date: Joi.string().pattern(/^\d{4}-\d{2}-\d{2}$/).required(),
      is_unavailable: Joi.boolean().default(false),
      start_time: Joi.string().pattern(/^\d{2}:\d{2}(:\d{2})?$/).allow(null),
      end_time: Joi.string().pattern(/^\d{2}:\d{2}(:\d{2})?$/).allow(null),
    }))
    .required(),
});

router.put('/availability/:id/overrides', validate(overridesSchema), asyncHandler(async (req, res) => {
  const sched = await loadOwnedSchedule(req);
  await supabaseAdmin.from('AvailabilityOverride').delete().eq('schedule_id', sched.id);
  const rows = req.body.overrides.map((o) => ({ ...o, schedule_id: sched.id }));
  const { data, error } = await supabaseAdmin.from('AvailabilityOverride').insert(rows).select('*');
  if (error) throw error;
  res.json({ overrides: data });
}));

const blockSchema = Joi.object({
  title: Joi.string().allow('', null).max(200),
  start_at: Joi.string().isoDate().required(),
  end_at: Joi.string().isoDate().required(),
  recurrence_rule: Joi.string().allow('', null).max(500),
});

router.post('/availability/blocks', validate(blockSchema), asyncHandler(async (req, res) => {
  const { data, error } = await supabaseAdmin
    .from('AvailabilityBlock')
    .insert({ ...req.body, user_id: req.user.id })
    .select('*')
    .single();
  if (error) throw error;
  res.status(201).json({ block: data });
}));

router.delete('/availability/blocks/:blockId', asyncHandler(async (req, res) => {
  const { data } = await supabaseAdmin.from('AvailabilityBlock').select('user_id').eq('id', req.params.blockId).maybeSingle();
  if (!data || data.user_id !== req.user.id) return res.status(404).json({ error: 'NOT_FOUND' });
  await supabaseAdmin.from('AvailabilityBlock').delete().eq('id', req.params.blockId);
  res.json({ ok: true });
}));

// ============================================================
// BOOKINGS — inbox + detail + lifecycle
// ============================================================

router.get('/bookings', withOwner('view'), asyncHandler(async (req, res) => {
  const { ownerType, ownerId } = req.scheduling;
  let q = supabaseAdmin.from('Booking').select('*').eq('owner_type', ownerType).eq('owner_id', ownerId);
  const status = req.query.status;
  const nowIso = new Date().toISOString();
  if (status === 'upcoming') q = q.in('status', ['confirmed']).gte('start_at', nowIso).order('start_at', { ascending: true });
  else if (status === 'pending') q = q.eq('status', 'pending').order('start_at', { ascending: true });
  else if (status === 'past') q = q.in('status', ['confirmed', 'completed', 'no_show']).lt('start_at', nowIso).order('start_at', { ascending: false });
  else if (status === 'cancelled') q = q.in('status', ['cancelled', 'declined']).order('start_at', { ascending: false });
  else q = q.order('start_at', { ascending: false });
  const { data, error } = await q.limit(200);
  if (error) throw error;
  res.json({ bookings: data || [] });
}));

async function loadOwnedBooking(req) {
  const { data } = await supabaseAdmin.from('Booking').select('*').eq('id', req.params.id).maybeSingle();
  if (!data) {
    const err = new Error('Booking not found.');
    err.statusCode = 404;
    throw err;
  }
  await assertCanManageOwner(data.owner_type, data.owner_id, req.user.id, 'view');
  return data;
}

router.get('/bookings/:id', asyncHandler(async (req, res) => {
  const booking = await loadOwnedBooking(req);
  const [{ data: attendees }, { data: et }] = await Promise.all([
    supabaseAdmin.from('BookingAttendee').select('*').eq('booking_id', booking.id),
    supabaseAdmin.from('EventType').select('id, name, location_mode').eq('id', booking.event_type_id).maybeSingle(),
  ]);
  res.json({ booking, attendees: attendees || [], eventType: et || null });
}));

const manualBookingSchema = Joi.object({
  owner_type: Joi.string().valid('user', 'home', 'business'),
  owner_id: Joi.string(),
  event_type_id: Joi.string().uuid().required(),
  start_at: Joi.string().isoDate().required(),
  duration_min: Joi.number().integer().min(5).max(1440),
  invitee_name: Joi.string().trim().max(200).allow('', null),
  invitee_email: Joi.string().email().max(320).allow('', null),
  invitee_phone: Joi.string().max(40).allow('', null),
  invitee_timezone: Joi.string().max(64).allow('', null),
  intake_answers: Joi.object().unknown(true).default({}),
});

router.post('/bookings', withOwner('edit'), validate(manualBookingSchema), asyncHandler(async (req, res) => {
  const et = await bookingService.getEventTypeById(req.body.event_type_id);
  if (!et) return res.status(404).json({ error: 'EVENT_TYPE_NOT_FOUND' });
  if (et.owner_type !== req.scheduling.ownerType || et.owner_id !== req.scheduling.ownerId) {
    return res.status(403).json({ error: 'OWNER_MISMATCH' });
  }
  try {
    const result = await bookingService.createBooking({
      eventType: et,
      startIso: req.body.start_at,
      durationMin: req.body.duration_min,
      invitee: {
        name: req.body.invitee_name,
        email: req.body.invitee_email ? normalizeEmail(req.body.invitee_email) : null,
        phone: req.body.invitee_phone,
        timezone: req.body.invitee_timezone,
      },
      intakeAnswers: req.body.intake_answers,
      createdVia: 'manual',
      actorUserId: req.user.id,
    });
    res.status(201).json(result);
  } catch (err) {
    if (err.statusCode) return res.status(err.statusCode).json({ error: err.code || 'ERROR', message: err.message });
    throw err;
  }
}));

const actionSchema = Joi.object({
  reason: Joi.string().max(500).allow('', null),
  start_at: Joi.string().isoDate(),
  host_user_id: Joi.string().uuid(),
}).unknown(true);

async function lifecycleHandler(req, res, fn) {
  const booking = await loadOwnedBooking(req);
  await assertCanManageOwner(booking.owner_type, booking.owner_id, req.user.id, 'edit');
  try {
    const updated = await fn(booking);
    res.json({ booking: updated });
  } catch (err) {
    if (err.statusCode) return res.status(err.statusCode).json({ error: err.code || 'ERROR', message: err.message });
    throw err;
  }
}

router.post('/bookings/:id/approve', asyncHandler((req, res) => lifecycleHandler(req, res, (b) => bookingService.approveBooking(b.id, req.user.id))));
router.post('/bookings/:id/decline', validate(actionSchema), asyncHandler((req, res) => lifecycleHandler(req, res, (b) => bookingService.declineBooking(b.id, req.user.id, req.body.reason))));
router.post('/bookings/:id/cancel', validate(actionSchema), asyncHandler((req, res) => lifecycleHandler(req, res, (b) => bookingService.cancelBooking(b.id, req.user.id, req.body.reason, 'host'))));
router.post('/bookings/:id/reschedule', validate(actionSchema), asyncHandler((req, res) => lifecycleHandler(req, res, (b) => bookingService.rescheduleBooking(b.id, req.user.id, req.body.start_at, 'host', req.body.host_user_id))));
router.post('/bookings/:id/no-show', asyncHandler((req, res) => lifecycleHandler(req, res, (b) => bookingService.markNoShow(b.id, req.user.id))));
router.post('/bookings/:id/reassign', validate(actionSchema), asyncHandler((req, res) => lifecycleHandler(req, res, (b) => bookingService.reassignBooking(b.id, req.user.id, req.body.host_user_id))));

// ============================================================
// HOME — find-a-time, who's-free, resources (home owner_type only)
// ============================================================

function requireHome(req) {
  if (req.scheduling.ownerType !== 'home') {
    const err = new Error('This endpoint is only available for the home pillar.');
    err.statusCode = 400;
    throw err;
  }
}

const findATimeSchema = Joi.object({
  owner_type: Joi.string().valid('home'),
  owner_id: Joi.string(),
  member_ids: Joi.array().items(Joi.string().uuid()).min(1).required(),
  mode: Joi.string().valid('collective', 'round_robin').default('collective'),
  duration_min: Joi.number().integer().min(5).max(1440).default(30),
  from: Joi.string().isoDate().required(),
  to: Joi.string().isoDate().required(),
  slot_interval_min: Joi.number().integer().min(5).max(240).default(30),
  timezone: Joi.string().max(64),
});

router.post('/find-a-time', withOwner('view'), validate(findATimeSchema), asyncHandler(async (req, res) => {
  requireHome(req);
  const { ownerType, ownerId } = req.scheduling;
  // Synthesize an ephemeral event type for the compute call.
  const pseudoEventType = {
    id: null,
    owner_type: ownerType,
    owner_id: ownerId,
    assignment_mode: req.body.mode,
    default_duration: req.body.duration_min,
    slot_interval_min: req.body.slot_interval_min,
    buffer_before_min: 0,
    buffer_after_min: 0,
    min_notice_min: 0,
    max_horizon_days: 365,
    schedule_id: null,
  };
  const slots = await availabilityService.computeSlots({
    ownerType,
    ownerId,
    eventType: pseudoEventType,
    from: req.body.from,
    to: req.body.to,
    viewerTimezone: req.body.timezone,
    memberOverride: req.body.member_ids,
  });
  res.json({ slots });
}));

router.get('/whos-free', withOwner('view'), asyncHandler(async (req, res) => {
  requireHome(req);
  const { ownerId } = req.scheduling;
  const from = req.query.from;
  const to = req.query.to;
  if (!from || !to) return res.status(400).json({ error: 'MISSING_RANGE', message: 'from and to are required.' });
  // Active home members.
  const { data: members } = await supabaseAdmin
    .from('HomeOccupancy')
    .select('user_id')
    .eq('home_id', ownerId)
    .eq('is_active', true);
  const memberIds = [...new Set((members || []).map((m) => m.user_id))];
  const grid = {};
  for (const id of memberIds) {
    grid[id] = await availabilityService.computeSlots({
      ownerType: 'user',
      ownerId: id,
      eventType: { default_duration: 30, slot_interval_min: 30, buffer_before_min: 0, buffer_after_min: 0, min_notice_min: 0, max_horizon_days: 365, assignment_mode: 'one_on_one', schedule_id: null },
      from,
      to,
      viewerTimezone: req.query.tz,
    });
  }
  res.json({ members: memberIds, freeByMember: grid });
}));

const resourceSchema = Joi.object({
  owner_type: Joi.string().valid('home'),
  owner_id: Joi.string(),
  name: Joi.string().trim().min(1).max(200).required(),
  resource_type: Joi.string().valid('room', 'vehicle', 'tool', 'charger', 'other').default('other'),
  photo_url: Joi.string().allow('', null).max(1000),
  who_can_book: Joi.string().valid('members', 'specific', 'guests').default('members'),
  max_duration_min: Joi.number().integer().min(5).allow(null),
  buffer_min: Joi.number().integer().min(0).default(0),
  requires_approval: Joi.boolean().default(false),
  available_hours: Joi.object().unknown(true).default({}),
});

router.get('/resources', withOwner('view'), asyncHandler(async (req, res) => {
  requireHome(req);
  const { data, error } = await supabaseAdmin.from('HomeResource').select('*').eq('home_id', req.scheduling.ownerId).eq('is_active', true);
  if (error) throw error;
  res.json({ resources: data || [] });
}));

router.post('/resources', withOwner('edit'), validate(resourceSchema), asyncHandler(async (req, res) => {
  requireHome(req);
  const body = { ...req.body };
  delete body.owner_type;
  delete body.owner_id;
  const { data, error } = await supabaseAdmin
    .from('HomeResource')
    .insert({ ...body, home_id: req.scheduling.ownerId, created_by: req.user.id })
    .select('*')
    .single();
  if (error) throw error;
  res.status(201).json({ resource: data });
}));

const resourcePatchSchema = Joi.object({
  owner_type: Joi.string().valid('home'),
  owner_id: Joi.string(),
  name: Joi.string().trim().min(1).max(200),
  resource_type: Joi.string().valid('room', 'vehicle', 'tool', 'charger', 'other'),
  photo_url: Joi.string().allow('', null).max(1000),
  who_can_book: Joi.string().valid('members', 'specific', 'guests'),
  max_duration_min: Joi.number().integer().min(5).allow(null),
  buffer_min: Joi.number().integer().min(0),
  requires_approval: Joi.boolean(),
  available_hours: Joi.object().unknown(true),
}).min(1);

router.put('/resources/:rid', withOwner('edit'), validate(resourcePatchSchema), asyncHandler(async (req, res) => {
  requireHome(req);
  const body = { ...req.body };
  delete body.owner_type;
  delete body.owner_id;
  const { data, error } = await supabaseAdmin
    .from('HomeResource')
    .update({ ...body, updated_at: new Date().toISOString() })
    .eq('id', req.params.rid)
    .eq('home_id', req.scheduling.ownerId)
    .select('*')
    .single();
  if (error) throw error;
  res.json({ resource: data });
}));

router.delete('/resources/:rid', withOwner('edit'), asyncHandler(async (req, res) => {
  requireHome(req);
  await supabaseAdmin.from('HomeResource').update({ is_active: false }).eq('id', req.params.rid).eq('home_id', req.scheduling.ownerId);
  res.json({ ok: true });
}));

module.exports = router;
