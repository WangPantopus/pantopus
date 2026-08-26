// ============================================================
// TEST: Block Founders (Wave 3, final slice)
//
// The invariants:
//   * ranks are permanent, first-come, and idempotent per home;
//   * the meters and raw count reach ONLY verified occupants — an
//     unverified member gets the T4 gate, before any data is read;
//   * every invite safeguard fires BEFORE money is spent, in order:
//     opt-out → already-on-Pantopus → recipient dedup → weekly cap;
//   * the card's sender line names the street, never the house
//     number, name, or unit;
//   * the opt-out redemption is idempotent and oracle-free.
// ============================================================

jest.mock('../services/addressValidation', () => ({
  mailVendorService: {
    getProvider: jest.fn(),
  },
}));

const express = require('express');
const request = require('supertest');
const { resetTables, seedTable, getTable } = require('./__mocks__/supabaseAdmin');

const { mailVendorService } = require('../services/addressValidation');
const blockFoundersService = require('../services/blockFoundersService');
const { streetOnly, cleanAddressInput, cellForHome, WEEKLY_INVITE_CAP } = blockFoundersService;
const { encodeGeohash } = require('../utils/geohash');
const { computeAddressHash } = require('../utils/normalizeAddress');
const blockFoundersRoutes = require('../routes/blockFounders');
const publicRoutes = require('../routes/public');

const USER = 'bf-user-1';
const NEIGHBOR_MEMBER = 'bf-user-2';
const HOME_ID = 'home-bf-1';
const LAT = 45.51;
const LNG = -122.65;
const CELL = encodeGeohash(LAT, LNG, 6);

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use('/api/homes', blockFoundersRoutes);
  app.use('/api/public', publicRoutes);
  return app;
}

const HOME_ROW = {
  id: HOME_ID,
  owner_id: USER,
  address: '1421 SE Oak St, Portland, OR 97214',
  map_center_lat: LAT,
  map_center_lng: LNG,
  address_hash: 'own-home-hash',
};

function seedVerified({ verification = 'verified' } = {}) {
  seedTable('Home', [HOME_ROW]);
  seedTable('HomeOccupancy', [
    { id: 'bf-occ-1', home_id: HOME_ID, user_id: USER, is_active: true, role: 'owner', role_base: 'owner', verification_status: verification },
    { id: 'bf-occ-2', home_id: HOME_ID, user_id: NEIGHBOR_MEMBER, is_active: true, role: 'member', role_base: 'member', verification_status: 'pending' },
  ]);
  seedTable('NeighborhoodPreview', [{ geohash: CELL, verified_users_count: 4 }]);
}

const RECIPIENT = { line1: '1425 SE Oak St', city: 'Portland', state: 'OR', zip: '97214' };
const RECIPIENT_HASH = computeAddressHash('1425 SE Oak St', '', 'Portland', 'OR', '97214');

function mockProvider() {
  const send = jest.fn().mockResolvedValue({ vendorJobId: 'psc_1', status: 'created' });
  mailVendorService.getProvider.mockReturnValue({ sendCustomPostcard: send });
  return send;
}

beforeEach(() => {
  resetTables();
  mockProvider();
});

// ── Pure helpers ─────────────────────────────────────────────

describe('helpers', () => {
  test('streetOnly names the street, never the house number', () => {
    expect(streetOnly({ address: '1421 SE Oak St, Portland, OR' })).toBe('SE Oak St');
    expect(streetOnly({ address: '221B Baker Street, London' })).toBe('Baker Street');
    expect(streetOnly({ address: '' })).toBe('your street');
  });

  test('cleanAddressInput fails closed on junk', () => {
    expect(() => cleanAddressInput({ line1: 'x', city: 'y', state: 'Oregon', zip: '97214' })).toThrow();
    expect(() => cleanAddressInput({ line1: 'x', city: 'y', state: 'OR', zip: 'abc' })).toThrow();
    expect(cleanAddressInput(RECIPIENT).state).toBe('OR');
  });

  test('cellForHome fails closed without coordinates', () => {
    expect(cellForHome({ map_center_lat: null, map_center_lng: null })).toBeNull();
    expect(cellForHome(HOME_ROW)).toBe(CELL);
  });
});

// ── The status panel ─────────────────────────────────────────

describe('block status', () => {
  test('an unverified member is gated before any insider data is read', async () => {
    seedVerified();
    const res = await request(buildApp())
      .get(`/api/homes/${HOME_ID}/block-founders`)
      .set('x-test-user-id', NEIGHBOR_MEMBER);
    expect(res.status).toBe(403);
    expect(res.body.code).toBe('VERIFICATION_REQUIRED');
    expect(JSON.stringify(res.body)).not.toContain('verified_count');
  });

  test('a verified occupant gets a permanent rank, the meters, and the invite budget', async () => {
    seedVerified();
    const app = buildApp();
    const res = await request(app)
      .get(`/api/homes/${HOME_ID}/block-founders`)
      .set('x-test-user-id', USER);
    expect(res.status).toBe(200);
    const block = res.body.block;
    expect(block.rank).toBe(1);
    expect(block.verified_count).toBe(4);
    const bill = block.meters.find((m) => m.id === 'bill_benchmark');
    expect(bill).toMatchObject({ current: 4, needed: 10, unlocked: false });
    expect(block.invites_remaining).toBe(WEEKLY_INVITE_CAP);

    // Idempotent: a second read keeps the same rank, mints nothing new.
    const again = await request(app)
      .get(`/api/homes/${HOME_ID}/block-founders`)
      .set('x-test-user-id', USER);
    expect(again.body.block.rank).toBe(1);
    expect(getTable('BlockFounder')).toHaveLength(1);
  });

  // The flagship meter reads RENT REPORTS, not verified homes — a block
  // of verified owner-occupiers has no rents to pool, and a meter that
  // counted them would promise an unlock the section then can't honor.
  test('the real_rent meter tracks rent reports, not the verified-home count', async () => {
    seedVerified(); // 4 verified homes in the cell, zero rent reports
    const app = buildApp();

    const empty = await request(app)
      .get(`/api/homes/${HOME_ID}/block-founders`)
      .set('x-test-user-id', USER);
    const emptyRent = empty.body.block.meters.find((m) => m.id === 'real_rent');
    expect(emptyRent).toMatchObject({ current: 0, needed: 10, unlocked: false });
    expect(empty.body.block.rent_reports).toBe(0);
    // The verified-home meters still read the density, unchanged.
    expect(empty.body.block.meters.find((m) => m.id === 'bill_benchmark').current).toBe(4);

    // Two neighbors share their rent; only the rent meter moves.
    const cell = require('../services/realRentService').cellForHome(HOME_ROW);
    seedTable('HomeRentReport', [
      { id: 'rr1', home_id: 'other-1', user_id: 'u1', geohash6: cell, monthly_rent_cents: 210000, bedrooms: 2 },
      { id: 'rr2', home_id: 'other-2', user_id: 'u2', geohash6: cell, monthly_rent_cents: 235000, bedrooms: 2 },
    ]);

    const withRents = await request(app)
      .get(`/api/homes/${HOME_ID}/block-founders`)
      .set('x-test-user-id', USER);
    expect(withRents.body.block.rent_reports).toBe(2);
    expect(withRents.body.block.meters.find((m) => m.id === 'real_rent').current).toBe(2);
    expect(withRents.body.block.meters.find((m) => m.id === 'bill_benchmark').current).toBe(4);
  });

  test('ranks are first-come within a cell', async () => {
    seedVerified();
    seedTable('BlockFounder', [{
      id: 'bf-0', home_id: 'other-home', user_id: 'other-user', geohash6: CELL, rank: 1,
      established_at: '2026-08-01T00:00:00.000Z',
    }]);
    const res = await request(buildApp())
      .get(`/api/homes/${HOME_ID}/block-founders`)
      .set('x-test-user-id', USER);
    expect(res.body.block.rank).toBe(2);
  });
});

// ── Invites: the safeguard ladder ────────────────────────────

describe('invites', () => {
  function invite(app, body = { recipient: RECIPIENT }, userId = USER) {
    return request(app)
      .post(`/api/homes/${HOME_ID}/block-founders/invites`)
      .set('x-test-user-id', userId)
      .send(body);
  }

  test('a clean send mails the card, records the row, and burns budget', async () => {
    seedVerified();
    const send = mockProvider();
    const res = await invite(buildApp());
    expect(res.status).toBe(201);
    expect(res.body).toEqual({ sent: true, invites_remaining: WEEKLY_INVITE_CAP - 1 });

    expect(send).toHaveBeenCalledTimes(1);
    const [addr, card] = send.mock.calls[0];
    expect(addr.line1).toBe('1425 SE Oak St');
    // Sender anonymity: the card names the street, never the house.
    expect(card.backHtml).toContain('SE Oak St');
    expect(card.backHtml).not.toContain('1421');
    expect(card.backHtml).toContain('pantopus.com/no-mail/');

    const rows = getTable('BlockInvite');
    expect(rows).toHaveLength(1);
    expect(rows[0].recipient_address_hash).toBe(RECIPIENT_HASH);
  });

  test('an opted-out address is refused before any send', async () => {
    seedVerified();
    seedTable('BlockInviteOptOut', [{ address_hash: RECIPIENT_HASH, created_at: '2026-08-01T00:00:00.000Z' }]);
    const send = mockProvider();
    const res = await invite(buildApp());
    expect(res.status).toBe(400);
    expect(res.body.code).toBe('OPTED_OUT');
    expect(send).not.toHaveBeenCalled();
  });

  test('an address already on Pantopus is refused', async () => {
    seedVerified();
    seedTable('Home', [HOME_ROW, { id: 'neighbor-home', owner_id: 'x', address: '1425 SE Oak St', address_hash: RECIPIENT_HASH }]);
    const res = await invite(buildApp());
    expect(res.status).toBe(400);
    expect(res.body.code).toBe('ALREADY_MEMBER');
  });

  test('a recently invited address is refused for ANY sender (no pile-on)', async () => {
    seedVerified();
    seedTable('BlockInvite', [{
      id: 'prev', sender_home_id: 'someone-else', sender_user_id: 'someone-else', geohash6: CELL,
      recipient_address_hash: RECIPIENT_HASH, recipient_address: RECIPIENT,
      opt_out_code: 'AAAA-BBBB-CCCC-DDDD', status: 'created',
      created_at: new Date(Date.now() - 10 * 24 * 60 * 60 * 1000).toISOString(),
    }]);
    const res = await invite(buildApp());
    expect(res.status).toBe(400);
    expect(res.body.code).toBe('RECENTLY_INVITED');
  });

  test('the weekly cap returns 429 and spends nothing', async () => {
    seedVerified();
    seedTable('BlockInvite', Array.from({ length: WEEKLY_INVITE_CAP }, (_, i) => ({
      id: `w${i}`, sender_home_id: HOME_ID, sender_user_id: USER, geohash6: CELL,
      recipient_address_hash: `hash-${i}`, recipient_address: RECIPIENT,
      opt_out_code: `AAAA-BBBB-CCCC-DDD${i}`, status: 'created',
      created_at: new Date(Date.now() - 60 * 60 * 1000).toISOString(),
    })));
    const send = mockProvider();
    const res = await invite(buildApp(), { recipient: { ...RECIPIENT, line1: '9 New St' } });
    expect(res.status).toBe(429);
    expect(res.body.code).toBe('WEEKLY_CAP');
    expect(send).not.toHaveBeenCalled();
  });

  test('an unverified member cannot invite at all', async () => {
    seedVerified();
    const res = await invite(buildApp(), { recipient: RECIPIENT }, NEIGHBOR_MEMBER);
    expect(res.status).toBe(403);
  });
});

// ── The recipient's kill switch ──────────────────────────────

describe('opt-out redemption', () => {
  test('a mailed code opts the address out permanently and idempotently; unknown codes are uniform', async () => {
    seedVerified();
    const app = buildApp();
    await request(app)
      .post(`/api/homes/${HOME_ID}/block-founders/invites`)
      .set('x-test-user-id', USER)
      .send({ recipient: RECIPIENT });
    const code = getTable('BlockInvite')[0].opt_out_code;

    const redeemed = await request(app).post(`/api/public/block-invites/opt-out/${code}`);
    expect(redeemed.body).toEqual({ done: true });
    expect(getTable('BlockInviteOptOut')[0].address_hash).toBe(RECIPIENT_HASH);

    // Idempotent, and the address now refuses future invites.
    expect((await request(app).post(`/api/public/block-invites/opt-out/${code}`)).body.done).toBe(true);
    const blocked = await request(app)
      .post(`/api/homes/${HOME_ID}/block-founders/invites`)
      .set('x-test-user-id', USER)
      .send({ recipient: RECIPIENT });
    expect(blocked.body.code).toBe('OPTED_OUT');

    expect((await request(app).post('/api/public/block-invites/opt-out/XXXX-YYYY-ZZZZ-0000')).body).toEqual({ done: false });
  });
});
