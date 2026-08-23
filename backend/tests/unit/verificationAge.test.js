/**
 * §5.1 / LIF-04 — verification was a single bit with no timestamp, so the
 * system could not express "this verification is 29 months old" and every trust
 * decision treated a three-year-old verification and this morning's as
 * identical.
 *
 * These cover the measurement. Enforcement is deliberately behind a flag: the
 * mechanism ships before the policy, because expiring the existing verified
 * base is a product decision.
 */

const { resetTables } = require('../__mocks__/supabaseAdmin');
const verificationAge = require('../../utils/verificationAge');
const flags = require('../../utils/addressRolloutFlags');

const daysAgo = (n) => new Date(Date.now() - n * 86400000).toISOString();

beforeEach(() => {
  resetTables();
  flags.__setOverrides(null);
});

afterAll(() => flags.stopRolloutFlagRefresh());

describe('age is measurable', () => {
  test('reports whole days since verification', () => {
    expect(verificationAge.ageInDays(daysAgo(30))).toBe(30);
    expect(verificationAge.ageInDays(daysAgo(0))).toBe(0);
  });

  test('an unknown timestamp reports null rather than zero', () => {
    // Zero would read as "verified today", which is the opposite of the truth.
    expect(verificationAge.ageInDays(null)).toBeNull();
    expect(verificationAge.ageInDays('not-a-date')).toBeNull();
  });

  test('expiry is one validity window past verification', () => {
    const at = new Date('2026-01-01T00:00:00.000Z').toISOString();
    const expiry = new Date(verificationAge.expiryFor(at));
    const days = (expiry.getTime() - new Date(at).getTime()) / 86400000;
    expect(days).toBe(verificationAge.validityDays());
  });
});

describe('staleness', () => {
  test('a verification past the window is stale', () => {
    expect(verificationAge.isStale(daysAgo(400))).toBe(true);
  });

  test('a recent verification is not', () => {
    expect(verificationAge.isStale(daysAgo(10))).toBe(false);
  });

  test('an unknown timestamp is NOT stale', () => {
    // Rows predating the column must not have trust revoked on deploy.
    expect(verificationAge.isStale(null)).toBe(false);
  });
});

describe('enforcement is separate from measurement', () => {
  test('staleness costs nothing while the flag is off', () => {
    expect(verificationAge.isStale(daysAgo(400))).toBe(true);
    expect(verificationAge.staleAffectsTrust(daysAgo(400))).toBe(false);
  });

  test('and costs something once it is on', () => {
    flags.__setOverrides({ enforceVerificationExpiry: true });
    expect(verificationAge.staleAffectsTrust(daysAgo(400))).toBe(true);
    expect(verificationAge.staleAffectsTrust(daysAgo(10))).toBe(false);
  });
});

describe('describe()', () => {
  test('surfaces age, staleness and whether it is enforced', () => {
    const out = verificationAge.describe(daysAgo(400));
    expect(out.age_days).toBe(400);
    expect(out.stale).toBe(true);
    expect(out.enforced).toBe(false);
  });
});
