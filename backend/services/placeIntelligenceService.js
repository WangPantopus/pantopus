// ============================================================
// PLACE INTELLIGENCE SERVICE
//
// Composes the PlaceIntelligence section-envelope contract (W0.1) for
// a claimed home by REUSING existing data services — it does not
// re-implement any provider fetching:
//   Today              providerOrchestrator.getHubToday (weather/AQI/alerts)
//   Risk & Readiness   neighborhoodProfileService.getProfile (FEMA flood)
//   Your Block         neighborhoodProfileService (Census tract context)
//                      + NeighborhoodPreview table (k-anon density bucket)
//   Your Home (Band B) propertyIntelligenceService.getProfile (ATTOM) —
//                      only when ATTOM_API_KEY is configured, else
//                      `unavailable` (never a 500)
//   Money Signals      BillBenchmark table (peer comparison)
//
// Every section degrades INDEPENDENTLY: a thrown/failed source yields
// that section's `error`/`unavailable` status — it never fails the
// whole response. Bands are respected: Band B/C/D are gated by tier
// (derived from the caller's home access), Band A is always visible.
//
// See docs/design/place + the product design doc §8.2 / §8.3 / §9.
// ============================================================

const supabaseAdmin = require('../config/supabaseAdmin');
const logger = require('../utils/logger');
const { encodeGeohash } = require('../utils/geohash');
const {
  PLACE_SECTION_IDS,
  PLACE_SECTION_META,
  serializePlaceSection,
  serializePlaceIntelligence,
} = require('../serializers/placeIntelligenceSerializer');

const providerOrchestrator = require('./context/providerOrchestrator');
const neighborhoodProfileService = require('./ai/neighborhoodProfileService');
const propertyIntelligenceService = require('./ai/propertyIntelligenceService');
const placeSectionAdapters = require('./placeSectionAdapters');
const { getHomePrivacy } = require('./homePrivacyService');
const { buildGoodDayTiles } = require('./goodDayEngine');
const { getDensityBucket } = require('./place/densityReader');
const { getSystemsLedger } = require('./homeSystemsService');

const HOME_SELECT =
  'id, owner_id, address, address2, city, state, zipcode, map_center_lat, map_center_lng, year_built, sq_ft, bedrooms, bathrooms, lot_sq_ft, home_type';

// k-anon density bucket labels (mirror @pantopus/types PLACE_DENSITY_LABELS).
const DENSITY_LABELS = {
  none: 'No activity shown yet',
  forming: 'Your block is starting to form',
  few: 'A few verified homes nearby',
  growing: 'Growing activity near this area',
};

// ── Trust tier from the caller's home access (§3) ────────────
// The endpoint requires home access, so the caller is claimed (T3+);
// a verified occupancy lifts them to T4.
function resolveTier(access) {
  if (!access || !access.hasAccess) return 'T1';
  const vs = access.occupancy && access.occupancy.verification_status;
  if (vs === 'verified') return 'T4';
  return 'T3';
}

// ── Band × tier → section access (§9.2) ──────────────────────
function bandAccess(band, tier) {
  if (band === 'A') return 'available';
  if (band === 'B' || band === 'C') return tier === 'T3' || tier === 'T4' ? 'available' : 'locked';
  if (band === 'D') return tier === 'T4' ? 'available' : 'locked';
  return 'locked';
}

// ── Location → geohash-6 (matches NeighborhoodPreview / BillBenchmark) ──
function homeLatLng(home) {
  const lat = Number(home.map_center_lat);
  const lng = Number(home.map_center_lng);
  if (Number.isFinite(lat) && Number.isFinite(lng)) return { lat, lng };
  return null;
}

// ── Provider value → contract union mappers (best-effort) ────
const CONDITION_CODES = new Set([
  'clear', 'partly_cloudy', 'cloudy', 'fog', 'rain', 'snow', 'sleet', 'thunderstorm', 'wind',
]);
function mapConditionCode(code) {
  const c = String(code || '').toLowerCase();
  if (CONDITION_CODES.has(c)) return c;
  if (c.includes('thunder') || c.includes('storm')) return 'thunderstorm';
  if (c.includes('snow')) return 'snow';
  if (c.includes('sleet') || c.includes('hail')) return 'sleet';
  if (c.includes('rain') || c.includes('drizzle') || c.includes('shower')) return 'rain';
  if (c.includes('fog') || c.includes('mist') || c.includes('haze')) return 'fog';
  if (c.includes('partly') || c.includes('mostly_sunny') || c.includes('mostly_clear')) return 'partly_cloudy';
  if (c.includes('cloud') || c.includes('overcast')) return 'cloudy';
  if (c.includes('wind')) return 'wind';
  if (c.includes('clear') || c.includes('sun') || c.includes('fair')) return 'clear';
  return 'clear';
}

function mapAqiCategory(category) {
  const c = String(category || '').toLowerCase();
  if (c.includes('hazard')) return 'hazardous';
  if (c.includes('very')) return 'very_unhealthy';
  if (c.includes('sensitive')) return 'unhealthy_sensitive';
  if (c.includes('unhealthy')) return 'unhealthy';
  if (c.includes('moderate')) return 'moderate';
  if (c.includes('good')) return 'good';
  return 'good';
}

const AQI_CATEGORY_LABELS = {
  good: 'Good',
  moderate: 'Moderate',
  unhealthy_sensitive: 'Unhealthy for sensitive groups',
  unhealthy: 'Unhealthy',
  very_unhealthy: 'Very unhealthy',
  hazardous: 'Hazardous',
};
const AQI_HEALTH_MESSAGES = {
  good: 'Air quality is good. A fine day to be active outdoors.',
  moderate: 'Air quality is acceptable. Unusually sensitive people should consider limiting prolonged exertion.',
  unhealthy_sensitive: 'Sensitive groups should limit time outdoors. It is fine for most people.',
  unhealthy: 'Limit time outdoors, especially if you are sensitive.',
  very_unhealthy: 'Avoid prolonged time outdoors.',
  hazardous: 'Stay indoors and keep windows closed where possible.',
};

function mapAlertSeverity(sev) {
  const s = String(sev || '').toLowerCase();
  if (s.includes('warn') || s.includes('severe') || s.includes('extreme')) return 'warning';
  if (s.includes('watch')) return 'watch';
  return 'advisory';
}

// FEMA zone → qualitative risk.
function floodInSfha(zone) {
  const z = String(zone || '').toUpperCase();
  return z.startsWith('A') || z.startsWith('V');
}
function floodRiskLevel(zone) {
  const z = String(zone || '').toUpperCase();
  if (floodInSfha(z)) return 'high';
  if (z.includes('0.2') || z.includes('X500') || z.includes('SHADED')) return 'moderate';
  return 'minimal';
}


function censusSummary(profile) {
  const parts = [];
  if (profile.median_year_built) parts.push(`most homes here were built around ${profile.median_year_built}`);
  if (profile.median_home_value) {
    parts.push(`the typical one is valued near $${Math.round(profile.median_home_value).toLocaleString('en-US')}`);
  }
  if (!parts.length) return 'Census tract context for your area.';
  return `${parts.join(', and ')}.`.replace(/^./, (m) => m.toUpperCase());
}

// ════════════════════════════════════════════════════════════
// Section composers — each returns an array of serialized
// envelopes and NEVER throws (failures degrade to a per-section
// error/unavailable status).
// ════════════════════════════════════════════════════════════

// Number(null) and Number('') are both 0, which is finite — so a plain
// Number.isFinite check silently turns a missing reading into a real 0°F.
// Anything not already a usable number becomes null here.
function finiteNumber(value) {
  if (value === null || value === undefined || value === '') return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

// The contract's hourly strip wants { time, temp_f, condition_code,
// precip_chance }; the provider speaks { datetime_utc, temp_f,
// condition_code, precip_chance_pct }. Rows without a usable timestamp or
// temperature are dropped rather than rendered as gaps in the strip.
function mapWeatherHours(hourly) {
  if (!Array.isArray(hourly)) return [];
  const out = [];
  for (const h of hourly) {
    if (!h || !h.datetime_utc) continue;
    const tempF = finiteNumber(h.temp_f);
    if (tempF === null) continue;
    out.push({
      time: h.datetime_utc,
      temp_f: tempF,
      condition_code: mapConditionCode(h.condition_code),
      precip_chance: finiteNumber(h.precip_chance_pct) ?? 0,
    });
  }
  return out;
}

// The contract types high_f/low_f as non-null numbers, so a day missing
// either bound is dropped rather than emitted with a hole in it.
function mapWeatherDays(daily) {
  if (!Array.isArray(daily)) return [];
  const out = [];
  for (const d of daily) {
    if (!d || !d.date) continue;
    const highF = finiteNumber(d.high_f);
    const lowF = finiteNumber(d.low_f);
    if (highF === null || lowF === null) continue;
    out.push({
      date: d.date,
      condition_code: mapConditionCode(d.condition_code),
      high_f: highF,
      low_f: lowF,
      precip_chance: finiteNumber(d.precip_chance_pct) ?? 0,
    });
  }
  return out;
}

// AirNow reports the pollutant as a display name ("PM2.5", "O3"); the
// contract wants a stable machine token.
const POLLUTANT_TOKENS = {
  'pm2.5': 'pm25',
  pm25: 'pm25',
  pm10: 'pm10',
  o3: 'ozone',
  ozone: 'ozone',
  no2: 'no2',
  so2: 'so2',
  co: 'co',
};
function mapDominantPollutant(pollutant) {
  const raw = String(pollutant || '').trim().toLowerCase();
  if (!raw) return null;
  return POLLUTANT_TOKENS[raw] || raw.replace(/[^a-z0-9]+/g, '');
}

// NOAA carries both a description (≤500 chars) and a protective-action
// instruction (≤300). The instruction is the actionable half, so it is
// kept rather than dropped — but never at the cost of the description.
function alertBody(a) {
  const description = String(a.description || '').trim();
  const instruction = String(a.instruction || '').trim();
  if (description && instruction) return `${description}\n\n${instruction}`;
  return description || instruction || String(a.title || '');
}

// The verdict engine wants the provider's richer hourly rows — wind and,
// where the provider supplies it, per-hour feels-like — which the narrower
// contract strip drops. Same source array, one extra projection.
function engineHours(hourly) {
  if (!Array.isArray(hourly)) return [];
  const out = [];
  for (const h of hourly) {
    if (!h || !h.datetime_utc) continue;
    const tempF = finiteNumber(h.temp_f);
    if (tempF === null) continue;
    out.push({
      time: h.datetime_utc,
      temp_f: tempF,
      feels_like_f: finiteNumber(h.feels_like_f),
      wind_mph: finiteNumber(h.wind_mph),
      precip_chance: finiteNumber(h.precip_chance_pct) ?? 0,
    });
  }
  return out;
}

async function composeToday(userId, home) {
  let hub = null;
  try {
    // `detail` carries the forecast arrays, feels-like, the dominant
    // pollutant and the real alert body — all already fetched upstream.
    hub = await providerOrchestrator.getHubToday(userId, { detail: true });
  } catch (err) {
    logger.warn('placeIntelligence: getHubToday failed', { userId, error: err.message });
  }
  const asOf = (hub && hub.fetched_at) || null;
  const out = [];

  if (hub && hub.weather) {
    out.push(serializePlaceSection('weather', {
      access: 'available',
      asOf,
      data: {
        current_temp_f: hub.weather.current_temp_f,
        condition_code: mapConditionCode(hub.weather.condition_code),
        condition_label: hub.weather.condition_label,
        feels_like_f: hub.weather.feels_like_f ?? null,
        high_f: hub.weather.high_f,
        low_f: hub.weather.low_f,
        hourly: mapWeatherHours(hub.weather.hourly),
        daily: mapWeatherDays(hub.weather.daily),
      },
    }));
  } else {
    out.push(serializePlaceSection('weather', { access: 'available', status: 'unavailable' }));
  }

  if (hub && hub.aqi) {
    const category = mapAqiCategory(hub.aqi.category);
    out.push(serializePlaceSection('air_quality', {
      access: 'available',
      asOf,
      data: {
        index: hub.aqi.index,
        category,
        category_label: hub.aqi.category || AQI_CATEGORY_LABELS[category],
        dominant_pollutant: mapDominantPollutant(hub.aqi.dominant_pollutant),
        health_message: AQI_HEALTH_MESSAGES[category],
      },
    }));
  } else {
    out.push(serializePlaceSection('air_quality', { access: 'available', status: 'unavailable' }));
  }

  if (hub) {
    const active = (hub.alerts || []).map((a) => ({
      id: String(a.id),
      event: a.title,
      severity: mapAlertSeverity(a.severity),
      headline: a.headline || a.title,
      description: alertBody(a),
      onset: a.starts_at || null,
      ends: a.ends_at || null,
    }));
    // An empty list is still "ready" — the UI renders "No active alerts".
    out.push(serializePlaceSection('alerts', { access: 'available', asOf, status: 'ready', data: { active } }));
  } else {
    out.push(serializePlaceSection('alerts', { access: 'available', status: 'unavailable' }));
  }

  // Verdicts, derived from what the two sections above already fetched —
  // no additional provider call. Omitted entirely when the forecast is
  // thin enough that every answer would be a guess.
  const goodDay = hub && hub.weather
    ? buildGoodDayTiles({
      // The engine reads the provider's richer hourly rows (wind, and
      // feels-like where the provider supplies it), not the narrower
      // contract shape.
      weather: {
        hourly: engineHours(hub.weather.hourly),
        daily: mapWeatherDays(hub.weather.daily),
      },
      aqi: hub.aqi
        ? {
          index: hub.aqi.index,
          category_label: hub.aqi.category || AQI_CATEGORY_LABELS[mapAqiCategory(hub.aqi.category)],
        }
        : null,
      timezone: (hub.location && hub.location.timezone) || null,
      homeType: home && home.home_type,
    })
    : null;

  if (goodDay) {
    out.push(serializePlaceSection('good_day_to', { access: 'available', asOf, data: goodDay }));
  } else {
    out.push(serializePlaceSection('good_day_to', { access: 'available', status: 'unavailable' }));
  }

  return out;
}

// heat_cold needs the same temperature forecast the Today group fetches.
// getHubToday is memoised per (user, shape) for 2 minutes, so asking again
// inside one request is free rather than a second provider round-trip.
async function composeHeatCold(home, userId) {
  let hub = null;
  try {
    hub = await providerOrchestrator.getHubToday(userId, { detail: true });
  } catch (err) {
    logger.warn('placeIntelligence: getHubToday failed for heat_cold', { userId, error: err.message });
  }

  const weather = hub && hub.weather
    ? {
      hourly: mapWeatherHours(hub.weather.hourly),
      daily: mapWeatherDays(hub.weather.daily),
      timezone: (hub.location && hub.location.timezone) || null,
    }
    : null;

  return placeSectionAdapters.composeHeatCold(home, weather);
}

async function composeNeighborhood(home) {
  const ll = homeLatLng(home);
  let profile = null;
  if (ll) {
    try {
      const result = await neighborhoodProfileService.getProfile({
        latitude: ll.lat,
        longitude: ll.lng,
        address: home.address || '',
      });
      profile = result && result.profile;
    } catch (err) {
      logger.warn('placeIntelligence: neighborhoodProfile failed', { homeId: home.id, error: err.message });
    }
  }
  const out = [];

  if (profile && profile.flood_zone) {
    out.push(serializePlaceSection('flood', {
      access: 'available',
      asOf: profile.cached_at || null,
      data: {
        zone: profile.flood_zone,
        zone_label: `Zone ${profile.flood_zone}`,
        risk_level: floodRiskLevel(profile.flood_zone),
        in_sfha: floodInSfha(profile.flood_zone),
        insurance_required: floodInSfha(profile.flood_zone),
        plain_meaning: profile.flood_zone_description || '',
      },
    }));
  } else {
    out.push(serializePlaceSection('flood', { access: 'available', status: 'unavailable' }));
  }

  if (profile && (profile.median_year_built != null || profile.median_home_value != null)) {
    out.push(serializePlaceSection('census_context', {
      access: 'available',
      asOf: profile.cached_at || null,
      data: {
        median_year_built: profile.median_year_built ?? null,
        median_home_value: profile.median_home_value ?? null,
        tract_name: profile.tract_id ? `Census tract ${profile.tract_id}` : null,
        summary: censusSummary(profile),
      },
    }));
  } else {
    out.push(serializePlaceSection('census_context', { access: 'available', status: 'unavailable' }));
  }

  return out;
}

async function composeDensity(home) {
  const ll = homeLatLng(home);
  if (!ll) return [serializePlaceSection('block_density', { access: 'available', status: 'unavailable' })];

  try {
    const geohash = encodeGeohash(ll.lat, ll.lng, 6);
    // Goes through the audited k-anon helper rather than a second inline
    // copy of the same flooring. Two implementations of one privacy
    // primitive is itself a leak — the same cell reporting different
    // buckets on two surfaces narrows the count by comparison.
    // The raw count never leaves the reader.
    const { bucket } = await getDensityBucket(geohash);
    return [serializePlaceSection('block_density', {
      access: 'available',
      data: { bucket, label: DENSITY_LABELS[bucket] },
    })];
  } catch (err) {
    logger.warn('placeIntelligence: density failed', { homeId: home.id, error: err.message });
    return [serializePlaceSection('block_density', { access: 'available', status: 'error' })];
  }
}

async function composeYourHome(home, tier) {
  const access = bandAccess('B', tier);
  if (access === 'locked') {
    return [serializePlaceSection('your_home', {
      access: 'locked',
      unavailableReason: "Claim your place to see your home's exact details and value.",
    })];
  }
  // Band B is ATTOM-paid — without a key there is no exact data (area teaser only).
  if (!process.env.ATTOM_API_KEY) {
    return [serializePlaceSection('your_home', {
      access,
      status: 'unavailable',
      unavailableReason: "Exact property details aren't available for your area yet.",
    })];
  }

  let result = null;
  try {
    result = await propertyIntelligenceService.getProfile(home.id);
  } catch (err) {
    logger.warn('placeIntelligence: propertyIntelligence failed', { homeId: home.id, error: err.message });
    return [serializePlaceSection('your_home', { access, status: 'error' })];
  }
  const profile = result && result.profile;
  if (!profile || result.source === 'error') {
    return [serializePlaceSection('your_home', { access, status: 'unavailable' })];
  }

  return [serializePlaceSection('your_home', {
    access,
    asOf: profile.cached_at || null,
    // 'cache_stale' = ATTOM was unreachable and an expired row served
    // instead (database-first resilience) — surfaced as a stale envelope.
    status: result.source === 'cache_stale' ? 'stale' : 'ready',
    data: {
      year_built: profile.year_built ?? null,
      sqft: profile.sqft ?? null,
      bedrooms: profile.bedrooms ?? null,
      bathrooms: profile.bathrooms ?? null,
      lot_sqft: profile.lot_sqft ?? null,
      home_type: profile.property_type ?? null,
      estimated_value: profile.estimated_value ?? null,
      value_low: profile.value_range_low ?? null,
      value_high: profile.value_range_high ?? null,
      assessed_value: profile.assessed_value ?? null,
    },
  })];
}

// Bill types worth comparing against neighbors. Deliberately the
// area-service bills only: rent and mortgage are wildly home-specific, so
// a peer comparison tells the resident nothing useful (and rent already
// has its own `rent_band` section from HUD FMRs). HOA and insurance are
// property-specific for the same reason.
const BENCHMARKABLE_BILL_TYPES = ['electric', 'gas', 'water', 'sewer', 'trash', 'internet', 'cable'];

const BILL_LABELS = {
  electric: 'electric',
  gas: 'gas',
  water: 'water',
  sewer: 'sewer',
  trash: 'trash',
  internet: 'internet',
  cable: 'cable',
};

/**
 * Pick which bill to benchmark.
 *
 * This used to be hardcoded to `electric` on BOTH reads, while the refresh
 * job has always grouped by bill_type — so benchmarks for gas, water,
 * internet and the rest were computed and then ignored. A resident whose
 * only logged bill was internet saw "unavailable" on a block that had an
 * internet benchmark sitting right there.
 *
 * Preference order, most useful first:
 *   1. a type where the resident has bills AND the block has a band — the
 *      only state that can show a comparison, which is the whole point;
 *   2. otherwise the band backed by the largest cohort (most reliable);
 *   3. otherwise nothing.
 */
function pickBillType(benchmarkRows, ownBillTypes) {
  const byType = new Map();
  for (const row of benchmarkRows) {
    if (!BENCHMARKABLE_BILL_TYPES.includes(row.bill_type)) continue;
    const entry = byType.get(row.bill_type) || { rows: [], households: 0 };
    entry.rows.push(row);
    entry.households = Math.max(entry.households, Number(row.household_count) || 0);
    byType.set(row.bill_type, entry);
  }
  if (byType.size === 0) return null;

  const comparable = [...byType.entries()].filter(([type]) => ownBillTypes.has(type));
  const pool = comparable.length ? comparable : [...byType.entries()];

  pool.sort((a, b) => b[1].households - a[1].households);
  const [type, entry] = pool[0];
  return { type, rows: entry.rows };
}

async function composeBillBenchmark(home) {
  const ll = homeLatLng(home);
  if (!ll) return [serializePlaceSection('bill_benchmark', { access: 'available', status: 'unavailable' })];

  let benchmarkRows = [];
  try {
    const geohash = encodeGeohash(ll.lat, ll.lng, 6);
    // Privacy floor: only household_count >= 10 may be shown (matches the
    // existing bill-trends read). Smaller cohorts stay unavailable.
    const { data } = await supabaseAdmin
      .from('BillBenchmark')
      .select('bill_type, avg_amount_cents, household_count')
      .eq('geohash', geohash)
      .gte('household_count', 10);
    benchmarkRows = data || [];
  } catch (err) {
    logger.warn('placeIntelligence: billBenchmark failed', { homeId: home.id, error: err.message });
    return [serializePlaceSection('bill_benchmark', { access: 'available', status: 'error' })];
  }

  // The resident's own bills (Band C input) — read first so the picker can
  // prefer a type they can actually be compared on.
  const ownByType = new Map();
  try {
    const { data: bills } = await supabaseAdmin
      .from('HomeBill')
      .select('amount, bill_type')
      .eq('home_id', home.id);
    for (const b of bills || []) {
      const amount = Number(b && b.amount);
      if (!b || !Number.isFinite(amount)) continue;
      if (!BENCHMARKABLE_BILL_TYPES.includes(b.bill_type)) continue;
      const list = ownByType.get(b.bill_type) || [];
      list.push(amount);
      ownByType.set(b.bill_type, list);
    }
  } catch (err) {
    logger.warn('placeIntelligence: own bills read failed', { homeId: home.id, error: err.message });
  }

  const picked = pickBillType(benchmarkRows, new Set(ownByType.keys()));
  if (!picked) {
    return [serializePlaceSection('bill_benchmark', { access: 'available', status: 'unavailable' })];
  }

  const amounts = picked.rows
    .map((r) => Number(r.avg_amount_cents) / 100)
    .filter((n) => Number.isFinite(n));
  if (!amounts.length) {
    return [serializePlaceSection('bill_benchmark', { access: 'available', status: 'unavailable' })];
  }

  const bandLow = Math.round(Math.min(...amounts));
  const bandHigh = Math.round(Math.max(...amounts));
  const bandMid = (bandLow + bandHigh) / 2;

  // HomeBill.amount is stored in cents (the benchmark job averages it
  // straight into avg_amount_cents); convert to dollars to match the band.
  const ownVals = ownByType.get(picked.type) || [];
  const yourAmount = ownVals.length
    ? Math.round(ownVals.reduce((a, b) => a + b, 0) / ownVals.length / 100)
    : null;

  const label = BILL_LABELS[picked.type] || picked.type;
  let comparison = 'typical';
  let comparisonPct = 0;
  let summary;
  if (yourAmount != null && bandMid > 0) {
    comparisonPct = Math.round(((yourAmount - bandMid) / bandMid) * 100);
    comparison = comparisonPct > 5 ? 'higher' : comparisonPct < -5 ? 'lower' : 'typical';
    const dir = comparison === 'higher' ? 'above' : comparison === 'lower' ? 'below' : 'in line with';
    summary = `Your ${label} bill is ${Math.abs(comparisonPct)}% ${dir} neighbors`;
  } else {
    summary = `Neighborhood ${label} bills average about $${Math.round(bandMid).toLocaleString('en-US')}/mo`;
  }

  return [serializePlaceSection('bill_benchmark', {
    access: 'available',
    data: {
      utility: picked.type,
      your_amount: yourAmount,
      band_low: bandLow,
      band_high: bandHigh,
      comparison,
      comparison_pct: comparisonPct,
      period: '12-month average',
      summary,
    },
  })];
}

// ── home_systems (Band C — the household's own record) ───────
// The first section in Band C, so it is also the first thing on this
// endpoint that the trust ladder actually gates: `bandAccess` returns
// 'locked' below T3, and the client renders the locked card.
async function composeHomeSystems(home, tier) {
  const access = bandAccess('C', tier);
  if (access !== 'available') {
    return [serializePlaceSection('home_systems', {
      access,
      status: 'unavailable',
      unavailableReason: 'Claim this home to keep its maintenance record.',
    })];
  }

  try {
    const ledger = await getSystemsLedger(home);
    if (!ledger) {
      return [serializePlaceSection('home_systems', { access, status: 'unavailable' })];
    }
    return [serializePlaceSection('home_systems', { access, data: ledger })];
  } catch (err) {
    logger.warn('placeIntelligence: home_systems failed', { homeId: home.id, error: err.message });
    return [serializePlaceSection('home_systems', { access, status: 'error' })];
  }
}

// ════════════════════════════════════════════════════════════
// composeHomeIntelligence — the full grouped response
// ════════════════════════════════════════════════════════════

// Which launch-set sections each wired composer produces, so a
// `?sections=` subset request only runs (and only pays for) the
// composers it actually needs.
//
// Still BUILD_PENDING: `incentives` only (DSIRE's API is license-gated;
// curated federal copy would rot).
const COMPOSER_SECTIONS = [
  { ids: ['weather', 'air_quality', 'alerts', 'good_day_to'], run: ({ home, userId }) => composeToday(userId, home) },
  { ids: ['sunrise_sunset'], run: ({ home }) => placeSectionAdapters.composeSunriseSunset(home) },
  { ids: ['flood', 'census_context'], run: ({ home }) => composeNeighborhood(home) },
  { ids: ['heat_cold'], run: ({ home, userId }) => composeHeatCold(home, userId) },
  { ids: ['seismic'], run: ({ home }) => placeSectionAdapters.composeSeismic(home) },
  { ids: ['wildfire'], run: ({ home }) => placeSectionAdapters.composeWildfire(home) },
  { ids: ['lead_radon'], run: ({ home }) => placeSectionAdapters.composeLeadRadon(home) },
  { ids: ['drinking_water'], run: ({ home }) => placeSectionAdapters.composeDrinkingWater(home) },
  { ids: ['environmental_hazards'], run: ({ home }) => placeSectionAdapters.composeEnvironmentalHazards(home) },
  { ids: ['block_density'], run: ({ home }) => composeDensity(home) },
  { ids: ['your_home'], run: ({ home, tier }) => composeYourHome(home, tier) },
  { ids: ['home_systems'], run: ({ home, tier }) => composeHomeSystems(home, tier) },
  { ids: ['bill_benchmark'], run: ({ home }) => composeBillBenchmark(home) },
  { ids: ['rent_band'], run: ({ home }) => placeSectionAdapters.composeRentBand(home) },
  { ids: ['civic_districts'], run: ({ home }) => placeSectionAdapters.composeCivicDistricts(home) },
  { ids: ['civic_election'], run: ({ home }) => placeSectionAdapters.composeCivicElection(home) },
];

// ── Per-home privacy → the place address ref (§ homePrivacy) ──
// The endpoint is member-only, so most of the 9 Security toggles do not
// apply here by design: guest_approval / notification_previews /
// doc_lock / photo_blur / vault_auto_lock govern other surfaces, and
// member_name_visibility / activity_visibility / map_opt_out govern what
// OUTSIDERS see (this payload reaches members only). The one toggle that
// shapes THIS payload is `address_precision` ("Street only · hide unit
// number"): when ON, the unit (address2) is stripped from the ref.
//
// The Home.address column is often a fully-formatted geocoder string
// ("4080 NE Tacoma Ct, Camas, Washington 98607, United States"); the ref
// wants the street line, so everything after the first comma is dropped
// (serializePlaceAddressRef re-appends the city for the display label).
function buildPlaceRef(home, privacy) {
  const full = String(home.address || '');
  const street = (full.split(',')[0] || '').trim() || full;
  const unit = String(home.address2 || '').trim();
  const includeUnit = unit && !(privacy && privacy.address_precision);
  return {
    line1: includeUnit ? `${street} ${unit}` : street,
    city: home.city,
    state: home.state,
    zipcode: home.zipcode,
  };
}

/**
 * @param {object} params
 * @param {string} params.homeId
 * @param {string} params.userId
 * @param {object} params.access  Result of checkHomePermission (hasAccess, isOwner, occupancy).
 * @param {string[]} [params.sectionIds]  Optional subset of PLACE_SECTION_IDS to compose
 *                                        (already validated by the route); omitted ⇒ all.
 * @returns {Promise<object|null>} The PlaceIntelligence response, or null if the home is missing.
 */
async function composeHomeIntelligence({ homeId, userId, access, sectionIds }) {
  const { data: home, error } = await supabaseAdmin
    .from('Home')
    .select(HOME_SELECT)
    .eq('id', homeId)
    .maybeSingle();

  if (error || !home) {
    if (error) logger.warn('placeIntelligence: home fetch failed', { homeId, error: error.message });
    return null;
  }

  const tier = resolveTier(access);
  const requested = new Set(
    Array.isArray(sectionIds) && sectionIds.length ? sectionIds : PLACE_SECTION_IDS,
  );

  // Compose only the composers that produce a requested section — plus the
  // home's privacy toggles — in parallel; each composer is self-contained
  // and resolves (never rejects), so one failure can't sink the response.
  const runs = COMPOSER_SECTIONS.filter(({ ids }) => ids.some((id) => requested.has(id)));
  const [privacy, ...groups] = await Promise.all([
    getHomePrivacy(homeId),
    ...runs.map(({ run }) => run({ home, userId, tier })),
  ]);

  const composed = {};
  for (const env of groups.flat()) composed[env.id] = env;

  // Emit the requested sections in canonical order; anything not yet wired
  // fills as `unavailable` so the full IA renders with section-by-section
  // degradation (turned on in later waves).
  const sections = PLACE_SECTION_IDS.filter((id) => requested.has(id)).map((id) => {
    if (composed[id]) return composed[id];
    const meta = PLACE_SECTION_META[id];
    return serializePlaceSection(id, { access: bandAccess(meta.band, tier), status: 'unavailable' });
  });

  return serializePlaceIntelligence({
    place: buildPlaceRef(home, privacy),
    tier,
    regionSupported: true,
    sections,
  });
}

module.exports = {
  composeHomeIntelligence,
  // Exported for unit testing.
  resolveTier,
  bandAccess,
  floodRiskLevel,
  mapAqiCategory,
  buildPlaceRef,
};
