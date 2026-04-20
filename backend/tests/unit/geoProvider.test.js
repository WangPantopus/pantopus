// ============================================================
// TEST: GeoProvider — MapboxProvider contract tests
//
// Verifies NormalizedSuggestion and NormalizedAddress shapes
// returned by the Mapbox provider. Mocks global fetch.
// ============================================================

jest.mock('../../utils/logger', () => ({
  info: jest.fn(),
  warn: jest.fn(),
  error: jest.fn(),
  debug: jest.fn(),
}));

const { geoCache } = require('../../utils/geoCache');

// ── Mapbox response fixtures ─────────────────────────────────

const MAPBOX_AUTOCOMPLETE_RESPONSE = {
  type: 'FeatureCollection',
  features: [
    {
      id: 'address.12345',
      type: 'Feature',
      place_type: ['address'],
      text: '123 Main St',
      place_name: '123 Main St, Portland, Oregon 97201, United States',
      center: [-122.6784, 45.5152],
      context: [
        { id: 'postcode.1001', text: '97201' },
        { id: 'place.2001', text: 'Portland' },
        { id: 'region.3001', text: 'Oregon', short_code: 'us-or' },
        { id: 'country.4001', text: 'United States', short_code: 'us' },
      ],
      properties: { accuracy: 'rooftop' },
    },
    {
      id: 'place.67890',
      type: 'Feature',
      place_type: ['place'],
      text: 'Portland',
      place_name: 'Portland, Oregon, United States',
      center: [-122.6765, 45.5231],
      context: [
        { id: 'region.3001', text: 'Oregon', short_code: 'us-or' },
        { id: 'country.4001', text: 'United States', short_code: 'us' },
      ],
      properties: {},
    },
  ],
};

const MAPBOX_REVERSE_RESPONSE = {
  type: 'FeatureCollection',
  features: [
    {
      id: 'address.99999',
      type: 'Feature',
      place_type: ['address'],
      text: '456 Oak Ave',
      place_name: '456 Oak Ave, Seattle, Washington 98101, United States',
      center: [-122.3321, 47.6062],
      context: [
        { id: 'postcode.5001', text: '98101' },
        { id: 'place.6001', text: 'Seattle' },
        { id: 'region.7001', text: 'Washington', short_code: 'us-wa' },
        { id: 'country.8001', text: 'United States', short_code: 'us' },
      ],
      properties: {},
    },
  ],
};

// ── Setup ────────────────────────────────────────────────────

let mapboxProvider;

beforeEach(() => {
  process.env.MAPBOX_ACCESS_TOKEN = 'test-token';
  geoCache.clear();
  jest.resetModules();

  global.fetch = jest.fn();
  mapboxProvider = require('../../services/geo/mapboxProvider');
});

afterEach(() => {
  delete process.env.MAPBOX_ACCESS_TOKEN;
  delete global.fetch;
});

// ── Helpers ──────────────────────────────────────────────────

function mockFetchOk(body) {
  global.fetch.mockResolvedValueOnce({
    ok: true,
    json: () => Promise.resolve(body),
  });
}

function mockFetchError(status, body) {
  global.fetch.mockResolvedValueOnce({
    ok: false,
    status,
    text: () => Promise.resolve(body || 'Error'),
  });
}

// ── NormalizedSuggestion shape validation ─────────────────────

function expectNormalizedSuggestion(s) {
  expect(s).toHaveProperty('suggestion_id');
  expect(s).toHaveProperty('primary_text');
  expect(s).toHaveProperty('secondary_text');
  expect(s).toHaveProperty('label');
  expect(s).toHaveProperty('center');
  expect(s).toHaveProperty('center.lat');
  expect(s).toHaveProperty('center.lng');
  expect(s).toHaveProperty('kind');
  expect(typeof s.suggestion_id).toBe('string');
  expect(typeof s.primary_text).toBe('string');
  expect(typeof s.secondary_text).toBe('string');
  expect(typeof s.label).toBe('string');
  expect(typeof s.center.lat).toBe('number');
  expect(typeof s.center.lng).toBe('number');
  expect(typeof s.kind).toBe('string');
}

function expectNormalizedAddress(n) {
  expect(n).toHaveProperty('address');
  expect(n).toHaveProperty('city');
  expect(n).toHaveProperty('state');
  expect(n).toHaveProperty('zipcode');
  expect(n).toHaveProperty('latitude');
  expect(n).toHaveProperty('longitude');
  expect(n).toHaveProperty('place_id');
  expect(n).toHaveProperty('verified', false);
  expect(n).toHaveProperty('source');
  expect(n).toHaveProperty('geocode_mode');
  expect(typeof n.address).toBe('string');
  expect(typeof n.city).toBe('string');
  expect(typeof n.state).toBe('string');
  expect(typeof n.zipcode).toBe('string');
  expect(['temporary', 'permanent']).toContain(n.geocode_mode);
}

// ── Tests ────────────────────────────────────────────────────

describe('MapboxProvider', () => {
  describe('autocomplete', () => {
    it('returns NormalizedSuggestion[] shape', async () => {
      mockFetchOk(MAPBOX_AUTOCOMPLETE_RESPONSE);

      const result = await mapboxProvider.autocomplete('123 Main St');

      expect(result).toHaveProperty('suggestions');
      expect(Array.isArray(result.suggestions)).toBe(true);
      expect(result.suggestions).toHaveLength(2);

      for (const s of result.suggestions) {
        expectNormalizedSuggestion(s);
      }
    });

    it('maps Mapbox fields to normalized fields correctly', async () => {
      mockFetchOk(MAPBOX_AUTOCOMPLETE_RESPONSE);

      const { suggestions } = await mapboxProvider.autocomplete('123 Main');
      const first = suggestions[0];

      expect(first.suggestion_id).toBe('address.12345');
      expect(first.primary_text).toBe('123 Main St');
      expect(first.label).toBe('123 Main St, Portland, Oregon 97201, United States');
      expect(first.center.lat).toBeCloseTo(45.5152);
      expect(first.center.lng).toBeCloseTo(-122.6784);
      expect(first.kind).toBe('address');
      expect(first.secondary_text).toContain('Portland');
      expect(first.secondary_text).toContain('OR');
    });

    it('returns empty suggestions for empty Mapbox response', async () => {
      mockFetchOk({ features: [] });

      const result = await mapboxProvider.autocomplete('nonexistent');
      expect(result.suggestions).toEqual([]);
    });

    it('throws on Mapbox HTTP error', async () => {
      mockFetchError(502, 'Bad Gateway');

      await expect(mapboxProvider.autocomplete('test'))
        .rejects.toThrow('Mapbox autocomplete failed: 502');
    });

    it('throws when MAPBOX_ACCESS_TOKEN is missing', async () => {
      delete process.env.MAPBOX_ACCESS_TOKEN;
      jest.resetModules();
      const freshProvider = require('../../services/geo/mapboxProvider');

      await expect(freshProvider.autocomplete('test'))
        .rejects.toThrow('Missing env var: MAPBOX_ACCESS_TOKEN');
    });

    it('passes limit and country options to URL', async () => {
      mockFetchOk({ features: [] });

      await mapboxProvider.autocomplete('test', { limit: 3, country: 'ca' });

      const url = global.fetch.mock.calls[0][0];
      expect(url).toContain('limit=3');
      expect(url).toContain('country=ca');
    });
  });

  describe('resolve', () => {
    it('returns NormalizedAddress shape from cache (after autocomplete)', async () => {
      mockFetchOk(MAPBOX_AUTOCOMPLETE_RESPONSE);

      // Autocomplete pre-populates the resolve cache
      await mapboxProvider.autocomplete('123 Main St');

      // Resolve should return from cache — no additional fetch
      const normalized = await mapboxProvider.resolve('address.12345');

      expectNormalizedAddress(normalized);
      // Only one fetch call (the autocomplete), no second call for resolve
      expect(global.fetch).toHaveBeenCalledTimes(1);
    });

    it('returns correct address fields from cached resolve', async () => {
      mockFetchOk(MAPBOX_AUTOCOMPLETE_RESPONSE);
      await mapboxProvider.autocomplete('123 Main St');

      const n = await mapboxProvider.resolve('address.12345');

      expect(n.address).toBe('123 Main St, Portland, Oregon 97201, United States');
      expect(n.city).toBe('Portland');
      expect(n.state).toBe('OR');
      expect(n.zipcode).toBe('97201');
      expect(n.latitude).toBeCloseTo(45.5152);
      expect(n.longitude).toBeCloseTo(-122.6784);
      expect(n.place_id).toBe('address.12345');
      expect(n.source).toBe('mapbox_geocode');
    });

    it('falls back to Mapbox API on cache miss', async () => {
      mockFetchOk({
        features: [MAPBOX_AUTOCOMPLETE_RESPONSE.features[0]],
      });

      const normalized = await mapboxProvider.resolve('address.12345');

      expectNormalizedAddress(normalized);
      expect(global.fetch).toHaveBeenCalledTimes(1);
    });

    it('throws when no result found on cache miss', async () => {
      mockFetchOk({ features: [] });

      await expect(mapboxProvider.resolve('nonexistent'))
        .rejects.toThrow('No result for suggestion_id');
    });
  });

  describe('reverseGeocode', () => {
    it('returns NormalizedAddress shape', async () => {
      mockFetchOk(MAPBOX_REVERSE_RESPONSE);

      const normalized = await mapboxProvider.reverseGeocode(47.6062, -122.3321);

      expectNormalizedAddress(normalized);
    });

    it('maps reverse geocode fields correctly', async () => {
      mockFetchOk(MAPBOX_REVERSE_RESPONSE);

      const n = await mapboxProvider.reverseGeocode(47.6062, -122.3321);

      expect(n.city).toBe('Seattle');
      expect(n.state).toBe('WA');
      expect(n.zipcode).toBe('98101');
      expect(n.source).toBe('mapbox_reverse');
    });

    it('throws when no address found', async () => {
      mockFetchOk({ features: [] });

      await expect(mapboxProvider.reverseGeocode(0, 0))
        .rejects.toThrow('No address found for that location');
    });

    it('throws on Mapbox HTTP error', async () => {
      mockFetchError(500, 'Internal Server Error');

      await expect(mapboxProvider.reverseGeocode(47.6, -122.3))
        .rejects.toThrow('Mapbox reverse geocode failed: 500');
    });
  });

  describe('forwardGeocode', () => {
    it('returns NormalizedAddress with specified mode', async () => {
      mockFetchOk({
        features: [MAPBOX_AUTOCOMPLETE_RESPONSE.features[0]],
      });

      const n = await mapboxProvider.forwardGeocode('123 Main St Portland', { mode: 'permanent' });

      expectNormalizedAddress(n);
      expect(n.geocode_mode).toBe('permanent');
    });

    it('defaults to temporary mode', async () => {
      mockFetchOk({
        features: [MAPBOX_AUTOCOMPLETE_RESPONSE.features[0]],
      });

      const n = await mapboxProvider.forwardGeocode('123 Main St Portland');

      expect(n.geocode_mode).toBe('temporary');
    });

    it('throws when no result found', async () => {
      mockFetchOk({ features: [] });

      await expect(mapboxProvider.forwardGeocode('nonexistent'))
        .rejects.toThrow('No result for address');
    });
  });
});
