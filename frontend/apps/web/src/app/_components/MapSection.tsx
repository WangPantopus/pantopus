// ─────────────────────────────────────────────────────────────────────────────
// MapSection — §9 The Map: discovery layer
// Server component (no 'use client')
// ─────────────────────────────────────────────────────────────────────────────

import Image from 'next/image';

const MAP_POINTS = [
  { color: 'bg-blue-500', label: 'Multiple layers: Tasks, Listings, Businesses, Posts, Free Items' },
  { color: 'bg-emerald-500', label: 'Real-time: new pins appear as people post' },
  { color: 'bg-violet-500', label: 'Tap to act: message, bid, claim, or share — without leaving the map' },
  { color: 'bg-sky-500', label: 'Search any area: zoom out, pan, or explore a different city' },
  { color: 'bg-amber-500', label: 'Reference points: see distances from your home, work, or any saved place' },
] as const;

export default function MapSection() {
  return (
    <section id="map" className="scroll-mt-20 py-24 bg-app-surface">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid md:grid-cols-2 gap-12 lg:gap-16 items-center">

          {/* ── Text side ─────────────────────────────────────────────── */}
          <div>
            <span className="inline-block text-xs font-semibold px-3 py-1.5 rounded-full bg-sky-100 text-sky-700 dark:bg-sky-900/40 dark:text-sky-300 mb-6">
              Discovery
            </span>
            <h2 className="text-3xl md:text-4xl font-bold text-app-text dark:text-white mb-4 leading-tight">
              See everything happening around you.
            </h2>
            <p className="text-lg text-app-text-secondary dark:text-app-text-muted mb-8 leading-relaxed">
              Tasks, listings, businesses, and posts — all on one map. Filter by distance, category, or trust level. Tap any pin to act.
            </p>

            <ul className="space-y-4">
              {MAP_POINTS.map(({ color, label }) => (
                <li key={label} className="flex items-start gap-3">
                  <span className={`w-2.5 h-2.5 rounded-full ${color} mt-1.5 flex-shrink-0`} />
                  <span className="text-sm text-app-text-secondary dark:text-app-text-muted leading-relaxed">{label}</span>
                </li>
              ))}
            </ul>
          </div>

          {/* ── Visual side: map screenshot ────────────────────────────── */}
          <div className="rounded-2xl overflow-hidden shadow-xl border border-app-border-subtle">
            <Image
              src="/landing/explore-map.png"
              alt="Explore Map with tasks, listings, and businesses"
              width={800}
              height={450}
              className="w-full h-auto"
            />
          </div>
        </div>
      </div>
    </section>
  );
}
