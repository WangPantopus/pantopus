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

export type RowHighlight = 'unread' | 'leading' | 'archived';

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
  inlineChip?: RowChip;
  chips?: RowChip[];
  timeMeta?: string;
  metaTail?: string;
  note?: string | null;
  highlight?: RowHighlight;
  footer?: RowFooter;
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

export interface FabAction {
  icon: LucideIcon;
  accessibilityLabel: string;
  variant?: FabVariant;
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

export interface BannerConfig {
  icon: LucideIcon;
  title: string;
  subtitle?: string;
  onTap?: () => void;
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
