// ============================================================
// STRIPE WEBHOOKS HANDLER
// Process Stripe Connect and payment webhooks.
//
// Supports Separate Charges and Transfers with manual capture:
//   - SetupIntent events (card saving for future gigs)
//   - PaymentIntent events (authorization + capture)
//   - Charge/Transfer events (escrow release)
//   - Dispute events (freeze, resolve)
// ============================================================

const express = require('express');
const router = express.Router();
const { getStripeClient } = require('./getStripeClient');
const stripe = getStripeClient();
const supabaseAdmin = require('../config/supabaseAdmin');
const logger = require('../utils/logger');
const { PAYMENT_STATES, transitionPaymentStatus } = require('./paymentStateMachine');
const {
  createNotification,
  notifyPaymentCaptured,
  notifyTransferCompleted,
  notifyDisputeCreated,
  notifyDisputeResolved,
  notifySetupFailed,
} = require('../services/notificationService');
const { submitDisputeEvidence } = require('./disputeService');
const stripeService = require('./stripeService');

// IMPORTANT: This route needs express.raw() middleware, NOT express.json()
// Configure in app.js:
// app.use('/api/webhooks/stripe', express.raw({type: 'application/json'}), stripeWebhooks);

/**
 * POST /api/webhooks/stripe
 * Handle Stripe webhook events
 */
router.post('/', async (req, res) => {
  const sig = req.headers['stripe-signature'];
  const webhookSecret = process.env.STRIPE_WEBHOOK_SECRET;
  const rawBody = req.body;

  // ── Phase A: Signature verification ──
  let event;
  try {
    if (!webhookSecret) {
      throw new Error('STRIPE_WEBHOOK_SECRET is not configured');
    }

    if (!(Buffer.isBuffer(rawBody) || typeof rawBody === 'string')) {
      throw new Error(
        'Invalid webhook payload type. Expected raw body buffer; ensure express.raw() runs before JSON parsing.'
      );
    }

    event = stripe.webhooks.constructEvent(rawBody, sig, webhookSecret);
  } catch (err) {
    logger.error('Webhook signature verification failed', { error: err.message });
    return res.status(400).send(`Webhook Error: ${err.message}`);
  }

  logger.info('Stripe webhook received', {
    type: event.type,
    eventId: event.id,
  });

  // ── Phase B: Idempotency check ──
  let existingRow = null;
  const { error: insertError } = await supabaseAdmin
    .from('StripeWebhookEvent')
    .insert({
      stripe_event_id: event.id,
      event_type: event.type,
      event_data: event.data,
      api_version: event.api_version,
      processed: false,
    });

  if (insertError) {
    // Duplicate stripe_event_id — look up existing row
    const { data: existing } = await supabaseAdmin
      .from('StripeWebhookEvent')
      .select('*')
      .eq('stripe_event_id', event.id)
      .single();

    if (existing && existing.processed) {
      logger.info('Webhook event already processed, skipping', { eventId: event.id });
      return res.json({ received: true, eventId: event.id, duplicate: true });
    }

    existingRow = existing;
    logger.info('Webhook event redelivered, reprocessing', { eventId: event.id });
  }

  // ── Phase C: Event processing ──
  try {
    switch (event.type) {
      // ============ CONNECT ACCOUNT EVENTS ============

      case 'account.updated':
        await handleAccountUpdated(event.data.object);
        break;

      case 'account.application.authorized':
        await handleAccountAuthorized(event.data.object);
        break;

      case 'account.application.deauthorized':
        await handleAccountDeauthorized(event.data.object);
        break;

      // ============ SETUP INTENT EVENTS ============

      case 'setup_intent.succeeded':
        await handleSetupIntentSucceeded(event.data.object);
        break;

      case 'setup_intent.setup_failed':
        await handleSetupIntentFailed(event.data.object);
        break;

      // ============ PAYMENT INTENT EVENTS ============

      case 'payment_intent.succeeded':
        await handlePaymentIntentSucceeded(event.data.object);
        break;

      case 'payment_intent.payment_failed':
        await handlePaymentIntentFailed(event.data.object);
        break;

      case 'payment_intent.canceled':
        await handlePaymentIntentCanceled(event.data.object);
        break;

      case 'payment_intent.requires_action':
        await handlePaymentIntentRequiresAction(event.data.object);
        break;

      case 'payment_intent.amount_capturable_updated':
        await handleAmountCapturableUpdated(event.data.object);
        break;

      // ============ CHARGE EVENTS ============

      case 'charge.succeeded':
        await handleChargeSucceeded(event.data.object);
        break;

      case 'charge.failed':
        await handleChargeFailed(event.data.object);
        break;

      case 'charge.refunded':
        await handleChargeRefunded(event.data.object);
        break;

      // ============ DISPUTE EVENTS ============

      case 'charge.dispute.created':
        await handleDisputeCreated(event.data.object);
        break;

      case 'charge.dispute.updated':
        await handleDisputeUpdated(event.data.object);
        break;

      case 'charge.dispute.closed':
        await handleDisputeClosed(event.data.object);
        break;

      // ============ TRANSFER EVENTS ============

      case 'transfer.created':
        await handleTransferCreated(event.data.object);
        break;

      case 'transfer.paid':
        await handleTransferPaid(event.data.object);
        break;

      case 'transfer.failed':
        await handleTransferFailed(event.data.object);
        break;

      case 'transfer.reversed':
        await handleTransferReversed(event.data.object);
        break;

      // ============ PAYOUT EVENTS ============

      case 'payout.created':
        await handlePayoutCreated(event.data.object, event.account);
        break;

      case 'payout.paid':
        await handlePayoutPaid(event.data.object, event.account);
        break;

      case 'payout.failed':
        await handlePayoutFailed(event.data.object, event.account);
        break;

      // ============ REFUND EVENTS ============

      case 'charge.refund.updated':
        await handleRefundUpdated(event.data.object);
        break;

      // ============ PAYMENT METHOD EVENTS ============

      case 'payment_method.attached':
        await handlePaymentMethodAttached(event.data.object);
        break;

      case 'payment_method.detached':
        await handlePaymentMethodDetached(event.data.object);
        break;

      // ============ CUSTOMER EVENTS ============

      case 'customer.updated':
        await handleCustomerUpdated(event.data.object);
        break;

      // ============ CAPABILITY EVENTS ============

      case 'capability.updated':
        await handleCapabilityUpdated(event.data.object);
        break;

      default:
        logger.info('Unhandled webhook event type', { type: event.type });
    }

    // Mark event as processed
    await supabaseAdmin
      .from('StripeWebhookEvent')
      .update({
        processed: true,
        processed_at: new Date().toISOString(),
      })
      .eq('stripe_event_id', event.id);

    return res.json({ received: true, eventId: event.id });
  } catch (err) {
    logger.error('Webhook processing error', {
      error: err.message,
      eventType: event.type,
      eventId: event.id,
    });

    // Record error and increment retry_count; return 500 so Stripe retries
    const currentRetry = existingRow ? (existingRow.retry_count || 0) : 0;
    await supabaseAdmin
      .from('StripeWebhookEvent')
      .update({
        processing_error: err.message,
        retry_count: currentRetry + 1,
      })
      .eq('stripe_event_id', event.id);

    return res.status(500).json({ error: 'Webhook handler failed' });
  }
});

// ============================================================
// HELPER: Look up Payment by various Stripe IDs
// ============================================================

async function findPaymentByField(field, value) {
  if (!value) return null;
  const { data } = await supabaseAdmin
    .from('Payment')
    .select('*')
    .eq(field, value)
    .single();
  return data;
}

async function findPaymentByPI(paymentIntentId) {
  return findPaymentByField('stripe_payment_intent_id', paymentIntentId);
}

async function findPaymentByCharge(chargeId) {
  return findPaymentByField('stripe_charge_id', chargeId);
}

async function findPaymentBySetupIntent(setupIntentId) {
  return findPaymentByField('stripe_setup_intent_id', setupIntentId);
}

async function findPaymentByTransfer(transferId) {
  return findPaymentByField('stripe_transfer_id', transferId);
}

/**
 * Get gig info for notification text.
 */
async function getGigInfo(gigId) {
  if (!gigId) return null;
  const { data } = await supabaseAdmin
    .from('Gig')
    .select('id, title, user_id, accepted_by')
    .eq('id', gigId)
    .single();
  return data;
}

// ============================================================
// CONNECT ACCOUNT HANDLERS
// ============================================================

async function handleAccountUpdated(account) {
  logger.info('Account updated', { accountId: account.id });

  await supabaseAdmin
    .from('StripeAccount')
    .update({
      charges_enabled: account.charges_enabled,
      payouts_enabled: account.payouts_enabled,
      details_submitted: account.details_submitted,
      card_payments_enabled: account.capabilities?.card_payments === 'active',
      transfers_enabled: account.capabilities?.transfers === 'active',
      requirements: account.requirements,
      last_synced_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    })
    .eq('stripe_account_id', account.id);
}

async function handleAccountAuthorized(application) {
  logger.info('Account authorized', { accountId: application.account });
}

async function handleAccountDeauthorized(application) {
  logger.info('Account deauthorized', { accountId: application.account });

  await supabaseAdmin
    .from('StripeAccount')
    .update({
      charges_enabled: false,
      payouts_enabled: false,
      onboarding_completed: false,
      updated_at: new Date().toISOString(),
    })
    .eq('stripe_account_id', application.account);
}

// ============================================================
// SETUP INTENT HANDLERS (Phase 6a)
// For saving cards when gig start is > 5 days away.
// ============================================================

async function handleSetupIntentSucceeded(setupIntent) {
  logger.info('SetupIntent succeeded', {
    setupIntentId: setupIntent.id,
    paymentMethod: setupIntent.payment_method,
  });

  const payment = await findPaymentBySetupIntent(setupIntent.id);

  if (!payment) {
    logger.warn('SetupIntent succeeded but no matching Payment found', {
      setupIntentId: setupIntent.id,
    });
    return;
  }

  // Only process if still in setup_pending state
  if (payment.payment_status !== PAYMENT_STATES.SETUP_PENDING) {
    logger.info('SetupIntent succeeded but payment already advanced', {
      paymentId: payment.id,
      currentStatus: payment.payment_status,
    });
    return;
  }

  try {
    // Transition to ready_to_authorize and store the saved payment method
    await transitionPaymentStatus(payment.id, PAYMENT_STATES.READY_TO_AUTHORIZE, {
      stripe_payment_method_id: setupIntent.payment_method,
    });

    logger.info('SetupIntent: payment method saved, ready for future authorization', {
      paymentId: payment.id,
      paymentMethod: setupIntent.payment_method,
    });
  } catch (err) {
    logger.error('Error processing SetupIntent succeeded', {
      paymentId: payment.id,
      error: err.message,
    });
  }
}

async function handleSetupIntentFailed(setupIntent) {
  logger.warn('SetupIntent failed', {
    setupIntentId: setupIntent.id,
    error: setupIntent.last_setup_error?.message,
  });

  const payment = await findPaymentBySetupIntent(setupIntent.id);

  if (!payment) {
    logger.warn('SetupIntent failed but no matching Payment found', {
      setupIntentId: setupIntent.id,
    });
    return;
  }

  // Update payment record with failure info
  await supabaseAdmin
    .from('Payment')
    .update({
      failure_code: setupIntent.last_setup_error?.code,
      failure_message: setupIntent.last_setup_error?.message,
      updated_at: new Date().toISOString(),
    })
    .eq('id', payment.id);

  // Notify the payer
  const gig = await getGigInfo(payment.gig_id);
  notifySetupFailed({
    userId: payment.payer_id,
    gigId: payment.gig_id,
    gigTitle: gig?.title,
  });
}

// ============================================================
// PAYMENT INTENT HANDLERS (Phase 6c, 6e)
// ============================================================

/**
 * payment_intent.succeeded
 *
 * With manual capture (capture_method: 'manual'), this event fires
 * when capture() is called — meaning funds have actually been taken.
 * Transition to captured_hold and set cooling_off_ends_at.
 *
 * For auto-capture PIs (tips, cancellation fees), this means the
 * charge went through — keep legacy behavior.
 */
async function handlePaymentIntentSucceeded(paymentIntent) {
  logger.info('Payment intent succeeded', {
    paymentIntentId: paymentIntent.id,
    amount: paymentIntent.amount,
    captureMethod: paymentIntent.capture_method,
  });

  const payment = await findPaymentByPI(paymentIntent.id);
  if (!payment) {
    // Could be a PI not tracked in our system
    logger.info('PaymentIntent succeeded but no matching Payment found', {
      paymentIntentId: paymentIntent.id,
    });
    return;
  }

  const chargeId = paymentIntent.latest_charge;
  const nowIso = new Date().toISOString();

  // Extract card details from the charge
  const charge = paymentIntent.charges?.data?.[0];
  const cardUpdates = {};
  if (charge) {
    cardUpdates.stripe_charge_id = charge.id;
    cardUpdates.payment_method_type = charge.payment_method_details?.type;
    cardUpdates.payment_method_last4 =
      charge.payment_method_details?.card?.last4 ||
      charge.payment_method_details?.us_bank_account?.last4;
    cardUpdates.payment_method_brand = charge.payment_method_details?.card?.brand;
  }

  // Support Train gift fund contribution: update contribution status on async confirmation
  if (paymentIntent.metadata?.type === 'support_train_gift_fund' && paymentIntent.metadata?.contribution_id) {
    try {
      await supabaseAdmin
        .from('SupportTrainFundContribution')
        .update({ payment_status: 'succeeded' })
        .eq('id', paymentIntent.metadata.contribution_id)
        .eq('payment_status', 'pending');
      logger.info('Updated SupportTrainFundContribution on PI succeeded', {
        contributionId: paymentIntent.metadata.contribution_id,
      });
    } catch (contribErr) {
      logger.warn('Failed to update SupportTrainFundContribution', {
        contributionId: paymentIntent.metadata.contribution_id,
        error: contribErr.message,
      });
    }
  }

  if (paymentIntent.capture_method === 'manual') {
    // ─── Manual capture PI: this means capture() was called ───
    // The capturePayment() in stripeService already transitions to captured_hold,
    // but this webhook serves as a safety net / confirmation.
    if (payment.payment_status === PAYMENT_STATES.AUTHORIZED) {
      // capturePayment hasn't run its transition yet — do it here
      const COOLING_OFF_MS = 48 * 60 * 60 * 1000;
      const coolingOffEnds = new Date(Date.now() + COOLING_OFF_MS);

      try {
        await transitionPaymentStatus(payment.id, PAYMENT_STATES.CAPTURED_HOLD, {
          captured_at: nowIso,
          cooling_off_ends_at: coolingOffEnds.toISOString(),
          payment_succeeded_at: nowIso,
          ...cardUpdates,
        });
      } catch (transErr) {
        // Likely already transitioned by capturePayment() — that's fine
        logger.info('PI succeeded: transition to captured_hold skipped (likely already done)', {
          paymentId: payment.id,
          error: transErr.message,
        });
      }

      // Notify requester: payment captured
      const gig = await getGigInfo(payment.gig_id);
      notifyPaymentCaptured({
        userId: payment.payer_id,
        gigId: payment.gig_id,
        gigTitle: gig?.title,
        amount: payment.amount_total,
      });
    } else if (payment.payment_status === PAYMENT_STATES.CAPTURED_HOLD) {
      // Already captured — just update card details if missing
      await supabaseAdmin
        .from('Payment')
        .update({ ...cardUpdates, updated_at: nowIso })
        .eq('id', payment.id);
    } else {
      logger.info('PI succeeded (manual) but payment in unexpected state', {
        paymentId: payment.id,
        currentStatus: payment.payment_status,
      });
    }
  } else {
    // ─── Auto-capture PI (tips, cancellation fees, legacy) ───
    // Keep these aligned with transfer pipeline:
    // captured_hold + cooling_off_ends_at so processPendingTransfers can pick them up.
    if (payment.payment_type === 'tip') {
      await stripeService.syncTipPaymentStatus(payment.id, { paymentIntent });
      return;
    }

    const COOLING_OFF_MS = 48 * 60 * 60 * 1000;
    const capturedAt = charge?.created
      ? new Date(charge.created * 1000)
      : new Date();
    const coolingOffEnds = new Date(capturedAt.getTime() + COOLING_OFF_MS);

    try {
      await transitionPaymentStatus(payment.id, PAYMENT_STATES.CAPTURED_HOLD, {
        captured_at: capturedAt.toISOString(),
        cooling_off_ends_at: coolingOffEnds.toISOString(),
        payment_succeeded_at: capturedAt.toISOString(),
        ...cardUpdates,
      });
    } catch (transErr) {
      // createTipPayment may have already advanced it — fall back to direct update
      logger.warn('Auto-capture PI: transition to captured_hold failed, direct update', {
        paymentId: payment.id, currentStatus: payment.payment_status, error: transErr.message,
      });
      await supabaseAdmin
        .from('Payment')
        .update({
          payment_status: PAYMENT_STATES.CAPTURED_HOLD,
          captured_at: capturedAt.toISOString(),
          cooling_off_ends_at: coolingOffEnds.toISOString(),
          payment_succeeded_at: capturedAt.toISOString(),
          ...cardUpdates,
          updated_at: nowIso,
        })
        .eq('id', payment.id);
    }

  }

}

async function handlePaymentIntentFailed(paymentIntent) {
  logger.warn('Payment intent failed', {
    paymentIntentId: paymentIntent.id,
    error: paymentIntent.last_payment_error?.message,
  });

  const payment = await findPaymentByPI(paymentIntent.id);
  if (!payment) return;

  // Check if this is an off-session auth failure
  const isAuthFailure = [
    PAYMENT_STATES.AUTHORIZE_PENDING,
    PAYMENT_STATES.READY_TO_AUTHORIZE,
  ].includes(payment.payment_status);

  if (isAuthFailure) {
    try {
      await transitionPaymentStatus(payment.id, PAYMENT_STATES.AUTHORIZATION_FAILED, {
        failure_code: paymentIntent.last_payment_error?.code,
        failure_message: paymentIntent.last_payment_error?.message,
        off_session_auth_required: true,
      });
    } catch (transErr) {
      logger.error('PI failed: transition error', { paymentId: payment.id, error: transErr.message });
    }

    // Notify requester to retry
    const gig = await getGigInfo(payment.gig_id);
    createNotification({
      userId: payment.payer_id,
      type: 'payment_auth_failed',
      title: 'Payment authorization failed',
      body: `Your payment for "${gig?.title || 'a gig'}" needs your attention. Please update your payment method.`,
      icon: '⚠️',
      link: `/gigs/${payment.gig_id}`,
      metadata: { gig_id: payment.gig_id, payment_id: payment.id },
    });
  } else {
    // Generic payment failure
    const failureFields = {
      failure_code: paymentIntent.last_payment_error?.code,
      failure_message: paymentIntent.last_payment_error?.message,
    };

    const POST_CAPTURE_STATES = [
      PAYMENT_STATES.CAPTURED_HOLD,
      PAYMENT_STATES.TRANSFER_SCHEDULED,
      PAYMENT_STATES.TRANSFER_PENDING,
      PAYMENT_STATES.TRANSFERRED,
      PAYMENT_STATES.REFUND_PENDING,
      PAYMENT_STATES.REFUNDED_PARTIAL,
      PAYMENT_STATES.REFUNDED_FULL,
      PAYMENT_STATES.DISPUTED,
    ];

    if (POST_CAPTURE_STATES.includes(payment.payment_status)) {
      // Post-capture: don't change status, just record failure details
      logger.warn('PI failed in post-capture state, logging only', {
        paymentId: payment.id, currentStatus: payment.payment_status,
      });
      await supabaseAdmin
        .from('Payment')
        .update({ ...failureFields, updated_at: new Date().toISOString() })
        .eq('id', payment.id);
    } else {
      // Pre-capture → cancel
      try {
        await transitionPaymentStatus(payment.id, PAYMENT_STATES.CANCELED, failureFields);
      } catch (transErr) {
        logger.error('PI failed: transition to canceled error', {
          paymentId: payment.id, currentStatus: payment.payment_status, error: transErr.message,
        });
        await supabaseAdmin
          .from('Payment')
          .update({
            payment_status: PAYMENT_STATES.CANCELED,
            ...failureFields,
            updated_at: new Date().toISOString(),
          })
          .eq('id', payment.id);
      }
    }
  }
}

async function handlePaymentIntentCanceled(paymentIntent) {
  logger.info('Payment intent canceled', { paymentIntentId: paymentIntent.id });

  const payment = await findPaymentByPI(paymentIntent.id);
  if (!payment) return;

  // If still in a pre-canceled state, transition cleanly
  if (payment.payment_status !== PAYMENT_STATES.CANCELED) {
    try {
      await transitionPaymentStatus(payment.id, PAYMENT_STATES.CANCELED);
    } catch (transErr) {
      // Fall back to direct update
      await supabaseAdmin
        .from('Payment')
        .update({
          payment_status: PAYMENT_STATES.CANCELED,
          updated_at: new Date().toISOString(),
        })
        .eq('id', payment.id);
    }
  }
}

async function handlePaymentIntentRequiresAction(paymentIntent) {
  logger.info('Payment intent requires action', {
    paymentIntentId: paymentIntent.id,
  });

  const payment = await findPaymentByPI(paymentIntent.id);
  if (!payment) return;

  // This fires when SCA/3DS is required.
  // Distinguish off-session (autoAuth job) vs on-session (user in browser/app).
  const isOffSession = paymentIntent.metadata?.off_session === 'true';

  if (isOffSession && payment.payment_status === PAYMENT_STATES.AUTHORIZE_PENDING) {
    // Off-session: mark as authorization_failed so the user can retry on-session
    try {
      await transitionPaymentStatus(payment.id, PAYMENT_STATES.AUTHORIZATION_FAILED, {
        off_session_auth_required: true,
        failure_message: 'Strong Customer Authentication (SCA) required. Please complete payment on-session.',
      });
    } catch (transErr) {
      logger.error('PI requires_action: transition error', { paymentId: payment.id, error: transErr.message });
    }

    const gig = await getGigInfo(payment.gig_id);
    createNotification({
      userId: payment.payer_id,
      type: 'payment_auth_failed',
      title: 'Payment needs your confirmation',
      body: `Your bank requires additional verification for "${gig?.title || 'a gig'}". Please open the app to complete payment.`,
      icon: '🔐',
      link: `/gigs/${payment.gig_id}`,
      metadata: { gig_id: payment.gig_id, payment_id: payment.id },
    });
  } else {
    // On-session: leave payment in authorize_pending — frontend handles the 3DS challenge
    logger.info('PI requires_action: on-session flow, staying in authorize_pending', {
      paymentId: payment.id,
      currentStatus: payment.payment_status,
    });
  }
}

/**
 * payment_intent.amount_capturable_updated (Phase 6c)
 *
 * Fires when a manual-capture PaymentIntent is authorized (hold placed).
 * This is the confirmation that the bank has approved the hold.
 */
async function handleAmountCapturableUpdated(paymentIntent) {
  logger.info('Amount capturable updated (auth hold placed)', {
    paymentIntentId: paymentIntent.id,
    amountCapturable: paymentIntent.amount_capturable,
    captureMethod: paymentIntent.capture_method,
  });

  if (paymentIntent.capture_method !== 'manual') return;

  const payment = await findPaymentByPI(paymentIntent.id);
  if (!payment) return;

  // Process if in authorize_pending or authorization_failed (retry after off-session SCA failure)
  const validSourceStates = [PAYMENT_STATES.AUTHORIZE_PENDING, PAYMENT_STATES.AUTHORIZATION_FAILED];
  if (!validSourceStates.includes(payment.payment_status)) {
    logger.info('amount_capturable_updated: payment not in an actionable state', {
      paymentId: payment.id,
      currentStatus: payment.payment_status,
    });
    return;
  }

  const AUTH_HOLD_MS = 7 * 24 * 60 * 60 * 1000;
  const authExpiresAt = new Date(Date.now() + AUTH_HOLD_MS);

  try {
    await transitionPaymentStatus(payment.id, PAYMENT_STATES.AUTHORIZED, {
      authorization_expires_at: authExpiresAt.toISOString(),
      stripe_charge_id: paymentIntent.latest_charge,
    });

    logger.info('Payment authorized via webhook', {
      paymentId: payment.id,
      authExpiresAt: authExpiresAt.toISOString(),
    });
  } catch (transErr) {
    // Likely already transitioned by createPaymentIntentForGig
    logger.info('amount_capturable_updated: transition skipped (likely already done)', {
      paymentId: payment.id,
      error: transErr.message,
    });
  }
}

// ============================================================
// CHARGE HANDLERS
// ============================================================

async function handleChargeSucceeded(charge) {
  logger.info('Charge succeeded', { chargeId: charge.id });

  // Look up by payment_intent (primary) or charge_id
  const payment = await findPaymentByPI(charge.payment_intent);
  if (!payment) return;

  await supabaseAdmin
    .from('Payment')
    .update({
      stripe_charge_id: charge.id,
      payment_method_type: charge.payment_method_details?.type,
      payment_method_last4:
        charge.payment_method_details?.card?.last4 ||
        charge.payment_method_details?.us_bank_account?.last4,
      payment_method_brand: charge.payment_method_details?.card?.brand,
      updated_at: new Date().toISOString(),
    })
    .eq('id', payment.id);
}

async function handleChargeFailed(charge) {
  logger.warn('Charge failed', {
    chargeId: charge.id,
    error: charge.failure_message,
  });

  const payment = await findPaymentByPI(charge.payment_intent);
  if (!payment) {
    logger.warn('handleChargeFailed: no payment found', { paymentIntent: charge.payment_intent });
    return;
  }

  const nowIso = new Date().toISOString();
  const failureFields = {
    failure_code: charge.failure_code,
    failure_message: charge.failure_message,
  };

  const POST_CAPTURE_STATES = [
    PAYMENT_STATES.CAPTURED_HOLD,
    PAYMENT_STATES.TRANSFER_SCHEDULED,
    PAYMENT_STATES.TRANSFER_PENDING,
    PAYMENT_STATES.TRANSFERRED,
    PAYMENT_STATES.REFUND_PENDING,
    PAYMENT_STATES.REFUNDED_PARTIAL,
    PAYMENT_STATES.REFUNDED_FULL,
    PAYMENT_STATES.DISPUTED,
  ];

  if ([PAYMENT_STATES.AUTHORIZE_PENDING, PAYMENT_STATES.READY_TO_AUTHORIZE].includes(payment.payment_status)) {
    // Auth-phase failure → authorization_failed
    try {
      await transitionPaymentStatus(payment.id, PAYMENT_STATES.AUTHORIZATION_FAILED, failureFields);
    } catch (transErr) {
      logger.error('handleChargeFailed: transition to authorization_failed error', {
        paymentId: payment.id, error: transErr.message,
      });
      await supabaseAdmin.from('Payment').update({
        payment_status: PAYMENT_STATES.AUTHORIZATION_FAILED, ...failureFields, updated_at: nowIso,
      }).eq('id', payment.id);
    }
  } else if (POST_CAPTURE_STATES.includes(payment.payment_status)) {
    // Post-capture: don't change status, just record failure details
    logger.warn('handleChargeFailed: charge failure in post-capture state, logging only', {
      paymentId: payment.id, currentStatus: payment.payment_status,
    });
    await supabaseAdmin.from('Payment').update({
      ...failureFields, updated_at: nowIso,
    }).eq('id', payment.id);
  } else {
    // Other pre-capture states (setup_pending, authorized, etc.) → canceled
    try {
      await transitionPaymentStatus(payment.id, PAYMENT_STATES.CANCELED, failureFields);
    } catch (transErr) {
      logger.error('handleChargeFailed: transition to canceled error', {
        paymentId: payment.id, error: transErr.message,
      });
      await supabaseAdmin.from('Payment').update({
        payment_status: PAYMENT_STATES.CANCELED, ...failureFields, updated_at: nowIso,
      }).eq('id', payment.id);
    }
  }
}

async function handleChargeRefunded(charge) {
  logger.info('Charge refunded', {
    chargeId: charge.id,
    amountRefunded: charge.amount_refunded,
  });

  const isFullRefund = charge.amount_refunded === charge.amount;

  const payment = await findPaymentByCharge(charge.id);
  if (!payment) {
    // Fallback: look up by PI
    await supabaseAdmin
      .from('Payment')
      .update({
        payment_status: isFullRefund ? PAYMENT_STATES.REFUNDED_FULL : PAYMENT_STATES.REFUNDED_PARTIAL,
        refunded_amount: charge.amount_refunded,
        updated_at: new Date().toISOString(),
      })
      .eq('stripe_payment_intent_id', charge.payment_intent);
    return;
  }

  // Use state machine transition if possible
  const targetState = isFullRefund ? PAYMENT_STATES.REFUNDED_FULL : PAYMENT_STATES.REFUNDED_PARTIAL;

  if (payment.payment_status === PAYMENT_STATES.REFUND_PENDING) {
    try {
      await transitionPaymentStatus(payment.id, targetState, {
        refunded_amount: charge.amount_refunded,
      });
    } catch (transErr) {
      // Direct update fallback
      await supabaseAdmin
        .from('Payment')
        .update({
          payment_status: targetState,
          refunded_amount: charge.amount_refunded,
          updated_at: new Date().toISOString(),
        })
        .eq('id', payment.id);
    }
  } else {
    // Direct update for non-state-machine flows
    await supabaseAdmin
      .from('Payment')
      .update({
        payment_status: targetState,
        refunded_amount: charge.amount_refunded,
        updated_at: new Date().toISOString(),
      })
      .eq('id', payment.id);
  }
}

// ============================================================
// DISPUTE HANDLERS (Phase 6b)
// ============================================================

/**
 * charge.dispute.created
 * CRITICAL: Freeze the payment. Stop pending transfers. Notify parties.
 */
async function handleDisputeCreated(dispute) {
  const paymentIntentId = dispute.payment_intent;
  const chargeId = dispute.charge;

  logger.warn('DISPUTE CREATED', {
    disputeId: dispute.id,
    paymentIntentId,
    chargeId,
    reason: dispute.reason,
    amount: dispute.amount,
  });

  // Find the payment — try charge first (more reliable for separate charges)
  let payment = await findPaymentByCharge(chargeId);
  if (!payment) payment = await findPaymentByPI(paymentIntentId);

  if (!payment) {
    logger.error('Dispute received but no matching Payment found', {
      disputeId: dispute.id,
      paymentIntentId,
      chargeId,
    });
    return;
  }

  // Store dispute info
  const disputeUpdates = {
    dispute_id: dispute.id,
    dispute_status: dispute.status, // 'warning_needs_response', 'needs_response', etc.
    updated_at: new Date().toISOString(),
  };

  // Transition to disputed state if possible
  const freezableStates = [
    PAYMENT_STATES.CAPTURED_HOLD,
    PAYMENT_STATES.TRANSFER_SCHEDULED,
    PAYMENT_STATES.TRANSFER_PENDING,
    PAYMENT_STATES.TRANSFERRED,
  ];

  if (freezableStates.includes(payment.payment_status)) {
    try {
      await transitionPaymentStatus(payment.id, PAYMENT_STATES.DISPUTED, disputeUpdates);
    } catch (transErr) {
      logger.error('Dispute: transition failed, direct update', {
        paymentId: payment.id,
        error: transErr.message,
      });
      await supabaseAdmin
        .from('Payment')
        .update({ ...disputeUpdates, payment_status: PAYMENT_STATES.DISPUTED })
        .eq('id', payment.id);
    }
  } else {
    // Already in a non-freezable state — just store dispute info
    await supabaseAdmin
      .from('Payment')
      .update(disputeUpdates)
      .eq('id', payment.id);
  }

  // Check if we already transferred funds to the provider
  const alreadyTransferred = payment.payment_status === PAYMENT_STATES.TRANSFERRED;

  if (alreadyTransferred) {
    logger.error('CRITICAL: Dispute on already-transferred payment. Provider may owe platform.', {
      paymentId: payment.id,
      transferId: payment.stripe_transfer_id,
      amount: dispute.amount,
    });
  }

  // Notify both parties
  const gig = await getGigInfo(payment.gig_id);
  const gigTitle = gig?.title || 'a gig';

  // Notify requester (payer)
  notifyDisputeCreated({
    userId: payment.payer_id,
    gigId: payment.gig_id,
    gigTitle,
    role: 'requester',
  });

  // Notify provider (payee)
  notifyDisputeCreated({
    userId: payment.payee_id,
    gigId: payment.gig_id,
    gigTitle,
    role: 'provider',
  });

  // Auto-submit evidence if we have completion proof
  // Run asynchronously — don't block the webhook response
  submitDisputeEvidence(payment.id, false).then(result => {
    logger.info('Auto-drafted dispute evidence', {
      paymentId: payment.id,
      disputeId: dispute.id,
      ...result,
    });
  }).catch(async (err) => {
    logger.error('Failed to auto-draft dispute evidence', {
      paymentId: payment.id,
      disputeId: dispute.id,
      error: err.message,
    });

    // Flag the payment so an admin dashboard can query for failed evidence drafts
    await supabaseAdmin
      .from('Payment')
      .update({
        metadata: {
          ...(payment.metadata || {}),
          evidence_auto_draft_failed: true,
          evidence_failure_reason: err.message,
        },
        updated_at: new Date().toISOString(),
      })
      .eq('id', payment.id);

    // Notify the payer so a human is aware
    createNotification({
      userId: payment.payer_id,
      type: 'dispute_evidence_failed',
      title: 'Action may be needed on your dispute',
      body: `A dispute was filed on your payment for "${gigTitle}". Our team has been alerted, but you may want to check your email for any communication from your bank.`,
      icon: '⚠️',
      link: `/gigs/${payment.gig_id}`,
      metadata: {
        payment_id: payment.id,
        dispute_id: dispute.id,
        gig_id: payment.gig_id,
      },
    });
  });
}

/**
 * charge.dispute.updated
 * Track dispute status changes.
 */
async function handleDisputeUpdated(dispute) {
  logger.info('Dispute updated', {
    disputeId: dispute.id,
    status: dispute.status,
  });

  // Find payment by dispute ID
  const { data: payment } = await supabaseAdmin
    .from('Payment')
    .select('id, gig_id')
    .eq('dispute_id', dispute.id)
    .single();

  if (!payment) {
    logger.warn('Dispute updated but no matching Payment found', { disputeId: dispute.id });
    return;
  }

  await supabaseAdmin
    .from('Payment')
    .update({
      dispute_status: dispute.status,
      updated_at: new Date().toISOString(),
    })
    .eq('id', payment.id);
}

/**
 * charge.dispute.closed
 * Resolve dispute: if won → restore previous state; if lost → finalize.
 */
async function handleDisputeClosed(dispute) {
  const status = dispute.status; // 'won', 'lost', 'warning_closed'

  logger.info('Dispute closed', {
    disputeId: dispute.id,
    status,
  });

  const { data: payment } = await supabaseAdmin
    .from('Payment')
    .select('*')
    .eq('dispute_id', dispute.id)
    .single();

  if (!payment) {
    logger.warn('Dispute closed but no matching Payment found', { disputeId: dispute.id });
    return;
  }

  const gig = await getGigInfo(payment.gig_id);
  const gigTitle = gig?.title || 'a gig';
  const nowIso = new Date().toISOString();

  if (status === 'won') {
    logger.info('Dispute WON — restoring payment state', {
      paymentId: payment.id,
      disputeId: dispute.id,
    });

    // Determine what state to restore to
    // If transfer was already done, go back to transferred
    // If not, go back to captured_hold so the transfer job picks it up
    let restoreState = PAYMENT_STATES.CAPTURED_HOLD;
    if (payment.stripe_transfer_id) {
      restoreState = PAYMENT_STATES.TRANSFERRED;
    }

    try {
      await transitionPaymentStatus(payment.id, restoreState, {
        dispute_status: 'won',
        dispute_evidence_submitted_at: nowIso,
      });
    } catch (transErr) {
      // Direct update fallback
      await supabaseAdmin
        .from('Payment')
        .update({
          payment_status: restoreState,
          dispute_status: 'won',
          updated_at: nowIso,
        })
        .eq('id', payment.id);
    }

    // Notify both parties — dispute won
    notifyDisputeResolved({ userId: payment.payer_id, gigId: payment.gig_id, gigTitle, won: false });
    notifyDisputeResolved({ userId: payment.payee_id, gigId: payment.gig_id, gigTitle, won: true });

  } else if (status === 'lost') {
    logger.error('Dispute LOST — funds are gone', {
      paymentId: payment.id,
      disputeId: dispute.id,
      amount: dispute.amount,
    });

    // Transition to refunded_full (money was taken by the bank)
    try {
      // disputed → refund_pending → refunded_full
      await transitionPaymentStatus(payment.id, PAYMENT_STATES.REFUND_PENDING, {
        dispute_status: 'lost',
      });
      await transitionPaymentStatus(payment.id, PAYMENT_STATES.REFUNDED_FULL, {
        refunded_amount: dispute.amount,
      });
    } catch (transErr) {
      await supabaseAdmin
        .from('Payment')
        .update({
          payment_status: PAYMENT_STATES.REFUNDED_FULL,
          dispute_status: 'lost',
          refunded_amount: dispute.amount,
          updated_at: nowIso,
        })
        .eq('id', payment.id);
    }

    // If we already paid the provider, they now owe us
    if (payment.stripe_transfer_id) {
      logger.error('ADMIN ACTION REQUIRED: Provider owes platform after lost dispute', {
        paymentId: payment.id,
        payeeId: payment.payee_id,
        transferId: payment.stripe_transfer_id,
        amount: dispute.amount,
      });
    }

    // Notify both parties — dispute lost
    notifyDisputeResolved({ userId: payment.payer_id, gigId: payment.gig_id, gigTitle, won: true });
    notifyDisputeResolved({ userId: payment.payee_id, gigId: payment.gig_id, gigTitle, won: false });

  } else {
    // warning_closed or other status
    await supabaseAdmin
      .from('Payment')
      .update({
        dispute_status: status,
        updated_at: nowIso,
      })
      .eq('id', payment.id);
  }
}

// ============================================================
// TRANSFER HANDLERS (Phase 6d, 6f)
// In Separate Charges + Transfers, transfer.source_transaction
// is the CHARGE ID, not the PaymentIntent ID.
// ============================================================

async function handleTransferCreated(transfer) {
  logger.info('Transfer created', {
    transferId: transfer.id,
    destination: transfer.destination,
    sourceTransaction: transfer.source_transaction,
  });

  // Look up by charge ID (source_transaction) — correct for Separate Charges + Transfers
  let payment = null;
  if (transfer.source_transaction) {
    payment = await findPaymentByCharge(transfer.source_transaction);
  }
  // Fallback: look up by transfer ID if already stored
  if (!payment) {
    payment = await findPaymentByTransfer(transfer.id);
  }
  // Fallback: check metadata
  if (!payment && transfer.metadata?.payment_id) {
    payment = await findPaymentByField('id', transfer.metadata.payment_id);
  }

  if (!payment) {
    logger.warn('Transfer created but no matching Payment found', {
      transferId: transfer.id,
      sourceTransaction: transfer.source_transaction,
    });
    return;
  }

  await supabaseAdmin
    .from('Payment')
    .update({
      stripe_transfer_id: transfer.id,
      transfer_status: 'in_transit',
      updated_at: new Date().toISOString(),
    })
    .eq('id', payment.id);
}

async function handleTransferPaid(transfer) {
  logger.info('Transfer paid', { transferId: transfer.id });

  // Find payment by transfer ID
  const payment = await findPaymentByTransfer(transfer.id);
  if (!payment) {
    logger.warn('Transfer paid but no matching Payment found', { transferId: transfer.id });
    return;
  }

  // Transition to transferred
  if (payment.payment_status === PAYMENT_STATES.TRANSFER_PENDING) {
    try {
      await transitionPaymentStatus(payment.id, PAYMENT_STATES.TRANSFERRED, {
        transfer_status: 'paid',
        transfer_completed_at: new Date().toISOString(),
      });
    } catch (transErr) {
      // Direct update fallback
      await supabaseAdmin
        .from('Payment')
        .update({
          payment_status: PAYMENT_STATES.TRANSFERRED,
          transfer_status: 'paid',
          transfer_completed_at: new Date().toISOString(),
          updated_at: new Date().toISOString(),
        })
        .eq('id', payment.id);
    }

    // Notify provider: payout received
    const gig = await getGigInfo(payment.gig_id);
    notifyTransferCompleted({
      userId: payment.payee_id,
      gigId: payment.gig_id,
      gigTitle: gig?.title,
      amount: payment.amount_to_payee,
    });
  } else {
    // Fallback update
    await supabaseAdmin
      .from('Payment')
      .update({
        transfer_status: 'paid',
        transfer_completed_at: new Date().toISOString(),
        updated_at: new Date().toISOString(),
      })
      .eq('stripe_transfer_id', transfer.id);
  }
}

async function handleTransferFailed(transfer) {
  logger.warn('Transfer failed', {
    transferId: transfer.id,
    error: transfer.failure_message,
  });

  const payment = await findPaymentByTransfer(transfer.id);
  if (!payment) return;

  await supabaseAdmin
    .from('Payment')
    .update({
      transfer_status: 'failed',
      failure_message: transfer.failure_message,
      updated_at: new Date().toISOString(),
    })
    .eq('id', payment.id);

  // Notify provider about failed transfer
  const gig = await getGigInfo(payment.gig_id);
  createNotification({
    userId: payment.payee_id,
    type: 'payout_failed',
    title: 'Payout failed',
    body: `Your payout for "${gig?.title || 'a gig'}" failed to process. Please check your Stripe account settings.`,
    icon: '❌',
    link: '/app/settings/payments',
    metadata: { gig_id: payment.gig_id, payment_id: payment.id },
  });
}

/**
 * transfer.reversed (Phase 6d)
 * Handles transfer reversals (e.g., post-transfer refund clawback).
 */
async function handleTransferReversed(transfer) {
  logger.warn('Transfer reversed', {
    transferId: transfer.id,
    amountReversed: transfer.amount_reversed,
    reversals: transfer.reversals?.data?.length,
  });

  const payment = await findPaymentByTransfer(transfer.id);
  if (!payment) {
    logger.warn('Transfer reversed but no matching Payment found', { transferId: transfer.id });
    return;
  }

  // Get the latest reversal
  const latestReversal = transfer.reversals?.data?.[0];

  await supabaseAdmin
    .from('Payment')
    .update({
      stripe_transfer_reversal_id: latestReversal?.id || null,
      transfer_status: transfer.amount_reversed >= transfer.amount ? 'reversed' : 'partially_reversed',
      updated_at: new Date().toISOString(),
    })
    .eq('id', payment.id);

  // Notify provider about reversal
  const gig = await getGigInfo(payment.gig_id);
  createNotification({
    userId: payment.payee_id,
    type: 'payout_reversed',
    title: 'Payout reversed',
    body: `A payout of $${(transfer.amount_reversed / 100).toFixed(2)} for "${gig?.title || 'a gig'}" has been reversed due to a refund or dispute.`,
    icon: '↩️',
    link: `/gigs/${payment.gig_id}`,
    metadata: {
      gig_id: payment.gig_id,
      payment_id: payment.id,
      amount_reversed: transfer.amount_reversed,
    },
  });
}

// ============================================================
// PAYOUT HANDLERS (unchanged)
// ============================================================

async function handlePayoutCreated(payout, connectedAccountId) {
  logger.info('Payout created', {
    payoutId: payout.id,
    amount: payout.amount,
    connectedAccountId,
  });

  // Look up StripeAccount — prefer event.account (works for all payout types),
  // fall back to payout.destination (only works for automatic payouts where
  // destination is the Connect account ID, not a bank account ID like ba_xxx).
  let account = null;
  if (connectedAccountId) {
    const { data } = await supabaseAdmin
      .from('StripeAccount')
      .select('id, user_id')
      .eq('stripe_account_id', connectedAccountId)
      .single();
    account = data;
  }
  if (!account) {
    const { data } = await supabaseAdmin
      .from('StripeAccount')
      .select('id, user_id')
      .eq('stripe_account_id', payout.destination)
      .single();
    account = data;
  }

  if (account) {
    await supabaseAdmin.from('Payout').insert({
      stripe_account_id: account.id,
      user_id: account.user_id,
      stripe_payout_id: payout.id,
      amount: payout.amount,
      currency: payout.currency,
      payout_status: payout.status,
      destination_type: payout.type,
      destination_last4: (typeof payout.destination === 'object' && payout.destination?.last4) || null,
      arrival_date: new Date(payout.arrival_date * 1000).toISOString().split('T')[0],
    });
  }
}

async function handlePayoutPaid(payout, connectedAccountId) {
  logger.info('Payout paid', { payoutId: payout.id, connectedAccountId });

  await supabaseAdmin
    .from('Payout')
    .update({
      payout_status: 'paid',
      updated_at: new Date().toISOString(),
    })
    .eq('stripe_payout_id', payout.id);
}

async function handlePayoutFailed(payout, connectedAccountId) {
  logger.warn('Payout failed', {
    payoutId: payout.id,
    connectedAccountId,
    error: payout.failure_message,
  });

  await supabaseAdmin
    .from('Payout')
    .update({
      payout_status: 'failed',
      failure_code: payout.failure_code,
      failure_message: payout.failure_message,
      updated_at: new Date().toISOString(),
    })
    .eq('stripe_payout_id', payout.id);
}

// ============================================================
// REFUND / PAYMENT METHOD / CUSTOMER / CAPABILITY HANDLERS
// ============================================================

async function handleRefundUpdated(refund) {
  logger.info('Refund updated', {
    refundId: refund.id,
    status: refund.status,
  });

  await supabaseAdmin
    .from('Refund')
    .update({
      refund_status: refund.status,
      refund_succeeded_at: refund.status === 'succeeded' ? new Date().toISOString() : null,
    })
    .eq('stripe_refund_id', refund.id);
}

async function handlePaymentMethodAttached(paymentMethod) {
  logger.info('Payment method attached', {
    paymentMethodId: paymentMethod.id,
    customer: paymentMethod.customer,
  });

  if (!paymentMethod.customer) return;

  // Find the user for this Stripe customer
  const { data: user } = await supabaseAdmin
    .from('User')
    .select('id')
    .eq('stripe_customer_id', paymentMethod.customer)
    .maybeSingle();

  if (!user) {
    logger.info('payment_method.attached: no user found for customer', {
      customer: paymentMethod.customer,
    });
    return;
  }

  // Check if already saved
  const { data: existing } = await supabaseAdmin
    .from('PaymentMethod')
    .select('id')
    .eq('stripe_payment_method_id', paymentMethod.id)
    .maybeSingle();

  if (existing) return; // Already tracked

  // Build details
  const details = {
    user_id: user.id,
    stripe_customer_id: paymentMethod.customer,
    stripe_payment_method_id: paymentMethod.id,
    payment_method_type: paymentMethod.type,
  };

  if (paymentMethod.type === 'card' && paymentMethod.card) {
    details.card_brand = paymentMethod.card.brand;
    details.card_last4 = paymentMethod.card.last4;
    details.card_exp_month = paymentMethod.card.exp_month;
    details.card_exp_year = paymentMethod.card.exp_year;
    details.card_funding = paymentMethod.card.funding;
  }

  // Check if this is the user's first method (make it default)
  const { data: existingMethods } = await supabaseAdmin
    .from('PaymentMethod')
    .select('id')
    .eq('user_id', user.id);

  const isFirstMethod = !existingMethods || existingMethods.length === 0;

  const { error: insertErr } = await supabaseAdmin
    .from('PaymentMethod')
    .insert({
      ...details,
      is_default: isFirstMethod,
    });

  if (insertErr) {
    logger.error('payment_method.attached: failed to save', {
      paymentMethodId: paymentMethod.id,
      error: insertErr.message,
    });
  } else {
    logger.info('payment_method.attached: saved to PaymentMethod table', {
      paymentMethodId: paymentMethod.id,
      userId: user.id,
      isDefault: isFirstMethod,
    });
  }
}

async function handlePaymentMethodDetached(paymentMethod) {
  logger.info('Payment method detached', {
    paymentMethodId: paymentMethod.id,
  });

  await supabaseAdmin
    .from('PaymentMethod')
    .delete()
    .eq('stripe_payment_method_id', paymentMethod.id);
}

async function handleCustomerUpdated(customer) {
  logger.info('Customer updated', { customerId: customer.id });

  // Update default payment method if changed
  if (customer.invoice_settings?.default_payment_method) {
    await supabaseAdmin
      .from('PaymentMethod')
      .update({ is_default: false })
      .eq('stripe_customer_id', customer.id);

    await supabaseAdmin
      .from('PaymentMethod')
      .update({ is_default: true })
      .eq('stripe_payment_method_id', customer.invoice_settings.default_payment_method);
  }
}

async function handleCapabilityUpdated(capability) {
  logger.info('Capability updated', {
    account: capability.account,
    capability: capability.id,
    status: capability.status,
  });

  const updates = {};

  if (capability.id === 'card_payments') {
    updates.card_payments_enabled = capability.status === 'active';
  }

  if (capability.id === 'transfers') {
    updates.transfers_enabled = capability.status === 'active';
  }

  if (Object.keys(updates).length > 0) {
    updates.updated_at = new Date().toISOString();

    await supabaseAdmin
      .from('StripeAccount')
      .update(updates)
      .eq('stripe_account_id', capability.account);
  }
}

module.exports = router;
