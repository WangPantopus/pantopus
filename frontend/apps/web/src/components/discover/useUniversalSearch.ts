'use client';

import { useQuery } from '@tanstack/react-query';
import * as api from '@pantopus/api';
import type { SearchScope, UnifiedResult } from './discoverTypes';
import { queryKeys } from '@/lib/query-keys';

/**
 * Performs universal (non-business) search across people, businesses, tasks,
 * and listings based on the current scope and debounced query.
 *
 * Uses a composite useQuery so:
 *  - Rapid typing produces key changes → React Query cancels stale requests
 *    and only the latest query's result is applied.
 *  - The AbortSignal passed to queryFn is checked after Promise.allSettled,
 *    so even if the in-flight API calls can't be plumbed through to axios,
 *    stale results never reach the component.
 */
export function useUniversalSearch(
  debouncedQuery: string,
  scope: SearchScope,
  showUniversalUI: boolean,
) {
  const trimmed = debouncedQuery.trim();
  const enabled = showUniversalUI && trimmed.length >= 2;

  const query = useQuery<UnifiedResult[]>({
    queryKey: queryKeys.discover('universal', { query: trimmed, scope }),
    enabled,
    staleTime: 30_000,
    queryFn: async ({ signal }) => {
      const unified: UnifiedResult[] = [];
      const limit = scope === 'all' ? 5 : 20;

      const shouldSearchPeople = scope === 'all' || scope === 'people';
      const shouldSearchBiz = scope === 'all';
      const shouldSearchTasks = scope === 'all' || scope === 'tasks';
      const shouldSearchListings = scope === 'all' || scope === 'listings';

      const promises: Promise<void>[] = [];

      if (shouldSearchPeople) {
        promises.push(
          api.users.searchUsers(trimmed, { type: 'people', limit }).then((res: Record<string, unknown>) => {
            for (const u of (res?.users || []) as Record<string, unknown>[]) {
              const name = u.name || [u.first_name, u.last_name].filter(Boolean).join(' ') || u.username;
              unified.push({
                id: u.id,
                type: 'person',
                title: name,
                subtitle: u.username ? `@${u.username}` : undefined,
                meta: [u.city, u.state].filter(Boolean).join(', ') || undefined,
                imageUrl: u.profile_picture_url || null,
                href: `/${u.username || u.id}`,
              });
            }
          }).catch(() => {}),
        );
      }

      if (shouldSearchBiz) {
        promises.push(
          api.businesses.discoverBusinesses({ q: trimmed, limit }).then((res: Record<string, unknown>) => {
            for (const b of (res?.businesses || []) as Record<string, unknown>[]) {
              unified.push({
                id: b.id,
                type: 'business',
                title: b.name || b.username || 'Business',
                subtitle: b.business_type || undefined,
                meta: [b.city, b.state].filter(Boolean).join(', ') || undefined,
                imageUrl: b.profile_picture_url || null,
                href: b.username ? `/business/${b.username}` : '/app/discover',
              });
            }
          }).catch(() => {}),
        );
      }

      if (shouldSearchTasks) {
        promises.push(
          api.gigs.searchGigs(trimmed, { limit } as Record<string, unknown>).then((res: Record<string, unknown>) => {
            for (const g of (res?.gigs || []) as Record<string, unknown>[]) {
              unified.push({
                id: g.id,
                type: 'task',
                title: g.title || 'Untitled Task',
                subtitle: g.category || undefined,
                meta: g.price ? `$${Number(g.price).toFixed(0)}` : undefined,
                imageUrl: g.poster_profile_picture_url || null,
                href: `/app/gigs/${g.id}`,
              });
            }
          }).catch(() => {}),
        );
      }

      if (shouldSearchListings) {
        promises.push(
          api.listings.getListings({ q: trimmed, limit } as Record<string, unknown>).then((res: Record<string, unknown>) => {
            for (const l of (res?.listings || []) as Record<string, unknown>[]) {
              unified.push({
                id: l.id,
                type: 'listing',
                title: l.title || 'Untitled Listing',
                subtitle: l.category || undefined,
                meta: l.price != null ? `$${Number(l.price).toFixed(0)}` : l.is_free ? 'Free' : undefined,
                imageUrl: (l as Record<string, unknown>).images ? ((l as Record<string, unknown>).images as Record<string, unknown>[])?.[0]?.url as string || null : null,
                href: `/app/marketplace/${l.id}`,
              });
            }
          }).catch(() => {}),
        );
      }

      await Promise.allSettled(promises);

      // Discard results from superseded queries.
      if (signal.aborted) throw new Error('aborted');
      return unified;
    },
  });

  return {
    uniResults: query.data ?? [],
    uniLoading: enabled && query.isPending,
  };
}
