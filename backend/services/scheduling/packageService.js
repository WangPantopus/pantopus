// ============================================================
// Calendarly — session packages: purchase (stateless Stripe verb, no gig-settlement-pipeline
// changes), credit redemption against a booking, and credit restore on cancel.
// NOTE: settlement of package payments rides the same deferred gig-coupled transfer pipeline as
// booking payments — purchase + credit grant work; payout settlement is the documented deferral.
// ============================================================

const supabaseAdmin = require('../../config/supabaseAdmin');
const logger = require('../../utils/logger');
const stripeService = require('../../stripe/stripeService');

/**
 * Buy a package: grant credits immediately for free packages, else create a PaymentIntent and
 * grant credits tied to that Payment. Requires a signed-in buyer (Stripe customer).
 * @returns {Promise<{ success, credit?, clientSecret?, paymentId?, error?, message? }>}
 */
async function purchasePackage({ pkg, buyerUserId }) {
  if (!buyerUserId) return { success: false, error: 'SIGNIN_REQUIRED', message: 'Sign in to buy a package.' };
  if (!pkg || !pkg.is_active) return { success: false, error: 'PACKAGE_UNAVAILABLE' };

  // Free package — grant credits directly.
  if (!pkg.price_cents || pkg.price_cents <= 0) {
    const { data: credit, error } = await supabaseAdmin
      .from('PackageCredit')
      .insert({ package_id: pkg.id, buyer_user_id: buyerUserId, total: pkg.sessions_count, remaining: pkg.sessions_count })
      .select('*')
      .single();
    if (error) return { success: false, error: 'GRANT_FAILED', message: error.message };
    return { success: true, credit, clientSecret: null };
  }

  const payeeId = pkg.owner_user_id;
  if (!payeeId) return { success: false, error: 'NO_PAYEE', message: 'This package has no payee configured.' };

  const res = await stripeService.createPaymentIntentForGig({
    payerId: buyerUserId,
    payeeId,
    gigId: null,
    amount: pkg.price_cents,
    metadata: { kind: 'package', package_id: pkg.id },
    description: `Pantopus package — ${pkg.name || 'Sessions'}`,
  });
  if (!res || !res.success) return { success: false, error: 'PAYMENT_INTENT_FAILED', message: (res && res.error) || 'Could not start payment.' };

  const { error: pErr } = await supabaseAdmin.from('Payment').update({ payment_type: 'package_payment' }).eq('id', res.paymentId);
  if (pErr) logger.error('[packageService] failed to tag package payment', { paymentId: res.paymentId, error: pErr.message });

  const { data: credit, error: cErr } = await supabaseAdmin
    .from('PackageCredit')
    .insert({ package_id: pkg.id, buyer_user_id: buyerUserId, total: pkg.sessions_count, remaining: pkg.sessions_count, payment_id: res.paymentId })
    .select('*')
    .single();
  if (cErr) return { success: false, error: 'GRANT_FAILED', message: cErr.message };

  return { success: true, credit, clientSecret: res.clientSecret, paymentId: res.paymentId };
}

/** Redeem one session credit against a booking (atomic optimistic decrement). */
async function redeemForBooking({ bookingId, creditId, userId }) {
  const { data: credit } = await supabaseAdmin.from('PackageCredit').select('*').eq('id', creditId).maybeSingle();
  if (!credit || credit.buyer_user_id !== userId) return { success: false, error: 'CREDIT_NOT_FOUND' };
  if (credit.remaining <= 0) return { success: false, error: 'NO_CREDIT_REMAINING' };
  // Guarded decrement (remaining unchanged since read) — prevents double-spend under concurrency.
  const { data: dec } = await supabaseAdmin
    .from('PackageCredit')
    .update({ remaining: credit.remaining - 1 })
    .eq('id', creditId)
    .eq('remaining', credit.remaining)
    .select('id');
  if (!dec || !dec.length) return { success: false, error: 'REDEEM_CONFLICT', message: 'Please try again.' };
  const { data: linked, error: bErr } = await supabaseAdmin.from('Booking').update({ package_credit_id: creditId }).eq('id', bookingId).select('id');
  if (bErr || !linked || !linked.length) {
    // Booking gone/modified — restore the credit we just decremented so it isn't lost.
    await supabaseAdmin.from('PackageCredit').update({ remaining: credit.remaining }).eq('id', creditId);
    return { success: false, error: 'BOOKING_UPDATE_FAILED', message: 'Could not apply the credit.' };
  }
  return { success: true, remaining: credit.remaining - 1 };
}

/** Restore a session credit when a credit-redeemed booking is cancelled. Best-effort. */
async function restoreForBooking(booking) {
  if (!booking || !booking.package_credit_id) return;
  const { data: credit } = await supabaseAdmin.from('PackageCredit').select('id, remaining, total').eq('id', booking.package_credit_id).maybeSingle();
  if (credit && credit.remaining < credit.total) {
    await supabaseAdmin.from('PackageCredit').update({ remaining: credit.remaining + 1 }).eq('id', credit.id);
  }
}

module.exports = { purchasePackage, redeemForBooking, restoreForBooking };
