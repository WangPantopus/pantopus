/**
 * NFIP Premium Benchmark (Wave 2 — "Flood Insurance, In Dollars")
 *
 * What flood policies around this home actually cost: count and
 * quartiles of real NFIP premiums in the home's census tract, from
 * OpenFEMA's NFIP Policies v3 (free, national, redacted to block
 * group). Extends the flood section — a zone tells you the hazard;
 * this tells you the bill.
 *
 * Probed live before building (2026-08-25):
 *   * v2 (FimaNfipPolicies) is DEPRECATED — frozen 2026-06-01, gone
 *     2026-10-15. v3 is /api/open/v3/NfipPolicies and renames
 *     censusTract → censusGeoid (12-digit block group), adds
 *     fullRiskPremium (Risk Rating 2.0).
 *   * The ONLY fast query shape is a bare censusGeoid range + $select
 *     + $top: latency scales ~20 ms/row, and adding $inlinecount,
 *     $orderby, or ANY second filter conjunction 503s around 60 s.
 *     A 2,000-row fetch runs ~40 s — far too slow for a request path.
 *
 * So the design is CACHE-ONLY composition + background warm:
 *   * the flood composer calls getTractBenchmark() — one cache read;
 *     on a miss it writes a `pending` marker and returns nothing;
 *   * the nfipTractWarm job (every 15 min) picks up pending/expired
 *     tracts and does the slow fetch with a job-sized timeout;
 *   * benchmarks cache 90 days (the dataset refreshes ~monthly);
 *   * fewer than K_MIN=10 recent policies → stored as `suppressed`
 *     (same floor the bill benchmark uses — premiums are about homes,
 *     and a benchmark of 3 neighbors is a disclosure, not a stat).
 *
 * Honesty: quartiles are labeled with their window (last 24 months of
 * policy effective dates) and coverage — a tract with more than
 * FETCH_ROW_CAP all-time rows gets stats over an arbitrary subset and
 * is marked `coverage: 'partial'`. Never presented as a quote.
 */

const supabaseAdmin = require('../config/supabaseAdmin');
const logger = require('../utils/logger');
const { readRow, writeRow } = require('./placeSectionCache');

const OPENFEMA_BASE = 'https://www.fema.gov/api/open/v3/NfipPolicies';
const SECTION_ID = '_nfip_tract';
const TTL_MS = 90 * 24 * 60 * 60 * 1000; // benchmark refresh cycle
const PENDING_TTL_MS = 90 * 24 * 60 * 60 * 1000; // marker lives until the job replaces it
const WINDOW_MONTHS = 24;
const K_MIN = 10;
// One request, hard row cap — latency is ~20 ms/row server-side, so
// 2,000 rows ≈ 40 s, inside the job timeout and past most tracts'
// all-time policy count.
const FETCH_ROW_CAP = 2000;
const FETCH_TIMEOUT_MS = 75000;

function cacheKeyFor(tractId) {
  return `tract:${tractId}`;
}

function isValidTract(tractId) {
  return /^\d{11}$/.test(String(tractId || ''));
}

// ── The slow fetch (job context only) ────────────────────────

/**
 * All policies for a tract, one unordered request. censusGeoid is the
 * 12-digit block group, so the tract's rows are the ge/le range over
 * its 10 possible last digits.
 */
async function fetchTractPolicies(tractId) {
  const filter = `censusGeoid ge '${tractId}0' and censusGeoid le '${tractId}9'`;
  const params = new URLSearchParams({
    $filter: filter,
    $top: String(FETCH_ROW_CAP),
    $select: 'totalInsurancePremiumOfThePolicy,fullRiskPremium,policyEffectiveDate,occupancyType',
  });
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);
  try {
    const res = await fetch(`${OPENFEMA_BASE}?${params}`, { signal: controller.signal });
    if (!res.ok) throw new Error(`OpenFEMA HTTP ${res.status}`);
    const json = await res.json();
    const rows = json && json.NfipPolicies;
    if (!Array.isArray(rows)) throw new Error('OpenFEMA: unexpected response shape');
    return rows;
  } finally {
    clearTimeout(timer);
  }
}

function quantile(sorted, q) {
  if (!sorted.length) return null;
  const idx = Math.min(sorted.length - 1, Math.max(0, Math.round(q * (sorted.length - 1))));
  return sorted[idx];
}

/**
 * Rows → the stored benchmark payload. Pure, exported for tests.
 * @param {Array<object>} rows raw v3 policy rows
 * @param {Date} now
 */
function computeBenchmark(rows, now = new Date()) {
  const windowStart = new Date(now);
  windowStart.setMonth(windowStart.getMonth() - WINDOW_MONTHS);
  const startIso = windowStart.toISOString();

  const recent = rows.filter((r) => typeof r.policyEffectiveDate === 'string' && r.policyEffectiveDate >= startIso);
  const premiums = recent
    .map((r) => Number(r.totalInsurancePremiumOfThePolicy))
    .filter((n) => Number.isFinite(n) && n > 0)
    .sort((a, b) => a - b);

  if (premiums.length < K_MIN) {
    // Stored (not just returned) so the composer doesn't re-request a
    // warm for a tract we already know is too thin.
    return { suppressed: true, policy_count: premiums.length };
  }

  const fullRisk = recent
    .map((r) => Number(r.fullRiskPremium))
    .filter((n) => Number.isFinite(n) && n > 0)
    .sort((a, b) => a - b);

  return {
    policy_count: premiums.length,
    premium_p25: quantile(premiums, 0.25),
    premium_median: quantile(premiums, 0.5),
    premium_p75: quantile(premiums, 0.75),
    // Risk Rating 2.0's actuarial number, where reported.
    full_risk_median: fullRisk.length >= K_MIN ? quantile(fullRisk, 0.5) : null,
    window_months: WINDOW_MONTHS,
    // FETCH_ROW_CAP all-time rows means an arbitrary subset was
    // sampled — the stats are honest but the coverage is not total.
    coverage: rows.length >= FETCH_ROW_CAP ? 'partial' : 'full',
  };
}

// ── Request-path read (never fetches) ────────────────────────

/**
 * The benchmark for a tract, from cache only.
 * @returns {Promise<{status: 'ready'|'pending'|'suppressed', data?: object, fetchedAt?: string}>}
 *   `pending` also covers "tract we've never seen" — a marker row is
 *   written so the warm job picks it up.
 */
async function getTractBenchmark(tractId) {
  if (!isValidTract(tractId)) return { status: 'pending' };

  const row = await readRow(cacheKeyFor(tractId), SECTION_ID);
  const payload = row && row.payload;
  if (payload && !payload.pending) {
    if (payload.suppressed) return { status: 'suppressed' };
    return { status: 'ready', data: payload, fetchedAt: row.fetched_at };
  }

  if (!payload) {
    // First sighting: leave a pending marker for the warm job. A lost
    // race between instances just writes the same marker twice.
    const nowIso = new Date().toISOString();
    await writeRow(cacheKeyFor(tractId), SECTION_ID, { pending: true, requested_at: nowIso }, PENDING_TTL_MS, nowIso);
  }
  return { status: 'pending' };
}

// ── The warm job worker ──────────────────────────────────────

/**
 * Fetch + store benchmarks for tracts that need it: pending markers
 * first, then the oldest expired benchmarks. Runs on every instance
 * with no leader election (like the other jobs) — a duplicate warm is
 * two identical upserts, harmless by design.
 * @returns {Promise<{warmed: number, failed: number}>}
 */
async function warmPendingTracts({ limit = 3 } = {}) {
  const nowIso = new Date().toISOString();
  const candidates = [];

  const { data: pending, error: pendErr } = await supabaseAdmin
    .from('PlaceSectionCache')
    .select('cache_key, payload')
    .eq('section_id', SECTION_ID)
    .filter('payload->>pending', 'eq', 'true')
    .order('fetched_at', { ascending: true })
    .limit(limit);
  if (pendErr) {
    // Missing table = migration 156 not applied; nothing to warm.
    logger.warn('nfipWarm: pending scan failed', { error: pendErr.message });
    return { warmed: 0, failed: 0 };
  }
  candidates.push(...(pending || []));

  if (candidates.length < limit) {
    const { data: expired } = await supabaseAdmin
      .from('PlaceSectionCache')
      .select('cache_key, payload')
      .eq('section_id', SECTION_ID)
      .lt('expires_at', nowIso)
      .order('expires_at', { ascending: true })
      .limit(limit - candidates.length);
    for (const row of expired || []) {
      if (!candidates.some((c) => c.cache_key === row.cache_key)) candidates.push(row);
    }
  }

  let warmed = 0;
  let failed = 0;
  for (const row of candidates) {
    const tractId = String(row.cache_key || '').replace(/^tract:/, '');
    if (!isValidTract(tractId)) continue;
    try {
      const rows = await fetchTractPolicies(tractId);
      const benchmark = computeBenchmark(rows);
      const fetchedIso = new Date().toISOString();
      await writeRow(cacheKeyFor(tractId), SECTION_ID, benchmark, TTL_MS, fetchedIso);
      warmed += 1;
      logger.info('nfipWarm: tract warmed', {
        tractId,
        rows: rows.length,
        suppressed: Boolean(benchmark.suppressed),
      });
    } catch (err) {
      // Leave the marker in place — the next run retries. An expired
      // real benchmark keeps serving stale meanwhile (database-first).
      failed += 1;
      logger.warn('nfipWarm: tract fetch failed', { tractId, error: err.message });
    }
  }
  return { warmed, failed };
}

module.exports = {
  getTractBenchmark,
  warmPendingTracts,
  // Exported for testing.
  computeBenchmark,
  fetchTractPolicies,
  isValidTract,
  K_MIN,
  WINDOW_MONTHS,
  FETCH_ROW_CAP,
  SECTION_ID,
};
