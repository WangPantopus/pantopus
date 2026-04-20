// ─────────────────────────────────────────────────────────────────────────────
// AICreationSection — §6 AI-powered task and listing creation demos
// Server component (no 'use client')
// ─────────────────────────────────────────────────────────────────────────────

import Image from 'next/image';

export default function AICreationSection() {
  return (
    <section id="ai-creation" className="scroll-mt-20 py-24 bg-gradient-to-br from-primary-50 to-white dark:from-gray-950 dark:to-gray-900">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">

        {/* Transition line */}
        <p className="text-center text-base italic text-app-text-secondary dark:text-app-text-muted mb-10">
          Pantopus uses AI too. But here, AI is your tool — not your replacement.
        </p>

        {/* Section header */}
        <div className="text-center mb-14">
          <span className="inline-block text-xs font-semibold px-3 py-1.5 rounded-full bg-primary-100 text-primary-700 dark:bg-primary-900/40 dark:text-primary-300 mb-4">
            AI-powered
          </span>
          <h2 className="text-3xl md:text-4xl font-bold text-app-text dark:text-white mb-4">
            Say what you need. AI handles the rest.
          </h2>
          <p className="text-lg text-app-text-secondary dark:text-app-text-muted max-w-2xl mx-auto leading-relaxed">
            Type a sentence. Pantopus turns it into a structured task with pricing, category, scheduling, and location — ready to post in one tap. Or snap a photo and AI creates your marketplace listing.
          </p>
        </div>

        {/* Demo panels — stacked, each demo gets full width so the before/after
            screenshots can render large enough to actually read. Within each card,
            input and output sit side-by-side on md+ and stack on small screens. */}
        <div className="flex flex-col gap-8 mb-16">

          {/* ── Magic Task Demo ─────────────────────────────────────── */}
          <div className="bg-app-surface rounded-2xl border border-app-border-subtle shadow-sm p-6">
            <div className="text-xs font-semibold text-app-text-muted uppercase tracking-wider mb-4">Magic Task</div>

            <div className="grid md:grid-cols-[1fr_auto_1fr] gap-4 md:gap-6 items-center">
              {/* Input screenshot — light/dark variants swap via prefers-color-scheme.
                  Drop *-light.png replacements into public/landing/ to use real light captures. */}
              <div>
                <Image
                  src="/landing/magic-task-input-light.png"
                  alt="Type a natural language task request"
                  width={1200}
                  height={900}
                  className="rounded-xl w-full h-auto shadow-sm block dark:hidden"
                />
                <Image
                  src="/landing/magic-task-input.png"
                  alt="Type a natural language task request"
                  width={1200}
                  height={900}
                  className="rounded-xl w-full h-auto shadow-sm hidden dark:block"
                />
              </div>

              {/* Arrow — horizontal on desktop, vertical on mobile */}
              <div className="flex md:flex-col items-center justify-center gap-2 text-primary-500 my-2 md:my-0">
                <span className="text-xs text-app-text-muted whitespace-nowrap order-2 md:order-1">AI structured it</span>
                <svg className="w-5 h-5 hidden md:block order-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                </svg>
                <svg className="w-5 h-5 md:hidden order-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                </svg>
              </div>

              {/* Output screenshot */}
              <div>
                <Image
                  src="/landing/magic-task-output-light.png"
                  alt="AI-structured task ready to post"
                  width={1200}
                  height={900}
                  className="rounded-xl w-full h-auto shadow-sm block dark:hidden"
                />
                <Image
                  src="/landing/magic-task-output.png"
                  alt="AI-structured task ready to post"
                  width={1200}
                  height={900}
                  className="rounded-xl w-full h-auto shadow-sm hidden dark:block"
                />
              </div>
            </div>
          </div>

          {/* ── Snap & Sell Demo ───────────────────────────────────── */}
          <div className="bg-app-surface rounded-2xl border border-app-border-subtle shadow-sm p-6">
            <div className="text-xs font-semibold text-app-text-muted uppercase tracking-wider mb-4">Snap &amp; Sell</div>

            <div className="grid md:grid-cols-[1fr_auto_1fr] gap-4 md:gap-6 items-center">
              {/* Item photo */}
              <div className="rounded-xl aspect-[4/3] overflow-hidden border border-app-border-subtle">
                <Image
                  src="/landing/snap-sell-photo.avif"
                  alt="Photo of a 2026 Tesla Model Y Performance for sale"
                  width={1200}
                  height={900}
                  className="w-full h-full object-cover"
                />
              </div>

              {/* Arrow — horizontal on desktop, vertical on mobile */}
              <div className="flex md:flex-col items-center justify-center gap-2 text-emerald-500 my-2 md:my-0">
                <span className="text-xs text-app-text-muted whitespace-nowrap order-2 md:order-1">AI generated listing</span>
                <svg className="w-5 h-5 hidden md:block order-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                </svg>
                <svg className="w-5 h-5 md:hidden order-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                </svg>
              </div>

              {/* Output card */}
              <div className="bg-app-surface-raised/60 dark:bg-gray-800/60 rounded-xl border border-app-border-subtle p-5">
                <div className="grid grid-cols-2 gap-4 text-sm">
                  <div>
                    <p className="text-xs text-app-text-muted mb-0.5">Title</p>
                    <p className="font-semibold text-app-text dark:text-white">2026 Tesla Model Y Performance</p>
                  </div>
                  <div>
                    <p className="text-xs text-app-text-muted mb-0.5">Category</p>
                    <p className="font-semibold text-app-text dark:text-white">Vehicles</p>
                  </div>
                  <div>
                    <p className="text-xs text-app-text-muted mb-0.5">Condition</p>
                    <p className="font-semibold text-app-text dark:text-white">Like New</p>
                  </div>
                  <div>
                    <p className="text-xs text-app-text-muted mb-0.5">Suggested Price</p>
                    <p className="font-semibold text-emerald-600 dark:text-emerald-400">$48,500–52,000</p>
                  </div>
                  <div className="col-span-2">
                    <p className="text-xs text-app-text-muted mb-0.5">Description</p>
                    <p className="text-sm text-app-text-secondary dark:text-app-text-muted leading-relaxed">2026 Tesla Model Y Performance in pristine condition. Low miles, single owner, all-wheel drive, autopilot included. Garage-kept, full service history available.</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Supporting bullets — 2x2 grid */}
        <div className="grid sm:grid-cols-2 gap-4 mb-12">
          {[
            { icon: '✍️', text: 'AI drafts posts, tasks, and listings from natural language' },
            { icon: '⚡', text: 'High-confidence drafts post instantly, low-confidence asks one question' },
            { icon: '📊', text: 'Price intelligence from real marketplace data, not guesses' },
            { icon: '🔄', text: 'Works for gigs, listings, feed posts, and mail summaries' },
          ].map(({ icon, text }) => (
            <div key={text} className="flex items-start gap-3 bg-app-surface-raised/40 rounded-xl border border-app-border-subtle px-4 py-3">
              <span className="text-xl flex-shrink-0">{icon}</span>
              <p className="text-sm text-app-text-secondary dark:text-app-text-muted leading-relaxed">{text}</p>
            </div>
          ))}
        </div>

        {/* Bottom line */}
        <p className="text-center italic text-app-text-muted dark:text-app-text-secondary text-base max-w-2xl mx-auto">
          &ldquo;The AI is invisible. The result is immediate. You describe what you need in plain English, and it&apos;s live in seconds.&rdquo;
        </p>
      </div>
    </section>
  );
}
