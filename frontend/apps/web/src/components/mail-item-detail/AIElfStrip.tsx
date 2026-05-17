'use client';

// Sparkles-headed extracted-info card. Renders `AIElfStripContent` per
// the `ElfStrip` block in `mail-detail.jsx:137-198`.

import { RefreshCw, Sparkles } from 'lucide-react';
import type { AIElfStripContent } from './types';

const DEFAULT_HEADLINE = 'Pantopus read this for you';

export default function AIElfStrip({ content }: { content: AIElfStripContent }) {
  const { headline = DEFAULT_HEADLINE, summary, bullets = [], trailingBadge, onRedo } = content;
  return (
    <div
      data-testid="mailItemDetail_aiElf"
      className="rounded-2xl border border-sky-200 bg-gradient-to-b from-sky-50 to-sky-100 p-3.5"
    >
      <div className="mb-2 flex items-center gap-2">
        <div className="flex h-6 w-6 items-center justify-center rounded-lg bg-sky-600 text-white shadow-sm">
          <Sparkles className="h-3.5 w-3.5" aria-hidden />
        </div>
        <div className="flex-1 text-[12px] font-bold text-sky-800">{headline}</div>
        {trailingBadge ? (
          <span
            data-testid="mailItemDetail_aiElfBadge"
            className="rounded-full border border-sky-200 bg-white px-2 py-0.5 text-[10px] font-bold text-sky-700"
          >
            {trailingBadge}
          </span>
        ) : null}
        {onRedo ? (
          <button
            type="button"
            data-testid="mailItemDetail_aiElfRedo"
            onClick={onRedo}
            className="flex items-center gap-1 rounded p-1 text-[11px] font-semibold text-sky-700 hover:bg-sky-100"
          >
            <RefreshCw className="h-3 w-3" aria-hidden />
            Redo
          </button>
        ) : null}
      </div>
      <p className="mb-2.5 text-[13px] leading-relaxed text-sky-900">{summary}</p>
      {bullets.length > 0 ? (
        <ul className="flex flex-col gap-1.5">
          {bullets.map((bullet) => (
            <li key={bullet.id} className="flex items-start gap-2 text-[12px] leading-snug">
              <span className="mt-0.5 flex h-4 w-4 flex-shrink-0 items-center justify-center rounded border border-sky-200 bg-white text-sky-700">
                <bullet.icon className="h-2.5 w-2.5" aria-hidden />
              </span>
              <span>
                <strong className="font-bold text-app-text">{bullet.label}</strong>
                {bullet.text ? (
                  <span className="text-app-text-strong"> — {bullet.text}</span>
                ) : null}
              </span>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}
