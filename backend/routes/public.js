const express = require('express');
const router = express.Router();
const supabaseAdmin = require('../config/supabaseAdmin');
const geo = require('../services/geo');
const {
  geocodeToTract,
  fetchCensusACS,
  fetchFloodZone,
} = require('../services/ai/neighborhoodProfileService');
const { encodeGeohash, encodeGeohash6 } = require('../utils/geohash');
const { GeoCache } = require('../utils/geoCache');

// ============================================================
// Public Preview Endpoints
// No authentication required. Returns sanitized data only —
// no user IDs, no exact addresses, no PII.
// ============================================================

// ── GET /api/public/gigs/:id ────────────────────────────────
router.get('/gigs/:id', async (req, res) => {
  try {
    const { id } = req.params;

    const { data: gig, error } = await supabaseAdmin
      .from('Gig')
      .select('id, title, description, category, price, price_max, city, state, status, created_at')
      .eq('id', id)
      .single();

    if (error || !gig) {
      return res.status(404).json({ error: 'Not found' });
    }

    const isExpired = gig.status === 'completed' || gig.status === 'cancelled' || gig.status === 'expired';

    res.json({
      id: gig.id,
      title: gig.title,
      description: isExpired ? null : (gig.description || '').slice(0, 300),
      category: gig.category,
      price_min: gig.price || null,
      price_max: gig.price_max || null,
      city: gig.city,
      state: gig.state,
      status: gig.status,
      is_expired: isExpired,
      created_at: gig.created_at,
    });
  } catch (err) {
    console.error('[public/gigs] Error:', err.message);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// ── GET /api/public/listings/:id ────────────────────────────
router.get('/listings/:id', async (req, res) => {
  try {
    const { id } = req.params;

    const { data: listing, error } = await supabaseAdmin
      .from('Listing')
      .select('id, title, description, price, currency, condition, city, state, status, photos, created_at')
      .eq('id', id)
      .single();

    if (error || !listing) {
      return res.status(404).json({ error: 'Not found' });
    }

    const isSold = listing.status === 'sold' || listing.status === 'removed' || listing.status === 'expired';

    // Only expose the first photo
    let photoUrl = null;
    if (listing.photos && Array.isArray(listing.photos) && listing.photos.length > 0) {
      const first = listing.photos[0];
      photoUrl = typeof first === 'string' ? first : first?.url || first?.uri || null;
    }

    res.json({
      id: listing.id,
      title: listing.title,
      description: isSold ? null : (listing.description || '').slice(0, 300),
      price: listing.price,
      currency: listing.currency || 'USD',
      condition: listing.condition,
      city: listing.city,
      state: listing.state,
      status: listing.status,
      is_sold: isSold,
      photo_url: photoUrl,
      created_at: listing.created_at,
    });
  } catch (err) {
    console.error('[public/listings] Error:', err.message);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// ── GET /api/public/posts/:id ───────────────────────────────
router.get('/posts/:id', async (req, res) => {
  try {
    const { id } = req.params;

    const { data: post, error } = await supabaseAdmin
      .from('Post')
      .select('id, title, content, post_type, city, state, visibility, created_at')
      .eq('id', id)
      .single();

    if (error || !post) {
      return res.status(404).json({ error: 'Not found' });
    }

    // Only expose publicly visible posts
    const publicVisibilities = ['public', 'neighborhood', 'city', 'global'];
    if (!publicVisibilities.includes(post.visibility)) {
      return res.status(404).json({ error: 'Not found' });
    }

    res.json({
      id: post.id,
      title: post.title,
      body: (post.content || '').slice(0, 200),
      post_type: post.post_type,
      city: post.city,
      state: post.state,
      created_at: post.created_at,
    });
  } catch (err) {
    console.error('[public/posts] Error:', err.message);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// ============================================================
// GET /api/public/place — anonymous T0 "Place" preview
//
// The signed-out, one-shot demonstration of "what's true about your
// address." Returns ONLY the free Band-A subset that proves the product:
//   • flood     — FEMA flood zone (area-level), via neighborhoodProfileService
//   • density   — verified-homes BUCKET enum only, NEVER a count (k-anon)
//   • area      — Census tract teaser (area medians), NOT an exact home record
// Everything recurring/exact (daily conditions, exact home value, health,
// money, civic) is returned as a *locked descriptor* so the client can render
// the locked cards and the soft-wall.
//
// Privacy (the §4 anti-leak rule):
//   • No auth.   • No ATTOM (no propertyDataService).   • No PII / exact home record.
//   • The PREVIEW persists nothing: no saved place, no per-user / per-address
//     row, NO DB writes at all — close + reopen still hits the wall.
//
// Caching (in-memory only, location-keyed, anonymous — never the user's
// preview, never the database):
//   • geocode → keyed by the typed address. Mapbox is the only billed
//     dependency, so this is the highest-value cache; the TTL is kept short to
//     respect Mapbox's "temporary result" terms.
//   • flood → keyed by a fine (~38m) geohash, fetched independently so it never
//     depends on the Census tract lookup.
//   • census teaser → keyed by a coarse (~1.2km) geohash (area-level by nature).
//   • density → read live (its own job refreshes it every 15 min).
// Each source degrades on its own; only positive results are cached. We call
// the stateless fetchers directly (not getProfile) so the preview neither
// writes the shared profile cache nor triggers the Walk Score dependency.
//
// Statuses: ready | partial | unsupported_region | rate_limited
//   • rate_limited is surfaced by previewLimiter as HTTP 429.
// ============================================================

// Density buckets are floored server-side from the raw verified-home count so
// the response can describe activity qualitatively without ever exposing a
// number (the §4.1 k-anon rule). Thresholds align with the preview-refresh
// milestone scale (first milestone = 10).
const DENSITY_THRESHOLDS = { growing: 10, few: 3, forming: 1 };
const DENSITY_LABELS = {
  growing: 'Growing activity near this area',
  few: 'A few verified homes nearby',
  forming: 'Your block is starting to form',
  none: 'No activity shown yet',
};

function densityBucket(count) {
  const n = Number.isFinite(count) ? count : 0;
  if (n >= DENSITY_THRESHOLDS.growing) return 'growing';
  if (n >= DENSITY_THRESHOLDS.few) return 'few';
  if (n >= DENSITY_THRESHOLDS.forming) return 'forming';
  return 'none';
}

// Everything outside the free demonstration subset, described so the client
// can render the locked cards + soft-wall. `unlock` is the tier that actually
// opens the section (account = T1, claim = T3); `band` is the §9 sensitivity.
const LOCKED_SECTIONS = [
  {
    id: 'daily_conditions', group: 'today', title: 'Daily conditions', band: 'A', unlock: 'account',
    reason: 'Create an account to get daily weather, air quality, and alerts — updated every day.',
  },
  {
    id: 'home_details', group: 'your_home', title: 'Home details & value', band: 'B', unlock: 'claim',
    reason: "Save this place to see your home's exact details and value.",
  },
  {
    id: 'health_environment', group: 'health_environment', title: 'Health & environment', band: 'A', unlock: 'account',
    reason: 'Create an account to see lead, radon, water, and nearby environmental records for your address.',
  },
  {
    id: 'money_signals', group: 'money_signals', title: 'Money signals', band: 'A', unlock: 'account',
    reason: 'Create an account to see bill benchmarks and rebates you may be eligible for.',
  },
  {
    id: 'civic', group: 'civic', title: 'Civic', band: 'A', unlock: 'account',
    reason: 'Create an account to see your voting districts, elections, and a residency letter.',
  },
];

// Coarse US bounding boxes (continental + AK + HI + PR/USVI). The geocoder is
// already locked to country=us, so a non-US address typically returns nothing;
// this is a string-free secondary guard against a fuzzy out-of-coverage hit.
function isLikelyUS(lat, lng) {
  if (lat >= 24.5 && lat <= 49.5 && lng >= -125 && lng <= -66.9) return true; // continental
  if (lat >= 51 && lat <= 71.6 && lng >= -179.5 && lng <= -129) return true;  // Alaska
  if (lat >= 18.9 && lat <= 22.3 && lng >= -160.3 && lng <= -154.7) return true; // Hawaii
  if (lat >= 17.6 && lat <= 18.6 && lng >= -67.4 && lng <= -64.5) return true; // PR / USVI
  return false;
}

function clip(value, max = 120) {
  if (typeof value !== 'string') return null;
  const trimmed = value.trim();
  if (!trimmed) return null;
  return trimmed.length > max ? trimmed.slice(0, max) : trimmed;
}

// Dedicated in-memory cache for the preview's free facts. Isolated from the
// shared mapbox-resolve cache (utils/geoCache singleton) so neither evicts the
// other. Holds only anonymous, location-keyed public facts — nothing per-user,
// nothing on disk or in the DB.
const previewCache = new GeoCache(5000);

// Geocode is kept short to respect Mapbox's "temporary result" terms; FEMA
// flood zones and Census tract data change rarely, so they live a day. Flood is
// keyed finely (zones can vary over ~100m near water); the Census teaser is
// area-level by nature, so a coarse key is correct.
const GEO_TTL_MS = 10 * 60 * 1000;       // 10 min — Mapbox temporary results
const AREA_TTL_MS = 24 * 60 * 60 * 1000; // 24 h  — FEMA / Census are stable
const FLOOD_GEOHASH_PRECISION = 8;       // ~38m

const geoKey = (address) => `geo:${address.toLowerCase().replace(/\s+/g, ' ')}`;

// Geocode an address to a US point via services/geo (country=us). Returns a
// sanitized, area-level place identity, or { ok: false } for anything we can't
// confidently place inside US coverage (→ unsupported_region, never a 500).
async function geocodeUsAddress(address) {
  const key = geoKey(address);
  const cached = previewCache.get(key);
  if (cached) return cached;

  let result;
  try {
    result = await geo.forwardGeocode(address);
  } catch (err) {
    // No-result and infra failures both land here. For an anonymous preview we
    // degrade gracefully rather than 500 — the address simply isn't placeable.
    // Failures are NOT cached: a retry must be able to reach the geocoder again.
    console.warn('[public/place] geocode failed:', err.message);
    return { ok: false };
  }

  if (!result) return { ok: false };

  const lat = Number(result.latitude);
  const lng = Number(result.longitude);
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return { ok: false };
  if (!isLikelyUS(lat, lng)) return { ok: false };

  const place = {
    ok: true,
    lat,
    lng,
    line: clip(result.address),
    city: clip(result.city),
    // The geo provider already uppercases the 2-letter US state code; don't
    // re-case it (that would turn a rare full-name fallback into "OREGON").
    state: clip(result.state, 24),
    zipcode: clip(result.zipcode, 12),
  };
  previewCache.set(key, place, GEO_TTL_MS);
  return place;
}

// Flood — FEMA zone for a point, fetched independently (only needs lat/lng, so
// it never depends on the Census tract lookup) and cached by a fine geohash.
// Only positive results are cached; a null could be a transient error.
async function fetchFloodCached(lat, lng) {
  const key = `flood:${encodeGeohash(lat, lng, FLOOD_GEOHASH_PRECISION)}`;
  const cached = previewCache.get(key);
  if (cached) return cached;

  const flood = await fetchFloodZone(lat, lng);
  if (flood && flood.flood_zone) previewCache.set(key, flood, AREA_TTL_MS);
  return flood;
}

// Census tract teaser — area-level medians only (NOT an exact home record,
// NOT ATTOM). Resolves the tract, then the ACS medians; cached by a coarse
// (area-level) geohash so the whole sub-pipeline is skipped on repeat.
async function fetchCensusTeaserCached(lat, lng) {
  const key = `census:${encodeGeohash6(lat, lng)}`;
  const cached = previewCache.get(key);
  if (cached) return cached;

  const tract = await geocodeToTract(lat, lng);
  if (!tract) return null;
  const { tractId, stateCode, countyCode } = tract;
  const tractCode = tractId.slice(stateCode.length + countyCode.length);
  const acs = await fetchCensusACS(stateCode, countyCode, tractCode);
  if (!acs) return null;

  const teaser = {
    median_year_built: acs.median_year_built ?? null,
    median_home_value: acs.median_home_value ?? null,
  };
  if (teaser.median_year_built != null || teaser.median_home_value != null) {
    previewCache.set(key, teaser, AREA_TTL_MS);
  }
  return teaser;
}

// Read the verified-homes count for the area and return ONLY its bucket.
// A read, never a write. Any failure degrades to 'none'.
async function readDensityBucket(geohash) {
  try {
    const { data, error } = await supabaseAdmin
      .from('NeighborhoodPreview')
      .select('verified_users_count')
      .eq('geohash', geohash)
      .maybeSingle();
    if (error) {
      console.warn('[public/place] density read error:', error.message);
      return 'none';
    }
    return densityBucket(data?.verified_users_count ?? 0);
  } catch (err) {
    console.warn('[public/place] density read exception:', err.message);
    return 'none';
  }
}

router.get('/place', async (req, res) => {
  try {
    const rawAddress = typeof req.query.address === 'string' ? req.query.address.trim() : '';
    if (!rawAddress) {
      return res.status(400).json({ error: 'An address query parameter is required.' });
    }
    if (rawAddress.length > 200) {
      return res.status(400).json({ error: 'That address is too long.' });
    }

    // 1. Geocode (services/geo, US-only). Non-US / ungeocodable → calm hand-off.
    const place = await geocodeUsAddress(rawAddress);
    if (!place.ok) {
      return res.json({
        status: 'unsupported_region',
        tier: 'preview',
        region: null,
        message: 'Home features are U.S.-only for now',
      });
    }

    const geohash = encodeGeohash6(place.lat, place.lng);

    // 2. The free demonstration subset only. Flood, the Census teaser, and the
    //    density bucket are fetched independently and cached in-memory; each
    //    degrades on its own, none persists the preview, none touches ATTOM.
    const [floodSettled, areaSettled, bucketSettled] = await Promise.allSettled([
      fetchFloodCached(place.lat, place.lng),
      fetchCensusTeaserCached(place.lat, place.lng),
      readDensityBucket(geohash),
    ]);

    const flood = floodSettled.status === 'fulfilled' ? floodSettled.value : null;
    const area = areaSettled.status === 'fulfilled' ? areaSettled.value : null;
    const bucket = bucketSettled.status === 'fulfilled' ? bucketSettled.value : 'none';

    const floodSection = flood && flood.flood_zone
      ? {
          status: 'ready',
          zone: flood.flood_zone,
          description: flood.flood_zone_description || null,
          source: 'FEMA National Flood Hazard Layer',
        }
      : { status: 'unavailable', source: 'FEMA National Flood Hazard Layer' };

    const areaSection = area && (area.median_year_built != null || area.median_home_value != null)
      ? {
          status: 'ready',
          median_year_built: area.median_year_built,
          median_home_value: area.median_home_value,
          note: 'Area-level, not your home',
          source: 'U.S. Census · American Community Survey',
        }
      : {
          status: 'unavailable',
          note: 'Area-level, not your home',
          source: 'U.S. Census · American Community Survey',
        };

    // Density always resolves (a bucket, even 'none'); it never gates ready.
    const densitySection = {
      status: 'ready',
      bucket,                       // enum only — NEVER a count
      label: DENSITY_LABELS[bucket],
      source: 'Pantopus verified neighbors',
    };

    const ready = floodSection.status === 'ready' && areaSection.status === 'ready';

    return res.json({
      status: ready ? 'ready' : 'partial',
      tier: 'preview',
      region: 'US',
      place: {
        address: place.line,
        city: place.city,
        state: place.state,
        zipcode: place.zipcode,
      },
      free: {
        flood: floodSection,
        density: densitySection,
        area: areaSection,
      },
      locked: LOCKED_SECTIONS,
      disclaimer: 'A free, one-time look. Create an account to save this place and get daily updates.',
    });
  } catch (err) {
    console.error('[public/place] Error:', err.message);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// ============================================================
// GET /api/public/residency-letters/:code — third-party letter check
//
// Anyone holding a residency letter can confirm it is genuine and not
// revoked. Returns exactly what is printed on the paper — never more.
// The mount-level previewLimiter (60/min/IP) plus the ~78-bit letter
// code keep enumeration impractical. Unknown/malformed codes are a
// uniform { valid: false } (no existence oracle).
// ============================================================
router.get('/residency-letters/:code', async (req, res) => {
  try {
    const residencyLetterService = require('../services/residencyLetterService');
    const result = await residencyLetterService.verifyByCode(req.params.code);
    return res.json(result);
  } catch (err) {
    console.error('[public/residency-letters] Error:', err.message);
    return res.status(500).json({ error: 'Verification failed. Try again.' });
  }
});

module.exports = router;
// Test-only hook: reset the in-memory preview caches between cases.
module.exports.__clearPreviewCaches = () => previewCache.clear();
