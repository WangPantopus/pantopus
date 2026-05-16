// T5.4.2 Discover businesses — category visual specs.
//
// Mirrors iOS `DiscoverBusinessesViewModel.categorySpec(for:)` +
// Android `DiscoverBusinessesViewModel.categorySpec(id)` so the three
// platforms render the same chip label / icon / gradient pair for the
// same backend `category` value.
//
// Theme-token use: shared values come from `@pantopus/theme`'s
// `colors` constant. The iOS/Android-only category accents (handyman,
// cleaning, petCare, …) are NOT yet exposed by the web theme module —
// inline hex with the canonical token name in a trailing comment so a
// future cleanup can replace them once `colors.category.<name>` lands
// in `@pantopus/theme/src/colors.ts`.

import { colors } from '@pantopus/theme';
import {
  Briefcase,
  Hammer,
  Heart,
  Lightbulb,
  Package,
  PawPrint,
  Sparkles,
  type LucideIcon,
} from 'lucide-react';
import type { GradientPair } from '@/components/list-of-rows/types';

export const DiscoverBusinessesChip = {
  ALL: 'all',
  HANDYMAN: 'handyman',
  CLEANING: 'cleaning',
  PET_CARE: 'pet-care',
  PLUMBING: 'plumbing',
  TUTORING: 'tutoring',
  CHILDCARE: 'childcare',
  MOVING: 'moving',
  LAWN_CARE: 'lawn-care',
} as const;

export type DiscoverBusinessesChipId =
  (typeof DiscoverBusinessesChip)[keyof typeof DiscoverBusinessesChip];

export const DISCOVER_BUSINESSES_CHIP_ORDER: string[] = [
  DiscoverBusinessesChip.ALL,
  DiscoverBusinessesChip.HANDYMAN,
  DiscoverBusinessesChip.CLEANING,
  DiscoverBusinessesChip.PET_CARE,
  DiscoverBusinessesChip.PLUMBING,
  DiscoverBusinessesChip.TUTORING,
  DiscoverBusinessesChip.CHILDCARE,
  DiscoverBusinessesChip.MOVING,
  DiscoverBusinessesChip.LAWN_CARE,
];

/** Catch-all section id for unrecognised categories. */
export const DISCOVER_BUSINESSES_OTHER_SECTION = 'other';

export interface CategorySpec {
  id: string;
  label: string;
  icon: LucideIcon;
  gradient: GradientPair;
}

// Category accent palette — values mirror iOS `Theme.Color.<token>`
// and Android `PantopusColors.<token>` so the three platforms render
// the same gradient for the same backend category id. Inline because
// `@pantopus/theme` doesn't expose category accents on the web yet
// (tracked: add `colors.category.<name>` to the theme package and
// import here).
const HANDYMAN = '#F97316';
const CLEANING = '#27AE60';
const PET_CARE = '#E74C3C';
const TECH = '#3498DB';
const TUTORING = '#2980B9';
const CHILD_CARE = '#F39C12';
const MOVING = '#8E44AD';

const SPECS: Record<string, CategorySpec> = {
  [DiscoverBusinessesChip.ALL]: {
    id: DiscoverBusinessesChip.ALL,
    label: 'All',
    icon: Briefcase,
    gradient: { start: colors.primary[500], end: colors.primary[700] },
  },
  [DiscoverBusinessesChip.HANDYMAN]: {
    id: DiscoverBusinessesChip.HANDYMAN,
    label: 'Handyman',
    icon: Hammer,
    gradient: { start: colors.semantic.warning, end: HANDYMAN },
  },
  [DiscoverBusinessesChip.CLEANING]: {
    id: DiscoverBusinessesChip.CLEANING,
    label: 'Cleaning',
    icon: Sparkles,
    gradient: { start: colors.semantic.success, end: CLEANING },
  },
  [DiscoverBusinessesChip.PET_CARE]: {
    id: DiscoverBusinessesChip.PET_CARE,
    label: 'Pet Care',
    icon: PawPrint,
    gradient: { start: colors.semantic.error, end: PET_CARE },
  },
  [DiscoverBusinessesChip.PLUMBING]: {
    id: DiscoverBusinessesChip.PLUMBING,
    label: 'Plumbing',
    icon: Hammer,
    gradient: { start: colors.primary[500], end: TECH },
  },
  [DiscoverBusinessesChip.TUTORING]: {
    id: DiscoverBusinessesChip.TUTORING,
    label: 'Tutoring',
    icon: Lightbulb,
    gradient: { start: colors.semantic.info, end: TUTORING },
  },
  [DiscoverBusinessesChip.CHILDCARE]: {
    id: DiscoverBusinessesChip.CHILDCARE,
    label: 'Childcare',
    icon: Heart,
    gradient: { start: colors.semantic.warning, end: CHILD_CARE },
  },
  [DiscoverBusinessesChip.MOVING]: {
    id: DiscoverBusinessesChip.MOVING,
    label: 'Moving',
    icon: Package,
    gradient: { start: colors.identity.business.color, end: MOVING },
  },
  [DiscoverBusinessesChip.LAWN_CARE]: {
    id: DiscoverBusinessesChip.LAWN_CARE,
    label: 'Lawn Care',
    icon: Sparkles,
    gradient: { start: colors.semantic.success, end: colors.identity.home.color },
  },
};

const OTHER_SPEC: CategorySpec = {
  id: DISCOVER_BUSINESSES_OTHER_SECTION,
  label: 'Other',
  icon: Briefcase,
  gradient: { start: colors.text.secondary, end: colors.text.strong },
};

export function categorySpec(id: string): CategorySpec {
  return SPECS[id] ?? OTHER_SPEC;
}

/** Canonical normalisation matching iOS / Android. */
export function normalizeCategory(raw: string): string {
  return raw.trim().toLowerCase().replace(/_/g, '-').replace(/\s+/g, '-');
}

/**
 * Pick the row's primary category key. Returns the first category from
 * the backend `categories[]` that maps to a known chip id; falls back
 * to `"other"` otherwise.
 */
export function primaryCategoryKey(categories: string[]): string {
  const known = new Set(DISCOVER_BUSINESSES_CHIP_ORDER);
  for (const raw of categories) {
    const normalized = normalizeCategory(raw);
    if (known.has(normalized)) return normalized;
  }
  return DISCOVER_BUSINESSES_OTHER_SECTION;
}
