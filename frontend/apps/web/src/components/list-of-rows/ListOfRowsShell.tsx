'use client';

// Pantopus — `<ListOfRowsShell />` is the web mirror of the iOS /
// Android `ListOfRows` archetype. Concrete screens (Notifications V2,
// My posts, My bids, My tasks V2, Connections, Discover hub, Bills,
// Pets, Offers, Listing offers, Review claims) build their UI state
// with `@tanstack/react-query` and feed the projection in here.
//
// Token-only — every colour comes from `@pantopus/theme` via Tailwind
// utility classes. No inline hex literals at the shell level.

import { useEffect, useRef } from 'react';
import { ChevronLeft, ChevronRight, Search } from 'lucide-react';
import type {
  BannerConfig,
  BannerCtaTint,
  ChipStripChip,
  ListOfRowsShellProps,
  RowSection,
  TopBarAction,
} from './types';
import TabStrip from './TabStrip';
import LoadingRows from './LoadingRows';
import EmptyState from './EmptyState';
import ErrorBanner from './ErrorBanner';
import FabButton from './FabButton';
import RowCard from './RowCard';

export default function ListOfRowsShell(props: ListOfRowsShellProps) {
  const {
    title,
    state,
    onRefresh,
    onLoadMore,
    tabs,
    selectedTab,
    onTabChange,
    topBarAction,
    fab,
    searchBar,
    chipStrip,
    banner,
  } = props;

  // Infinite-scroll sentinel
  const sentinelRef = useRef<HTMLDivElement | null>(null);
  useEffect(() => {
    if (!onLoadMore) return;
    if (state.kind !== 'loaded' || !state.hasMore) return;
    const node = sentinelRef.current;
    if (!node) return;
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            onLoadMore();
            break;
          }
        }
      },
      { rootMargin: '200px' },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, [onLoadMore, state]);

  return (
    <div className="flex flex-col min-h-full bg-app-bg" data-testid="listOfRowsContainer">
      {/* Top bar */}
      <header className="sticky top-0 z-10 flex items-center h-13 px-3 bg-app-surface border-b border-app-border">
        <button
          type="button"
          className="w-9 h-9 inline-flex items-center justify-center text-app-text rounded-md hover:bg-app-hover"
          aria-label="Back"
          onClick={() => window.history.back()}
        >
          <ChevronLeft className="w-[22px] h-[22px]" />
        </button>
        <h1 className="flex-1 text-center text-base font-semibold text-app-text tracking-tight">
          {title}
        </h1>
        <div className="min-w-[36px] h-9 flex items-center justify-end pr-1">
          {topBarAction && <TopBarActionButton action={topBarAction} />}
        </div>
      </header>

      {/* Optional search bar */}
      {searchBar && (
        <div className="bg-app-surface border-b border-app-border-subtle px-4 py-2">
          <div className="flex items-center gap-2 bg-app-surface-sunken rounded-lg px-3 py-2">
            <Search className="w-4 h-4 text-app-text-secondary" />
            <input
              type="text"
              value={searchBar.value}
              onChange={(e) => searchBar.onChange(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') searchBar.onSubmit?.();
              }}
              placeholder={searchBar.placeholder}
              className="flex-1 bg-transparent outline-none text-sm text-app-text placeholder:text-app-text-muted"
              data-testid="listOfRowsSearchBar"
            />
          </div>
        </div>
      )}

      {/* Optional chip strip (alt to tabs) */}
      {chipStrip ? (
        <ChipStripRow chipStrip={chipStrip} />
      ) : tabs && tabs.length > 0 ? (
        <TabStrip
          tabs={tabs}
          selectedId={selectedTab ?? tabs[0].id}
          onSelect={onTabChange ?? (() => {})}
        />
      ) : null}

      {/* Body */}
      <div className="flex-1 relative">
        {state.kind === 'loading' && <LoadingRows />}
        {state.kind === 'error' && (
          <ErrorBanner message={state.message} onRetry={onRefresh ?? (() => {})} />
        )}
        {state.kind === 'empty' && <EmptyState config={state.config} />}
        {state.kind === 'loaded' && (
          <div className="px-4 py-4 space-y-4">
            {banner && <BannerCard config={banner} />}
            {state.sections.map((section) => (
              <SectionView key={section.id} section={section} />
            ))}
            {state.hasMore && (
              <div
                ref={sentinelRef}
                className="flex items-center justify-center py-4 text-xs text-app-text-muted"
              >
                Loading more…
              </div>
            )}
          </div>
        )}
      </div>

      {/* FAB */}
      {fab && <FabButton fab={fab} />}
    </div>
  );
}

function TopBarActionButton({ action }: { action: TopBarAction }) {
  const enabled = action.isEnabled ?? true;
  if (action.label) {
    return (
      <button
        type="button"
        onClick={action.onClick}
        disabled={!enabled}
        aria-label={action.accessibilityLabel}
        data-testid="listOfRowsTopBarAction"
        className={`px-2.5 h-9 inline-flex items-center justify-center rounded-md text-xs font-semibold whitespace-nowrap transition disabled:cursor-default ${
          enabled
            ? 'text-primary-600 hover:bg-app-hover'
            : 'text-app-text-muted'
        }`}
      >
        {action.label}
      </button>
    );
  }
  return (
    <button
      type="button"
      onClick={action.onClick}
      disabled={!enabled}
      aria-label={action.accessibilityLabel}
      data-testid="listOfRowsTopBarAction"
      className={`w-9 h-9 inline-flex items-center justify-center rounded-md ${
        enabled ? 'text-app-text hover:bg-app-hover' : 'text-app-text-muted'
      }`}
    >
      <action.icon className="w-[22px] h-[22px]" />
    </button>
  );
}

function ChipStripRow({ chipStrip }: { chipStrip: { chips: ChipStripChip[]; selectedId: string; onSelect: (id: string) => void } }) {
  return (
    <div className="bg-app-surface border-b border-app-border-subtle overflow-x-auto">
      <div className="flex gap-2 px-4 py-2 w-max">
        {chipStrip.chips.map((chip) => {
          const active = chip.id === chipStrip.selectedId;
          const Icon = chip.icon;
          return (
            <button
              key={chip.id}
              type="button"
              onClick={() => chipStrip.onSelect(chip.id)}
              data-testid={`chip.${chip.id}`}
              aria-pressed={active}
              className={`inline-flex items-center gap-1.5 px-3 h-[30px] rounded-full text-xs font-semibold transition border ${
                active
                  ? 'bg-primary-600 text-white border-primary-600'
                  : 'bg-app-surface text-app-text-strong border-app-border hover:bg-app-hover'
              }`}
            >
              {Icon && <Icon className="w-3 h-3" />}
              {chip.label}
            </button>
          );
        })}
      </div>
    </div>
  );
}

/**
 * Resolved background / border / foreground classes for a banner tint.
 * Mirrors the iOS `BannerTokens` switch so the two platforms render
 * identical surfaces.
 */
function bannerTintClasses(tint: BannerCtaTint): {
  background: string;
  border: string;
  iconFg: string;
} {
  switch (tint) {
    case 'primary':
      return {
        background: 'bg-primary-50',
        border: 'border-primary-100',
        iconFg: 'text-primary-600',
      };
    case 'home':
      return {
        background: 'bg-app-home-bg',
        border: 'border-app-home-bg',
        iconFg: 'text-app-home',
      };
    case 'business':
      return {
        background: 'bg-app-business-bg',
        border: 'border-app-business-bg',
        iconFg: 'text-app-business',
      };
    case 'warning':
      return {
        background: 'bg-app-warning-bg',
        border: 'border-app-warning-bg',
        iconFg: 'text-app-warning',
      };
  }
}

/** Solid fill class used by the banner CTA pill / FAB. */
function tintFillClasses(tint: BannerCtaTint): string {
  switch (tint) {
    case 'primary':
      return 'bg-primary-600 hover:bg-primary-700';
    case 'home':
      return 'bg-app-home hover:opacity-90';
    case 'business':
      return 'bg-app-business hover:opacity-90';
    case 'warning':
      return 'bg-app-warning hover:opacity-90';
  }
}

function BannerCard({ config }: { config: BannerConfig }) {
  const tint = config.tint ?? 'primary';
  const tokens = bannerTintClasses(tint);
  const Icon = config.icon;
  const cta = config.cta;
  const iconTileBorder = tint === 'primary' ? 'border-primary-100' : tokens.border;
  const body = (
    <div
      className={`flex items-center gap-3 px-3.5 py-3 rounded-xl border ${tokens.background} ${tokens.border}`}
    >
      <div
        className={`w-8 h-8 rounded-md bg-app-surface border flex items-center justify-center shrink-0 ${iconTileBorder}`}
      >
        <Icon className={`w-4 h-4 ${tokens.iconFg}`} />
      </div>
      <div className="flex-1 min-w-0">
        <div className="text-xs font-semibold text-app-text">{config.title}</div>
        {config.subtitle && (
          <div className="text-[11px] text-app-text-secondary mt-0.5">{config.subtitle}</div>
        )}
      </div>
      {cta ? (
        <BannerCtaButton cta={cta} fallbackTint={tint} />
      ) : (
        // T6.0a: kept the trailing chevron on the no-cta path so
        // existing banner consumers (review-claims, etc.) render
        // identically to T5. iOS doesn't show a chevron — the web
        // archetype has always carried it as a visual affordance.
        <ChevronRight className="w-4 h-4 text-app-text-muted shrink-0" />
      )}
    </div>
  );
  // When a CTA is present the focused action is the pill — don't wrap
  // the whole card in a button (that would steal taps on the CTA).
  if (!cta && config.onTap) {
    return (
      <button type="button" onClick={config.onTap} className="w-full text-left">
        {body}
      </button>
    );
  }
  return body;
}

function BannerCtaButton({
  cta,
  fallbackTint,
}: {
  cta: NonNullable<BannerConfig['cta']>;
  fallbackTint: BannerCtaTint;
}) {
  const Icon = cta.icon;
  const tint = cta.tint ?? fallbackTint;
  const fill = tintFillClasses(tint);
  return (
    <button
      type="button"
      onClick={(e) => {
        e.stopPropagation();
        cta.onClick();
      }}
      aria-label={cta.accessibilityLabel ?? cta.label}
      className={`inline-flex items-center gap-1 px-3 py-[7px] rounded-md text-[11.5px] font-semibold text-white shrink-0 transition ${fill}`}
    >
      {Icon && <Icon className="w-3.5 h-3.5" />}
      {cta.label}
    </button>
  );
}

function SectionView({ section }: { section: RowSection }) {
  const style = section.style ?? 'flat';
  return (
    <div>
      {(section.header || section.count != null || section.onSeeAll) && (
        <div className="flex items-baseline gap-2 py-2 px-1">
          {section.header && (
            <span className="text-[11px] font-bold uppercase tracking-wider text-app-text-secondary">
              {section.header}
            </span>
          )}
          {section.count != null && (
            <span className="text-[11px] text-app-text-muted">({section.count})</span>
          )}
          <span className="flex-1" />
          {section.onSeeAll && (
            <button
              type="button"
              onClick={section.onSeeAll}
              className="inline-flex items-center gap-1 text-[11.5px] font-semibold text-primary-600 hover:text-primary-700"
              aria-label={section.header ? `See all ${section.header}` : 'See all'}
            >
              See all
              <ChevronRight className="w-3 h-3" />
            </button>
          )}
        </div>
      )}
      {style === 'card' ? (
        <div className="rounded-xl bg-app-surface border border-app-border overflow-hidden">
          {section.rows.map((row, idx) => (
            <RowCard
              key={row.id}
              row={row}
              context="grouped"
              isLastInGroup={idx === section.rows.length - 1}
            />
          ))}
        </div>
      ) : (
        <div className="space-y-2.5">
          {section.rows.map((row) => (
            <RowCard key={row.id} row={row} />
          ))}
        </div>
      )}
    </div>
  );
}
