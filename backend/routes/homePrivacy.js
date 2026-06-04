/**
 * Home Privacy Routes
 *
 * P3F / A14.2 — Backs the per-home "Security" screen's 9 consumer privacy
 * toggles (3 groups × 3): Access control, Privacy, Documents. GET returns
 * the persisted toggle set (defaults applied on first read), PATCH updates
 * any subset (the client flips optimistically and PATCHes the single key).
 *
 * Distinct from `homeOwnership /:id/security`, which governs ownership /
 * claim-policy enums — a different concept. Gated on `security.manage`,
 * the same permission as the ownership security route, since this is the
 * per-home Security surface.
 *
 * Mounted at /api/homes alongside the other home routers. The `/:id/privacy`
 * path is two segments, so it never collides with home.js's `/:id`.
 */

const express = require('express');
const router = express.Router();
const Joi = require('joi');
const supabaseAdmin = require('../config/supabaseAdmin');
const verifyToken = require('../middleware/verifyToken');
const validate = require('../middleware/validate');
const { checkHomePermission } = require('../utils/homePermissions');
const logger = require('../utils/logger');

// The 9 toggles, in design order. Each column is the snake_case of the
// camelCase row id the clients send — kept in lockstep with the iOS
// `HomeSecurityViewModel.Toggles` and Android `HomeSecurityToggles`.
const TOGGLE_KEYS = [
  // Access control
  'guest_approval',
  'member_name_visibility',
  'address_precision',
  // Privacy
  'activity_visibility',
  'map_opt_out',
  'notification_previews',
  // Documents
  'doc_lock',
  'photo_blur',
  'vault_auto_lock',
];

// Backend defaults mirror the design's "balanced setup" baseline (5/9 on)
// and the column DEFAULTs in migration 153, so a home with no row yet reads
// the same calm state the screen ships with.
const DEFAULTS = {
  guest_approval: true,
  member_name_visibility: true,
  address_precision: false,
  activity_visibility: true,
  map_opt_out: false,
  notification_previews: true,
  doc_lock: true,
  photo_blur: false,
  vault_auto_lock: false,
};

// Every toggle is optional; PATCH must carry at least one.
const updatePrivacySchema = Joi.object(
  TOGGLE_KEYS.reduce((acc, key) => {
    acc[key] = Joi.boolean();
    return acc;
  }, {}),
).min(1);

/** Project a HomePrivacy row (or null) into the API shape, defaults applied. */
function serialize(homeId, row) {
  const privacy = { home_id: homeId };
  for (const key of TOGGLE_KEYS) {
    privacy[key] = row && typeof row[key] === 'boolean' ? row[key] : DEFAULTS[key];
  }
  return { privacy };
}

/**
 * GET /:id/privacy
 * Read the per-home privacy toggle set (defaults applied if no row yet).
 */
router.get('/:id/privacy', verifyToken, async (req, res) => {
  try {
    const homeId = req.params.id;
    const userId = req.user.id;

    const access = await checkHomePermission(homeId, userId, 'security.manage');
    if (!access.hasAccess) {
      return res.status(403).json({ error: 'Not authorized' });
    }

    const { data: row } = await supabaseAdmin
      .from('HomePrivacy')
      .select('*')
      .eq('home_id', homeId)
      .maybeSingle();

    return res.json(serialize(homeId, row));
  } catch (err) {
    logger.error('Failed to fetch home privacy', { error: err.message });
    return res.status(500).json({ error: 'Failed to fetch privacy settings' });
  }
});

/**
 * PATCH /:id/privacy
 * Update a subset of toggles. Upserts the single per-home row, seeding
 * defaults for the first write so a partial PATCH still persists a complete
 * record.
 */
router.patch('/:id/privacy', verifyToken, validate(updatePrivacySchema), async (req, res) => {
  try {
    const homeId = req.params.id;
    const userId = req.user.id;

    const access = await checkHomePermission(homeId, userId, 'security.manage');
    if (!access.hasAccess) {
      return res.status(403).json({ error: 'Not authorized' });
    }

    const updates = {};
    for (const key of TOGGLE_KEYS) {
      if (typeof req.body[key] === 'boolean') updates[key] = req.body[key];
    }

    const { data: existing } = await supabaseAdmin
      .from('HomePrivacy')
      .select('*')
      .eq('home_id', homeId)
      .maybeSingle();

    // DEFAULTS first, then any existing row, then this PATCH's keys win.
    const merged = {
      ...DEFAULTS,
      ...(existing || {}),
      ...updates,
      home_id: homeId,
      updated_at: new Date().toISOString(),
    };

    const { data: saved, error } = await supabaseAdmin
      .from('HomePrivacy')
      .upsert(merged, { onConflict: 'home_id' })
      .select()
      .single();

    if (error) {
      logger.error('Failed to upsert home privacy', { error: error.message });
      return res.status(500).json({ error: 'Failed to update privacy settings' });
    }

    return res.json(serialize(homeId, saved));
  } catch (err) {
    logger.error('Failed to update home privacy', { error: err.message });
    return res.status(500).json({ error: 'Failed to update privacy settings' });
  }
});

module.exports = router;
