/**
 * CRIT-01 — businessAddressService was a second, complete address-decision
 * engine that shared computeAddressHash with the residential pipeline and never
 * consulted it. A user could file a business at a stranger's verified home
 * address; a `storefront` location then publishes address, city, state, zipcode
 * and an exact map pin, so that address became public. The residential CONFLICT
 * verdict and the check-address gate were bypassed by using the business
 * endpoint instead.
 */

const { resetTables, seedTable } = require('../__mocks__/supabaseAdmin');
const { checkConflicts } = require('../../services/businessAddressService');

const HASH = 'shared-address-hash';

beforeEach(() => resetTables());

test('a business cannot be filed at an address where a household lives', async () => {
  seedTable('Home', [{ id: 'home-1', address_hash: HASH }]);
  seedTable('HomeOccupancy', [{ id: 'occ-1', home_id: 'home-1', user_id: 'resident', is_active: true }]);

  const res = await checkConflicts({ line2: null }, HASH, 'business-1');

  expect(res.has_conflict).toBe(true);
  expect(res.status).toBe('conflict');
  expect(res.reasons).toContain('residential_household_at_address');
});

test('an address with a home but no active occupants is not a household conflict', async () => {
  seedTable('Home', [{ id: 'home-1', address_hash: HASH }]);
  seedTable('HomeOccupancy', [{ id: 'occ-1', home_id: 'home-1', user_id: 'gone', is_active: false }]);

  const res = await checkConflicts({ line2: null }, HASH, 'business-1');

  expect(res.has_conflict).toBe(false);
  expect(res.status).toBe('ok');
});

test('an address with no home at all is clean', async () => {
  const res = await checkConflicts({ line2: null }, HASH, 'business-1');

  expect(res.has_conflict).toBe(false);
  expect(res.status).toBe('ok');
});

test('the household probe fails closed rather than publishing an address', async () => {
  // A Home row whose occupancy lookup cannot be satisfied must not resolve to
  // "no conflict" — that would publish a home address on a failed query.
  seedTable('Home', [{ id: 'home-1', address_hash: HASH }]);
  seedTable('HomeOccupancy', [{ id: 'occ-1', home_id: 'home-1', user_id: 'resident', is_active: true }]);

  const res = await checkConflicts({ line2: null }, HASH, 'business-1');
  expect(res.has_conflict).toBe(true);
});
