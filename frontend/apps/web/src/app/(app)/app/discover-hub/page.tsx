'use client';

// T5.4.1 / P11 — Discover hub.
//
// Reskinned from the legacy "feature card grid" layout to the typed
// section list (People · Businesses · Gigs · Listings) that mirrors
// the iOS / Android Discover hub. Backend: four parallel calls to
// `GET /api/hub/discovery?filter=<type>` (extended with the
// `since=today` / `verified=true` / `freeOrWanted=true` chip filters
// in `backend/routes/hub.js:757`). See-all CTAs push to the live web
// routes per type — businesses falls through to `/app/discover`
// pending P12.

import { useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useQueries } from '@tanstack/react-query';
import * as api from '@pantopus/api';
import type { DiscoveryItem, DiscoveryFilter } from '@pantopus/api';
import {
  AlertCircle,
  BadgeCheck,
  Briefcase,
  ChevronRight,
  Compass,
  Hammer,
  Home,
  MapPin,
  Package,
  PawPrint,
  Sparkles,
  SlidersHorizontal,
  Check,
} from 'lucide-react';
import { queryKeys } from '@/lib/query-keys';

type ChipId = 'nearby' | 'new-today' | 'verified' | 'free-or-wanted';

const CHIPS: Array<{ id: ChipId; label: string; icon?: typeof MapPin }> = [
  { id: 'nearby', label: 'Nearby', icon: MapPin },
  { id: 'new-today', label: 'New today' },
  { id: 'verified', label: 'Verified', icon: BadgeCheck },
  { id: 'free-or-wanted', label: 'Free / wanted' },
];

const PER_TYPE_LIMIT = 5;

export default function DiscoverHubPage() {
  const router = useRouter();
  const [chip, setChip] = useState<ChipId>('nearby');

  const queryParams = useMemo(() => {
    const params: { since?: 'today'; verified?: boolean; freeOrWanted?: boolean } = {};
    if (chip === 'new-today') params.since = 'today';
    if (chip === 'verified') params.verified = true;
    if (chip === 'free-or-wanted') params.freeOrWanted = true;
    return params;
  }, [chip]);

  const queries = useQueries({
    queries: (['people', 'businesses', 'gigs', 'listings'] as DiscoveryFilter[]).map(
      (filter) => ({
        queryKey: queryKeys.hubDiscovery(filter, chip),
        queryFn: async () => {
          const res = await api.getDiscovery({
            filter,
            limit: PER_TYPE_LIMIT,
            ...queryParams,
          });
          return res.items;
        },
        staleTime: 30_000,
      }),
    ),
  });
  const [peopleQ, businessesQ, gigsQ, listingsQ] = queries;

  const loading = queries.every((q) => q.isPending);
  const allFailed = queries.every((q) => q.isError);
  const people = peopleQ.data ?? [];
  const businesses = businessesQ.data ?? [];
  const gigs = gigsQ.data ?? [];
  const listings = listingsQ.data ?? [];
  const allEmpty = !loading && people.length === 0 && businesses.length === 0
    && gigs.length === 0 && listings.length === 0;

  return (
    <div className="max-w-3xl mx-auto">
      {/* Top bar */}
      <header className="sticky top-0 z-10 flex items-center gap-2 px-4 py-3 bg-app-surface border-b border-app-border">
        <button
          type="button"
          onClick={() => router.back()}
          className="w-9 h-9 flex items-center justify-center text-app-text"
          aria-label="Back"
        >
          <ChevronRight className="w-5 h-5 rotate-180" />
        </button>
        <h1 className="flex-1 text-center text-base font-semibold text-app-text tracking-tight">
          Discover hub
        </h1>
        <button
          type="button"
          className="w-9 h-9 flex items-center justify-center text-app-text"
          aria-label="Filter discovery"
        >
          <SlidersHorizontal className="w-5 h-5" />
        </button>
      </header>

      {/* Chip strip */}
      <div className="bg-app-surface border-b border-app-border overflow-x-auto">
        <div className="flex gap-1.5 px-4 py-2.5 w-max">
          {CHIPS.map((c) => {
            const active = chip === c.id;
            const Icon = c.icon;
            return (
              <button
                key={c.id}
                type="button"
                data-testid={`chip.${c.id}`}
                aria-pressed={active}
                onClick={() => setChip(c.id)}
                className={
                  'inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs whitespace-nowrap border ' +
                  (active
                    ? 'bg-primary-600 text-white border-primary-600 font-semibold'
                    : 'bg-app-surface text-app-text-secondary border-app-border font-medium')
                }
              >
                {Icon && <Icon className="w-3 h-3" />}
                {c.label}
              </button>
            );
          })}
        </div>
      </div>

      {/* Body */}
      <div className="px-4 py-4 pb-12">
        {loading ? (
          <SkeletonSections />
        ) : allFailed ? (
          <ErrorState onRetry={() => queries.forEach((q) => q.refetch())} />
        ) : allEmpty ? (
          <EmptyState />
        ) : (
          <>
            {people.length > 0 && (
              <SectionCard
                label="People"
                count={people.length}
                onSeeAll={() => router.push('/app/connections')}
                items={people}
                renderRow={(item) => (
                  <PersonRow
                    key={item.id}
                    item={item}
                    onTap={() => router.push(`/app/profile/${item.id}`)}
                  />
                )}
              />
            )}
            {businesses.length > 0 && (
              <SectionCard
                label="Businesses"
                count={businesses.length}
                onSeeAll={() => router.push('/app/discover')}
                items={businesses}
                renderRow={(item) => (
                  <BusinessRow
                    key={item.id}
                    item={item}
                    onTap={() => router.push(`/app/businesses/${item.id}`)}
                  />
                )}
              />
            )}
            {gigs.length > 0 && (
              <SectionCard
                label="Gigs"
                count={gigs.length}
                onSeeAll={() => router.push('/app/gigs')}
                items={gigs}
                renderRow={(item) => (
                  <GigRow
                    key={item.id}
                    item={item}
                    onTap={() => router.push(`/app/gigs/${item.id}`)}
                  />
                )}
              />
            )}
            {listings.length > 0 && (
              <SectionCard
                label="Listings"
                count={listings.length}
                onSeeAll={() => router.push('/app/marketplace')}
                items={listings}
                renderRow={(item) => (
                  <ListingRow
                    key={item.id}
                    item={item}
                    onTap={() => router.push(`/app/marketplace/${item.id}`)}
                  />
                )}
              />
            )}
          </>
        )}
      </div>
    </div>
  );
}

// ─── Section card ────────────────────────────────────────────────

function SectionCard({
  label,
  count,
  onSeeAll,
  items,
  renderRow,
}: {
  label: string;
  count: number;
  onSeeAll: () => void;
  items: DiscoveryItem[];
  renderRow: (item: DiscoveryItem) => React.ReactNode;
}) {
  return (
    <section className="mb-4" data-testid={`discoverHub.section.${label.toLowerCase()}`}>
      <header className="flex items-baseline gap-2 px-1 pt-3 pb-2.5">
        <span className="text-[11px] font-bold tracking-widest uppercase text-app-text">
          {label}
        </span>
        <span className="text-[11px] text-app-text-muted">({count})</span>
        <span className="flex-1" />
        <button
          type="button"
          onClick={onSeeAll}
          className="inline-flex items-center gap-0.5 text-[11.5px] font-semibold text-primary-600"
          aria-label={`See all ${label}`}
        >
          See all
          <ChevronRight className="w-3 h-3" />
        </button>
      </header>
      <div className="bg-app-surface border border-app-border rounded-2xl shadow-sm overflow-hidden">
        {items.map((item, i) => (
          <div key={item.id}>
            {renderRow(item)}
            {i < items.length - 1 && <div className="h-px bg-app-border" />}
          </div>
        ))}
      </div>
    </section>
  );
}

// ─── Per-type rows ───────────────────────────────────────────────

function PersonRow({ item, onTap }: { item: DiscoveryItem; onTap: () => void }) {
  const verified = item.verified === true;
  return (
    <button
      type="button"
      onClick={onTap}
      className="w-full flex items-center gap-2.5 px-3.5 py-2.5 hover:bg-app-hover transition text-left"
    >
      <div className="relative flex-shrink-0">
        <div
          className="w-9 h-9 rounded-full flex items-center justify-center text-white font-bold text-xs"
          style={{ background: `linear-gradient(135deg, ${toneFor(item.id, 'start')}, ${toneFor(item.id, 'end')})` }}
        >
          {initials(item.title)}
        </div>
        {verified && (
          <div className="absolute -right-0.5 -bottom-0.5 w-3.5 h-3.5 rounded-full bg-green-600 border-2 border-app-surface flex items-center justify-center">
            <Check className="w-2 h-2 text-white" strokeWidth={4} />
          </div>
        )}
      </div>
      <div className="flex-1 min-w-0">
        <div className="text-[13px] font-semibold text-app-text truncate">{item.title}</div>
        {item.subtitle ? (
          <div className="text-[11px] text-app-text-secondary mt-0.5">{item.subtitle}</div>
        ) : item.meta ? (
          <div className="text-[11px] text-app-text-secondary mt-0.5">{item.meta}</div>
        ) : null}
      </div>
      <ChevronRight className="w-3.5 h-3.5 text-app-text-muted flex-shrink-0" />
    </button>
  );
}

function BusinessRow({ item, onTap }: { item: DiscoveryItem; onTap: () => void }) {
  const Icon = iconForBusinessCategory(item.category ?? null);
  return (
    <button
      type="button"
      onClick={onTap}
      className="w-full flex items-center gap-2.5 px-3.5 py-2.5 hover:bg-app-hover transition text-left"
    >
      <div
        className="w-9 h-9 rounded-lg flex items-center justify-center text-white flex-shrink-0"
        style={{ background: `linear-gradient(135deg, ${toneFor(item.id, 'start')}, ${toneFor(item.id, 'end')})` }}
      >
        <Icon className="w-4 h-4" />
      </div>
      <div className="flex-1 min-w-0">
        <div className="text-[13px] font-semibold text-app-text truncate">{item.title}</div>
        {item.subtitle ? (
          <div className="text-[11px] text-app-text-secondary mt-0.5">{item.subtitle}</div>
        ) : item.meta ? (
          <div className="text-[11px] text-app-text-secondary mt-0.5">{item.meta}</div>
        ) : null}
      </div>
      <ChevronRight className="w-3.5 h-3.5 text-app-text-muted flex-shrink-0" />
    </button>
  );
}

function GigRow({ item, onTap }: { item: DiscoveryItem; onTap: () => void }) {
  const Icon = iconForGigCategory(item.category ?? null);
  return (
    <button
      type="button"
      onClick={onTap}
      className="w-full flex items-center gap-2.5 px-3.5 py-2.5 hover:bg-app-hover transition text-left"
    >
      <div
        className="w-9 h-9 rounded-lg flex items-center justify-center text-white flex-shrink-0"
        style={{ background: `linear-gradient(135deg, ${toneFor(item.id, 'start')}, ${toneFor(item.id, 'end')})` }}
      >
        <Icon className="w-4 h-4" />
      </div>
      <div className="flex-1 min-w-0">
        <div className="text-[13px] font-semibold text-app-text truncate">{item.title}</div>
        {item.subtitle ? (
          <div className="text-[11px] text-app-text-secondary mt-0.5">{item.subtitle}</div>
        ) : item.meta ? (
          <div className="text-[11px] text-app-text-secondary mt-0.5">{item.meta}</div>
        ) : null}
      </div>
      {item.price && (
        <span className="text-[13px] font-bold text-app-text flex-shrink-0">{item.price}</span>
      )}
      <ChevronRight className="w-3.5 h-3.5 text-app-text-muted flex-shrink-0" />
    </button>
  );
}

function ListingRow({ item, onTap }: { item: DiscoveryItem; onTap: () => void }) {
  const Icon = iconForListingCategory(item.category ?? null);
  return (
    <button
      type="button"
      onClick={onTap}
      className="w-full flex items-center gap-2.5 px-3.5 py-2.5 hover:bg-app-hover transition text-left"
    >
      <div
        className="w-11 h-11 rounded-lg flex items-center justify-center text-white flex-shrink-0 overflow-hidden"
        style={{ background: `linear-gradient(135deg, ${toneFor(item.id, 'start')}, ${toneFor(item.id, 'end')})` }}
      >
        {item.avatarUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={item.avatarUrl} alt="" className="w-full h-full object-cover" />
        ) : (
          <Icon className="w-5 h-5" />
        )}
      </div>
      <div className="flex-1 min-w-0">
        <div className="text-[13px] font-semibold text-app-text truncate">{item.title}</div>
        {item.subtitle ? (
          <div className="text-[11px] text-app-text-secondary mt-0.5">{item.subtitle}</div>
        ) : item.meta ? (
          <div className="text-[11px] text-app-text-secondary mt-0.5">{item.meta}</div>
        ) : null}
      </div>
      {item.price && (
        <span className="text-[13px] font-bold text-app-text flex-shrink-0">{item.price}</span>
      )}
      <ChevronRight className="w-3.5 h-3.5 text-app-text-muted flex-shrink-0" />
    </button>
  );
}

// ─── States ─────────────────────────────────────────────────────

function EmptyState() {
  return (
    <div className="flex flex-col items-center justify-center text-center py-20 px-8">
      <div className="w-[72px] h-[72px] rounded-full bg-primary-50 text-primary-600 flex items-center justify-center mb-4">
        <Compass className="w-9 h-9" strokeWidth={1.8} />
      </div>
      <div className="text-xl font-semibold text-app-text mb-2 max-w-[280px]">
        Nothing to discover yet
      </div>
      <div className="text-[13px] text-app-text-secondary leading-snug max-w-[270px]">
        You&rsquo;re early to this block. People, businesses, gigs, and listings will
        appear here as neighbors verify and join. Check back soon.
      </div>
    </div>
  );
}

function ErrorState({ onRetry }: { onRetry: () => void }) {
  return (
    <div className="flex flex-col items-center justify-center text-center py-20 px-8">
      <AlertCircle className="w-10 h-10 text-red-500 mb-3" />
      <div className="text-base font-semibold text-app-text mb-1">
        Couldn&rsquo;t load discovery
      </div>
      <div className="text-sm text-app-text-secondary mb-4">Try again in a moment.</div>
      <button
        type="button"
        onClick={onRetry}
        className="px-4 py-2 rounded-lg bg-primary-600 text-white font-semibold text-sm"
      >
        Try again
      </button>
    </div>
  );
}

function SkeletonSections() {
  return (
    <div className="space-y-4">
      {[0, 1, 2, 3].map((i) => (
        <div key={i} className="bg-app-surface border border-app-border rounded-2xl overflow-hidden">
          {[0, 1, 2].map((j) => (
            <div key={j} className="flex items-center gap-2.5 px-3.5 py-2.5">
              <div className="w-9 h-9 rounded-full bg-app-border-subtle animate-pulse" />
              <div className="flex-1">
                <div className="h-3 w-2/3 bg-app-border-subtle animate-pulse rounded" />
                <div className="h-2.5 w-1/3 bg-app-border-subtle animate-pulse rounded mt-2" />
              </div>
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}

// ─── Helpers ────────────────────────────────────────────────────

function initials(name: string): string {
  const parts = name.split(/\s+/).filter(Boolean).slice(0, 2);
  if (parts.length === 0) return '?';
  return parts.map((p) => p[0]?.toUpperCase() ?? '').join('');
}

const TONES: Array<[string, string]> = [
  ['#0ea5e9', '#0369a1'],
  ['#16a34a', '#15803d'],
  ['#fb923c', '#c2410c'],
  ['#f87171', '#b91c1c'],
  ['#a78bfa', '#6d28d9'],
  ['#9ca3af', '#374151'],
];

function toneFor(id: string, end: 'start' | 'end'): string {
  let hash = 0;
  for (let i = 0; i < id.length; i += 1) hash += id.charCodeAt(i);
  const tone = TONES[Math.abs(hash) % TONES.length];
  return tone[end === 'start' ? 0 : 1];
}

function iconForBusinessCategory(category: string | null) {
  const key = (category ?? '').toLowerCase();
  if (key.includes('handy') || key.includes('repair') || key.includes('contract')) return Hammer;
  if (key.includes('pet')) return PawPrint;
  if (key.includes('clean')) return Sparkles;
  if (key.includes('home')) return Home;
  return Briefcase;
}

function iconForGigCategory(category: string | null) {
  const key = (category ?? '').toLowerCase();
  if (key.includes('clean')) return Sparkles;
  if (key.includes('handy') || key.includes('assemble') || key.includes('repair')) return Hammer;
  if (key.includes('pet')) return PawPrint;
  if (key.includes('delivery') || key.includes('pickup')) return Package;
  return Briefcase;
}

function iconForListingCategory(category: string | null) {
  const key = (category ?? '').toLowerCase();
  if (key.includes('furniture') || key.includes('home')) return Home;
  return Package;
}
