"use client";

// D9 — Open-in-App / deep-link hand-off (web variant). Per the wiring contract,
// web is a smart-banner + an OpenInApp button (no native interstitial). A slim,
// dismissible strip over the public surface that hands an app-having invitee to
// the native flow (with their identity/tz/saved details) via the W0
// OpenInAppButton (custom scheme + universal-link + store fallback).

import { useState } from "react";
import { X } from "lucide-react";
import clsx from "clsx";
import OpenInAppButton from "@/components/public-share/OpenInAppButton";
import { pillarTokens, type Pillar } from "@/components/scheduling";

function PantopusMark({ pillar }: { pillar: Pillar }) {
  const tk = pillarTokens(pillar);
  return (
    <span
      className={clsx(
        "flex h-8 w-8 shrink-0 items-center justify-center rounded-lg",
        tk.bg,
      )}
      aria-hidden
    >
      <svg width="18" height="18" viewBox="0 0 20 20" fill="none">
        <circle cx="10" cy="10" r="8.5" stroke="#fff" strokeOpacity="0.4" />
        <circle cx="10" cy="10" r="5" stroke="#fff" strokeWidth="1.4" />
        <circle cx="10" cy="10" r="1.6" fill="#fff" />
      </svg>
    </span>
  );
}

export default function OpenInAppHandoff({
  appUrl,
  linkHref,
  fallbackUrl,
  pillar = "personal",
  title = "Open in Pantopus",
  subtitle = "Faster, with your saved details",
  className,
}: {
  /** Custom scheme target, e.g. buildBookingPageAppUrl(slug). */
  appUrl: string;
  /** Canonical https URL for this screen (same-origin → used for the hand-off). */
  linkHref?: string | null;
  /** App / Play Store URL when the app isn't installed. */
  fallbackUrl?: string | null;
  pillar?: Pillar;
  title?: string;
  subtitle?: string;
  className?: string;
}) {
  const [dismissed, setDismissed] = useState(false);
  const tk = pillarTokens(pillar);
  if (dismissed) return null;

  return (
    <div
      className={clsx(
        "flex items-center gap-2.5 border-b border-app-border bg-app-surface px-3 py-2.5",
        className,
      )}
    >
      <button
        type="button"
        onClick={() => setDismissed(true)}
        aria-label="Dismiss"
        className="flex h-5 w-5 shrink-0 items-center justify-center rounded text-app-text-muted hover:text-app-text"
      >
        <X className="h-3.5 w-3.5" aria-hidden />
      </button>
      <PantopusMark pillar={pillar} />
      <div className="min-w-0 flex-1">
        <p className="truncate text-[13px] font-bold text-app-text-strong">
          {title}
        </p>
        <p className="truncate text-[11px] text-app-text-muted">{subtitle}</p>
      </div>
      <OpenInAppButton
        appUrl={appUrl}
        linkHref={linkHref}
        fallbackUrl={fallbackUrl}
        className={clsx(
          "shrink-0 rounded-full px-4 py-1.5 text-xs font-bold",
          tk.bg,
          tk.textOn,
        )}
      >
        Open
      </OpenInAppButton>
    </div>
  );
}
