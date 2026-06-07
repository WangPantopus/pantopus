// ============================================================
// PLACE — address-led home intelligence (the "Place" / ProfileDashboard)
//
// Thin wrappers over the PlaceIntelligence section-envelope contract
// (@pantopus/types · placeIntelligence.ts), served by:
//   GET /api/homes/:id/intelligence — the living dashboard for a saved
//                                     / claimed / verified place (T1–T4)
//   GET /api/public/place           — the anonymous T0 one-shot preview
//
// Reuses the shared axios client. The richer AI surfaces
// (ai.getPlaceBrief / getNeighborhoodPulse) stay where the dashboard
// wants narrative copy; these wrappers carry the structured contract.
// ============================================================

import { get } from '../client';
import type { PlaceIntelligence } from '@pantopus/types';

/**
 * The T0 public preview is the same section-envelope contract served at
 * the preview tier — a one-shot, non-persistent snapshot (the anti-leak
 * rule, design doc §4). It shares PlaceIntelligence's shape, with
 * `tier: 'T0'`: the Band-A free subset comes back live while every
 * recurring / exact section comes back `locked`.
 */
export type PlacePreview = PlaceIntelligence;

/**
 * The living, updating dashboard for a saved place. The viewer's tier
 * (T1–T4) and per-section access / gating are resolved server-side and
 * carried in each section envelope.
 *
 * GET /api/homes/:id/intelligence
 */
export async function getPlaceIntelligence(homeId: string): Promise<PlaceIntelligence> {
  return get<PlaceIntelligence>(`/api/homes/${homeId}/intelligence`);
}

/**
 * The anonymous, address-only preview — no account required. Returns the
 * Band-A free subset live, with everything recurring or exact gated as a
 * `locked` section (the soft wall lives in the UI, not here).
 *
 * GET /api/public/place?address=...
 */
export async function getPublicPlacePreview(address: string): Promise<PlacePreview> {
  return get<PlacePreview>('/api/public/place', { address });
}
