// ============================================================
// Place — HEADER. "Your Place" + the address line + the trust avatar
// (verified green check / claimed amber home / none). `rightSlot`
// swaps the avatar for a quiet control — e.g. a "Sign in" link on the
// signed-out preview.
// ============================================================

'use client';

import type { ReactNode } from 'react';
import { MapPin } from 'lucide-react';
import { PlaceAvatar, type PlaceAvatarStatus } from './primitives';

export interface PlaceHeaderProps {
  title?: string;
  address: string;
  initials?: string;
  /** Trust status driving the avatar badge. */
  status?: PlaceAvatarStatus;
  /** Override the avatar (e.g. a "Sign in" link on the preview). */
  rightSlot?: ReactNode;
  className?: string;
}

export default function PlaceHeader({
  title = 'Your Place',
  address,
  initials = 'RC',
  status = 'verified',
  rightSlot,
  className = '',
}: PlaceHeaderProps) {
  return (
    <header className={`flex items-start justify-between gap-3 ${className}`}>
      <div className="min-w-0">
        <h1 className="text-[28px] leading-8 font-bold -tracking-[0.02em] text-app-text">{title}</h1>
        <div className="flex items-center gap-1.5 mt-1 text-app-text-secondary">
          <MapPin size={14} strokeWidth={2} className="shrink-0 text-app-text-muted" />
          <span className="text-sm font-medium truncate">{address}</span>
        </div>
      </div>
      {rightSlot ?? <PlaceAvatar initials={initials} status={status} size={40} />}
    </header>
  );
}
