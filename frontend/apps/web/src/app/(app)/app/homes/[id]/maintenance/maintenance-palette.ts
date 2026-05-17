// T6.3b / P10 — Per-task-category visual tokens for the Maintenance
// row.
//
// Web mirror of
// `frontend/apps/ios/Pantopus/Features/Homes/Maintenance/MaintenanceCategoryPalette.swift`
// and
// `frontend/apps/android/.../ui/screens/homes/maintenance/MaintenanceCategoryPalette.kt`.
// Lifted from the design at `maintenance-frames.jsx:49-63`. Feature
// code (Maintenance page row mapping) references these typed swatches;
// no hex literal appears in the maintenance page outside this file.
//
// **Documented hex-literal exception** — every other file under
// `frontend/apps/web/src/app/**` routes through `@pantopus/theme`
// tokens. This file is the cross-platform parity surface for category
// swatches and matches the iOS / Android palettes 1:1.

import {
  BellRing,
  Bug,
  CloudRain,
  Fan,
  Flame,
  Home as HomeIcon,
  PaintRoller,
  Refrigerator,
  Sparkles,
  Trees,
  Wrench,
  Zap,
  type LucideIcon,
} from 'lucide-react';

/**
 * The 12 designed task categories + a `generic` fallback for any
 * task title the inference helper can't classify.
 */
export type MaintenanceCategory =
  | 'hvac'
  | 'plumbing'
  | 'electrical'
  | 'roof'
  | 'gutter'
  | 'appliance'
  | 'pest'
  | 'landscape'
  | 'cleaning'
  | 'painting'
  | 'safety'
  | 'chimney'
  | 'generic';

export interface MaintenanceVisual {
  /** User-facing label (matches the design `TASK` map labels; also a
   *  fallback title when the backend has no task name). */
  label: string;
  /** Lucide icon glyph for the 40px category tile. */
  icon: LucideIcon;
  /** Soft-tinted background for the 40px category tile. */
  background: string;
  /** Foreground tint for the glyph inside the 40px tile. */
  foreground: string;
}

/**
 * Visual tokens per maintenance category. Same values as iOS / Android
 * palettes.
 */
const VISUALS: Record<MaintenanceCategory, MaintenanceVisual> = {
  hvac: {
    label: 'HVAC',
    icon: Fan,
    background: '#fef3c7',
    foreground: '#a16207',
  },
  plumbing: {
    label: 'Plumbing',
    icon: Wrench,
    background: '#dbeafe',
    foreground: '#1d4ed8',
  },
  electrical: {
    label: 'Electrical',
    icon: Zap,
    background: '#fef9c3',
    foreground: '#a16207',
  },
  roof: {
    label: 'Roof',
    icon: HomeIcon,
    background: '#e2e8f0',
    foreground: '#334155',
  },
  gutter: {
    label: 'Gutters',
    icon: CloudRain,
    background: '#ccfbf1',
    foreground: '#0f766e',
  },
  appliance: {
    label: 'Appliance',
    icon: Refrigerator,
    background: '#e0e7ff',
    foreground: '#4338ca',
  },
  pest: {
    label: 'Pest',
    icon: Bug,
    background: '#fee2e2',
    foreground: '#b91c1c',
  },
  landscape: {
    label: 'Landscape',
    icon: Trees,
    background: '#dcfce7',
    foreground: '#15803d',
  },
  cleaning: {
    label: 'Cleaning',
    icon: Sparkles,
    background: '#cffafe',
    foreground: '#0e7490',
  },
  painting: {
    label: 'Painting',
    icon: PaintRoller,
    background: '#ede9fe',
    foreground: '#6d28d9',
  },
  safety: {
    label: 'Safety',
    icon: BellRing,
    background: '#fed7aa',
    foreground: '#c2410c',
  },
  chimney: {
    label: 'Chimney',
    icon: Flame,
    background: '#fecaca',
    foreground: '#b91c1c',
  },
  generic: {
    label: 'Other',
    icon: Wrench,
    // Matches primary50 / primary600 from @pantopus/theme. Inlined
    // here so this file is self-contained for parity audits.
    background: '#f0f9ff',
    foreground: '#0284c7',
  },
};

/**
 * Look up the visual tokens for a maintenance category. Stable shape
 * so call sites can destructure `{icon, background, foreground, label}`.
 */
export function maintenanceVisual(
  category: MaintenanceCategory,
): MaintenanceVisual {
  return VISUALS[category];
}

/**
 * Ordered pattern table. **Order matters** — first match wins, so
 * more-specific patterns sit before generic ones (e.g. "chimney sweep"
 * wins over "sweep" which could otherwise hit cleaning).
 *
 * Mirrors `MaintenanceCategoryPalette.{swift,kt}` patterns 1:1.
 */
const PATTERNS: ReadonlyArray<{
  category: MaintenanceCategory;
  matchers: string[];
}> = [
  // Chimney first so "chimney sweep" doesn't hit cleaning.
  { category: 'chimney', matchers: ['chimney', 'fireplace', 'flue', 'soot'] },
  // HVAC.
  {
    category: 'hvac',
    matchers: [
      'hvac',
      'furnace',
      'air condition',
      'ac unit',
      'heater',
      'boiler',
      'thermostat',
      'duct',
      'vent',
      'filter swap',
      'filter change',
      'air filter',
    ],
  },
  // Plumbing.
  {
    category: 'plumbing',
    matchers: [
      'plumbing',
      'plumber',
      'leak',
      'drain',
      'faucet',
      'water heater',
      'toilet',
      'pipe',
      'sump pump',
    ],
  },
  // Electrical.
  {
    category: 'electrical',
    matchers: [
      'electrical',
      'electrician',
      'wiring',
      'outlet',
      'breaker',
      'panel',
      'circuit',
    ],
  },
  // Roof.
  { category: 'roof', matchers: ['roof', 'shingle', 'flashing'] },
  // Gutter / downspout.
  { category: 'gutter', matchers: ['gutter', 'downspout'] },
  // Appliance.
  {
    category: 'appliance',
    matchers: [
      'appliance',
      'fridge',
      'refrigerator',
      'dishwasher',
      'washer',
      'dryer',
      'oven',
      'microwave',
      'disposal',
    ],
  },
  // Pest control.
  {
    category: 'pest',
    matchers: [
      'pest',
      'exterminate',
      'termite',
      'rodent',
      'ant',
      'roach',
      'mouse',
      'rats',
    ],
  },
  // Landscaping + yard.
  {
    category: 'landscape',
    matchers: [
      'landscape',
      'yard',
      'lawn',
      'mow',
      'garden',
      'tree',
      'hedge',
      'mulch',
      'irrigation',
    ],
  },
  // Cleaning.
  {
    category: 'cleaning',
    matchers: ['clean', 'wash', 'pressure wash', 'deep clean'],
  },
  // Painting.
  { category: 'painting', matchers: ['paint', 'stain', 'primer'] },
  // Safety — smoke + CO alarms.
  {
    category: 'safety',
    matchers: [
      'alarm',
      'smoke detector',
      'co detector',
      'carbon monoxide',
      'fire extinguisher',
      'safety check',
    ],
  },
];

/**
 * Client-side inference from a task title (case-insensitive substring
 * match, first-match wins). Returns `generic` when no pattern matches.
 *
 * Mirrors iOS / Android `MaintenanceCategory.from()` 1:1.
 */
export function categoryFromTask(
  task: string | null | undefined,
): MaintenanceCategory {
  if (!task) return 'generic';
  const lower = task.toLowerCase();
  if (lower.length === 0) return 'generic';
  for (const entry of PATTERNS) {
    if (entry.matchers.some((m) => lower.includes(m))) {
      return entry.category;
    }
  }
  return 'generic';
}

/**
 * Human-readable recurrence label for the row subtitle. Returns null
 * for `one_time` so the subtitle stays compact ("Riverside HVAC"
 * rather than "Riverside HVAC · One-time").
 */
export function recurrenceLabel(
  recurrence: HomeRecurrence | null | undefined,
): string | null {
  switch (recurrence) {
    case 'weekly':
      return 'Weekly';
    case 'monthly':
      return 'Monthly';
    case 'quarterly':
      return 'Quarterly';
    case 'yearly':
      return 'Yearly';
    case 'one_time':
    case null:
    case undefined:
    default:
      return null;
  }
}

type HomeRecurrence =
  | 'one_time'
  | 'weekly'
  | 'monthly'
  | 'quarterly'
  | 'yearly';
