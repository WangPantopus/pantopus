/**
 * Tests for GET /api/public/place — the anonymous T0 "Place" preview.
 *
 * Asserts the W0.3 contract + its constraints:
 *   • density is a BUCKET enum, never a raw count;
 *   • never calls ATTOM (no attomdata.com fetch, no propertyDataService);
 *   • the PREVIEW persists no user state (no SavedPlace / Home / per-address
 *     row); the only thing written is the shared, anonymous, tract-keyed
 *     public-data cache (NeighborhoodProfileCache) — no address / PII / user id;
 *   • a non-US / ungeocodable address degrades to `unsupported_region` with
 *     HTTP 200 — never a 500;
 *   • repeat requests are served from cache (geocode in-memory; flood + census
 *     from the tract cache).
 *
 * The geocoder (services/geo) is mocked; the data fetchers run for real against
 * a URL-routed global.fetch mock so we can prove no ATTOM URL is hit and that
 * caching eliminates the second FEMA/Census round-trip. supabaseAdmin is the
 * project's in-memory mock (seedTable / getTable); geoCache is the real
 * in-memory LRU, cleared between tests.
 */

const express = require('express');
const request = require('supertest');
const { resetTables, seedTable, getTable } = require('./__mocks__/supabaseAdmin');
const { geoCache } = require('../utils/geoCache');

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

function mockResp(data, ok = true) {
  return {
    ok,
    status: ok ? 200 : 500,
    json: () => Promise.resolve(data),
    text: () => Promise.resolve(JSON.stringify(data)),
  };
}

// URL-routed fetch so the real Census/FEMA fetchers resolve. `femaOk`/`censusOk`
// let a test simulate an outage (→ partial). An ATTOM URL would resolve too, so
// the "no ATTOM" assertion proves intent, not a mock-induced failure.
function installFetch({ femaOk = true, censusOk = true, geocoderOk = true } = {}) {
  global.fetch = jest.fn((url) => {
    const u = String(url);
    if (u.includes('attomdata.com')) return Promise.resolve(mockResp({ status: { msg: 'SuccessWithResult' }, property: [] }));
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
  geoCache.clear();
  jest.clearAllMocks();
  // WalkScore/Census keys off → deterministic external calls (no walkscore fetch).
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

  describe('no ATTOM, no preview persistence', () => {
    it('never calls ATTOM', async () => {
      await request(buildApp()).get('/api/public/place').query({ address: '1421 SE Oak St' });
      expect(attomWasCalled()).toBe(false);
      expect(propertyDataService.verifyPropertyOwnership).not.toHaveBeenCalled();
    });

    it('persists no user/preview state — only an anonymous, tract-keyed cache', async () => {
      await request(buildApp()).get('/api/public/place').query({ address: '1421 SE Oak St' });

      // The preview itself is never persisted: no saved place, no home record.
      expect(getTable('SavedPlace')).toHaveLength(0);
      expect(getTable('Home')).toHaveLength(0);

      // The only write is the shared public-data cache, keyed by Census tract,
      // holding anonymous area facts — no address, no PII, no user id.
      const cache = getTable('NeighborhoodProfileCache');
      expect(cache).toHaveLength(1);
      const row = cache[0];
      expect(row.tract_id).toBe('41051001902');
      expect(row).not.toHaveProperty('user_id');
      expect(row).not.toHaveProperty('address');
      const serialized = JSON.stringify(row);
      expect(serialized).not.toMatch(/Oak|1421/i); // no street address echoed into the cache
    });
  });

  describe('caching', () => {
    it('serves a repeat request from cache (no second Mapbox / FEMA / Census-ACS hit)', async () => {
      const app = buildApp();

      await request(app).get('/api/public/place').query({ address: '1421 SE Oak St' });
      const geocodeCalls = geo.forwardGeocode.mock.calls.length;
      const femaCalls = countFetch('hazards.fema.gov');
      const acsCalls = countFetch('api.census.gov');
      expect(geocodeCalls).toBe(1);
      expect(femaCalls).toBe(1);
      expect(acsCalls).toBe(1);

      await request(app).get('/api/public/place').query({ address: '1421 SE Oak St' });

      // Mapbox geocode (the billed call) is served from the in-memory cache.
      expect(geo.forwardGeocode.mock.calls.length).toBe(1);
      // Flood + Census are served from the tract cache — no new round-trips.
      expect(countFetch('hazards.fema.gov')).toBe(1);
      expect(countFetch('api.census.gov')).toBe(1);
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

  describe('section-by-section degradation', () => {
    it('returns partial when FEMA is unavailable but Census is not', async () => {
      installFetch({ femaOk: false });
      const res = await request(buildApp()).get('/api/public/place').query({ address: '1421 SE Oak St' });

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('partial');
      expect(res.body.free.flood.status).toBe('unavailable');
      expect(res.body.free.area.status).toBe('ready');
      // density still resolves
      expect(res.body.free.density.bucket).toBe('few');
    });

    it('returns partial when the Census area teaser is unavailable', async () => {
      installFetch({ censusOk: false });
      const res = await request(buildApp()).get('/api/public/place').query({ address: '1421 SE Oak St' });

      expect(res.body.status).toBe('partial');
      expect(res.body.free.area.status).toBe('unavailable');
      expect(res.body.free.flood.status).toBe('ready');
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

    it('does not 500 — and does not cache — a transient geocoder failure', async () => {
      geo.forwardGeocode.mockRejectedValue(new Error('boom'));
      await request(buildApp()).get('/api/public/place').query({ address: 'somewhere' });
      // Failures are never cached (a retry must be able to hit the geocoder again).
      geo.forwardGeocode.mockResolvedValue({ ...PORTLAND });
      const res = await request(buildApp()).get('/api/public/place').query({ address: 'somewhere' });
      expect(res.body.status).not.toBe('unsupported_region');
      expect(getTable('NeighborhoodProfileCache').length).toBeLessThanOrEqual(1);
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
