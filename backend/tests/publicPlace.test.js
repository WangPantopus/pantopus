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
 *   • a non-US / ungeocodable address degrades to `unsupported_region` with
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
      expect(density.bucket).toBe('few'); // seeded count = 5
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
    const cases = [
      [0, 'none'],
      [1, 'forming'],
      [2, 'forming'],
      [3, 'few'],
      [9, 'few'],
      [10, 'growing'],
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
      const res = await request(app).get('/api/public/place').query({ address: '1421 SE Oak St' });

      expect(res.body.status).toBe('ready');
      expect(res.body.free.flood).toMatchObject({ status: 'ready', zone: 'X' });
      expect(res.body.free.area).toMatchObject({ status: 'ready', median_year_built: 1985 });
      expect(res.body.free.density.bucket).toBe('few');
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
      expect(res.body.free.density.bucket).toBe('few');
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

  describe('unsupported_region — never a 500', () => {
    it('handles a non-US address (geocoder returns nothing → throws)', async () => {
      geo.forwardGeocode.mockRejectedValue(new Error('No result for address'));
      const res = await request(buildApp()).get('/api/public/place').query({ address: '221B Baker St, London' });

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('unsupported_region');
      expect(res.body.region).toBeNull();
      expect(res.body.message).toMatch(/U\.S\.-only/i);
    });

    it('handles an ungeocodable address (no result object)', async () => {
      geo.forwardGeocode.mockResolvedValue(null);
      const res = await request(buildApp()).get('/api/public/place').query({ address: 'asdfghjkl' });

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('unsupported_region');
    });

    it('handles a resolved point outside US coverage', async () => {
      // Valid lat/lng but in London — fails the US bounding-box guard.
      geo.forwardGeocode.mockResolvedValue({ latitude: 51.5237, longitude: -0.1585, city: 'London', state: 'England' });
      const res = await request(buildApp()).get('/api/public/place').query({ address: '221B Baker St' });

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('unsupported_region');
      // A non-US lookup must not reach any external data source.
      expect(attomWasCalled()).toBe(false);
      expect(countFetch('hazards.fema.gov')).toBe(0);
      expect(countFetch('api.census.gov')).toBe(0);
    });

    it('does not cache a transient geocoder failure (a retry can still succeed)', async () => {
      geo.forwardGeocode.mockRejectedValue(new Error('boom'));
      const first = await request(buildApp()).get('/api/public/place').query({ address: 'somewhere' });
      expect(first.body.status).toBe('unsupported_region');

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
