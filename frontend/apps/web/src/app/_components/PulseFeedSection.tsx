// ─────────────────────────────────────────────────────────────────────────────
// PulseFeedSection — "The Pulse" feed preview with mock posts
// Server component (no 'use client')
// ─────────────────────────────────────────────────────────────────────────────

import { INTENT_TAGS, FEED_POSTS } from './constants';
import FeedPostMock from './FeedPostMock';

export default function PulseFeedSection() {
  return (
    <section id="pulse" className="bg-gradient-to-br from-primary-50 to-blue-50 dark:from-gray-900 dark:to-gray-950 border-y border-primary-100 py-24 scroll-mt-20">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid md:grid-cols-2 gap-12 items-center">
          <div>
            <div className="inline-flex items-center gap-2 rounded-full bg-primary-100 dark:bg-primary-900/40 text-primary-700 dark:text-primary-300 text-xs font-semibold px-3 py-1.5 mb-5 tracking-wide uppercase">
              The Pulse
            </div>
            <h2 className="text-3xl md:text-4xl font-bold text-app-text dark:text-white mb-5 leading-tight">
              A feed with{' '}
              <span className="text-primary-600 dark:text-primary-400">actual purpose.</span>
            </h2>
            <p className="text-app-text-secondary dark:text-app-text-muted text-lg mb-8 leading-relaxed">
              Every post starts with an intent — Ask, Recommend, Event, Alert, Announce — so what you see is useful, not noise.
            </p>
            <div className="flex flex-wrap gap-2 mb-8">
              {INTENT_TAGS.map(({ label, color }) => (
                <span key={label} className={`px-4 py-1.5 rounded-full text-sm font-semibold ${color}`}>
                  {label}
                </span>
              ))}
            </div>
            <p className="text-sm text-app-text-muted dark:text-app-text-secondary mb-4">
              Audience controls let you post to Nearby, Followers, your Household, or a Saved Place — not just &quot;everyone.&quot;
            </p>
            <p className="text-sm text-app-text-muted dark:text-app-text-secondary">
              Follow any place you care about — your block, your workplace, your parents&apos; street, a city across the country.
            </p>
          </div>

          {/* Feed mock */}
          <div className="relative">
            <div className="bg-app-surface rounded-2xl shadow-xl shadow-black/5 dark:shadow-black/40 border border-app-border-subtle overflow-hidden">
              <div className="p-4 border-b border-app-border-subtle flex items-center justify-between">
                <span className="font-semibold text-app-text dark:text-white text-sm">📍 Your Places</span>
                <span className="text-xs text-app-text-muted">Nearby &middot; 0.3 mi radius</span>
              </div>
              <div className="divide-y divide-app-border-subtle">
                {FEED_POSTS.map((post) => (
                  <FeedPostMock key={post.user} {...post} />
                ))}
              </div>
            </div>
            {/* Floating badge */}
            <div className="absolute -bottom-4 -right-4 bg-primary-600 text-white text-xs font-semibold px-4 py-2 rounded-full shadow-lg">
              Verified people highlighted
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
