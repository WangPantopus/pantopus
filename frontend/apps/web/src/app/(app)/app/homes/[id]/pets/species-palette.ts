// T5.2.1 — Per-species visual tokens for the Pets row. Lifted from the
// design at `more-designed-pages/pets-frames.jsx:22-30`. Feature code
// (`page.tsx`) references these typed swatches; no raw hex literal
// appears in the page render code.
//
// Mirrors iOS `Core/Design/SpeciesPalette.swift` and Android
// `ui/theme/SpeciesPalette.kt` exactly — same wire enum, same five
// gradient palettes plus an `Other` fallback. Web carries the gradient
// stops as raw CSS strings because Tailwind's color-utility tokens
// don't cover this niche palette and lifting six new theme entries for
// one screen is heavier than a feature-local file.
//
// To keep the iOS / Android / web row mappers projectable in lockstep,
// the wire enum + palette-key + label triplet matches all three sides.

import { Bird, Cat, Dog, Fish, PawPrint, Turtle, type LucideIcon } from 'lucide-react';

/** Backend species enum from `backend/routes/home.js:6766`
 *  (`createPetSchema`). Lowercase to match the wire format. */
export type PetSpeciesWire =
  | 'dog'
  | 'cat'
  | 'bird'
  | 'fish'
  | 'reptile'
  | 'rabbit'
  | 'hamster'
  | 'other';

/** Title-case label for the species pill + Add Pet picker. */
export const SPECIES_LABEL: Record<PetSpeciesWire, string> = {
  dog: 'Dog',
  cat: 'Cat',
  bird: 'Bird',
  fish: 'Fish',
  reptile: 'Reptile',
  rabbit: 'Rabbit',
  hamster: 'Hamster',
  other: 'Other',
};

/** Visual bucket. Rabbit, hamster, and any unknown future species
 *  collapse to `other`. */
export type SpeciesPaletteKey = 'dog' | 'cat' | 'bird' | 'reptile' | 'fish' | 'other';

export function paletteFor(species: PetSpeciesWire): SpeciesPaletteKey {
  if (species === 'rabbit' || species === 'hamster' || species === 'other') return 'other';
  return species as SpeciesPaletteKey;
}

export function parseSpecies(raw: string | null | undefined): PetSpeciesWire {
  if (!raw) return 'other';
  const lower = raw.toLowerCase();
  switch (lower) {
    case 'dog':
    case 'cat':
    case 'bird':
    case 'fish':
    case 'reptile':
    case 'rabbit':
    case 'hamster':
      return lower;
    default:
      return 'other';
  }
}

/** Two-stop gradient (135deg) painted into the 64dp leading thumbnail. */
export interface SpeciesGradient {
  start: string;
  end: string;
}

export interface SpeciesSwatch {
  /** Gradient stops for the 64dp thumbnail background. */
  iconBackground: SpeciesGradient;
  /** Fallback icon glyph rendered when no `photo_url`. */
  iconForeground: string;
  /** Inline species pill — light tint background. */
  chipBackground: string;
  /** Inline species pill — text colour. */
  chipForeground: string;
  /** Lucide glyph used as fallback inside the thumbnail. */
  icon: LucideIcon;
}

/** Six canonical species swatches from the design. */
export const SPECIES_PALETTE: Record<SpeciesPaletteKey, SpeciesSwatch> = {
  dog: {
    iconBackground: { start: '#fed7aa', end: '#fb923c' },
    iconForeground: '#7c2d12',
    chipBackground: '#ffedd5',
    chipForeground: '#9a3412',
    icon: Dog,
  },
  cat: {
    iconBackground: { start: '#ddd6fe', end: '#a78bfa' },
    iconForeground: '#4c1d95',
    chipBackground: '#ede9fe',
    chipForeground: '#5b21b6',
    icon: Cat,
  },
  bird: {
    iconBackground: { start: '#bfdbfe', end: '#60a5fa' },
    iconForeground: '#1e3a8a',
    chipBackground: '#dbeafe',
    chipForeground: '#1e40af',
    icon: Bird,
  },
  reptile: {
    iconBackground: { start: '#bbf7d0', end: '#4ade80' },
    iconForeground: '#14532d',
    chipBackground: '#dcfce7',
    chipForeground: '#166534',
    icon: Turtle,
  },
  fish: {
    iconBackground: { start: '#a5f3fc', end: '#22d3ee' },
    iconForeground: '#155e75',
    chipBackground: '#cffafe',
    chipForeground: '#155e75',
    icon: Fish,
  },
  other: {
    iconBackground: { start: '#e5e7eb', end: '#9ca3af' },
    iconForeground: '#1f2937',
    chipBackground: '#f3f4f6',
    chipForeground: '#374151',
    icon: PawPrint,
  },
};

/** Convenience for the row mapper. */
export function swatchFor(species: PetSpeciesWire): SpeciesSwatch {
  return SPECIES_PALETTE[paletteFor(species)];
}
