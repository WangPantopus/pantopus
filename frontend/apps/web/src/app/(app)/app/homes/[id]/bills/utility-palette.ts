// T6.0a — Per-utility-category visual tokens for the Bills row.
//
// Web mirror of `frontend/apps/ios/Pantopus/Features/Homes/Bills/UtilityCategoryPalette.swift`.
// Lifted from the design at `bills-frames.jsx:53-62`. Feature code (Bills
// page row mapping + detail modal header) references these typed swatches;
// no hex literal appears in the bills page outside this file.
//
// Why not in `@pantopus/theme`? These are per-category chip pairs
// (icon-background + icon-foreground) that don't fit the existing
// `(name) → (single color)` semantic-token model. Lifting them into
// their own palette file keeps `theme` semantic-only.
//
// Per the T6 open-question Q2 decision (see
// `docs/t6-open-questions-decisions.md`), category is **client-derived
// from the payee string** — there is no backend `category` field today
// on `HomeBill`. `categoryFromPayee(...)` is the canonical inference
// helper, used by iOS, Android, and web in parallel.
//
// **Documented hex-literal exception** — every other file under
// `frontend/apps/web/src/app/**` routes through `@pantopus/theme`
// tokens. This file is the cross-platform parity surface for category
// swatches and matches the iOS `UtilityCategoryPalette.swift` design
// 1:1.

import {
  Building2,
  Droplet,
  Flame,
  Receipt,
  ShieldCheck,
  Smartphone,
  Trash2,
  Wifi,
  Zap,
  type LucideIcon,
} from 'lucide-react';

/**
 * The 8 designed utility categories + a `generic` fallback for any
 * payee the inference helper can't classify.
 */
export type UtilityCategory =
  | 'electric'
  | 'gas'
  | 'water'
  | 'internet'
  | 'hoa'
  | 'insurance'
  | 'trash'
  | 'phone'
  | 'generic';

export interface UtilityVisual {
  /** User-facing label (also doubles as a fallback payee when the
   *  backend has no provider name). */
  label: string;
  /** Lucide icon glyph for the 40px category tile. */
  icon: LucideIcon;
  /** Soft-tinted background for the 40px category tile. */
  background: string;
  /** Foreground tint for the glyph inside the 40px tile. */
  foreground: string;
}

/**
 * Visual tokens per utility category. Hex literals here are intentional
 * — this file is the documented exception (see file header). Same
 * values as iOS `UtilityCategory.{background,foreground}` and Android
 * `UtilityCategoryPalette.kt`.
 */
const VISUALS: Record<UtilityCategory, UtilityVisual> = {
  electric: {
    label: 'Electric',
    icon: Zap,
    background: '#fef9c3',
    foreground: '#a16207',
  },
  gas: {
    label: 'Gas',
    icon: Flame,
    background: '#ffedd5',
    foreground: '#c2410c',
  },
  water: {
    label: 'Water',
    icon: Droplet,
    background: '#dbeafe',
    foreground: '#1d4ed8',
  },
  internet: {
    label: 'Internet',
    icon: Wifi,
    background: '#ede9fe',
    foreground: '#6d28d9',
  },
  hoa: {
    label: 'HOA',
    icon: Building2,
    background: '#dcfce7',
    foreground: '#15803d',
  },
  insurance: {
    label: 'Insurance',
    icon: ShieldCheck,
    background: '#ccfbf1',
    foreground: '#0f766e',
  },
  trash: {
    label: 'Trash',
    icon: Trash2,
    background: '#e2e8f0',
    foreground: '#334155',
  },
  phone: {
    label: 'Phone',
    icon: Smartphone,
    background: '#fee2e2',
    foreground: '#b91c1c',
  },
  generic: {
    label: 'Bill',
    icon: Receipt,
    // Matches theme.colors.primary[50] / primary[600] tokens. Inlined
    // here so this file is self-contained for parity audits against
    // iOS UtilityCategoryPalette.swift.
    background: '#f0f9ff',
    foreground: '#0284c7',
  },
};

/**
 * Look up the visual tokens for a utility category. Stable shape so
 * call sites can destructure `{icon, background, foreground, label}`.
 */
export function utilityVisual(category: UtilityCategory): UtilityVisual {
  return VISUALS[category];
}

/**
 * Ordered pattern table. **Order matters** — first match wins, so
 * more-specific patterns sit before generic ones (e.g. "verizon
 * wireless" precedes "verizon"; "comcast" precedes generic words so a
 * fictitious "att comcast" string lands on internet not phone).
 *
 * Mirrors `UtilityCategoryPalette.swift` patterns 1:1. Adding a new
 * payee → category mapping is a one-line edit + (eventually) a test
 * fixture across the three clients.
 */
const PATTERNS: ReadonlyArray<{ category: UtilityCategory; matchers: string[] }> = [
  // Electric — utility brands first, generic "electric" last.
  {
    category: 'electric',
    matchers: [
      'pg&e',
      'pge',
      'coned',
      'con ed',
      'edison',
      'dominion',
      'duke energy',
      'eversource',
      'national grid',
      'pacificorp',
      'xcel',
      'electric',
    ],
  },
  // Gas — branded providers first, then generic.
  {
    category: 'gas',
    matchers: [
      'socalgas',
      'southern california gas',
      'atmos',
      'centerpoint',
      'national fuel',
      'spire',
      'natural gas',
      'gas company',
      'gas bill',
      ' gas',
    ],
  },
  // Water + sewer.
  {
    category: 'water',
    matchers: [
      'water board',
      'water works',
      'municipal water',
      'aqua',
      'sewer',
      'wastewater',
      'water',
    ],
  },
  // Internet ISPs + fiber.
  {
    category: 'internet',
    matchers: [
      'comcast',
      'xfinity',
      'spectrum',
      'fios',
      'starlink',
      'google fiber',
      'earthlink',
      'cox',
      'frontier',
      'centurylink',
      'viasat',
      'hughesnet',
      'internet',
    ],
  },
  // HOA + condo associations.
  {
    category: 'hoa',
    matchers: [
      'hoa',
      'homeowners association',
      'homeowners assoc',
      'condo assn',
      'condo association',
      'strata',
    ],
  },
  // Insurance carriers + generic.
  {
    category: 'insurance',
    matchers: [
      'state farm',
      'geico',
      'allstate',
      'progressive',
      'liberty mutual',
      'farmers insurance',
      'nationwide',
      'aaa auto',
      'metlife',
      'insurance',
    ],
  },
  // Trash + refuse + recycling.
  {
    category: 'trash',
    matchers: [
      'waste management',
      'republic services',
      'recology',
      'refuse',
      'trash',
      'garbage',
      'recycling',
    ],
  },
  // Phone carriers + generic.
  {
    category: 'phone',
    matchers: [
      't-mobile',
      'tmobile',
      'sprint',
      'mint mobile',
      'google fi',
      'boost mobile',
      'cricket',
      'wireless',
      'cell phone',
      'phone bill',
      'phone',
    ],
  },
];

/**
 * Client-side inference from a payee string (case-insensitive substring
 * match, first-match wins). Returns `generic` when no pattern matches.
 *
 * Per the T6 Q2 decision, this is the source of truth for category
 * until/unless the backend exposes a typed field on `HomeBill`.
 */
export function categoryFromPayee(payee: string | null | undefined): UtilityCategory {
  if (!payee) return 'generic';
  const lower = payee.toLowerCase();
  if (lower.length === 0) return 'generic';
  for (const entry of PATTERNS) {
    if (entry.matchers.some((m) => lower.includes(m))) {
      return entry.category;
    }
  }
  return 'generic';
}
