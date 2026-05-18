'use client';

// Pantopus — `<MapListHybridShell />` preview canvas.
//
// T6.6a (P24) — Designer sanity check for the map+list hybrid archetype
// shell. Shows the shell at all three detents (`collapsed`, `standard`,
// `expanded`) side-by-side so the designer can confirm sheet heights,
// drag-handle geometry, chrome overlay placement, and pin↔list
// selection sync land per the design files. Not linked from the
// production navigation — accessed via `/map-list-hybrid-preview`.

import { useState } from 'react';
import { ArrowLeft, ChevronDown, ChevronUp, Hammer, Layers, MapPin, Sliders } from 'lucide-react';
import {
  MapListHybridShell,
  type MapAnchor,
  type MapListHybridDetent,
  type MapPin as MapPinModel,
} from '@/components/map-list-hybrid';

const CATEGORIES: { key: string; label: string; color: string }[] = [
  { key: 'all', label: 'All', color: '#0284C7' },
  { key: 'handyman', label: 'Handyman', color: '#EA580C' },
  { key: 'cleaning', label: 'Cleaning', color: '#0EA5E9' },
  { key: 'moving', label: 'Moving', color: '#7C3AED' },
  { key: 'petcare', label: 'Pet care', color: '#16A34A' },
  { key: 'childcare', label: 'Child care', color: '#DB2777' },
  { key: 'tutoring', label: 'Tutoring', color: '#CA8A04' },
];

const SAMPLE_PINS: MapPinModel[] = [
  { id: 'handyman-1', latitude: 40.7494, longitude: -73.9867, color: '#EA580C' },
  { id: 'cleaning-1', latitude: 40.7502, longitude: -73.9840, color: '#0EA5E9' },
  { id: 'moving-1', latitude: 40.7470, longitude: -73.9810, color: '#7C3AED', state: 'pending' },
  { id: 'petcare-1', latitude: 40.7459, longitude: -73.9882, color: '#16A34A' },
  { id: 'childcare-1', latitude: 40.7515, longitude: -73.9905, color: '#DB2777' },
  { id: 'tutoring-1', latitude: 40.7440, longitude: -73.9930, color: '#CA8A04', state: 'pending' },
  { id: 'handyman-2', latitude: 40.7460, longitude: -73.9990, color: '#EA580C' },
];

const ANCHOR: MapAnchor = { latitude: 40.7484, longitude: -73.9857 };

const SAMPLE_ROWS = SAMPLE_PINS.map((p, i) => ({
  id: p.id,
  color: p.color,
  title: `Sample task ${i + 1}`,
  price: '$60',
  distance: '0.2 mi',
}));

const DETENT_LABEL: Record<MapListHybridDetent, string> = {
  collapsed: 'Collapsed (160 px)',
  standard: 'Standard (296 px)',
  expanded: 'Expanded (518 px)',
};

export default function MapListHybridPreviewPage() {
  return (
    <div className="min-h-screen bg-slate-100 px-4 py-8">
      <div className="mx-auto max-w-7xl">
        <header className="mb-6">
          <h1 className="text-2xl font-bold text-app-text">MapListHybridShell preview</h1>
          <p className="mt-1 text-sm text-app-text-secondary">
            P24 / T6.6a — every detent of the shared map+list hybrid archetype. Drag the sheet
            grabber to snap between stops; tap a pin or row to flip the selection.
          </p>
        </header>

        <div className="grid grid-cols-1 gap-8 xl:grid-cols-3">
          {(['collapsed', 'standard', 'expanded'] as const).map((stop) => (
            <PreviewFrame key={stop} title={DETENT_LABEL[stop]}>
              <ShellSample initialDetent={stop} />
            </PreviewFrame>
          ))}
        </div>

        <p className="mt-8 text-xs text-app-text-secondary">
          The map uses live Mapbox tiles. If they don&apos;t load, set{' '}
          <code>NEXT_PUBLIC_MAPBOX_TOKEN</code> locally — the rest of the chrome still renders
          deterministically.
        </p>
      </div>
    </div>
  );
}

function ShellSample({ initialDetent }: { initialDetent: MapListHybridDetent }) {
  const [detent, setDetent] = useState<MapListHybridDetent>(initialDetent);
  const [selectedPinId, setSelectedPinId] = useState<string | null>(SAMPLE_PINS[0]!.id);

  return (
    <div className="relative h-[680px] w-full overflow-hidden rounded-3xl border border-app-border bg-white shadow-lg">
      <MapListHybridShell
        pins={SAMPLE_PINS}
        anchor={ANCHOR}
        selectedPinId={selectedPinId}
        onPinTap={(id) => {
          setSelectedPinId(id);
          setDetent('standard');
        }}
        detent={detent}
        onDetentChange={setDetent}
        topPill={
          <div className="flex items-center justify-between rounded-full border border-app-border bg-white/95 px-2 py-2 shadow-lg backdrop-blur">
            <button
              type="button"
              aria-label="Back"
              className="flex h-8 w-8 items-center justify-center rounded-full text-app-text"
            >
              <ArrowLeft size={18} strokeWidth={2.2} />
            </button>
            <div className="text-sm font-bold text-app-text">Gigs</div>
            <button
              type="button"
              aria-label="Filters"
              className="flex h-8 w-8 items-center justify-center rounded-full text-app-text"
            >
              <Sliders size={16} strokeWidth={2.2} />
            </button>
          </div>
        }
        categoryChips={
          <div className="flex gap-1.5 overflow-x-auto px-3.5 py-0.5">
            {CATEGORIES.map((cat) => {
              const active = cat.key === 'all';
              return (
                <button
                  key={cat.key}
                  type="button"
                  className={`flex h-7 shrink-0 items-center gap-1.5 rounded-full px-3 text-[11.5px] font-semibold shadow-sm ${
                    active ? 'text-white' : 'border border-app-border bg-white/95 text-app-text-strong'
                  }`}
                  style={active ? { background: cat.color } : undefined}
                >
                  {cat.key !== 'all' && (
                    <span
                      className="h-[7px] w-[7px] rounded-full"
                      style={{ background: active ? '#fff' : cat.color }}
                    />
                  )}
                  {cat.label}
                </button>
              );
            })}
          </div>
        }
        mapControls={
          <>
            <button
              type="button"
              aria-label="Locate me"
              className="flex h-9 w-9 items-center justify-center rounded-full border border-app-border bg-white/95 text-app-text shadow-md"
            >
              <MapPin size={16} />
            </button>
            <button
              type="button"
              aria-label="Layers"
              className="flex h-9 w-9 items-center justify-center rounded-full border border-app-border bg-white/95 text-app-text shadow-md"
            >
              <Layers size={16} />
            </button>
          </>
        }
        sheetHeader={
          <div className="flex items-center justify-between px-4 pb-3 pt-1">
            <div className="text-sm font-bold text-app-text">{SAMPLE_ROWS.length} gigs nearby</div>
            <div className="flex items-center gap-1 text-xs">
              <span className="font-medium text-app-text-secondary">Sort:</span>
              <span className="font-semibold text-app-text-strong">Closest</span>
              <ChevronDown size={12} strokeWidth={2.4} className="text-app-text-strong" />
            </div>
          </div>
        }
        sheetBody={
          detent === 'collapsed' ? (
            <div className="px-4 pb-3">
              <button
                type="button"
                className="flex w-full items-center justify-center gap-2 rounded-full border border-app-border bg-app-surface-sunken px-3 py-2 text-[11.5px] font-medium text-app-text-secondary"
                onClick={() => setDetent('standard')}
              >
                <ChevronUp size={13} strokeWidth={2.4} />
                Drag up to see the list
              </button>
            </div>
          ) : detent === 'standard' ? (
            <div className="flex gap-2.5 overflow-x-auto px-4 pb-4">
              {SAMPLE_ROWS.slice(0, 5).map((row) => (
                <button
                  type="button"
                  key={row.id}
                  onClick={() => {
                    setSelectedPinId(row.id);
                    setDetent('standard');
                  }}
                  className={`flex w-60 shrink-0 items-center gap-2.5 rounded-2xl border bg-white p-3 text-left shadow-sm ${
                    selectedPinId === row.id ? 'border-2' : ''
                  }`}
                  style={
                    selectedPinId === row.id
                      ? { borderColor: row.color }
                      : { borderColor: 'rgb(229 231 235)' }
                  }
                >
                  <div
                    className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl text-white"
                    style={{ background: row.color }}
                  >
                    <Hammer size={22} />
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="truncate text-sm font-semibold text-app-text">{row.title}</div>
                    <div className="mt-0.5 text-xs font-bold text-sky-600">
                      {row.price} · {row.distance}
                    </div>
                  </div>
                </button>
              ))}
            </div>
          ) : (
            <div>
              {SAMPLE_ROWS.map((row) => (
                <button
                  type="button"
                  key={row.id}
                  onClick={() => {
                    setSelectedPinId(row.id);
                    setDetent('standard');
                  }}
                  className="flex w-full items-start gap-3 border-b border-app-border-subtle px-4 py-3 text-left"
                  style={
                    selectedPinId === row.id
                      ? { background: `${row.color}10` }
                      : undefined
                  }
                >
                  <div
                    className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl text-white"
                    style={{ background: row.color }}
                  >
                    <Hammer size={20} />
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="text-sm font-semibold text-app-text">{row.title}</div>
                    <div className="mt-0.5 text-xs font-bold text-sky-600">
                      {row.price} · {row.distance}
                    </div>
                  </div>
                </button>
              ))}
            </div>
          )
        }
      />
    </div>
  );
}

function PreviewFrame({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-3xl border border-app-border bg-app-surface p-4 shadow-sm">
      <h2 className="mb-3 text-sm font-semibold text-app-text">{title}</h2>
      {children}
    </section>
  );
}
