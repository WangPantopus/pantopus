'use client';

import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { useInfiniteQuery, useQueryClient, type InfiniteData } from '@tanstack/react-query';
import { useVirtualizer } from '@tanstack/react-virtual';
import * as api from '@pantopus/api';
import { getAuthToken } from '@pantopus/api';
import { formatTimeAgo as timeAgo } from '@pantopus/ui-utils';
import { useSocket } from '@/contexts/SocketContext';
import { queryKeys } from '@/lib/query-keys';
import type { Notification } from '@pantopus/types';
import NotificationRow from './NotificationRow';

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('en-US', {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

type Filter = 'all' | 'unread' | 'read';
type ContextFilter = 'all' | 'personal' | 'business';

type NotificationsPage = Awaited<ReturnType<typeof api.notifications.getNotifications>>;

const LIMIT = 30;

export default function NotificationsPage() {
  const router = useRouter();
  const socket = useSocket();
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState<Filter>('all');
  const [contextFilter, setContextFilter] = useState<ContextFilter>('all');
  const [selectedNotif, setSelectedNotif] = useState<Notification | null>(null);

  // Auth guard
  useEffect(() => {
    if (!getAuthToken()) router.push('/login');
  }, [router]);

  // Query key for this filter combo — filter + contextFilter segment the cache
  const notifKey = useMemo(
    () => [...queryKeys.notifications(), filter, contextFilter] as const,
    [filter, contextFilter],
  );

  // ── Notifications list: useInfiniteQuery (30 per page) ──────
  const notifQuery = useInfiniteQuery<NotificationsPage, Error, InfiniteData<NotificationsPage>, typeof notifKey, number>({
    queryKey: notifKey,
    initialPageParam: 0,
    queryFn: async ({ pageParam }) => {
      const params: Record<string, unknown> = {
        limit: LIMIT,
        offset: pageParam,
        ...(filter === 'unread' ? { unread: true } : {}),
      };
      if (contextFilter !== 'all') {
        params.context = contextFilter;
      }
      return api.notifications.getNotifications(params);
    },
    getNextPageParam: (lastPage, allPages) => {
      if (!lastPage?.hasMore) return undefined;
      return allPages.reduce((sum, p) => sum + (p.notifications?.length || 0), 0);
    },
    staleTime: 30_000,
  });

  // Derived state (preserves the useState shape consumed by the JSX below)
  const notifications = useMemo<Notification[]>(() => {
    const pages = notifQuery.data?.pages ?? [];
    const seen = new Set<string>();
    const out: Notification[] = [];
    for (const page of pages) {
      for (const n of page?.notifications || []) {
        if (seen.has(n.id)) continue;
        seen.add(n.id);
        out.push(n);
      }
    }
    return out;
  }, [notifQuery.data]);

  const loading = notifQuery.isPending;
  const loadingMore = notifQuery.isFetchingNextPage;
  const hasMore = notifQuery.hasNextPage ?? false;

  // Cache mutation helpers
  const mutateNotifications = useCallback(
    (updater: (n: Notification) => Notification) => {
      queryClient.setQueryData<InfiniteData<NotificationsPage>>(notifKey, (old) => {
        if (!old) return old;
        return {
          ...old,
          pages: old.pages.map((page) => ({
            ...page,
            notifications: (page.notifications || []).map(updater),
          })),
        };
      });
    },
    [queryClient, notifKey],
  );

  const removeNotification = useCallback(
    (predicate: (n: Notification) => boolean) => {
      queryClient.setQueryData<InfiniteData<NotificationsPage>>(notifKey, (old) => {
        if (!old) return old;
        return {
          ...old,
          pages: old.pages.map((page) => ({
            ...page,
            notifications: (page.notifications || []).filter((n) => !predicate(n)),
          })),
        };
      });
    },
    [queryClient, notifKey],
  );

  const prependNotification = useCallback(
    (notif: Notification) => {
      queryClient.setQueryData<InfiniteData<NotificationsPage>>(notifKey, (old) => {
        if (!old) return old;
        // Dedupe — check all pages to avoid duplicate entries from socket + refetch races
        for (const page of old.pages) {
          if ((page.notifications || []).some((n) => n.id === notif.id)) return old;
        }
        const [firstPage, ...rest] = old.pages;
        if (!firstPage) return old;
        return {
          ...old,
          pages: [
            { ...firstPage, notifications: [notif, ...(firstPage.notifications || [])] },
            ...rest,
          ],
        };
      });
    },
    [queryClient, notifKey],
  );

  // Listen for real-time notification:new from socket
  useEffect(() => {
    if (!socket) return;
    const handleNewNotification = (notif: Notification) => {
      prependNotification(notif);
    };
    socket.on('notification:new', handleNewNotification);
    return () => {
      socket.off('notification:new', handleNewNotification);
    };
  }, [socket, prependNotification]);

  const handleNotificationClick = useCallback(async (notif: Notification) => {
    // Mark as read
    if (!notif.is_read) {
      try {
        await api.notifications.markAsRead(notif.id);
        mutateNotifications((n) => (n.id === notif.id ? { ...n, is_read: true } : n));
      } catch {}
    }

    // If has a link, navigate directly (normalize /homes/... to /app/homes/... for web routes)
    if (notif.link) {
      const path = notif.link.startsWith('/homes/') && !notif.link.startsWith('/app/')
        ? `/app${notif.link}`
        : notif.link;
      router.push(path);
    } else {
      setSelectedNotif(notif);
    }
  }, [router, mutateNotifications]);

  const handleMarkAllRead = async () => {
    try {
      await api.notifications.markAllAsRead();
      mutateNotifications((n) => ({ ...n, is_read: true }));
    } catch {}
  };

  const handleDelete = useCallback(async (notifId: string) => {
    try {
      await api.notifications.deleteNotification(notifId);
      removeNotification((n) => n.id === notifId);
      setSelectedNotif((prev) => prev?.id === notifId ? null : prev);
    } catch {}
  }, [removeNotification]);

  const unreadCount = useMemo(
    () => notifications.filter((n) => !n.is_read).length,
    [notifications],
  );

  // Filter locally for "read" since API only supports "unread" filter
  const displayedNotifications = useMemo(
    () => (filter === 'read' ? notifications.filter((n) => n.is_read) : notifications),
    [filter, notifications],
  );

  // Group by date (memoized — was being recomputed every render)
  const grouped = useMemo(() => groupByDate(displayedNotifications), [displayedNotifications]);

  // Flatten groups into a single (Header | Notification)[] array for virtualization.
  // Each date-group header becomes a single "header" virtual item that sits above
  // its group's notifications.
  type FlatItem =
    | { type: 'header'; label: string; key: string }
    | { type: 'notification'; notif: Notification; groupLabel: string; isFirst: boolean; isLast: boolean };

  const flatItems = useMemo<FlatItem[]>(() => {
    const out: FlatItem[] = [];
    for (const group of grouped) {
      out.push({ type: 'header', label: group.label, key: `header:${group.label}` });
      group.items.forEach((notif, i) => {
        out.push({
          type: 'notification',
          notif,
          groupLabel: group.label,
          isFirst: i === 0,
          isLast: i === group.items.length - 1,
        });
      });
    }
    return out;
  }, [grouped]);

  // ── Virtualize flat list ──────────────────────────────────
  const listScrollRef = useRef<HTMLDivElement>(null);
  const virtualizer = useVirtualizer({
    count: flatItems.length,
    getScrollElement: () => listScrollRef.current,
    estimateSize: (index) => (flatItems[index]?.type === 'header' ? 36 : 84),
    overscan: 8,
  });

  // Shim for load-more button onClick
  const loadNotifications = useCallback(
    (_reset = false) => {
      if (_reset) void notifQuery.refetch();
      else void notifQuery.fetchNextPage();
    },
    [notifQuery],
  );

  return (
    <div className="max-w-3xl mx-auto px-4 py-6">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-bold text-app-text">Notifications</h1>
          <p className="text-sm text-app-text-secondary mt-0.5">
            {unreadCount > 0
              ? `${unreadCount} unread notification${unreadCount !== 1 ? 's' : ''}`
              : 'All caught up!'}
          </p>
        </div>
        <div className="flex items-center gap-2">
          {unreadCount > 0 && (
            <button
              onClick={handleMarkAllRead}
              className="px-3 py-1.5 text-xs font-medium text-blue-600 hover:text-blue-800 hover:bg-blue-50 rounded-lg transition"
            >
              Mark all read
            </button>
          )}
        </div>
      </div>

      {/* Filter tabs */}
      <div className="flex items-center justify-between gap-4 mb-4">
        <div className="flex gap-1">
          {(['all', 'unread', 'read'] as Filter[]).map((f) => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              className={`px-3 py-1.5 rounded-lg text-sm font-medium transition capitalize ${
                filter === f
                  ? 'bg-gray-900 text-white'
                  : 'text-app-text-secondary hover:bg-app-hover'
              }`}
            >
              {f}
            </button>
          ))}
        </div>
        <div className="flex gap-1">
          {(['all', 'personal', 'business'] as ContextFilter[]).map((ctx) => (
            <button
              key={ctx}
              onClick={() => setContextFilter(ctx)}
              className={`px-2.5 py-1 rounded-full text-xs font-medium transition ${
                contextFilter === ctx
                  ? 'bg-primary-600 text-white'
                  : 'bg-app-surface-sunken text-app-text-secondary hover:text-app-text'
              }`}
            >
              {ctx === 'all' ? 'All' : ctx === 'personal' ? 'Personal' : 'Business'}
            </button>
          ))}
        </div>
      </div>

      {/* Detail panel (right side overlay on mobile, inline on desktop) */}
      {selectedNotif && (
        <div className="mb-4 bg-app-surface rounded-xl border border-app-border p-5">
          <div className="flex items-start justify-between gap-3 mb-3">
            <div className="flex items-start gap-3">
              <span className="text-2xl flex-shrink-0">{selectedNotif.icon || '🔔'}</span>
              <div>
                <h2 className="text-base font-semibold text-app-text">
                  {selectedNotif.title}
                </h2>
                <p className="text-xs text-app-text-muted mt-0.5">
                  {formatDate(selectedNotif.created_at)}
                </p>
              </div>
            </div>
            <button
              onClick={() => setSelectedNotif(null)}
              className="p-1.5 hover:bg-app-hover rounded-lg transition text-app-text-muted hover:text-app-text-secondary"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          {selectedNotif.body && (
            <p className="text-sm text-app-text-strong leading-relaxed whitespace-pre-wrap">
              {selectedNotif.body}
            </p>
          )}

          {selectedNotif.link && (
            <button
              onClick={() => router.push(selectedNotif.link!)}
              className="mt-4 px-4 py-2 bg-gray-900 text-white text-sm font-medium rounded-lg hover:bg-gray-800 transition"
            >
              View Details →
            </button>
          )}

          <div className="mt-4 pt-3 border-t border-app-border-subtle flex items-center gap-3">
            <span className="text-xs text-app-text-muted capitalize">
              Type: {selectedNotif.type?.replace(/_/g, ' ')}
            </span>
            <button
              onClick={() => handleDelete(selectedNotif.id)}
              className="text-xs text-red-500 hover:text-red-700 font-medium ml-auto"
            >
              Delete
            </button>
          </div>
        </div>
      )}

      {/* Notification list */}
      {loading ? (
        <div className="text-center py-16">
          <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-gray-600 mx-auto" />
          <p className="mt-4 text-sm text-app-text-secondary">Loading notifications...</p>
        </div>
      ) : displayedNotifications.length === 0 ? (
        <div className="text-center py-16 bg-app-surface rounded-xl border border-app-border">
          <div className="text-5xl mb-3">🔔</div>
          <h3 className="text-lg font-semibold text-app-text mb-1">
            {filter === 'unread' ? 'No unread notifications' : filter === 'read' ? 'No read notifications' : 'No notifications yet'}
          </h3>
          <p className="text-sm text-app-text-secondary">
            {filter === 'all'
              ? "We'll notify you when something happens."
              : 'Try a different filter.'}
          </p>
        </div>
      ) : (
        <div>
          <div
            ref={listScrollRef}
            className="overflow-y-auto"
            style={{ maxHeight: 'calc(100vh - 260px)' }}
          >
            <div
              style={{
                height: `${virtualizer.getTotalSize()}px`,
                width: '100%',
                position: 'relative',
              }}
            >
              {virtualizer.getVirtualItems().map((virtualRow) => {
                const item = flatItems[virtualRow.index];
                return (
                  <div
                    key={item.type === 'header' ? item.key : item.notif.id}
                    data-index={virtualRow.index}
                    ref={virtualizer.measureElement}
                    style={{
                      position: 'absolute',
                      top: 0,
                      left: 0,
                      width: '100%',
                      transform: `translateY(${virtualRow.start}px)`,
                    }}
                  >
                    {item.type === 'header' ? (
                      <h3 className="text-xs font-semibold text-app-text-muted uppercase tracking-wider mb-2 px-1 pt-4 first:pt-0">
                        {item.label}
                      </h3>
                    ) : (
                      <div
                        className={`bg-app-surface border-l border-r border-app-border ${
                          item.isFirst ? 'rounded-t-xl border-t' : ''
                        } ${
                          item.isLast ? 'rounded-b-xl border-b mb-4' : 'border-b border-b-app-border-subtle'
                        } overflow-hidden`}
                      >
                        <NotificationRow
                          notif={item.notif}
                          isSelected={selectedNotif?.id === item.notif.id}
                          onClick={handleNotificationClick}
                          onDelete={handleDelete}
                        />
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>

          {/* Load more */}
          {hasMore && (
            <div className="text-center py-3">
              <button
                onClick={() => loadNotifications(false)}
                disabled={loadingMore}
                className="px-4 py-2 text-sm font-medium text-app-text-secondary hover:text-app-text hover:bg-app-hover rounded-lg transition disabled:opacity-50"
              >
                {loadingMore ? 'Loading...' : 'Load more'}
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// Group notifications by date
function groupByDate(notifications: Notification[]) {
  const groups: { label: string; items: Notification[] }[] = [];
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const yesterday = new Date(today);
  yesterday.setDate(yesterday.getDate() - 1);
  const weekAgo = new Date(today);
  weekAgo.setDate(weekAgo.getDate() - 7);

  const buckets: Record<string, Notification[]> = {};

  for (const notif of notifications) {
    const d = new Date(notif.created_at);
    d.setHours(0, 0, 0, 0);
    let label: string;

    if (d >= today) {
      label = 'Today';
    } else if (d >= yesterday) {
      label = 'Yesterday';
    } else if (d >= weekAgo) {
      label = 'This Week';
    } else {
      label = d.toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
    }

    if (!buckets[label]) buckets[label] = [];
    buckets[label].push(notif);
  }

  for (const [label, items] of Object.entries(buckets)) {
    groups.push({ label, items });
  }

  return groups;
}
