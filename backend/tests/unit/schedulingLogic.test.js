// Wiring + pure-logic tests for the Calendarly backend (no live DB/Stripe).
// errorHandler.js -> ./utils/logger -> winston via a path the jest config doesn't remap; winston
// fails to construct under jest's module environment, so stub winston itself.
jest.mock('winston', () => {
  const logger = { info: () => {}, warn: () => {}, error: () => {}, debug: () => {}, http: () => {}, add: () => {} };
  const fmt = () => ({});
  return {
    createLogger: () => logger,
    addColors: () => {},
    format: new Proxy({}, { get: () => fmt }),
    transports: { Console: function Console() {}, File: function File() {} },
  };
});
// Mock the Stripe service so requiring bookingService/payments doesn't need Stripe env.
jest.mock('../../stripe/stripeService', () => ({
  createPaymentIntentForGig: jest.fn(),
  capturePayment: jest.fn(),
  createSmartRefund: jest.fn(),
  calculateFees: jest.fn(),
  getEffectiveFeeRate: jest.fn(),
}));

const { resetTables, seedTable } = require('../__mocks__/supabaseAdmin');

describe('module wiring (require without error)', () => {
  it('loads all scheduling services + routers', () => {
    expect(typeof require('../../services/scheduling/availabilityService').computeSlots).toBe('function');
    expect(typeof require('../../services/scheduling/bookingService').createBooking).toBe('function');
    expect(typeof require('../../services/scheduling/schedulingPaymentsService').computeRefundCents).toBe('function');
    expect(typeof require('../../services/scheduling/bookingNotifyService').notifyBookingEvent).toBe('function');
    expect(typeof require('../../services/scheduling/icsService').buildIcs).toBe('function');
    expect(typeof require('../../services/scheduling/schedulingShared').ownerColumns).toBe('function');
    expect(typeof require('../../routes/scheduling')).toBe('function'); // express router
    expect(typeof require('../../routes/schedulingPublic')).toBe('function');
    expect(typeof require('../../jobs/bookingReminders').runBookingReminders).toBe('function');
  });
});

describe('schedulingShared.ownerColumns', () => {
  const { ownerColumns } = require('../../services/scheduling/schedulingShared');
  it('maps user/business to owner_user_id and home to home_id (consistent with owner_id)', () => {
    expect(ownerColumns('user', 'u1')).toEqual({ owner_type: 'user', owner_id: 'u1', owner_user_id: 'u1', home_id: null });
    expect(ownerColumns('business', 'b1')).toEqual({ owner_type: 'business', owner_id: 'b1', owner_user_id: 'b1', home_id: null });
    expect(ownerColumns('home', 'h1')).toEqual({ owner_type: 'home', owner_id: 'h1', owner_user_id: null, home_id: 'h1' });
  });
});

describe('schedulingPaymentsService.computeRefundCents', () => {
  const { computeRefundCents } = require('../../services/scheduling/schedulingPaymentsService');
  const start = Date.parse('2026-07-10T15:00:00Z');
  const base = { amountTotal: 10000, startAtMs: start };

  it('full refund when cancelled within the free window', () => {
    const policy = { cancellation_window_min: 1440, refund_policy: 'none' }; // 24h free window
    // now is 25h before start -> within free window -> full
    expect(computeRefundCents({ ...base, policy, nowMs: start - 25 * 60 * 60 * 1000 })).toBe(10000);
  });
  it('refund_policy none -> 0 outside the free window', () => {
    const policy = { cancellation_window_min: 60, refund_policy: 'none' };
    expect(computeRefundCents({ ...base, policy, nowMs: start - 30 * 60 * 1000 })).toBe(0);
  });
  it('deposit_only with non-refundable deposit forfeits the deposit (outside free window)', () => {
    const policy = { cancellation_window_min: 60, refund_policy: 'deposit_only', deposit_cents: 2500, deposit_refundable: false };
    expect(computeRefundCents({ ...base, policy, nowMs: start - 30 * 60 * 1000 })).toBe(7500);
  });
  it('partial -> half (outside free window)', () => {
    const policy = { cancellation_window_min: 60, refund_policy: 'partial' };
    expect(computeRefundCents({ ...base, policy, nowMs: start - 30 * 60 * 1000 })).toBe(5000);
  });
  it('no-show forfeits up to the no_show_fee', () => {
    const policy = { no_show_fee_cents: 3000 };
    expect(computeRefundCents({ ...base, policy, nowMs: start, noShow: true })).toBe(7000);
  });
});

describe('icsService.buildIcs', () => {
  const { buildIcs } = require('../../services/scheduling/icsService');
  it('emits a valid REQUEST VEVENT with stable UID and escaped text', () => {
    const ics = buildIcs({
      uid: 'abc-123',
      start: '2026-07-06T09:00:00Z',
      end: '2026-07-06T09:30:00Z',
      summary: 'Chat, with me; really',
      method: 'REQUEST',
    });
    expect(ics).toContain('BEGIN:VCALENDAR');
    expect(ics).toContain('METHOD:REQUEST');
    expect(ics).toContain('UID:abc-123@pantopus.com');
    expect(ics).toContain('DTSTART:20260706T090000Z');
    expect(ics).toContain('DTEND:20260706T093000Z');
    expect(ics).toContain('SUMMARY:Chat\\, with me\\; really'); // escaped comma + semicolon
    expect(ics).toContain('STATUS:CONFIRMED');
    expect(ics.endsWith('\r\n')).toBe(true);
  });
  it('CANCEL method marks the event cancelled', () => {
    const ics = buildIcs({ uid: 'x', start: '2026-07-06T09:00:00Z', end: '2026-07-06T09:30:00Z', summary: 'x', method: 'CANCEL' });
    expect(ics).toContain('METHOD:CANCEL');
    expect(ics).toContain('STATUS:CANCELLED');
  });
});

describe('bookingService.pickRoundRobinHost (fair rotation)', () => {
  const bookingService = require('../../services/scheduling/bookingService');
  beforeEach(() => resetTables());

  it('picks the eligible host with the lowest assigned_count', async () => {
    seedTable('EventTypeAssignee', [
      { id: 'a1', event_type_id: 'et1', subject_id: 'm1', assigned_count: 5, priority: 0, last_assigned_at: null, is_active: true },
      { id: 'a2', event_type_id: 'et1', subject_id: 'm2', assigned_count: 1, priority: 0, last_assigned_at: null, is_active: true },
    ]);
    const host = await bookingService._internal.pickRoundRobinHost('et1', ['m1', 'm2']);
    expect(host).toBe('m2');
  });

  it('returns the single eligible host without a query', async () => {
    const host = await bookingService._internal.pickRoundRobinHost('et1', ['only']);
    expect(host).toBe('only');
  });
});

describe('bookingService.createResourceBooking guards', () => {
  const bookingService = require('../../services/scheduling/bookingService');
  beforeEach(() => resetTables());

  it('rejects a duration exceeding the resource max', async () => {
    const resource = { id: 'r1', home_id: 'h1', max_duration_min: 30, buffer_min: 0, requires_approval: false };
    await expect(
      bookingService.createResourceBooking({ resource, startIso: '2026-07-02T10:00:00Z', durationMin: 60, booker: { id: 'u1' } })
    ).rejects.toMatchObject({ code: 'DURATION_TOO_LONG', statusCode: 400 });
  });

  it('rejects an invalid start time', async () => {
    const resource = { id: 'r1', home_id: 'h1', buffer_min: 0 };
    await expect(
      bookingService.createResourceBooking({ resource, startIso: 'not-a-date', booker: { id: 'u1' } })
    ).rejects.toMatchObject({ code: 'BAD_START' });
  });
});

describe('bookingService.isOverlapViolation', () => {
  const bookingService = require('../../services/scheduling/bookingService');
  it('detects the exclusion-constraint SQLSTATE and constraint name', () => {
    expect(bookingService._internal.isOverlapViolation({ code: '23P01' })).toBe(true);
    expect(bookingService._internal.isOverlapViolation({ message: 'conflicting key value violates exclusion constraint "Booking_no_overlap"' })).toBe(true);
    expect(bookingService._internal.isOverlapViolation({ code: '23505' })).toBe(false);
    expect(bookingService._internal.isOverlapViolation(null)).toBe(false);
  });
});
