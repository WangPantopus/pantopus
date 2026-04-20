// ─────────────────────────────────────────────────────────────────────────────
// PillarCard — Product pillar card used in the Features section
// Server component (no 'use client')
// ─────────────────────────────────────────────────────────────────────────────

import type { PillarTone } from './constants';

export interface PillarCardProps {
  emoji: string;
  tone: PillarTone;
  badge: string;
  title: string;
  tagline: string;
  bullets: readonly string[];
  wide?: boolean;
}

const toneMap: Record<PillarTone, { chip: string; icon: string }> = {
  primary: { chip: 'bg-primary-100 text-primary-700 dark:bg-primary-900/40 dark:text-primary-300', icon: 'text-primary-600 dark:text-primary-400' },
  sky: { chip: 'bg-sky-100 text-sky-700 dark:bg-sky-900/40 dark:text-sky-300', icon: 'text-sky-600 dark:text-sky-400' },
  emerald: { chip: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300', icon: 'text-emerald-600 dark:text-emerald-400' },
  violet: { chip: 'bg-violet-100 text-violet-700 dark:bg-violet-900/40 dark:text-violet-300', icon: 'text-violet-600 dark:text-violet-400' },
  indigo: { chip: 'bg-indigo-100 text-indigo-700 dark:bg-indigo-900/40 dark:text-indigo-300', icon: 'text-indigo-600 dark:text-indigo-400' },
  amber: { chip: 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300', icon: 'text-amber-600 dark:text-amber-400' },
  rose: { chip: 'bg-rose-100 text-rose-700 dark:bg-rose-900/40 dark:text-rose-300', icon: 'text-rose-600 dark:text-rose-400' },
};

export default function PillarCard({
  emoji,
  tone,
  badge,
  title,
  tagline,
  bullets,
  wide = false,
}: PillarCardProps) {
  const t = toneMap[tone];

  return (
    <div className={`bg-app-surface rounded-2xl border border-app-border-subtle p-6 shadow-sm hover:shadow-md dark:hover:shadow-black/30 transition ${wide ? 'sm:col-span-2 lg:col-span-3' : ''}`}>
      <div className="flex items-start gap-4">
        <span className="text-3xl flex-shrink-0">{emoji}</span>
        <div className="flex-1 min-w-0">
          <div className="flex flex-wrap items-center gap-2 mb-2">
            <span className={`text-xs font-semibold px-2.5 py-1 rounded-full ${t.chip}`}>{badge}</span>
          </div>
          <h3 className="text-lg font-bold text-app-text dark:text-white mb-1">{title}</h3>
          <p className="text-app-text-secondary dark:text-app-text-muted text-sm italic mb-4">{tagline}</p>
          <ul className={`space-y-1.5 ${wide ? 'grid sm:grid-cols-2 lg:grid-cols-4 gap-2 space-y-0' : ''}`}>
            {bullets.map((b) => (
              <li key={b} className="flex items-start gap-2 text-sm text-app-text-secondary dark:text-app-text-muted">
                <span className={`${t.icon} mt-0.5 flex-shrink-0 font-bold`}>·</span>
                {b}
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
}
