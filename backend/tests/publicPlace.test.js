/**
 * Tests for GET /api/public/place — the anonymous T0 "Place" preview.
 *
 * Asserts the W0.3 contract + its constraints:
 *   • density is a BUCKET enum, never a raw count;
 *   • never calls ATTOM (no attomdata.com fetch, no propertyDataService);
 *   • the preview persists NOTHING — no SavedPlace / Home / per-address row and
 *     no DB writes at all (caches are in-memory, location-keyed, anonymous);
 *   • flood degrades independently of the Census tract lookup, and Walk Score
 *     is never fetched;
 *   • a non-US address degrades to `unsupported_region` and an unreadable one
 *     to `could_not_place` — DIFFERENT answers — with
 *     HTTP 200 — never a 500;
 *   • repeat requests are served from the in-memory cache (no second Mapbox /
 *     FEMA / Census round-trip).
 *
 * The geocoder (services/geo) is mocked; the data fetchers run for real against
 * a URL-routed global.fetch mock so we can prove no ATTOM/Walk-Score URL is hit
 * and that caching eliminates the second round-trip. supabaseAdmin is the
 * project's in-memory mock; the route's in-memory caches are reset between
 * tests via its __clearPreviewCaches hook.
 */

const express = require('express');
const request = require('supertest');
const { resetTables, seedTable, getTable } = require('./__mocks__/supabaseAdmin');

// Geocoder — overridden per test. Default (US) set in beforeEach.
jest.mock('../services/geo', () => ({ forwardGeocode: jest.fn() }));
const geo = require('../services/geo');

// ATTOM entry point — must never be invoked by the preview.
jest.mock('../services/propertyDataService', () => ({
  verifyPropertyOwnership: jest.fn(),
  matchOwnerName: jest.fn(),
  isAvailable: jest.fn(() => false),
  PROVIDER: 'none',
}));
const propertyDataService = require('../services/propertyDataService');

const { encodeGeohash6 } = require('../utils/geohash');
const publicRouter = require('../routes/public');

// ── Fixtures ───────────────────────────────────────────────────────────────

const PORTLAND = {
  latitude: 45.5202,
  longitude: -122.6742,
  city: 'Portland',
  state: 'OR',
  zipcode: '97214',
  address: '1421 SE Oak St',
};
const PORTLAND_GEOHASH = encodeGeohash6(PORTLAND.latitude, PORTLAND.longitude);

const CENSUS_GEOCODER = {
  result: { geographies: { 'Census Tracts': [{ STATE: '41', COUNTY: '051', TRACT: '001902' }] } },
};
const CENSUS_ACS = [
  ['B25035_001E', 'B25077_001E', 'B19013_001E', 'B01003_001E', 'B25001_001E', 'state', 'county', 'tract'],
  ['1985', '498000', '70000', '4000', '1800', '41', '051', '001902'],
];
const FEMA = { features: [{ attributes: { FLD_ZONE: 'X', ZONE_SUBTY: null } }] };
const WALKSCORE_URL = 'api.walkscore.com';

function mockResp(data, ok = true) {
  return {
    ok,
    status: ok ? 200 : 500,
    json: () => Promise.resolve(data),
    text: () => Promise.resolve(JSON.stringify(data)),
  };
}

// URL-routed fetch so the real Census/FEMA fetchers resolve. `femaOk`/`censusOk`
// let a test simulate an outage (→ partial). ATTOM and Walk Score URLs would
// resolve too, so asserting they're never called proves intent, not a
// mock-induced failure.
function installFetch({ femaOk = true, censusOk = true, geocoderOk = true } = {}) {
  global.fetch = jest.fn((url) => {
    const u = String(url);
    if (u.includes('attomdata.com')) return Promise.resolve(mockResp({ status: { msg: 'SuccessWithResult' }, property: [] }));
    if (u.includes(WALKSCORE_URL)) return Promise.resolve(mockResp({ status: 1, walkscore: 50 }));
    if (u.includes('geocoding.geo.census.gov')) return Promise.resolve(mockResp(CENSUS_GEOCODER, geocoderOk));
    if (u.includes('api.census.gov')) return Promise.resolve(mockResp(CENSUS_ACS, censusOk));
    if (u.includes('hazards.fema.gov')) return Promise.resolve(mockResp(FEMA, femaOk));
    return Promise.resolve(mockResp({}, false));
  });
}

const countFetch = (substr) => (global.fetch.mock?.calls || []).filter(([u]) => String(u).includes(substr)).length;
const attomWasCalled = () => countFetch('attomdata.com') > 0;

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use('/api/public', publicRouter);
  return app;
}

// ── Setup ────────────────────────────────────────────────────────────────

beforeEach(() => {
  resetTables();
  publicRouter.__clearPreviewCaches();
  jest.clearAllMocks();
  // Walk Score / Census keys off → deterministic external calls.
  delete process.env.WALKSCORE_API_KEY;
  delete process.env.CENSUS_API_KEY;
  geo.forwardGeocode.mockResolvedValue({ ...PORTLAND });
  installFetch();
  seedTable('NeighborhoodPreview', [{ geohash: PORTLAND_GEOHASH, verified_users_count: 5 }]);
});

// ── Tests ────────────────────────────────────────────────────────────────

describe('GET /api/public/place', () => {
  describe('ready — the free demonstration subset', () => {
    it('returns flood, a density bucket, and a Census area teaser', async () => {
      const res = await request(buildApp()).get('/api/public/place').query({ address: '1421 SE Oak St, Portland' });

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('ready');
      expect(res.body.tier).toBe('preview');
      expect(res.body.region).toBe('US');

      // area-level place identity, no precise coordinates leaked
      expect(res.body.place).toEqual({
        address: '1421 SE Oak St', city: 'Portland', state: 'OR', zipcode: '97214',
      });
      expect(res.body.place).not.toHaveProperty('latitude');
      expect(res.body.place).not.toHaveProperty('longitude');

      // flood (FEMA, area-level)
      expect(res.body.free.flood).toMatchObject({ status: 'ready', zone: 'X', description: 'Minimal flood risk' });

      // Census teaser — area medians, explicitly NOT the home's own record
      expect(res.body.free.area).toMatchObject({
        status: 'ready', median_year_built: 1985, median_home_value: 498000, note: 'Area-level, not your home',
      });
    });

    it('exposes density only as a bucket enum — never a count', async () => {
      const res = await request(buildApp()).get('/api/public/place').query({ address: '1421 SE Oak St' });

      const density = res.body.free.density;
      expect(typeof density.bucket).toBe('string');
      expect(['none', 'forming', 'few', 'growing']).toContain(density.bucket);
      // Seeded count = 5, which is below the k-anon floor (10) and so must
      // be indistinguishable from a count of 1.
      expect(density.bucket).toBe('forming');
      // The raw count (5) must never appear anywhere on the density object.
      expect(density).not.toHaveProperty('verified_users_count');
      expect(density).not.toHaveProperty('count');
      expect(JSON.stringify(density)).not.toMatch(/\b5\b/);
    });

    it('returns locked descriptors (group + unlock tier) for everything else', async () => {
      const res = await request(buildApp()).get('/api/public/place').query({ address: '1421 SE Oak St' });

      const ids = res.body.locked.map((s) => s.id);
      expect(ids).toEqual(
        expect.arrayContaining(['daily_conditions', 'home_details', 'health_environment', 'money_signals', 'civic']),
      );
      for (const section of res.body.locked) {
        expect(section).toHaveProperty('group');
        expect(['account', 'claim', 'verify']).toContain(section.unlock);
      }
      // Exact home details/value are Band-B → require claiming, not just an account.
      const home = res.body.locked.find((s) => s.id === 'home_details');
      expect(home).toMatchObject({ band: 'B', unlock: 'claim' });
    });
  });

  describe('density buckets are floored server-side', () => {
    // This endpoint used to carry its own thresholds ({growing:10, few:3,
    // forming:1}) — a third implementation of the k-anon flooring, and the
    // loosest of the three on the only UNAUTHENTICATED surface: a public
    // `forming` meant the cell held exactly 1–2 verified users. It now
    // shares services/place/densityReader, so counts below K_ANON_MIN (10)
    // are indistinguishable from one another here too.
    //
    // Strictly more conservative than before: nothing that was private
    // became public, and cells of 3–9 stopped being separable from 1–2.
    const cases = [
      [0, 'none'],
      [1, 'forming'],
      [2, 'forming'],
      [3, 'forming'],
      [9, 'forming'],
      [10, 'few'],
      [24, 'few'],
      [25, 'growing'],
      [250, 'growing'],
    ];
    it.each(cases)('count %i → bucket "%s" (no number leaked)', async (count, expected) => {
      seedTable('NeighborhoodPreview', [{ geohash: PORTLAND_GEOHASH, verified_users_count: count }]);
      const res = await request(buildApp()).get('/api/public/place').query({ address: '1421 SE Oak St' });
      expect(res.body.free.density.bucket).toBe(expected);
      expect(typeof res.body.free.density.bucket).toBe('string');
    });

    it('treats a cell with no preview row as "none"', async () => {
      seedTable('NeighborhoodPreview', []);
      const res = await request(buildApp()).get('/api/public/place').query({ address: '1421 SE Oak St' });
      expect(res.body.free.density.bucket).toBe('none');
    });
  });

  describe('no ATTOM, no Walk Score, no persistence', () => {
    it('never calls ATTOM or Walk Score', async () => {
      await request(buildApp()).get('/api/public/place').query({ address: '1421 SE Oak St' });
      expect(attomWasCalled()).toBe(false);
      expect(countFetch(WALKSCORE_URL)).toBe(0);
      expect(propertyDataService.verifyPropertyOwnership).not.toHaveBeenCalled();
    });

    it('writes nothing to the database', async () => {
      await request(buildApp()).get('/api/public/place').query({ address: '1421 SE Oak St' });

      // Caches are in-memory only — no DB writes anywhere.
      expect(getTable('NeighborhoodProfileCache')).toHaveLength(0);
      // The preview itself is never persisted.
      expect(getTable('SavedPlace')).toHaveLength(0);
      expect(getTable('Home')).toHaveLength(0);
      // The bucket source is only read, never mutated.
      const previews = getTable('NeighborhoodPreview');
      expect(previews).toHaveLength(1);
      expect(previews[0].verified_users_count).toBe(5);
    });
  });

  describe('caching (in-memory, location-keyed)', () => {
    it('serves a repeat request from cache — no second Mapbox / FEMA / Census hit', async () => {
      const app = buildApp();

      await request(app).get('/api/public/place').query({ address: '1421 SE Oak St' });
      expect(geo.forwardGeocode.mock.calls.length).toBe(1);
      expect(countFetch('hazards.fema.gov')).toBe(1);
      expect(countFetch('geocoding.geo.census.gov')).toBe(1);
      expect(countFetch('api.census.gov')).toBe(1);

      await request(app).get('/api/public/place').query({ address: '1421 SE Oak St' });

      // Every external dependency is served from the in-memory cache the 2nd time.
      expect(geo.forwardGeocode.mock.calls.length).toBe(1);      // Mapbox (billed)
      expect(countFetch('hazards.fema.gov')).toBe(1);            // FEMA flood
      expect(countFetch('geocoding.geo.census.gov')).toBe(1);    // Census tract geocoder
      expect(countFetch('api.census.gov')).toBe(1);              // Census ACS
    });

    it('still returns correct data on the cached (second) request', async () => {
      const app = buildApp();
      await request(app).get('/api/public/place').query({ address: '1421 SE Oak St' });
      const res = await request(buildApp()).get('/api/public/place').query({ address: '1421 SE Oak St' });

      expect(res.body.status).toBe('ready');
      expect(res.body.free.flood).toMatchObject({ status: 'ready', zone: 'X' });
      expect(res.body.free.area).toMatchObject({ status: 'ready', median_year_built: 1985 });
      // Seeded count 5 is below the k-anon floor (10) — see the bucket table.
      expect(res.body.free.density.bucket).toBe('forming');
    });
  });

  describe('section-by-section degradation (independent)', () => {
    it('returns partial when FEMA is unavailable but Census is not', async () => {
      installFetch({ femaOk: false });
      const res = await request(buildApp()).get('/api/public/place').query({ address: '1421 SE Oak St' });

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('partial');
      expect(res.body.free.flood.status).toBe('unavailable');
      expect(res.body.free.area.status).toBe('ready');
      // Seeded count 5 is below the k-anon floor (10) — see the bucket table.
      expect(res.body.free.density.bucket).toBe('forming');
    });

    it('returns partial when the Census area teaser is unavailable', async () => {
      installFetch({ censusOk: false });
      const res = await request(buildApp()).get('/api/public/place').query({ address: '1421 SE Oak St' });

      expect(res.body.status).toBe('partial');
      expect(res.body.free.area.status).toBe('unavailable');
      expect(res.body.free.flood.status).toBe('ready');
    });

    it('keeps flood when ONLY the Census tract geocoder fails (independence)', async () => {
      // The exact regression the refactor fixes: a tract-geocoder outage must
      // not take flood down with it.
      installFetch({ geocoderOk: false });
      const res = await request(buildApp()).get('/api/public/place').query({ address: '1421 SE Oak St' });

      expect(res.body.status).toBe('partial');
      expect(res.body.free.flood.status).toBe('ready'); // flood survived
      expect(res.body.free.area.status).toBe('unavailable');
    });
  });

  describe('the two non-ready answers are two different answers', () => {
    // These tests used to PIN THE CONFLATION: every geocoder failure was
    // asserted to yield `unsupported_region`. The never-a-500 goal was
    // right; the geographic laundering was not. A rejected geocode is a
    // failure to READ the address, and telling a US resident during an
    // outage that the product is not for them is the same class of
    // falsehood the sibling routes were fixed for.
    it('a geocoder that throws is "could not place", not a geographic denial', async () => {
      geo.forwardGeocode.mockRejectedValue(new Error('No result for address'));
      const res = await request(buildApp()).get('/api/public/place').query({ address: '1421 SE Oak St' });

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('could_not_place');
      expect(res.body.message).toMatch(/city and state/i);
      expect(res.body.message).not.toMatch(/U\.S\.-only/i);
    });

    it('a geocoder that returns nothing is also "could not place"', async () => {
      geo.forwardGeocode.mockResolvedValue(null);
      const res = await request(buildApp()).get('/api/public/place').query({ address: 'asdfghjkl' });

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('could_not_place');
      expect(res.body.message).not.toMatch(/U\.S\.-only/i);
    });

    it('a point resolved OUTSIDE the US is the only geographic denial', async () => {
      // Valid lat/lng but in London — fails the US bounding-box guard.
      // This is the one case the "U.S.-only" copy is true for.
      geo.forwardGeocode.mockResolvedValue({ latitude: 51.5237, longitude: -0.1585, city: 'London', state: 'England' });
      const res = await request(buildApp()).get('/api/public/place').query({ address: '221B Baker St' });

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('unsupported_region');
      expect(res.body.message).toMatch(/U\.S\.-only/i);
      // A non-US lookup must not reach any external data source.
      expect(attomWasCalled()).toBe(false);
      expect(countFetch('hazards.fema.gov')).toBe(0);
      expect(countFetch('api.census.gov')).toBe(0);
    });

    it('neither answer is ever a 500', async () => {
      geo.forwardGeocode.mockRejectedValue(new Error('boom'));
      const res = await request(buildApp()).get('/api/public/place').query({ address: 'x' });
      expect(res.status).toBe(200);
    });

    it('does not cache a transient geocoder failure (a retry can still succeed)', async () => {
      geo.forwardGeocode.mockRejectedValue(new Error('boom'));
      const first = await request(buildApp()).get('/api/public/place').query({ address: 'somewhere' });
      expect(first.body.status).toBe('could_not_place');

      geo.forwardGeocode.mockResolvedValue({ ...PORTLAND });
      const second = await request(buildApp()).get('/api/public/place').query({ address: 'somewhere' });
      expect(second.body.status).toBe('ready');
      expect(getTable('NeighborhoodProfileCache')).toHaveLength(0);
    });
  });

  describe('input validation', () => {
    it('returns 400 when the address is missing', async () => {
      const res = await request(buildApp()).get('/api/public/place');
      expect(res.status).toBe(400);
      expect(res.body.error).toMatch(/address/i);
    });

    it('returns 400 when the address is blank', async () => {
      const res = await request(buildApp()).get('/api/public/place').query({ address: '   ' });
      expect(res.status).toBe(400);
    });
  });
});

// ── Wave 4: the money-first lead ─────────────────────────────
// The preview used to open with data tiles. It now leads with a real
// dollar figure when one is available for the address — the highest-
// converting address ask there is — without ATTOM, an account, or any
// persistence. The honesty rules are the point: every figure states its
// scope, a benchmark is never a quote, and nothing is invented.
describe('the money lead', () => {
  test('leads with the tract flood-premium band when one is warmed', async () => {
    seedTable('PlaceSectionCache', [{
      cache_key: 'tract:41051001902',
      section_id: '_nfip_tract',
      payload: { policy_count: 128, premium_p25: 480, premium_median: 760, premium_p75: 1240, window_months: 24, coverage: 'full' },
      fetched_at: '2026-08-01T00:00:00.000Z',
      expires_at: '2026-11-01T00:00:00.000Z',
    }]);

    const res = await request(buildApp()).get('/api/public/place').query({ address: '1421 SE Oak St' });
    expect(res.status).toBe(200);
    const lead = res.body.money_lead;
    expect(lead).toBeTruthy();
    expect(lead.kind).toBe('flood_premium');
    expect(lead.headline).toMatch(/\$480–\$1,240 a year/);
    // Scope stated, and never sold as a quote.
    expect(lead.scope).toBe('census tract');
    expect(lead.detail).toMatch(/not a quote/i);
    expect(lead.headline).not.toMatch(/your (home|policy|premium)/i);
  });

  test('falls back to the tiles rather than inventing a figure', async () => {
    // No NFIP benchmark and no HUD row for this county.
    const res = await request(buildApp()).get('/api/public/place').query({ address: '1421 SE Oak St' });
    expect(res.status).toBe(200);
    expect(res.body.money_lead).toBeNull();
    // The preview still works — the tiles carry it exactly as before.
    expect(res.body.free.flood).toBeTruthy();
    expect(res.body.free.density).toBeTruthy();
  });

  // ── Every figure in the rent lead must come off the HUD row ──
  //
  // HUD prices all but ~14 US counties at a SINGLE 2-bedroom number:
  // fmr_hi[2] === fmr_lo[2] in 3,209 of the 3,223 rows migration 158
  // seeds. The lead computed `Math.max(fmr_hi[2], lo * 1.2)`, so for
  // 99.6% of the country it rendered an upper bound HUD never published,
  // under a bare "HUD Fair Market Rents" attribution — on a branch whose
  // own comment promises the preview "falls back to the tiles rather
  // than inventing a number". The T1 dashboard section does extend a
  // single figure by 20%, but says so in the same sentence; the
  // anonymous lead had no such clause.
  test('a county HUD prices at ONE number is shown as one number', async () => {
    seedTable('HudFmr', [{
      county_fips: '41051', fiscal_year: 2026, county_name: 'Multnomah County', state_abbr: 'OR',
      area_name: 'Portland', fmr_lo: [1400, 1600, 1922, 2400, 2800], fmr_hi: [1400, 1600, 1922, 2400, 2800],
    }]);

    const res = await request(buildApp()).get('/api/public/place').query({ address: '1421 SE Oak St' });
    const lead = res.body.money_lead;
    expect(lead.kind).toBe('rent_band');
    expect(lead.low).toBe(1922);
    // 1922 * 1.2 = 2306 — a figure HUD never published.
    expect(lead.high).toBe(1922);
    expect(lead.headline).not.toContain('2,306');

    // Stronger and drift-proof: every dollar figure in the rendered copy
    // must appear somewhere in the HUD row it cites.
    const hudFigures = new Set([1400, 1600, 1922, 2400, 2800].map((n) => n.toLocaleString('en-US')));
    for (const shown of lead.headline.match(/\$[\d,]+/g) || []) {
      expect(hudFigures).toContain(shown.slice(1));
    }
  });

  test('a county HUD DOES publish a range for keeps HUD’s own upper figure', async () => {
    // Cumberland County, ME is one of the ~14. Its real high is 2130;
    // the old Math.max discarded that in favour of 1833 * 1.2 = 2200.
    seedTable('HudFmr', [{
      county_fips: '41051', fiscal_year: 2026, county_name: 'Cumberland County', state_abbr: 'ME',
      area_name: 'Portland', fmr_lo: [1400, 1600, 1833, 2400, 2800], fmr_hi: [1400, 1600, 2130, 2400, 2800],
    }]);

    const lead = (await request(buildApp()).get('/api/public/place').query({ address: '1421 SE Oak St' })).body.money_lead;
    expect(lead.low).toBe(1833);
    expect(lead.high).toBe(2130);
    expect(lead.headline).not.toContain('2,200');
  });

  test('an anonymous view does not take a slot in the NFIP warm queue', async () => {
    // The warm job pulls 3 tracts per run, 12 runs an hour, FIFO on the
    // pending lane. Letting drive-by previews enqueue put anonymous
    // traffic in front of tracts where someone actually lives — reads
    // never gate on expiry, so the visible effect is a benchmark that
    // quietly stops being refreshed rather than one that disappears.
    const res = await request(buildApp()).get('/api/public/place').query({ address: '1421 SE Oak St' });
    expect(res.status).toBe(200);

    const pending = getTable('PlaceSectionCache')
      .filter((r) => r.section_id === '_nfip_tract' && r.payload && r.payload.pending);
    expect(pending).toEqual([]);
  });

  test('a geocoder that cannot resolve the tract is called ONCE, not three times', async () => {
    // The census teaser and the money lead share one tract resolution.
    // Passing the resolved VALUE made `null` — "tried, could not place
    // it" — indistinguishable from "no hint given", so both consumers
    // re-resolved and a failing geocoder took three round trips per
    // request, at the exact moment it was least able to serve them.
    installFetch({ geocoderOk: false });
    const res = await request(buildApp()).get('/api/public/place').query({ address: '1421 SE Oak St' });
    expect(res.status).toBe(200);
    expect(countFetch('geocoding.geo.census.gov')).toBe(1);
  });

  test('never leaks a count below the density floor alongside the lead', async () => {
    const res = await request(buildApp()).get('/api/public/place').query({ address: '1421 SE Oak St' });
    expect(JSON.stringify(res.body)).not.toContain('verified_users_count');
    expect(res.body.free.density.bucket).toBeDefined();
  });
});

// Regression: the NFIP quantile returns a RAW OpenFEMA premium, which
// carries cents. Both native clients type money_lead.low/high as Int, so
// a fractional value fails the decode and takes the entire preview down.
test('the money lead is always whole dollars, even from fractional premiums', async () => {
  seedTable('PlaceSectionCache', [{
    cache_key: 'tract:41051001902',
    section_id: '_nfip_tract',
    payload: { policy_count: 128, premium_p25: 480.5, premium_median: 760.25, premium_p75: 1243.75, window_months: 24, coverage: 'full' },
    fetched_at: '2026-08-01T00:00:00.000Z',
    expires_at: '2026-11-01T00:00:00.000Z',
  }]);

  const res = await request(buildApp()).get('/api/public/place').query({ address: '1421 SE Oak St' });
  const lead = res.body.money_lead;
  expect(Number.isInteger(lead.low)).toBe(true);
  expect(Number.isInteger(lead.high)).toBe(true);
  // And the headline must not print a stray decimal.
  expect(lead.headline).not.toMatch(/\.\d/);
  expect(lead.headline).toMatch(/\$481–\$1,244 a year/);
});
