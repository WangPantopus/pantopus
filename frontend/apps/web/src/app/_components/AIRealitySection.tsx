// ─────────────────────────────────────────────────────────────────────────────
// AIRealitySection — §5 Emotional pivot: AI as threat to income + identity
// Server component (no 'use client')
// ─────────────────────────────────────────────────────────────────────────────

import Link from 'next/link';
import { AI_REALITY_SKILLS } from './constants';

export default function AIRealitySection() {
  return (
    <section
      id="ai-reality"
      className="scroll-mt-20 py-24 bg-gradient-to-br from-app-surface-raised to-app-surface dark:from-gray-900 dark:to-gray-950"
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Two-panel grid */}
        <div className="grid md:grid-cols-2 gap-12 lg:gap-16">

          {/* ── Left Panel: Economic Angle ──────────────────────────────── */}
          <div className="flex flex-col">
            <span className="inline-flex self-start items-center gap-1.5 text-xs font-semibold px-3 py-1.5 rounded-full bg-emerald-500/15 text-emerald-700 dark:bg-emerald-500/20 dark:text-emerald-300 mb-6">
              <span>✦</span> Your skills are AI-proof
            </span>

            <h2 className="text-3xl md:text-4xl font-extrabold text-app-text leading-tight mb-6">
              AI can write code.<br />
              <span className="text-emerald-600 dark:text-emerald-400">It can&apos;t fix your fence.</span>
            </h2>

            <div className="space-y-4 text-app-text-secondary text-base leading-relaxed mb-8">
              <p>
                Every week, another headline about AI replacing jobs. But look around your actual life — who&apos;s going to clean your gutters, watch your dog, move your furniture, tutor your kid, mow your lawn?
              </p>
              <p>
                Real-world work requires real hands, real trust, and real presence. AI can&apos;t show up at your door.
              </p>
              <p>
                On Pantopus, your skills connect directly to people who need them. No algorithm middleman. No platform taking a cut. Just you, your reputation, and people who&apos;ll hire you again next week.
              </p>
            </div>

            {/* Proof points */}
            <ul className="space-y-2 mb-8">
              {[
                'Physical skills, caregiving, and hands-on services are growing in demand, not shrinking',
                'Earn on your schedule — side hustle or primary income',
                'Your reputation builds with every completed task and review',
              ].map((point) => (
                <li key={point} className="flex items-start gap-2.5 text-sm text-app-text-muted">
                  <span className="text-emerald-600 dark:text-emerald-400 mt-0.5 flex-shrink-0">✓</span>
                  {point}
                </li>
              ))}
            </ul>

            {/* Skills tag cloud */}
            <div className="flex flex-wrap gap-2">
              {AI_REALITY_SKILLS.map((skill) => (
                <span
                  key={skill}
                  className="text-xs font-medium px-3 py-1.5 rounded-full border border-app-border text-app-text-secondary bg-app-surface"
                >
                  {skill}
                </span>
              ))}
            </div>
          </div>

          {/* ── Right Panel: Trust / Identity Angle ─────────────────────── */}
          <div className="flex flex-col">
            <span className="inline-flex self-start items-center gap-1.5 text-xs font-semibold px-3 py-1.5 rounded-full bg-amber-500/15 text-amber-700 dark:bg-amber-500/20 dark:text-amber-300 mb-6">
              <span>✦</span> Real identity in a fake world
            </span>

            <h2 className="text-3xl md:text-4xl font-extrabold text-app-text leading-tight mb-6">
              AI can fake a face.<br />
              <span className="text-amber-600 dark:text-amber-400">It can&apos;t fake an address.</span>
            </h2>

            <div className="space-y-4 text-app-text-secondary text-base leading-relaxed mb-8">
              <p>
                Fake profiles. AI-generated reviews. Deepfake photos. Chatbots pretending to be people. Every platform you use is getting harder to trust. You don&apos;t know if the person you&apos;re messaging is real, let alone whether they live where they say they do.
              </p>
              <p>
                Pantopus is built differently. Every account is tied to a verified physical address — proven through real mail, property records, or documentation. When you deal with someone on Pantopus, they&apos;re provably human, provably located, and provably accountable.
              </p>
              <p>
                In a world where AI can fake almost anything, that&apos;s not a feature — it&apos;s the foundation.
              </p>
            </div>

            {/* Proof points */}
            <ul className="space-y-2">
              {[
                'Verified to a real address — not just an email or phone number',
                'Reputation earned through real transactions, not bot-farmable',
                'When someone says they\'re nearby, they actually are',
              ].map((point) => (
                <li key={point} className="flex items-start gap-2.5 text-sm text-app-text-muted">
                  <span className="text-amber-600 dark:text-amber-400 mt-0.5 flex-shrink-0">✓</span>
                  {point}
                </li>
              ))}
            </ul>
          </div>
        </div>

        {/* ── Unifying Bottom Statement ────────────────────────────────── */}
        <div className="mt-16 pt-12 border-t border-app-border-subtle text-center">
          <blockquote className="text-xl md:text-2xl font-bold text-app-text max-w-3xl mx-auto leading-snug mb-8">
            &ldquo;The world is being flooded with artificial everything. Your real skills, your real identity, and your real presence are becoming more valuable every day — not less. Pantopus is where that value lives.&rdquo;
          </blockquote>
          <Link
            href="/register"
            className="inline-flex items-center gap-2 bg-primary-600 hover:bg-primary-700 text-white px-8 py-3.5 rounded-xl text-base font-semibold transition focus:outline-none focus:ring-2 focus:ring-primary-400/40 shadow-md shadow-primary-900/40"
          >
            Claim your place
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          </Link>
        </div>
      </div>
    </section>
  );
}
