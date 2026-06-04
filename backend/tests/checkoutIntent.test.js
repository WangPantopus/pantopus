// ============================================================
// TEST: Checkout PaymentSheet params (Block 3B)
// POST /api/payments/intent must return the full set of params the
// mobile PaymentSheet needs (clientSecret + customer + ephemeralKey +
// publishableKey), and must attribute marketplace checkouts (listingId /
// offerId) onto the PaymentIntent metadata.
// ============================================================

const express = require('express');
const request = require('supertest');

jest.mock('../config/supabaseAdmin', () => ({
  from: jest.fn(),
}));

jest.mock('../stripe/stripeService', () => ({
  createPaymentIntent: jest.fn(),
  getOrCreateCustomer: jest.fn(),
  createEphemeralKey: jest.fn(),
}));
const stripeService = require('../stripe/stripeService');

jest.mock('../middleware/verifyToken', () => {
  const mw = (req, _res, next) => {
    req.user = { id: req.headers['x-test-user-id'] || PAYER_ID, role: 'user' };
    next();
  };
  mw.requireAdmin = (_req, _res, next) => next();
  mw.invalidateRoleCache = () => {};
  return mw;
});

const paysRoutes = require('../routes/pays');

const PAYER_ID = 'aaaaaaaa-aaaa-1aaa-8aaa-aaaaaaaaaaaa';
const SELLER_ID = 'bbbbbbbb-bbbb-1bbb-8bbb-bbbbbbbbbbbb';
const LISTING_ID = 'cccccccc-cccc-1ccc-8ccc-cccccccccccc';
const OFFER_ID = 'dddddddd-dddd-1ddd-8ddd-dddddddddddd';

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use('/api/payments', paysRoutes);
  return app;
}

beforeEach(() => {
  jest.clearAllMocks();
  process.env.STRIPE_PUBLISHABLE_KEY = 'pk_test_block3b';
  stripeService.createPaymentIntent.mockResolvedValue({
    clientSecret: 'pi_secret_123',
    paymentIntentId: 'pi_123',
    payment: { id: 'pay-1', payment_status: 'authorize_pending' },
  });
  stripeService.getOrCreateCustomer.mockResolvedValue('cus_123');
  stripeService.createEphemeralKey.mockResolvedValue({ secret: 'ek_secret_123' });
});

describe('POST /api/payments/intent — PaymentSheet params', () => {
  test('returns clientSecret + customer + ephemeralKey + publishableKey', async () => {
    const res = await request(buildApp())
      .post('/api/payments/intent')
      .send({ payeeId: SELLER_ID, amount: 5000 });

    expect(res.status).toBe(201);
    expect(res.body).toMatchObject({
      clientSecret: 'pi_secret_123',
      paymentIntentId: 'pi_123',
      customer: 'cus_123',
      ephemeralKey: 'ek_secret_123',
      publishableKey: 'pk_test_block3b',
    });
    expect(stripeService.getOrCreateCustomer).toHaveBeenCalledWith(PAYER_ID);
    expect(stripeService.createEphemeralKey).toHaveBeenCalledWith('cus_123');
  });

  test('attributes a marketplace checkout via listing_id / offer_id metadata', async () => {
    const res = await request(buildApp())
      .post('/api/payments/intent')
      .send({ payeeId: SELLER_ID, amount: 7500, listingId: LISTING_ID, offerId: OFFER_ID });

    expect(res.status).toBe(201);
    const args = stripeService.createPaymentIntent.mock.calls[0][0];
    expect(args.metadata).toMatchObject({ listing_id: LISTING_ID, offer_id: OFFER_ID });
    expect(args.payeeId).toBe(SELLER_ID);
    expect(args.amount).toBe(7500);
  });

  test('still succeeds (card-only) when ephemeral-key minting fails', async () => {
    stripeService.createEphemeralKey.mockRejectedValueOnce(new Error('stripe down'));

    const res = await request(buildApp())
      .post('/api/payments/intent')
      .send({ payeeId: SELLER_ID, amount: 5000 });

    expect(res.status).toBe(201);
    expect(res.body.clientSecret).toBe('pi_secret_123');
    expect(res.body.ephemeralKey).toBeNull();
  });
});
