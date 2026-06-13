// ============================================================
// Calendarly — public booking flow (no auth required). Mounted at /api/public.
//   GET  /book/:slug                          -> page + visible event types (is_live gate)
//   GET  /book/:slug/:eventTypeSlug/slots      -> computed free slots
//   POST /book/:slug/:eventTypeSlug            -> create a booking (write-limited, email-bound)
//   GET  /booking/:token                       -> manage view via token
//   GET  /booking/:token/ics                   -> calendar invite (.ics)
//   POST /booking/:token/reschedule | /cancel  -> invitee-side, policy-gated
//   POST /booking/:token/unsubscribe           -> suppress reminder emails for this address
//
// These are the system's first unauthenticated WRITE endpoints — modeled on the Support Trains
// guest-reserve precedent: dedicated write limiter, optionalAuth, and email-match identity binding.
// ============================================================

const express = require('express');
const Joi = require('joi');

const router = express.Router();
const supabaseAdmin = require('../config/supabaseAdmin');
const optionalAuth = require('../middleware/optionalAuth');
const validate = require('../middleware/validate');
const { previewLimiter, bookingWriteLimiter } = require('../middleware/rateLimiter');
const { asyncHandler } = require('../errorHandler');
const logger = require('../utils/logger');
const { DateTime } = require('luxon');
const availabilityService = require('../services/scheduling/availabilityService');
const bookingService = require('../services/scheduling/bookingService');
const payments = require('../services/scheduling/schedulingPaymentsService');
const { buildIcs } = require('../services/scheduling/icsService');
const { hashToken, normalizeEmail, hashEmail } = require('../services/scheduling/schedulingShared');

// ---------- page lookup helpers ----------

async function loadLivePage(slug) {
  const { data } = await supabaseAdmin
    .from('BookingPage')
    .select('*')
    .ilike('slug', slug) // lower(slug) unique index; ilike is case-insensitive exact here
    .maybeSingle();
  if (!data || !data.is_live) return null;
  return data;
}

function publicPageView(page) {
  return {
    slug: page.slug,
    title: page.title,
    tagline: page.tagline,
    avatar_url: page.avatar_url,
    intro: page.intro,
    timezone: page.timezone,
    branding: page.branding,
    owner_type: page.owner_type,
  };
}

function publicEventTypeView(et) {
  return {
    id: et.id,
    name: et.name,
    slug: et.slug,
    description: et.description,
    color: et.color,
    durations: et.durations,
    default_duration: et.default_duration,
    location_mode: et.location_mode,
    location_detail: et.location_detail,
    price_cents: et.price_cents,
    currency: et.currency,
    deposit_cents: et.deposit_cents,
    deposit_refundable: et.deposit_refundable,
    refund_policy: et.refund_policy,
    cancellation_window_min: et.cancellation_window_min,
    reschedule_cutoff_min: et.reschedule_cutoff_min,
    requires_approval: et.requires_approval,
  };
}

// Computed reschedule/cancel availability for the Manage / Policy-Blocked screens.
function bookingActionState(booking, eventType, paymentAmountTotal) {
  const policy = booking.policy_snapshot || {};
  const startMs = Date.parse(booking.start_at);
  const now = Date.now();
  const isActive = ['pending', 'confirmed'].includes(booking.status);
  const inviteeCancelAllowed = eventType ? eventType.allow_invitee_cancel !== false : true;
  const inviteeReschedAllowed = eventType ? eventType.allow_invitee_reschedule !== false : true;
  const reschedDeadlineMs = startMs - (policy.reschedule_cutoff_min || 0) * 60000;
  const state = {
    can_cancel: isActive && inviteeCancelAllowed && now < startMs,
    can_reschedule: isActive && inviteeReschedAllowed && now <= reschedDeadlineMs,
    invitee_cancel_allowed: inviteeCancelAllowed,
    invitee_reschedule_allowed: inviteeReschedAllowed,
    reschedule_deadline: Number.isFinite(reschedDeadlineMs) ? new Date(reschedDeadlineMs).toISOString() : null,
    free_cancel_until: new Date(startMs - (policy.cancellation_window_min || 0) * 60000).toISOString(),
  };
  if (paymentAmountTotal != null) {
    state.refund_estimate_cents = payments.computeRefundCents({ policy, amountTotal: paymentAmountTotal, startAtMs: startMs, nowMs: now });
  }
  return state;
}

// ---------- GET /book/:slug ----------

router.get('/book/:slug', previewLimiter, asyncHandler(async (req, res) => {
  const { data: page } = await supabaseAdmin.from('BookingPage').select('*').ilike('slug', req.params.slug).maybeSingle();
  // Distinguish unavailable (offline/not-found) from paused so the invitee sees the right state.
  if (!page || !page.is_live) {
    return res.status(404).json({ error: 'NOT_FOUND', status: 'unavailable', message: 'This booking page is not available.' });
  }
  // Listed page shows public, active event types. Secret ones are reachable only by direct slug.
  const { data: eventTypes } = await supabaseAdmin
    .from('EventType')
    .select('*')
    .eq('page_id', page.id)
    .eq('is_active', true)
    .eq('visibility', 'public')
    .order('sort_order');
  res.json({
    page: { ...publicPageView(page), cancellation_policy: page.cancellation_policy || null },
    status: page.is_paused ? 'paused' : 'active',
    eventTypes: (eventTypes || []).map(publicEventTypeView),
  });
}));

async function loadPageEventType(slug, eventTypeSlug) {
  const page = await loadLivePage(slug);
  if (!page) return { page: null, eventType: null };
  const { data: et } = await supabaseAdmin
    .from('EventType')
    .select('*')
    .eq('page_id', page.id)
    .ilike('slug', eventTypeSlug)
    .eq('is_active', true)
    .maybeSingle();
  return { page, eventType: et || null };
}

// ---------- GET /book/:slug/:eventTypeSlug/slots ----------

router.get('/book/:slug/:eventTypeSlug/slots', previewLimiter, asyncHandler(async (req, res) => {
  const { page, eventType } = await loadPageEventType(req.params.slug, req.params.eventTypeSlug);
  if (!page || !eventType) return res.status(404).json({ error: 'NOT_FOUND' });
  const from = req.query.from;
  const to = req.query.to;
  if (!from || !to) return res.status(400).json({ error: 'MISSING_RANGE', message: 'from and to are required.' });
  const tz = req.query.tz || page.timezone;
  // Paused page: no bookable times, surface the paused state instead of an empty grid.
  if (page.is_paused) {
    return res.json({ eventType: publicEventTypeView(eventType), timezone: tz, status: 'paused', slots: [] });
  }
  const slots = await availabilityService.computeSlots({
    ownerType: eventType.owner_type,
    ownerId: eventType.owner_id,
    eventType,
    from,
    to,
    viewerTimezone: tz,
  });
  // Redact host identity from the public payload (eligibility set is internal).
  res.json({
    eventType: publicEventTypeView(eventType),
    timezone: tz,
    status: 'active',
    slots: slots.map((s) => ({ start: s.start, end: s.end, startLocal: s.startLocal })),
  });
}));

// ---------- POST /book/:slug/:eventTypeSlug ----------

const createSchema = Joi.object({
  start_at: Joi.string().isoDate().required(),
  duration_min: Joi.number().integer().min(5).max(1440),
  name: Joi.string().trim().min(1).max(200).required(),
  email: Joi.string().email().max(320).required(),
  phone: Joi.string().max(40).allow('', null),
  timezone: Joi.string().max(64).allow('', null),
  answers: Joi.object().unknown(true).default({}),
});

async function nearestAlternatives(eventType, aroundIso, tz) {
  try {
    const from = new Date(Date.parse(aroundIso)).toISOString();
    const to = new Date(Date.parse(aroundIso) + 3 * 24 * 60 * 60 * 1000).toISOString();
    const slots = await availabilityService.computeSlots({ ownerType: eventType.owner_type, ownerId: eventType.owner_id, eventType, from, to, viewerTimezone: tz });
    return slots.slice(0, 4).map((s) => ({ start: s.start, end: s.end, startLocal: s.startLocal }));
  } catch (_e) {
    return [];
  }
}

// ---------- one-off / single-use links (/book/o/:token) ----------
// Registered BEFORE the generic /book/:slug/:eventTypeSlug routes so the 3-segment paths
// (/book/o/<token> vs /book/<slug>/<eventType>) don't collide.
async function loadByOneOffToken(rawToken) {
  const tokenHash = hashToken(rawToken);
  const { data: tok } = await supabaseAdmin
    .from('BookingToken')
    .select('*')
    .eq('token_hash', tokenHash)
    .eq('kind', 'one_off')
    .maybeSingle();
  if (!tok || !tok.event_type_id) return null;
  if (tok.expires_at && new Date(tok.expires_at) < new Date()) return null;
  if (tok.single_use && tok.consumed_at) return null;
  const { data: et } = await supabaseAdmin.from('EventType').select('*').eq('id', tok.event_type_id).eq('is_active', true).maybeSingle();
  if (!et) return null;
  return { tok, eventType: et };
}

router.get('/book/o/:token', previewLimiter, asyncHandler(async (req, res) => {
  const ctx = await loadByOneOffToken(req.params.token);
  if (!ctx) return res.status(404).json({ error: 'NOT_FOUND', status: 'expired', message: 'This link is invalid, already used, or expired.' });
  const { tok, eventType } = ctx;
  const tz = req.query.tz || 'UTC';
  let slots;
  if (tok.offered_slots && tok.offered_slots.length) {
    const now = Date.now();
    slots = tok.offered_slots
      .filter((s) => Date.parse(s.start) > now)
      .map((s) => ({ start: new Date(s.start).toISOString(), end: new Date(s.end).toISOString(), startLocal: DateTime.fromISO(s.start, { zone: tz }).toISO() }));
  } else {
    const from = req.query.from || new Date().toISOString();
    const to = req.query.to || new Date(Date.now() + (eventType.max_horizon_days || 60) * 24 * 60 * 60 * 1000).toISOString();
    const computed = await availabilityService.computeSlots({ ownerType: eventType.owner_type, ownerId: eventType.owner_id, eventType, from, to, viewerTimezone: tz });
    slots = computed.map((s) => ({ start: s.start, end: s.end, startLocal: s.startLocal }));
  }
  res.json({ eventType: publicEventTypeView(eventType), single_use: tok.single_use, slots });
}));

router.post('/book/o/:token', bookingWriteLimiter, optionalAuth, validate(createSchema), asyncHandler(async (req, res) => {
  const ctx = await loadByOneOffToken(req.params.token);
  if (!ctx) return res.status(404).json({ error: 'NOT_FOUND', message: 'This link is invalid, already used, or expired.' });
  const { tok, eventType } = ctx;
  if (tok.offered_slots && tok.offered_slots.length) {
    const requested = Date.parse(req.body.start_at);
    if (!tok.offered_slots.some((s) => Date.parse(s.start) === requested)) {
      return res.status(400).json({ error: 'SLOT_NOT_OFFERED', message: 'Please choose one of the offered times.' });
    }
  }
  // Claim a single-use token atomically (consumed_at IS NULL guard) before creating the booking.
  if (tok.single_use) {
    const { data: claimed } = await supabaseAdmin
      .from('BookingToken')
      .update({ consumed_at: new Date().toISOString() })
      .eq('id', tok.id)
      .is('consumed_at', null)
      .select('id');
    if (!claimed || !claimed.length) return res.status(409).json({ error: 'LINK_USED', message: 'This single-use link has already been used.' });
  }
  const inviteeEmail = normalizeEmail(req.body.email);
  const inviteeUserId = req.user && req.user.id && normalizeEmail(req.user.email) === inviteeEmail ? req.user.id : null;
  try {
    const result = await bookingService.createBooking({
      eventType,
      page: null,
      startIso: req.body.start_at,
      durationMin: req.body.duration_min,
      invitee: { user_id: inviteeUserId, name: req.body.name, email: inviteeEmail, phone: req.body.phone, timezone: req.body.timezone },
      intakeAnswers: req.body.answers,
      createdVia: 'one_off',
      actorUserId: inviteeUserId,
    });
    res.status(201).json({
      booking: { id: result.booking.id, status: result.booking.status, start_at: result.booking.start_at, end_at: result.booking.end_at },
      eventType: publicEventTypeView(eventType),
      manageToken: result.manageToken,
      clientSecret: result.clientSecret || null,
    });
  } catch (err) {
    if (tok.single_use) await supabaseAdmin.from('BookingToken').update({ consumed_at: null }).eq('id', tok.id); // release on failure
    if (err.statusCode) return res.status(err.statusCode).json({ error: err.code || 'ERROR', message: err.message });
    logger.error('[schedulingPublic] one-off booking failed', { error: err.message });
    return res.status(500).json({ error: 'INTERNAL', message: 'Could not create the booking.' });
  }
}));

router.post('/book/:slug/:eventTypeSlug', bookingWriteLimiter, optionalAuth, validate(createSchema), asyncHandler(async (req, res) => {
  const { page, eventType } = await loadPageEventType(req.params.slug, req.params.eventTypeSlug);
  if (!page || !eventType) return res.status(404).json({ error: 'NOT_FOUND' });
  if (page.is_paused) return res.status(409).json({ error: 'PAGE_PAUSED', message: 'This page is not accepting bookings right now.' });

  const inviteeEmail = normalizeEmail(req.body.email);
  // Identity binding: only attribute to a user if the signed-in user's email matches (anti-spoof).
  const inviteeUserId =
    req.user && req.user.id && normalizeEmail(req.user.email) === inviteeEmail ? req.user.id : null;

  try {
    const result = await bookingService.createBooking({
      eventType,
      page,
      startIso: req.body.start_at,
      durationMin: req.body.duration_min,
      invitee: {
        user_id: inviteeUserId,
        name: req.body.name,
        email: inviteeEmail,
        phone: req.body.phone,
        timezone: req.body.timezone || page.timezone,
      },
      intakeAnswers: req.body.answers,
      createdVia: 'public_link',
      actorUserId: inviteeUserId,
    });
    res.status(201).json({
      booking: {
        id: result.booking.id,
        status: result.booking.status,
        start_at: result.booking.start_at,
        end_at: result.booking.end_at,
        requires_approval: result.booking.status === 'pending',
        policy_snapshot: result.booking.policy_snapshot || null,
      },
      eventType: publicEventTypeView(eventType),
      page: { confirmation_message: page.confirmation_message || null, timezone: page.timezone },
      manageToken: result.manageToken,
      clientSecret: result.clientSecret || null,
    });
  } catch (err) {
    // On a slot conflict, hand back a few nearest open times for the "Slot Taken" screen.
    if (err.statusCode === 409 && ['SLOT_TAKEN', 'SLOT_UNAVAILABLE', 'SLOT_FULL'].includes(err.code)) {
      const alternatives = await nearestAlternatives(eventType, req.body.start_at, req.body.timezone || page.timezone);
      return res.status(409).json({ error: err.code, message: err.message, alternatives });
    }
    if (err.statusCode) return res.status(err.statusCode).json({ error: err.code || 'ERROR', message: err.message });
    logger.error('[schedulingPublic] create booking failed', { error: err.message });
    return res.status(500).json({ error: 'INTERNAL', message: 'Could not create the booking.' });
  }
}));

// ---------- token-based manage endpoints ----------

async function loadByManageToken(rawToken) {
  const tokenHash = hashToken(rawToken);
  const { data: tok } = await supabaseAdmin
    .from('BookingToken')
    .select('*')
    .eq('token_hash', tokenHash)
    .eq('kind', 'manage')
    .maybeSingle();
  if (!tok || !tok.booking_id) return null;
  if (tok.expires_at && new Date(tok.expires_at) < new Date()) return null;
  const ctx = await bookingService.getBookingContext(tok.booking_id);
  return ctx ? { token: tok, ...ctx } : null;
}

router.get('/booking/:token', previewLimiter, asyncHandler(async (req, res) => {
  const ctx = await loadByManageToken(req.params.token);
  if (!ctx) return res.status(404).json({ error: 'NOT_FOUND', message: 'This booking link is invalid or expired.' });
  const { booking, eventType, page } = ctx;

  // Payment summary (for the confirmed-paid receipt + refund estimate), if any.
  let payment = null;
  if (booking.payment_id) {
    const { data: p } = await supabaseAdmin
      .from('Payment')
      .select('id, amount_total, currency, payment_status, created_at')
      .eq('id', booking.payment_id)
      .maybeSingle();
    if (p) payment = { amount_total: p.amount_total, currency: p.currency, payment_status: p.payment_status, paid_at: p.created_at };
  }

  res.json({
    booking: {
      id: booking.id,
      status: booking.status,
      start_at: booking.start_at,
      end_at: booking.end_at,
      invitee_name: booking.invitee_name,
      invitee_timezone: booking.invitee_timezone,
      location_mode: booking.location_mode,
      location_detail: booking.location_detail,
      previous_start_at: booking.previous_start_at,
      cancel_reason: booking.cancel_reason,
      policy_snapshot: booking.policy_snapshot || null,
    },
    actions: bookingActionState(booking, eventType, payment ? payment.amount_total : null),
    payment,
    eventType: eventType ? publicEventTypeView(eventType) : null,
    page: page ? { ...publicPageView(page), cancellation_policy: page.cancellation_policy || null } : null,
  });
}));

// Slot grid for the invitee Reschedule flow (excludes this booking's own range).
router.get('/booking/:token/available-slots', previewLimiter, asyncHandler(async (req, res) => {
  const ctx = await loadByManageToken(req.params.token);
  if (!ctx) return res.status(404).json({ error: 'NOT_FOUND' });
  const { booking, eventType } = ctx;
  if (!req.query.from || !req.query.to || !eventType) return res.json({ slots: [] });
  const slots = await availabilityService.computeSlots({
    ownerType: booking.owner_type,
    ownerId: booking.owner_id,
    eventType,
    from: req.query.from,
    to: req.query.to,
    viewerTimezone: req.query.tz || booking.invitee_timezone,
    excludeBookingId: booking.id,
  });
  res.json({ slots: slots.map((s) => ({ start: s.start, end: s.end, startLocal: s.startLocal })) });
}));

router.get('/booking/:token/ics', previewLimiter, asyncHandler(async (req, res) => {
  const ctx = await loadByManageToken(req.params.token);
  if (!ctx) return res.status(404).json({ error: 'NOT_FOUND' });
  const { booking, eventType } = ctx;
  const cancelled = ['cancelled', 'declined'].includes(booking.status);
  const ics = buildIcs({
    uid: booking.id,
    start: booking.start_at,
    end: booking.end_at,
    summary: eventType ? eventType.name : 'Appointment',
    description: eventType ? eventType.description || '' : '',
    location: booking.location_detail || '',
    attendeeEmail: booking.invitee_email || undefined,
    method: cancelled ? 'CANCEL' : 'REQUEST',
    sequence: booking.previous_start_at ? 1 : 0,
  });
  res.setHeader('Content-Type', 'text/calendar; charset=utf-8');
  res.setHeader('Content-Disposition', 'attachment; filename="invite.ics"');
  res.send(ics);
}));

const tokenRescheduleSchema = Joi.object({ start_at: Joi.string().isoDate().required() });

router.post('/booking/:token/reschedule', bookingWriteLimiter, validate(tokenRescheduleSchema), asyncHandler(async (req, res) => {
  const ctx = await loadByManageToken(req.params.token);
  if (!ctx) return res.status(404).json({ error: 'NOT_FOUND' });
  try {
    const updated = await bookingService.rescheduleBooking(ctx.booking.id, ctx.booking.invitee_user_id, req.body.start_at, 'invitee');
    res.json({ booking: { id: updated.id, status: updated.status, start_at: updated.start_at, end_at: updated.end_at } });
  } catch (err) {
    if (err.statusCode) return res.status(err.statusCode).json({ error: err.code || 'ERROR', message: err.message });
    throw err;
  }
}));

const tokenCancelSchema = Joi.object({ reason: Joi.string().max(500).allow('', null) });

router.post('/booking/:token/cancel', bookingWriteLimiter, validate(tokenCancelSchema), asyncHandler(async (req, res) => {
  const ctx = await loadByManageToken(req.params.token);
  if (!ctx) return res.status(404).json({ error: 'NOT_FOUND' });
  try {
    const updated = await bookingService.cancelBooking(ctx.booking.id, ctx.booking.invitee_user_id, req.body.reason, 'invitee');
    res.json({ booking: { id: updated.id, status: updated.status } });
  } catch (err) {
    if (err.statusCode) return res.status(err.statusCode).json({ error: err.code || 'ERROR', message: err.message });
    throw err;
  }
}));

// One-click unsubscribe from reminder emails (transactional confirmations are still sent).
router.post('/booking/:token/unsubscribe', bookingWriteLimiter, asyncHandler(async (req, res) => {
  const ctx = await loadByManageToken(req.params.token);
  if (!ctx || !ctx.booking.invitee_email) return res.status(404).json({ error: 'NOT_FOUND' });
  const emailHash = hashEmail(ctx.booking.invitee_email);
  const { data: existing } = await supabaseAdmin.from('EmailSuppression').select('id').eq('email_hash', emailHash).maybeSingle();
  if (!existing) {
    await supabaseAdmin.from('EmailSuppression').insert({ email_hash: emailHash, reason: 'invitee_unsubscribe' });
  }
  res.json({ ok: true });
}));

// ---------- waitlist join (public) ----------
const waitlistSchema = Joi.object({
  name: Joi.string().trim().max(200).allow('', null),
  email: Joi.string().email().max(320).required(),
  desired_from: Joi.string().isoDate().allow(null),
  desired_to: Joi.string().isoDate().allow(null),
});
router.post('/book/:slug/:eventTypeSlug/waitlist', bookingWriteLimiter, optionalAuth, validate(waitlistSchema), asyncHandler(async (req, res) => {
  const { page, eventType } = await loadPageEventType(req.params.slug, req.params.eventTypeSlug);
  if (!page || !eventType) return res.status(404).json({ error: 'NOT_FOUND' });
  const email = normalizeEmail(req.body.email);
  const inviteeUserId = req.user && req.user.id && normalizeEmail(req.user.email) === email ? req.user.id : null;
  const { data, error } = await supabaseAdmin
    .from('SchedulingWaitlist')
    .insert({
      event_type_id: eventType.id,
      owner_type: eventType.owner_type,
      owner_id: eventType.owner_id,
      invitee_user_id: inviteeUserId,
      invitee_name: req.body.name || null,
      invitee_email: email,
      desired_from: req.body.desired_from || null,
      desired_to: req.body.desired_to || null,
    })
    .select('id, status')
    .single();
  if (error) throw error;
  res.status(201).json({ waitlist: data });
}));

// ---------- proposed-reschedule accept / decline (invitee, via manage token) ----------
router.post('/booking/:token/accept-reschedule', bookingWriteLimiter, asyncHandler(async (req, res) => {
  const ctx = await loadByManageToken(req.params.token);
  if (!ctx) return res.status(404).json({ error: 'NOT_FOUND' });
  try {
    const updated = await bookingService.acceptProposedReschedule(ctx.booking.id, ctx.booking.invitee_user_id);
    res.json({ booking: { id: updated.id, status: updated.status, start_at: updated.start_at, end_at: updated.end_at } });
  } catch (err) {
    if (err.statusCode) return res.status(err.statusCode).json({ error: err.code || 'ERROR', message: err.message });
    throw err;
  }
}));
router.post('/booking/:token/decline-reschedule', bookingWriteLimiter, asyncHandler(async (req, res) => {
  const ctx = await loadByManageToken(req.params.token);
  if (!ctx) return res.status(404).json({ error: 'NOT_FOUND' });
  const updated = await bookingService.declineProposedReschedule(ctx.booking.id);
  res.json({ booking: { id: updated.id, status: updated.status } });
}));

// ---------- meeting polls (public view + vote) ----------
router.get('/poll/:id', previewLimiter, asyncHandler(async (req, res) => {
  const { data: poll } = await supabaseAdmin.from('SchedulingPoll').select('id, title, description, duration_min, status, finalized_start_at').eq('id', req.params.id).maybeSingle();
  if (!poll) return res.status(404).json({ error: 'NOT_FOUND' });
  const [{ data: options }, { data: votes }] = await Promise.all([
    supabaseAdmin.from('SchedulingPollOption').select('id, start_at, end_at').eq('poll_id', poll.id).order('start_at'),
    supabaseAdmin.from('SchedulingPollVote').select('option_id, voter_name, value').eq('poll_id', poll.id),
  ]);
  res.json({ poll, options: options || [], votes: votes || [] });
}));

const pollVoteSchema = Joi.object({
  name: Joi.string().trim().max(200).allow('', null),
  email: Joi.string().email().max(320).allow('', null),
  votes: Joi.array().items(Joi.object({ option_id: Joi.string().uuid().required(), value: Joi.string().valid('yes', 'maybe', 'no').default('yes') })).min(1).required(),
});
router.post('/poll/:id/vote', bookingWriteLimiter, optionalAuth, validate(pollVoteSchema), asyncHandler(async (req, res) => {
  const { data: poll } = await supabaseAdmin.from('SchedulingPoll').select('id, status').eq('id', req.params.id).maybeSingle();
  if (!poll) return res.status(404).json({ error: 'NOT_FOUND' });
  if (poll.status !== 'open') return res.status(409).json({ error: 'POLL_CLOSED' });
  const voterKey = (req.user && req.user.id) || (req.body.email ? normalizeEmail(req.body.email) : null);
  if (!voterKey) return res.status(400).json({ error: 'VOTER_REQUIRED', message: 'Sign in or provide an email to vote.' });
  const voterName = req.body.name || null;
  // Validate options belong to this poll.
  const { data: validOpts } = await supabaseAdmin.from('SchedulingPollOption').select('id').eq('poll_id', poll.id);
  const validIds = new Set((validOpts || []).map((o) => o.id));
  for (const v of req.body.votes) {
    if (!validIds.has(v.option_id)) continue;
    // Upsert one vote per (option, voter).
    const { data: existing } = await supabaseAdmin.from('SchedulingPollVote').select('id').eq('option_id', v.option_id).eq('voter_key', voterKey).maybeSingle();
    if (existing) {
      await supabaseAdmin.from('SchedulingPollVote').update({ value: v.value, voter_name: voterName }).eq('id', existing.id);
    } else {
      await supabaseAdmin.from('SchedulingPollVote').insert({ poll_id: poll.id, option_id: v.option_id, voter_user_id: req.user ? req.user.id : null, voter_name: voterName, voter_key: voterKey, value: v.value });
    }
  }
  res.json({ ok: true });
}));

module.exports = router;
