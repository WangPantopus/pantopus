// ============================================================
// NOTIFICATION ENDPOINTS
// In-app notification management
// ============================================================

import { get, post, del } from '../client';
import apiClient from '../client';
import type { ApiRequestConfig } from '../client';

// Re-exported from @pantopus/types
export type { Notification } from '@pantopus/types/notification';
import type { Notification } from '@pantopus/types/notification';

/**
 * Get notifications for the current user
 */
export async function getNotifications(opts?: {
  limit?: number;
  offset?: number;
  unread?: boolean;
}): Promise<{ notifications: Notification[]; unreadCount: number; hasMore: boolean }> {
  const params = new URLSearchParams();
  if (opts?.limit) params.set('limit', String(opts.limit));
  if (opts?.offset) params.set('offset', String(opts.offset));
  if (opts?.unread) params.set('unread', 'true');
  const qs = params.toString();
  return get(`/api/notifications${qs ? `?${qs}` : ''}`);
}

/**
 * Get unread count only (lightweight, for badge)
 */
export async function getUnreadCount(config?: ApiRequestConfig): Promise<{ count: number }> {
  return get('/api/notifications/unread-count', undefined, config);
}

/**
 * Mark a single notification as read
 */
export async function markAsRead(notificationId: string): Promise<{ notification: Notification }> {
  return apiClient.patch(`/api/notifications/${notificationId}/read`).then(r => r.data);
}

/**
 * Mark all notifications as read
 */
export async function markAllAsRead(): Promise<{ message: string }> {
  return post('/api/notifications/read-all');
}

/**
 * Delete a notification
 */
export async function deleteNotification(notificationId: string): Promise<{ message: string }> {
  return del(`/api/notifications/${notificationId}`);
}

/**
 * Register an Expo push token for the current user's device.
 */
export async function registerPushToken(token: string): Promise<{ message: string }> {
  return post('/api/notifications/push-token', { token });
}

/**
 * Unregister an Expo push token (e.g. on logout).
 */
export async function unregisterPushToken(token: string): Promise<{ message: string }> {
  return del('/api/notifications/push-token', { token });
}

/**
 * Check if the user is eligible for the no-bid gig nudge floating modal.
 * Returns whether they have an unread no_bid_gig_nudge notification
 * and whether they have a verified home address.
 */
export async function getNoBidNudgeCheck(): Promise<{ eligible: boolean; hasHome?: boolean }> {
  return get('/api/notifications/no-bid-nudge-check');
}

/**
 * Mark all no_bid_gig_nudge notifications as read.
 * Called after the floating modal fires (or was already dismissed)
 * so the trigger won't block lower-priority promos on next app open.
 */
export async function markNoBidNudgeRead(): Promise<{ ok: boolean }> {
  return post('/api/notifications/no-bid-nudge-mark-read');
}
