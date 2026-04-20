// ─────────────────────────────────────────────────────────────────────────────
// HeroSection — Main hero with headline, CTAs, jump links, phone mockup
// Server component (no 'use client')
// ─────────────────────────────────────────────────────────────────────────────

import Link from 'next/link';
import Image from 'next/image';
import { IOS_APP_STORE_URL, ANDROID_PLAY_STORE_URL } from '@pantopus/utils';
import { HERO_JUMP_LINKS } from './constants';
import HeroVisual from './HeroVisual';

export default function HeroSection() {
  return (
    <section className="relative overflow-hidden bg-gradient-to-b from-blue-50 via-white to-white dark:from-gray-900 dark:via-gray-950 dark:to-gray-950">
      {/* Background decorations */}
      <div className="absolute inset-0 pointer-events-none">
        <div className="absolute top-0 left-1/2 -translate-x-1/2 w-[900px] h-[500px] rounded-full bg-primary-100/60 dark:bg-primary-900/20 blur-3xl" />
        <div className="absolute top-20 right-10 w-64 h-64 rounded-full bg-emerald-100/40 dark:bg-emerald-900/10 blur-3xl" />
      </div>

      <div className="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-24 md:py-32">
        <div className="text-center max-w-4xl mx-auto">
          {/* Kicker pill */}
          <div className="inline-flex flex-wrap justify-center items-center gap-2 rounded-full border border-primary-200/60 dark:border-primary-700/30 bg-app-surface/80 px-5 py-2 text-sm mb-8 shadow-sm">
            <span className="font-semibold text-primary-700 dark:text-primary-300">Verified identity</span>
            <span className="text-gray-300 dark:text-app-text-strong">&bull;</span>
            <span className="text-app-text-secondary dark:text-app-text-muted">Tasks &amp; marketplace</span>
            <span className="text-gray-300 dark:text-app-text-strong">&bull;</span>
            <span className="text-app-text-secondary dark:text-app-text-muted">Home command center</span>
            <span className="text-gray-300 dark:text-app-text-strong">&bull;</span>
            <span className="text-app-text-secondary dark:text-app-text-muted">Digital mailbox</span>
          </div>

          {/* Headline */}
          <h1 className="text-5xl md:text-7xl font-extrabold text-app-text dark:text-white leading-[1.05] tracking-tight mb-6">
            Get real things done<br />
            <span className="text-primary-600 dark:text-primary-400">
              with real people.
            </span>
          </h1>

          {/* Subhead */}
          <p className="text-xl md:text-2xl text-app-text-secondary dark:text-app-text-muted mb-4 max-w-2xl mx-auto leading-relaxed">
            Pantopus connects you with verified, address-proven people — to hire, sell, buy, coordinate, and manage the real-world stuff that apps built for strangers can&apos;t handle.
          </p>
          <p className="text-sm text-app-text-muted dark:text-app-text-secondary mb-10">
            Private by default. Verification builds trust, not exposure.
          </p>

          {/* CTAs */}
          <div className="flex flex-col sm:flex-row justify-center gap-4">
            <Link
              href="/register"
              className="inline-flex items-center justify-center gap-2 bg-primary-600 text-white px-8 py-3.5 rounded-xl text-base font-semibold hover:bg-primary-700 focus:outline-none focus:ring-2 focus:ring-primary-400/40 transition shadow-md shadow-primary-200 dark:shadow-primary-900/30"
            >
              Create your account
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
              </svg>
            </Link>
            <a
              href="#how-it-works"
              className="inline-flex items-center justify-center gap-2 bg-app-surface text-app-text-strong px-8 py-3.5 rounded-xl text-base font-semibold border border-app-border hover:bg-app-hover dark:hover:bg-gray-800 transition"
            >
              See how it works
            </a>
          </div>

          {/* App Store badges */}
          <div className="mt-5 flex justify-center items-center gap-3">
            <a
              href={IOS_APP_STORE_URL}
              target="_blank"
              rel="noopener noreferrer"
              aria-label="Download on the App Store"
            >
              <Image
                src="/landing/badge-appstore.svg"
                alt="Download on the App Store"
                width={132}
                height={44}
                className="block dark:hidden"
              />
              <Image
                src="/landing/badge-appstore-dark.svg"
                alt="Download on the App Store"
                width={132}
                height={44}
                className="hidden dark:block"
              />
            </a>
            <a
              href={ANDROID_PLAY_STORE_URL}
              target="_blank"
              rel="noopener noreferrer"
              aria-label="Get it on Google Play"
            >
              <Image
                src="/landing/badge-playstore.svg"
                alt="Get it on Google Play"
                width={140}
                height={44}
                className="block dark:hidden"
              />
              <Image
                src="/landing/badge-playstore-dark.svg"
                alt="Get it on Google Play"
                width={140}
                height={44}
                className="hidden dark:block"
              />
            </a>
          </div>

          {/* Jump links */}
          <div className="mt-8 flex flex-wrap justify-center gap-x-6 gap-y-2 text-sm text-app-text-muted dark:text-app-text-secondary">
            <span className="text-gray-300 dark:text-app-text-secondary hidden sm:inline">Jump to:</span>
            {HERO_JUMP_LINKS.map(({ label, href }) => (
              <a key={href} href={href} className="hover:text-app-text-secondary dark:hover:text-gray-300 transition underline underline-offset-2">
                {label}
              </a>
            ))}
          </div>

          {/* Phone mockup with video */}
          <HeroVisual />
        </div>
      </div>
    </section>
  );
}
