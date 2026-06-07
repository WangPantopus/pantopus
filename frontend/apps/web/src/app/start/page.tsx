'use client';

// ============================================================
// /start — the signed-out Place funnel (public, not under /app).
// Hero → address → one-shot preview → wall → /register. The
// StartFunnel container owns the flow and the anti-leak gating.
// ============================================================

import StartFunnel from '@/components/place/StartFunnel';

export default function StartPage() {
  return <StartFunnel />;
}
