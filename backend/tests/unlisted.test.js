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

  test('no entry understates what a site publishes relative to another entry for the same site', () => {
    // Spokeo declared [address, phone, email, age] while AnyWho — which
    // runs on Spokeo's platform and cites the identical source_url —
    // declared relatives and prior addresses on top. Both cards render on
    // one screen, and the omitted token was the dangerous one
    // ("Relatives and household members").
    //
    // Deliberately NOT "same opt-out URL implies same exposes": one
    // suppression portal can legitimately serve brands that publish
    // different fields, and a smaller brand may genuinely publish less
    // than the platform it runs on. The sound rule is directional — a
    // brand cannot publish a field its own platform does not.
    const byId = new Map(DATA_BROKERS.map((b) => [b.id, b]));
    const contradictions = [];
    for (const brand of DATA_BROKERS) {
      if (!brand.same_platform_as) continue;
      const platform = byId.get(brand.same_platform_as);
      expect(platform).toBeTruthy();
      for (const token of brand.exposes) {
        if (!platform.exposes.includes(token)) {
          contradictions.push(`${brand.id} declares "${token}", ${platform.id} does not`);
        }
      }
    }
    expect(contradictions).toEqual([]);
  });
});

describe('the exposure profile', () => {
  test('states plainly that we did not look the address up', () => {
    const profile = unlistedService.getExposureProfile('OR');
    // Without this line the page implies a scan it never performed.
    expect(profile.method_note).toBeTruthy();
    expect(profile.method_note).toMatch(/still verifying|do not look/i);

    // THE COMPLETENESS CLAIM. The note used to end "This is every site
    // that republishes county records" — the one sentence on the page a
    // frightened person would read as permission to stop. It is false:
    // the registry omits anything whose opt-out could not be verified.
    // Telling someone the list is complete fails them the same way
    // telling them their state has no program does.
    //
    // The previous assertion (/still verifying|do not look/) passed on
    // either wording, which is why the overclaim shipped.
    expect(profile.method_note).not.toMatch(/every site/i);
    expect(profile.method_note).not.toMatch(/\ball of (them|the sites)\b/i);
    // Stating the count keeps the sentence tied to the list it describes.
    expect(profile.method_note).toContain(String(profile.broker_count));
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

// ── The state program: three distinct answers ────────────────
// "We verified your state has one", "we verified your state has none",
// and "we could not confirm" are three different claims, and only the
// first two are ours to make. Collapsing the third into the second
// would tell someone in danger that no help exists when we simply did
// not check.
describe('the state escape hatch', () => {
  const { STATE_DISCLOSURE } = require('../data/stateDisclosure');

  test('a verified program carries a name, an official URL, and who qualifies', () => {
    const profile = unlistedService.getExposureProfile('CA');
    expect(profile.state_program.exists).toBe(true);
    expect(profile.state_program.name).toBeTruthy();
    expect(profile.state_program.url).toMatch(/^https?:\/\//);
    expect(profile.state_program.eligibility).toBeTruthy();
    expect(profile.state_program.source_url).toMatch(/^https?:\/\//);
  });

  test('a verified ABSENCE is exists:false with its source — not null', () => {
    // Alabama was checked and genuinely has no substitute-address
    // program. That is a finding, and it still cites where it came from.
    const profile = unlistedService.getExposureProfile('AL');
    expect(profile.state_program).not.toBeNull();
    expect(profile.state_program.exists).toBe(false);
    expect(profile.state_program.source_url).toMatch(/^https?:\/\//);
    // It must still explain what the state DOES offer, if anything.
    expect(profile.state_program.eligibility).toBeTruthy();
  });

  test('an unchecked state is null — never dressed as "no program"', () => {
    expect(unlistedService.getExposureProfile('ZZ').state_program).toBeNull();
    expect(unlistedService.getExposureProfile('').state_program).toBeNull();
    expect(unlistedService.getExposureProfile(null).state_program).toBeNull();
  });

  test('every state entry cites a source, including the negative ones', () => {
    for (const [code, s] of Object.entries(STATE_DISCLOSURE)) {
      expect(s.source_url).toMatch(new RegExp('^https?://'));
      expect(s.verified_at).toMatch(/^\d{4}-\d{2}-\d{2}/);
      expect(s.state).toBe(code);
      // A program that exists must be reachable; one that does not must
      // not carry a dangling link.
      if (s.acp_exists) expect(s.acp_url).toMatch(new RegExp('^https?://'));
    }
  });

  test('every citation is a government or program-operator page, not a secondary summary', () => {
    // "is it a URL" has no teeth, and that is exactly why three states
    // shipped citing a law-review blog for the most dangerous claim in
    // the file — that the reader's state has no program at all. That
    // blog's own list is wrong about Arkansas and South Carolina, both of
    // which this registry contradicts with the states' own pages, so it
    // was provably not what those entries were verified against.
    //
    // The allowlist is for program operators that are not themselves .gov
    // (NACAP is the national association of the state programs, and a
    // state AG's campaign site is the AG's own publication).
    const OPERATOR_HOSTS = ['nacap.org', 'attorneygenerallynnfitch.com'];
    const offenders = [];
    for (const [code, s] of Object.entries(STATE_DISCLOSURE)) {
      const host = new URL(s.source_url).hostname.toLowerCase();
      const ok = host.endsWith('.gov')
        || host.endsWith('.us')
        || OPERATOR_HOSTS.some((h) => host === h || host.endsWith(`.${h}`));
      if (!ok) offenders.push(`${code}: ${host}`);
    }
    expect(offenders).toEqual([]);
  });

  test('all 50 states and DC are covered, so no resident sees a blank', () => {
    expect(Object.keys(STATE_DISCLOSURE)).toHaveLength(51);
    expect(STATE_DISCLOSURE.DC).toBeTruthy();
  });
});

// ============================================================
// The anonymous route: GET /api/public/unlisted
//
// Two promises live here, and both were broken.
//
//   1. "We do not save this address, and we do not send it anywhere
//      else." The route geocoded through Mapbox, which put the typed
//      address into a third-party query string — on a page whose
//      readers are disproportionately hiding from a specific person.
//
//   2. "We could not place that" and "you are not in the United States"
//      are different answers. Every geocoder failure — an outage, a
//      missing API key, an address it simply could not parse — returned
//      the geographic denial, so a Mapbox blip told every US visitor at
//      once that the product had nothing for them, and withheld the
//      entire national removal list, which never needed the address.
// ============================================================

const publicRouter = require('../routes/public');

function buildPublicApp() {
  const app = express();
  app.use(express.json());
  app.use('/api/public', publicRouter);
  return app;
}

describe('the anonymous unlisted lookup', () => {
  test('a full address resolves to its state and sends nothing anywhere', async () => {
    const realFetch = global.fetch;
    const seen = [];
    global.fetch = jest.fn(async (url) => {
      seen.push(String(url));
      return { ok: false, status: 503, json: async () => ({}) };
    });
    try {
      const res = await request(buildPublicApp())
        .get('/api/public/unlisted')
        .query({ address: '1421 SE Oak St, Portland, OR 97214' });
      expect(res.status).toBe(200);
      expect(res.body.status).toBe('ready');
      expect(res.body.place.state).toBe('OR');
      // No city — resolving one would mean the third-party hop the page
      // promises does not happen.
      expect(res.body.place.city).toBeNull();
      expect(res.body.unlisted.state_program).not.toBeNull();
    } finally {
      global.fetch = realFetch;
    }
    expect(seen).toEqual([]);
  });

  test('an address it cannot place is NOT told it is outside the U.S.', async () => {
    const res = await request(buildPublicApp())
      .get('/api/public/unlisted')
      .query({ address: 'the blue house behind the school' });

    expect(res.status).toBe(200);
    expect(res.body.status).toBe('could_not_place');
    expect(res.body.status).not.toBe('unsupported_region');
    // The exact laundering that shipped: a geographic denial rendered to
    // someone who is standing in the United States.
    expect(JSON.stringify(res.body)).not.toMatch(/U\.S\.-only|outside the U\.S\./i);
  });

  test('an address it cannot place still gets the WHOLE removal list', async () => {
    // Every broker path is national. None of it needed the address, so
    // withholding it because a state could not be read is a pure loss to
    // the person who came here for exactly that list.
    const full = unlistedService.getExposureProfile('OR');
    const res = await request(buildPublicApp())
      .get('/api/public/unlisted')
      .query({ address: 'no state here' });

    expect(res.body.unlisted.broker_count).toBe(full.broker_count);
    expect(res.body.unlisted.groups).toHaveLength(full.groups.length);
    expect(res.body.unlisted.method_note).toBe(full.method_note);
    // And the state answer degrades to "not checked", never to "none".
    expect(res.body.unlisted.state_program).toBeNull();
  });

  test('a ZIP on its own is enough to reach the state program', async () => {
    const res = await request(buildPublicApp())
      .get('/api/public/unlisted')
      .query({ address: '97214' });
    expect(res.body.status).toBe('ready');
    expect(res.body.place.state).toBe('OR');
  });
});
