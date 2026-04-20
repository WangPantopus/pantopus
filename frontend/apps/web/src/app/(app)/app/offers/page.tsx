'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Image from 'next/image';
import * as api from '@pantopus/api';
import PageHeader from '@/components/PageHeader';
import SearchInput from '@/components/SearchInput';
import { BID_STATUS } from '@/components/statusColors';
import UserIdentityLink from '@/components/user/UserIdentityLink';
import { formatTimeAgo as timeAgo } from '@pantopus/ui-utils';

type Offer = {
  id: string;
  gig_id: string;
  user_id: string;
  bid_amount: number;
  message: string;
  proposed_time?: string;
  status: string;
  created_at: string;
  gig?: { id: string; title: string; price: number; status: string; category?: string };
  bidder?: { id: string; username: string; name?: string; first_name?: string; profile_picture_url?: string; city?: string; state?: string };
};

const STATUS_STYLES: Record<string, { bg: string; text: string; label: string }> = BID_STATUS;

export default function OffersPage() {
  const router = useRouter();
  const [tab, setTab] = useState<'received' | 'sent'>('received');
  const [received, setReceived] = useState<Offer[]>([]);
  const [sent, setSent] = useState<Offer[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<string>('all');
  const [search, setSearch] = useState('');

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setLoading(true);
    try {
      const [recvRes, sentRes] = await Promise.allSettled([
        api.gigs.getReceivedOffers(),
        api.gigs.getMyBids(),
      ]);
      if (recvRes.status === 'fulfilled') setReceived(((recvRes.value as Record<string, unknown>).offers || []) as Offer[]);
      if (sentRes.status === 'fulfilled') setSent(((sentRes.value as Record<string, unknown>).bids || []) as Offer[]);
    } catch {}
    setLoading(false);
  };

  const items = tab === 'received' ? received : sent;
  const filtered = items.filter(o => {
    if (filter !== 'all' && o.status !== filter) return false;
    if (search.trim()) {
      const q = search.toLowerCase();
      const title = (o.gig?.title || '').toLowerCase();
      const msg = (o.message || '').toLowerCase();
      const name = (o.bidder?.name || o.bidder?.username || '').toLowerCase();
      if (!title.includes(q) && !msg.includes(q) && !name.includes(q)) return false;
    }
    return true;
  });

  const recvPending = received.filter(o => o.status === 'pending').length;
  const sentPending = sent.filter(o => o.status === 'pending').length;

  return (
    <div className="max-w-3xl mx-auto p-4 sm:p-6">
      {/* Header */}
      <PageHeader
        title="Offers"
        subtitle="Manage bids and offers on your tasks"
        ctaLabel="Post a Task"
        ctaOnClick={() => router.push('/app/gigs-v2/new')}
      >
        <SearchInput
          value={search}
          onChange={setSearch}
          placeholder="Search offers…"
          className="max-w-sm"
        />
      </PageHeader>

      {/* Tabs */}
      <div className="flex gap-1 bg-app-surface-sunken rounded-lg p-1 mb-5">
        <button
          onClick={() => { setTab('received'); setFilter('all'); }}
          className={`flex-1 py-2 text-sm font-medium rounded-md transition ${
            tab === 'received' ? 'bg-app-surface text-app-text shadow-sm' : 'text-app-text-secondary hover:text-app-text'
          }`}
        >
          Received {recvPending > 0 && (
            <span className="ml-1.5 inline-flex items-center justify-center min-w-[20px] h-5 px-1.5 bg-amber-500 text-white text-[11px] font-bold rounded-full">
              {recvPending}
            </span>
          )}
        </button>
        <button
          onClick={() => { setTab('sent'); setFilter('all'); }}
          className={`flex-1 py-2 text-sm font-medium rounded-md transition ${
            tab === 'sent' ? 'bg-app-surface text-app-text shadow-sm' : 'text-app-text-secondary hover:text-app-text'
          }`}
        >
          Sent {sentPending > 0 && (
            <span className="ml-1.5 inline-flex items-center justify-center min-w-[20px] h-5 px-1.5 bg-blue-500 text-white text-[11px] font-bold rounded-full">
              {sentPending}
            </span>
          )}
        </button>
      </div>

      {/* Filter */}
      <div className="flex gap-2 mb-4 overflow-x-auto">
        {['all', 'pending', 'accepted', 'declined'].map(f => (
          <button
            key={f}
            onClick={() => setFilter(f)}
            className={`px-3 py-1.5 text-xs font-medium rounded-full whitespace-nowrap transition ${
              filter === f
                ? 'bg-gray-900 text-white'
                : 'bg-app-surface-sunken text-app-text-secondary hover:bg-app-hover'
            }`}
          >
            {f === 'all' ? 'All' : (STATUS_STYLES[f]?.label || f)}
          </button>
        ))}
      </div>

      {/* Content */}
      {loading ? (
        <div className="py-16 text-center">
          <div className="animate-spin rounded-full h-8 w-8 border-2 border-app-border border-t-gray-600 mx-auto" />
          <p className="text-sm text-app-text-secondary mt-3">Loading offers...</p>
        </div>
      ) : filtered.length === 0 ? (
        <div className="py-16 text-center bg-app-surface rounded-xl border border-app-border">
          <div className="text-4xl mb-3">{tab === 'received' ? '📥' : '📤'}</div>
          <h3 className="text-lg font-semibold text-app-text mb-1">
            {filter !== 'all'
              ? `No ${filter} offers`
              : tab === 'received'
                ? 'No offers received yet'
                : 'No offers sent yet'}
          </h3>
          <p className="text-sm text-app-text-secondary mb-4">
            {tab === 'received'
              ? 'Post a task and people will send you offers.'
              : 'Browse the map and bid on tasks to get started.'}
          </p>
          <button
            onClick={() => router.push(tab === 'received' ? '/app/gigs-v2/new' : '/app/map')}
            className="px-4 py-2 bg-gray-900 text-white text-sm font-medium rounded-lg hover:bg-gray-800 transition"
          >
            {tab === 'received' ? 'Post a Task' : 'Browse Tasks'}
          </button>
        </div>
      ) : (
        <div className="space-y-3">
          {filtered.map(offer => (
            <OfferCard
              key={offer.id}
              offer={offer}
              perspective={tab}
              onNavigate={(path) => router.push(path)}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function OfferCard({
  offer,
  perspective,
  onNavigate,
}: {
  offer: Offer;
  perspective: 'received' | 'sent';
  onNavigate: (path: string) => void;
}) {
  const status = STATUS_STYLES[offer.status] || STATUS_STYLES.pending;
  const personName = perspective === 'received'
    ? (offer.bidder?.name || offer.bidder?.first_name || offer.bidder?.username || 'Unknown')
    : null;

  return (
    <div
      onClick={() => offer.gig?.id && onNavigate(`/app/gigs/${offer.gig.id}`)}
      className={`bg-app-surface rounded-xl border border-app-border p-4 hover:border-app-border hover:shadow-sm transition cursor-pointer ${
        offer.status === 'pending' ? 'border-l-4 border-l-amber-400' : ''
      }`}
    >
      <div className="flex items-start justify-between gap-3">
        {/* Left: info */}
        <div className="min-w-0 flex-1">
          {/* Gig title */}
          <h3 className="text-sm font-semibold text-app-text truncate">
            {offer.gig?.title || 'Unknown Gig'}
          </h3>

          {/* Person (for received offers) */}
          {perspective === 'received' && offer.bidder && (
            <div className="flex items-center gap-2 mt-1.5">
              {offer.bidder.profile_picture_url ? (
                <Image src={offer.bidder.profile_picture_url} alt="" width={20} height={20} sizes="20px" quality={75} className="w-5 h-5 rounded-full object-cover" />
              ) : (
                <div className="w-5 h-5 rounded-full bg-gray-300 text-white text-[10px] font-bold flex items-center justify-center">
                  {(personName || '?')[0].toUpperCase()}
                </div>
              )}
              {offer.bidder.username ? (
                <UserIdentityLink
                  userId={offer.bidder.id}
                  username={offer.bidder.username}
                  displayName={personName || offer.bidder.username}
                  avatarUrl={offer.bidder.profile_picture_url || null}
                  city={offer.bidder.city || null}
                  state={offer.bidder.state || null}
                  textClassName="text-xs text-app-text-secondary hover:underline"
                />
              ) : (
                <span className="text-xs text-app-text-secondary">
                  {personName}
                  {offer.bidder.city && ` · ${offer.bidder.city}`}
                </span>
              )}
            </div>
          )}

          {/* Message */}
          {offer.message && (
            <p className="text-xs text-app-text-secondary mt-1.5 line-clamp-2">&quot;{offer.message}&quot;</p>
          )}

          {/* Time */}
          <p className="text-[10px] text-app-text-muted mt-2">{timeAgo(offer.created_at)}</p>
        </div>

        {/* Right: amount + status */}
        <div className="text-right flex-shrink-0">
          <div className="text-lg font-bold text-app-text">${offer.bid_amount}</div>
          {offer.gig?.price && offer.bid_amount !== offer.gig.price && (
            <div className="text-[10px] text-app-text-muted line-through">${offer.gig.price} asked</div>
          )}
          <span className={`inline-block mt-1.5 px-2 py-0.5 text-[10px] font-semibold rounded-full ${status.bg} ${status.text}`}>
            {status.label}
          </span>
        </div>
      </div>
    </div>
  );
}
