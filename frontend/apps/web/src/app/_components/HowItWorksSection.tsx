// ─────────────────────────────────────────────────────────────────────────────
// HowItWorksSection — 3-step "How it works" section
// Server component (no 'use client')
// ─────────────────────────────────────────────────────────────────────────────

import { HOW_IT_WORKS_STEPS } from './constants';

export default function HowItWorksSection() {
  return (
    <section id="how-it-works" className="scroll-mt-20 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-24">
      <div className="text-center mb-14">
        <h2 className="text-3xl md:text-4xl font-bold text-app-text dark:text-white mb-3">
          How it works
        </h2>
      </div>

      <div className="grid md:grid-cols-3 gap-8 relative">
        {/* Connector line */}
        <div className="hidden md:block absolute top-9 left-[calc(16.67%+1.5rem)] right-[calc(16.67%+1.5rem)] h-px bg-gradient-to-r from-primary-200 via-primary-300 to-primary-200 dark:from-primary-800 dark:via-primary-700 dark:to-primary-800" />

        {HOW_IT_WORKS_STEPS.map(({ step, title, line }) => (
          <div key={step} className="text-center relative">
            <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-primary-100 dark:bg-primary-900/40 text-primary-700 dark:text-primary-300 text-xl font-bold mb-5 relative z-10">
              {step}
            </div>
            <h3 className="text-xl font-semibold text-app-text dark:text-white mb-2">{title}</h3>
            <p className="text-app-text-secondary dark:text-app-text-muted">{line}</p>
          </div>
        ))}
      </div>

      <p className="text-center text-sm text-app-text-muted dark:text-app-text-secondary mt-10">
        Home features are private by default. Everything else is intentional.
      </p>
    </section>
  );
}
