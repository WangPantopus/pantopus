// Pantopus — `@/components/list-of-rows` barrel.
//
// The web mirror of the iOS / Android `ListOfRows` archetype. Feature
// screens import only the shell and the types; they never reach into
// the internals.

export { default as ListOfRowsShell } from './ListOfRowsShell';
export { default as RowCard } from './RowCard';
export { default as TabStrip } from './TabStrip';
export { default as LoadingRows } from './LoadingRows';
export { default as EmptyState } from './EmptyState';
export { default as ErrorBanner } from './ErrorBanner';
export { default as FabButton } from './FabButton';

export type {
  // Core
  RowModel,
  RowSection,
  RowTemplate,
  RowLeading,
  RowTrailing,
  RowChip,
  RowChipTint,
  RowFooter,
  RowFooterAction,
  RowHighlight,
  RowEngagement,
  RowEngagementItem,
  RowEngagementCTA,
  RowBodyEmphasis,
  // Primitives
  Bidder,
  BidderTone,
  GradientPair,
  AvatarBackground,
  AvatarBadgeSize,
  ThumbnailImage,
  ThumbnailSize,
  StatusChipVariant,
  CompactButtonVariant,
  IdentityPillar,
  VerticalAction,
  // Section
  SectionStyle,
  // Shell config
  ListOfRowsTab,
  TopBarAction,
  FabAction,
  FabVariant,
  SearchBarConfig,
  ChipStripConfig,
  ChipStripChip,
  BannerConfig,
  EmptyConfig,
  ListOfRowsState,
  ListOfRowsShellProps,
} from './types';
