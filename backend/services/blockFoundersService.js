/**
 * Block Founders (Wave 3, final slice) — the growth mechanic.
 *
 * Three moves, all riding live infrastructure:
 *   RANKS   "Verified home #2 on your block" — a permanent, scarce
 *           claim per geohash-6 cell, assigned first-come on a
 *           verified home's first read. The UNIQUE (geohash6, rank)
 *           constraint is the arbiter: racing instances retry into
 *           the next free rank, nobody mints a duplicate. Founding
 *           order is recorded from feature launch (documented — we do
 *           not fake historical order).
 *   METERS  per-section unlock progress ("bill benchmark: 4 of 10
 *           verified homes") from the density primitive's insider
 *           read. Shown ONLY to a T4 occupant of the cell — the route
 *           enforces it; previews and lower tiers keep the k-anon
 *           buckets.
 *   INVITES real Lob postcards to nearby addresses, wearing the
 *           neighbor-messaging safeguards wholesale: template-only
 *           content, sender anonymized to their street (never name or
 *           unit), 3/sender/week cap, 90-day per-recipient dedup
 *           across ALL senders (no pile-on), a permanent per-address
 *           opt-out registry checked before every send, and no
 *           invites to addresses already on Pantopus.
 */

const crypto = require('crypto');
const supabaseAdmin = require('../config/supabaseAdmin');
const logger = require('../utils/logger');
const { encodeGeohash } = require('../utils/geohash');
const { computeAddressHash } = require('../utils/normalizeAddress');
const { readRawCountForVerifiedInsider } = require('./place/densityReader');
const realRentService = require('./realRentService');
const { mailVendorService } = require('./addressValidation');
const { generateLetterCode } = require('./residencyLetterService');

const WEEKLY_INVITE_CAP = 3;
const RECIPIENT_DEDUP_DAYS = 90;
const RANK_RETRY_LIMIT = 5;
const WEEK_MS = 7 * 24 * 60 * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;

// The unlock meters.
//
// Two different readings, deliberately:
//   * `real_rent` counts RENT REPORTS in the cell, not verified homes.
//     It is the flagship unlock and the only meter whose progress the
//     resident can move by asking a neighbor — a block of 25 verified
//     owner-occupiers has no rents to pool, and a meter that claimed
//     otherwise would be a lie the section then fails to honor.
//   * the others count VERIFIED HOMES, which is what actually gates
//     them: bill_benchmark's floor is the audited k-anon minimum, and
//     the density bucket flips 'few' → 'growing' at 25.
const METERS = [
  { id: 'real_rent', label: 'Real rents on your block', needed: 10, source: 'rent_reports' },
  { id: 'bill_benchmark', label: 'Bill benchmark', needed: 10, source: 'verified_homes' },
  { id: 'block_growing', label: '“Growing block” status', needed: 25, source: 'verified_homes' },
];

class BlockFoundersError extends Error {
  constructor(message, code) {
    super(message);
    this.code = code;
  }
}

function cellForHome(home) {
  // Number(null) is 0 — a finite value — so a missing coordinate must
  // be rejected BEFORE coercion or the home lands in the (0,0) cell.
  if (home.map_center_lat == null || home.map_center_lng == null) return null;
  const lat = Number(home.map_center_lat);
  const lng = Number(home.map_center_lng);
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;
  return encodeGeohash(lat, lng, 6);
}

function streetOnly(home) {
  const line1 = String(home.address || '').split(',')[0].trim();
  // Drop the leading house number so the card names the street, never
  // the sender's exact house.
  return line1.replace(/^[0-9][0-9A-Za-z-]*\s+/, '') || 'your street';
}

/**
 * Assign (or return) the home's permanent founding rank in its cell.
 * First-come: rank = current row count + 1, retried on the unique
 * constraint when instances race — the loser takes the next number.
 * @returns {Promise<object|null>} the BlockFounder row, or null when
 *   the home has no usable coordinates.
 */
async function ensureFounderRank({ home, userId }) {
  const geohash6 = cellForHome(home);
  if (!geohash6) return null;

  const { data: existing } = await supabaseAdmin
    .from('BlockFounder')
    .select('*')
    .eq('home_id', home.id)
    .maybeSingle();
  if (existing) return existing;

  for (let attempt = 0; attempt < RANK_RETRY_LIMIT; attempt += 1) {
    const { count } = await supabaseAdmin
      .from('BlockFounder')
      .select('id', { count: 'exact', head: true })
      .eq('geohash6', geohash6);
    const rank = (count || 0) + 1 + attempt;

    const { data: inserted, error } = await supabaseAdmin
      .from('BlockFounder')
      .insert({
        id: crypto.randomUUID(),
        home_id: home.id,
        user_id: userId,
        geohash6,
        rank,
        established_at: new Date().toISOString(),
      })
      .select()
      .maybeSingle();
    if (inserted) return inserted;
    // Unique violation on (geohash6, rank) → someone else took it; on
    // (home_id) → someone else ranked THIS home concurrently: re-read.
    const { data: raced } = await supabaseAdmin
      .from('BlockFounder')
      .select('*')
      .eq('home_id', home.id)
      .maybeSingle();
    if (raced) return raced;
    if (error) logger.warn('blockFounders: rank insert retry', { homeId: home.id, rank, error: error.message });
  }
  logger.error('blockFounders: rank assignment exhausted retries', { homeId: home.id, geohash6 });
  return null;
}

/**
 * The founders panel for a VERIFIED occupant (route-gated T4):
 * their permanent rank, the cell's verified count, the unlock meters,
 * and this week's remaining invite budget.
 */
async function getBlockStatus({ home, userId }) {
  const geohash6 = cellForHome(home);
  if (!geohash6) return { available: false, reason: 'NO_COORDINATES' };

  const [founder, verifiedCount, rentReports, { count: weekCount }] = await Promise.all([
    ensureFounderRank({ home, userId }),
    readRawCountForVerifiedInsider(geohash6),
    realRentService.countCellReports(geohash6),
    supabaseAdmin
      .from('BlockInvite')
      .select('id', { count: 'exact', head: true })
      .eq('sender_user_id', userId)
      .gte('created_at', new Date(Date.now() - WEEK_MS).toISOString()),
  ]);

  const readingFor = (source) => (source === 'rent_reports' ? rentReports : verifiedCount);

  return {
    available: true,
    rank: founder ? founder.rank : null,
    established_at: founder ? founder.established_at : null,
    verified_count: verifiedCount,
    rent_reports: rentReports,
    meters: METERS.map((m) => {
      const reading = readingFor(m.source);
      return {
        id: m.id,
        label: m.label,
        current: Math.min(reading, m.needed),
        needed: m.needed,
        unlocked: reading >= m.needed,
      };
    }),
    invites_remaining: Math.max(0, WEEKLY_INVITE_CAP - (weekCount || 0)),
    invites_weekly_cap: WEEKLY_INVITE_CAP,
  };
}

// ── Invites ──────────────────────────────────────────────────

function cleanAddressInput(recipient) {
  const line1 = String((recipient && recipient.line1) || '').replace(/\s+/g, ' ').trim().slice(0, 100);
  const city = String((recipient && recipient.city) || '').replace(/\s+/g, ' ').trim().slice(0, 60);
  // Validate BEFORE truncating — 'Oregon'.slice(0, 2) would silently
  // pass as 'OR' when the sender typed the wrong thing.
  const stateRaw = String((recipient && recipient.state) || '').trim();
  const state = /^[A-Za-z]{2}$/.test(stateRaw) ? stateRaw.toUpperCase() : '';
  const zip = String((recipient && recipient.zip) || '').trim().slice(0, 10);
  if (!line1 || !city || !/^[A-Z]{2}$/.test(state) || !/^\d{5}(-\d{4})?$/.test(zip)) {
    throw new BlockFoundersError('Enter the neighbor\'s street address, city, state, and ZIP.', 'BAD_ADDRESS');
  }
  return { line1, city, state, zip };
}

function inviteFrontHtml() {
  return `<html><body style="width:6.25in;height:4.25in;margin:0;display:flex;align-items:center;justify-content:center;background:#0284c7;color:#fff;font-family:Helvetica,Arial,sans-serif;">
    <div style="text-align:center;padding:0.4in;">
      <div style="font-size:34px;font-weight:bold;">Your block is forming</div>
      <div style="font-size:16px;margin-top:12px;">Verified neighbors on your street are already comparing bills, flood costs, and more.</div>
    </div></body></html>`;
}

function inviteBackHtml({ street, verifiedCount, optOutCode }) {
  const countLine = verifiedCount >= 2
    ? `${verifiedCount} homes near you are already verified.`
    : 'Your neighbors are starting to verify their homes.';
  return `<html><body style="width:6.25in;height:4.25in;margin:0;font-family:Helvetica,Arial,sans-serif;color:#111827;">
    <div style="padding:0.45in 0.5in;">
      <div style="font-size:15px;line-height:1.5;">A verified neighbor on <b>${street}</b> invited this address to Pantopus. ${countLine}</div>
      <div style="font-size:15px;line-height:1.5;margin-top:10px;">See what your address already knows — flood risk, air quality, what neighbors pay for utilities — free, no account needed:</div>
      <div style="font-size:22px;font-weight:bold;margin-top:10px;">pantopus.com/start</div>
      <div style="font-size:10px;color:#6b7280;margin-top:26px;line-height:1.5;">This invitation was mailed through Pantopus; the sender chose the address but never wrote this text, and your address was not shared with anyone. To never receive another: pantopus.com/no-mail/${optOutCode}</div>
    </div></body></html>`;
}

/**
 * Send one template postcard invite. Every safeguard is checked
 * server-side, in order, before any money is spent:
 * opt-out registry → already-on-Pantopus → 90-day recipient dedup →
 * sender weekly cap.
 */
async function sendInvite({ home, userId, recipient }) {
  const geohash6 = cellForHome(home);
  if (!geohash6) throw new BlockFoundersError('This home has no usable location.', 'NO_COORDINATES');

  const address = cleanAddressInput(recipient);
  const addressHash = computeAddressHash(address.line1, '', address.city, address.state, address.zip);

  const { data: optedOut } = await supabaseAdmin
    .from('BlockInviteOptOut')
    .select('address_hash')
    .eq('address_hash', addressHash)
    .maybeSingle();
  if (optedOut) {
    throw new BlockFoundersError('That address has asked not to receive invitations.', 'OPTED_OUT');
  }

  const { data: existingHome } = await supabaseAdmin
    .from('Home')
    .select('id')
    .eq('address_hash', addressHash)
    .maybeSingle();
  if (existingHome) {
    throw new BlockFoundersError('That address is already on Pantopus.', 'ALREADY_MEMBER');
  }

  const dedupSince = new Date(Date.now() - RECIPIENT_DEDUP_DAYS * DAY_MS).toISOString();
  const { count: recentToRecipient } = await supabaseAdmin
    .from('BlockInvite')
    .select('id', { count: 'exact', head: true })
    .eq('recipient_address_hash', addressHash)
    .gte('created_at', dedupSince);
  if ((recentToRecipient || 0) > 0) {
    throw new BlockFoundersError('That address was invited recently — one invitation per season, from anyone.', 'RECENTLY_INVITED');
  }

  const { count: weekCount } = await supabaseAdmin
    .from('BlockInvite')
    .select('id', { count: 'exact', head: true })
    .eq('sender_user_id', userId)
    .gte('created_at', new Date(Date.now() - WEEK_MS).toISOString());
  if ((weekCount || 0) >= WEEKLY_INVITE_CAP) {
    throw new BlockFoundersError(`You've sent this week's ${WEEKLY_INVITE_CAP} invitations.`, 'WEEKLY_CAP');
  }

  const optOutCode = generateLetterCode();
  const verifiedCount = await readRawCountForVerifiedInsider(geohash6);
  const provider = mailVendorService.getProvider();
  let lob;
  try {
    lob = await provider.sendCustomPostcard(address, {
      description: 'Pantopus block invite',
      frontHtml: inviteFrontHtml(),
      backHtml: inviteBackHtml({ street: streetOnly(home), verifiedCount, optOutCode }),
    });
  } catch (err) {
    logger.error('blockFounders: invite send failed', { userId, error: err.message });
    throw new BlockFoundersError('The postcard could not be sent. Try again shortly.', 'SEND_FAILED');
  }

  const { data: saved, error } = await supabaseAdmin
    .from('BlockInvite')
    .insert({
      id: crypto.randomUUID(),
      sender_home_id: home.id,
      sender_user_id: userId,
      geohash6,
      recipient_address_hash: addressHash,
      recipient_address: address,
      opt_out_code: optOutCode,
      lob_id: lob.vendorJobId,
      status: 'created',
      created_at: new Date().toISOString(),
    })
    .select()
    .single();
  if (error) {
    // The card is in the mail either way — record loss is a logged
    // inconsistency, not a user-facing failure.
    logger.error('blockFounders: invite record insert failed', { userId, error: error.message });
    return { sent: true, invites_remaining: Math.max(0, WEEKLY_INVITE_CAP - (weekCount || 0) - 1) };
  }
  logger.info('blockFounders: invite sent', { inviteId: saved.id, geohash6 });
  return { sent: true, invites_remaining: Math.max(0, WEEKLY_INVITE_CAP - (weekCount || 0) - 1) };
}

/**
 * Redeem an opt-out code from a mailed card: permanently silences
 * invites to that recipient address from all senders. Idempotent;
 * unknown codes are a uniform { done: false } (no oracle).
 */
async function redeemOptOut(code) {
  const normalized = String(code || '').toUpperCase().replace(/[^A-Z2-9]/g, '');
  if (normalized.length !== 16) return { done: false };
  const formatted = normalized.match(/.{4}/g).join('-');

  const { data: invite } = await supabaseAdmin
    .from('BlockInvite')
    .select('recipient_address_hash')
    .eq('opt_out_code', formatted)
    .maybeSingle();
  if (!invite) return { done: false };

  const { error } = await supabaseAdmin
    .from('BlockInviteOptOut')
    .upsert({ address_hash: invite.recipient_address_hash, created_at: new Date().toISOString() });
  if (error) {
    logger.error('blockFounders: opt-out upsert failed', { error: error.message });
    return { done: false };
  }
  return { done: true };
}

module.exports = {
  getBlockStatus,
  sendInvite,
  redeemOptOut,
  BlockFoundersError,
  WEEKLY_INVITE_CAP,
  // Exported for testing.
  ensureFounderRank,
  cellForHome,
  streetOnly,
  cleanAddressInput,
  METERS,
};
