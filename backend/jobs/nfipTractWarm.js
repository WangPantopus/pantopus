// ============================================================
// JOB: NFIP Tract Warm (Wave 2 — Flood Insurance, In Dollars)
//
// The OpenFEMA NfipPolicies v3 API is ~20 ms/row and 503s on any
// filter beyond a bare censusGeoid range — a tract fetch runs tens of
// seconds, so the flood composer never calls it. Instead the composer
// leaves `pending` markers in PlaceSectionCache, and this job does the
// slow fetches on a schedule: pending tracts first, then the oldest
// expired benchmarks.
//
// Budget: up to 3 tracts per run (~1–2 min worst case), every 15
// minutes — a newly opened dashboard has its premium benchmark within
// minutes, and 288 tract-warms/day comfortably covers organic growth.
// Runs on every instance without leader election, like its siblings;
// a duplicated warm is two identical upserts.
// ============================================================

const { warmPendingTracts } = require('../services/nfipPremiumService');

module.exports = async function nfipTractWarm() {
  return warmPendingTracts({ limit: 3 });
};
