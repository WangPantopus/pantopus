// ─────────────────────────────────────────────────────────────────────────────
// TrustSafetySection — Trust & verification selling points
// Server component (no 'use client')
// ─────────────────────────────────────────────────────────────────────────────

import Link from 'next/link';
import { TRUST_CARDS } from './constants';

export default function TrustSafetySection() {
  return (
    <section
      id="trust"
      className="scroll-mt-20 py-24 bg-app-surface-raised dark:bg-gray-950/60"
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid md:grid-cols-2 gap-12 items-center">
          <div>
            <div className="inline-flex items-center gap-2 rounded-full bg-app-surface border border-app-border-subtle text-app-text-secondary text-xs font-semibold px-3 py-1.5 mb-5 tracking-wide uppercase">
              Built for trust
            </div>
            <h2 className="text-3xl md:text-4xl font-bold text-app-text mb-5 leading-tight">
              Trust that&apos;s proven,<br />
              <span className="text-primary-600 dark:text-primary-400">not promised.</span>
            </h2>
            <p className="text-app-text-secondary text-lg mb-8 leading-relaxed">
              Every Pantopus account is tied to a verified physical address. Not a phone number. Not an email. A place where someone actually lives or operates.
            </p>
            <Link
              href="/register"
              className="inline-flex items-center gap-2 bg-primary-600 text-white px-6 py-3 rounded-xl text-sm font-semibold hover:bg-primary-700 transition"
            >
              Get started
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
              </svg>
            </Link>
          </div>

          <div className="grid grid-cols-2 gap-4">
            {TRUST_CARDS.map(({ icon, title, body }) => (
              <div
                key={title}
                className="bg-app-surface border border-app-border-subtle rounded-xl p-5 hover:border-app-border-strong transition shadow-sm"
              >
                <span className="text-2xl mb-3 block">{icon}</span>
                <h4 className="font-semibold text-app-text text-sm mb-1">{title}</h4>
                <p className="text-app-text-muted text-xs leading-relaxed">{body}</p>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
