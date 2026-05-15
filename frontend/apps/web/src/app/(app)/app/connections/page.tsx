'use client';

import { Suspense, useMemo, useState, type ReactNode } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useQueries, useQueryClient } from '@tanstack/react-query';
import * as api from '@pantopus/api';
import {
  Check,
  MapPin,
  MessageCircle,
  Search,
  UserPlus,
  Users,
} from 'lucide-react';
import Image from 'next/image';
import { queryKeys } from '@/lib/query-keys';
import type { Relationship, ConnectionRequest, RelationshipUser } from '@pantopus/types';

type Tab = 'all' | 'neighbors' | 'pending';

type AcceptedRow = {
  kind: 'accepted';
  id: string;
  user: RelationshipUser | null | undefined;
  acceptedAt: string;
};

type PendingRow = {
  kind: 'pending';
  id: string;
  user: RelationshipUser | null | undefined;
  createdAt: string;
};

function ConnectionsPageContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const initialTab = mapInitialTab(searchParams.get('tab'));

  const [activeTab, setActiveTab] = useState<Tab>(initialTab);
  const [searchText, setSearchText] = useState('');
  // Optimistic mutation bookkeeping. When set, the affected pending id is
  // hidden from the list (treated as accepted/declined) until the server
  // confirms — and rolled back if the call fails.
  const [optimisticallyHandled, setOptimisticallyHandled] = useState<Record<string, 'accept' | 'reject'>>({});
  const queryClient = useQueryClient();

  const [connectionsQ, pendingQ] = useQueries({
    queries: [
      {
        queryKey: queryKeys.connections(),
        queryFn: async (): Promise<Relationship[]> => {
          const res = await api.relationships.getConnections();
          return (res.relationships || []).filter((r) => r.status === 'accepted');
        },
        staleTime: 30_000,
      },
      {
        queryKey: queryKeys.connectionRequests('pending'),
        queryFn: async (): Promise<ConnectionRequest[]> => {
          const res = await api.relationships.getPendingRequests();
          return res.requests || [];
        },
        staleTime: 30_000,
      },
    ],
  });

  const accepted: Relationship[] = connectionsQ.data ?? [];
  const pending: ConnectionRequest[] = pendingQ.data ?? [];
  const loading = connectionsQ.isPending || pendingQ.isPending;
  const errored = connectionsQ.isError && pendingQ.isError;

  const acceptedRows: AcceptedRow[] = useMemo(
    () =>
      accepted.map((rel) => ({
        kind: 'accepted',
        id: rel.id,
        user: rel.other_user,
        acceptedAt: rel.accepted_at || rel.created_at,
      })),
    [accepted],
  );

  const pendingRows: PendingRow[] = useMemo(
    () =>
      pending
        .filter((req) => optimisticallyHandled[req.id] === undefined)
        .map((req) => ({
          kind: 'pending',
          id: req.id,
          user: req.requester,
          createdAt: req.created_at,
        })),
    [pending, optimisticallyHandled],
  );

  const filteredAccepted = useMemo(
    () => filterBySearch(acceptedRows, searchText),
    [acceptedRows, searchText],
  );
  const filteredNeighbors = useMemo(
    () => filterBySearch(acceptedRows.filter((r) => Boolean(r.user?.city)), searchText),
    [acceptedRows, searchText],
  );
  const filteredPending = useMemo(
    () => filterBySearch(pendingRows, searchText),
    [pendingRows, searchText],
  );

  const allCount = filteredAccepted.length;
  const neighborsCount = filteredNeighbors.length;
  const pendingCount = filteredPending.length;

  const refetchAll = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.connections() }),
      queryClient.invalidateQueries({ queryKey: queryKeys.connectionRequests('pending') }),
    ]);
  };

  const handleAccept = async (id: string) => {
    setOptimisticallyHandled((prev) => ({ ...prev, [id]: 'accept' }));
    try {
      await api.relationships.acceptRequest(id);
      await refetchAll();
    } catch (err) {
      console.error('Failed to accept connection:', err);
      setOptimisticallyHandled((prev) => {
        const next = { ...prev };
        delete next[id];
        return next;
      });
    }
  };

  const handleReject = async (id: string) => {
    setOptimisticallyHandled((prev) => ({ ...prev, [id]: 'reject' }));
    try {
      await api.relationships.rejectRequest(id);
      await refetchAll();
    } catch (err) {
      console.error('Failed to reject connection:', err);
      setOptimisticallyHandled((prev) => {
        const next = { ...prev };
        delete next[id];
        return next;
      });
    }
  };

  const handleOpenChat = (user: RelationshipUser | null | undefined) => {
    if (!user) return;
    router.push(`/${user.username ?? user.id}`);
  };

  const tabs: { key: Tab; label: string; count: number }[] = [
    { key: 'all', label: 'All', count: allCount },
    { key: 'neighbors', label: 'Neighbors', count: neighborsCount },
    { key: 'pending', label: 'Pending', count: pendingCount },
  ];

  const visibleRows: (AcceptedRow | PendingRow)[] =
    activeTab === 'pending'
      ? filteredPending
      : activeTab === 'neighbors'
      ? filteredNeighbors
      : filteredAccepted;

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 lg:px-8 py-6" data-testid="connections-page">
      {/* Title row mirrors the mobile top bar geometry — back chevron is
          handled by the app shell on mobile; on web the route renders
          inside the app frame, so we render the title + trailing
          "Find people" affordance in-place. */}
      <div className="flex items-center justify-between mb-5">
        <h1 className="text-xl font-semibold text-app-text tracking-tight">Connections</h1>
        <button
          onClick={() => router.push('/app/discover-hub')}
          className="inline-flex items-center gap-2 text-sm font-semibold text-primary-600 hover:text-primary-700"
          aria-label="Find people"
        >
          <UserPlus className="w-4 h-4" />
          Find people
        </button>
      </div>

      {/* Search bar */}
      <div className="mb-4">
        <div className="flex items-center gap-2 bg-app-surface-sunken rounded-xl px-3 py-2.5 border border-transparent focus-within:border-app-border">
          <Search className="w-4 h-4 text-app-text-secondary" aria-hidden="true" />
          <input
            type="search"
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            placeholder="Search by name or neighborhood"
            className="flex-1 bg-transparent text-sm text-app-text placeholder:text-app-text-muted focus:outline-none"
            aria-label="Search connections"
          />
        </div>
      </div>

      {/* 3-tab strip — equal-width, underline indicator. */}
      <div className="flex border-b border-app-border mb-4" role="tablist">
        {tabs.map((tab) => {
          const active = activeTab === tab.key;
          return (
            <button
              key={tab.key}
              role="tab"
              aria-selected={active}
              data-testid={`connections-tab-${tab.key}`}
              onClick={() => setActiveTab(tab.key)}
              className={`flex-1 py-3 text-sm font-medium border-b-2 transition ${
                active
                  ? 'border-primary-600 text-primary-600'
                  : 'border-transparent text-app-text-secondary hover:text-app-text'
              }`}
            >
              {tab.label} ({tab.count})
            </button>
          );
        })}
      </div>

      {loading ? (
        <LoadingRows />
      ) : errored ? (
        <ErrorBanner onRetry={() => refetchAll()} />
      ) : visibleRows.length === 0 ? (
        <EmptyForTab tab={activeTab} onFindPeople={() => router.push('/app/discover-hub')} />
      ) : (
        <div className="space-y-2.5">
          {visibleRows.map((row) =>
            row.kind === 'pending' ? (
              <PendingPersonRow
                key={row.id}
                row={row}
                onAccept={() => handleAccept(row.id)}
                onReject={() => handleReject(row.id)}
              />
            ) : (
              <AcceptedPersonRow
                key={row.id}
                row={row}
                onOpenChat={() => handleOpenChat(row.user)}
                onOpenProfile={() => handleOpenChat(row.user)}
              />
            ),
          )}
        </div>
      )}
    </div>
  );
}

function mapInitialTab(raw: string | null): Tab {
  switch (raw) {
    case 'pending':
    case 'requests':
      return 'pending';
    case 'neighbors':
      return 'neighbors';
    default:
      return 'all';
  }
}

function filterBySearch<T extends { user: RelationshipUser | null | undefined }>(
  rows: T[],
  text: string,
): T[] {
  const needle = text.trim().toLowerCase();
  if (!needle) return rows;
  return rows.filter((row) => {
    const u = row.user;
    if (!u) return false;
    const haystack = [
      u.name,
      u.first_name,
      u.last_name,
      u.username,
      u.city,
      u.state,
    ]
      .filter(Boolean)
      .join(' ')
      .toLowerCase();
    return haystack.includes(needle);
  });
}

function displayName(u: RelationshipUser | null | undefined): string {
  if (!u) return 'Member';
  return (
    u.name ||
    (u.first_name && u.last_name ? `${u.first_name} ${u.last_name}` : null) ||
    u.username ||
    'Member'
  );
}

function localityText(u: RelationshipUser | null | undefined): string | null {
  if (!u) return null;
  const city = (u.city || '').trim();
  const state = (u.state || '').trim();
  if (city && state) return `${city}, ${state}`;
  if (city) return city;
  if (state) return state;
  return null;
}

function relativeTime(raw: string): string {
  if (!raw) return 'recently';
  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) return 'recently';
  const diffMs = Date.now() - date.getTime();
  const sec = Math.max(0, Math.floor(diffMs / 1000));
  if (sec < 60) return 'just now';
  if (sec < 3600) return `${Math.floor(sec / 60)}m ago`;
  if (sec < 86400) return `${Math.floor(sec / 3600)}h ago`;
  const days = Math.floor(sec / 86400);
  if (days === 1) return 'yesterday';
  if (days < 7) return `${days}d ago`;
  if (days < 30) return `${Math.floor(days / 7)}w ago`;
  return date.toLocaleDateString();
}

// ─── Sub-components ──────────────────────────────────────────────

function Avatar({
  user,
  verified,
}: {
  user: RelationshipUser | null | undefined;
  verified: boolean;
}) {
  const name = displayName(user);
  const initials = name
    .split(' ')
    .slice(0, 2)
    .map((p) => p.charAt(0))
    .join('')
    .toUpperCase();
  return (
    <div className="relative flex-shrink-0">
      {user?.profile_picture_url ? (
        <Image
          src={user.profile_picture_url}
          alt={name}
          width={44}
          height={44}
          sizes="44px"
          quality={75}
          className="w-11 h-11 rounded-full object-cover"
        />
      ) : (
        <div className="w-11 h-11 rounded-full bg-gradient-to-br from-primary-500 to-primary-700 flex items-center justify-center text-white text-sm font-bold">
          {initials || '?'}
        </div>
      )}
      {verified && (
        <div className="absolute -right-0.5 -bottom-0.5 w-4 h-4 rounded-full bg-green-600 border-2 border-app-surface flex items-center justify-center">
          <Check className="w-2.5 h-2.5 text-white" strokeWidth={4} aria-hidden="true" />
        </div>
      )}
    </div>
  );
}

function AcceptedPersonRow({
  row,
  onOpenChat,
  onOpenProfile,
}: {
  row: AcceptedRow;
  onOpenChat: () => void;
  onOpenProfile: () => void;
}) {
  const user = row.user;
  const name = displayName(user);
  const locality = localityText(user);
  const interaction = `Connected ${relativeTime(row.acceptedAt)}`;
  return (
    <div
      className="flex items-center gap-3 bg-app-surface border border-app-border rounded-2xl p-3 shadow-sm"
      data-testid="connections-row-accepted"
    >
      <button onClick={onOpenProfile} className="flex-shrink-0" aria-label={`Open ${name}'s profile`}>
        <Avatar user={user} verified />
      </button>
      <div className="flex-1 min-w-0">
        <button
          onClick={onOpenProfile}
          className="block text-left w-full"
          aria-label={`Open ${name}'s profile`}
        >
          <p className="text-sm font-semibold text-app-text truncate">{name}</p>
          {locality && (
            <p className="mt-0.5 text-xs text-app-text-secondary truncate flex items-center gap-1">
              <MapPin className="w-3 h-3" aria-hidden="true" />
              {locality}
            </p>
          )}
          <p className="mt-0.5 text-xs text-app-text-muted truncate flex items-center gap-1">
            <UserPlus className="w-2.5 h-2.5" aria-hidden="true" />
            {interaction}
          </p>
        </button>
      </div>
      <button
        onClick={onOpenChat}
        aria-label={`Message ${name}`}
        className="flex-shrink-0 w-9 h-9 rounded-full bg-primary-50 text-primary-600 hover:bg-primary-100 flex items-center justify-center"
      >
        <MessageCircle className="w-4 h-4" />
      </button>
    </div>
  );
}

function PendingPersonRow({
  row,
  onAccept,
  onReject,
}: {
  row: PendingRow;
  onAccept: () => void;
  onReject: () => void;
}) {
  const user = row.user;
  const name = displayName(user);
  const locality = localityText(user);
  const interaction = `New request ${relativeTime(row.createdAt)}`;
  return (
    <div
      className="flex items-center gap-3 bg-app-surface border border-app-border rounded-2xl p-3 shadow-sm"
      data-testid="connections-row-pending"
    >
      <Avatar user={user} verified={false} />
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold text-app-text truncate">{name}</p>
        {locality && (
          <p className="mt-0.5 text-xs text-app-text-secondary truncate flex items-center gap-1">
            <MapPin className="w-3 h-3" aria-hidden="true" />
            {locality}
          </p>
        )}
        <p className="mt-0.5 text-xs text-app-text-muted truncate flex items-center gap-1">
          <UserPlus className="w-2.5 h-2.5" aria-hidden="true" />
          {interaction}
        </p>
      </div>
      <div className="flex flex-col gap-1.5 flex-shrink-0">
        <button
          onClick={onAccept}
          className="h-8 px-3 rounded-lg bg-primary-600 text-white text-xs font-semibold hover:bg-primary-700"
        >
          Accept
        </button>
        <button
          onClick={onReject}
          className="h-7 px-3 rounded-lg border border-app-border text-app-text-secondary text-xs font-medium hover:bg-app-hover"
        >
          Ignore
        </button>
      </div>
    </div>
  );
}

function LoadingRows() {
  return (
    <div className="space-y-2.5">
      {Array.from({ length: 4 }).map((_, i) => (
        <div
          key={i}
          className="flex items-center gap-3 bg-app-surface border border-app-border rounded-2xl p-3 animate-pulse"
        >
          <div className="w-11 h-11 rounded-full bg-app-surface-sunken" />
          <div className="flex-1 space-y-1.5">
            <div className="h-3.5 w-1/2 bg-app-surface-sunken rounded" />
            <div className="h-3 w-1/3 bg-app-surface-sunken rounded" />
          </div>
          <div className="w-9 h-9 rounded-full bg-app-surface-sunken" />
        </div>
      ))}
    </div>
  );
}

function ErrorBanner({ onRetry }: { onRetry: () => void }) {
  return (
    <div className="text-center py-12 bg-app-surface rounded-xl border border-app-border">
      <p className="text-app-text font-semibold mb-1">Couldn't load your connections</p>
      <p className="text-sm text-app-text-secondary mb-4">Check your connection and try again.</p>
      <button
        onClick={onRetry}
        className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-primary-600 text-white text-sm font-semibold hover:bg-primary-700"
      >
        Try again
      </button>
    </div>
  );
}

function EmptyForTab({
  tab,
  onFindPeople,
}: {
  tab: Tab;
  onFindPeople: () => void;
}) {
  switch (tab) {
    case 'pending':
      return (
        <EmptyState
          icon={<Users className="w-8 h-8 text-primary-600" />}
          headline="No pending requests"
          subcopy="When someone sends you a connection request, it'll show up here."
        />
      );
    case 'neighbors':
      return (
        <EmptyState
          icon={<MapPin className="w-8 h-8 text-primary-600" />}
          headline="No neighbors yet"
          subcopy="Connections who share their locality show up here. Invite a neighbor or accept a nearby request to get started."
        />
      );
    default:
      return (
        <EmptyState
          icon={<Users className="w-8 h-8 text-primary-600" />}
          headline="No connections yet"
          subcopy="Meet verified neighbors. Browse the Pulse, reply to a post, or invite someone you know on the block."
          ctaLabel="Find people"
          onCta={onFindPeople}
        />
      );
  }
}

function EmptyState({
  icon,
  headline,
  subcopy,
  ctaLabel,
  onCta,
}: {
  icon: ReactNode;
  headline: string;
  subcopy: string;
  ctaLabel?: string;
  onCta?: () => void;
}) {
  return (
    <div className="flex flex-col items-center justify-center text-center py-16 bg-app-surface border border-app-border rounded-2xl">
      <div className="w-16 h-16 rounded-full bg-primary-50 flex items-center justify-center mb-4">
        {icon}
      </div>
      <p className="text-base font-semibold text-app-text mb-1">{headline}</p>
      <p className="text-sm text-app-text-secondary max-w-sm mb-4">{subcopy}</p>
      {ctaLabel && onCta && (
        <button
          onClick={onCta}
          className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-primary-600 text-white text-sm font-semibold hover:bg-primary-700"
        >
          <UserPlus className="w-4 h-4" />
          {ctaLabel}
        </button>
      )}
    </div>
  );
}

export default function ConnectionsPage() {
  return (
    <Suspense>
      <ConnectionsPageContent />
    </Suspense>
  );
}
