// ─────────────────────────────────────────────────────────────────────────────
// MarketplaceSection — §7 Marketplace spotlight
// Server component (no 'use client')
// ─────────────────────────────────────────────────────────────────────────────

import Image from 'next/image';

const FEATURE_CARDS = [
  { emoji: '🤝', title: 'Offers & Counter-offers', desc: 'Make offers, negotiate, accept — all tracked in one thread' },
  { emoji: '🔄', title: 'Trade & Swap', desc: "Don't want cash? Propose a trade. The system tracks both sides." },
  { emoji: '📊', title: 'AI Price Intelligence', desc: 'See what similar items actually sold for. Price with data, not guesswork.' },
  { emoji: '🔔', title: 'Saved Searches', desc: 'Get notified when something you want is listed' },
  { emoji: '📈', title: 'Seller Analytics', desc: 'See views, saves, and offer activity on your listings' },
  { emoji: '⭐', title: 'Reputation Scores', desc: 'Built from real completed transactions, not gaming' },
] as const;

const LISTING_TYPES = [
  'Sell Item', 'Free Item', 'Wanted Request', 'Trade/Swap',
  'Rent/Sublet', 'Vehicle Sale', 'Pre-Order', 'Flash Sale', 'Recurring',
] as const;

export default function MarketplaceSection() {
  return (
    <section
      id="marketplace"
      className="scroll-mt-20 bg-app-surface-raised/40 border-y border-app-border-subtle py-24"
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">

        {/* Header */}
        <div className="text-center mb-14">
          <span className="inline-block text-xs font-semibold px-3 py-1.5 rounded-full bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300 mb-4">
            Marketplace
          </span>
          <h2 className="text-3xl md:text-4xl font-bold text-app-text dark:text-white mb-4">
            Buy, sell, trade — with people you can verify.
          </h2>
          <p className="text-lg text-app-text-secondary dark:text-app-text-muted max-w-2xl mx-auto leading-relaxed">
            Not a classified board. A real marketplace with offers, counter-offers, trades, AI-powered pricing, and reputation scores — backed by verified identity.
          </p>
        </div>

        {/* Feature cards grid */}
        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-5 mb-12">
          {FEATURE_CARDS.map(({ emoji, title, desc }) => (
            <div
              key={title}
              className="bg-app-surface rounded-2xl border border-app-border-subtle p-5 shadow-sm hover:shadow-md dark:hover:shadow-black/30 transition"
            >
              <span className="text-2xl mb-3 block">{emoji}</span>
              <h3 className="text-base font-bold text-app-text dark:text-white mb-1.5">{title}</h3>
              <p className="text-sm text-app-text-secondary dark:text-app-text-muted leading-relaxed">{desc}</p>
            </div>
          ))}
        </div>

        {/* Marketplace mockup — hand-crafted, theme-adaptive, no screenshot
            so it stays crisp at any zoom and follows light/dark automatically. */}
        <div className="mb-12 max-w-5xl mx-auto rounded-3xl overflow-hidden shadow-2xl border border-app-border-subtle bg-app-surface">

          {/* Top chrome: search + filter chips */}
          <div className="px-5 sm:px-7 pt-6 pb-4 border-b border-app-border-subtle bg-gradient-to-b from-app-surface-raised/40 to-app-surface">
            <div className="flex items-center gap-3 mb-4">
              <div className="flex-1 flex items-center gap-2 px-4 py-2.5 rounded-full bg-app-surface-sunken border border-app-border-subtle">
                <svg className="w-4 h-4 text-app-text-muted flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                </svg>
                <span className="text-sm text-app-text-muted">Search listings near you</span>
              </div>
              <span className="hidden sm:inline-flex text-xs font-semibold px-3 py-1.5 rounded-full bg-emerald-500 text-white">+ Post</span>
            </div>
            <div className="flex flex-wrap gap-1.5">
              {[
                { label: 'All', active: true },
                { label: 'Free' },
                { label: 'Wanted' },
                { label: '< 1 mi' },
                { label: '✓ Verified' },
                { label: 'Under $25' },
                { label: '$25–100' },
                { label: '$100+' },
              ].map((f) => (
                <span
                  key={f.label}
                  className={
                    f.active
                      ? 'text-xs font-semibold px-3 py-1 rounded-full bg-app-text text-app-surface'
                      : 'text-xs font-medium px-3 py-1 rounded-full border border-app-border text-app-text-secondary'
                  }
                >
                  {f.label}
                </span>
              ))}
            </div>
          </div>

          {/* Listing grid — featured hero on the left, three stacked tiles on the right */}
          <div className="p-5 sm:p-7 grid lg:grid-cols-3 gap-4">

            {/* ── Hero listing: spans 2 cols on lg ── */}
            <div className="lg:col-span-2 group rounded-2xl overflow-hidden border border-app-border-subtle bg-app-surface hover:shadow-lg transition">
              <div className="relative aspect-[16/10] overflow-hidden">
                <Image
                  src="/landing/marketplace-hero-sofa.webp"
                  alt="Mid-century velvet sofa in sage green"
                  width={1200}
                  height={750}
                  className="w-full h-full object-cover group-hover:scale-[1.02] transition-transform duration-300"
                />
                {/* Price chip top-left */}
                <span className="absolute top-3 left-3 px-3 py-1 rounded-full text-sm font-bold bg-black/70 text-white backdrop-blur-sm">$650</span>
                {/* Verified badge top-right */}
                <span className="absolute top-3 right-3 inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-emerald-500/95 text-white">
                  <svg className="w-3 h-3" fill="currentColor" viewBox="0 0 20 20"><path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd"/></svg>
                  Verified
                </span>
                {/* Photo count chip — implies real listing has multiple images */}
                <span className="absolute bottom-3 right-3 inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[11px] font-semibold bg-black/65 text-white backdrop-blur-sm">
                  <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 7h2l2-3h10l2 3h2a2 2 0 012 2v9a2 2 0 01-2 2H3a2 2 0 01-2-2V9a2 2 0 012-2z"/><circle cx="12" cy="13" r="3.5" strokeWidth={2}/></svg>
                  6
                </span>
              </div>
              <div className="p-4">
                <h3 className="text-lg font-bold text-app-text dark:text-white">Mid-Century Velvet Sofa — Sage Green</h3>
                <p className="text-sm text-app-text-secondary mt-1 line-clamp-1">Like new · 84&quot; long · Pet-free, smoke-free home · Pickup only</p>
                <div className="mt-3 flex items-center gap-3 text-xs text-app-text-muted">
                  <span className="inline-flex items-center gap-1">
                    <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17.657 16.657L13.414 20.9a2 2 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z"/><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 11a3 3 0 11-6 0 3 3 0 016 0z"/></svg>
                    0.9 mi · Camas, WA
                  </span>
                  <span>·</span>
                  <span>Posted 3h ago</span>
                </div>
              </div>
            </div>

            {/* ── Three side tiles ── */}
            <div className="flex flex-col gap-4">
              {[
                {
                  title: 'Wooden Playhouse',
                  price: '$150',
                  meta: 'Good · 0.4 mi',
                  gradient: 'from-amber-300 to-orange-400 dark:from-amber-700 dark:to-orange-800',
                  emoji: '🏡',
                  verified: true,
                },
                {
                  title: 'Modern Table Lamp',
                  price: '$50',
                  meta: 'Like new · 1.1 mi',
                  gradient: 'from-violet-300 to-fuchsia-400 dark:from-violet-700 dark:to-fuchsia-800',
                  emoji: '💡',
                  verified: false,
                  ending: true,
                },
                {
                  title: 'Logitech Wireless Mouse',
                  price: '$25',
                  meta: 'Used · 0.8 mi',
                  gradient: 'from-sky-300 to-cyan-400 dark:from-sky-700 dark:to-cyan-800',
                  emoji: '🖱️',
                  verified: true,
                },
                {
                  title: 'Home-made Dumplings (50 ct)',
                  price: '$40',
                  meta: 'Fresh · 0.6 mi',
                  gradient: 'from-rose-300 to-pink-400 dark:from-rose-700 dark:to-pink-800',
                  emoji: '🥟',
                  verified: true,
                },
                {
                  title: 'Home-made Cheesecake (12")',
                  price: '$20',
                  meta: 'Made today · 1.3 mi',
                  gradient: 'from-yellow-300 to-amber-400 dark:from-yellow-700 dark:to-amber-800',
                  emoji: '🍰',
                  verified: true,
                },
              ].map((card) => (
                <div
                  key={card.title}
                  className="group flex gap-3 rounded-2xl overflow-hidden border border-app-border-subtle bg-app-surface hover:shadow-md transition"
                >
                  {/* Thumbnail tile */}
                  <div className={`relative flex-shrink-0 w-24 h-24 bg-gradient-to-br ${card.gradient} flex items-center justify-center`}>
                    <span className="text-3xl drop-shadow-sm" aria-hidden>{card.emoji}</span>
                    <span className="absolute bottom-1 left-1 px-1.5 py-0.5 rounded text-[10px] font-bold bg-black/65 text-white backdrop-blur-sm">{card.price}</span>
                  </div>
                  {/* Body */}
                  <div className="flex-1 min-w-0 py-2 pr-2">
                    <div className="flex items-start gap-2">
                      <h4 className="text-sm font-semibold text-app-text dark:text-white truncate flex-1">{card.title}</h4>
                      {card.verified && (
                        <span className="inline-flex items-center text-emerald-600 dark:text-emerald-400" title="Verified seller">
                          <svg className="w-3.5 h-3.5" fill="currentColor" viewBox="0 0 20 20"><path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd"/></svg>
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-app-text-secondary mt-0.5 truncate">{card.meta}</p>
                    {card.ending && (
                      <p className="text-[11px] text-amber-600 dark:text-amber-400 font-medium mt-1">Ends in 2h</p>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Footer micro-stats — adds depth without crowding */}
          <div className="px-5 sm:px-7 py-3 border-t border-app-border-subtle bg-app-surface-raised/40 flex items-center justify-between text-xs text-app-text-muted">
            <span>6 listings in view · 5 verified · 1 ending soon</span>
            <span className="hidden sm:inline">Updated just now</span>
          </div>
        </div>

        {/* Listing types showcase */}
        <div className="text-center">
          <p className="text-xs font-semibold text-app-text-muted uppercase tracking-wider mb-4">Listing types</p>
          <div className="flex flex-wrap justify-center gap-2">
            {LISTING_TYPES.map((type) => (
              <span
                key={type}
                className="text-sm font-medium px-4 py-1.5 rounded-full border border-app-border text-app-text-secondary dark:text-app-text-muted hover:border-app-border-strong hover:text-app-text dark:hover:text-white transition"
              >
                {type}
              </span>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
