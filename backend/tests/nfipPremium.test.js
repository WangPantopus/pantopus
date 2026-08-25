// ============================================================
// TEST: NFIP Premium Benchmark (Wave 2 — Flood Insurance, In Dollars)
//
// The invariants:
//   * the request path NEVER fetches — a cache miss writes a pending
//     marker and returns nothing;
//   * the k-floor (10 recent policies) suppresses thin tracts, and the
//     suppression is STORED so the composer stops re-requesting;
//   * the stats window is 24 months of policy effective dates, and a
//     row-capped fetch is labeled coverage:'partial', never total;
//   * the warm job serves pending tracts first and leaves the marker
//     in place on failure (next run retries).
// ============================================================

const { resetTables, seedTable, getTable } = require('./__mocks__/supabaseAdmin');

const nfip = require('../services/nfipPremiumService');
const { computeBenchmark, getTractBenchmark, warmPendingTracts, K_MIN, FETCH_ROW_CAP } = nfip;

const TRACT = '53011041310';
const NOW = new Date('2026-08-25T00:00:00.000Z');

function policy(premium, effective = '2025-06-01T00:00:00.000Z', extra = {}) {
  return { totalInsurancePremiumOfThePolicy: premium, policyEffectiveDate: effective, ...extra };
}

beforeEach(() => {
  resetTables();
  global.fetch = jest.fn();
});

afterEach(() => {
  delete global.fetch;
});

// ── computeBenchmark (pure) ──────────────────────────────────

describe('computeBenchmark', () => {
  test('quartiles over the 24-month window only', () => {
    const rows = [
      // 12 recent policies 100..1200
      ...Array.from({ length: 12 }, (_, i) => policy((i + 1) * 100)),
      // old policies outside the window must not move the stats
      policy(99999, '2020-01-01T00:00:00.000Z'),
      policy(99999, '2023-01-01T00:00:00.000Z'),
    ];
    const b = computeBenchmark(rows, NOW);
    expect(b.suppressed).toBeUndefined();
    expect(b.policy_count).toBe(12);
    expect(b.premium_median).toBeGreaterThanOrEqual(600);
    expect(b.premium_median).toBeLessThanOrEqual(700);
    expect(b.premium_p25).toBeLessThan(b.premium_median);
    expect(b.premium_p75).toBeGreaterThan(b.premium_median);
    expect(b.window_months).toBe(24);
    expect(b.coverage).toBe('full');
  });

  test('fewer than K_MIN recent policies is stored as suppressed', () => {
    const rows = Array.from({ length: K_MIN - 1 }, () => policy(500));
    expect(computeBenchmark(rows, NOW)).toEqual({ suppressed: true, policy_count: K_MIN - 1 });
  });

  test('a row-capped fetch is labeled partial coverage', () => {
    const rows = Array.from({ length: FETCH_ROW_CAP }, () => policy(500));
    expect(computeBenchmark(rows, NOW).coverage).toBe('partial');
  });

  test('fullRiskPremium median only when it clears the same floor', () => {
    const withFrp = Array.from({ length: 12 }, () => policy(500, undefined, { fullRiskPremium: 900 }));
    expect(computeBenchmark(withFrp, NOW).full_risk_median).toBe(900);
    const fewFrp = [
      ...Array.from({ length: 12 }, () => policy(500)),
      policy(500, undefined, { fullRiskPremium: 900 }),
    ];
    expect(computeBenchmark(fewFrp, NOW).full_risk_median).toBeNull();
  });
});

// ── The cache-only request path ──────────────────────────────

describe('getTractBenchmark', () => {
  test('a miss writes a pending marker and NEVER fetches', async () => {
    const res = await getTractBenchmark(TRACT);
    expect(res.status).toBe('pending');
    expect(global.fetch).not.toHaveBeenCalled();

    const rows = getTable('PlaceSectionCache');
    expect(rows).toHaveLength(1);
    expect(rows[0].cache_key).toBe(`tract:${TRACT}`);
    expect(rows[0].payload.pending).toBe(true);
  });

  test('a warmed benchmark serves ready; a suppressed one serves suppressed', async () => {
    seedTable('PlaceSectionCache', [
      {
        cache_key: `tract:${TRACT}`,
        section_id: '_nfip_tract',
        payload: { policy_count: 40, premium_median: 800, premium_p25: 500, premium_p75: 1200, window_months: 24, coverage: 'full' },
        fetched_at: '2026-08-01T00:00:00.000Z',
        expires_at: '2026-11-01T00:00:00.000Z',
      },
      {
        cache_key: 'tract:12009064128',
        section_id: '_nfip_tract',
        payload: { suppressed: true, policy_count: 3 },
        fetched_at: '2026-08-01T00:00:00.000Z',
        expires_at: '2026-11-01T00:00:00.000Z',
      },
    ]);
    const ready = await getTractBenchmark(TRACT);
    expect(ready.status).toBe('ready');
    expect(ready.data.premium_median).toBe(800);
    expect(ready.fetchedAt).toBe('2026-08-01T00:00:00.000Z');

    expect((await getTractBenchmark('12009064128')).status).toBe('suppressed');
    expect(global.fetch).not.toHaveBeenCalled();
  });

  test('an invalid tract id is pending without a marker', async () => {
    expect((await getTractBenchmark('not-a-tract')).status).toBe('pending');
    expect(getTable('PlaceSectionCache')).toHaveLength(0);
  });
});

// ── The warm job ─────────────────────────────────────────────

describe('warmPendingTracts', () => {
  function seedPending(tractId = TRACT) {
    seedTable('PlaceSectionCache', [{
      cache_key: `tract:${tractId}`,
      section_id: '_nfip_tract',
      payload: { pending: true, requested_at: '2026-08-25T00:00:00.000Z' },
      fetched_at: '2026-08-25T00:00:00.000Z',
      expires_at: '2026-11-25T00:00:00.000Z',
    }]);
  }

  test('fetches the pending tract with the bare-range query and stores the benchmark', async () => {
    seedPending();
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => ({
        NfipPolicies: Array.from({ length: 20 }, (_, i) => policy((i + 1) * 100, '2025-06-01T00:00:00.000Z')),
      }),
    });

    const result = await warmPendingTracts({ limit: 3 });
    expect(result).toEqual({ warmed: 1, failed: 0 });

    // The only fast query shape: bare censusGeoid range, no count, no
    // orderby, no extra conjunctions (all of those 503 — probed live).
    const url = global.fetch.mock.calls[0][0];
    expect(url).toContain('censusGeoid');
    expect(url).not.toContain('inlinecount');
    expect(url).not.toContain('orderby');
    expect(url).not.toContain('policyEffectiveDate%20ge');

    const row = getTable('PlaceSectionCache').find((r) => r.cache_key === `tract:${TRACT}`);
    expect(row.payload.pending).toBeUndefined();
    expect(row.payload.policy_count).toBe(20);
    expect(row.payload.premium_median).toBeGreaterThan(0);
  });

  test('a failed fetch leaves the pending marker for the next run', async () => {
    seedPending();
    global.fetch.mockRejectedValue(new Error('503'));

    const result = await warmPendingTracts({ limit: 3 });
    expect(result).toEqual({ warmed: 0, failed: 1 });

    const row = getTable('PlaceSectionCache').find((r) => r.cache_key === `tract:${TRACT}`);
    expect(row.payload.pending).toBe(true);
  });
});
