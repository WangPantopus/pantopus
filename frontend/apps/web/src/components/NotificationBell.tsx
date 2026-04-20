'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import * as api from '@pantopus/api';
import { useBadges } from '@/contexts/BadgeContext';
import { useSocket } from '@/contexts/SocketContext';
import { formatTimeAgo as timeAgo } from '@pantopus/ui-utils';
import type { Notification } from '@pantopus/types';

export default function NotificationBell() {
  const router = useRouter();
  const socket = useSocket();
  const [open, setOpen] = useState(false);
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const { notifications: unreadCount } = useBadges();
  const [loading, setLoading] = useState(false);
  const panelRef = useRef<HTMLDivElement>(null);
  const [contextFilter, setContextFilter] = useState<'all' | 'personal' | 'business'>('all');

  const loadNotifications = useCallback(async () => {
    setLoading(true);
    try {
      const params: Record<string, unknown> = { limit: 20 };
      if (contextFilter !== 'all') {
        params.context = contextFilter;
      }
      const res = await api.notifications.getNotifications(params);
      setNotifications(res.notifications || []);
    } catch {
      // silent
    } finally {
      setLoading(false);
    }
  }, [contextFilter]);

  // Load full list when panel opens or context filter changes
  useEffect(() => {
    if (open) {
      loadNotifications();
    }
  }, [open, loadNotifications]);

  // Listen for real-time notification:new from socket
  useEffect(() => {
    if (!socket) return;

    const handleNewNotification = (notif: Notification) => {
      // Prepend to list if panel has been loaded
      setNotifications((prev) => {
        if (prev.length === 0 && !open) return prev; // not yet loaded
        if (prev.some((n) => n.id === notif.id)) return prev; // dedupe
        return [notif, ...prev];
      });
    };

    socket.on('notification:new', handleNewNotification);
    return () => {
      socket.off('notification:new', handleNewNotification);
    };
  }, [socket, open]);

  // Close on outside click
  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (panelRef.current && !panelRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open]);

  const handleNotificationClick = async (notif: Notification) => {
    // Mark as read
    if (!notif.is_read) {
      try {
        await api.notifications.markAsRead(notif.id);
        setNotifications((prev) =>
          prev.map((n) => (n.id === notif.id ? { ...n, is_read: true } : n))
        );
      } catch {}
    }

    // Navigate if link exists (normalize /homes/... to /app/homes/... for web routes)
    if (notif.link) {
      setOpen(false);
      const path = notif.link.startsWith('/homes/') && !notif.link.startsWith('/app/')
        ? `/app${notif.link}`
        : notif.link;
      router.push(path);
    }
  };

  const handleMarkAllRead = async () => {
    try {
      await api.notifications.markAllAsRead();
      setNotifications((prev) => prev.map((n) => ({ ...n, is_read: true })));
    } catch {}
  };

  const handleDelete = async (e: React.MouseEvent, notifId: string) => {
    e.stopPropagation();
    try {
      await api.notifications.deleteNotification(notifId);
      setNotifications((prev) => prev.filter((n) => n.id !== notifId));
    } catch {}
  };

  return (
    <div className="relative" ref={panelRef}>
      {/* Bell button */}
      <button
        onClick={() => setOpen(!open)}
        className="p-2 hover-bg-app rounded-lg relative"
        aria-label="Notifications"
        title="Notifications"
      >
        <svg className="w-5 h-5 text-app-muted" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"
          />
        </svg>
        {unreadCount > 0 && (
          <span className="absolute top-0.5 right-0.5 min-w-[18px] h-[18px] bg-red-500 text-white text-[10px] font-bold rounded-full flex items-center justify-center px-1">
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {/* Dropdown panel */}
      {open && (
        <div className="absolute right-0 top-full mt-2 w-80 sm:w-96 bg-surface rounded-xl shadow-2xl border border-app z-[100] overflow-hidden">
          {/* Header */}
          <div className="px-4 py-3 border-b border-app">
            <div className="flex items-center justify-between mb-2">
              <h3 className="text-sm font-semibold text-app">Notifications</h3>
              {unreadCount > 0 && (
                <button
                  onClick={handleMarkAllRead}
                  className="text-xs text-blue-600 hover:text-blue-800 font-medium"
                >
                  Mark all read
                </button>
              )}
            </div>
            {/* Context filter tabs */}
            <div className="flex gap-1">
              {(['all', 'personal', 'business'] as const).map((ctx) => (
                <button
                  key={ctx}
                  onClick={() => setContextFilter(ctx)}
                  className={`px-2.5 py-1 rounded-full text-xs font-medium transition ${
                    contextFilter === ctx
                      ? 'bg-primary-600 text-white'
                      : 'bg-surface-muted text-app-secondary hover:text-app'
                  }`}
                >
                  {ctx === 'all' ? 'All' : ctx === 'personal' ? 'Personal' : 'Business'}
                </button>
              ))}
            </div>
          </div>

          {/* List */}
          <div className="max-h-[400px] overflow-y-auto">
            {loading && notifications.length === 0 ? (
              <div className="px-4 py-8 text-center">
                <div className="animate-spin rounded-full h-6 w-6 border-2 border-app-border border-t-gray-600 dark:border-t-gray-300 mx-auto" />
                <p className="text-xs text-app-muted mt-2">Loading...</p>
              </div>
            ) : notifications.length === 0 ? (
              <div className="px-4 py-8 text-center">
                <div className="text-3xl mb-2">🔔</div>
                <p className="text-sm text-app-muted">No notifications yet</p>
                <p className="text-xs text-app-muted mt-1">We&apos;ll notify you when something happens</p>
              </div>
            ) : (
              <div>
                {notifications.map((notif) => (
                  <div
                    key={notif.id}
                    role="button"
                    tabIndex={0}
                    onClick={() => handleNotificationClick(notif)}
                    onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') handleNotificationClick(notif); }}
                    className={`w-full text-left px-4 py-3 flex gap-3 hover-bg-app transition border-b border-app last:border-0 group cursor-pointer ${
                      !notif.is_read ? 'bg-blue-50/40 dark:bg-primary-900/20' : ''
                    }`}
                  >
                    {/* Icon */}
                    <div className="text-xl flex-shrink-0 mt-0.5">{notif.icon || '🔔'}</div>

                    {/* Content */}
                    <div className="min-w-0 flex-1">
                      <div className="flex items-start justify-between gap-2">
                        <p
                          className={`text-sm leading-snug ${
                            !notif.is_read ? 'font-semibold text-app' : 'font-medium text-app-muted'
                          }`}
                        >
                          {notif.title}
                        </p>
                        {/* Unread dot */}
                        {!notif.is_read && (
                          <span className="w-2 h-2 rounded-full bg-blue-500 flex-shrink-0 mt-1.5" />
                        )}
                      </div>
                      {notif.body && (
                        <p className="text-xs text-app-muted mt-0.5 line-clamp-2">{notif.body}</p>
                      )}
                      <p className="text-[10px] text-app-muted mt-1">{timeAgo(notif.created_at)}</p>
                    </div>

                    {/* Delete on hover */}
                    <button
                      onClick={(e) => handleDelete(e, notif.id)}
                      className="opacity-0 group-hover:opacity-100 text-app-muted hover:text-red-500 p-1 flex-shrink-0 transition"
                      title="Remove"
                    >
                      <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                      </svg>
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Footer */}
          {notifications.length > 0 && (
            <div className="border-t border-app px-4 py-2.5 text-center">
              <button
                onClick={() => {
                  setOpen(false);
                  router.push('/app/notifications');
                }}
                className="text-xs text-blue-600 hover:text-blue-800 font-medium"
              >
                View all notifications
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
