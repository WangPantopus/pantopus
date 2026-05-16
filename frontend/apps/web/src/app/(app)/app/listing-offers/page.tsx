'use client';

// T5.3.4 — Listing offers (web reskin). Built on top of the shared
// `<RowCard />` so each offer row matches the iOS / Android shell
// pixel-for-pixel: 44pt buyer avatar leading, priceStack trailing,
// status chip + counter pill + meta tail, optional note, and per-
// status footer.
//
// Distinct from `/app/offers` (the cross-listing inbox panel) — this
// surface is scoped to a single listing and is reached from listing
// detail's "View offers" affordance with `?listingId=…&title=…`.

import { Suspense, useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import {
  ArrowLeft,
  Check,
  CheckCheck,
  ChevronDown,
  CircleDot,
  Clock,
  FileText,
  HandCoins,
  Pencil,
  Repeat,
  Share2,
  ShoppingBag,
  Sparkles,
  Timer,
  X,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import * as api from '@pantopus/api';
import { getAuthToken } from '@pantopus/api';
import { toast } from '@/components/ui/toast-store';
import { confirmStore } from '@/components/ui/confirm-store';
import RowCard from '@/components/list-of-rows/RowCard';
import type {
  RowChip,
  RowFooter,
  RowModel,
} from '@/components/list-of-rows/types';

type OfferStatus =
  | 'pending'
  | 'countered'
  | 'accepted'
  | 'declined'
  | 'expired'
  | 'withdrawn'
  | 'completed';

const STATUS_META: Record<
  OfferStatus,
  { label: string; tone: RowChip['tint']; icon: LucideIcon }
> = {
  pending: { label: 'Pending', tone: { kind: 'status', variant: 'personal' }, icon: Sparkles },
  countered: { label: 'Countered', tone: { kind: 'status', variant: 'warning' }, icon: Repeat },
  accepted: { label: 'Accepted', tone: { kind: 'status', variant: 'success' }, icon: Check },
  declined: { label: 'Declined', tone: { kind: 'status', variant: 'neutral' }, icon: X },
  expired: { label: 'Expired', tone: { kind: 'status', variant: 'neutral' }, icon: Timer },
  withdrawn: { label: 'Withdrawn', tone: { kind: 'status', variant: 'neutral' }, icon: ArrowLeft },
  completed: { label: 'Completed', tone: { kind: 'status', variant: 'success' }, icon: CheckCheck },
};

function formatPrice(amount: number | null | undefined): string {
  if (amount == null) return '$—';
  return `$${Math.round(amount)}`;
}

function formatRelativeTime(raw: string | undefined): string | null {
  if (!raw) return null;
  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) return null;
  const seconds = (Date.now() - date.getTime()) / 1000;
  if (seconds < 60) return 'now';
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h`;
  const days = Math.floor(seconds / 86400);
  if (days === 1) return 'yesterday';
  return `${days}d`;
}

function ageInDays(raw: string | undefined): number {
  if (!raw) return 0;
  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) return 0;
  return Math.floor((Date.now() - date.getTime()) / (1000 * 86400));
}

function postedAgo(raw: string | undefined): string | null {
  if (!raw) return null;
  const seconds = (Date.now() - new Date(raw).getTime()) / 1000;
  if (Number.isNaN(seconds)) return null;
  if (seconds < 60) return 'just now';
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
  const days = Math.floor(seconds / 86400);
  if (days === 1) return 'yesterday';
  return `${days} days ago`;
}

function displayName(buyer?: api.ListingCreator | null): string {
  if (!buyer) return 'Someone';
  const first = buyer.first_name?.trim();
  const last = buyer.last_name?.trim();
  if (first) return last ? `${first} ${last}` : first;
  if (buyer.name) return buyer.name;
  if (buyer.username) return buyer.username;
  return 'Someone';
}

// Avatar tones — six categorical solid backgrounds the row leading
// cycles through. Mirrors the iOS / Android tone palette
// (sky / teal / amber / rose / violet / slate). Hex literals are
// scoped to this file because the shared `<RowCard />` renders the
// avatar background inline as a CSS color value.
const AVATAR_TONES = [
  '#bae6fd', // sky
  '#a7f3d0', // teal
  '#fde68a', // amber
  '#fecdd3', // rose
  '#ddd6fe', // violet
  '#e2e8f0', // slate
];

function avatarToneColor(seed: string): string {
  let acc = 0;
  for (const ch of seed) acc = (acc * 31 + ch.charCodeAt(0)) | 0;
  return AVATAR_TONES[Math.abs(acc) % AVATAR_TONES.length] ?? AVATAR_TONES[0];
}

function ListingOffersContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const listingId = searchParams.get('listingId') || '';
  const listingTitleHint = searchParams.get('title') || '';

  const [offers, setOffers] = useState<api.ListingOffer[]>([]);
  const [listing, setListing] = useState<api.Listing | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [counterTarget, setCounterTarget] = useState<api.ListingOffer | null>(null);
  const [counterAmount, setCounterAmount] = useState('');
  const [counterMessage, setCounterMessage] = useState('');

  useEffect(() => {
    if (!getAuthToken()) router.push('/login');
  }, [router]);

  const fetchAll = useCallback(async () => {
    if (!listingId) return;
    try {
      const [offersResp, listingResp] = await Promise.all([
        api.listings.getListingOffers(listingId),
        api.listings.getListing(listingId).catch(() => ({ listing: null }) as { listing: api.Listing | null }),
      ]);
      setOffers(offersResp.offers || []);
      setListing(listingResp.listing);
      setError(null);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to load offers';
      setError(message);
      toast.error(message);
    }
  }, [listingId]);

  useEffect(() => {
    setLoading(true);
    fetchAll().finally(() => setLoading(false));
  }, [fetchAll]);

  const handleAccept = useCallback(
    async (offer: api.ListingOffer) => {
      const yes = await confirmStore.open({
        title: 'Accept offer',
        description:
          offer.amount != null
            ? `Accept this offer for $${Math.round(offer.amount)}?`
            : 'Accept this offer?',
        confirmLabel: 'Accept',
        variant: 'primary',
      });
      if (!yes) return;
      const previous = offers;
      setOffers((rows) =>
        rows.map((row) => (row.id === offer.id ? { ...row, status: 'accepted' } : row)),
      );
      try {
        await api.listings.acceptOffer(listingId, offer.id);
        toast.success('Offer accepted!');
        fetchAll();
      } catch (err: unknown) {
        const message = err instanceof Error ? err.message : 'Failed to accept offer';
        toast.error(message);
        setOffers(previous);
      }
    },
    [listingId, offers, fetchAll],
  );

  const handleDecline = useCallback(
    async (offer: api.ListingOffer) => {
      const yes = await confirmStore.open({
        title: 'Decline offer',
        description: 'Are you sure you want to decline this offer?',
        confirmLabel: 'Decline',
        variant: 'destructive',
      });
      if (!yes) return;
      const previous = offers;
      setOffers((rows) =>
        rows.map((row) => (row.id === offer.id ? { ...row, status: 'declined' } : row)),
      );
      try {
        await api.listings.declineOffer(listingId, offer.id);
        toast.success('Offer declined');
        fetchAll();
      } catch (err: unknown) {
        const message = err instanceof Error ? err.message : 'Failed to decline offer';
        toast.error(message);
        setOffers(previous);
      }
    },
    [listingId, offers, fetchAll],
  );

  const openCounter = useCallback((offer: api.ListingOffer) => {
    setCounterTarget(offer);
    setCounterAmount(offer.amount != null ? String(Math.round(offer.amount)) : '');
    setCounterMessage('');
  }, []);

  const handleCounter = useCallback(async () => {
    if (!counterTarget) return;
    const amt = parseFloat(counterAmount);
    if (!amt || amt <= 0) {
      toast.warning('Enter a valid amount');
      return;
    }
    const previous = offers;
    setOffers((rows) =>
      rows.map((row) =>
        row.id === counterTarget.id
          ? { ...row, status: 'countered', counter_amount: amt }
          : row,
      ),
    );
    setCounterTarget(null);
    setCounterAmount('');
    setCounterMessage('');
    try {
      await api.listings.counterOffer(listingId, counterTarget.id, {
        counterAmount: amt,
        counterMessage: counterMessage || undefined,
      });
      toast.success('Counter offer sent!');
      fetchAll();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to send counter offer';
      toast.error(message);
      setOffers(previous);
    }
  }, [counterAmount, counterMessage, counterTarget, listingId, offers, fetchAll]);

  const sorted = useMemo(
    () => [...offers].sort((a, b) => (b.amount ?? 0) - (a.amount ?? 0)),
    [offers],
  );

  const leadingId = useMemo(
    () => sorted.find((offer) => statusOf(offer) === 'pending')?.id ?? null,
    [sorted],
  );

  const headerTitle = listing?.title ?? listingTitleHint ?? 'Listing';

  return (
    <div className="min-h-screen bg-app-bg">
      <header className="sticky top-0 z-10 flex items-center h-13 px-3 bg-app-surface border-b border-app-border">
        <button
          type="button"
          onClick={() => router.back()}
          aria-label="Back"
          className="w-9 h-9 inline-flex items-center justify-center text-app-text rounded-md hover:bg-app-hover"
        >
          <ArrowLeft className="w-[22px] h-[22px]" />
        </button>
        <div className="flex-1 min-w-0 text-center px-2">
          <div className="text-base font-semibold text-app-text tracking-tight leading-tight">
            Listing offers
          </div>
          {(listing?.title || listingTitleHint) && (
            <div className="text-[11px] text-app-text-secondary truncate">
              {listing?.title ?? listingTitleHint}
            </div>
          )}
        </div>
        <button
          type="button"
          aria-label="Share listing"
          className="w-9 h-9 inline-flex items-center justify-center text-app-text rounded-md hover:bg-app-hover"
        >
          <Share2 className="w-[22px] h-[22px]" />
        </button>
      </header>

      <div className="max-w-3xl mx-auto px-4 py-4">
        <ListingContextHeader
          title={headerTitle}
          listing={listing}
          offerCount={offers.length}
        />

        {loading ? (
          <LoadingRows />
        ) : error ? (
          <div className="text-center py-12">
            <p className="text-sm text-app-error">{error}</p>
            <button
              type="button"
              onClick={() => fetchAll()}
              className="mt-3 px-4 py-2 bg-primary-600 text-white rounded-md text-sm font-semibold hover:bg-primary-700"
            >
              Try again
            </button>
          </div>
        ) : sorted.length === 0 ? (
          <EmptyOffers />
        ) : (
          <div className="space-y-2.5 mt-1">
            {sorted.map((offer, index) => (
              <RowCard
                key={offer.id}
                row={buildRowModel(offer, {
                  index,
                  total: sorted.length,
                  isLeading: offer.id === leadingId,
                  askingPrice: listing?.price ?? null,
                  onAccept: () => handleAccept(offer),
                  onDecline: () => handleDecline(offer),
                  onCounter: () => openCounter(offer),
                  onViewTransaction: () =>
                    router.push(`/app/marketplace/${listingId}`),
                })}
              />
            ))}
          </div>
        )}
      </div>

      {counterTarget && (
        <CounterSheet
          target={counterTarget}
          amount={counterAmount}
          message={counterMessage}
          onAmountChange={setCounterAmount}
          onMessageChange={setCounterMessage}
          onCancel={() => setCounterTarget(null)}
          onConfirm={handleCounter}
        />
      )}
    </div>
  );
}

function statusOf(offer: api.ListingOffer): OfferStatus {
  const raw = (offer.status ?? '').toLowerCase();
  if (raw === 'pending' || raw === 'countered' || raw === 'accepted' || raw === 'declined' || raw === 'expired' || raw === 'withdrawn' || raw === 'completed') {
    return raw as OfferStatus;
  }
  return 'pending';
}

function buildRowModel(
  offer: api.ListingOffer,
  ctx: {
    index: number;
    total: number;
    isLeading: boolean;
    askingPrice: number | null;
    onAccept: () => void;
    onDecline: () => void;
    onCounter: () => void;
    onViewTransaction: () => void;
  },
): RowModel {
  const status = statusOf(offer);
  const meta = STATUS_META[status];
  const buyerName = displayName(offer.buyer);
  const chips: RowChip[] = [
    {
      text: meta.label,
      icon: meta.icon,
      tint: meta.tone,
    },
  ];
  if (status === 'countered' && offer.counter_amount != null) {
    chips.push({
      text: `Your counter ${formatPrice(offer.counter_amount)}`,
      icon: Repeat,
      tint: {
        kind: 'custom',
        background: 'var(--app-surface-sunken, #f3f4f6)',
        foreground: 'var(--app-text-strong, #374151)',
      },
    });
  }

  const footer = buildFooter(status, ctx);
  const age = ageInDays(offer.created_at);
  const metaTailParts: string[] = [];
  if (age >= 1) metaTailParts.push(`${age} day${age === 1 ? '' : 's'} old`);
  if (ctx.total > 1) metaTailParts.push(`${ctx.index + 1} of ${ctx.total} offers`);
  const toneColor = avatarToneColor(offer.buyer?.id ?? offer.id);
  const sublabel =
    ctx.askingPrice != null && ctx.askingPrice > 0
      ? `asking ${formatPrice(ctx.askingPrice)}`
      : undefined;

  return {
    id: offer.id,
    title: buyerName,
    subtitle: formatRelativeTime(offer.created_at) ?? '',
    template: 'statusChip',
    leading: {
      kind: 'avatarWithBadge',
      name: buyerName,
      imageURL: offer.buyer?.profile_picture_url ?? null,
      size: 'large',
      verified: false,
      background: { kind: 'solid', color: toneColor },
    },
    trailing: {
      kind: 'priceStack',
      amount: formatPrice(offer.amount),
      sublabel,
    },
    chips,
    metaTail: metaTailParts.length ? metaTailParts.join(' · ') : undefined,
    note: offer.message?.trim() ? offer.message : undefined,
    highlight: ctx.isLeading ? 'leading' : undefined,
    footer,
  };
}

function buildFooter(
  status: OfferStatus,
  ctx: {
    onAccept: () => void;
    onDecline: () => void;
    onCounter: () => void;
    onViewTransaction: () => void;
  },
): RowFooter | undefined {
  switch (status) {
    case 'pending':
      return {
        actions: [
          { title: 'Counter', icon: Repeat, variant: 'ghost', onClick: ctx.onCounter },
          { title: 'Accept', icon: Check, variant: 'primary', onClick: ctx.onAccept },
        ],
      };
    case 'countered':
      return {
        actions: [
          {
            title: 'Withdraw counter',
            icon: X,
            variant: 'destructive',
            onClick: ctx.onDecline,
          },
          { title: 'Send counter', icon: Repeat, variant: 'primary', onClick: ctx.onCounter },
        ],
      };
    case 'accepted':
    case 'completed':
      return {
        actions: [
          {
            title: 'View transaction',
            icon: FileText,
            variant: 'primary',
            onClick: ctx.onViewTransaction,
          },
        ],
      };
    default:
      return undefined;
  }
}

function ListingContextHeader({
  title,
  listing,
  offerCount,
}: {
  title: string;
  listing: api.Listing | null;
  offerCount: number;
}) {
  const askPrice = listing?.is_free
    ? 'Free'
    : listing?.price != null
      ? formatPrice(listing.price)
      : '';
  const posted = postedAgo(listing?.created_at);
  const statusLabel = (listing?.status ?? 'active').replace(/^./, (c) => c.toUpperCase());
  const StatusIcon = listing?.status === 'reserved' ? Check : CircleDot;
  return (
    <div className="space-y-3 mb-2">
      <div className="bg-app-surface border border-app-border rounded-2xl p-3.5 shadow-sm">
        <div className="flex items-center gap-3">
          {listing?.media_urls?.[0] ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={listing.media_urls[0]}
              alt={title}
              className="w-16 h-16 rounded-xl object-cover flex-shrink-0"
            />
          ) : (
            <div className="w-16 h-16 rounded-xl bg-gradient-to-br from-app-business to-primary-700 flex items-center justify-center flex-shrink-0">
              <ShoppingBag className="w-7 h-7 text-white" />
            </div>
          )}
          <div className="flex-1 min-w-0">
            <div className="flex items-baseline gap-2 mb-1">
              <div className="flex-1 min-w-0 text-sm font-semibold text-app-text truncate">
                {title}
              </div>
              {askPrice && (
                <div className="text-lg font-bold text-app-text">{askPrice}</div>
              )}
            </div>
            <div className="flex items-center gap-2.5 mb-1.5 text-[11px] text-app-text-secondary">
              {posted && (
                <span className="inline-flex items-center gap-1">
                  <Clock className="w-2.5 h-2.5" />
                  Listed {posted}
                </span>
              )}
              {typeof listing?.view_count === 'number' && (
                <>
                  <span>·</span>
                  <span>{listing.view_count} views</span>
                </>
              )}
            </div>
            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-app-success-bg text-app-success text-[10px] font-semibold">
              <StatusIcon className="w-2.5 h-2.5" />
              {statusLabel}
            </span>
          </div>
        </div>
      </div>
      <div className="flex items-center justify-between px-1">
        <div className="text-xs font-semibold text-app-text-strong">
          {offerCount} {offerCount === 1 ? 'offer' : 'offers'}
        </div>
        <button
          type="button"
          className="inline-flex items-center gap-1 text-xs font-medium text-app-text-secondary hover:text-app-text"
        >
          <Repeat className="w-3 h-3" />
          Highest first
          <ChevronDown className="w-3 h-3" />
        </button>
      </div>
    </div>
  );
}

function EmptyOffers() {
  return (
    <div className="flex flex-col items-center justify-center text-center py-12 px-8">
      <div className="w-[72px] h-[72px] rounded-full bg-primary-50 text-primary-600 flex items-center justify-center mb-4">
        <HandCoins className="w-8 h-8" />
      </div>
      <p className="text-base font-semibold text-app-text mb-2">No offers on this listing yet</p>
      <p className="text-sm text-app-text-secondary max-w-xs mb-5">
        Most listings draw their first offer within 24 hours. Share it with a few neighborhoods
        to speed things up.
      </p>
      <div className="flex gap-2 flex-wrap justify-center">
        <button
          type="button"
          className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-primary-600 text-white text-sm font-semibold shadow-sm hover:bg-primary-700"
        >
          <Share2 className="w-3.5 h-3.5" />
          Share listing
        </button>
        <button
          type="button"
          className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-app-surface border border-app-border text-app-text text-sm font-semibold hover:bg-app-hover"
        >
          <Pencil className="w-3.5 h-3.5" />
          Edit price
        </button>
      </div>
    </div>
  );
}

function LoadingRows() {
  return (
    <div className="space-y-2.5 mt-1">
      {Array.from({ length: 4 }).map((_, idx) => (
        <div key={idx} className="rounded-xl bg-app-surface border border-app-border px-4 py-3">
          <div className="flex items-center gap-3">
            <div className="w-11 h-11 rounded-full bg-app-surface-sunken animate-pulse" />
            <div className="flex-1 space-y-2">
              <div className="h-3.5 w-40 bg-app-surface-sunken rounded animate-pulse" />
              <div className="h-3 w-24 bg-app-surface-sunken rounded animate-pulse" />
            </div>
            <div className="h-5 w-12 bg-app-surface-sunken rounded animate-pulse" />
          </div>
        </div>
      ))}
    </div>
  );
}

function CounterSheet({
  target,
  amount,
  message,
  onAmountChange,
  onMessageChange,
  onCancel,
  onConfirm,
}: {
  target: api.ListingOffer;
  amount: string;
  message: string;
  onAmountChange: (v: string) => void;
  onMessageChange: (v: string) => void;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const buyerName = displayName(target.buyer);
  const canSend = parseFloat(amount) > 0;
  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="counter-sheet-title"
      className="fixed inset-0 z-50 bg-black/40 flex items-end sm:items-center justify-center"
      onClick={onCancel}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="w-full sm:max-w-md bg-app-surface rounded-t-2xl sm:rounded-2xl p-5 space-y-4"
      >
        <div className="space-y-1">
          <h2 id="counter-sheet-title" className="text-lg font-semibold text-app-text">
            Counter {buyerName}&apos;s offer
          </h2>
          {target.amount != null && (
            <p className="text-sm text-app-text-secondary">
              Original offer: {formatPrice(target.amount)}
            </p>
          )}
        </div>

        <label className="block">
          <span className="block text-xs font-semibold text-app-text mb-1.5">
            Your counter amount
          </span>
          <div className="flex items-center gap-1.5 border border-app-border rounded-lg px-3 py-2">
            <span className="text-base font-semibold text-app-text">$</span>
            <input
              type="text"
              inputMode="decimal"
              value={amount}
              onChange={(e) => onAmountChange(e.target.value.replace(/[^0-9.]/g, ''))}
              placeholder="Amount"
              data-testid="counter-amount"
              className="flex-1 py-1 text-base text-app-text bg-transparent outline-none"
              autoFocus
            />
          </div>
        </label>

        <label className="block">
          <span className="block text-xs font-semibold text-app-text mb-1.5">
            Optional message
          </span>
          <textarea
            value={message}
            onChange={(e) => onMessageChange(e.target.value)}
            rows={2}
            data-testid="counter-message"
            placeholder="Add a note (optional)"
            className="w-full border border-app-border rounded-lg px-3 py-2 text-sm text-app-text bg-transparent outline-none resize-none"
          />
        </label>

        <div className="flex gap-2 pt-1">
          <button
            type="button"
            onClick={onCancel}
            data-testid="counter-cancel"
            className="flex-1 px-4 py-2 border border-app-border text-app-text rounded-lg text-sm font-semibold hover:bg-app-hover"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={!canSend}
            data-testid="counter-confirm"
            className={`flex-1 px-4 py-2 rounded-lg text-sm font-semibold text-white ${
              canSend
                ? 'bg-primary-600 hover:bg-primary-700'
                : 'bg-app-text-muted cursor-not-allowed'
            }`}
          >
            Send counter
          </button>
        </div>
      </div>
    </div>
  );
}

export default function ListingOffersPage() {
  return (
    <Suspense>
      <ListingOffersContent />
    </Suspense>
  );
}
