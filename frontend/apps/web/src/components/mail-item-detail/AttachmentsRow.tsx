'use client';

// Section-card render for `AttachmentsRowContent`. Per
// `mail-detail.jsx:289`, each row is a 36×44 type-color tile + name +
// meta + 32px download button.

import { Download } from 'lucide-react';
import type { AttachmentItem, AttachmentKind, AttachmentsRowContent } from './types';

interface TileTokens {
  label: string;
  background: string;
  foreground: string;
  border: string;
}

/**
 * Per-kind tile tokens. Tailwind utility classes; the shell file is the
 * only place these palette decisions live so feature variants don't
 * fork the colour mapping.
 */
const TILE_TOKENS: Record<AttachmentKind, TileTokens> = {
  pdf: { label: 'PDF', background: 'bg-red-100', foreground: 'text-red-700', border: 'border-red-200' },
  image: {
    label: 'IMG',
    background: 'bg-blue-100',
    foreground: 'text-blue-700',
    border: 'border-blue-200',
  },
  video: {
    label: 'VID',
    background: 'bg-pink-100',
    foreground: 'text-pink-700',
    border: 'border-pink-200',
  },
  audio: {
    label: 'AUD',
    background: 'bg-violet-100',
    foreground: 'text-violet-700',
    border: 'border-violet-200',
  },
  link: {
    label: 'URL',
    background: 'bg-slate-100',
    foreground: 'text-slate-700',
    border: 'border-slate-200',
  },
  other: {
    label: 'FILE',
    background: 'bg-slate-100',
    foreground: 'text-slate-700',
    border: 'border-slate-200',
  },
};

export default function AttachmentsRow({ content }: { content: AttachmentsRowContent }) {
  const { title = 'Attachments', items } = content;
  return (
    <section
      data-testid="mailItemDetail_attachments"
      className="overflow-hidden rounded-2xl border border-app-border bg-app-surface shadow-sm"
    >
      <header className="flex items-center gap-1 border-b border-app-border-subtle px-3.5 py-2">
        <h3 className="text-[11px] font-bold uppercase tracking-wide text-app-text-secondary">
          {title}
        </h3>
        <span className="text-[11px] font-semibold text-app-text-muted">· {items.length}</span>
      </header>
      <ul className="flex flex-col">
        {items.map((item, index) => (
          <li
            key={item.id}
            className={
              index < items.length - 1
                ? 'border-b border-app-border-subtle [&:not(:last-child)>div]:[border-bottom-color:transparent]'
                : ''
            }
          >
            <AttachmentRowItem item={item} />
          </li>
        ))}
      </ul>
    </section>
  );
}

function AttachmentRowItem({ item }: { item: AttachmentItem }) {
  const tokens = TILE_TOKENS[item.kind];
  const handleClick = () => item.onSelect?.();
  return (
    <button
      type="button"
      data-testid={`mailItemDetail_attachment_${item.id}`}
      onClick={handleClick}
      className="flex w-full items-center gap-3 px-3.5 py-2.5 text-left hover:bg-app-hover"
    >
      <span
        className={
          'flex h-11 w-9 flex-shrink-0 items-center justify-center rounded-md border ' +
          'text-[9px] font-bold uppercase tracking-wide ' +
          tokens.background +
          ' ' +
          tokens.foreground +
          ' ' +
          tokens.border
        }
        aria-hidden
      >
        {tokens.label}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-[13px] font-semibold text-app-text">{item.name}</span>
        {item.meta ? (
          <span className="block text-[11px] text-app-text-secondary">{item.meta}</span>
        ) : null}
      </span>
      <span className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full bg-app-surface-sunken text-app-text-strong">
        <Download className="h-3.5 w-3.5" aria-hidden />
      </span>
    </button>
  );
}
