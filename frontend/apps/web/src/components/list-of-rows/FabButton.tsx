'use client';

// `<FabButton />` — three variants per the T5 design system:
//   - canonicalCreate (56px round) — the screen's primary create action
//   - secondaryCreate (52px round) — non-canonical create
//   - extendedNav (48px pill with label) — navigation FAB
//
// Mirrors iOS `FABAction.Variant` and Android `FabVariant`.

import type { FabAction } from './types';

interface Props {
  fab: FabAction;
}

export default function FabButton({ fab }: Props) {
  const variant = fab.variant ?? { kind: 'canonicalCreate' };
  const Icon = fab.icon;

  switch (variant.kind) {
    case 'canonicalCreate':
      return (
        <button
          type="button"
          onClick={fab.onClick}
          aria-label={fab.accessibilityLabel}
          className="fixed right-6 bottom-6 z-10 w-14 h-14 rounded-full bg-primary-600 text-white shadow-lg shadow-primary-600/30 flex items-center justify-center hover:bg-primary-700 transition"
        >
          <Icon className="w-6 h-6" />
        </button>
      );
    case 'secondaryCreate':
      return (
        <button
          type="button"
          onClick={fab.onClick}
          aria-label={fab.accessibilityLabel}
          className="fixed right-6 bottom-6 z-10 w-[52px] h-[52px] rounded-full bg-primary-600 text-white shadow-lg shadow-primary-600/30 flex items-center justify-center hover:bg-primary-700 transition"
        >
          <Icon className="w-[22px] h-[22px]" />
        </button>
      );
    case 'extendedNav':
      return (
        <button
          type="button"
          onClick={fab.onClick}
          aria-label={fab.accessibilityLabel}
          className="fixed right-6 bottom-6 z-10 h-12 px-5 rounded-full bg-primary-600 text-white shadow-lg shadow-primary-600/30 inline-flex items-center gap-2 font-semibold text-sm hover:bg-primary-700 transition"
        >
          <Icon className="w-[18px] h-[18px]" />
          {variant.label}
        </button>
      );
  }
}
