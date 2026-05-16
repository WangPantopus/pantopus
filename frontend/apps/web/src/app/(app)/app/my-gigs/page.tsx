// @ts-nocheck
'use client';

// T5.3.2 — My tasks V2 (canonical web). Reskin of the legacy `/app/my-gigs`
// route with the 4-tab design from mytasks-frames.jsx:
//   Open   (reviewing bids / urgent / no bids yet)
//   Active (in progress / scheduled)
//   Done   (completed / awaiting review)
//   Closed (cancelled / expired)
// Mirrors the iOS / Android MyTasksViewModel mappings. See
// docs/t5-buildout-plan.md "My tasks V2 implementation notes" for the
// canonical bucket → status table.
//
// The legacy `/app/my-gigs-v2` staging route is removed in this PR — every
// caller already targets `/app/my-gigs`. No redirect needed.

import { useState, useEffect, useMemo } from 'react';
import { useRouter } from 'next/navigation';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import * as api from '@pantopus/api';
import { getAuthToken } from '@pantopus/api';
import { queryKeys } from '@/lib/query-keys';
import {
  Inbox, Timer, Play, Calendar, Star, CheckCheck, X, Ban,
  CircleSlash, Pencil, Rocket, MessageCircle, ClipboardList,
  RotateCcw, Plus, Sparkles, Tv, Package as PackageIcon,
  Hammer, Dog, Laptop, Repeat, MapPin, Monitor, Shuffle,
  ArrowUpRight,
} from 'lucide-react';
import PageHeader from '@/components/PageHeader';
import EmptyState from '@/components/ui/EmptyState';
import ErrorState from '@/components/ui/ErrorState';
import LoadingSkeleton from '@/components/ui/LoadingSkeleton';
import { toast } from '@/components/ui/toast-store';

// ─── Types ────────────────────────────────────────────────────────────

type TopBidder = { id: string; initials: string; color: string };

type MyGig = {
  id: string;
  title: string;
  description?: string;
  price?: number | null;
  category?: string;
  status?: string;
  created_at?: string;
  updated_at?: string;
  deadline?: string;
  user_id?: string;
  accepted_by?: string;
  accepted_at?: string;
  scheduled_start?: string;
  pay_type?: string;
  bid_count?: number;
  top_bid_amount?: number | null;
  top_bidders?: TopBidder[];
  boosted_at?: string | null;
  boost_expires_at?: string | null;
  // T6.0b — Magic Task archetype + engagement-mode badge.
  source_flow?: string | null;
  task_archetype?: string | null;
  task_format?: 'in_person' | 'drop_off' | 'remote' | 'hybrid' | null;
};

// T6.0b — Magic Task archetype taxonomy mirrors iOS/Android.
type ArchetypeKey =
  | 'quick_help' | 'delivery_errand' | 'home_service' | 'pro_service_quote'
  | 'care_task' | 'event_shift' | 'remote_task' | 'recurring_service' | 'general';

const ARCHETYPE_META: Record<ArchetypeKey, { label: string; icon: typeof Sparkles; gradient: string }> = {
  quick_help:        { label: 'Quick help',        icon: Sparkles,    gradient: 'from-sky-400 to-sky-700' },
  delivery_errand:   { label: 'Delivery',          icon: PackageIcon, gradient: 'from-violet-400 to-violet-700' },
  home_service:      { label: 'Mount & install',   icon: Tv,          gradient: 'from-sky-400 to-blue-700' },
  pro_service_quote: { label: 'Pro service',       icon: Hammer,      gradient: 'from-amber-500 to-amber-700' },
  care_task:         { label: 'Pet care',          icon: Dog,         gradient: 'from-emerald-400 to-emerald-700' },
  event_shift:       { label: 'Event help',        icon: Calendar,    gradient: 'from-rose-400 to-rose-700' },
  remote_task:       { label: 'Tech support',      icon: Laptop,      gradient: 'from-cyan-400 to-cyan-700' },
  recurring_service: { label: 'Recurring',         icon: Repeat,      gradient: 'from-sky-600 to-sky-800' },
  general:           { label: 'Magic task',        icon: ClipboardList, gradient: 'from-violet-300 to-violet-700' },
};

function archetypeOf(raw: string | null | undefined): ArchetypeKey {
  if (!raw) return 'general';
  const k = raw.toLowerCase() as ArchetypeKey;
  return (k in ARCHETYPE_META) ? k : 'general';
}

const FORMAT_META: Record<NonNullable<MyGig['task_format']>, { label: string; icon: typeof MapPin }> = {
  in_person: { label: 'In person', icon: MapPin },
  drop_off:  { label: 'Drop-off',  icon: PackageIcon },
  remote:    { label: 'Remote',    icon: Monitor },
  hybrid:    { label: 'Hybrid',    icon: Shuffle },
};

function isMagicTask(gig: MyGig): boolean {
  return (gig.source_flow || '').toLowerCase() === 'magic';
}

function truncateOverline(s: string): string {
  return s.length > 24 ? s.slice(0, 24) + '…' : s;
}

type MyTasksTab = 'open' | 'active' | 'done' | 'closed';

type ChipVariant = 'info' | 'error' | 'neutral' | 'success';

type MyTasksStatus =
  | { kind: 'reviewing' }
  | { kind: 'urgent'; hoursLeft: number }
  | { kind: 'noBids' }
  | { kind: 'inProgress' }
  | { kind: 'scheduled'; weekday: string }
  | { kind: 'awaitReview' }
  | { kind: 'completed' }
  | { kind: 'cancelled' }
  | { kind: 'expired' };

const URGENT_WINDOW_SECONDS = 4 * 60 * 60;

// ─── Pure projections (mirror iOS/Android MyTasksViewModel) ────────────

function tabFor(status: MyTasksStatus): MyTasksTab {
  switch (status.kind) {
    case 'reviewing': case 'urgent': case 'noBids':
      return 'open';
    case 'inProgress': case 'scheduled':
      return 'active';
    case 'completed': case 'awaitReview':
      return 'done';
    case 'cancelled': case 'expired':
      return 'closed';
  }
}

function derivedStatus(gig: MyGig, now: number): MyTasksStatus {
  const s = (gig.status ?? '').toLowerCase();
  if (s === 'cancelled') return { kind: 'cancelled' };
  if (s === 'completed') return { kind: 'awaitReview' };
  if (s === 'in_progress') return { kind: 'inProgress' };
  if (s === 'assigned') {
    const scheduled = parseTimestamp(gig.scheduled_start);
    if (scheduled && scheduled > now) {
      return { kind: 'scheduled', weekday: formatWeekday(scheduled) };
    }
    return { kind: 'inProgress' };
  }
  // open or unknown
  const deadline = parseTimestamp(gig.deadline);
  if (deadline) {
    if (deadline <= now) return { kind: 'expired' };
    const secondsLeft = (deadline - now) / 1000;
    if (secondsLeft < URGENT_WINDOW_SECONDS) {
      const hoursLeft = Math.max(1, Math.ceil(secondsLeft / 3600));
      return { kind: 'urgent', hoursLeft };
    }
  }
  return (gig.bid_count ?? 0) === 0 ? { kind: 'noBids' } : { kind: 'reviewing' };
}

function statusLabel(status: MyTasksStatus): string {
  switch (status.kind) {
    case 'reviewing': return 'Reviewing bids';
    case 'urgent': return `Closes in ${status.hoursLeft}h`;
    case 'noBids': return 'No bids yet';
    case 'inProgress': return 'In progress';
    case 'scheduled': return `Starts ${status.weekday}`;
    case 'awaitReview': return 'Leave a review';
    case 'completed': return 'Completed';
    case 'cancelled': return 'Cancelled';
    case 'expired': return 'Expired';
  }
}

function statusVariant(status: MyTasksStatus): ChipVariant {
  switch (status.kind) {
    case 'reviewing': case 'scheduled': case 'awaitReview': return 'info';
    case 'urgent': return 'error';
    case 'noBids': case 'cancelled': case 'expired': return 'neutral';
    case 'inProgress': case 'completed': return 'success';
  }
}

function statusIcon(status: MyTasksStatus) {
  switch (status.kind) {
    case 'reviewing': return Inbox;
    case 'urgent': return Timer;
    case 'noBids': return CircleSlash;
    case 'inProgress': return Play;
    case 'scheduled': return Calendar;
    case 'awaitReview': return Star;
    case 'completed': return CheckCheck;
    case 'cancelled': return X;
    case 'expired': return Ban;
  }
}

function parseTimestamp(raw: string | null | undefined): number | null {
  if (!raw) return null;
  const t = Date.parse(raw);
  return Number.isFinite(t) ? t : null;
}

function formatWeekday(timestamp: number): string {
  return new Date(timestamp).toLocaleDateString('en-US', { weekday: 'short' });
}

function formatRelativeTime(raw: string | null | undefined, now: number): string | null {
  const t = parseTimestamp(raw);
  if (t == null) return null;
  const seconds = Math.max(0, (now - t) / 1000);
  if (seconds < 60) return 'just now';
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  if (seconds < 86_400) return `${Math.floor(seconds / 3600)}h ago`;
  const days = Math.floor(seconds / 86_400);
  if (days === 1) return '1d ago';
  if (days < 7) return `${days}d ago`;
  if (days < 30) return `${Math.floor(days / 7)}w ago`;
  return `${Math.floor(days / 30)}mo ago`;
}

function formatAmount(value: number): string {
  return `${Math.round(value)}`;
}

function formatBudget(price: number | null | undefined, payType: string | null | undefined): string {
  if (price == null || price <= 0) return '—';
  const isHourly = (payType ?? '').toLowerCase() === 'hourly';
  return isHourly ? `$${formatAmount(price)}/hr` : `$${formatAmount(price)}`;
}

function formatBidRange(top: number | null | undefined, ask: number | null | undefined): string | null {
  if (top == null || top <= 0) return null;
  if (ask != null && ask > 0 && Math.abs(top - ask) > 0.01) {
    const lo = Math.min(top, ask);
    const hi = Math.max(top, ask);
    return `$${formatAmount(lo)} – $${formatAmount(hi)}`;
  }
  return `$${formatAmount(top)}`;
}

// ─── Page ──────────────────────────────────────────────────────────────

const TABS: { id: MyTasksTab; label: string }[] = [
  { id: 'open', label: 'Open' },
  { id: 'active', label: 'Active' },
  { id: 'done', label: 'Done' },
  { id: 'closed', label: 'Closed' },
];

export default function MyTasksV2Page() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState<MyTasksTab>('open');

  useEffect(() => {
    if (!getAuthToken()) router.push('/login');
  }, [router]);

  const gigsQuery = useQuery<MyGig[]>({
    queryKey: queryKeys.myGigs(),
    queryFn: async () => {
      const response = await api.gigs.getMyGigs({ limit: 100 });
      const resObj = response as Record<string, unknown>;
      return ((resObj?.gigs || resObj?.data || []) as MyGig[]);
    },
    staleTime: 30_000,
  });

  const boostMutation = useMutation({
    mutationFn: (gigId: string) => api.gigs.boostGig(gigId),
    onSuccess: () => {
      toast.success('Task boosted in the feed');
      queryClient.invalidateQueries({ queryKey: queryKeys.myGigs() });
    },
    onError: () => toast.error('Failed to boost task'),
  });

  const completeMutation = useMutation({
    mutationFn: (gigId: string) => api.gigs.completeGig(gigId),
    onSuccess: () => {
      toast.success('Task marked complete');
      queryClient.invalidateQueries({ queryKey: queryKeys.myGigs() });
    },
    onError: () => toast.error('Failed to mark complete'),
  });

  const gigs = gigsQuery.data ?? [];
  const loading = gigsQuery.isPending;
  const fetchError = gigsQuery.error ? 'Failed to load your tasks. Please try again.' : null;

  const now = Date.now();

  const projections = useMemo(
    () =>
      gigs.map((g) => {
        const status = derivedStatus(g, now);
        return { gig: g, status, tab: tabFor(status) };
      }),
    [gigs, now],
  );

  const counts = useMemo(() => {
    const c = { open: 0, active: 0, done: 0, closed: 0 };
    for (const p of projections) c[p.tab]++;
    return c;
  }, [projections]);

  const filtered = projections.filter((p) => p.tab === activeTab);

  const banner = useMemo(() => {
    if (activeTab !== 'open' || counts.open === 0) return null;
    const yesterday = now - 24 * 3600 * 1000;
    let newBidsToday = 0;
    let closingSoon = 0;
    for (const p of projections) {
      if (p.tab !== 'open') continue;
      const updated = parseTimestamp(p.gig.updated_at);
      if (updated != null && updated > yesterday) newBidsToday += p.gig.bid_count ?? 0;
      const deadline = parseTimestamp(p.gig.deadline);
      if (deadline != null) {
        const secondsLeft = (deadline - now) / 1000;
        if (secondsLeft > 0 && secondsLeft < 24 * 3600) closingSoon++;
      }
    }
    const title =
      newBidsToday > 0
        ? `${newBidsToday} new ${newBidsToday === 1 ? 'bid' : 'bids'} since yesterday`
        : `${counts.open} open ${counts.open === 1 ? 'task' : 'tasks'}`;
    const subtitle = closingSoon > 0 ? `${closingSoon} closing in the next 24h` : null;
    return { title, subtitle };
  }, [projections, activeTab, counts.open, now]);

  return (
    <div className="min-h-[calc(100vh-64px)] relative">
      <main className="max-w-3xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
        <PageHeader title="My tasks" subtitle={`${gigs.length} total · ${counts.open} open`} />

        <div className="border-b border-app-border mb-6 mt-2 flex gap-1" data-testid="my-tasks-tabs">
          {TABS.map((tab) => {
            const active = tab.id === activeTab;
            const count = counts[tab.id];
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={`flex-1 px-3 py-3 text-sm font-medium border-b-2 transition flex items-center justify-center gap-2 ${
                  active
                    ? 'border-primary-600 text-primary-600'
                    : 'border-transparent text-app-text-secondary hover:text-app-text'
                }`}
                data-testid={`my-tasks-tab-${tab.id}`}
              >
                {tab.label}
                <span
                  className={`px-2 py-0.5 rounded-full text-xs font-semibold ${
                    active ? 'bg-primary-50 text-primary-700' : 'bg-app-surface-sunken text-app-text-secondary'
                  }`}
                >
                  {count}
                </span>
              </button>
            );
          })}
        </div>

        {fetchError && <ErrorState message={fetchError} onRetry={() => gigsQuery.refetch()} />}

        {banner && (
          <div className="flex items-center gap-3 p-3 mb-4 bg-primary-50 border border-primary-100 rounded-xl">
            <div className="w-8 h-8 rounded-lg bg-white border border-primary-100 text-primary-600 flex items-center justify-center flex-shrink-0">
              <Inbox className="w-4 h-4" />
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-semibold text-app-text">{banner.title}</p>
              {banner.subtitle && (
                <p className="text-xs text-app-text-secondary mt-0.5">
                  <span className="text-red-700 font-semibold">{banner.subtitle}</span>
                </p>
              )}
            </div>
          </div>
        )}

        {loading ? (
          <LoadingSkeleton variant="gig-card" count={3} />
        ) : filtered.length === 0 ? (
          <EmptyTabContent tab={activeTab} onPostTask={() => router.push('/app/gigs/new')} />
        ) : (
          <div className="space-y-3" data-testid="my-tasks-list">
            {filtered.map(({ gig, status }) => (
              <TaskRow
                key={gig.id}
                gig={gig}
                status={status}
                now={now}
                onOpen={() => router.push(`/app/gigs/${gig.id}`)}
                onReviewBids={() => router.push(`/app/gigs/${gig.id}#bids`)}
                onEdit={() => router.push(`/app/gigs/${gig.id}?action=edit`)}
                onBoost={() => boostMutation.mutate(gig.id)}
                onMessage={() => router.push(`/app/gigs/${gig.id}#chat`)}
                onMarkComplete={() => completeMutation.mutate(gig.id)}
                onLeaveReview={() => router.push(`/app/gigs/${gig.id}?action=review`)}
                onRepost={() => router.push('/app/gigs/new')}
              />
            ))}
          </div>
        )}
      </main>

      {/* T6.0b — 60px Magic Task FAB. Gradient primary600 → primary700
          with a sparkles disc clipped over the top-right corner. */}
      <button
        onClick={() => router.push('/app/gigs/new')}
        className="fixed bottom-6 right-6 w-[60px] h-[60px] rounded-full bg-gradient-to-br from-primary-600 to-primary-700 text-white shadow-lg shadow-primary-600/40 hover:from-primary-700 hover:to-primary-700 transition flex items-center justify-center relative"
        aria-label="Post a task with Magic Task"
        data-testid="post-a-task-fab"
      >
        <Plus className="w-[22px] h-[22px]" strokeWidth={2.4} />
        <span
          className="absolute top-2 right-2 w-[18px] h-[18px] rounded-full bg-white flex items-center justify-center"
          style={{ color: 'var(--color-identity-magic)' }}
          aria-hidden="true"
          data-testid="magic-fab-sparkles"
        >
          <Sparkles className="w-[11px] h-[11px]" strokeWidth={2.6} />
        </span>
      </button>
    </div>
  );
}

// ─── Task row ───────────────────────────────────────────────────────────

function TaskRow({
  gig,
  status,
  now,
  onOpen,
  onReviewBids,
  onEdit,
  onBoost,
  onMessage,
  onMarkComplete,
  onLeaveReview,
  onRepost,
}: {
  gig: MyGig;
  status: MyTasksStatus;
  now: number;
  onOpen: () => void;
  onReviewBids: () => void;
  onEdit: () => void;
  onBoost: () => void;
  onMessage: () => void;
  onMarkComplete: () => void;
  onLeaveReview: () => void;
  onRepost: () => void;
}) {
  const StatusIcon = statusIcon(status);
  const variant = statusVariant(status);
  const muted = status.kind === 'cancelled' || status.kind === 'expired';
  const bidCount = gig.bid_count ?? 0;
  const budget = formatBudget(gig.price, gig.pay_type);
  const subtitle = subtitleFor(gig, status, now);
  const topBidders = gig.top_bidders ?? [];
  const overflow = Math.max(0, bidCount - topBidders.length);

  // T6.0b — Magic Task chrome.
  const isMagic = isMagicTask(gig);
  const archetypeKey = archetypeOf(gig.task_archetype);
  const archetypeMeta = ARCHETYPE_META[archetypeKey];
  const ArchetypeIcon = archetypeMeta.icon;
  const overline = isMagic ? truncateOverline(archetypeMeta.label) : null;
  const format = gig.task_format ? FORMAT_META[gig.task_format] : null;
  const FormatIcon = format?.icon;

  return (
    <div
      className={`bg-app-surface border border-app-border rounded-2xl p-4 shadow-sm hover:shadow-md transition ${
        muted ? 'opacity-75' : ''
      }`}
      data-testid={`task-row-${gig.id}`}
    >
      <button onClick={onOpen} className="w-full text-left">
        <div className="flex items-start gap-3">
          {isMagic ? (
            // 44px Magic Task archetype tile with sparkles disc.
            <div className="relative w-11 h-11 flex-shrink-0" data-testid="magic-archetype-tile">
              <div className={`w-11 h-11 rounded-[11px] bg-gradient-to-br ${archetypeMeta.gradient} text-white flex items-center justify-center`}>
                <ArchetypeIcon className="w-[22px] h-[22px]" strokeWidth={1.7} />
              </div>
              <div
                className="absolute -top-1 -right-1 w-[18px] h-[18px] rounded-full bg-white flex items-center justify-center shadow-sm"
                style={{ border: '1.5px solid var(--color-identity-magic-border)', color: 'var(--color-identity-magic)' }}
                aria-hidden="true"
              >
                <Sparkles className="w-[10px] h-[10px]" strokeWidth={2.4} />
              </div>
            </div>
          ) : (
            <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-primary-400 to-primary-600 text-white flex items-center justify-center flex-shrink-0">
              <ClipboardList className="w-5 h-5" />
            </div>
          )}
          <div className="flex-1 min-w-0">
            {overline && (
              <div
                className="text-[9.5px] font-bold uppercase mb-0.5"
                style={{ color: 'var(--color-identity-magic)', letterSpacing: '0.06em' }}
                data-testid="row-archetype-overline"
              >
                {overline}
              </div>
            )}
            <div className="flex items-start gap-2 mb-1">
              <h3 className="flex-1 text-sm font-semibold text-app-text line-clamp-2 leading-snug">
                {gig.title || 'Untitled task'}
              </h3>
              <span className="flex-shrink-0 text-base font-bold text-app-text">{budget}</span>
            </div>
            {subtitle && (
              <p className="text-xs text-app-text-secondary mb-2 truncate">{subtitle}</p>
            )}
            <div className="flex items-center gap-2 flex-wrap">
              {topBidders.length > 0 && (
                <BidderStack bidders={topBidders} overflow={overflow} />
              )}
              <StatusChip icon={StatusIcon} label={statusLabel(status)} variant={variant} />
              {format && FormatIcon && (
                <span
                  className="inline-flex items-center gap-1 px-[7px] py-[3px] rounded-md text-[10px] font-semibold bg-app-surface border border-app-border text-app-text-strong whitespace-nowrap"
                  data-testid="row-task-format-badge"
                >
                  <FormatIcon className="w-2.5 h-2.5" />
                  {format.label}
                </span>
              )}
            </div>
          </div>
        </div>
      </button>

      <FooterActions
        status={status}
        bidCount={bidCount}
        onReviewBids={onReviewBids}
        onEdit={onEdit}
        onBoost={onBoost}
        onMessage={onMessage}
        onMarkComplete={onMarkComplete}
        onLeaveReview={onLeaveReview}
        onRepost={onRepost}
      />
    </div>
  );
}

function subtitleFor(gig: MyGig, status: MyTasksStatus, now: number): string {
  if (status.kind === 'inProgress' && gig.accepted_by) {
    const posted = formatRelativeTime(gig.created_at, now);
    return posted ? `Helper assigned · ${posted}` : 'Helper assigned';
  }
  const parts: string[] = [];
  const posted = formatRelativeTime(gig.created_at, now);
  if (posted) parts.push(`Posted ${posted}`);
  const bidCount = gig.bid_count ?? 0;
  if (bidCount > 0) {
    parts.push(`${bidCount} ${bidCount === 1 ? 'bid' : 'bids'}`);
    const range = formatBidRange(gig.top_bid_amount, gig.price);
    if (range) parts.push(range);
  }
  return parts.join(' · ');
}

function BidderStack({ bidders, overflow }: { bidders: TopBidder[]; overflow: number }) {
  return (
    <div className="flex items-center" data-testid="bidder-stack">
      {bidders.map((b, i) => (
        <div
          key={b.id}
          className={`w-[22px] h-[22px] rounded-full border-2 border-app-surface flex items-center justify-center text-[9px] font-semibold ${toneClasses(b.color)} ${
            i === 0 ? '' : '-ml-2'
          }`}
        >
          {b.initials.slice(0, 2).toUpperCase()}
        </div>
      ))}
      {overflow > 0 && (
        <div className={`w-[22px] h-[22px] rounded-full border-2 border-app-surface bg-app-surface-sunken text-app-text-strong flex items-center justify-center text-[9px] font-bold ${
          bidders.length > 0 ? '-ml-2' : ''
        }`}>
          +{overflow}
        </div>
      )}
    </div>
  );
}

function toneClasses(color: string): string {
  switch (color.toLowerCase()) {
    case 'sky': return 'bg-sky-200 text-sky-800';
    case 'teal': return 'bg-emerald-200 text-emerald-800';
    case 'amber': return 'bg-amber-200 text-amber-800';
    case 'rose': return 'bg-rose-200 text-rose-800';
    case 'violet': return 'bg-violet-200 text-violet-800';
    default: return 'bg-slate-200 text-slate-800';
  }
}

function StatusChip({
  icon: Icon, label, variant,
}: { icon: typeof Inbox; label: string; variant: ChipVariant }) {
  const tone = chipClasses(variant);
  return (
    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold whitespace-nowrap ${tone}`}>
      <Icon className="w-2.5 h-2.5" />
      {label}
    </span>
  );
}

function chipClasses(variant: ChipVariant): string {
  switch (variant) {
    case 'success': return 'bg-emerald-100 text-emerald-700';
    case 'error': return 'bg-red-100 text-red-700';
    case 'info': return 'bg-blue-100 text-blue-700';
    case 'neutral': return 'bg-slate-100 text-slate-600';
  }
}

function FooterActions({
  status,
  bidCount,
  onReviewBids,
  onEdit,
  onBoost,
  onMessage,
  onMarkComplete,
  onLeaveReview,
  onRepost,
}: {
  status: MyTasksStatus;
  bidCount: number;
  onReviewBids: () => void;
  onEdit: () => void;
  onBoost: () => void;
  onMessage: () => void;
  onMarkComplete: () => void;
  onLeaveReview: () => void;
  onRepost: () => void;
}) {
  if (status.kind === 'completed') return null;

  const Ghost = ({ icon: Icon, label, onClick, flex = 1 }: any) => (
    <button
      onClick={onClick}
      style={{ flex }}
      className="h-[34px] rounded-lg px-3 border border-app-border bg-app-surface text-app-text text-xs font-semibold inline-flex items-center justify-center gap-1.5 hover:bg-app-hover"
    >
      <Icon className="w-3 h-3" />
      {label}
    </button>
  );
  const Primary = ({ icon: Icon, label, onClick, flex = 1 }: any) => (
    <button
      onClick={onClick}
      style={{ flex }}
      className="h-[34px] rounded-lg px-3 bg-primary-600 text-white text-xs font-semibold inline-flex items-center justify-center gap-1.5 hover:bg-primary-700 shadow-sm"
    >
      <Icon className="w-3 h-3" />
      {label}
    </button>
  );

  if (status.kind === 'reviewing') {
    return (
      <div className="flex gap-2 mt-3 pt-3 border-t border-app-border">
        <Ghost icon={Pencil} label="Edit" onClick={onEdit} />
        <Primary
          icon={Inbox}
          label={bidCount > 0 ? `Review ${bidCount} bids` : 'Review bids'}
          onClick={onReviewBids}
          flex={2}
        />
      </div>
    );
  }
  if (status.kind === 'urgent') {
    return (
      <div className="flex gap-2 mt-3 pt-3 border-t border-app-border">
        <Ghost icon={Timer} label="Extend 24h" onClick={onEdit} />
        <Primary
          icon={Inbox}
          label={bidCount > 0 ? `Review ${bidCount} bids` : 'Review bids'}
          onClick={onReviewBids}
          flex={2}
        />
      </div>
    );
  }
  if (status.kind === 'noBids') {
    return (
      <div className="flex gap-2 mt-3 pt-3 border-t border-app-border">
        <Ghost icon={Pencil} label="Edit details" onClick={onEdit} />
        <Primary icon={Rocket} label="Boost in feed" onClick={onBoost} />
      </div>
    );
  }
  if (status.kind === 'inProgress' || status.kind === 'scheduled') {
    return (
      <div className="flex gap-2 mt-3 pt-3 border-t border-app-border">
        <Ghost icon={MessageCircle} label="Message" onClick={onMessage} />
        <Primary icon={CheckCheck} label="Mark complete" onClick={onMarkComplete} />
      </div>
    );
  }
  if (status.kind === 'awaitReview') {
    return (
      <div className="flex gap-2 mt-3 pt-3 border-t border-app-border">
        <Primary icon={Star} label="Leave a review" onClick={onLeaveReview} />
      </div>
    );
  }
  if (status.kind === 'cancelled' || status.kind === 'expired') {
    return (
      <div className="flex gap-2 mt-3 pt-3 border-t border-app-border">
        <Primary icon={RotateCcw} label="Repost task" onClick={onRepost} />
      </div>
    );
  }
  return null;
}

function EmptyTabContent({
  tab, onPostTask,
}: { tab: MyTasksTab; onPostTask: () => void }) {
  switch (tab) {
    case 'open':
      // T6.0b — Magic Task primary CTA on empty state.
      return (
        <div data-testid="my-tasks-empty-open">
          <EmptyState
            icon={Sparkles}
            title="No tasks posted yet — try Magic Task"
            description="Describe what you need in a sentence. Magic Task drafts the title, budget, and schedule — you just confirm and post."
            actionLabel="Try Magic Task"
            onAction={onPostTask}
          />
          <div className="max-w-md mx-auto mt-4 flex flex-col gap-1.5">
            {[
              'Mount a TV above my fireplace this weekend',
              'Walk my dog Tue / Thu mornings',
              'Help me move a couch on Saturday',
            ].map((prompt) => (
              <button
                key={prompt}
                onClick={onPostTask}
                className="flex items-center gap-2 px-3 py-2.5 rounded-lg bg-app-surface border border-app-border text-app-text-strong text-xs font-medium text-left hover:shadow-sm transition"
                data-testid="my-tasks-empty-magic-prompt"
              >
                <Sparkles className="w-3 h-3 flex-shrink-0" style={{ color: 'var(--color-identity-magic)' }} strokeWidth={2.2} />
                <span className="flex-1 min-w-0">{prompt}</span>
                <ArrowUpRight className="w-3 h-3 flex-shrink-0 text-app-text-muted" strokeWidth={2} />
              </button>
            ))}
          </div>
        </div>
      );
    case 'active':
      return (
        <EmptyState
          icon={Play}
          title="No active tasks"
          description="Tasks you've assigned to a helper will show up here while the work is in progress."
        />
      );
    case 'done':
      return (
        <EmptyState
          icon={CheckCheck}
          title="No completed tasks yet"
          description="Finished tasks land here so you can leave reviews."
        />
      );
    case 'closed':
      return (
        <EmptyState
          icon={Ban}
          title="Nothing here"
          description="Cancelled or expired tasks will land here."
        />
      );
  }
}
