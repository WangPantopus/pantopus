// Pantopus — `<MailItemDetailShell />` types.
//
// T6.5a (P19) — Web mirror of the iOS `MailItemDetailShell.swift` and
// Android `MailItemDetailShell.kt` data contract. P20–P23 variants
// (Generic, Booklet, Certified, Community, Ceremonial) build these
// payloads from their backend DTOs and hand them in.
//
// Slots in render order:
//   1. Top nav bar           (required — `MailTopBarConfig`)
//   2. Hero card             (generic `ReactNode`)
//   3. AI elf strip          (optional — `AIElfStripContent`)
//   4. Key facts panel       (generic `ReactNode`)
//   5. Body card             (generic `ReactNode`)
//   6. Attachments row       (optional — `AttachmentsRowContent`)
//   7. Sender card           (generic `ReactNode`)
//   8. Action buttons        (sticky bottom — generic `ReactNode`)

import type { LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';

// ── Trust ────────────────────────────────────────────────

/** Trust level for the eyebrow dot on the top bar. */
export type MailDetailTrust = 'verified' | 'neutral' | 'warning';

// ── Top bar ──────────────────────────────────────────────

/** One row in the overflow menu (Forward / Archive / Mark unread / Delete / Report). */
export interface MailOverflowItem {
  id: string;
  icon: LucideIcon;
  label: string;
  /** Renders the row in error-tinted red and adds a `role="menuitem"` destructive hint. */
  isDestructive?: boolean;
  onSelect: () => void;
}

/** Pre-overflow icon button (bookmark / pin / save). */
export interface MailTopBarTrailingAction {
  icon: LucideIcon;
  accessibilityLabel: string;
  /** When `true`, the button paints in primary-100 + primary-600 to signal an "on" state. */
  isActive?: boolean;
  onClick: () => void;
}

/** Required configuration for the top nav bar. */
export interface MailTopBarConfig {
  /** Eyebrow label sandwiched between back button + actions. `null` hides the eyebrow. */
  eyebrow: string | null;
  trust: MailDetailTrust;
  /** "Back" callback. When `null` the leading chevron is hidden. */
  onBack: (() => void) | null;
  trailingAction?: MailTopBarTrailingAction;
  overflowItems?: MailOverflowItem[];
}

// ── AI elf strip ─────────────────────────────────────────

/** One bullet in the AI elf summary. */
export interface AIElfBullet {
  id: string;
  icon: LucideIcon;
  label: string;
  text?: string;
}

/**
 * Sparkles-headed extracted-info strip. Per `mail-detail.jsx:137`,
 * rendered as a sky-tinted gradient card with a sparkles disc + headline +
 * summary paragraph + bullet list, plus an optional trailing badge
 * (e.g. "2 min summary").
 */
export interface AIElfStripContent {
  /** Bold sentence at the top of the card. Defaults to "Pantopus read this for you". */
  headline?: string;
  summary: string;
  bullets?: AIElfBullet[];
  trailingBadge?: string;
  /** Optional refresh / redo handler. When omitted the redo affordance is hidden. */
  onRedo?: () => void;
}

// ── Attachments ──────────────────────────────────────────

/** File kind drives the 36×44 thumbnail tile color + glyph. */
export type AttachmentKind = 'pdf' | 'image' | 'video' | 'audio' | 'link' | 'other';

/** One row in the attachments list. */
export interface AttachmentItem {
  id: string;
  kind: AttachmentKind;
  name: string;
  meta?: string;
  onSelect?: () => void;
}

/** Attachments section payload. */
export interface AttachmentsRowContent {
  /** Section title (defaults to "Attachments" in the renderer). */
  title?: string;
  items: AttachmentItem[];
}

// ── Shell props ──────────────────────────────────────────

export interface MailItemDetailShellProps {
  topBar: MailTopBarConfig;
  /** Required — variants always supply some hero design. */
  hero: ReactNode;
  aiElf?: AIElfStripContent | null;
  keyFacts?: ReactNode;
  body?: ReactNode;
  attachments?: AttachmentsRowContent | null;
  sender?: ReactNode;
  /** Sticky bottom slot — pinned above the system tab bar when populated. */
  actions?: ReactNode;
  /** Optional extra class on the shell root (used by the preview page). */
  className?: string;
}
