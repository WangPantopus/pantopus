// T6.3f / P14 — My listings (web). Refactored onto `<ListOfRowsShell />`
// so the iOS / Android tabbed-roster has parity on web. Three tabs map
// the seller-side funnel: Active (live + pending pickup), Sold,
// Drafts. Per-row quick-status / edit actions move onto the listing
// detail page since the new shell shows a chevron-only trailing on web.
//
// Backend: `GET /api/listings/me` — `backend/routes/listings.js:1058`.

'use client';

import { useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Camera, CheckCircle2, Circle, Clock, Eye, File, HandCoins, Pencil } from 'lucide-react';
import * as api from '@pantopus/api';
import { getAuthToken } from '@pantopus/api';
import type { Listing } from '@pantopus/types';
import ListOfRowsShell from '@/components/list-of-rows/ListOfRowsShell';
import type {
  ListOfRowsState,
  ListOfRowsTab,
  RowChip,
  RowModel,
} from '@/components/list-of-rows/types';

type TabId = 'active' | 'sold' | 'drafts';

const TAB_STATUSES: Record<TabId, ReadonlyArray<string>> = {
  active: ['active', 'pending_pickup'],
  sold: ['sold'],
  drafts: ['draft'],
};

const TAB_LABELS: Record<TabId, string> = {
  active: 'Active',
  sold: 'Sold',
  drafts: 'Drafts',
};

function formatRelative(iso?: string | null): string | null {
  if (!iso) return null;
  const t = Date.parse(iso);
  if (Number.isNaN(t)) return null;
  const sec = Math.floor((Date.now() - t) / 1000);
  if (sec < 60) return 'now';
  if (sec < 3600) return `${Math.floor(sec / 60)}m`;
  if (sec < 86_400) return `${Math.floor(sec / 3600)}h`;
  if (sec < 604_800) return `${Math.floor(sec / 86_400)}d`;
  if (sec < 2_419_200) return `${Math.floor(sec / 604_800)}w`;
  return `${Math.max(1, Math.floor(sec / 2_628_000))}mo`;
}

function formatPrice(price: number | string | null | undefined, isFree?: boolean | null): string | null {
  if (isFree) return 'Free';
  if (price == null) return null;
  const n = typeof price === 'string' ? Number(price) : price;
  if (Number.isNaN(n)) return null;
  const opts: Intl.NumberFormatOptions = {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: n % 1 === 0 ? 0 : 2,
  };
  return new Intl.NumberFormat('en-US', opts).format(n);
}

function statusChip(status: string): RowChip {
  switch (status) {
    case 'active':
      return { text: 'Active', icon: Circle, tint: { kind: 'status', variant: 'success' } };
    case 'pending_pickup':
      return { text: 'Pickup pending', icon: Clock, tint: { kind: 'status', variant: 'warning' } };
    case 'sold':
      return { text: 'Sold', icon: CheckCircle2, tint: { kind: 'status', variant: 'success' } };
    case 'archived':
      return { text: 'Archived', icon: File, tint: { kind: 'status', variant: 'neutral' } };
    case 'draft':
      return { text: 'Draft', icon: Pencil, tint: { kind: 'status', variant: 'info' } };
    default:
      return {
        text: status.charAt(0).toUpperCase() + status.slice(1),
        tint: { kind: 'status', variant: 'neutral' },
      };
  }
}

export default function MyListingsPage() {
  const router = useRouter();
  const [listings, setListings] = useState<Listing[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedTab, setSelectedTab] = useState<TabId>('active');

  useEffect(() => {
    const token = getAuthToken();
    if (!token) {
      router.push('/login');
      return;
    }
    setLoading(true);
    setError('');
    api.listings
      .getMyListings({ limit: 100 })
      .then((res) => {
        const data = (res as Record<string, unknown>)?.listings as Listing[] | undefined;
        setListings(data ?? []);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Failed to load listings');
      })
      .finally(() => setLoading(false));
  }, [router]);

  const tabs = useMemo<ListOfRowsTab[]>(() => {
    return (Object.keys(TAB_LABELS) as TabId[]).map((id) => ({
      id,
      label: TAB_LABELS[id],
      count: listings.filter((l) => TAB_STATUSES[id].includes(l.status || '')).length,
    }));
  }, [listings]);

  const state = useMemo<ListOfRowsState>(() => {
    if (loading) return { kind: 'loading' };
    if (error) return { kind: 'error', message: error };

    const filtered = listings.filter((l) =>
      TAB_STATUSES[selectedTab].includes(l.status || ''),
    );
    if (filtered.length === 0) {
      return {
        kind: 'empty',
        config:
          selectedTab === 'active'
            ? {
                icon: Camera,
                headline: 'No active listings',
                subcopy: 'Post your first item to start hearing from neighbors.',
                ctaTitle: 'List something',
                onCta: () => router.push('/app/marketplace?create=true'),
              }
            : selectedTab === 'sold'
              ? {
                  icon: CheckCircle2,
                  headline: 'Nothing sold yet',
                  subcopy: 'Items move here automatically once you mark them sold.',
                }
              : {
                  icon: File,
                  headline: 'No drafts',
                  subcopy: 'Saved drafts will appear here so you can finish them later.',
                },
      };
    }

    const rows: RowModel[] = filtered.map((l) => {
      const title = l.title || 'Untitled listing';
      const price = formatPrice(l.price, l.is_free);
      const ago = formatRelative(l.created_at);
      const subtitle = [price, ago].filter(Boolean).join(' · ');
      const views = (l as { view_count?: number }).view_count ?? 0;
      const offers = (l as { active_offer_count?: number }).active_offer_count ?? 0;
      const photo = l.media_urls?.[0];
      return {
        id: l.id,
        title,
        subtitle: subtitle || null,
        template: 'fileChevron',
        leading: {
          kind: 'thumbnail',
          image: photo
            ? {
                kind: 'url',
                url: photo,
                fallback: Camera,
                gradient: { start: '#f0f9ff', end: '#e0f2fe' },
              }
            : {
                kind: 'icon',
                icon: Camera,
                gradient: { start: '#f0f9ff', end: '#e0f2fe' },
              },
          size: 'large',
        },
        trailing: { kind: 'chevron' },
        chips: [
          {
            text: `${views} ${views === 1 ? 'view' : 'views'}`,
            icon: Eye,
            tint: { kind: 'status', variant: 'neutral' },
          },
          {
            text: `${offers} ${offers === 1 ? 'offer' : 'offers'}`,
            icon: HandCoins,
            tint:
              offers > 0
                ? { kind: 'status', variant: 'info' }
                : { kind: 'status', variant: 'neutral' },
          },
          statusChip(l.status || 'active'),
        ],
        onTap: () => router.push(`/app/marketplace/${l.id}`),
      };
    });
    return { kind: 'loaded', sections: [{ id: `my-listings-${selectedTab}`, rows }] };
  }, [loading, error, listings, selectedTab, router]);

  return (
    <ListOfRowsShell
      title="My listings"
      state={state}
      tabs={tabs}
      selectedTab={selectedTab}
      onTabChange={(id) => setSelectedTab(id as TabId)}
      fab={{
        icon: Camera,
        accessibilityLabel: 'List something',
        variant: { kind: 'canonicalCreate' },
        onClick: () => router.push('/app/marketplace?create=true'),
      }}
    />
  );
}
