/**
 * Before-You-Sign Scout (Wave 4) — the address you are ABOUT to commit to.
 *
 * THE TENSION THE ROADMAP FLAGGED, AND HOW IT RESOLVES.
 * Scout looked like it fought the locked-teaser economics: give it away
 * and you lose the conversion lever, lock it and you gate the product.
 * The resolution falls out of noticing who the user is — someone
 * considering an address they do NOT live at. They cannot be a verified
 * resident of it; verification is impossible by definition, so gating
 * Scout behind T4 would make it unusable by exactly the audience it
 * exists for.
 *
 * So Scout runs at T1: an account, no address claim, no postcard. That
 * is a genuinely different ask from the rest of the product, which is
 * the point — it reaches people who are not ready to claim an address
 * but are about to sign a lease or an offer, which is the moment they
 * care most.
 *
 * WHAT IT IS. Not another dashboard. The dashboard answers "what is true
 * about my home"; Scout answers "what should I ask before I sign", and
 * the derived question list IS the product. Every question is generated
 * from a fact we actually have and carries the fact that produced it, so
 * it can be checked rather than trusted.
 *
 * THE PRIVACY CONSTRAINT THAT SHAPES THE WHOLE THING. Someone scouting an
 * address is looking up a place where SOMEBODY ELSE currently lives.
 * That makes this the one surface in the product where the person asking
 * is not the person the data is about. So Scout is restricted to facts
 * about LAND AND BUILDINGS that are already public — flood zone,
 * environmental risk, county rent bands, civic districts — and never
 * touches anything derived from the people there:
 *   * no resident or owner names, no occupancy, no household record;
 *   * no Band B property valuation (that is the owner's record);
 *   * no Band D real-rent band (that is the block's residents' data,
 *     contributed for each other, not for someone shopping);
 *   * no raw verified-neighbour count — the k-anon bucket only, exactly
 *     as the anonymous preview shows it.
 * A prospective tenant learning the flood zone is fair. A prospective
 * anything learning about the current occupants is not.
 *
 * NEVER ADVICE. Every generated line is a QUESTION TO ASK or a fact,
 * never an instruction. "Ask whether it has been tested" — not "demand a
 * test", and never anything that reads as legal or financial advice.
 */

const logger = require('../utils/logger');
const placeSectionAdapters = require('./placeSectionAdapters');
const nfipPremiumService = require('./nfipPremiumService');

// Federal lead-paint disclosure applies to housing built before 1978.
const LEAD_DISCLOSURE_YEAR = 1978;

/**
 * The question list — the actual product.
 *
 * Each entry carries `because`: the fact that generated it. A question
 * without its reason is just a checklist someone found on the internet;
 * with it, the reader can judge whether it applies to them.
 */
function askBeforeYouSign({ flood, nfip, radon, water, rentBand, askingRent }) {
  const asks = [];

  if (flood && flood.in_sfha) {
    asks.push({
      id: 'flood_insurance_required',
      question: 'Who pays for flood insurance here, and what does it cost this year?',
      because: `This address sits in FEMA flood zone ${flood.zone}, where a federally backed mortgage requires flood insurance.`,
      source: 'FEMA National Flood Hazard Layer',
    });
    if (nfip && nfip.premium_median) {
      asks.push({
        id: 'flood_premium_benchmark',
        question: 'Ask to see the current flood policy and its declarations page.',
        because: `Real NFIP policies in this census tract run a median of $${nfip.premium_median.toLocaleString('en-US')} a year. A quote for this address could differ, but a number far below that is worth asking about.`,
        source: 'FEMA · OpenFEMA NFIP policies',
      });
    }
  } else if (flood && flood.zone) {
    asks.push({
      id: 'flood_history',
      question: 'Has this property ever flooded, and is there a flood policy on it now?',
      because: `The address is outside the high-risk zone (${flood.zone}), where flood insurance is usually optional — which also means it is often absent.`,
      source: 'FEMA National Flood Hazard Layer',
    });
  }

  if (radon && radon.radon_zone === 1) {
    asks.push({
      id: 'radon_tested',
      question: 'Has this home been tested for radon, and can you see the result?',
      because: 'This county is EPA Radon Zone 1 — the highest predicted indoor level. A test is inexpensive and the result is specific to the building.',
      source: 'EPA radon zones',
    });
  }

  if (radon && radon.year_built != null && radon.year_built < LEAD_DISCLOSURE_YEAR) {
    asks.push({
      id: 'lead_disclosure',
      question: 'Ask for the lead-paint disclosure and any inspection records.',
      // Attributed to the reader, because they supplied the year. We do
      // not look up a build year for an address we have no claim on.
      because: `You told us the building dates to ${radon.year_built}. Federal law requires sellers and landlords of most pre-1978 housing to give you a lead-paint disclosure before you sign.`,
      source: 'HUD / EPA lead disclosure rule',
    });
  }

  if (water && water.violation_count > 0) {
    asks.push({
      id: 'water_violations',
      question: 'Which water system serves this address, and has it had recent violations?',
      because: `The system serving this area has ${water.violation_count} recorded violation${water.violation_count === 1 ? '' : 's'} in the EPA's database.`,
      source: 'EPA SDWIS',
    });
  }

  if (rentBand && askingRent != null) {
    if (askingRent > rentBand.band_high) {
      asks.push({
        id: 'rent_above_band',
        question: 'What does this rent include that comparable units do not?',
        because: `The asking rent is above HUD's fair market rent band for this county ($${rentBand.band_low.toLocaleString('en-US')}–$${rentBand.band_high.toLocaleString('en-US')}). That is common in desirable buildings and is not by itself a problem — it is a thing to have an answer for.`,
        source: 'HUD Fair Market Rents',
      });
    }
    asks.push({
      id: 'utilities_included',
      question: 'Which utilities are included, and what did they run last winter?',
      because: 'A rent that looks competitive can be undone by utilities, and last winter\'s bills are a fact the current occupant can produce.',
      source: null,
    });
  }

  // Always asked. It costs nothing and it is the question people most
  // regret not asking.
  asks.push({
    id: 'whats_changed',
    question: 'What has been repaired or replaced in the last five years, and is there paperwork?',
    because: 'Roof, heating, water heater and electrical panel are the expensive four, and their age predicts what you will spend.',
    source: null,
  });

  return asks;
}

/**
 * Compose a Scout report for an address the caller does NOT occupy.
 *
 * @param {{lat:number, lng:number, line:string, city:string, state:string, zipcode:string}} place
 * @param {{askingRent?: number, yearBuilt?: number}} [options]
 *   `yearBuilt` comes from the CALLER — a listing states it, and we do
 *   not know it without the owner's property record (Band B, excluded
 *   here on purpose). Questions derived from it say so, because a fact
 *   the reader supplied and a fact we looked up are not the same kind of
 *   thing and should not be presented as one.
 */
async function getScoutReport(place, { askingRent, yearBuilt } = {}) {
  // A synthetic, occupant-free "home" for the composers. It deliberately
  // carries no id, no owner and no household fields — nothing here may
  // resolve to a real Home row, so no composer can reach occupancy,
  // ownership, or any resident-contributed data even by accident.
  const synthetic = {
    id: null,
    address: place.line,
    city: place.city,
    state: place.state,
    zipcode: place.zipcode,
    map_center_lat: place.lat,
    map_center_lng: place.lng,
    bedrooms: null,
    // Only ever the caller's own number; never resolved from a record.
    year_built: Number.isFinite(Number(yearBuilt)) && Number(yearBuilt) > 1500 ? Number(yearBuilt) : null,
  };

  const [radonSettled, waterSettled, rentSettled, civicSettled] = await Promise.allSettled([
    placeSectionAdapters.composeLeadRadon(synthetic),
    placeSectionAdapters.composeDrinkingWater(synthetic),
    placeSectionAdapters.composeRentBand(synthetic),
    placeSectionAdapters.composeCivicDistricts(synthetic),
  ]);

  const dataOf = (settled) => {
    if (settled.status !== 'fulfilled') return null;
    const [section] = settled.value || [];
    return section && (section.status === 'ready' || section.status === 'stale') ? section.data : null;
  };

  const radon = dataOf(radonSettled);
  const water = dataOf(waterSettled);
  const rentBand = dataOf(rentSettled);
  const civic = dataOf(civicSettled);

  // Flood + what insurance actually costs there: the pair that changes a
  // decision more than anything else on the page.
  //
  // Fetched DIRECTLY rather than via neighborhoodProfileService.getProfile,
  // which would have sent the typed address to WalkScore
  // (fetchWalkScore puts it in a query string to api.walkscore.com).
  // Scout promises the reader "we did not tell anyone you looked", and
  // that promise has to be true: the address never leaves this process.
  // Scout wants only the zone and the tract id, and both are reachable
  // from coordinates alone — getProfile was over-fetching anyway.
  let flood = null;
  let nfip = null;
  try {
    const neighborhood = require('./ai/neighborhoodProfileService');
    const [zoneSettled, tractSettled] = await Promise.allSettled([
      neighborhood.fetchFloodZone(place.lat, place.lng),
      neighborhood.geocodeToTractCached(place.lat, place.lng),
    ]);

    const zoneRow = zoneSettled.status === 'fulfilled' ? zoneSettled.value : null;
    const rawZone = zoneRow && (zoneRow.flood_zone || zoneRow.zone || zoneRow.FLD_ZONE);
    if (rawZone) {
      const zone = String(rawZone).toUpperCase();
      flood = {
        zone: rawZone,
        in_sfha: zone.startsWith('A') || zone.startsWith('V'),
        plain_meaning: (zoneRow && (zoneRow.flood_zone_description || zoneRow.description)) || null,
      };
    }

    const tract = tractSettled.status === 'fulfilled' ? tractSettled.value : null;
    if (tract && tract.tractId) {
      const benchmark = await nfipPremiumService.getTractBenchmark(tract.tractId);
      if (benchmark && benchmark.status === 'ready') nfip = benchmark.data;
    }
  } catch (err) {
    logger.warn('scout: flood/nfip failed', { error: err.message });
  }

  const asks = askBeforeYouSign({
    flood,
    nfip,
    radon: radon ? { ...radon, year_built: radon.year_built ?? null } : null,
    water,
    rentBand,
    askingRent,
  });

  return {
    place: {
      address: place.line,
      city: place.city,
      state: place.state,
      zipcode: place.zipcode,
    },
    flood,
    flood_cost: nfip
      ? {
        premium_p25: nfip.premium_p25,
        premium_median: nfip.premium_median,
        premium_p75: nfip.premium_p75,
        policy_count: nfip.policy_count,
        scope: 'census tract',
        note: 'Real policies near this address. A benchmark, not a quote.',
      }
      : null,
    environment: { radon, water },
    rent: rentBand
      ? {
        band_low: rentBand.band_low,
        band_high: rentBand.band_high,
        period: rentBand.period,
        asking_rent: askingRent ?? null,
        // Stated as a position against a public band, never as a verdict
        // on whether the price is fair — we do not know the unit.
        position: askingRent == null
          ? null
          : askingRent > rentBand.band_high
            ? 'above_band'
            : askingRent < rentBand.band_low
              ? 'below_band'
              : 'in_band',
        scope: 'county',
      }
      : null,
    civic,
    ask_before_you_sign: asks,
    // Rendered verbatim by every client. Scout is about land and
    // buildings; the people currently living there are not our subject.
    scope_note: 'Everything here describes the property and the area from public records. '
      + 'Nothing about the people who live there is shown, and we did not tell anyone you looked.',
  };
}

module.exports = {
  getScoutReport,
  // Exported for testing.
  askBeforeYouSign,
  LEAD_DISCLOSURE_YEAR,
};
