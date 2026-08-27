// ============================================================
// TEST: Unlisted (Wave 4 — the acquisition slice)
//
// The invariants here are almost entirely about what we must NOT do or
// say, because the failure modes are all overclaiming:
//   * we never assert that a person IS listed anywhere — we do not
//     query brokers, so we do not possess that fact;
//   * the anonymous path persists NOTHING and discloses the address to
//     no one;
//   * an unverified state renders as "we could not confirm", never as
//     "your state has no program" — those are different claims and only
//     one is ours to make;
//   * removal progress is personal, not household: a row saying someone
//     is erasing their address is exactly what must not leak sideways.
// ============================================================

const express = require('express');
const request = require('supertest');
const { resetTables, seedTable, getTable } = require('./__mocks__/supabaseAdmin');

const unlistedService = require('../services/unlistedService');
const unlistedRoutes = require('../routes/unlisted');
const { DATA_BROKERS } = require('../data/dataBrokers');

const USER = 'unlisted-user-1';
const OTHER = 'unlisted-user-2';
const HOME_ID = 'home-unlisted-1';

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use('/api/homes', unlistedRoutes);
  return app;
}

function seedHome({ state = 'OR' } = {}) {
  seedTable('Home', [{ id: HOME_ID, owner_id: USER, state, address: '1421 SE Oak St' }]);
  seedTable('HomeOccupancy', [
    { id: 'o1', home_id: HOME_ID, user_id: USER, is_active: true, role: 'owner', role_base: 'owner', verification_status: 'pending' },
  ]);
}

beforeEach(() => resetTables());

// ── The registry's own honesty ───────────────────────────────

describe('the broker registry never claims more than it knows', () => {
  test('no entry carries a "found"-style assertion about a person', () => {
    // We do not query brokers, so no field may imply we checked. This
    // guards the shape of the data, not just today's copy.
    const forbidden = ['found', 'is_listed', 'listed', 'matched', 'hit'];
    for (const broker of DATA_BROKERS) {
      for (const key of Object.keys(broker)) {
        expect(forbidden).not.toContain(key);
      }
    }
  });

  test('every published entry carries the source it was verified against', () => {
    // A wrong opt-out URL sends a frightened person to a dead end, so an
    // unverifiable entry must be omitted rather than guessed.
    for (const broker of DATA_BROKERS) {
      expect(broker.source_url).toMatch(/^https?:\/\//);
      expect(broker.opt_out_url).toMatch(/^https?:\/\//);
      expect(broker.verified_at).toMatch(/^\d{4}-\d{2}-\d{2}/);
      expect(broker.id).toMatch(/^[a-z0-9-]+$/);
    }
  });
});

describe('the exposure profile', () => {
  test('states plainly that we did not look the address up', () => {
    const profile = unlistedService.getExposureProfile('OR');
    // Without this line the page implies a scan it never performed.
    expect(profile.method_note).toBeTruthy();
    expect(profile.method_note).toMatch(/still verifying|do not look/i);
  });

  test('an unverified state is "could not confirm", never "has no program"', () => {
    const profile = unlistedService.getExposureProfile('ZZ');
    // null is the honest answer; the absence of a verified entry is not
    // evidence that the state lacks a program.
    expect(profile.state_program).toBeNull();
  });

  test('groups are ordered and carry only their own brokers', () => {
    const profile = unlistedService.getExposureProfile('OR');
    const seen = new Set();
    for (const group of profile.groups) {
      expect(group.brokers.length).toBeGreaterThan(0);
      for (const b of group.brokers) {
        expect(b.category).toBe(group.category);
        expect(seen.has(b.id)).toBe(false);
        seen.add(b.id);
      }
    }
    expect(profile.broker_count).toBe(seen.size);
  });
});

// ── Progress tracking ────────────────────────────────────────

describe('removal progress', () => {
  test('a claimed (unverified) resident can use it — this must not wait for a postcard', async () => {
    seedHome();
    const res = await request(buildApp())
      .get(`/api/homes/${HOME_ID}/unlisted`)
      .set('x-test-user-id', USER);
    expect(res.status).toBe(200);
    expect(res.body.unlisted.groups).toBeDefined();
    expect(res.body.unlisted.method_note).toBeTruthy();
  });

  test('a non-occupant is refused', async () => {
    seedHome();
    const res = await request(buildApp())
      .get(`/api/homes/${HOME_ID}/unlisted`)
      .set('x-test-user-id', OTHER);
    expect(res.status).toBe(403);
  });

  test('an unknown broker id is refused rather than stored', async () => {
    seedHome();
    const res = await request(buildApp())
      .put(`/api/homes/${HOME_ID}/unlisted/removals/not-a-real-broker`)
      .set('x-test-user-id', USER)
      .send({ status: 'requested' });
    expect(res.status).toBe(400);
    expect(res.body.code).toBe('UNKNOWN_BROKER');
    expect(getTable('UnlistedRemoval')).toHaveLength(0);
  });

  test('an unknown status is refused', async () => {
    seedHome();
    const res = await request(buildApp())
      .put(`/api/homes/${HOME_ID}/unlisted/removals/anything`)
      .set('x-test-user-id', USER)
      .send({ status: 'vanished' });
    expect(res.status).toBe(400);
    expect(getTable('UnlistedRemoval')).toHaveLength(0);
  });

  test('progress is personal — a housemate never sees it', async () => {
    seedHome();
    seedTable('HomeOccupancy', [
      { id: 'o1', home_id: HOME_ID, user_id: USER, is_active: true, role: 'owner', role_base: 'owner', verification_status: 'pending' },
      { id: 'o2', home_id: HOME_ID, user_id: OTHER, is_active: true, role: 'member', role_base: 'member', verification_status: 'pending' },
    ]);
    seedTable('UnlistedRemoval', [
      { id: 'r1', home_id: HOME_ID, user_id: USER, broker_id: 'someone', status: 'requested' },
    ]);

    const mine = await unlistedService.listRemovals({ homeId: HOME_ID, userId: USER });
    const theirs = await unlistedService.listRemovals({ homeId: HOME_ID, userId: OTHER });
    expect(mine).toHaveLength(1);
    // A row saying "this person is erasing their address" must not be
    // visible to anyone else in the household.
    expect(theirs).toHaveLength(0);
  });

  test('a failed read is null, not an empty checklist', async () => {
    // An empty array would render as "nothing done yet" — a confident
    // statement we cannot make when the read failed.
    const supabaseAdmin = require('../config/supabaseAdmin');
    const realFrom = supabaseAdmin.from;
    supabaseAdmin.from = (table) => {
      if (table !== 'UnlistedRemoval') return realFrom.call(supabaseAdmin, table);
      return { select: () => ({ eq: () => ({ eq: () => Promise.resolve({ data: null, error: { message: 'boom' } }) }) }) };
    };
    try {
      const out = await unlistedService.listRemovals({ homeId: HOME_ID, userId: USER });
      expect(out).toBeNull();
    } finally {
      supabaseAdmin.from = realFrom;
    }
  });
});
