/**
 * Push Service
 *
 * Sends push notifications to iOS/Android devices via Expo's push service.
 * Manages device token storage and cleanup of expired tokens.
 * Checks delivery receipts to detect APNs/FCM failures.
 */

const { Expo } = require('expo-server-sdk');
const supabaseAdmin = require('../config/supabaseAdmin');
const logger = require('../utils/logger');

const expo = new Expo();

// Collect ticket IDs so we can check delivery receipts later.
// This is an in-memory queue; a production system might persist these.
const _pendingReceiptIds = [];
const RECEIPT_CHECK_INTERVAL_MS = 60_000; // check every 60 seconds
const RECEIPT_BATCH_SIZE = 300; // Expo recommends max ~300 per request

/**
 * Save or update a user's Expo push token.
 * The token column is globally unique — upserting on it reassigns
 * ownership to the current user, preventing cross-account push leaks
 * on shared devices.
 */
async function saveToken(userId, token) {
  if (!Expo.isExpoPushToken(token)) {
    logger.warn('Invalid Expo push token', { userId, token });
    return null;
  }

  const { data, error } = await supabaseAdmin
    .from('PushToken')
    .upsert(
      { user_id: userId, token, updated_at: new Date().toISOString() },
      { onConflict: 'token' },
    )
    .select()
    .single();

  if (error) {
    logger.error('Failed to save push token', { error: error.message, userId });
    return null;
  }
  return data;
}

/**
 * Remove a specific push token (e.g. on logout).
 */
async function removeToken(userId, token) {
  const { error } = await supabaseAdmin
    .from('PushToken')
    .delete()
    .eq('user_id', userId)
    .eq('token', token);

  if (error) {
    logger.error('Failed to remove push token', { error: error.message, userId });
  }
}

/**
 * Remove all push tokens for a user (e.g. on account deletion).
 */
async function removeAllTokens(userId) {
  const { error } = await supabaseAdmin
    .from('PushToken')
    .delete()
    .eq('user_id', userId);

  if (error) {
    logger.error('Failed to remove all push tokens', { error: error.message, userId });
  }
}

/**
 * Send push notifications and collect ticket IDs for receipt checking.
 * Returns the list of invalid tokens that should be cleaned up.
 */
async function sendMessages(messages) {
  if (messages.length === 0) return [];

  const chunks = expo.chunkPushNotifications(messages);
  const invalidTokens = [];

  for (const chunk of chunks) {
    try {
      const tickets = await expo.sendPushNotificationsAsync(chunk);
      for (let i = 0; i < tickets.length; i++) {
        const ticket = tickets[i];
        if (ticket.status === 'ok' && ticket.id) {
          // Queue the receipt ID for later verification
          _pendingReceiptIds.push(ticket.id);
        } else if (ticket.status === 'error') {
          logger.warn('Push ticket error', {
            error: ticket.message,
            detail: ticket.details?.error,
            token: chunk[i].to,
          });
          if (
            ticket.details?.error === 'DeviceNotRegistered' ||
            ticket.details?.error === 'InvalidCredentials'
          ) {
            invalidTokens.push(chunk[i].to);
          }
        }
      }
    } catch (err) {
      logger.error('Push chunk send failed', { error: err.message });
    }
  }

  return invalidTokens;
}

/**
 * Send a push notification to a single user.
 * Fetches all registered tokens for the user and sends to each.
 */
async function sendToUser(userId, { title, body, data }) {
  const { data: tokens, error } = await supabaseAdmin
    .from('PushToken')
    .select('token')
    .eq('user_id', userId);

  if (error || !tokens || tokens.length === 0) return;

  const messages = tokens
    .filter((t) => Expo.isExpoPushToken(t.token))
    .map((t) => ({
      to: t.token,
      sound: 'default',
      title,
      body,
      data: data || {},
    }));

  const invalidTokens = await sendMessages(messages);

  if (invalidTokens.length > 0) {
    await supabaseAdmin
      .from('PushToken')
      .delete()
      .eq('user_id', userId)
      .in('token', invalidTokens);
  }
}

/**
 * Send push notifications to multiple users at once.
 */
async function sendToUsers(userIds, { title, body, data }) {
  if (!userIds || userIds.length === 0) return;

  const { data: tokens, error } = await supabaseAdmin
    .from('PushToken')
    .select('user_id, token')
    .in('user_id', userIds);

  if (error || !tokens || tokens.length === 0) return;

  const messages = tokens
    .filter((t) => Expo.isExpoPushToken(t.token))
    .map((t) => ({
      to: t.token,
      sound: 'default',
      title,
      body,
      data: data || {},
    }));

  const invalidTokens = await sendMessages(messages);

  if (invalidTokens.length > 0) {
    await supabaseAdmin
      .from('PushToken')
      .delete()
      .in('token', invalidTokens);
  }
}

// ── Receipt checking ────────────────────────────────────────────────
// Expo recommends checking receipts ~15 min after sending to learn
// whether APNs/FCM actually accepted or rejected the notification.
// We run a periodic check that drains the pending receipt queue.

async function checkReceipts() {
  if (_pendingReceiptIds.length === 0) return;

  // Drain up to RECEIPT_BATCH_SIZE receipt IDs
  const batch = _pendingReceiptIds.splice(0, RECEIPT_BATCH_SIZE);

  try {
    const receiptMap = await expo.getPushNotificationReceiptsAsync(batch);
    const invalidTokensToClean = [];

    for (const [receiptId, receipt] of Object.entries(receiptMap)) {
      if (receipt.status === 'error') {
        logger.warn('Push receipt error (APNs/FCM rejected)', {
          receiptId,
          error: receipt.message,
          detail: receipt.details?.error,
        });
        if (
          receipt.details?.error === 'DeviceNotRegistered' ||
          receipt.details?.error === 'InvalidCredentials'
        ) {
          // We don't have the token here, but the error is logged for debugging.
          // DeviceNotRegistered tokens will also fail on the next send and get cleaned up then.
          logger.warn('Push delivery failed — token may be stale or APNs credentials misconfigured', {
            receiptId,
            detail: receipt.details?.error,
          });
        }
      }
    }
  } catch (err) {
    // If receipt check fails, put the IDs back so we retry next cycle
    _pendingReceiptIds.unshift(...batch);
    logger.error('Failed to check push receipts', { error: err.message, batchSize: batch.length });
  }
}

// Start periodic receipt checking (non-blocking, won't crash the process)
const _receiptInterval = setInterval(() => {
  checkReceipts().catch((err) => {
    logger.error('Receipt check interval error', { error: err.message });
  });
}, RECEIPT_CHECK_INTERVAL_MS);

// Don't let the interval keep Node alive if everything else shuts down
if (_receiptInterval.unref) {
  _receiptInterval.unref();
}

module.exports = {
  saveToken,
  removeToken,
  removeAllTokens,
  sendToUser,
  sendToUsers,
  checkReceipts, // exported for testing
};
