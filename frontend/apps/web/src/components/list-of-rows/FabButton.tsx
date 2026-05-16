'use client';

// `<FabButton />` — three variants per the T5 design system:
//   - canonicalCreate (56px round) — the screen's primary create action
//   - secondaryCreate (52px round) — non-canonical create
//   - extendedNav (48px pill with label) — navigation FAB
//
// T6.0a — added optional `tint: FabTint` (default `sky`) for the home
// and business identity tints. Existing call sites compile unchanged
// because the field defaults.
//
// Mirrors iOS `FABAction.Variant` + `FabTint` and Android `FabVariant`.

import type { FabAction, FabTint } from './types';

interface Props {
  fab: FabAction;
}

/** Resolve a FAB tint to the bg + hover + (optional) tinted-shadow
 *  class triple. The `home` / `business` tints route through the
 *  semantic `app-home` / `app-business` colors (CSS variables, no
 *  Tailwind `<alpha-value>` template) so an opacity-modifier shadow
 *  like `shadow-primary-600/30` doesn't apply — they fall back to the
 *  default `shadow-lg` which reads as the same affordance at the scale
 *  the FAB renders. The `sky` case preserves the T5 tinted-shadow. */
function fabTintClasses(tint: FabTint): string {
  switch (tint) {
    case 'sky':
      return 'bg-primary-600 hover:bg-primary-700 shadow-primary-600/30';
    case 'home':
      return 'bg-app-home hover:opacity-90';
    case 'business':
      return 'bg-app-business hover:opacity-90';
  }
}

export default function FabButton({ fab }: Props) {
  const variant = fab.variant ?? { kind: 'canonicalCreate' };
  const tint = fab.tint ?? 'sky';
  const tintCls = fabTintClasses(tint);
  const Icon = fab.icon;

  switch (variant.kind) {
    case 'canonicalCreate':
      return (
        <button
          type="button"
          onClick={fab.onClick}
          aria-label={fab.accessibilityLabel}
          className={`fixed right-6 bottom-6 z-10 w-14 h-14 rounded-full text-white shadow-lg flex items-center justify-center transition ${tintCls}`}
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
          className={`fixed right-6 bottom-6 z-10 w-[52px] h-[52px] rounded-full text-white shadow-lg flex items-center justify-center transition ${tintCls}`}
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
          className={`fixed right-6 bottom-6 z-10 h-12 px-5 rounded-full text-white shadow-lg inline-flex items-center gap-2 font-semibold text-sm transition ${tintCls}`}
        >
          <Icon className="w-[18px] h-[18px]" />
          {variant.label}
        </button>
      );
  }
}
