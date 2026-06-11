/**
 * Place Section Adapters (Phase 2)
 *
 * Per-section providers for the launch-set sections that were
 * BUILD_PENDING after wave 0/1. Each compose* returns serialized
 * envelopes and NEVER throws — failures degrade to that section's
 * error/unavailable status. All remote fetches go through
 * placeSectionCache.readThrough (Phase 0), so each section gets its
 * Step-1 freshness budget + stale-serve for free.
 *
 *   sunrise_sunset         Open-Meteo daily (keyless)        ~6 h cache
 *   lead_radon             CountyRadonZone table (mig. 158)  local read
 *                          + Home.year_built lead screening
 *   rent_band              HudFmr table (mig. 158)           local read
 *   drinking_water         EPA SDWIS via data.epa.gov        90 d cache
 *   environmental_hazards  EPA ECHO get_facilities           90 d cache
 *
 * `incentives` stays BUILD_PENDING: DSIRE's API is license-gated
 * (programs.dsireusa.org returns "Access denied" without one) and
 * hand-curated federal copy would rot — no trustworthy free source.
 *
 * County resolution: the Census geocoder (already used by
 * neighborhoodProfileService) gives state+county FIPS for the home's
 * lat/lng; that lookup is itself cached per home for 90 days.
 */

const supabaseAdmin = require('../config/supabaseAdmin');
const logger = require('../utils/logger');
const { encodeGeohash } = require('../utils/geohash');
const { serializePlaceSection } = require('../serializers/placeIntelligenceSerializer');
const { readThrough } = require('./placeSectionCache');
const { geocodeToTract } = require('./ai/neighborhoodProfileService');

const DAY_MS = 24 * 60 * 60 * 1000;
const FETCH_TIMEOUT_MS = 8000;

function homeLatLng(home) {
  const lat = Number(home.map_center_lat);
  const lng = Number(home.map_center_lng);
  if (Number.isFinite(lat) && Number.isFinite(lng)) return { lat, lng };
  return null;
}

async function fetchJson(url, { headers } = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);
  try {
    const res = await fetch(url, { signal: controller.signal, headers });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.json();
  } finally {
    clearTimeout(timer);
  }
}

// ── County FIPS for a home (geocoder, cached 90 d per home) ──
async function homeCountyFips(home) {
  const ll = homeLatLng(home);
  if (!ll) return null;
  try {
    const { payload } = await readThrough({
      cacheKey: `home:${home.id}`,
      sectionId: '_county_fips',
      ttlMs: 90 * DAY_MS,
      fetch: async () => {
        const tract = await geocodeToTract(ll.lat, ll.lng);
        if (!tract || !tract.stateCode || !tract.countyCode) return null;
        return { county_fips: `${tract.stateCode}${tract.countyCode}` };
      },
    });
    return (payload && payload.county_fips) || null;
  } catch (err) {
    logger.warn('placeSections: county fips failed', { homeId: home.id, error: err.message });
    return null;
  }
}

async function hudFmrRow(countyFips) {
  const { data } = await supabaseAdmin
    .from('HudFmr')
    .select('county_fips, fiscal_year, county_name, state_abbr, area_name, fmr_lo, fmr_hi')
    .eq('county_fips', countyFips)
    .order('fiscal_year', { ascending: false })
    .limit(1)
    .maybeSingle();
  return data || null;
}

// ════════════════════════════════════════════════════════════
// sunrise_sunset — Open-Meteo daily, keyless
// ════════════════════════════════════════════════════════════
async function composeSunriseSunset(home) {
  const ll = homeLatLng(home);
  if (!ll) return [serializePlaceSection('sunrise_sunset', { status: 'unavailable' })];

  try {
    // geohash-5 (~5 km): sun times don't move within a cell; 6 h budget
    // keeps the day fresh without a per-minute fetch.
    const gh5 = encodeGeohash(ll.lat, ll.lng, 5);
    const { payload, fetchedAt, stale } = await readThrough({
      cacheKey: `geo:${gh5}`,
      sectionId: 'sunrise_sunset',
      ttlMs: 6 * 60 * 60 * 1000,
      fetch: async () => {
        const data = await fetchJson(
          `https://api.open-meteo.com/v1/forecast?latitude=${ll.lat}&longitude=${ll.lng}` +
          '&daily=sunrise,sunset,daylight_duration&timezone=auto&forecast_days=1',
        );
        const d = data && data.daily;
        if (!d || !d.sunrise || !d.sunrise[0]) return null;
        return {
          sunrise: d.sunrise[0],
          sunset: d.sunset[0],
          daylight_minutes: Math.round((Number(d.daylight_duration && d.daylight_duration[0]) || 0) / 60),
        };
      },
    });
    if (!payload) return [serializePlaceSection('sunrise_sunset', { status: 'unavailable' })];
    return [serializePlaceSection('sunrise_sunset', {
      asOf: fetchedAt,
      status: stale ? 'stale' : 'ready',
      data: payload,
    })];
  } catch (err) {
    logger.warn('placeSections: sunrise failed', { homeId: home.id, error: err.message });
    return [serializePlaceSection('sunrise_sunset', { status: 'error' })];
  }
}

// ════════════════════════════════════════════════════════════
// lead_radon — EPA radon zone (county) + lead-paint screening (year built)
// ════════════════════════════════════════════════════════════
// EPA prevalence of lead-based paint by era: pre-1960 ~69–87%,
// 1960–1977 ~24%, banned for residential use in 1978.
function leadRiskForYear(yearBuilt) {
  if (!Number.isFinite(yearBuilt)) return 'possible';
  if (yearBuilt < 1960) return 'likely';
  if (yearBuilt < 1978) return 'possible';
  return 'unlikely';
}

const RADON_ZONE_PHRASES = {
  1: 'highest radon potential (zone 1)',
  2: 'moderate radon potential (zone 2)',
  3: 'low radon potential (zone 3)',
};

function leadRadonSummary(yearBuilt, risk, zone) {
  const lead = Number.isFinite(yearBuilt)
    ? (risk === 'unlikely'
      ? `Built ${yearBuilt} — after the 1978 lead-paint ban`
      : `Built ${yearBuilt} — lead paint ${risk}; test before renovating`)
    : 'Build year unknown — assume lead paint is possible in older homes';
  const radon = zone
    ? `Your county has the ${RADON_ZONE_PHRASES[zone]}`
    : 'County radon zone unavailable for your area';
  return `${lead}. ${radon}.`;
}

async function composeLeadRadon(home) {
  try {
    const countyFips = await homeCountyFips(home);
    let zone = null;
    if (countyFips) {
      const { data } = await supabaseAdmin
        .from('CountyRadonZone')
        .select('zone')
        .eq('county_fips', countyFips)
        .maybeSingle();
      zone = (data && data.zone) || null;
    }

    const yearBuilt = Number.isFinite(Number(home.year_built)) && Number(home.year_built) > 0
      ? Number(home.year_built)
      : null;
    const risk = leadRiskForYear(yearBuilt);

    if (yearBuilt == null && zone == null) {
      return [serializePlaceSection('lead_radon', {
        status: 'unavailable',
        unavailableReason: 'No build year on file and no county radon coverage for this area yet.',
      })];
    }

    return [serializePlaceSection('lead_radon', {
      status: yearBuilt != null && zone != null ? 'ready' : 'partial',
      coverage: zone == null ? 'partial' : 'full',
      data: {
        year_built: yearBuilt,
        lead_paint_risk: risk,
        radon_zone: zone,
        summary: leadRadonSummary(yearBuilt, risk, zone),
        disclaimer: 'Screening based on build year and county radon zone — not a test of this home. Test kits settle both.',
      },
    })];
  } catch (err) {
    logger.warn('placeSections: lead_radon failed', { homeId: home.id, error: err.message });
    return [serializePlaceSection('lead_radon', { status: 'error' })];
  }
}

// ════════════════════════════════════════════════════════════
// rent_band — HUD Fair Market Rents (county table, migration 158)
// ════════════════════════════════════════════════════════════
async function composeRentBand(home) {
  try {
    const countyFips = await homeCountyFips(home);
    const row = countyFips ? await hudFmrRow(countyFips) : null;
    if (!row) {
      return [serializePlaceSection('rent_band', {
        status: 'unavailable',
        unavailableReason: 'No HUD rent data for your county yet.',
      })];
    }

    const bedrooms = Math.min(4, Math.max(0, Number(home.bedrooms) || 2));
    const fmrLo = row.fmr_lo[bedrooms];
    const fmrHi = row.fmr_hi[bedrooms];
    // HUD FMRs are 40th-percentile point estimates; where HUD prices a
    // county at one number (lo = hi) the band extends 20% above it to
    // show a typical asking-rent spread — the summary says exactly that.
    const bandLow = fmrLo;
    const bandHigh = Math.max(fmrHi, Math.round(fmrLo * 1.2));
    const bedroomsLabel = bedrooms === 0 ? 'studio' : `${bedrooms}-bedroom`;

    return [serializePlaceSection('rent_band', {
      status: 'ready',
      data: {
        bedrooms,
        band_low: bandLow,
        band_high: bandHigh,
        // Full comparison track: efficiency floor to 20% over the 4BR top.
        market_low: Math.min(row.fmr_lo[0], bandLow),
        market_high: Math.max(Math.round(row.fmr_hi[4] * 1.2), bandHigh),
        period: `FY ${row.fiscal_year}`,
        summary: `HUD's FY ${row.fiscal_year} fair market rent for a ${bedroomsLabel} in ${row.county_name} is $${fmrLo.toLocaleString('en-US')}/mo; the band runs to about 20% above it.`,
      },
    })];
  } catch (err) {
    logger.warn('placeSections: rent_band failed', { homeId: home.id, error: err.message });
    return [serializePlaceSection('rent_band', { status: 'error' })];
  }
}

// ════════════════════════════════════════════════════════════
// drinking_water — EPA SDWIS (data.epa.gov dmapservice)
// ════════════════════════════════════════════════════════════
const SDWIS_BASE = 'https://data.epa.gov/dmapservice';

function stripCountySuffix(name) {
  return String(name || '').replace(/\s+(county|parish|borough|census area|municipality|planning region)$/i, '').trim();
}

// One county fetch: active community systems → pick the one serving the
// home's city when the name matches, else the largest; then its
// health-based violations (last 5 years).
async function fetchCountyWater(countyFips, homeCity) {
  const fmr = await hudFmrRow(countyFips);
  if (!fmr) return null;
  const county = stripCountySuffix(fmr.county_name);
  const state = fmr.state_abbr;

  const areas = await fetchJson(
    `${SDWIS_BASE}/sdwis.geographic_area/area_type_code/equals/CN` +
    `/and/county_served/equals/${encodeURIComponent(county)}` +
    `/and/primacy_agency_code/equals/${state}` +
    '/and/pws_type_code/equals/CWS/and/pws_activity_code/equals/A/1:40/json',
  );
  const pwsids = [...new Set((areas || []).map((a) => a.pwsid).filter(Boolean))];
  if (!pwsids.length) return null;

  const orChain = pwsids.map((id) => `pwsid/equals/${id}`).join('/or/');
  const systems = (await fetchJson(`${SDWIS_BASE}/sdwis.water_system/${orChain}/json`)) || [];
  const active = systems.filter((s) => s.pws_activity_code === 'A' && s.pws_type_code === 'CWS');
  if (!active.length) return null;

  const cityUpper = String(homeCity || '').toUpperCase();
  const cityMatches = cityUpper
    ? active.filter((s) => String(s.pws_name || '').toUpperCase().includes(cityUpper))
    : [];
  const pool = cityMatches.length ? cityMatches : active;
  const system = pool.reduce((a, b) =>
    (Number(b.population_served_count) || 0) > (Number(a.population_served_count) || 0) ? b : a);

  const violations = (await fetchJson(
    `${SDWIS_BASE}/sdwis.violation/pwsid/equals/${system.pwsid}` +
    '/and/is_health_based_ind/equals/Y/1:200/json',
  )) || [];
  const cutoff = Date.now() - 5 * 365 * DAY_MS;
  const recent = violations.filter((v) => {
    const t = Date.parse(v.compl_per_begin_date || '');
    return Number.isFinite(t) && t >= cutoff;
  });

  return {
    utility_name: String(system.pws_name || '').replace(/\s+/g, ' ').trim(),
    pws_id: system.pwsid || null,
    recent_health_violations: recent.length > 0,
    violation_count: recent.length,
    summary: recent.length > 0
      ? `${recent.length} health-based violation${recent.length === 1 ? '' : 's'} reported in the last 5 years.`
      : 'No health-based violations reported in the last 5 years.',
  };
}

async function composeDrinkingWater(home) {
  try {
    const countyFips = await homeCountyFips(home);
    if (!countyFips) return [serializePlaceSection('drinking_water', { status: 'unavailable' })];

    // City folded into the key: neighbors in the same city share the row.
    const citySlug = String(home.city || '').toLowerCase().replace(/[^a-z]/g, '') || 'any';
    const { payload, fetchedAt, stale } = await readThrough({
      cacheKey: `county:${countyFips}:${citySlug}`,
      sectionId: 'drinking_water',
      ttlMs: 90 * DAY_MS,
      fetch: () => fetchCountyWater(countyFips, home.city),
    });
    if (!payload) {
      return [serializePlaceSection('drinking_water', {
        status: 'unavailable',
        unavailableReason: 'No community water system found for your area in EPA records.',
      })];
    }
    return [serializePlaceSection('drinking_water', {
      asOf: fetchedAt,
      status: stale ? 'stale' : 'ready',
      coverage: 'partial', // county-level match, not a service-area polygon
      data: payload,
    })];
  } catch (err) {
    logger.warn('placeSections: drinking_water failed', { homeId: home.id, error: err.message });
    return [serializePlaceSection('drinking_water', { status: 'error' })];
  }
}

// ════════════════════════════════════════════════════════════
// environmental_hazards — EPA ECHO facilities within 1 mile
// ════════════════════════════════════════════════════════════
const ECHO_URL = 'https://echodata.epa.gov/echo/echo_rest_services.get_facilities';
const ECHO_PROGRAM_FLAGS = [
  ['CWAFlag', 'Clean Water Act'],
  ['CAAFlag', 'Clean Air Act'],
  ['RCRAFlag', 'Hazardous waste (RCRA)'],
  ['SDWISFlag', 'Safe Drinking Water Act'],
];

function haversineMiles(lat1, lng1, lat2, lng2) {
  const rad = (d) => (d * Math.PI) / 180;
  const dLat = rad(lat2 - lat1);
  const dLng = rad(lng2 - lng1);
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 3958.8 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

async function fetchEchoFacilities(lat, lng) {
  const data = await fetchJson(
    `${ECHO_URL}?output=JSON&p_lat=${lat}&p_long=${lng}&p_radius=1&responseset=20`,
  );
  const results = data && data.Results;
  if (!results || results.Error) {
    throw new Error((results && results.Error && results.Error.ErrorMessage) || 'ECHO error');
  }
  const rows = results.Facilities || [];
  const facilities = rows.slice(0, 5).map((f) => {
    const programs = ECHO_PROGRAM_FLAGS.filter(([flag]) => String(f[flag] || '') === 'Y').map(([, label]) => label);
    const fLat = Number(f.FacLat);
    const fLng = Number(f.FacLong);
    return {
      name: String(f.FacName || 'Regulated facility'),
      program: programs[0] || 'EPA-regulated',
      distance_mi: Number.isFinite(fLat) && Number.isFinite(fLng)
        ? Math.round(haversineMiles(lat, lng, fLat, fLng) * 10) / 10
        : 1,
    };
  });
  const count = Number(results.QueryRows) || rows.length;
  return {
    facilities_within_mile: count,
    radius_mi: 1,
    facilities,
    summary: count === 0
      ? 'No EPA-regulated facilities within a mile.'
      : `${count} EPA-regulated facilit${count === 1 ? 'y' : 'ies'} within a mile — regulated activity nearby, not unsafe exposure.`,
    disclaimer: 'Presence on EPA registries means regulated activity, not contamination or danger.',
  };
}

async function composeEnvironmentalHazards(home) {
  const ll = homeLatLng(home);
  if (!ll) return [serializePlaceSection('environmental_hazards', { status: 'unavailable' })];
  try {
    const gh6 = encodeGeohash(ll.lat, ll.lng, 6);
    const { payload, fetchedAt, stale } = await readThrough({
      cacheKey: `geo:${gh6}`,
      sectionId: 'environmental_hazards',
      ttlMs: 90 * DAY_MS,
      fetch: () => fetchEchoFacilities(ll.lat, ll.lng),
    });
    if (!payload) return [serializePlaceSection('environmental_hazards', { status: 'unavailable' })];
    return [serializePlaceSection('environmental_hazards', {
      asOf: fetchedAt,
      status: stale ? 'stale' : 'ready',
      data: payload,
    })];
  } catch (err) {
    logger.warn('placeSections: environmental_hazards failed', { homeId: home.id, error: err.message });
    return [serializePlaceSection('environmental_hazards', { status: 'error' })];
  }
}

module.exports = {
  composeSunriseSunset,
  composeLeadRadon,
  composeRentBand,
  composeDrinkingWater,
  composeEnvironmentalHazards,
  // Exported for testing.
  leadRiskForYear,
  haversineMiles,
  stripCountySuffix,
};
