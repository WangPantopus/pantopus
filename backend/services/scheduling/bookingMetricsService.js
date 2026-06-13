// ============================================================
// Calendarly — booking metrics for the Scheduling Hub stats card + Summary Card.
// Aggregates directly from the Booking table (no separate event-log table needed for v1).
// ============================================================

const supabaseAdmin = require('../../config/supabaseAdmin');

const DAY_MS = 24 * 60 * 60 * 1000;

function monthStart(d) {
  return new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), 1)).toISOString();
}
function prevMonthStart(d) {
  return new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() - 1, 1)).toISOString();
}

/**
 * Summary metrics for an owner over the current month + a 30-day sparkline.
 * @returns {Promise<{ bookingsThisMonth, bookingsLastMonth, deltaPct, upcomingCount, noShowCount, sparkline, byEventType }>}
 */
async function getSummary({ ownerType, ownerId, now }) {
  const nowDate = now ? new Date(now) : new Date();
  const nowIso = nowDate.toISOString();
  const thisMonth = monthStart(nowDate);
  const lastMonth = prevMonthStart(nowDate);
  const windowStart = new Date(nowDate.getTime() - 30 * DAY_MS).toISOString();

  const base = () => supabaseAdmin.from('Booking').select('id', { count: 'exact', head: true }).eq('owner_type', ownerType).eq('owner_id', ownerId);

  const [thisMonthRes, lastMonthRes, upcomingRes, noShowRes] = await Promise.all([
    base().gte('created_at', thisMonth).in('status', ['pending', 'confirmed', 'completed', 'no_show']),
    base().gte('created_at', lastMonth).lt('created_at', thisMonth).in('status', ['pending', 'confirmed', 'completed', 'no_show']),
    base().eq('status', 'confirmed').gt('start_at', nowIso),
    base().eq('status', 'no_show').gte('start_at', thisMonth),
  ]);

  const bookingsThisMonth = thisMonthRes.count || 0;
  const bookingsLastMonth = lastMonthRes.count || 0;
  const deltaPct = bookingsLastMonth > 0
    ? Math.round(((bookingsThisMonth - bookingsLastMonth) / bookingsLastMonth) * 100)
    : (bookingsThisMonth > 0 ? 100 : 0);

  // Rows for the 30-day sparkline + per-event-type breakdown (aggregate in JS).
  const { data: rows } = await supabaseAdmin
    .from('Booking')
    .select('event_type_id, start_at, created_at, status')
    .eq('owner_type', ownerType)
    .eq('owner_id', ownerId)
    .gte('created_at', windowStart)
    .in('status', ['pending', 'confirmed', 'completed', 'no_show'])
    .limit(2000);

  const sparkMap = new Map();
  const etMap = new Map();
  for (const r of rows || []) {
    const day = (r.created_at || '').slice(0, 10);
    if (day) sparkMap.set(day, (sparkMap.get(day) || 0) + 1);
    if (r.event_type_id) etMap.set(r.event_type_id, (etMap.get(r.event_type_id) || 0) + 1);
  }
  const sparkline = [];
  for (let i = 29; i >= 0; i--) {
    const day = new Date(nowDate.getTime() - i * DAY_MS).toISOString().slice(0, 10);
    sparkline.push({ date: day, count: sparkMap.get(day) || 0 });
  }
  const byEventType = [...etMap.entries()].map(([event_type_id, count]) => ({ event_type_id, count })).sort((a, b) => b.count - a.count);

  return {
    bookingsThisMonth,
    bookingsLastMonth,
    deltaPct,
    upcomingCount: upcomingRes.count || 0,
    noShowCount: noShowRes.count || 0,
    sparkline,
    byEventType,
  };
}

module.exports = { getSummary };
