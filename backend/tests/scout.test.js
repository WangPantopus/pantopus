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

// ── One report per address, not one per deployment ───────────
//
// `homeCountyFips` cached the Census geocode under `home:${home.id}`.
// Scout's synthetic home carries `id: null` on purpose — nothing may
// resolve to a real Home row — so that template literal produced the
// literal string "home:null": ONE global cache row, TTL a year, shared
// by every Scout request in the deployment.
//
// The first address anyone scouted pinned its county forever. Every
// later report then priced rent, screened radon and named the water
// utility for that stranger's county. A Brooklyn scout was told Travis
// County's rent band — inverting "above band" and "below band" on the
// single most decision-relevant line of a page whose whole premise is
// checking before you sign.
describe('two addresses in one process are two different places', () => {
  const { resetTables, seedTable, getTable } = require('./__mocks__/supabaseAdmin');
  const scoutService = require('../services/scoutService');

  // Travis County, TX and Kings County, NY — real FIPS, distinct rent
  // bands, distinct radon zones.
  const AUSTIN = { lat: 30.2672, lng: -97.7431, line: '1 Congress Ave', city: 'Austin', state: 'TX', zipcode: '78701' };
  const BROOKLYN = { lat: 40.6782, lng: -73.9442, line: '1 Bedford Ave', city: 'Brooklyn', state: 'NY', zipcode: '11211' };

  function seedCounties() {
    seedTable('HudFmr', [
      {
        county_fips: '48453', fiscal_year: 2026, county_name: 'Travis County', state_abbr: 'TX',
        area_name: 'Austin', fmr_lo: [1200, 1400, 1600, 2000, 2400], fmr_hi: [1200, 1400, 1600, 2000, 2400],
      },
      {
        county_fips: '36047', fiscal_year: 2026, county_name: 'Kings County', state_abbr: 'NY',
        area_name: 'New York', fmr_lo: [2100, 2400, 2800, 3500, 3900], fmr_hi: [2100, 2400, 2800, 3500, 3900],
      },
    ]);
    seedTable('CountyRadonZone', [
      { county_fips: '48453', zone: 1 },
      { county_fips: '36047', zone: 3 },
    ]);
  }

  // The Census geocoder answers per-coordinate; everything else is down,
  // so the only thing under test is which county each address resolves to.
  function mockGeocoderByCoordinate() {
    return jest.fn(async (url) => {
      const u = String(url);
      if (u.includes('geocoding.geo.census.gov')) {
        const isAustin = u.includes('30.2672') || u.includes('-97.7431');
        return {
          ok: true,
          status: 200,
          json: async () => ({
            result: {
              geographies: {
                'Census Tracts': isAustin
                  ? [{ STATE: '48', COUNTY: '453', TRACT: '001100' }]
                  : [{ STATE: '36', COUNTY: '047', TRACT: '050300' }],
              },
            },
          }),
        };
      }
      return { ok: false, status: 503, json: async () => ({}) };
    });
  }

  test('the second scout gets its OWN county, not the first one’s', async () => {
    resetTables();
    seedCounties();
    const realFetch = global.fetch;
    global.fetch = mockGeocoderByCoordinate();

    let austin;
    let brooklyn;
    try {
      austin = await scoutService.getScoutReport(AUSTIN, { askingRent: 1800, yearBuilt: 1970 });
      brooklyn = await scoutService.getScoutReport(BROOKLYN, { askingRent: 2400, yearBuilt: 1970 });
    } finally {
      global.fetch = realFetch;
    }

    expect(austin.rent.band_low).toBe(1600);
    expect(brooklyn.rent.band_low).toBe(2800);
    // The line that flipped: $2,400 is BELOW Kings County's band, and was
    // reported as above it while Travis County's band was being served.
    expect(austin.rent.position).toBe('in_band');
    expect(brooklyn.rent.position).toBe('below_band');

    expect(austin.environment.radon.radon_zone).toBe(1);
    expect(brooklyn.environment.radon.radon_zone).toBe(3);
  });

  test('no cache row is keyed on a null home id', async () => {
    resetTables();
    seedCounties();
    const realFetch = global.fetch;
    global.fetch = mockGeocoderByCoordinate();
    try {
      await scoutService.getScoutReport(AUSTIN, {});
    } finally {
      global.fetch = realFetch;
    }
    for (const row of getTable('PlaceSectionCache')) {
      expect(row.cache_key).not.toMatch(/null/);
    }
  });
});

// ── The never-advice rules apply to the WHOLE payload ────────
//
// They were only ever enforced on `askBeforeYouSign`. Everything else in
// the report came from the dashboard composers, which write for a reader
// who LIVES there: "Your county has the highest radon potential (zone 1)
// — test before renovating." Forwarded whole, that addressed a
// non-resident as the occupant and issued an instruction, straight past
// the rules two describe blocks up.
describe('nothing in the report speaks to the reader as a resident', () => {
  const { resetTables, seedTable } = require('./__mocks__/supabaseAdmin');
  const scoutService = require('../services/scoutService');

  const PLACE = { lat: 30.2672, lng: -97.7431, line: '1 Congress Ave', city: 'Austin', state: 'TX', zipcode: '78701' };

  function censusOnly() {
    return jest.fn(async (url) => {
      if (String(url).includes('geocoding.geo.census.gov')) {
        return {
          ok: true,
          status: 200,
          json: async () => ({ result: { geographies: { 'Census Tracts': [{ STATE: '48', COUNTY: '453', TRACT: '001100' }] } } }),
        };
      }
      return { ok: false, status: 503, json: async () => ({}) };
    });
  }

  test('the serialized payload carries no possessive and no imperative', async () => {
    resetTables();
    seedTable('CountyRadonZone', [{ county_fips: '48453', zone: 1 }]);
    seedTable('HudFmr', [{
      county_fips: '48453', fiscal_year: 2026, county_name: 'Travis County', state_abbr: 'TX',
      area_name: 'Austin', fmr_lo: [1200, 1400, 1600, 2000, 2400], fmr_hi: [1200, 1400, 1600, 2000, 2400],
    }]);

    const realFetch = global.fetch;
    global.fetch = censusOnly();
    let report;
    try {
      report = await scoutService.getScoutReport(PLACE, { askingRent: 1800, yearBuilt: 1961 });
    } finally {
      global.fetch = realFetch;
    }

    // The composer really did run — otherwise this proves nothing.
    expect(report.environment.radon.radon_zone).toBe(1);

    const text = JSON.stringify(report);
    // The reader does not live here. Nothing may call it theirs.
    expect(text).not.toMatch(/\byour (county|area|home|building|water|neighbou?rhood)\b/i);
    // And nothing may tell them what to do.
    expect(text).not.toMatch(/\btest before renovating\b/i);
    expect(text).not.toMatch(/\byou should\b|\bwe recommend\b|\bdemand\b/i);
  });

  test('a caller-supplied build year still raises the lead question with no radon coverage', async () => {
    // Radon coverage is county-by-county. Where it is missing the
    // composer degrades to `partial`, which `dataOf` drops — and that
    // used to take the build year with it, losing a federally mandated
    // disclosure question that was never the composer's fact to begin
    // with.
    resetTables(); // no CountyRadonZone rows at all
    const realFetch = global.fetch;
    global.fetch = censusOnly();
    let report;
    try {
      report = await scoutService.getScoutReport(PLACE, { yearBuilt: 1961 });
    } finally {
      global.fetch = realFetch;
    }
    expect(report.ask_before_you_sign.map((a) => a.id)).toContain('lead_disclosure');
  });
});
