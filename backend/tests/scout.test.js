// ============================================================
// TEST: Before-You-Sign Scout (Wave 4)
//
// Scout is the one surface where the person asking is NOT the person the
// data is about — they are considering an address somebody else
// currently lives at. So the invariants are mostly about restraint:
//   * facts about land and buildings only, never about the occupants;
//   * every generated line is a QUESTION or a fact, never advice;
//   * a fact the CALLER supplied is attributed to them, not presented as
//     something we looked up;
//   * the typed address is never persisted — checking out a place you
//     might rent is not consent to a record of having looked.
// ============================================================

const { askBeforeYouSign, LEAD_DISCLOSURE_YEAR } = require('../services/scoutService');

const FLOOD_HIGH = { zone: 'AE', in_sfha: true };
const FLOOD_LOW = { zone: 'X', in_sfha: false };
const NFIP = { premium_p25: 480, premium_median: 760, premium_p75: 1240, policy_count: 128 };
const RENT = { band_low: 2120, band_high: 2600, period: 'FY 2026' };

describe('the question list is the product', () => {
  test('every question carries the fact that generated it', () => {
    const asks = askBeforeYouSign({
      flood: FLOOD_HIGH, nfip: NFIP, radon: { radon_zone: 1, year_built: 1961 },
      water: { violation_count: 2 }, rentBand: RENT, askingRent: 2900,
    });
    expect(asks.length).toBeGreaterThan(4);
    for (const a of asks) {
      expect(a.id).toBeTruthy();
      expect(a.question).toBeTruthy();
      // A question without its reason is a checklist off the internet.
      expect(a.because).toBeTruthy();
      expect(a.because.length).toBeGreaterThan(20);
    }
  });

  test('nothing reads as advice, an instruction, or a legal opinion', () => {
    const asks = askBeforeYouSign({
      flood: FLOOD_HIGH, nfip: NFIP, radon: { radon_zone: 1, year_built: 1961 },
      water: { violation_count: 2 }, rentBand: RENT, askingRent: 2900,
    });
    const text = asks.map((a) => `${a.question} ${a.because}`).join(' ');
    // We are not the reader's lawyer, agent, or inspector.
    expect(text).not.toMatch(/\byou should\b/i);
    expect(text).not.toMatch(/\bdemand\b/i);
    expect(text).not.toMatch(/\bwe recommend\b/i);
    expect(text).not.toMatch(/\bdo not sign\b/i);
    expect(text).not.toMatch(/\bwalk away\b/i);
    // And never a verdict on the price.
    expect(text).not.toMatch(/\boverpriced\b|\bbad deal\b|\brip.?off\b/i);
  });

  test('a high-risk flood zone asks who pays, and prices it from real policies', () => {
    const asks = askBeforeYouSign({ flood: FLOOD_HIGH, nfip: NFIP });
    const ids = asks.map((a) => a.id);
    expect(ids).toContain('flood_insurance_required');
    expect(ids).toContain('flood_premium_benchmark');
    const priced = asks.find((a) => a.id === 'flood_premium_benchmark');
    expect(priced.because).toMatch(/\$760/);
    // A benchmark, never a quote for this address.
    expect(priced.because).toMatch(/could differ/i);
  });

  test('a low-risk zone asks the opposite question — is there a policy at all', () => {
    const ids = askBeforeYouSign({ flood: FLOOD_LOW }).map((a) => a.id);
    expect(ids).toContain('flood_history');
    expect(ids).not.toContain('flood_insurance_required');
  });

  test('the lead-paint question attributes the year to the CALLER', () => {
    // We do not look up a build year for an address we have no claim on
    // — the reader supplied it from the listing, and the copy says so.
    const ask = askBeforeYouSign({ radon: { year_built: LEAD_DISCLOSURE_YEAR - 1 } })
      .find((a) => a.id === 'lead_disclosure');
    expect(ask).toBeTruthy();
    expect(ask.because).toMatch(/you told us/i);
  });

  test('no lead question when the year is unknown or after the rule', () => {
    expect(askBeforeYouSign({ radon: { year_built: null } }).some((a) => a.id === 'lead_disclosure')).toBe(false);
    expect(askBeforeYouSign({ radon: {} }).some((a) => a.id === 'lead_disclosure')).toBe(false);
    expect(askBeforeYouSign({ radon: { year_built: LEAD_DISCLOSURE_YEAR } }).some((a) => a.id === 'lead_disclosure')).toBe(false);
  });

  test('an above-band rent is framed as a thing to have an answer for, not a verdict', () => {
    const ask = askBeforeYouSign({ rentBand: RENT, askingRent: 3200 }).find((a) => a.id === 'rent_above_band');
    expect(ask).toBeTruthy();
    expect(ask.because).toMatch(/not by itself a problem/i);
  });

  test('an in-band rent raises no pricing question at all', () => {
    const ids = askBeforeYouSign({ rentBand: RENT, askingRent: 2300 }).map((a) => a.id);
    expect(ids).not.toContain('rent_above_band');
  });

  test('the one question everyone gets is always present', () => {
    // Even with nothing known about the address.
    expect(askBeforeYouSign({}).map((a) => a.id)).toContain('whats_changed');
  });

  test('a quiet address produces a short list, not padding', () => {
    const asks = askBeforeYouSign({ flood: null, radon: { radon_zone: 3 }, water: { violation_count: 0 } });
    expect(asks.length).toBeLessThanOrEqual(2);
  });
});

describe('Scout never describes the people who live there', () => {
  test('no generated line mentions an occupant, owner, or neighbour', () => {
    const asks = askBeforeYouSign({
      flood: FLOOD_HIGH, nfip: NFIP, radon: { radon_zone: 1, year_built: 1961 },
      water: { violation_count: 2 }, rentBand: RENT, askingRent: 2900,
    });
    const text = JSON.stringify(asks).toLowerCase();
    // "current occupant" appears once, deliberately, as someone to ASK —
    // never as a subject we describe. Everything else is off-limits.
    expect(text).not.toMatch(/\bowner'?s? name\b|\bresident'?s? name\b/);
    expect(text).not.toMatch(/\bneighbou?rs? (pay|earn|are)\b/);
    expect(text).not.toMatch(/\bhousehold\b/);
    expect(text).not.toMatch(/\bverified (homes?|neighbou?rs?)\b/);
  });
});

// ── The promise in the copy must be true in the code ─────────
// Scout's scope_note tells the reader "we did not tell anyone you
// looked". That is only true if the typed address never leaves this
// process. It did once: getScoutReport called
// neighborhoodProfileService.getProfile, which passes the address
// straight into a WalkScore query string. This pins the fix.
describe('the typed address never leaves the process', () => {
  const { resetTables } = require('./__mocks__/supabaseAdmin');
  const scoutService = require('../services/scoutService');

  const PLACE = {
    lat: 45.51,
    lng: -122.65,
    // Distinctive enough that any appearance in an outbound URL is proof.
    line: '1421 ZZQUNIQUEADDR St',
    city: 'Portland',
    state: 'OR',
    zipcode: '97214',
  };

  test('no outbound request carries the address, in any encoding', async () => {
    resetTables();
    const seen = [];
    const realFetch = global.fetch;
    // The tract geocode must SUCCEED, or a leak downstream of it is never
    // reached and the test proves nothing — which is exactly what a
    // blanket-503 mock did on the first attempt.
    global.fetch = jest.fn(async (url) => {
      const u = String(url);
      seen.push(u);
      if (u.includes('geocoding.geo.census.gov')) {
        return {
          ok: true,
          status: 200,
          json: async () => ({
            result: { geographies: { 'Census Tracts': [{ STATE: '41', COUNTY: '051', TRACT: '001902' }] } },
          }),
        };
      }
      return { ok: false, status: 503, json: async () => ({}) };
    });
    process.env.WALKSCORE_API_KEY = 'test-key-that-would-enable-the-leak';

    try {
      await scoutService.getScoutReport(PLACE, { askingRent: 2400 });
    } finally {
      global.fetch = realFetch;
      delete process.env.WALKSCORE_API_KEY;
    }

    expect(seen.length).toBeGreaterThan(0); // it really did call out
    for (const url of seen) {
      const decoded = decodeURIComponent(url).toLowerCase();
      expect(decoded).not.toContain('zzquniqueaddr');
      // And never to the service that took the address before.
      expect(url).not.toContain('walkscore.com');
    }
  });

  test('the report still says so, and still answers', async () => {
    resetTables();
    const report = await scoutService.getScoutReport(PLACE, {});
    expect(report.scope_note).toMatch(/did not tell anyone you looked/i);
    // Degrading to no external data must still produce the question list.
    expect(report.ask_before_you_sign.length).toBeGreaterThan(0);
  });
});
