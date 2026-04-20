// ============================================================
// MOCK: Stripe SDK
// Returns controllable promises for all Stripe methods used.
// Each method is a jest.fn() so tests can inspect calls and
// override return values with mockResolvedValueOnce().
// ============================================================

const stripeMock = {
  paymentIntents: {
    create: jest.fn().mockResolvedValue({
      id: 'pi_mock_123',
      client_secret: 'pi_mock_123_secret',
      status: 'requires_confirmation',
      latest_charge: null,
    }),
    confirm: jest.fn().mockResolvedValue({
      id: 'pi_mock_123',
      status: 'requires_capture',
      latest_charge: 'ch_mock_123',
    }),
    capture: jest.fn().mockResolvedValue({
      id: 'pi_mock_123',
      status: 'succeeded',
      latest_charge: 'ch_mock_123',
    }),
    cancel: jest.fn().mockResolvedValue({
      id: 'pi_mock_123',
      status: 'canceled',
    }),
    retrieve: jest.fn().mockResolvedValue({
      id: 'pi_mock_123',
      status: 'requires_capture',
    }),
  },
  setupIntents: {
    create: jest.fn().mockResolvedValue({
      id: 'seti_mock_123',
      client_secret: 'seti_mock_123_secret',
      status: 'succeeded',
      payment_method: 'pm_mock_123',
    }),
    retrieve: jest.fn().mockResolvedValue({
      id: 'seti_mock_123',
      status: 'succeeded',
      payment_method: 'pm_mock_123',
    }),
  },
  transfers: {
    create: jest.fn().mockResolvedValue({
      id: 'tr_mock_123',
      amount: 8500,
      destination: 'acct_mock_payee',
    }),
    createReversal: jest.fn().mockResolvedValue({
      id: 'trr_mock_123',
      amount: 8500,
    }),
  },
  refunds: {
    create: jest.fn().mockResolvedValue({
      id: 're_mock_123',
      amount: 10000,
      status: 'succeeded',
    }),
  },
  customers: {
    create: jest.fn().mockResolvedValue({ id: 'cus_mock_123' }),
    retrieve: jest.fn().mockResolvedValue({ id: 'cus_mock_123' }),
  },
  disputes: {
    update: jest.fn().mockResolvedValue({ id: 'dp_mock_123', status: 'under_review' }),
  },
};

// Reset all mocks between tests
stripeMock._resetAll = () => {
  Object.values(stripeMock).forEach(namespace => {
    if (typeof namespace === 'object' && namespace !== null) {
      Object.values(namespace).forEach(fn => {
        if (typeof fn === 'function' && fn.mockReset) fn.mockReset();
      });
    }
  });
};

module.exports = stripeMock;
