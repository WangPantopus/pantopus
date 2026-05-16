'use client';

// T5.2.4 — Cross-listing Offers V2 (web).
//
// Rewritten to use `<ListOfRowsShell />` so iOS / Android / web all
// render the same shape:
//   - Two equal-width tabs: "Received (N)" + "Sent (N)"
//   - No FAB (offers are created from a listing/gig detail)
//   - Each row is a Shape C — category gradient icon + listing title +
//     priceStack ("$220" + "asking $240") + status chip
//   - Row tap pushes the gig detail (each offer is on a gig).
//
// Backend (existing, unchanged): `/api/gigs/received-offers` and
// `/api/gigs/my-bids`. Both wrappers already live in `@pantopus/api`.

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ArrowLeft,
  AlertCircle,
  Briefcase,
  Check,
  Filter,
  Hammer,
  HandCoins,
  Heart,
  Hourglass,
  Info,
  Lightbulb,
  Package,
  Repeat,
  Send,
  Sparkles,
  Timer,
  UserPlus,
  X as XIcon,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import * as api from '@pantopus/api';
import { getAuthToken } from '@pantopus/api';
import ListOfRowsShell from '@/components/list-of-rows/ListOfRowsShell';
import type {
  GradientPair,
  ListOfRowsState,
  RowModel,
  StatusChipVariant,
} from '@/components/list-of-rows/types';

type Tab = 'received' | 'sent';

type Bid = {
  id: string;
  gig_id?: string | null;
  user_id?: string | null;
  bid_amount?: number | null;
  message?: string | null;
  proposed_time?: string | null;
  status?: string | null;
  created_at?: string | null;
  updated_at?: string | null;
  expires_at?: string | null;
  counter_amount?: number | null;
  counter_status?: string | null;
  countered_at?: string | null;
  withdrawn_at?: string | null;
  gig?: {
    id: string;
    title?: string | null;
    description?: string | null;
    price?: number | null;
    category?: string | null;
    status?: string | null;
    user_id?: string | null;
  } | null;
  bidder?: {
    id: string;
    username?: string | null;
    name?: string | null;
    first_name?: string | null;
    profile_picture_url?: string | null;
    city?: string | null;
    state?: string | null;
  } | null;
};

// ─── Status derivation (mirrors iOS / Android) ─────────────────

type OfferStatus =
  | 'new'
  | 'expiring'
  | 'countered'
  | 'accepted'
  | 'pending'
  | 'declined'
  | 'withdrawn'
  | 'expired';

const NEW_WINDOW_MS = 12 * 60 * 60 * 1000;
const EXPIRING_WINDOW_MS = 4 * 60 * 60 * 1000;

const STATUS_META: Record<OfferStatus, {
  label: string;
  icon: LucideIcon;
  variant: StatusChipVariant;
}> = {
  new: { label: 'New offer', icon: Sparkles, variant: 'personal' },
  expiring: { label: 'Expiring soon', icon: Timer, variant: 'error' },
  countered: { label: 'Countered', icon: Repeat, variant: 'warning' },
  accepted: { label: 'Accepted', icon: Check, variant: 'success' },
  pending: { label: 'Pending response', icon: Hourglass, variant: 'neutral' },
  declined: { label: 'Declined', icon: XIcon, variant: 'neutral' },
  withdrawn: { label: 'Withdrawn', icon: ArrowLeft, variant: 'neutral' },
  expired: { label: 'Expired', icon: AlertCircle, variant: 'neutral' },
};

function derivedStatus(bid: Bid, now: Date): OfferStatus {
  const status = (bid.status || '').toLowerCase();
  const hasLiveCounter =
    (bid.counter_amount ?? 0) > 0 || !!(bid.counter_status && bid.counter_status.length);
  if (hasLiveCounter && status === 'pending') return 'countered';
  switch (status) {
    case 'accepted':
    case 'assigned':
      return 'accepted';
    case 'rejected':
    case 'declined':
      return 'declined';
    case 'withdrawn':
      return 'withdrawn';
    case 'expired':
      return 'expired';
    case 'pending': {
      if (bid.expires_at) {
        const expires = new Date(bid.expires_at).getTime();
        const left = expires - now.getTime();
        if (left > 0 && left < EXPIRING_WINDOW_MS) return 'expiring';
        if (left <= 0) return 'expired';
      }
      if (bid.created_at) {
        const created = new Date(bid.created_at).getTime();
        if (now.getTime() - created < NEW_WINDOW_MS) return 'new';
      }
      return 'pending';
    }
    default:
      return 'pending';
  }
}

// ─── Category mapping (mirrors iOS / Android `OffersCategory`) ─

type Category =
  | 'handyman'
  | 'cleaning'
  | 'moving'
  | 'petCare'
  | 'childCare'
  | 'tutoring'
  | 'tech'
  | 'delivery'
  | 'other';

interface CategoryStyle {
  icon: LucideIcon;
  gradient: GradientPair;
}

// Hex literals here mirror the design-token values in
// `@pantopus/theme` (Category/* + Semantic/*). Wrapping them in the
// `CATEGORY_STYLES` map keeps individual row mappers token-free.
const CATEGORY_STYLES: Record<Category, CategoryStyle> = {
  handyman: { icon: Hammer, gradient: { start: '#f97316', end: '#d97706' } },
  cleaning: { icon: Briefcase, gradient: { start: '#27AE60', end: '#0284c7' } },
  moving: { icon: Package, gradient: { start: '#8E44AD', end: '#7c3aed' } },
  petCare: { icon: Heart, gradient: { start: '#E74C3C', end: '#16a34a' } },
  childCare: { icon: UserPlus, gradient: { start: '#F39C12', end: '#b91c1c' } },
  tutoring: { icon: Lightbulb, gradient: { start: '#2980B9', end: '#d97706' } },
  tech: { icon: Info, gradient: { start: '#3498DB', end: '#6b7280' } },
  delivery: { icon: Send, gradient: { start: '#374151', end: '#0369a1' } },
  other: { icon: Briefcase, gradient: { start: '#0284c7', end: '#0369a1' } },
};

function categoryFrom(raw: string | null | undefined): Category {
  const key = (raw ?? '').toLowerCase().replace(/[_\-\s]/g, '');
  switch (key) {
    case 'handyman':
    case 'handy':
    case 'repair':
    case 'repairs':
      return 'handyman';
    case 'cleaning':
    case 'clean':
      return 'cleaning';
    case 'moving':
    case 'move':
    case 'movers':
      return 'moving';
    case 'petcare':
    case 'pet':
    case 'pets':
    case 'dogwalking':
    case 'petsitting':
      return 'petCare';
    case 'childcare':
    case 'child':
    case 'babysitting':
    case 'nanny':
      return 'childCare';
    case 'tutoring':
    case 'tutor':
    case 'lessons':
    case 'teaching':
      return 'tutoring';
    case 'tech':
    case 'technology':
    case 'it':
    case 'computer':
    case 'techsupport':
      return 'tech';
    case 'delivery':
    case 'deliveries':
    case 'courier':
      return 'delivery';
    default:
      return 'other';
  }
}

// ─── Formatters (mirrors iOS / Android) ────────────────────────

function formatPrice(amount: number | null | undefined): string {
  if (amount == null) return '$—';
  return `$${Math.round(amount)}`;
}

function formatAskingSublabel(price: number | null | undefined): string | undefined {
  if (price == null || price <= 0) return undefined;
  return `asking ${formatPrice(price)}`;
}

function formatRelativeTime(raw: string | null | undefined, now: Date): string | undefined {
  if (!raw) return undefined;
  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) return undefined;
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
  if (days < 7) return date.toLocaleDateString('en-US', { weekday: 'short' });
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

function displayBidderName(bid: Bid): string {
  return (
    bid.bidder?.name ||
    bid.bidder?.first_name ||
    bid.bidder?.username ||
    'Someone'
  );
}

function subtitleFor(bid: Bid, perspective: Tab, now: Date): string {
  const parts: string[] = [];
  if (perspective === 'received') {
    parts.push(`From ${displayBidderName(bid)}`);
    if (bid.bidder?.city) parts.push(bid.bidder.city);
  } else {
    parts.push('Your offer');
  }
  const time = formatRelativeTime(bid.created_at, now);
  if (time) parts.push(time);
  return parts.join(' · ');
}

function metaTailFor(bid: Bid, status: OfferStatus, perspective: Tab): string | undefined {
  if (status !== 'countered') return undefined;
  const counter = bid.counter_amount;
  if (counter == null || counter <= 0) return undefined;
  return perspective === 'received'
    ? `you countered ${formatPrice(counter)}`
    : `counter ${formatPrice(counter)}`;
}

function makeRow(bid: Bid, perspective: Tab, now: Date, onTap: (b: Bid) => void): RowModel {
  const status = derivedStatus(bid, now);
  const meta = STATUS_META[status];
  const category = categoryFrom(bid.gig?.category);
  const style = CATEGORY_STYLES[category];
  return {
    id: bid.id,
    title: bid.gig?.title || 'Offer',
    subtitle: subtitleFor(bid, perspective, now),
    template: 'statusChip',
    leading: { kind: 'categoryGradientIcon', icon: style.icon, gradient: style.gradient },
    trailing: {
      kind: 'priceStack',
      amount: formatPrice(bid.bid_amount),
      sublabel: formatAskingSublabel(bid.gig?.price),
    },
    onTap: () => onTap(bid),
    chips: [{ text: meta.label, icon: meta.icon, tint: { kind: 'status', variant: meta.variant } }],
    metaTail: metaTailFor(bid, status, perspective),
  };
}

// ─── Page ──────────────────────────────────────────────────────

export default function OffersPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<Tab>('received');

  useEffect(() => {
    if (!getAuthToken()) router.push('/login');
  }, [router]);

  const receivedQuery = useQuery({
    queryKey: ['offers', 'received'],
    queryFn: async () => (await api.gigs.getReceivedOffers()) as { offers: Bid[]; total?: number },
    staleTime: 30_000,
  });
  const sentQuery = useQuery({
    queryKey: ['offers', 'sent'],
    queryFn: async () => (await api.gigs.getMyBids()) as { bids: Bid[] },
    staleTime: 30_000,
  });

  const received = useMemo<Bid[]>(() => receivedQuery.data?.offers ?? [], [receivedQuery.data]);
  const sent = useMemo<Bid[]>(() => sentQuery.data?.bids ?? [], [sentQuery.data]);

  const now = useMemo(() => new Date(), [received, sent]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleOpenOffer = useCallback(
    (bid: Bid) => {
      const id = bid.gig_id || bid.gig?.id;
      if (id) router.push(`/app/gigs/${id}`);
    },
    [router],
  );

  const handleRefresh = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['offers'] });
  }, [queryClient]);

  const state = useMemo<ListOfRowsState>(() => {
    const loading = receivedQuery.isPending || sentQuery.isPending;
    if (loading) return { kind: 'loading' };
    if (receivedQuery.isError && sentQuery.isError) {
      const message =
        receivedQuery.error?.message ?? sentQuery.error?.message ?? "Couldn't load offers.";
      return { kind: 'error', message };
    }
    const items = tab === 'received' ? received : sent;
    if (items.length === 0) {
      return {
        kind: 'empty',
        config:
          tab === 'received'
            ? {
                icon: HandCoins,
                headline: 'No offers yet',
                subcopy:
                  'When a neighbor offers a price on one of your listings, it’ll land here. Listings with photos and a fair ask tend to draw offers within a day.',
                ctaTitle: 'Post a task',
                onCta: () => router.push('/app/gigs-v2/new'),
              }
            : {
                icon: HandCoins,
                headline: 'No offers sent yet',
                subcopy:
                  "Browse listings and gigs you'd like to buy or help with — your offers will show up here.",
                ctaTitle: 'Browse listings',
                onCta: () => router.push('/app/map'),
              },
      };
    }
    const rows = items.map((bid) => makeRow(bid, tab, now, handleOpenOffer));
    return {
      kind: 'loaded',
      sections: [{ id: tab, rows }],
      hasMore: false,
    };
  }, [
    receivedQuery.isPending,
    receivedQuery.isError,
    receivedQuery.error,
    sentQuery.isPending,
    sentQuery.isError,
    sentQuery.error,
    received,
    sent,
    tab,
    now,
    handleOpenOffer,
    router,
  ]);

  return (
    <ListOfRowsShell
      title="Offers"
      state={state}
      onRefresh={handleRefresh}
      tabs={[
        { id: 'received', label: 'Received', count: received.length },
        { id: 'sent', label: 'Sent', count: sent.length },
      ]}
      selectedTab={tab}
      onTabChange={(id) => setTab(id as Tab)}
      topBarAction={{
        icon: Filter,
        accessibilityLabel: 'Filter offers',
        onClick: () => router.push('/app/offers'),
      }}
    />
  );
}
