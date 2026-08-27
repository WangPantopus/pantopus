// ============================================================
// BEFORE-YOU-SIGN SCOUT (Wave 4)
//
//   GET /api/scout?address=…&asking_rent=…&year_built=…
//
// T1: an account, no address claim, no verification. That is deliberate
// and is the resolution of the roadmap's locked-teaser tension — the
// person using Scout is considering an address they do NOT live at, so
// they cannot be a verified resident of it, and gating this behind a
// postcard would make it unusable by the only audience it serves. See
// services/scoutService.js for the full reasoning.
//
// Rate-limited: every call geocodes and fans out to several external
// providers, and unlike the anonymous preview this one is authenticated,
// so an account is the unit to limit.
//
// NOT mounted under /api/homes — Scout has no home. Wiring it there
// would put it behind home-permission middleware for a home that by
// definition does not exist.
// ============================================================

const express = require('express');
const router = express.Router();

const verifyToken = require('../middleware/verifyToken');
const { aiDraftLimiter } = require('../middleware/rateLimiter');
const scoutService = require('../services/scoutService');
const { geocodeUsAddress } = require('./public');
const logger = require('../utils/logger');

function positiveNumber(value) {
  const n = Number(value);
  return Number.isFinite(n) && n > 0 ? n : undefined;
}

// GET /api/scout — the report for an address you are considering
router.get('/', verifyToken, aiDraftLimiter, async (req, res) => {
  try {
    const rawAddress = typeof req.query.address === 'string' ? req.query.address.trim() : '';
    if (!rawAddress) {
      return res.status(400).json({ error: 'An address query parameter is required.' });
    }
    if (rawAddress.length > 200) {
      return res.status(400).json({ error: 'That address is too long.' });
    }

    const place = await geocodeUsAddress(rawAddress);
    if (!place.ok) {
      return res.json({
        status: 'unsupported_region',
        message: 'Scout is U.S.-only for now',
      });
    }

    const report = await scoutService.getScoutReport(place, {
      askingRent: positiveNumber(req.query.asking_rent),
      yearBuilt: positiveNumber(req.query.year_built),
    });

    // The typed address is NOT persisted, exactly as the anonymous
    // preview promises: someone checking out an address they might rent
    // has not agreed to a record of having looked.
    return res.json({ status: 'ready', scout: report });
  } catch (err) {
    logger.error('scout: report failed', { error: err.message });
    return res.status(500).json({ error: 'Could not build the report.' });
  }
});

module.exports = router;
