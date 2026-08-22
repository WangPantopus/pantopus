/**
 * Security regression tests for address verification.
 *
 * Each test here corresponds to a finding from the 2026-08-22 audit
 * (docs/home-address-verification-audit-2026-08-22.md). They exist to stop
 * the specific bypass from being reintroduced, so prefer asserting the
 * security property directly over asserting an implementation detail.
 */

const { resetTables, getTable, seedTable } = require('../__mocks__/supabaseAdmin');

const mockDispatchPostcard = jest.fn();
jest.mock('../../services/addressValidation/mailVendorService', () => ({
  dispatchPostcard: (...args) => mockDispatchPostcard(...args),
}));
jest.mock('../../services/occupancyAttachService', () => ({
  attachOccupancy: jest.fn().mockResolvedValue({ success: true, occupancy_id: 'occ-1' }),
}));

const mailVerificationService = require('../../services/addressValidation/mailVerificationService');

function seedAddress() {
  seedTable('HomeAddress', [{
    id: 'addr-1',
    address_line1_norm: '123 Main St',
    address_line2_norm: null,
    city_norm: 'Portland',
    state: 'OR',
    postal_code: '97201',
    validation_raw_response: { dpv_match_code: 'Y' },
  }]);
}

beforeEach(() => {
  resetTables();
  mockDispatchPostcard.mockReset();
  mockDispatchPostcard.mockResolvedValue({ success: true, vendorJobId: 'psc_1' });
  seedAddress();
});

describe('SCN-02 / PRV-03 — the verification code is never persisted', () => {
  test('startVerification stores no code on the job row', async () => {
    const res = await mailVerificationService.startVerification('user-1', 'addr-1');
    expect(res.success).toBe(true);

    for (const job of getTable('MailVerificationJob')) {
      expect(job.metadata).toBeDefined();
      expect(job.metadata.code).toBeUndefined();
      // no field of the metadata blob may hold a bare 6-digit code
      for (const value of Object.values(job.metadata)) {
        expect(String(value)).not.toMatch(/^\d{6}$/);
      }
    }
  });

  test('the code reaches the mail vendor in memory instead', async () => {
    await mailVerificationService.startVerification('user-1', 'addr-1');
    expect(mockDispatchPostcard).toHaveBeenCalledTimes(1);
    expect(mockDispatchPostcard.mock.calls[0][1]).toMatch(/^\d{6}$/);
  });

  test('resendCode also persists no code', async () => {
    const start = await mailVerificationService.startVerification('user-1', 'addr-1');
    // clear the cooldown so the resend is permitted
    const tokens = getTable('AddressVerificationToken');
    tokens[0].cooldown_until = new Date(Date.now() - 1000).toISOString();

    const res = await mailVerificationService.resendCode(start.attempt_id, 'user-1');
    expect(res.success).toBe(true);

    for (const job of getTable('MailVerificationJob')) {
      expect(job.metadata.code).toBeUndefined();
    }
    expect(mockDispatchPostcard.mock.calls[1][1]).toMatch(/^\d{6}$/);
    expect(mockDispatchPostcard.mock.calls[1][1])
      .not.toBe(mockDispatchPostcard.mock.calls[0][1]);
  });

  test('only the hash is written to AddressVerificationToken', async () => {
    await mailVerificationService.startVerification('user-1', 'addr-1');
    const code = mockDispatchPostcard.mock.calls[0][1];

    const tokens = getTable('AddressVerificationToken');
    expect(tokens).toHaveLength(1);
    expect(tokens[0].code_hash).toMatch(/^[0-9a-f]{64}$/);
    expect(tokens[0].code_hash).not.toContain(code);
    expect(tokens[0].code).toBeUndefined();
  });
});
