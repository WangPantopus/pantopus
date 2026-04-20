// ─────────────────────────────────────────────────────────────────────────────
// FirstWinCard — "Pick Your First Win" card component
// Server component (no 'use client')
// ─────────────────────────────────────────────────────────────────────────────

import Link from 'next/link';
import type { FirstWinColor } from './constants';

export interface FirstWinCardProps {
  icon: string;
  color: FirstWinColor;
  title: string;
  body: string;
  bullets: readonly string[];
  cta: string;
  href: string;
}

const colorMap: Record<FirstWinColor, { bg: string; border: string; badge: string; btn: string; bullet: string }> = {
  blue: {
    bg: 'bg-blue-50 dark:bg-blue-900/20',
    border: 'border-blue-100 dark:border-blue-800/30',
    badge: 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300',
    btn: 'bg-blue-600 hover:bg-blue-700 text-white',
    bullet: 'text-blue-500',
  },
  emerald: {
    bg: 'bg-emerald-50 dark:bg-emerald-900/20',
    border: 'border-emerald-100 dark:border-emerald-800/30',
    badge: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300',
    btn: 'bg-emerald-600 hover:bg-emerald-700 text-white',
    bullet: 'text-emerald-500',
  },
  indigo: {
    bg: 'bg-indigo-50 dark:bg-indigo-900/20',
    border: 'border-indigo-100 dark:border-indigo-800/30',
    badge: 'bg-indigo-100 text-indigo-700 dark:bg-indigo-900/40 dark:text-indigo-300',
    btn: 'bg-indigo-600 hover:bg-indigo-700 text-white',
    bullet: 'text-indigo-500',
  },
  amber: {
    bg: 'bg-amber-50 dark:bg-amber-900/20',
    border: 'border-amber-100 dark:border-amber-800/30',
    badge: 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300',
    btn: 'bg-amber-600 hover:bg-amber-700 text-white',
    bullet: 'text-amber-500',
  },
};

export default function FirstWinCard({
  icon,
  color,
  title,
  body,
  bullets,
  cta,
  href,
}: FirstWinCardProps) {
  const c = colorMap[color];

  return (
    <div className={`${c.bg} border ${c.border} rounded-2xl p-6 flex flex-col`}>
      <span className="text-3xl mb-4 block">{icon}</span>
      <h3 className="text-lg font-bold text-app-text dark:text-white mb-2">{title}</h3>
      <p className="text-app-text-secondary dark:text-app-text-muted text-sm mb-4 leading-relaxed">{body}</p>
      <ul className="space-y-2 mb-6 flex-1">
        {bullets.map((b) => (
          <li key={b} className="flex items-start gap-2 text-sm text-app-text-secondary dark:text-app-text-muted">
            <span className={`${c.bullet} mt-0.5 flex-shrink-0`}>✓</span>
            {b}
          </li>
        ))}
      </ul>
      <Link
        href={href}
        className={`${c.btn} text-center text-sm font-semibold px-4 py-2.5 rounded-lg transition focus:outline-none focus:ring-2 focus:ring-offset-1`}
      >
        {cta}
      </Link>
    </div>
  );
}
