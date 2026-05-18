'use client';

// T6.6a (P24) — Shared archetype for map+list hybrid surfaces.
//
// Full-bleed Leaflet map underneath, five chrome slots overlaid (top
// pill, category chips, map controls), and a draggable bottom sheet
// that snaps between three detents: 'collapsed' (160px), 'standard'
// (296px), 'expanded' (518px) — per the Q9 contract documented in
// docs/t6-open-questions-decisions.md.
//
// Slots are React children passed as props so each consumer (Gigs map,
// Marketplace map mode, Discover Businesses map) supplies its own
// back-pill, category strip, locate-me / layers stack, sheet header,
// and sheet body. The shell owns: the map canvas (rendering supplied
// `pins`), the sheet shell + drag-to-snap gesture, and the layout.

import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type ReactNode,
} from 'react';
import dynamic from 'next/dynamic';
import {
  MAP_LIST_HYBRID_DETENT_HEIGHTS,
  resolveMapListHybridDetent,
  type MapAnchor,
  type MapListHybridDetent,
  type MapPin,
} from './types';

interface MapListHybridShellProps {
  pins: MapPin[];
  anchor?: MapAnchor;
  selectedPinId?: string | null;
  onPinTap?: (id: string) => void;
  detent: MapListHybridDetent;
  onDetentChange: (next: MapListHybridDetent) => void;
  topPill?: ReactNode;
  categoryChips?: ReactNode;
  mapControls?: ReactNode;
  sheetHeader?: ReactNode;
  sheetBody?: ReactNode;
}

// react-leaflet imports browser-only Leaflet APIs that break SSR. Load
// the map layer lazily so the shell can SSR the chrome while the canvas
// hydrates client-side.
const MapListHybridMapLayer = dynamic(() => import('./MapListHybridMapLayer'), {
  ssr: false,
  loading: () => (
    <div
      style={{ position: 'absolute', inset: 0, background: '#e8edf2' }}
      data-testid="mapListHybridMapSkeleton"
    />
  ),
});

export default function MapListHybridShell({
  pins,
  anchor,
  selectedPinId,
  onPinTap,
  detent,
  onDetentChange,
  topPill,
  categoryChips,
  mapControls,
  sheetHeader,
  sheetBody,
}: MapListHybridShellProps) {
  const baseHeight = MAP_LIST_HYBRID_DETENT_HEIGHTS[detent];
  const [dragOffset, setDragOffset] = useState(0);
  const dragStartY = useRef<number | null>(null);
  const dragLastY = useRef<number | null>(null);
  const dragLastTime = useRef<number | null>(null);
  const draggedDistance = useRef(0);

  const prefersReducedMotion = usePrefersReducedMotion();

  const liveHeight = Math.max(120, baseHeight + dragOffset);

  const handleGrabberPointerDown = useCallback((e: React.PointerEvent<HTMLButtonElement>) => {
    (e.currentTarget as HTMLButtonElement).setPointerCapture(e.pointerId);
    dragStartY.current = e.clientY;
    dragLastY.current = e.clientY;
    dragLastTime.current = performance.now();
    draggedDistance.current = 0;
  }, []);

  const handleGrabberPointerMove = useCallback((e: React.PointerEvent<HTMLButtonElement>) => {
    if (dragStartY.current == null) return;
    const delta = e.clientY - dragStartY.current;
    draggedDistance.current = Math.max(draggedDistance.current, Math.abs(delta));
    setDragOffset(-delta);
    dragLastY.current = e.clientY;
    dragLastTime.current = performance.now();
  }, []);

  const handleGrabberPointerUp = useCallback(
    (e: React.PointerEvent<HTMLButtonElement>) => {
      if (dragStartY.current == null) return;
      const now = performance.now();
      const lastY = dragLastY.current ?? e.clientY;
      const lastTime = dragLastTime.current ?? now;
      const dt = Math.max(16, now - lastTime);
      // px-per-ms → px-per-second so the threshold matches the iOS and
      // Android resolvers.
      const velocity = (-(e.clientY - lastY) / dt) * 1000;
      const displaced = baseHeight + dragOffset;

      // True tap (no measurable drag) → cycle forward to the next detent.
      // Otherwise resolve based on drag + velocity.
      let next: MapListHybridDetent;
      if (draggedDistance.current < 4) {
        const order: MapListHybridDetent[] = ['collapsed', 'standard', 'expanded'];
        next = order[(order.indexOf(detent) + 1) % order.length]!;
      } else {
        next = resolveMapListHybridDetent(detent, velocity, displaced);
      }

      onDetentChange(next);
      setDragOffset(0);
      dragStartY.current = null;
      dragLastY.current = null;
      dragLastTime.current = null;
      draggedDistance.current = 0;
    },
    [baseHeight, dragOffset, detent, onDetentChange],
  );

  const sheetStyle: CSSProperties = useMemo(
    () => ({
      height: liveHeight,
      transition:
        dragOffset === 0 && !prefersReducedMotion
          ? 'height 240ms cubic-bezier(0.2, 0.8, 0.2, 1)'
          : undefined,
    }),
    [liveHeight, dragOffset, prefersReducedMotion],
  );

  const mapControlsBottom = liveHeight + 14;

  return (
    <div
      style={containerStyle}
      data-testid="mapListHybridShell"
    >
      <div style={mapWrapperStyle}>
        <MapListHybridMapLayer
          pins={pins}
          anchor={anchor}
          selectedPinId={selectedPinId ?? null}
          onPinTap={onPinTap}
          reduceMotion={prefersReducedMotion}
        />
      </div>

      {topPill && (
        <div style={topPillStyle} data-testid="mapListHybridTopPill">
          {topPill}
        </div>
      )}

      {categoryChips && (
        <div style={chipsStyle} data-testid="mapListHybridChips">
          {categoryChips}
        </div>
      )}

      {mapControls && (
        <div
          style={{
            ...mapControlsStyle,
            bottom: mapControlsBottom,
          }}
          data-testid="mapListHybridMapControls"
        >
          {mapControls}
        </div>
      )}

      <div
        style={{
          ...sheetStyle,
          ...sheetBaseStyle,
        }}
        data-testid="mapListHybridSheet"
      >
        <div style={grabberWrapStyle}>
          <button
            type="button"
            data-sheet-grabber
            aria-label="Drag handle"
            style={grabberButtonStyle}
            onPointerDown={handleGrabberPointerDown}
            onPointerMove={handleGrabberPointerMove}
            onPointerUp={handleGrabberPointerUp}
            onPointerCancel={handleGrabberPointerUp}
          >
            <span style={grabberBarStyle} />
          </button>
        </div>
        {sheetHeader && <div data-testid="mapListHybridSheetHeader">{sheetHeader}</div>}
        {sheetBody && (
          <div style={sheetBodyStyle} data-testid="mapListHybridSheetBody">
            {sheetBody}
          </div>
        )}
      </div>
    </div>
  );
}

// MARK: - Reduce-motion hook

function usePrefersReducedMotion(): boolean {
  const [reduced, setReduced] = useState(false);
  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return;
    const media = window.matchMedia('(prefers-reduced-motion: reduce)');
    setReduced(media.matches);
    const handler = (event: MediaQueryListEvent) => setReduced(event.matches);
    media.addEventListener('change', handler);
    return () => media.removeEventListener('change', handler);
  }, []);
  return reduced;
}

// MARK: - Styles

const containerStyle: CSSProperties = {
  position: 'relative',
  width: '100%',
  height: '100%',
  overflow: 'hidden',
  background: 'var(--color-app-bg, #F6F7F9)',
};

const mapWrapperStyle: CSSProperties = {
  position: 'absolute',
  inset: 0,
};

const topPillStyle: CSSProperties = {
  position: 'absolute',
  top: 12,
  left: 14,
  right: 14,
  zIndex: 30,
};

const chipsStyle: CSSProperties = {
  position: 'absolute',
  top: 64,
  left: 0,
  right: 0,
  zIndex: 28,
};

const mapControlsStyle: CSSProperties = {
  position: 'absolute',
  right: 14,
  zIndex: 22,
  display: 'flex',
  flexDirection: 'column',
  gap: 8,
};

const sheetBaseStyle: CSSProperties = {
  position: 'absolute',
  left: 0,
  right: 0,
  bottom: 0,
  zIndex: 25,
  background: 'var(--color-app-surface, #FFFFFF)',
  borderTopLeftRadius: 22,
  borderTopRightRadius: 22,
  boxShadow: '0 -10px 30px rgba(17,24,39,0.12)',
  overflow: 'hidden',
  touchAction: 'none',
  display: 'flex',
  flexDirection: 'column',
};

const grabberWrapStyle: CSSProperties = {
  display: 'flex',
  justifyContent: 'center',
  paddingTop: 8,
  paddingBottom: 4,
  flexShrink: 0,
};

const grabberButtonStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  background: 'transparent',
  border: 'none',
  padding: 8,
  cursor: 'grab',
  touchAction: 'none',
};

const grabberBarStyle: CSSProperties = {
  display: 'block',
  width: 40,
  height: 4,
  borderRadius: 4,
  background: 'var(--color-app-border-strong, #D1D5DB)',
};

const sheetBodyStyle: CSSProperties = {
  flex: 1,
  minHeight: 0,
  overflowY: 'auto',
};
