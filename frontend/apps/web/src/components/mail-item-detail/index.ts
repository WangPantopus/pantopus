// Pantopus — `@/components/mail-item-detail` barrel.
//
// T6.5a (P19) — Web mirror of the iOS / Android `MailItemDetailShell`
// archetype. Variant screens (Generic, Booklet, Certified, Community,
// Ceremonial) import only the shell + types from here; they never
// reach into the internal renderers.

export { default as MailItemDetailShell, MailItemDetailTopBar } from './MailItemDetailShell';
export { default as AIElfStrip } from './AIElfStrip';
export { default as AttachmentsRow } from './AttachmentsRow';

export type {
  MailDetailTrust,
  MailOverflowItem,
  MailTopBarConfig,
  MailTopBarTrailingAction,
  AIElfBullet,
  AIElfStripContent,
  AttachmentKind,
  AttachmentItem,
  AttachmentsRowContent,
  MailItemDetailShellProps,
} from './types';
