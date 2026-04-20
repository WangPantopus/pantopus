// ─────────────────────────────────────────────────────────────────────────────
// FeaturesSection — 5-card product features grid
// Server component (no 'use client')
// ─────────────────────────────────────────────────────────────────────────────

import { PILLAR_CARDS } from './constants';
import PillarCard from './PillarCard';

export default function FeaturesSection() {
  return (
    <section id="features" className="scroll-mt-20 bg-app-surface-raised/40 border-y border-app-border-subtle py-24">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="text-center mb-14">
          <h2 className="text-3xl md:text-4xl font-bold text-app-text dark:text-white mb-3">
            Built for people and businesses alike.
          </h2>
          <p className="text-app-text-secondary dark:text-app-text-muted text-lg">
            Every module works on its own. They work better together.
          </p>
        </div>

        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-6">
          {PILLAR_CARDS.map((card) => (
            <PillarCard key={card.title} {...card} />
          ))}
        </div>
      </div>
    </section>
  );
}
