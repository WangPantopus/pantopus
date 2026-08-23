/**
 * Retention for address-verification observability events.
 *
 * PRV — AddressVerificationEvent grows without bound. No job in the repository
 * touched it, there was no retention policy, and rows reference a HomeAddress
 * indefinitely. Verification telemetry is operationally useful for weeks, not
 * forever, and an unbounded table of "who tried to verify what, when" is both a
 * storage problem and the most subpoena-attractive dataset the product holds
 * (audit 2026-08-22).
 *
 * Deletes events older than the retention window, in bounded batches.
 */

const supabaseAdmin = require('../config/supabaseAdmin');
const logger = require('../utils/logger');

/** How long verification telemetry is kept. */
const RETENTION_DAYS = Number(process.env.ADDRESS_EVENT_RETENTION_DAYS || 90);

/** Maximum rows removed per run, so a first run on a large table stays bounded. */
const BATCH_LIMIT = Number(process.env.ADDRESS_EVENT_PURGE_BATCH || 5000);

async function purgeAddressVerificationEvents(options = {}) {
  const {
    dryRun = false,
    retentionDays = RETENTION_DAYS,
    limit = BATCH_LIMIT,
  } = options;

  const cutoff = new Date(Date.now() - retentionDays * 24 * 60 * 60 * 1000).toISOString();

  const { data: stale, error } = await supabaseAdmin
    .from('AddressVerificationEvent')
    .select('id')
    .lt('created_at', cutoff)
    .limit(limit);

  if (error) {
    logger.error('[purgeAddressVerificationEvents] Failed to query stale events', {
      error: error.message,
    });
    throw error;
  }

  const ids = (stale || []).map((r) => r.id);

  if (ids.length === 0) {
    logger.info('[purgeAddressVerificationEvents] Nothing to purge', {
      retention_days: retentionDays, dry_run: dryRun,
    });
    return { scanned: 0, deleted: 0, retention_days: retentionDays, dry_run: dryRun };
  }

  if (dryRun) {
    logger.info('[purgeAddressVerificationEvents] Dry run', {
      would_delete: ids.length, retention_days: retentionDays,
    });
    return { scanned: ids.length, deleted: 0, retention_days: retentionDays, dry_run: true };
  }

  const { error: delErr } = await supabaseAdmin
    .from('AddressVerificationEvent')
    .delete()
    .in('id', ids);

  if (delErr) {
    logger.error('[purgeAddressVerificationEvents] Delete failed', { error: delErr.message });
    throw delErr;
  }

  logger.info('[purgeAddressVerificationEvents] Completed', {
    deleted: ids.length, retention_days: retentionDays,
  });

  return { scanned: ids.length, deleted: ids.length, retention_days: retentionDays, dry_run: false };
}

module.exports = purgeAddressVerificationEvents;
