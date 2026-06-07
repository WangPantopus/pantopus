// ============================================================
// PlaceDashboard — the authed container for /app/place.
//
// Resolves the resident's primary home, then fetches its
// PlaceIntelligence and hands it to the presentational view. Owns the
// page states: auth gate, shimmer skeleton (loading), error (retry),
// and the no-place empty state. Responsive single column, mobile-web
// first with a comfortable desktop max-width.
// ============================================================

'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import * as api from '@pantopus/api';
import { getAuthToken } from '@pantopus/api';
import { MapPinned } from 'lucide-react';
import { queryKeys } from '@/lib/query-keys';
import ErrorState from '@/components/ui/ErrorState';
import EmptyState from '@/components/ui/EmptyState';
import PlaceDashboardView from './PlaceDashboardView';
import PlaceDashboardSkeleton from './PlaceDashboardSkeleton';

const REDIRECT_TO = encodeURIComponent('/app/place');

// Comfortable reading column: full-width on mobile, capped on desktop.
function Shell({ children }: { children: React.ReactNode }) {
  return (
    <div className="mx-auto w-full max-w-[640px] px-4 sm:px-5 py-5 sm:py-6">{children}</div>
  );
}

export default function PlaceDashboard() {
  const router = useRouter();
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  // getAuthToken gate (middleware also guards /app/*; this is the client guard).
  useEffect(() => {
    if (mounted && !getAuthToken()) {
      router.replace(`/login?redirectTo=${REDIRECT_TO}`);
    }
  }, [mounted, router]);

  const authed = mounted && !!getAuthToken();

  // 1) Resolve the resident's primary home.
  const homeQuery = useQuery({
    queryKey: queryKeys.placePrimaryHome(),
    queryFn: async () => api.homes.getPrimaryHome(),
    enabled: authed,
    staleTime: 60_000,
  });

  const homeId = homeQuery.data?.home?.id ?? null;

  // 2) Fetch its PlaceIntelligence (dependent on the home id).
  const intelQuery = useQuery({
    queryKey: homeId ? queryKeys.placeIntelligence(homeId) : ['place', 'intelligence', 'none'],
    queryFn: async () => api.place.getPlaceIntelligence(homeId as string),
    enabled: authed && !!homeId,
    staleTime: 60_000,
  });

  // ── States ───────────────────────────────────────────────
  if (!mounted || !authed) {
    return <Shell><PlaceDashboardSkeleton /></Shell>;
  }

  if (homeQuery.isError) {
    return (
      <Shell>
        <ErrorState message="We couldn't load your place. Check your connection and try again." onRetry={() => homeQuery.refetch()} />
      </Shell>
    );
  }

  // No claimed home yet — point at adding one (the funnel claims/verifies elsewhere).
  if (homeQuery.isSuccess && !homeId) {
    return (
      <Shell>
        <EmptyState
          icon={MapPinned}
          title="You haven't added a place yet"
          description="Claim your address to see flood risk, today's air, your home's value, and your verified neighbors."
          actionLabel="Add your place"
          onAction={() => router.push('/app/homes')}
        />
      </Shell>
    );
  }

  if (homeQuery.isPending || intelQuery.isPending) {
    return <Shell><PlaceDashboardSkeleton /></Shell>;
  }

  if (intelQuery.isError || !intelQuery.data) {
    return (
      <Shell>
        <ErrorState message="We couldn't load your place. Check your connection and try again." onRetry={() => intelQuery.refetch()} />
      </Shell>
    );
  }

  return (
    <Shell>
      <PlaceDashboardView
        intelligence={intelQuery.data}
        onOpenSection={(slug) => router.push(`/app/place/${slug}`)}
      />
    </Shell>
  );
}
