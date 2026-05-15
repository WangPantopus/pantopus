'use client';

// T5.1 — Notifications V2 (web).
//
// Single source of truth = `<ListOfRowsShell />`. The page builds two
// tabs (All / Unread), buckets the loaded notifications into Today /
// Earlier sections, and projects each `Notification` DTO into the same
// `RowModel` shape iOS and Android use. The mark-all-read text-button
// lives in the shell's top-bar trailing slot.
//
// Per the T5 plan:
//   - `read` filter is dropped (design has 2 tabs, not 3).
//   - Personal/business context filter is dropped (design has none).
//   - Per-row hover delete affordance is dropped.

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  useInfiniteQuery,
  useQueryClient,
  type InfiniteData,
} from '@tanstack/react-query';
import {
  AtSign,
  BadgeCheck,
  Bell,
  Briefcase,
  CheckCheck,
  Info as InfoIcon,
  MessageCircle,
  ShieldAlert,
  Tag as TagIcon,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import * as api from '@pantopus/api';
import { getAuthToken } from '@pantopus/api';
import type { Notification } from '@pantopus/types';
import { useSocket } from '@/contexts/SocketContext';
import { queryKeys } from '@/lib/query-keys';
import ListOfRowsShell from '@/components/list-of-rows/ListOfRowsShell';
import type {
  ListOfRowsState,
  RowModel,
  RowSection,
  StatusChipVariant,
} from '@/components/list-of-rows/types';

type Tab = 'all' | 'unread';

type NotificationsPage = Awaited<ReturnType<typeof api.notifications.getNotifications>>;

const LIMIT = 30;

type Category =
  | 'reply'
  | 'mention'
  | 'claim'
  | 'gig'
  | 'listing'
  | 'safety'
  | 'system';

interface CategoryStyle {
  label: string;
  icon: LucideIcon;
  chipVariant: StatusChipVariant;
  tileBackground: string;
  tileForeground: string;
}

const CATEGORY_STYLES: Record<Category, CategoryStyle> = {
  reply: {
    label: 'Reply',
    icon: MessageCircle,
    chipVariant: 'personal',
    tileBackground: '#dbeafe',
    tileForeground: '#1d4ed8',
  },
  mention: {
    label: 'Mention',
    icon: AtSign,
    chipVariant: 'business',
    tileBackground: '#f3e8ff',
    tileForeground: '#7c3aed',
  },
  claim: {
    label: 'Claim',
    icon: BadgeCheck,
    chipVariant: 'success',
    tileBackground: '#d1fae5',
    tileForeground: '#047857',
  },
  gig: {
    label: 'Gig',
    icon: Briefcase,
    chipVariant: 'warning',
    tileBackground: '#fef3c7',
    tileForeground: '#92400e',
  },
  listing: {
    label: 'Listing',
    icon: TagIcon,
    chipVariant: 'home',
    tileBackground: '#dcfce7',
    tileForeground: '#16a34a',
  },
  safety: {
    label: 'Safety',
    icon: ShieldAlert,
    chipVariant: 'error',
    tileBackground: '#fee2e2',
    tileForeground: '#b91c1c',
  },
  system: {
    label: 'System',
    icon: InfoIcon,
    chipVariant: 'neutral',
    tileBackground: '#f3f4f6',
    tileForeground: '#374151',
  },
};

function categoryFromType(raw: string | null | undefined): Category {
  const lower = (raw ?? '').toLowerCase();
  switch (lower) {
    case 'reply':
    case 'comment':
    case 'chat':
    case 'chat_message':
    case 'dm':
      return 'reply';
    case 'mention':
    case 'follow':
    case 'connection':
    case 'connections':
    case 'user':
      return 'mention';
    case 'claim':
    case 'home_member_request':
    case 'home_claim':
    case 'home_ownership':
      return 'claim';
    case 'gig':
    case 'gig_bid':
    case 'gig_match':
      return 'gig';
    case 'listing':
    case 'listing_sale':
    case 'marketplace':
      return 'listing';
    case 'safety':
    case 'alert':
    case 'security':
    case 'porch_alert':
      return 'safety';
    case 'system':
    case 'info':
    case 'support_train':
    case 'support-train':
    case 'announcement':
      return 'system';
    default:
      if (!lower) return 'system';
      if (lower.includes('gig')) return 'gig';
      if (lower.includes('listing') || lower.includes('mail')) return 'listing';
      if (lower.includes('home')) return 'claim';
      if (lower.includes('post') || lower.includes('reply')) return 'reply';
      return 'system';
  }
}

function formatRelative(createdAt: string, now: Date): string {
  const date = new Date(createdAt);
  const ms = now.getTime() - date.getTime();
  if (ms < 60_000) return 'now';
  if (ms < 3_600_000) return `${Math.floor(ms / 60_000)}m`;
  if (ms < 86_400_000) return `${Math.floor(ms / 3_600_000)}h`;
  const startOfToday = new Date(now);
  startOfToday.setHours(0, 0, 0, 0);
  const startOfDate = new Date(date);
  startOfDate.setHours(0, 0, 0, 0);
  const days = Math.round((startOfToday.getTime() - startOfDate.getTime()) / 86_400_000);
  if (days === 1) return 'Yesterday';
  if (days < 7) {
    return date.toLocaleDateString('en-US', { weekday: 'short' });
  }
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

function makeRow(
  notif: Notification,
  now: Date,
  onTap: (notif: Notification) => void,
): RowModel {
  const category = categoryFromType(notif.type);
  const style = CATEGORY_STYLES[category];
  const unread = !notif.is_read;
  return {
    id: notif.id,
    title: notif.title || 'Notification',
    template: 'statusChip',
    leading: {
      kind: 'typeIcon',
      icon: style.icon,
      background: style.tileBackground,
      foreground: style.tileForeground,
    },
    trailing: { kind: 'none' },
    onTap: () => onTap(notif),
    body: notif.body ?? null,
    chips: [
      {
        text: style.label,
        icon: style.icon,
        tint: { kind: 'status', variant: style.chipVariant },
      },
    ],
    timeMeta: formatRelative(notif.created_at, now),
    highlight: unread ? 'unread' : undefined,
  };
}

function bucketSections(
  notifications: Notification[],
  now: Date,
  onTap: (notif: Notification) => void,
): RowSection[] {
  const startOfToday = new Date(now);
  startOfToday.setHours(0, 0, 0, 0);
  const todayRows: RowModel[] = [];
  const earlierRows: RowModel[] = [];
  for (const notif of notifications) {
    const created = new Date(notif.created_at);
    const row = makeRow(notif, now, onTap);
    if (created.getTime() >= startOfToday.getTime()) {
      todayRows.push(row);
    } else {
      earlierRows.push(row);
    }
  }
  const sections: RowSection[] = [];
  if (todayRows.length > 0) {
    sections.push({ id: 'today', header: 'Today', rows: todayRows });
  }
  if (earlierRows.length > 0) {
    sections.push({ id: 'earlier', header: 'Earlier', rows: earlierRows });
  }
  return sections;
}

export default function NotificationsPage() {
  const router = useRouter();
  const socket = useSocket();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<Tab>('all');

  useEffect(() => {
    if (!getAuthToken()) router.push('/login');
  }, [router]);

  const notifKey = useMemo(() => [...queryKeys.notifications(), tab] as const, [tab]);

  const notifQuery = useInfiniteQuery<
    NotificationsPage,
    Error,
    InfiniteData<NotificationsPage>,
    typeof notifKey,
    number
  >({
    queryKey: notifKey,
    initialPageParam: 0,
    queryFn: async ({ pageParam }) => {
      const params: Record<string, unknown> = { limit: LIMIT, offset: pageParam };
      if (tab === 'unread') params.unread = true;
      return api.notifications.getNotifications(params);
    },
    getNextPageParam: (lastPage, allPages) => {
      if (!lastPage?.hasMore) return undefined;
      return allPages.reduce((sum, p) => sum + (p.notifications?.length || 0), 0);
    },
    staleTime: 30_000,
  });

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

  const unreadCount = useMemo(() => {
    const pages = notifQuery.data?.pages ?? [];
    // Prefer the server-emitted count from the first page (matches the
    // mobile feeds). Fall back to a client-side count.
    return (
      pages[0]?.unreadCount ??
      notifications.filter((n) => !n.is_read).length
    );
  }, [notifQuery.data, notifications]);

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

  const prependNotification = useCallback(
    (notif: Notification) => {
      queryClient.setQueryData<InfiniteData<NotificationsPage>>(notifKey, (old) => {
        if (!old) return old;
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

  // Real-time socket
  useEffect(() => {
    if (!socket) return;
    const onNew = (notif: Notification) => prependNotification(notif);
    socket.on('notification:new', onNew);
    return () => {
      socket.off('notification:new', onNew);
    };
  }, [socket, prependNotification]);

  const handleNotificationClick = useCallback(
    async (notif: Notification) => {
      if (!notif.is_read) {
        // Optimistic: flip local state first, roll back on failure to
        // match the iOS / Android markRead pattern.
        const previous = queryClient.getQueryData(notifKey);
        mutateNotifications((n) => (n.id === notif.id ? { ...n, is_read: true } : n));
        try {
          await api.notifications.markAsRead(notif.id);
        } catch {
          queryClient.setQueryData(notifKey, previous);
        }
      }
      if (notif.link) {
        const path =
          notif.link.startsWith('/homes/') && !notif.link.startsWith('/app/')
            ? `/app${notif.link}`
            : notif.link;
        router.push(path);
      }
    },
    [router, mutateNotifications, queryClient, notifKey],
  );

  const handleMarkAllRead = useCallback(async () => {
    if (unreadCount === 0) return;
    const previous = queryClient.getQueryData(notifKey);
    mutateNotifications((n) => ({ ...n, is_read: true }));
    try {
      await api.notifications.markAllAsRead();
    } catch {
      queryClient.setQueryData(notifKey, previous);
    }
  }, [mutateNotifications, unreadCount, queryClient, notifKey]);

  const now = useMemo(() => new Date(), [notifications]); // eslint-disable-line react-hooks/exhaustive-deps

  const state = useMemo<ListOfRowsState>(() => {
    if (notifQuery.isPending) return { kind: 'loading' };
    if (notifQuery.isError) {
      return {
        kind: 'error',
        message: notifQuery.error?.message ?? "Couldn't load notifications.",
      };
    }
    if (notifications.length === 0) {
      if (tab === 'unread') {
        return {
          kind: 'empty',
          config: {
            icon: CheckCheck,
            headline: "You're all caught up",
            subcopy:
              'No unread notifications. Replies, mentions, claim updates, and safety alerts from your neighborhood will land here.',
            ctaTitle: 'View all notifications',
            onCta: () => setTab('all'),
          },
        };
      }
      return {
        kind: 'empty',
        config: {
          icon: Bell,
          headline: 'All caught up',
          subcopy: "When something needs your attention, it'll show up here.",
        },
      };
    }
    return {
      kind: 'loaded',
      sections: bucketSections(notifications, now, handleNotificationClick),
      hasMore: notifQuery.hasNextPage ?? false,
    };
  }, [
    notifQuery.isPending,
    notifQuery.isError,
    notifQuery.error,
    notifQuery.hasNextPage,
    notifications,
    now,
    handleNotificationClick,
    tab,
  ]);

  return (
    <ListOfRowsShell
      title="Notifications"
      state={state}
      onRefresh={() => notifQuery.refetch()}
      onLoadMore={() => notifQuery.fetchNextPage()}
      tabs={[
        { id: 'all', label: 'All', count: notifications.length },
        { id: 'unread', label: 'Unread', count: unreadCount },
      ]}
      selectedTab={tab}
      onTabChange={(id) => setTab(id as Tab)}
      topBarAction={{
        icon: CheckCheck,
        label: 'Mark all read',
        accessibilityLabel: 'Mark all read',
        isEnabled: unreadCount > 0,
        onClick: handleMarkAllRead,
      }}
    />
  );
}
