// T6.6a (P24) — Shared type contract for the web MapListHybridShell.
//
// Mirrors the iOS `MapListHybridContent.swift` and the Android
// `MapListHybridContent.kt` so the pin model + detent semantics stay
// in lock-step across platforms.

/**
 * Three snap stops for the map+list hybrid sheet. Pixel heights match
 * the iOS / Android contract (160 / 296 / 518 pt) — on mobile widths
 * the same numbers translate to viewport pixels; on desktop the shell
 * caps the sheet width and keeps the heights honest.
 */
export type MapListHybridDetent = 'collapsed' | 'standard' | 'expanded';

export const MAP_LIST_HYBRID_DETENT_HEIGHTS: Record<MapListHybridDetent, number> = {
  collapsed: 160,
  standard: 296,
  expanded: 518,
};

export const MAP_LIST_HYBRID_DETENT_ORDER: readonly MapListHybridDetent[] = [
  'collapsed',
  'standard',
  'expanded',
];

/** One pin on the map. Coordinates are raw lat/lon doubles. */
export interface MapPin {
  id: string;
  latitude: number;
  longitude: number;
  /** CSS color value (e.g. `#EA580C`). */
  color: string;
  state?: MapPinState;
}

export type MapPinState = 'confirmed' | 'pending';

export interface MapAnchor {
  latitude: number;
  longitude: number;
}

/**
 * Pure resolver for the next detent after a drag release. Mirrors the
 * iOS / Android resolvers so the same gesture lands at the same stop
 * on every platform.
 *
 * @param current      Detent at drag start
 * @param velocity     Pixels-per-second (positive = downward / shrink)
 * @param displacedPx  Live sheet height the moment the drag released
 */
export function resolveMapListHybridDetent(
  current: MapListHybridDetent,
  velocity: number,
  displacedPx: number,
): MapListHybridDetent {
  const VELOCITY_THRESHOLD = 600;

  if (velocity > VELOCITY_THRESHOLD) {
    if (current === 'expanded') return 'standard';
    if (current === 'standard') return 'collapsed';
    return 'collapsed';
  }
  if (velocity < -VELOCITY_THRESHOLD) {
    if (current === 'collapsed') return 'standard';
    if (current === 'standard') return 'expanded';
    return 'expanded';
  }

  return [...MAP_LIST_HYBRID_DETENT_ORDER].sort(
    (a, b) =>
      Math.abs(MAP_LIST_HYBRID_DETENT_HEIGHTS[a] - displacedPx) -
      Math.abs(MAP_LIST_HYBRID_DETENT_HEIGHTS[b] - displacedPx),
  )[0] ?? current;
}
