// ============================================================
// UNLISTED ROUTES (Wave 4 — the acquisition slice)
//
//   GET /api/homes/:id/unlisted                    profile + my progress
//   PUT /api/homes/:id/unlisted/removals/:brokerId record a step
//
// The anonymous half lives in routes/public.js
// (GET /api/public/unlisted?address=…) because it must sit behind the
// same preview limiter as the rest of T0 and must persist nothing.
//
// Progress is PERSONAL, not household: reads and writes are scoped to
// the caller inside the service. A household member must not be able to
// see that someone is erasing their address — that is precisely the
// fact most worth protecting here.
//
// Gate: home access only, NOT verification. Someone who has just claimed
// their address is exactly who needs this, and making them wait for a
// postcard to start removing themselves from people-search sites would
// invert the product.
// ============================================================

const express = require('express');
const router = express.Router();

const verifyToken = require('../middleware/verifyToken');
const { checkHomePermission } = require('../utils/homePermissions');
const supabaseAdmin = require('../config/supabaseAdmin');
const unlistedService = require('../services/unlistedService');
const logger = require('../utils/logger');

// GET /api/homes/:id/unlisted — the state profile plus my own progress
router.get('/:id/unlisted', verifyToken, async (req, res) => {
  const { id } = req.params;
  const userId = req.user.id;
  try {
    const access = await checkHomePermission(id, userId);
    if (!access.hasAccess) {
      return res.status(403).json({ error: 'You do not have access to this place.' });
    }

    const { data: home } = await supabaseAdmin
      .from('Home')
      .select('id, state')
      .eq('id', id)
      .maybeSingle();
    if (!home) return res.status(404).json({ error: 'Home not found.' });

    const profile = unlistedService.getExposureProfile(home.state);
    const removals = await unlistedService.listRemovals({ homeId: id, userId });
    return res.json({
      unlisted: {
        ...profile,
        // null (not []) when the read FAILED, so the client can say so
        // rather than showing a confident empty checklist.
        removals,
      },
    });
  } catch (err) {
    logger.error('unlisted: profile failed', { homeId: id, userId, error: err.message });
    return res.status(500).json({ error: 'Could not load your removal list.' });
  }
});

// PUT /api/homes/:id/unlisted/removals/:brokerId — record a step
router.put('/:id/unlisted/removals/:brokerId', verifyToken, async (req, res) => {
  const { id, brokerId } = req.params;
  const userId = req.user.id;
  try {
    const access = await checkHomePermission(id, userId);
    if (!access.hasAccess) {
      return res.status(403).json({ error: 'You do not have access to this place.' });
    }
    const removal = await unlistedService.setRemovalStatus({
      homeId: id,
      userId,
      brokerId,
      status: req.body && req.body.status,
    });
    return res.json({ removal });
  } catch (err) {
    if (err instanceof unlistedService.UnlistedError) {
      return res.status(400).json({ error: err.message, code: err.code });
    }
    logger.error('unlisted: removal update failed', { homeId: id, userId, error: err.message });
    return res.status(500).json({ error: 'Could not save your progress.' });
  }
});

module.exports = router;
