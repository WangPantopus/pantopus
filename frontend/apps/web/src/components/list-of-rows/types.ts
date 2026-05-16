// Pantopus — shared `<ListOfRowsShell />` types.
//
// Mirrors the iOS `RowModel.swift` and Android `RowModel.kt` data
// contract. Every web row screen (Notifications V2, My posts, My bids,
// My tasks V2, Connections, Discover hub, Bills, Pets, Offers, Listing
// offers, Review claims) builds a `RowModel[]` from its backend DTOs
// and hands the list to `<ListOfRowsShell />`. The same projection
// runs on iOS / Android with identical field names, so a row mapper is
// a pure projection that's trivial to keep in sync across platforms.

import type { LucideIcon } from 'lucide-react';

// ─── Status chip ───────────────────────────────────────────────

export type StatusChipVariant =
  | 'success'
  | 'warning'
  | 'error'
  | 'info'
  | 'personal'
  | 'home'
  | 'business'
  | 'neutral';

// ─── Visual primitives ─────────────────────────────────────────

export interface GradientPair {
  /** Tailwind-tolerant CSS color OR a `linear-gradient(...)` start stop. */
  start: string;
  end: string;
}

export type AvatarBadgeSize = 'small' | 'medium' | 'large';

export type AvatarBackground =
  | { kind: 'solid'; color: string }
  | { kind: 'gradient'; gradient: GradientPair };

export type ThumbnailImage =
  | { kind: 'icon'; icon: LucideIcon; gradient: GradientPair }
  | { kind: 'url'; url: string; fallback: LucideIcon; gradient: GradientPair };

export type ThumbnailSize = 'medium' | 'large';

export type BidderTone = 'sky' | 'teal' | 'amber' | 'rose' | 'violet' | 'slate';

export interface Bidder {
  id: string;
  initials: string;
  tone: BidderTone;
}

// ─── Split stack (T6.0a Bills) ─────────────────────────────────

/**
 * One member of a bill-split stack. Smaller geometry (18px vs Bidder
 * 22px) and right-aligned so it reads as a property tag rather than
 * social-proof competition. Tone palette is shared with `Bidder` so the
 * two surfaces draw from the same 6-color set.
 */
export interface SplitMember {
  id: string;
  initials: string;
  tone: BidderTone;
}

/**
 * Split-payer stack payload rendered at the RIGHT EDGE of the chip row
 * on Bills rows (T6.0a). Differs from `BidderStackData` in geometry
 * (18px vs 22px) + alignment (right vs left of the chips) + caption —
 * "Split N ways" is shown alongside the avatars.
 *
 * Kept as a separate field on `RowModel` (`splitWith`) so the renderer
 * can place each in the correct slot. The shell ships the type today
 * but Bills rows never populate it yet — backend `/api/homes/:id/bills`
 * doesn't surface split membership on the list endpoint. Splits remain
 * visible only on the detail screen until a backend follow-up extends
 * the list response with `split_members[≤3]` + `split_total_ways`.
 */
export interface SplitStackData {
  members: SplitMember[];
  overflow?: number;
  /** Total people in the split including the viewer; drives the
   *  "Split N ways" caption. */
  totalWays: number;
}

export type IdentityPillar = 'personal' | 'home' | 'business';

// ─── Leading ───────────────────────────────────────────────────

export type RowLeading =
  | { kind: 'icon'; icon: LucideIcon; tint?: string }
  | {
      kind: 'avatar';
      name: string;
      imageURL?: string | null;
      identity: IdentityPillar;
      ringProgress: number;
    }
  | { kind: 'none' }
  | { kind: 'typeIcon'; icon: LucideIcon; background: string; foreground: string }
  | { kind: 'categoryGradientIcon'; icon: LucideIcon; gradient: GradientPair }
  | {
      kind: 'avatarWithBadge';
      name: string;
      imageURL?: string | null;
      background: AvatarBackground;
      size: AvatarBadgeSize;
      verified?: boolean;
    }
  | { kind: 'thumbnail'; image: ThumbnailImage; size: ThumbnailSize }
  | { kind: 'bidderStack'; bidders: Bidder[]; overflow?: number };

// ─── Trailing ──────────────────────────────────────────────────

export type CompactButtonVariant = 'primary' | 'ghost' | 'destructive';

export interface VerticalAction {
  label: string;
  variant: CompactButtonVariant;
  onClick: () => void;
}

export type RowTrailing =
  | { kind: 'statusChip'; text: string; variant: StatusChipVariant; icon?: LucideIcon }
  | { kind: 'chevron' }
  | { kind: 'kebab' }
  | { kind: 'none' }
  | {
      kind: 'amountWithChip';
      amount: string;
      chipText: string;
      chipVariant: StatusChipVariant;
      chipIcon?: LucideIcon;
    }
  | {
      kind: 'circularAction';
      icon: LucideIcon;
      accessibilityLabel: string;
      background?: string;
      foreground?: string;
      onClick: () => void;
    }
  | { kind: 'verticalActions'; primary: VerticalAction; secondary: VerticalAction }
  | { kind: 'priceStack'; amount: string; sublabel?: string };

// ─── Chip / footer / highlight ─────────────────────────────────

export type RowChipTint =
  | { kind: 'status'; variant: StatusChipVariant }
  | { kind: 'custom'; background: string; foreground: string };

export interface RowChip {
  text: string;
  icon?: LucideIcon;
  tint: RowChipTint;
}

export interface RowFooterAction {
  title: string;
  icon?: LucideIcon;
  variant?: CompactButtonVariant;
  /** Flex weight inside the footer row. Default 1. */
  flex?: number;
  onClick: () => void;
}

export interface RowFooter {
  actions: RowFooterAction[];
}

export type RowHighlight = 'unread' | 'leading' | 'archived' | 'muted';

// ─── Engagement footer (T5.3.3 My posts) ───────────────────────

export interface RowEngagementItem {
  id: string;
  icon: LucideIcon;
  label: string;
}

export interface RowEngagementCTA {
  label: string;
  icon?: LucideIcon;
  accessibilityLabel?: string;
  onClick: () => void;
}

export interface RowEngagement {
  items: RowEngagementItem[];
  cta?: RowEngagementCTA;
}

// ─── Body emphasis (T5.3.3 My posts) ───────────────────────────

/**
 * `secondary` (default) — 12px caption, secondary text colour
 * (Notifications V2 body line).
 * `primary` — 14px small, primary text colour (My posts body IS the
 * row's headline content).
 */
export type RowBodyEmphasis = 'secondary' | 'primary';

// ─── RowModel ──────────────────────────────────────────────────

export type RowTemplate = 'statusChip' | 'fileChevron' | 'avatarKebab';

export interface RowModel {
  id: string;
  title: string;
  subtitle?: string | null;
  template: RowTemplate;
  leading?: RowLeading;
  trailing?: RowTrailing;
  onTap?: () => void;
  onSecondary?: () => void;

  // T5 additions — every field optional with `undefined` default
  body?: string | null;
  bodyEmphasis?: RowBodyEmphasis;
  inlineChip?: RowChip;
  chips?: RowChip[];
  /** Chip row above the body (My posts intent header). */
  headerChips?: RowChip[];
  timeMeta?: string;
  metaTail?: string;
  note?: string | null;
  highlight?: RowHighlight;
  footer?: RowFooter;
  /** Hairline-separated engagement strip below the body (My posts). */
  engagement?: RowEngagement;
  /**
   * T6.0a — split-payer stack rendered at the RIGHT EDGE of the chip
   * row (Bills "Split N ways" + 18px avatars). Future-ready field —
   * Bills rows never populate it today because the backend list
   * endpoint doesn't surface split membership yet. The shell renders
   * the stack when set so feature code can wire it up additively once
   * the backend response carries `split_members[≤3]` + `split_total_ways`.
   */
  splitWith?: SplitStackData;
}

// ─── Section ───────────────────────────────────────────────────

export type SectionStyle = 'flat' | 'card';

export interface RowSection {
  id: string;
  header?: string | null;
  rows: RowModel[];
  count?: number;
  onSeeAll?: () => void;
  style?: SectionStyle;
}

// ─── Shell config ──────────────────────────────────────────────

export interface ListOfRowsTab {
  id: string;
  label: string;
  count?: number;
}

export interface TopBarAction {
  icon: LucideIcon;
  accessibilityLabel: string;
  onClick: () => void;
  /** Text-button label. When set, the shell renders the text in primary
   *  tint instead of the icon. Used by Notifications V2 "Mark all read". */
  label?: string;
  /** When false, renders the action greyed-out and ignores taps. */
  isEnabled?: boolean;
}

export type FabVariant =
  | { kind: 'canonicalCreate' }
  | { kind: 'secondaryCreate' }
  | { kind: 'extendedNav'; label: string };

/**
 * Identity tint for a FAB. Default `sky` keeps every existing FAB call
 * site rendering with the T5 sky-blue background. T6.0a added `home`
 * and `business` so home-pillar screens (Bills, Maintenance, Calendar)
 * and business-pillar screens (My businesses) can swap the FAB color
 * to match identity without forking the variant taxonomy.
 */
export type FabTint = 'sky' | 'home' | 'business';

export interface FabAction {
  icon: LucideIcon;
  accessibilityLabel: string;
  variant?: FabVariant;
  /** Identity tint. Default `sky`. */
  tint?: FabTint;
  onClick: () => void;
}

export interface SearchBarConfig {
  placeholder: string;
  value: string;
  onChange: (next: string) => void;
  onSubmit?: () => void;
}

export interface ChipStripChip {
  id: string;
  label: string;
  icon?: LucideIcon;
}

export interface ChipStripConfig {
  chips: ChipStripChip[];
  selectedId: string;
  onSelect: (id: string) => void;
}

/**
 * Tint options for a banner background and its optional trailing CTA
 * pill. Resolved at render time to the matching token pair. `primary`
 * (sky) is the default — preserves T5 behaviour. `home` is used by
 * Bills (T6.0a) per the home-pillar identity; `business` mirrors that
 * for business surfaces; `warning` is reserved for overdue surfaces.
 */
export type BannerCtaTint = 'primary' | 'home' | 'business' | 'warning';

/**
 * Optional trailing CTA on a banner (Bills "Pay all" — T6.0a).
 * When set, the banner renders the CTA as a tinted pill on the trailing
 * edge and disables the whole-card `onTap` — the CTA's `onClick` is the
 * focused action. When unset, banner-wide tap behavior is unchanged.
 */
export interface BannerCta {
  label: string;
  icon?: LucideIcon;
  accessibilityLabel?: string;
  /** Tint for the CTA pill. Defaults to `primary`. */
  tint?: BannerCtaTint;
  onClick: () => void;
}

export interface BannerConfig {
  icon: LucideIcon;
  title: string;
  subtitle?: string;
  onTap?: () => void;
  /** T6.0a — optional trailing CTA pill (Bills "Pay all"). */
  cta?: BannerCta;
  /** T6.0a — overrides the banner background + border tint. Default
   *  `primary` matches T5 behaviour. Bills uses `home`. */
  tint?: BannerCtaTint;
}

export interface EmptyConfig {
  icon: LucideIcon;
  headline: string;
  subcopy: string;
  ctaTitle?: string;
  onCta?: () => void;
}

export type ListOfRowsState =
  | { kind: 'loading' }
  | { kind: 'loaded'; sections: RowSection[]; hasMore?: boolean }
  | { kind: 'empty'; config: EmptyConfig }
  | { kind: 'error'; message: string };

export interface ListOfRowsShellProps {
  title: string;
  state: ListOfRowsState;
  onRefresh?: () => void;
  onLoadMore?: () => void;
  tabs?: ListOfRowsTab[];
  selectedTab?: string;
  onTabChange?: (id: string) => void;
  topBarAction?: TopBarAction;
  fab?: FabAction;
  searchBar?: SearchBarConfig;
  chipStrip?: ChipStripConfig;
  banner?: BannerConfig;
}
