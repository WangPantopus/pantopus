/**
 * Lifecycle regression tests (audit 2026-08-22).
 *
 * These cover the "who still has access after they leave" class of findings,
 * which had no coverage at all before.
 */

const { resetTables, getTable, seedTable } = require('../__mocks__/supabaseAdmin');

jest.mock('../../utils/homePermissions', () => {
  const actual = jest.requireActual('../../utils/homePermissions');
  return { ...actual, writeAuditLog: jest.fn().mockResolvedValue(undefined) };
});

const occupancyAttachService = require('../../services/occupancyAttachService');

beforeEach(() => resetTables());

function seedHomeWithOwner(ownerId = 'user-owner') {
  seedTable('Home', [{ id: 'home-1', owner_id: ownerId, name: 'Test Home' }]);
  seedTable('HomeOccupancy', [{
    id: 'occ-1',
    home_id: 'home-1',
    user_id: ownerId,
    is_active: true,
    role: 'owner',
    role_base: 'owner',
    verification_status: 'verified',
  }]);
}

describe('LIF-01 — moving out revokes legacy ownership', () => {
  test('detach clears Home.owner_id when the departing user is the owner', async () => {
    seedHomeWithOwner('user-owner');

    const res = await occupancyAttachService.detach({
      homeId: 'home-1',
      userId: 'user-owner',
      reason: 'move_out',
      actorId: 'user-owner',
    });

    expect(res.success).toBe(true);

    const home = getTable('Home').find((h) => h.id === 'home-1');
    // Left set, checkHomePermission's isLegacyOwner branch would keep granting
    // this user full control of a home they no longer live in.
    expect(home.owner_id).toBeNull();
  });

  test('detach leaves owner_id alone when someone else moves out', async () => {
    seedHomeWithOwner('user-owner');
    seedTable('HomeOccupancy', [
      ...getTable('HomeOccupancy'),
      {
        id: 'occ-2',
        home_id: 'home-1',
        user_id: 'user-roommate',
        is_active: true,
        role: 'member',
        role_base: 'member',
        verification_status: 'verified',
      },
    ]);

    const res = await occupancyAttachService.detach({
      homeId: 'home-1',
      userId: 'user-roommate',
      reason: 'move_out',
      actorId: 'user-roommate',
    });

    expect(res.success).toBe(true);
    const home = getTable('Home').find((h) => h.id === 'home-1');
    expect(home.owner_id).toBe('user-owner');
  });

  test('the occupancy is deactivated, not deleted, so residency history survives', async () => {
    seedHomeWithOwner('user-owner');

    await occupancyAttachService.detach({
      homeId: 'home-1',
      userId: 'user-owner',
      reason: 'move_out',
      actorId: 'user-owner',
    });

    const occ = getTable('HomeOccupancy').find((o) => o.id === 'occ-1');
    expect(occ).toBeDefined();
    expect(occ.is_active).toBe(false);
    expect(occ.verification_status).toBe('moved_out');
    expect(occ.end_at).toBeTruthy();
  });
});
