/**
 * Unlisted (Wave 4) — what each state exposes, and its escape hatch.
 *
 * The most valuable thing on the Unlisted page is not the broker list.
 * It is this: most US states run an ADDRESS CONFIDENTIALITY PROGRAM —
 * a legal substitute address for survivors of domestic violence, sexual
 * assault, stalking or trafficking, so the real one stays out of public
 * records at the source rather than being chased across thirty sites
 * forever.
 *
 * Someone typing their address into a page called "get my address off
 * the internet" is disproportionately likely to be doing it because of
 * a specific person. For that reader this is the single highest-value
 * fact we can surface, and the broker list is the consolation prize.
 * It is placed first for that reason.
 *
 * ACCURACY IS NOT OPTIONAL HERE. A wrong program name or a dead link
 * fails someone at the worst possible moment. Every entry carries the
 * official source it was verified against; an unverifiable entry is
 * omitted rather than guessed, and the UI must degrade to "we could not
 * confirm a program for your state" rather than inventing one.
 *
 * We do NOT ask why someone is here, we do not record that they looked,
 * and the T0 endpoint persists nothing.
 */

/**
 * @typedef {object} StateDisclosure
 * @property {string}  state           two-letter code
 * @property {boolean} acp_exists
 * @property {string}  acp_name        the program's official name
 * @property {string}  acp_url         official state URL
 * @property {string}  acp_eligibility one sentence: who qualifies
 * @property {string}  source_url      the page this was verified against
 * @property {string}  verified_at     ISO date
 */

/** @type {Record<string, StateDisclosure>} */
const STATE_DISCLOSURE = {
  // Populated exclusively from verified official sources — see header.
};

/**
 * The disclosure facts for a state, or null when we have not verified
 * that state. Null must render as "we could not confirm", never as
 * "your state has no program" — those are different claims and only one
 * of them is ours to make.
 */
function forState(stateCode) {
  const key = String(stateCode || '').trim().toUpperCase();
  if (!key) return null;
  return STATE_DISCLOSURE[key] || null;
}

module.exports = {
  STATE_DISCLOSURE,
  forState,
};
