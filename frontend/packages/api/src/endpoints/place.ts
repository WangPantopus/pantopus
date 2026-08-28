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

import { get, put } from '../client';
import type {
  PlaceIntelligence,
  PlaceDensityBucket,
  PlaceGroup,
  PlaceBand,
  PlaceSectionId,
} from '@pantopus/types';

// ─── Public preview (T0) — the anonymous one-shot demonstration ───
// NOTE: the preview endpoint does NOT return the full PlaceIntelligence
// envelope. It returns a deliberately small, sanitized shape (the §4
// anti-leak rule): only the free Band-A subset live (flood, density
// bucket, area teaser) plus *locked descriptors* for everything
// recurring/exact, so the client can render the locked cards + soft
// wall. This mirrors backend/routes/public.js (GET /api/public/place).

/**
 * `could_not_place` and `unsupported_region` are DIFFERENT answers, and
 * a client that renders them alike tells a US resident during a geocoder
 * outage that the product is not for them. Only `unsupported_region`
 * means the address resolved outside US coverage.
 */
export type PlacePreviewStatus = 'ready' | 'partial' | 'could_not_place' | 'unsupported_region';

export interface PlacePreviewFlood {
  status: 'ready' | 'unavailable';
  zone?: string;
  description?: string | null;
  source: string;
}

export interface PlacePreviewDensity {
  status: 'ready';
  /** k-anon bucket only — never a count (§4.1). */
  bucket: PlaceDensityBucket;
  label: string;
  source: string;
}

export interface PlacePreviewArea {
  status: 'ready' | 'unavailable';
  median_year_built?: number | null;
  median_home_value?: number | null;
  note: string;
  source: string;
}

/**
 * The preview's headline dollar figure (Wave 4). A real, free, public
 * benchmark for the AREA — an NFIP tract premium band or a HUD county
 * fair market rent — never a quote, never "your home".
 *
 * `money_lead: null` means no figure was genuinely available. The tiles
 * then carry the page exactly as they did before: NEVER synthesize a
 * number client-side, and never leave a placeholder where one would be.
 */
export interface PlacePreviewMoneyLead {
  kind: 'flood_premium' | 'rent_band';
  /** Server-composed sentence with the figure already in it. */
  headline: string;
  /** What the figure is drawn from, and what it is not. */
  detail: string;
  low: number;
  high: number;
  /** The geography the figure describes, e.g. "census tract", "county". */
  scope: string;
  source: string;
}

/** A gated section descriptor — drives a LockedCard + the soft wall. */
export interface PlacePreviewLockedSection {
  id: string;
  group: PlaceGroup;
  title: string;
  band: PlaceBand;
  /** The tier that opens it: account = T1, claim = T3. */
  unlock: 'account' | 'claim';
  reason: string;
}

export interface PlacePreview {
  status: PlacePreviewStatus;
  tier: 'preview';
  region: 'US' | null;
  /** Present on `unsupported_region`. */
  message?: string;
  /** Sanitized area-level place identity (no exact coords). */
  place?: {
    address: string | null;
    city: string | null;
    state: string | null;
    zipcode: string | null;
  };
  /**
   * The lead figure, above the tiles. Null (or absent) when nothing
   * real was available — fall back to the tiles, do not invent one.
   */
  money_lead?: PlacePreviewMoneyLead | null;
  /** The free demonstration subset (present on ready/partial). */
  free?: {
    flood: PlacePreviewFlood;
    density: PlacePreviewDensity;
    area: PlacePreviewArea;
  };
  locked?: PlacePreviewLockedSection[];
  disclaimer?: string;
}

/**
 * The living, updating dashboard for a saved place. The viewer's tier
 * (T1–T4) and per-section access / gating are resolved server-side and
 * carried in each section envelope.
 *
 * Pass `sections` to lazy-load a subset (e.g. a detail page refreshing
 * only its own group): the response then carries just those envelopes,
 * in canonical order. Omitted ⇒ the full launch set.
 *
 * GET /api/homes/:id/intelligence[?sections=a,b,c]
 */
export async function getPlaceIntelligence(
  homeId: string,
  sections?: PlaceSectionId[],
): Promise<PlaceIntelligence> {
  const params = sections && sections.length ? { sections: sections.join(',') } : undefined;
  return get<PlaceIntelligence>(`/api/homes/${homeId}/intelligence`, params);
}

/**
 * The anonymous, address-only preview — no account required. Returns the
 * free Band-A subset live (flood, density bucket, area teaser) with
 * everything recurring or exact as a `locked` descriptor. Non-persistent
 * (no DB writes): close and reopen still hits the wall.
 *
 * GET /api/public/place?address=...
 */
export async function getPublicPlacePreview(address: string): Promise<PlacePreview> {
  return get<PlacePreview>('/api/public/place', { address });
}

/**
 * Systems Ledger — "it was replaced". Records what the household knows
 * about a system's install year; provenance ratchets, so a resident's
 * answer is never overwritten by a derived source.
 *
 * PUT /api/homes/:id/systems/:key
 */
export async function putHomeSystem(
  homeId: string,
  systemKey: string,
  installedYear: number,
): Promise<{ ok: boolean }> {
  return put<{ ok: boolean }>(`/api/homes/${homeId}/systems/${systemKey}`, { installed_year: installedYear });
}
