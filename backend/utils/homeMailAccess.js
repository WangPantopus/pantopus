/**
 * Household mail access scoping.
 *
 * This is the sole authorization gate for the physical-mail routes — the one
 * surface where a verified address produces continuous, high-sensitivity
 * third-party data (who writes to this household: scanned envelopes, sender
 * identities, package notifications).
 *
 * It used to be duplicated verbatim in five route files. Four filtered
 * `is_active = true`; the copy in routes/mailbox.js did not, and additionally
 * admitted any `Home.owner_id` match, so a roommate who had properly moved out
 * kept reading the household's mail indefinitely on that surface while
 * correctly losing it on the other four (audit 2026-08-22, CRIT-03).
 *
 * One definition, one behaviour. Do not inline a copy of this.
 */

const supabaseAdmin = require('../config/supabaseAdmin');
const logger = require('./logger');

/**
 * Home ids whose household mail this user may read.
 *
 * Requires an ACTIVE occupancy. Fails closed: on a lookup error the user is
 * scoped to nothing rather than to everything.
 *
 * @param {string} userId
 * @returns {Promise<string[]>}
 */
async function getAccessibleHomeIds(userId) {
  if (!userId) return [];

  const { data, error } = await supabaseAdmin
    .from('HomeOccupancy')
    .select('home_id')
    .eq('user_id', userId)
    .eq('is_active', true);

  if (error) {
    logger.error('getAccessibleHomeIds: failing closed', { userId, error: error.message });
    return [];
  }

  return [...new Set((data || []).map((r) => r.home_id).filter(Boolean))];
}

module.exports = { getAccessibleHomeIds };
