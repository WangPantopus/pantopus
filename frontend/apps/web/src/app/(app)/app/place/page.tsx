'use client';

// ============================================================
// /app/place — the address-led Place dashboard (authed).
// Thin route: the PlaceDashboard container owns fetching, the auth
// gate, and the page states.
// ============================================================

import PlaceDashboard from '@/components/place/PlaceDashboard';

export default function PlacePage() {
  return <PlaceDashboard />;
}
