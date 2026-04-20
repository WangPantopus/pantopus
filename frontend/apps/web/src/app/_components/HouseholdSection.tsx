// ─────────────────────────────────────────────────────────────────────────────
// HouseholdSection — §11 Home Command Center + Digital Mailbox (merged)
// Server component (no 'use client')
// ─────────────────────────────────────────────────────────────────────────────

import type React from 'react';

// Phone chrome shared by both Home and Mailbox mockups. Renders a small status
// bar at the top so the inner content reads as "a mobile app screen" rather
// than a card. Uses theme tokens so it adapts to light/dark with the page.
function PhoneFrame({ children }: { children: React.ReactNode }) {
  return (
    <div className="rounded-[28px] overflow-hidden shadow-2xl border border-app-border bg-app-surface">
      {/* Status bar */}
      <div className="flex items-center justify-between px-5 pt-3 pb-2 text-[11px] font-semibold text-app-text-secondary">
        <span>11:38</span>
        <div className="flex items-center gap-1.5">
          {/* signal */}
          <svg className="w-3.5 h-3.5" fill="currentColor" viewBox="0 0 16 16"><rect x="1" y="11" width="2" height="3" rx="0.5"/><rect x="5" y="8" width="2" height="6" rx="0.5"/><rect x="9" y="5" width="2" height="9" rx="0.5"/><rect x="13" y="2" width="2" height="12" rx="0.5" opacity="0.4"/></svg>
          {/* wifi */}
          <svg className="w-3.5 h-3.5" fill="currentColor" viewBox="0 0 16 16"><path d="M8 12.5a1 1 0 100 2 1 1 0 000-2zM4.5 9.5a5 5 0 017 0l-1 1a3.5 3.5 0 00-5 0l-1-1zm-2-2a8 8 0 0111 0l-1 1a6.5 6.5 0 00-9 0l-1-1z"/></svg>
          {/* battery */}
          <span className="ml-0.5 inline-flex items-center px-1.5 py-0.5 rounded-md bg-emerald-500 text-white text-[9px] font-bold leading-none">100</span>
        </div>
      </div>
      {children}
    </div>
  );
}

function HomeDashboardMockup() {
  return (
    <PhoneFrame>
      {/* Header */}
      <div className="flex items-center justify-between px-5 py-3 border-b border-app-border-subtle">
        <div className="flex items-center gap-3">
          <div className="flex flex-col gap-1">
            <span className="block w-4 h-0.5 bg-app-text rounded-full" />
            <span className="block w-4 h-0.5 bg-app-text rounded-full" />
            <span className="block w-4 h-0.5 bg-app-text rounded-full" />
          </div>
          <div>
            <p className="text-base font-bold text-app-text leading-tight">Sweet Home</p>
            <p className="text-[11px] text-app-text-secondary leading-tight">Camas, WA</p>
          </div>
        </div>
        <svg className="w-5 h-5 text-app-text-secondary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
        </svg>
      </div>

      {/* Viewing-as pill */}
      <div className="px-5 pt-3">
        <span className="inline-flex items-center gap-1.5 text-[11px] font-semibold px-2.5 py-1 rounded-full bg-sky-500/15 text-sky-600 dark:text-sky-300">
          👁 Viewing as: Household
        </span>
      </div>

      {/* Tab row */}
      <div className="grid grid-cols-4 gap-1 px-3 pt-3 pb-2 text-[11px] font-semibold text-app-text-secondary">
        <span className="text-center py-1.5 rounded-md bg-sky-500 text-white">Dashboard</span>
        <span className="text-center py-1.5">Share</span>
        <span className="text-center py-1.5">Members</span>
        <span className="text-center py-1.5">Settings</span>
      </div>

      {/* Date + status banner */}
      <div className="px-3">
        <div className="rounded-xl border border-app-border-subtle p-3 mb-2">
          <div className="flex items-center justify-between mb-2">
            <div>
              <p className="text-sm font-bold text-app-text">Tuesday, April 14</p>
              <p className="text-[11px] text-app-text-secondary">0 household members</p>
            </div>
            <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-sky-500/15 text-sky-600 dark:text-sky-300">Today</span>
          </div>
          <div className="flex items-center gap-2 px-3 py-2 rounded-lg bg-emerald-500/10 border border-emerald-500/20">
            <span className="inline-flex items-center justify-center w-5 h-5 rounded-full bg-emerald-500 text-white text-[10px]">✓</span>
            <span className="text-xs text-emerald-700 dark:text-emerald-300 font-medium">All clear! Nothing needs attention.</span>
          </div>
        </div>

        {/* List rows */}
        {[
          { icon: 'ℹ️', tint: 'bg-violet-500/15 text-violet-600 dark:text-violet-300', title: 'Property Details', sub: '🏠 house', tail: 'Public records' },
          { icon: '📅', tint: 'bg-sky-500/15 text-sky-600 dark:text-sky-300', title: 'Calendar', sub: 'No upcoming events' },
          { icon: '✓', tint: 'bg-amber-500/15 text-amber-600 dark:text-amber-300', title: 'Tasks', sub: 'All tasks complete' },
          { icon: '🔧', tint: 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-300', title: 'Home Help', sub: 'No active help requests' },
          { icon: '💼', tint: 'bg-fuchsia-500/15 text-fuchsia-600 dark:text-fuchsia-300', title: 'Bills & Budget' },
        ].map((row) => (
          <div key={row.title} className="flex items-center gap-3 px-2 py-2.5 rounded-xl mb-1.5 border border-app-border-subtle">
            <span className={`inline-flex items-center justify-center w-7 h-7 rounded-full ${row.tint} text-xs`}>
              {row.icon}
            </span>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-semibold text-app-text">{row.title}</p>
              {row.sub && <p className="text-[11px] text-app-text-secondary truncate">{row.sub}</p>}
            </div>
            {row.tail && <span className="text-[11px] text-app-text-muted">{row.tail}</span>}
            <svg className="w-3.5 h-3.5 text-app-text-muted flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7"/></svg>
          </div>
        ))}
      </div>

      {/* FAB peeking */}
      <div className="relative h-12">
        <div className="absolute right-4 -top-2 w-10 h-10 rounded-full bg-sky-500 text-white flex items-center justify-center shadow-lg text-xl leading-none">+</div>
      </div>
    </PhoneFrame>
  );
}

function MailboxMockup() {
  return (
    <PhoneFrame>
      {/* Header */}
      <div className="flex items-start justify-between px-5 py-3 border-b border-app-border-subtle">
        <div>
          <p className="text-xl font-bold text-app-text leading-tight">Mailbox</p>
          <p className="text-[11px] text-app-text-secondary leading-tight mt-0.5">
            <span className="inline-block w-1.5 h-1.5 rounded-full bg-emerald-500 align-middle mr-1" />
            123 Main St, Camas WA · Verified Resident
          </p>
        </div>
        <span className="inline-flex items-center justify-center w-7 h-7 rounded-full bg-emerald-700 text-white text-xs">
          <svg className="w-3.5 h-3.5" fill="currentColor" viewBox="0 0 20 20"><path fillRule="evenodd" d="M10 9a3 3 0 100-6 3 3 0 000 6zm-7 9a7 7 0 1114 0H3z" clipRule="evenodd"/></svg>
        </span>
      </div>

      {/* Top tabs */}
      <div className="grid grid-cols-4 px-3 pt-3 text-[11px] font-semibold border-b border-app-border-subtle">
        {[
          { label: 'Me', icon: '👤', active: true },
          { label: 'Home', icon: '🏠' },
          { label: 'Biz', icon: '💼' },
          { label: 'Earn', icon: '📷' },
        ].map((t) => (
          <div key={t.label} className={`text-center py-2 ${t.active ? 'text-emerald-700 dark:text-emerald-400' : 'text-app-text-secondary'}`}>
            <div className="text-base leading-none">{t.icon}</div>
            <div className="mt-1">{t.label}</div>
            {t.active && <div className="mx-auto mt-1.5 h-0.5 w-8 rounded-full bg-emerald-700 dark:bg-emerald-400" />}
          </div>
        ))}
      </div>

      {/* Sub-tabs */}
      <div className="grid grid-cols-3 px-3 pt-3 text-[12px] font-semibold">
        <div className="text-center py-1.5 text-emerald-700 dark:text-emerald-400 border-b-2 border-emerald-700 dark:border-emerald-400">Incoming</div>
        <div className="text-center py-1.5 text-app-text-secondary">Counter</div>
        <div className="text-center py-1.5 text-app-text-secondary">Vault</div>
      </div>

      {/* Quick-action chips */}
      <div className="grid grid-cols-6 gap-1.5 px-3 py-3">
        {[
          { label: 'Records', icon: '🏡' },
          { label: 'Map', icon: '📍' },
          { label: 'Community', icon: '👥' },
          { label: 'Mail Day', icon: '📬' },
          { label: 'Stamps', icon: '💎' },
          { label: 'Wallet', icon: '💰' },
        ].map((c) => (
          <div key={c.label} className="flex flex-col items-center justify-center gap-1 py-2 rounded-xl bg-app-surface-raised border border-app-border-subtle">
            <span className="text-base leading-none">{c.icon}</span>
            <span className="text-[9px] font-semibold text-app-text-secondary truncate w-full text-center px-0.5">{c.label}</span>
          </div>
        ))}
      </div>

      {/* Empty state */}
      <div className="flex flex-col items-center justify-center text-center px-6 py-10">
        <svg className="w-12 h-12 text-app-text-muted mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M3 8l9 6 9-6M5 19h14a2 2 0 002-2V8.5a2 2 0 00-.876-1.654l-7.5-5.142a2 2 0 00-2.248 0l-7.5 5.142A2 2 0 003 8.5V17a2 2 0 002 2z" />
        </svg>
        <p className="text-base font-bold text-app-text">No new mail</p>
        <p className="text-[11px] text-app-text-secondary mt-1 max-w-[220px] leading-relaxed">
          When something arrives, it will appear here as an envelope, postcard, or package.
        </p>
      </div>
    </PhoneFrame>
  );
}

const HOME_FEATURES = [
  'Household members: Invite people, assign roles (owner, resident, guest)',
  'Home tasks: Chores, repairs, shopping lists — assignable and trackable',
  'Bills & utilities: Track what\'s due, who paid, what\'s overdue',
  'Packages: Expected, out for delivery, delivered, picked up',
  'Maintenance records: Service history, vendor contacts, warranties',
  'Documents, pets, emergency contacts, access codes',
] as const;

const MAILBOX_FEATURES = [
  'Certified mail and community notices to your verified address',
  'Package tracking and delivery alerts',
  'Document vault for leases, insurance, HOA docs',
  'Offers and rewards on your terms',
  'Works at every address you verify — home, rental, office',
] as const;

const AVAILABLE_FEATURES = [
  'Household members & roles',
  'Home tasks & bills',
  'Package tracking',
  'Certified mail',
  'Document vault',
  'Offers & coupons',
] as const;

const COMING_SOON_FEATURES = [
  'Mail translation',
  'Vacation holds',
  'Community stamps',
] as const;

export default function HouseholdSection() {
  return (
    <section id="household" className="scroll-mt-20 py-24 bg-gradient-to-br from-indigo-50 to-white dark:from-gray-950 dark:to-gray-900">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">

        {/* Section header */}
        <div className="text-center mb-14">
          <span className="inline-block text-xs font-semibold px-3 py-1.5 rounded-full bg-indigo-100 text-indigo-700 dark:bg-indigo-900/40 dark:text-indigo-300 mb-4">
            Your household
          </span>
          <h2 className="text-3xl md:text-4xl font-bold text-app-text dark:text-white mb-4">
            Your home, organized. Your mail, digital.
          </h2>
          <p className="text-lg text-app-text-secondary dark:text-app-text-muted max-w-2xl mx-auto leading-relaxed">
            Claim your address. Invite your household. Manage everything from tasks and bills to packages and important mail — all private, all in one place.
          </p>
        </div>

        {/* Two-panel layout */}
        <div className="grid md:grid-cols-2 gap-12 mb-14">

          {/* ── Left: Home Command Center ──────────────────────────────── */}
          <div>
            <div className="flex items-center gap-2 mb-6">
              <span className="text-2xl">🏠</span>
              <h3 className="text-xl font-bold text-app-text dark:text-white">Home</h3>
            </div>

            <ul className="space-y-3 mb-8">
              {HOME_FEATURES.map((feat) => (
                <li key={feat} className="flex items-start gap-2.5 text-sm text-app-text-secondary dark:text-app-text-muted">
                  <span className="text-indigo-500 mt-0.5 flex-shrink-0">✓</span>
                  {feat}
                </li>
              ))}
            </ul>

            {/* Home dashboard mockup */}
            <HomeDashboardMockup />
          </div>

          {/* ── Right: Digital Mailbox ─────────────────────────────────── */}
          <div>
            <div className="flex items-center gap-2 mb-6">
              <span className="text-2xl">📬</span>
              <h3 className="text-xl font-bold text-app-text dark:text-white">Mailbox</h3>
            </div>

            <ul className="space-y-3 mb-8">
              {MAILBOX_FEATURES.map((feat) => (
                <li key={feat} className="flex items-start gap-2.5 text-sm text-app-text-secondary dark:text-app-text-muted">
                  <span className="text-amber-500 mt-0.5 flex-shrink-0">✓</span>
                  {feat}
                </li>
              ))}
            </ul>

            {/* Mailbox mockup */}
            <MailboxMockup />
          </div>
        </div>

        {/* Feature availability grid */}
        <div className="bg-app-surface rounded-2xl border border-app-border-subtle p-6 mb-8">
          <div className="grid sm:grid-cols-2 gap-6">
            {/* Available */}
            <div>
              <p className="text-xs font-semibold text-app-text-muted uppercase tracking-wider mb-3">Available now</p>
              <ul className="space-y-2">
                {AVAILABLE_FEATURES.map((feat) => (
                  <li key={feat} className="flex items-center gap-2 text-sm text-app-text-secondary dark:text-app-text-muted">
                    <span className="text-emerald-500">✅</span>
                    {feat}
                  </li>
                ))}
              </ul>
            </div>
            {/* Coming soon */}
            <div>
              <p className="text-xs font-semibold text-app-text-muted uppercase tracking-wider mb-3">Coming soon</p>
              <ul className="space-y-2">
                {COMING_SOON_FEATURES.map((feat) => (
                  <li key={feat} className="flex items-center gap-2 text-sm text-app-text-secondary dark:text-app-text-muted">
                    <span>🔜</span>
                    {feat}
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </div>

        {/* Bottom line */}
        <p className="text-center italic text-app-text-muted dark:text-app-text-secondary text-base">
          &ldquo;Every home has a story. Pantopus gives it a system.&rdquo;
        </p>
      </div>
    </section>
  );
}
