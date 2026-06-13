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
const availabilityService = require('../services/scheduling/availabilityService');
const bookingService = require('../services/scheduling/bookingService');
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
    price_cents: et.price_cents,
    currency: et.currency,
    requires_approval: et.requires_approval,
  };
}

// ---------- GET /book/:slug ----------

router.get('/book/:slug', previewLimiter, asyncHandler(async (req, res) => {
  const page = await loadLivePage(req.params.slug);
  if (!page) return res.status(404).json({ error: 'NOT_FOUND', message: 'This booking page is not available.' });
  // Listed page shows public, active event types. Secret ones are reachable only by direct slug.
  const { data: eventTypes } = await supabaseAdmin
    .from('EventType')
    .select('*')
    .eq('page_id', page.id)
    .eq('is_active', true)
    .eq('visibility', 'public')
    .order('sort_order');
  res.json({ page: publicPageView(page), eventTypes: (eventTypes || []).map(publicEventTypeView) });
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

router.post('/book/:slug/:eventTypeSlug', bookingWriteLimiter, optionalAuth, validate(createSchema), asyncHandler(async (req, res) => {
  const { page, eventType } = await loadPageEventType(req.params.slug, req.params.eventTypeSlug);
  if (!page || !eventType) return res.status(404).json({ error: 'NOT_FOUND' });

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
      },
      manageToken: result.manageToken,
      clientSecret: result.clientSecret || null,
    });
  } catch (err) {
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
    },
    eventType: eventType ? publicEventTypeView(eventType) : null,
    page: page ? publicPageView(page) : null,
  });
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

module.exports = router;
