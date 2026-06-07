// ============================================================
// PLACE INTELLIGENCE ROUTES
//
// GET /api/homes/:id/intelligence — the Place dashboard contract
// (the PlaceIntelligence section envelopes, W0.1) for a claimed home.
//
// Mounted at /api/homes BEFORE the generic home router so the
// two-segment `/:id/intelligence` path resolves here. Thin by design:
// auth + permission gate, then delegate composition to the service.
// ============================================================

const express = require('express');
const router = express.Router();

const verifyToken = require('../middleware/verifyToken');
const { checkHomePermission } = require('../utils/homePermissions');
const placeIntelligenceService = require('../services/placeIntelligenceService');
const logger = require('../utils/logger');

// GET /api/homes/:id/intelligence
router.get('/:id/intelligence', verifyToken, async (req, res) => {
  const { id } = req.params;
  const userId = req.user.id;
  try {
    const access = await checkHomePermission(id, userId);
    if (!access.hasAccess) {
      return res.status(403).json({ error: 'You do not have access to this place.' });
    }

    const intelligence = await placeIntelligenceService.composeHomeIntelligence({
      homeId: id,
      userId,
      access,
    });
    if (!intelligence) {
      return res.status(404).json({ error: 'Home not found' });
    }

    return res.json(intelligence);
  } catch (err) {
    logger.error('Place intelligence error', { error: err.message, homeId: id });
    return res.status(500).json({ error: 'Failed to load place intelligence' });
  }
});

module.exports = router;
