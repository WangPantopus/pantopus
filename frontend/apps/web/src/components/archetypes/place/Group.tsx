// ============================================================
// Place — GROUP. An overline label over a vertical stack of cards.
// The presentation layer that composes contract sections into the
// curated dashboard groups (Today, Your home, Risk & readiness, …).
// ============================================================

import type { ReactNode } from 'react';
import Overline from '../primitives/Overline';

export interface GroupProps {
  label: ReactNode;
  children: ReactNode;
  className?: string;
}

export default function Group({ label, children, className = '' }: GroupProps) {
  return (
    <div className={`mb-6 ${className}`}>
      <Overline as="div" className="px-0.5 mb-2 tracking-[0.08em] text-app-text-muted">
        {label}
      </Overline>
      <div className="flex flex-col gap-2">{children}</div>
    </div>
  );
}
