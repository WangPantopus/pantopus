/**
 * Exemption Check (Wave 2 — "Exemption Left on the Table")
 *
 * Does the county's own record show a homestead-style exemption on
 * file for this parcel? The exemption flag lives on the assessor roll
 * line — there is no ZIP-level answer — and for a homeowner who was
 * never told the exemption isn't automatic, "nothing appears on file"
 * is worth real money every year.
 *
 * Honesty ladder (the whole feature is the ladder):
 *   on_file      — the ATTOM assessment carries exemption entries;
 *                  we show their labels.
 *   none_on_file — the assessment carries an exemption CONTAINER that
 *                  is empty/zero. Only this state may say "no
 *                  exemption appears on file" — copy still says
 *                  "appears", because assessor feeds lag.
 *   unknown      — ATTOM has the parcel but reports no exemption
 *                  structure at all (county doesn't feed it). Never
 *                  dressed as either of the above.
 * No savings claims, no eligibility determinations — the state program
 * table (data/homesteadPrograms.js) is deliberately conservative and
 * points at the county assessor.
 *
 * ATTOM's exemption reporting varies by county AND by response
 * vintage, so extraction is defensive across the shapes seen in
 * assessment payloads: `assessment.exemption` / `assessment.exemptions`
 * (object or array) and `assessment.tax.exemption` / `exemptiontype`.
 */

const logger = require('../utils/logger');
const propertyIntelligenceService = require('./ai/propertyIntelligenceService');
const { programForState } = require('../data/homesteadPrograms');

const HOMESTEAD_RE = /homestead|hmstd|hstd|principal residence|owner[- ]?occ/i;

function toEntries(value) {
  if (value == null) return [];
  if (Array.isArray(value)) return value.filter((v) => v != null);
  if (typeof value === 'object') return [value];
  // A bare string/number ("HS", 7000) is still a reported exemption.
  return [value];
}

function entryLabel(entry) {
  if (entry == null) return null;
  if (typeof entry === 'string') return entry.trim() || null;
  if (typeof entry === 'number') return entry > 0 ? `Exemption amount ${entry}` : null;
  if (typeof entry === 'object') {
    const label = entry.exemptionType || entry.exemptiontype || entry.type
      || entry.description || entry.desc || entry.name || null;
    const amount = Number(entry.exemptionAmount ?? entry.exemptionamount ?? entry.amt ?? entry.amount);
    if (label) return String(label).trim();
    if (Number.isFinite(amount) && amount > 0) return `Exemption amount ${amount}`;
    return null;
  }
  return null;
}

/**
 * Pull exemption info out of an ATTOM assessment block.
 * Pure, exported for tests.
 * @returns {{ reported: boolean, labels: string[], homestead: boolean }}
 *   reported=false → the county feed carries no exemption structure
 *   (the `unknown` rung); reported=true with empty labels → an empty
 *   container (the `none_on_file` rung).
 */
function extractExemptions(assessment) {
  if (!assessment || typeof assessment !== 'object') {
    return { reported: false, labels: [], homestead: false };
  }
  const tax = assessment.tax || {};
  const containers = [
    ['exemption', assessment.exemption],
    ['exemptions', assessment.exemptions],
    ['tax.exemption', tax.exemption],
    ['tax.exemptions', tax.exemptions],
    ['tax.exemptiontype', tax.exemptiontype],
    ['tax.exemptionType', tax.exemptionType],
  ].filter(([, v]) => v !== undefined);

  if (!containers.length) return { reported: false, labels: [], homestead: false };

  const labels = [];
  for (const [, value] of containers) {
    for (const entry of toEntries(value)) {
      const label = entryLabel(entry);
      if (label && !labels.includes(label)) labels.push(label);
    }
  }
  return {
    reported: true,
    labels,
    homestead: labels.some((l) => HOMESTEAD_RE.test(l)),
  };
}

/**
 * The exemption check for a home.
 * @returns {Promise<{status: string, data?: object, unavailableReason?: string}>}
 *   status 'ready' with data, or 'unavailable' with a reason key the
 *   composer maps to honest copy.
 */
async function getExemptionCheck(home) {
  if (!process.env.ATTOM_API_KEY) {
    return { status: 'unavailable', unavailableReason: 'ATTOM_NOT_CONFIGURED' };
  }

  let result;
  try {
    result = await propertyIntelligenceService.getHomeAttomPropertyDetail(home);
  } catch (err) {
    logger.warn('exemptionCheck: property detail failed', { homeId: home.id, error: err.message });
    return { status: 'error' };
  }
  const payload = result && result.attomPayload;
  const property = payload?.property?.[0] || payload?.full_response?.property?.[0] || null;
  if (!property) {
    return { status: 'unavailable', unavailableReason: result?.unavailableReason || 'NO_PARCEL_MATCH' };
  }

  const extracted = extractExemptions(property.assessment);
  const program = programForState(home.state);

  const filing_status = !extracted.reported
    ? 'unknown'
    : (extracted.labels.length ? 'on_file' : 'none_on_file');

  return {
    status: 'ready',
    data: {
      // unknown | on_file | none_on_file — the honesty ladder.
      filing_status,
      exemptions: extracted.labels,
      homestead_on_file: extracted.homestead,
      state_program: {
        state: String(home.state || '').trim().toUpperCase() || null,
        label: program.label,
        filing: program.filing,
        note: program.note,
        curated: program.curated,
      },
    },
  };
}

module.exports = {
  getExemptionCheck,
  // Exported for testing.
  extractExemptions,
};
