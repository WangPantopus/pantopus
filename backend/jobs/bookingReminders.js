// ============================================================
// JOB: Calendarly booking reminders — runs every 15 minutes (registered in jobs/index.js).
// Sends T-24h and T-1h reminders for confirmed bookings to host + invitee (app or email),
// deduped via BookingReminderLog UNIQUE(booking_id, kind). Cron scaffolding mirrors
// jobs/supportTrainReminders.js; the worker body is booking-specific.
// ============================================================

const supabaseAdmin = require('../config/supabaseAdmin');
const logger = require('../utils/logger');
const notify = require('../services/scheduling/bookingNotifyService');

const MIN = 60 * 1000;

// Reminder windows (wider than the 15-min cron so every booking is caught; the unique log dedupes).
const WINDOWS = [
  { kind: 'reminder_24h', minBefore: 24 * 60 - 8, maxBefore: 24 * 60 + 8 }, // ~24h out
  { kind: 'reminder_1h', minBefore: 60 - 8, maxBefore: 60 + 8 }, // ~1h out
];

async function alreadySent(bookingId, kind) {
  const { data } = await supabaseAdmin
    .from('BookingReminderLog')
    .select('id')
    .eq('booking_id', bookingId)
    .eq('kind', kind)
    .maybeSingle();
  return !!data;
}

async function logSent(bookingId, kind) {
  // Insert-first dedupe: the UNIQUE(booking_id, kind) index makes a concurrent duplicate fail.
  const { error } = await supabaseAdmin.from('BookingReminderLog').insert({ booking_id: bookingId, kind });
  return !error; // false if a concurrent worker already logged it
}

async function runBookingReminders() {
  const now = Date.now();
  // Fetch confirmed bookings starting within the next ~24h15m (covers both windows).
  const fromIso = new Date(now + (60 - 10) * MIN).toISOString();
  const toIso = new Date(now + (24 * 60 + 10) * MIN).toISOString();

  const { data: bookings, error } = await supabaseAdmin
    .from('Booking')
    .select('*')
    .eq('status', 'confirmed')
    .gte('start_at', fromIso)
    .lte('start_at', toIso);

  if (error) {
    logger.error('[bookingReminders] query failed', { error: error.message });
    return;
  }
  if (!bookings || !bookings.length) return;

  // Cache event types + pages to avoid refetching per booking.
  const etCache = new Map();
  const pageCache = new Map();
  let sent = 0;

  for (const booking of bookings) {
    const minutesUntil = (Date.parse(booking.start_at) - now) / MIN;
    for (const w of WINDOWS) {
      if (minutesUntil < w.minBefore || minutesUntil > w.maxBefore) continue;
      if (await alreadySent(booking.id, w.kind)) continue;
      // Reserve the log row first so a retry/concurrent tick won't double-send.
      if (!(await logSent(booking.id, w.kind))) continue;

      try {
        if (!etCache.has(booking.event_type_id)) {
          const { data: et } = await supabaseAdmin.from('EventType').select('*').eq('id', booking.event_type_id).maybeSingle();
          etCache.set(booking.event_type_id, et || null);
        }
        if (booking.page_id && !pageCache.has(booking.page_id)) {
          const { data: page } = await supabaseAdmin.from('BookingPage').select('*').eq('id', booking.page_id).maybeSingle();
          pageCache.set(booking.page_id, page || null);
        }
        await notify.sendBookingReminder({
          booking,
          eventType: etCache.get(booking.event_type_id),
          page: booking.page_id ? pageCache.get(booking.page_id) : null,
          kind: w.kind,
        });
        sent += 1;
      } catch (err) {
        logger.error('[bookingReminders] send failed', { bookingId: booking.id, kind: w.kind, error: err.message });
      }
    }
  }

  if (sent > 0) logger.info('[bookingReminders] reminders sent', { count: sent });
}

module.exports = { runBookingReminders };
