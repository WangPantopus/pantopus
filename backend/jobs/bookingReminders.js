// ============================================================
// JOB: Calendarly booking reminders — runs every 15 minutes (registered in jobs/index.js).
// Sends reminders for confirmed bookings at each lead offset configured on the booking's page
// (BookingPage.reminder_minutes, default [1440, 60] = 1 day + 1 hour). Recipients: host (subject
// to their notify prefs) + invitee (app or email). Deduped via BookingReminderLog UNIQUE(booking_id, kind).
// ============================================================

const supabaseAdmin = require('../config/supabaseAdmin');
const logger = require('../utils/logger');
const notify = require('../services/scheduling/bookingNotifyService');

const MIN = 60 * 1000;
const DEFAULT_REMINDER_MINUTES = [1440, 60];
const SCAN_AHEAD_MIN = 7 * 24 * 60 + 15; // scan confirmed bookings starting within the next 7 days
const MAX_OFFSET_MIN = 7 * 24 * 60; // honor configured offsets up to 7 days

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
  return !error;
}

async function runBookingReminders() {
  const now = Date.now();
  const fromIso = new Date(now + MIN).toISOString();
  const toIso = new Date(now + SCAN_AHEAD_MIN * MIN).toISOString();

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

  const etCache = new Map();
  const pageCache = new Map();
  let sent = 0;

  for (const booking of bookings) {
    if (booking.page_id && !pageCache.has(booking.page_id)) {
      const { data: page } = await supabaseAdmin.from('BookingPage').select('*').eq('id', booking.page_id).maybeSingle();
      pageCache.set(booking.page_id, page || null);
    }
    const page = booking.page_id ? pageCache.get(booking.page_id) : null;
    const offsets = (page && Array.isArray(page.reminder_minutes) && page.reminder_minutes.length
      ? page.reminder_minutes
      : DEFAULT_REMINDER_MINUTES
    ).filter((m) => m > 0 && m <= MAX_OFFSET_MIN);

    const minutesUntil = (Date.parse(booking.start_at) - now) / MIN;
    for (const offset of offsets) {
      // ~15-minute window per offset (matches the cron cadence); the unique log dedupes overlaps.
      if (minutesUntil < offset - 7 || minutesUntil > offset + 8) continue;
      const kind = `reminder_${offset}m`;
      if (await alreadySent(booking.id, kind)) continue;
      if (!(await logSent(booking.id, kind))) continue;

      try {
        if (booking.event_type_id && !etCache.has(booking.event_type_id)) {
          const { data: et } = await supabaseAdmin.from('EventType').select('*').eq('id', booking.event_type_id).maybeSingle();
          etCache.set(booking.event_type_id, et || null);
        }
        await notify.sendBookingReminder({
          booking,
          eventType: booking.event_type_id ? etCache.get(booking.event_type_id) : null,
          page,
          kind,
          offsetMinutes: offset,
        });
        sent += 1;
      } catch (err) {
        logger.error('[bookingReminders] send failed', { bookingId: booking.id, kind, error: err.message });
      }
    }
  }

  if (sent > 0) logger.info('[bookingReminders] reminders sent', { count: sent });
}

module.exports = { runBookingReminders };
