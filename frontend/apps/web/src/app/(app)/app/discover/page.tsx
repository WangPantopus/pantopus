'use client';

// T5.4.2 — Discover businesses (web). The route stays at
// `(app)/app/discover` per the buildout plan's F6 resolution; the
// page is reskinned to render the shared list-of-rows shell with a
// category chip strip + grouped sections, matching the iOS / Android
// implementations.
//
// The legacy rich map/list page is still reachable via `(app)/app/map`
// for power-user workflows (see `frontend/apps/web/src/app/(app)/app/map/page.tsx`).

import DiscoverBusinessesScreen from '@/components/discover-businesses/DiscoverBusinessesScreen';

export default function DiscoverBusinessesPage() {
  return <DiscoverBusinessesScreen />;
}
