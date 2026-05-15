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
            {banner && (
              <BannerCard
                title={banner.title}
                subtitle={banner.subtitle}
                Icon={banner.icon}
                onTap={banner.onTap}
              />
            )}
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

function BannerCard({
  title,
  subtitle,
  Icon,
  onTap,
}: {
  title: string;
  subtitle?: string;
  Icon: import('lucide-react').LucideIcon;
  onTap?: () => void;
}) {
  const content = (
    <div className="flex items-center gap-3 px-3.5 py-3 rounded-xl bg-primary-50 border border-primary-100">
      <div className="w-8 h-8 rounded-md bg-app-surface border border-primary-100 flex items-center justify-center shrink-0">
        <Icon className="w-4 h-4 text-primary-600" />
      </div>
      <div className="flex-1 min-w-0">
        <div className="text-xs font-semibold text-app-text">{title}</div>
        {subtitle && (
          <div className="text-[11px] text-app-text-secondary mt-0.5">{subtitle}</div>
        )}
      </div>
      <ChevronRight className="w-4 h-4 text-app-text-muted shrink-0" />
    </div>
  );
  return onTap ? (
    <button type="button" onClick={onTap} className="w-full text-left">
      {content}
    </button>
  ) : (
    content
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
