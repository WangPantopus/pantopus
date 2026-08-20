// ============================================================
// MAIL DAY NOTIFICATION JOB
//
// Sends the one daily push for physical-mail triage.
//
// This job previously built a summary, wrote a MailEvent row, and stopped
// — "In a production system, this would call the notification service."
// It also ran on a fixed `0 8 * * *` UTC cron, which is 1am Pacific, so
// even once wired it would have fired in the middle of the night about
// mail that had not been scanned yet.
//
// Two rules shape the rewrite:
//
//   1. SCHEDULE OFF THE SCAN, NOT THE CLOCK. A "you have mail" push at
//      7:40am for a mailbox that gets scanned at 2pm is a lie, and a
//      channel that lies gets muted inside a week. The job now runs
//      frequently and sends only once a user actually has unreviewed
//      pieces waiting, inside their own local daytime window.
//
//   2. NOTHING IDENTIFYING ON THE LOCK SCREEN. The copy is counts and
//      generic kinds only — never a sender, never a category that leaks
//      a health, legal, or financial relationship. "3 pieces today —
//      1 needs you." is the whole message; the detail lives behind auth.
//
// Idempotency is `MailDaySession.notified_at` (migration 160): one row
// per (user_id, day_date) with a unique constraint, so a frequent job
// cannot double-send across overlapping runs or restarts.
// ============================================================

const supabaseAdmin = require('../config/supabaseAdmin');
const logger = require('../utils/logger');
const notificationService = require('../services/notificationService');

// The local-time window the push may land in. Outside it the user is
// simply skipped — the next run re-evaluates, so a late scan still gets
// delivered the same day rather than being dropped.
const SEND_AFTER_LOCAL_HOUR = 9;
const SEND_BEFORE_LOCAL_HOUR = 20;

const DEFAULT_TIMEZONE = 'America/Los_Angeles';

// Kinds that carry an action the resident is the only one who can take.
// Everything else is triage-at-leisure and never drives the "needs you".
const ACTIONABLE_KINDS = new Set(['bill', 'package']);

function todayDate() {
  return new Date().toISOString().slice(0, 10); // UTC — matches MailDayItem.day_date
}

/** Local wall-clock hour (0–23) in the user's timezone. */
function localHour(timezone, now = new Date()) {
  try {
    const parts = new Intl.DateTimeFormat('en-US', {
      timeZone: timezone || DEFAULT_TIMEZONE,
      hour: '2-digit',
      hour12: false,
    }).formatToParts(now);
    const hour = Number(parts.find((p) => p.type === 'hour')?.value);
    if (Number.isFinite(hour)) return hour % 24;
  } catch {
    // Unknown timezone string — fall through to the default below.
  }
  if (timezone && timezone !== DEFAULT_TIMEZONE) return localHour(DEFAULT_TIMEZONE, now);
  return now.getUTCHours();
}

/** "22:00" / "22:00:00" → 22. Null for anything unparseable. */
function parseHour(timeStr) {
  if (!timeStr) return null;
  const m = /^(\d{1,2}):/.exec(String(timeStr));
  if (!m) return null;
  const h = Number(m[1]);
  return Number.isInteger(h) && h >= 0 && h <= 23 ? h : null;
}

/**
 * Quiet hours are a local wall-clock range that may wrap past midnight
 * (e.g. 22:00 → 07:00 is quiet at 23:00 and at 02:00, but not at 12:00).
 */
function inQuietHours(hour, startStr, endStr) {
  const start = parseHour(startStr);
  const end = parseHour(endStr);
  if (start === null || end === null) return false;
  if (start === end) return false;
  if (start < end) return hour >= start && hour < end;
  return hour >= start || hour < end;
}

/** Whether this user may be interrupted right now. */
function isSendableNow(prefs, now = new Date()) {
  const timezone = prefs?.daily_briefing_timezone || DEFAULT_TIMEZONE;
  const hour = localHour(timezone, now);
  if (hour < SEND_AFTER_LOCAL_HOUR || hour >= SEND_BEFORE_LOCAL_HOUR) return false;
  if (inQuietHours(hour, prefs?.quiet_hours_start_local, prefs?.quiet_hours_end_local)) return false;
  return true;
}

/**
 * The push copy. Counts only — no sender, no category, nothing that
 * describes a relationship. Returns null when there is nothing to say.
 */
function composeMailDayPush(unreviewedItems) {
  const pieces = unreviewedItems.length;
  if (pieces === 0) return null;

  const needsYou = unreviewedItems.filter((i) => ACTIONABLE_KINDS.has(i.kind)).length;
  const pieceWord = pieces === 1 ? 'piece' : 'pieces';

  const body = needsYou > 0
    ? `${pieces} ${pieceWord} today — ${needsYou} needs you.`
    : `${pieces} ${pieceWord} today.`;

  return { title: 'Mail day', body, pieces, needs_you: needsYou };
}

// ── The job ─────────────────────────────────────────────────

async function mailDayNotification() {
  const today = todayDate();
  logger.info('[MailDay] Starting mail day notification job', { day_date: today });

  // Only users with something still to triage today are candidates.
  const { data: items, error } = await supabaseAdmin
    .from('MailDayItem')
    .select('user_id, kind, scanned_at')
    .eq('day_date', today)
    .eq('status', 'unreviewed');

  if (error) {
    logger.error('[MailDay] Failed to query today\'s triage queue', { error: error.message });
    return;
  }

  const byUser = new Map();
  for (const item of items || []) {
    if (!item.user_id) continue;
    if (!byUser.has(item.user_id)) byUser.set(item.user_id, []);
    byUser.get(item.user_id).push(item);
  }

  logger.info(`[MailDay] ${byUser.size} users with unreviewed mail today`);

  let notified = 0;
  let deferred = 0;
  let skipped = 0;

  for (const [userId, userItems] of byUser) {
    try {
      // Already notified, or the user already finished the day on their own.
      const { data: session } = await supabaseAdmin
        .from('MailDaySession')
        .select('id, notified_at, finished_at, streak_days')
        .eq('user_id', userId)
        .eq('day_date', today)
        .maybeSingle();

      if (session && (session.notified_at || session.finished_at)) {
        skipped++;
        continue;
      }

      const { data: prefs } = await supabaseAdmin
        .from('UserNotificationPreferences')
        .select('daily_briefing_timezone, quiet_hours_start_local, quiet_hours_end_local')
        .eq('user_id', userId)
        .maybeSingle();

      // Outside the local window — leave it for a later run today rather
      // than dropping it, so a 4pm scan still gets its push at 4pm.
      if (!isSendableNow(prefs)) {
        deferred++;
        continue;
      }

      const push = composeMailDayPush(userItems);
      if (!push) {
        skipped++;
        continue;
      }

      // createNotification applies the user's global push toggle and the
      // `mail_summary_enabled` type preference, emits the socket event, and
      // refreshes the badge — so the job never talks to pushService directly.
      const created = await notificationService.createNotification({
        userId,
        type: 'mail_summary',
        title: push.title,
        body: push.body,
        icon: '📬',
        link: '/app/mailbox',
        metadata: {
          day_date: today,
          pieces: push.pieces,
          needs_you: push.needs_you,
        },
      });

      if (!created) {
        // The notification row failed to write; leave notified_at unset so
        // the next run retries rather than silently losing the day.
        skipped++;
        continue;
      }

      // Claim the day. Upsert because the triage session row is created
      // lazily — a user who has not opened the screen yet has no row.
      await supabaseAdmin
        .from('MailDaySession')
        .upsert({
          user_id: userId,
          day_date: today,
          notified_at: new Date().toISOString(),
          streak_days: session?.streak_days || 0,
          updated_at: new Date().toISOString(),
        }, { onConflict: 'user_id,day_date' });

      await supabaseAdmin
        .from('MailEvent')
        .insert({
          event_type: 'mail_day_notification',
          user_id: userId,
          metadata: { day_date: today, pieces: push.pieces, needs_you: push.needs_you },
        });

      notified++;
    } catch (err) {
      logger.error('[MailDay] Error processing user notification', {
        userId,
        error: err.message,
      });
    }
  }

  logger.info(`[MailDay] Complete: ${notified} notified, ${deferred} deferred, ${skipped} skipped`);
}

module.exports = mailDayNotification;
// Exported for unit testing.
module.exports.composeMailDayPush = composeMailDayPush;
module.exports.isSendableNow = isSendableNow;
module.exports.inQuietHours = inQuietHours;
module.exports.localHour = localHour;
