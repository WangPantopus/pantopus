// T5.4.2 Discover businesses — category visual specs.
//
// Mirrors iOS `DiscoverBusinessesViewModel.categorySpec(for:)` +
// Android `DiscoverBusinessesViewModel.categorySpec(id)` so the three
// platforms render the same chip label / icon / gradient pair for the
// same backend `category` value.

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

const SPECS: Record<string, CategorySpec> = {
  [DiscoverBusinessesChip.ALL]: {
    id: DiscoverBusinessesChip.ALL,
    label: 'All',
    icon: Briefcase,
    gradient: { start: '#0EA5E9', end: '#0369A1' }, // primary500 → primary700
  },
  [DiscoverBusinessesChip.HANDYMAN]: {
    id: DiscoverBusinessesChip.HANDYMAN,
    label: 'Handyman',
    icon: Hammer,
    gradient: { start: '#D97706', end: '#F97316' }, // warning → handyman
  },
  [DiscoverBusinessesChip.CLEANING]: {
    id: DiscoverBusinessesChip.CLEANING,
    label: 'Cleaning',
    icon: Sparkles,
    gradient: { start: '#059669', end: '#27AE60' }, // success → cleaning
  },
  [DiscoverBusinessesChip.PET_CARE]: {
    id: DiscoverBusinessesChip.PET_CARE,
    label: 'Pet Care',
    icon: PawPrint,
    gradient: { start: '#DC2626', end: '#E74C3C' }, // error → petCare
  },
  [DiscoverBusinessesChip.PLUMBING]: {
    id: DiscoverBusinessesChip.PLUMBING,
    label: 'Plumbing',
    icon: Hammer,
    gradient: { start: '#0EA5E9', end: '#3498DB' }, // primary500 → tech
  },
  [DiscoverBusinessesChip.TUTORING]: {
    id: DiscoverBusinessesChip.TUTORING,
    label: 'Tutoring',
    icon: Lightbulb,
    gradient: { start: '#0284C7', end: '#2980B9' }, // info → tutoring
  },
  [DiscoverBusinessesChip.CHILDCARE]: {
    id: DiscoverBusinessesChip.CHILDCARE,
    label: 'Childcare',
    icon: Heart,
    gradient: { start: '#D97706', end: '#F39C12' }, // warning → childCare
  },
  [DiscoverBusinessesChip.MOVING]: {
    id: DiscoverBusinessesChip.MOVING,
    label: 'Moving',
    icon: Package,
    gradient: { start: '#7C3AED', end: '#8E44AD' }, // business → moving
  },
  [DiscoverBusinessesChip.LAWN_CARE]: {
    id: DiscoverBusinessesChip.LAWN_CARE,
    label: 'Lawn Care',
    icon: Sparkles,
    gradient: { start: '#059669', end: '#16A34A' }, // success → home
  },
};

const OTHER_SPEC: CategorySpec = {
  id: DISCOVER_BUSINESSES_OTHER_SECTION,
  label: 'Other',
  icon: Briefcase,
  gradient: { start: '#6B7280', end: '#374151' }, // appTextSecondary → appTextStrong
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
