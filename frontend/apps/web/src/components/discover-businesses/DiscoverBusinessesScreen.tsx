'use client';

// T5.4.2 — Discover businesses (web). Mirrors iOS
// `DiscoverBusinessesView` + Android `DiscoverBusinessesScreen` on the
// shared `ListOfRowsShell` archetype.
//
// Layout:
//   - Top bar: back chevron + "Discover businesses" + trailing
//     `sliders-horizontal` filter action.
//   - Search bar above the chip strip (existing `searchBar` slot).
//   - Horizontal category chip strip ("All" + 8 categories).
//   - Body: when chip = "All", results group into multiple category
//     sections rendered as cards. When a specific chip is selected,
//     the list collapses to that single section.
//   - Each row: 40px gradient category icon leading + name title +
//     meta subtitle (description · open-now · distance) + chevron.
//   - No FAB (matches the visual frame).
//
// Backend: `GET /api/businesses/search` —
// `backend/routes/businessDiscovery.js:436`.

import { useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { SlidersHorizontal, Compass, MapPin } from 'lucide-react';
import * as api from '@pantopus/api';
import type { DiscoverySearchResult } from '@pantopus/api';
import ListOfRowsShell from '@/components/list-of-rows/ListOfRowsShell';
import type {
  ListOfRowsState,
  RowModel,
  RowSection,
} from '@/components/list-of-rows/types';
import useViewerHome from '@/hooks/useViewerHome';
import {
  DISCOVER_BUSINESSES_CHIP_ORDER,
  DiscoverBusinessesChip,
  categorySpec,
  primaryCategoryKey,
} from './categories';

const PAGE_SIZE = 50;

export default function DiscoverBusinessesScreen() {
  const router = useRouter();
  const { viewerHome, loading: homeLoading, hasHome } = useViewerHome();

  const [selectedChip, setSelectedChip] = useState<string>(DiscoverBusinessesChip.ALL);
  const [searchText, setSearchText] = useState<string>('');
  const [debouncedSearch, setDebouncedSearch] = useState<string>('');

  // Debounce search input 300ms, matching the mobile VMs.
  useEffect(() => {
    const t = setTimeout(() => setDebouncedSearch(searchText), 300);
    return () => clearTimeout(t);
  }, [searchText]);

  const categoriesParam =
    selectedChip === DiscoverBusinessesChip.ALL ? undefined : selectedChip;
  const queryParam = debouncedSearch.trim() ? debouncedSearch.trim() : undefined;

  const businessesQuery = useQuery({
    queryKey: [
      'discover-businesses',
      viewerHome?.homeId,
      categoriesParam ?? null,
      queryParam ?? null,
    ],
    queryFn: async () => {
      // The backend resolves the viewer's home when lat/lng are absent —
      // but the TypeScript signature on `searchNearbyBusinesses` requires
      // them. Pass the resolved home centre when available; otherwise
      // bypass the typed wrapper and let the backend respond with 400 so
      // we can render the no-location empty state.
      if (!viewerHome) {
        throw new Error('LOCATION_REQUIRED');
      }
      return api.businesses.searchNearbyBusinesses({
        lat: viewerHome.lat,
        lng: viewerHome.lng,
        radius_miles: 5,
        page: 1,
        page_size: PAGE_SIZE,
        sort: 'relevance',
        viewer_home_id: viewerHome.homeId,
        categories: categoriesParam,
        q: queryParam,
      });
    },
    enabled: !homeLoading && hasHome,
    staleTime: 30_000,
  });

  // Build the shell state.
  const state = useMemo<ListOfRowsState>(() => {
    if (homeLoading) return { kind: 'loading' };

    if (!hasHome) {
      return {
        kind: 'empty',
        config: {
          icon: MapPin,
          headline: 'Set a home address',
          subcopy:
            'We need a verified home address to surface businesses near you. Add one in your profile and they’ll appear here.',
          ctaTitle: 'Widen radius',
          onCta: () => router.push('/app/profile'),
        },
      };
    }

    if (businessesQuery.isLoading) return { kind: 'loading' };

    if (businessesQuery.isError) {
      return {
        kind: 'error',
        message: "Couldn't load businesses. Try again.",
      };
    }

    const results = businessesQuery.data?.results ?? [];

    if (results.length === 0) {
      return {
        kind: 'empty',
        config: {
          icon: Compass,
          headline: 'No verified businesses nearby yet',
          subcopy:
            'Widen your search radius, or invite a business you trust on the block. They’ll show up here once they verify their address.',
          ctaTitle: 'Invite a business',
          onCta: () => router.push('/app/profile'),
        },
      };
    }

    if (selectedChip !== DiscoverBusinessesChip.ALL) {
      const spec = categorySpec(selectedChip);
      const rows = results.map((r) => rowForBusiness(r, selectedChip, router));
      const section: RowSection = {
        id: selectedChip,
        header: spec.label,
        rows,
        count: rows.length,
        style: 'card',
      };
      return { kind: 'loaded', sections: [section], hasMore: false };
    }

    // Group results by primary category in chip-strip order.
    const grouped = new Map<string, DiscoverySearchResult[]>();
    for (const r of results) {
      const key = primaryCategoryKey(r.categories);
      if (!grouped.has(key)) grouped.set(key, []);
      grouped.get(key)!.push(r);
    }
    const chipOrder = DISCOVER_BUSINESSES_CHIP_ORDER;
    const sortedKeys = [...grouped.keys()].sort((a, b) => {
      const aIdx = chipOrder.indexOf(a);
      const bIdx = chipOrder.indexOf(b);
      const aRank = aIdx === -1 ? Number.MAX_SAFE_INTEGER : aIdx;
      const bRank = bIdx === -1 ? Number.MAX_SAFE_INTEGER : bIdx;
      return aRank - bRank;
    });
    const sections: RowSection[] = sortedKeys.map((key) => {
      const items = grouped.get(key)!;
      const spec = categorySpec(key);
      return {
        id: key,
        header: spec.label,
        rows: items.map((r) => rowForBusiness(r, key, router)),
        count: items.length,
        style: 'card',
      };
    });
    return { kind: 'loaded', sections, hasMore: false };
  }, [
    homeLoading,
    hasHome,
    businessesQuery.isLoading,
    businessesQuery.isError,
    businessesQuery.data,
    selectedChip,
    router,
  ]);

  return (
    <ListOfRowsShell
      title="Discover businesses"
      state={state}
      onRefresh={() => businessesQuery.refetch()}
      searchBar={{
        placeholder: 'Search businesses or services',
        value: searchText,
        onChange: (next) => setSearchText(next),
      }}
      chipStrip={{
        chips: DISCOVER_BUSINESSES_CHIP_ORDER.map((id) => ({
          id,
          label: categorySpec(id).label,
        })),
        selectedId: selectedChip,
        onSelect: (id) => setSelectedChip(id),
      }}
      topBarAction={{
        icon: SlidersHorizontal,
        accessibilityLabel: 'Filter discovery',
        onClick: () => router.push('/app/discover/filters'),
      }}
    />
  );
}

// ─── Row mapping ───────────────────────────────────────────────

function rowForBusiness(
  item: DiscoverySearchResult,
  categoryKey: string,
  router: ReturnType<typeof useRouter>,
): RowModel {
  const spec = categorySpec(categoryKey);
  return {
    id: `business-${item.business_user_id}`,
    title: item.name,
    subtitle: subtitleFor(item),
    template: 'fileChevron',
    leading: {
      kind: 'categoryGradientIcon',
      icon: spec.icon,
      gradient: spec.gradient,
    },
    trailing: { kind: 'chevron' },
    onTap: () =>
      router.push(
        `/app/businesses/${encodeURIComponent(item.username || item.business_user_id)}`,
      ),
  };
}

function subtitleFor(item: DiscoverySearchResult): string | undefined {
  const parts: string[] = [];
  if (item.description && item.description.trim().length > 0) {
    parts.push(item.description.trim());
  }
  if (item.is_open_now === true) parts.push('Open now');
  const distance = item.distance_miles;
  if (typeof distance === 'number' && distance > 0) {
    parts.push(formatDistance(distance));
  }
  return parts.length > 0 ? parts.join(' · ') : undefined;
}

function formatDistance(miles: number): string {
  if (miles < 0.1) return 'Nearby';
  const rounded = Math.round(miles * 10) / 10;
  return `${rounded} mi`;
}
