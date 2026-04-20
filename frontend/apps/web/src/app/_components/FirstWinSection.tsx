// ─────────────────────────────────────────────────────────────────────────────
// FirstWinSection — "Pick Your First Win" card grid
// Server component (no 'use client')
// ─────────────────────────────────────────────────────────────────────────────

import { FIRST_WIN_CARDS } from './constants';
import FirstWinCard from './FirstWinCard';

export default function FirstWinSection() {
  return (
    <section id="first-win" className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-24">
      <div className="text-center mb-14">
        <h2 className="text-3xl md:text-4xl font-bold text-app-text dark:text-white mb-3">
          What do you want to do today?
        </h2>
        <p className="text-app-text-secondary dark:text-app-text-muted text-lg">
          Start with one win. Everything else unlocks naturally.
        </p>
      </div>

      <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-5">
        {FIRST_WIN_CARDS.map((card) => (
          <FirstWinCard key={card.title} {...card} />
        ))}
      </div>
    </section>
  );
}
