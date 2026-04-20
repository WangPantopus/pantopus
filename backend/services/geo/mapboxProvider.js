/**
 * MapboxProvider — GeoProvider implementation backed by Mapbox Geocoding v5.
 *
 * Autocomplete pre-parses structured address components from the Mapbox
 * response and caches them keyed by suggestion_id so that resolve() can
 * return a NormalizedAddress without a second Mapbox call.
 */

const logger = require('../../utils/logger');
const { geoCache } = require('../../utils/geoCache');
const { GEO_SERVER_TOKEN } = require('../../config/geo');

// ── Helpers ──────────────────────────────────────────────────

function requireToken() {
  if (!GEO_SERVER_TOKEN) throw new Error('Missing env var: MAPBOX_ACCESS_TOKEN');
  return GEO_SERVER_TOKEN;
}

/**
 * Extract a context field by id prefix from a Mapbox feature.
 */
function findCtx(context, prefix) {
  return (context || []).find((c) => (c.id || '').startsWith(prefix));
}

/**
 * Parse a Mapbox v5 feature into a NormalizedAddress.
 */
function featureToNormalized(f, source, mode) {
  const ctx = f.context || [];
  const postcode = findCtx(ctx, 'postcode')?.text || '';
  const place = findCtx(ctx, 'place')?.text || '';
  const region = findCtx(ctx, 'region')?.text || '';
  const regionShort = findCtx(ctx, 'region')?.short_code || '';

  const stateCode = regionShort?.includes('-')
    ? regionShort.split('-').pop().toUpperCase()
    : '';

  return {
    address: f.place_name || '',
    city: place,
    state: stateCode || region,
    zipcode: postcode,
    latitude: f.center ? f.center[1] : null,
    longitude: f.center ? f.center[0] : null,
    place_id: f.id || null,
    verified: false,
    source,
    geocode_mode: mode || 'temporary',
  };
}

/**
 * Determine the "kind" of a Mapbox feature from its place_type array.
 */
function featureKind(f) {
  const types = f.place_type || [];
  if (types.includes('address')) return 'address';
  if (types.includes('place')) return 'place';
  if (types.includes('locality')) return 'locality';
  if (types.includes('postcode')) return 'postcode';
  return types[0] || 'unknown';
}

/**
 * Build a display-friendly secondary text from context fields.
 */
function secondaryText(f) {
  const ctx = f.context || [];
  const place = findCtx(ctx, 'place')?.text || '';
  const regionShort = findCtx(ctx, 'region')?.short_code || '';
  const stateCode = regionShort?.includes('-')
    ? regionShort.split('-').pop().toUpperCase()
    : findCtx(ctx, 'region')?.text || '';
  const postcode = findCtx(ctx, 'postcode')?.text || '';

  return [place, stateCode, postcode].filter(Boolean).join(', ');
}

// ── Provider ─────────────────────────────────────────────────

// Cache key prefix for resolved suggestions (pre-parsed on autocomplete).
const RESOLVE_PREFIX = 'geo:resolve:';
const RESOLVE_TTL = 300_000; // 5 minutes

// TODO: Upgrade to Mapbox Search Box API v2 when access is available.
//       v2 provides structured address components natively and supports
//       session-based billing, reducing per-suggestion costs.

const mapboxProvider = {
  /**
   * @param {string} query
   * @param {{ sessionToken?: string, limit?: number, country?: string }} [options]
   * @returns {Promise<{ suggestions: import('./geoProvider').NormalizedSuggestion[] }>}
   */
  async autocomplete(query, options = {}) {
    const { limit = 6, country = 'us' } = options;
    const token = requireToken();

    const url =
      `https://api.mapbox.com/geocoding/v5/mapbox.places/${encodeURIComponent(query)}.json` +
      `?access_token=${encodeURIComponent(token)}` +
      `&autocomplete=true&limit=${limit}&country=${encodeURIComponent(country)}` +
      `&types=address,place,locality,postcode`;

    const r = await fetch(url);
    if (!r.ok) {
      const text = await r.text();
      logger.warn('mapbox_autocomplete_error', { status: r.status, body: text });
      throw new Error(`Mapbox autocomplete failed: ${r.status}`);
    }

    const data = await r.json();
    const features = data.features || [];

    const suggestions = features.map((f) => {
      const suggestionId = f.id;

      // Pre-parse and cache the normalized address so resolve() is free.
      const normalized = featureToNormalized(f, 'mapbox_geocode', 'temporary');
      geoCache.set(RESOLVE_PREFIX + suggestionId, normalized, RESOLVE_TTL);

      return {
        suggestion_id: suggestionId,
        primary_text: f.text || '',
        secondary_text: secondaryText(f),
        label: f.place_name || '',
        center: f.center
          ? { lat: f.center[1], lng: f.center[0] }
          : { lat: 0, lng: 0 },
        kind: featureKind(f),
      };
    });

    return { suggestions };
  },

  /**
   * @param {string} suggestionId
   * @param {string} [_sessionToken]
   * @returns {Promise<import('./geoProvider').NormalizedAddress>}
   */
  async resolve(suggestionId, _sessionToken) {
    // Check pre-parsed cache first (populated by autocomplete).
    const cached = geoCache.get(RESOLVE_PREFIX + suggestionId);
    if (cached) return cached;

    // Cache miss — fall back to forward geocode using the suggestion_id as a
    // Mapbox feature id lookup, or if that fails, treat it as text.
    logger.info('geo_resolve_cache_miss', { suggestion_id: suggestionId });

    const token = requireToken();
    const url =
      `https://api.mapbox.com/geocoding/v5/mapbox.places/${encodeURIComponent(suggestionId)}.json` +
      `?access_token=${encodeURIComponent(token)}` +
      `&limit=1&country=us&types=address,place,locality,postcode`;

    const r = await fetch(url);
    if (!r.ok) {
      const text = await r.text();
      logger.warn('mapbox_resolve_error', { status: r.status, body: text });
      throw new Error(`Mapbox resolve failed: ${r.status}`);
    }

    const data = await r.json();
    const f = (data.features || [])[0];
    if (!f) throw new Error('No result for suggestion_id');

    const normalized = featureToNormalized(f, 'mapbox_geocode', 'temporary');
    geoCache.set(RESOLVE_PREFIX + f.id, normalized, RESOLVE_TTL);
    return normalized;
  },

  /**
   * @param {number} lat
   * @param {number} lng
   * @returns {Promise<import('./geoProvider').NormalizedAddress>}
   */
  async reverseGeocode(lat, lng) {
    const token = requireToken();

    const url =
      `https://api.mapbox.com/geocoding/v5/mapbox.places/${encodeURIComponent(lng)},${encodeURIComponent(lat)}.json` +
      `?access_token=${encodeURIComponent(token)}` +
      `&limit=1&types=address,place,locality,postcode`;

    const r = await fetch(url);
    if (!r.ok) {
      const text = await r.text();
      logger.warn('mapbox_reverse_error', { status: r.status, body: text });
      throw new Error(`Mapbox reverse geocode failed: ${r.status}`);
    }

    const data = await r.json();
    const f = (data.features || [])[0];
    if (!f) throw new Error('No address found for that location');

    return featureToNormalized(f, 'mapbox_reverse', 'temporary');
  },

  /**
   * @param {string} address
   * @param {{ mode?: 'temporary'|'permanent' }} [options]
   * @returns {Promise<import('./geoProvider').NormalizedAddress>}
   */
  async forwardGeocode(address, options = {}) {
    const { mode = 'temporary' } = options;
    const token = requireToken();

    const url =
      `https://api.mapbox.com/geocoding/v5/mapbox.places/${encodeURIComponent(address)}.json` +
      `?access_token=${encodeURIComponent(token)}` +
      `&limit=1&country=us&types=address,place,locality,postcode`;

    const r = await fetch(url);
    if (!r.ok) {
      const text = await r.text();
      logger.warn('mapbox_forward_error', { status: r.status, body: text });
      throw new Error(`Mapbox forward geocode failed: ${r.status}`);
    }

    const data = await r.json();
    const f = (data.features || [])[0];
    if (!f) throw new Error('No result for address');

    return featureToNormalized(f, 'mapbox_geocode', mode);
  },
};

module.exports = mapboxProvider;
