'use client';

// Pantopus — `<MailItemDetailShell />` is the web mirror of the iOS /
// Android A17 Mailbox item detail archetype shell. Concrete variant
// screens (Generic, Booklet, Certified, Community, Ceremonial) compose
// the shell with their own hero / key-facts / body / sender / actions
// designs and the shell handles top bar + spacing + nil-slot skipping
// + AI elf strip + attachments row.
//
// Token-only — colours come from Tailwind theme tokens already wired in
// `tailwind.config.js`. No inline hex literals at the shell level.

import { isValidElement, type MouseEvent, type ReactNode } from 'react';
import { ChevronLeft, MoreHorizontal } from 'lucide-react';
import type { MailItemDetailShellProps, MailTopBarConfig } from './types';
import AIElfStrip from './AIElfStrip';
import AttachmentsRow from './AttachmentsRow';

const trustDotClass: Record<string, string> = {
  verified: 'bg-emerald-500',
  neutral: 'bg-slate-400',
  warning: 'bg-amber-500',
};

function hasContent(node: ReactNode): boolean {
  if (node === null || node === undefined || node === false) return false;
  if (typeof node === 'string') return node.trim().length > 0;
  if (Array.isArray(node)) return node.some(hasContent);
  if (isValidElement(node)) return true;
  return Boolean(node);
}

export default function MailItemDetailShell({
  topBar,
  hero,
  aiElf,
  keyFacts,
  body,
  attachments,
  sender,
  actions,
  className,
}: MailItemDetailShellProps) {
  const showActions = hasContent(actions);
  return (
    <div
      data-testid="mailItemDetailShell"
      className={
        'flex h-full min-h-screen w-full flex-col bg-app-bg ' + (className ?? '')
      }
    >
      <MailItemDetailTopBar config={topBar} />
      <div
        className={
          'flex flex-1 flex-col gap-3 overflow-y-auto px-4 pt-3 ' +
          (showActions ? 'pb-28' : 'pb-8')
        }
      >
        <div data-testid="mailItemDetail_hero">{hero}</div>
        {aiElf ? <AIElfStrip content={aiElf} /> : null}
        {hasContent(keyFacts) ? (
          <div data-testid="mailItemDetail_keyFacts">{keyFacts}</div>
        ) : null}
        {hasContent(body) ? (
          <div data-testid="mailItemDetail_body">{body}</div>
        ) : null}
        {attachments ? <AttachmentsRow content={attachments} /> : null}
        {hasContent(sender) ? (
          <div data-testid="mailItemDetail_sender">{sender}</div>
        ) : null}
      </div>
      {showActions ? (
        <div
          data-testid="mailItemDetail_actions"
          className="sticky bottom-0 left-0 right-0 border-t border-app-border-subtle bg-app-surface px-4 py-3"
        >
          {actions}
        </div>
      ) : null}
    </div>
  );
}

/**
 * 44px top nav bar — back chevron (with optional "Mailbox" label) +
 * eyebrow trust dot + optional bookmark/pin button + overflow menu.
 * Exported for variants that want to render it inside their own layout.
 */
export function MailItemDetailTopBar({ config }: { config: MailTopBarConfig }) {
  const { eyebrow, trust, onBack, trailingAction, overflowItems } = config;
  const dotClass = trustDotClass[trust] ?? trustDotClass.neutral;
  return (
    <div
      data-testid="mailItemDetail_topBar"
      className="flex h-11 items-center justify-between border-b border-app-border-subtle bg-app-surface px-2"
    >
      {/* Leading: back button or spacer */}
      {onBack ? (
        <button
          type="button"
          aria-label="Back to Mailbox"
          data-testid="mailItemDetail_back"
          onClick={onBack}
          className="-ml-1 flex h-9 items-center gap-0 rounded-md px-1 text-sky-600 hover:bg-app-hover"
        >
          <ChevronLeft className="h-5 w-5" aria-hidden />
          <span className="text-[15px]">Mailbox</span>
        </button>
      ) : (
        <span className="h-9 w-11" aria-hidden />
      )}

      {/* Eyebrow */}
      {eyebrow ? (
        <div
          data-testid="mailItemDetail_eyebrow"
          role="heading"
          aria-level={1}
          className="flex items-center gap-1.5"
        >
          <span className={'h-2 w-2 rounded-full ' + dotClass} aria-hidden />
          <span className="text-[12px] font-bold uppercase tracking-wide text-app-text-strong">
            {eyebrow}
          </span>
        </div>
      ) : (
        <span aria-hidden />
      )}

      {/* Trailing: optional bookmark + overflow */}
      <div className="flex items-center gap-0.5">
        {trailingAction ? (
          <button
            type="button"
            aria-label={trailingAction.accessibilityLabel}
            data-testid="mailItemDetail_trailingAction"
            aria-pressed={trailingAction.isActive ? 'true' : undefined}
            onClick={trailingAction.onClick}
            className={
              'flex h-[34px] w-[34px] items-center justify-center rounded-full transition ' +
              (trailingAction.isActive
                ? 'bg-sky-100 text-sky-700'
                : 'bg-app-surface-sunken text-app-text-strong hover:bg-app-hover')
            }
          >
            <trailingAction.icon className="h-[18px] w-[18px]" aria-hidden />
          </button>
        ) : null}
        {overflowItems && overflowItems.length > 0 ? (
          <OverflowMenu items={overflowItems} />
        ) : null}
      </div>
    </div>
  );
}

function OverflowMenu({ items }: { items: NonNullable<MailTopBarConfig['overflowItems']> }) {
  // Lightweight uncontrolled <details>-based menu so the shell stays
  // dependency-free. Concrete variants can swap in a richer popover via
  // the trailingAction slot if they need a custom menu shell.
  return (
    <details
      data-testid="mailItemDetail_overflow"
      className="relative"
    >
      <summary
        aria-label="More actions"
        className="flex h-[34px] w-[34px] cursor-pointer list-none items-center justify-center rounded-full bg-app-surface-sunken text-app-text-strong hover:bg-app-hover [&::-webkit-details-marker]:hidden"
      >
        <MoreHorizontal className="h-[18px] w-[18px]" aria-hidden />
      </summary>
      <ul
        role="menu"
        className="absolute right-0 top-9 z-30 min-w-[200px] rounded-xl border border-app-border bg-app-surface py-1 shadow-lg"
      >
        {items.map((item) => (
          <li key={item.id} role="none">
            <button
              type="button"
              role="menuitem"
              data-testid={`mailItemDetail_overflowItem_${item.id}`}
              onClick={(e: MouseEvent<HTMLButtonElement>) => {
                item.onSelect();
                // Close the parent <details> after a select.
                const details = e.currentTarget.closest('details');
                if (details) {
                  (details as HTMLDetailsElement).open = false;
                }
              }}
              className={
                'flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] ' +
                (item.isDestructive
                  ? 'text-red-600 hover:bg-red-50'
                  : 'text-app-text hover:bg-app-hover')
              }
            >
              <item.icon className="h-4 w-4" aria-hidden />
              <span className="font-medium">{item.label}</span>
            </button>
          </li>
        ))}
      </ul>
    </details>
  );
}
