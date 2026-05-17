// T6.3c — Per-chore-category visual tokens for the Household tasks row.
//
// Web mirror of
// `frontend/apps/ios/Pantopus/Features/Homes/Tasks/HouseholdTaskCategoryPalette.swift`
// and
// `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/homes/tasks/HouseholdTaskCategoryPalette.kt`.
// Lifted from the design at `householdtasks-frames.jsx:55-65`. Feature
// code (Tasks page row mapping + empty-state quick-start tiles)
// references these typed swatches; no hex literal appears in the tasks
// page outside this file.
//
// Same per-feature-palette exception as the Bills `utility-palette.ts`:
// these are per-category chip pairs (icon-background + icon-foreground)
// that don't fit the existing `(name) → (single color)` semantic-token
// model.
//
// Category is **client-derived from the task title** — there is no
// backend `category` field on `HomeTask`. The schema's `task_type`
// column carries 5 broad buckets (chore / shopping / project /
// reminder / repair) that don't match the 8 design categories
// (cleaning / trash / kitchen / laundry / yard / pet / errand /
// kids), so the inference helper maps the title to a category, with
// the `task_type` as a hint for the `other` fallback. Mirrors the
// payee-to-category pattern in `utility-palette.ts`.

import {
  Baby,
  CheckCircle2,
  Leaf,
  PawPrint,
  Shuffle,
  ShoppingBag,
  Sparkles,
  Trash2,
  Utensils,
  type LucideIcon,
} from 'lucide-react';

export type HouseholdTaskCategory =
  | 'cleaning'
  | 'trash'
  | 'kitchen'
  | 'laundry'
  | 'yard'
  | 'pet'
  | 'errand'
  | 'kids'
  | 'other';

export interface CategoryVisual {
  /** User-facing label (currently unused in the row but exposed for
   *  filter sheets / quick-start tiles). */
  label: string;
  /** Lucide icon glyph for the 40px category tile. */
  icon: LucideIcon;
  /** Soft-tinted background for the 40px category tile. */
  background: string;
  /** Foreground tint for the icon glyph inside the 40px tile. */
  foreground: string;
}

const PALETTE: Record<HouseholdTaskCategory, CategoryVisual> = {
  cleaning: {
    label: 'Cleaning',
    icon: Sparkles,
    background: '#dbeafe', // sky-100
    foreground: '#1d4ed8', // blue-700
  },
  trash: {
    label: 'Trash',
    icon: Trash2,
    background: '#e2e8f0', // slate-200
    foreground: '#334155', // slate-700
  },
  kitchen: {
    label: 'Kitchen',
    icon: Utensils,
    background: '#fef3c7', // amber-100
    foreground: '#92400e', // amber-800
  },
  laundry: {
    label: 'Laundry',
    icon: Shuffle,
    background: '#ede9fe', // violet-100
    foreground: '#6d28d9', // violet-700
  },
  yard: {
    label: 'Yard',
    icon: Leaf,
    background: '#dcfce7', // green-100
    foreground: '#15803d', // green-700
  },
  pet: {
    label: 'Pets',
    icon: PawPrint,
    background: '#ffedd5', // orange-100
    foreground: '#c2410c', // orange-700
  },
  errand: {
    label: 'Errand',
    icon: ShoppingBag,
    background: '#ccfbf1', // teal-100
    foreground: '#0f766e', // teal-700
  },
  kids: {
    label: 'Kids',
    icon: Baby,
    background: '#fce7f3', // pink-100
    foreground: '#be185d', // pink-700
  },
  other: {
    label: 'Task',
    icon: CheckCircle2,
    background: '#f3f4f6', // gray-100
    foreground: '#374151', // gray-700
  },
};

/** Look up the visual swatches for a category. */
export function taskCategoryVisual(category: HouseholdTaskCategory): CategoryVisual {
  return PALETTE[category];
}

interface Pattern {
  category: HouseholdTaskCategory;
  matchers: string[];
}

/** Ordered pattern table — first match wins. More specific patterns
 *  precede generic ones (e.g. "dishwasher" precedes "dish"). */
const PATTERNS: Pattern[] = [
  {
    category: 'trash',
    matchers: ['trash', 'garbage', 'recycle', 'recycling', 'rubbish', 'bin out', 'bins out', 'compost'],
  },
  {
    category: 'pet',
    matchers: [
      'walk the dog', 'walk dog', 'dog walk', 'feed the dog', 'feed the cat',
      'litter box', 'dog', ' cat ', 'puppy', ' pet ', 'pet ', 'vet ',
    ],
  },
  {
    category: 'kitchen',
    matchers: [
      'dishwasher', 'dishes', 'dish', 'cook', 'meal', 'fridge',
      'groceries away', 'stove', 'oven',
    ],
  },
  {
    category: 'laundry',
    matchers: [
      'laundry', 'wash clothes', 'fold clothes', 'fold the laundry',
      'dryer', 'ironing', 'iron the',
    ],
  },
  {
    category: 'yard',
    matchers: [
      'water plants', 'water the plants', 'plants', 'garden', 'mow', 'lawn',
      'rake', 'leaves', 'yard', 'porch', 'weed',
    ],
  },
  {
    category: 'cleaning',
    matchers: [
      'vacuum', 'clean', 'dust', 'mop', 'wipe', 'scrub', 'sweep',
      'tidy', 'bathroom', 'bedroom',
    ],
  },
  {
    category: 'errand',
    matchers: [
      'costco', 'grocery', 'groceries', 'shopping', 'shop ', 'pick up',
      'pickup', 'buy ', 'errand', 'store run', 'post office', 'pharmacy',
    ],
  },
  {
    category: 'kids',
    matchers: [
      'kid', 'kids', 'school', 'homework', 'lunchbox', 'lunchboxes',
      'daycare', 'playdate', 'baby ', 'diaper',
    ],
  },
];

/**
 * Client-side inference from a task title + optional `task_type`
 * (case-insensitive substring match, first-match wins). Returns
 * `'other'` when no pattern matches. Mirrors the iOS / Android
 * helpers 1:1 so all three platforms tag the same row the same way.
 */
export function categoryFromTitle(
  title: string | null | undefined,
  taskType?: string | null,
): HouseholdTaskCategory {
  if (!title || title.length === 0) {
    return (taskType ?? '').toLowerCase() === 'shopping' ? 'errand' : 'other';
  }
  const lower = title.toLowerCase();
  for (const entry of PATTERNS) {
    if (entry.matchers.some((m) => lower.includes(m))) {
      return entry.category;
    }
  }
  return (taskType ?? '').toLowerCase() === 'shopping' ? 'errand' : 'other';
}
