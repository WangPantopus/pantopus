"use client";

// A5 — Scheduling Summary Card. An at-a-glance "this month" pulse, powered by
// GET /bookings/summary. Self-contained (fetches its own data) and reusable:
// other streams import it read-only. States: loading · error+retry · empty
// (share CTA) · loaded. Themed by the owner pillar.

import { useCallback, useEffect, useState } from "react";
import clsx from "clsx";
import { CalendarClock, CloudOff, RotateCcw, Share2 } from "lucide-react";
import Link from "next/link";
import * as api from "@pantopus/api";
import type { BookingsSummary, SchedulingOwnerRef } from "@pantopus/types";
import {
  pillarTokens,
  type Pillar,
} from "@/components/scheduling/pillarTokens";

interface SummaryCardProps {
  owner: SchedulingOwnerRef;
  pillar: Pillar;
  /** Called when the empty-state "Share booking link" CTA is pressed. */
  onShare?: () => void;
  insightsHref?: string;
  className?: string;
}

function num(v: unknown): number {
  return typeof v === "number" && Number.isFinite(v) ? v : 0;
}

function StatCell({ value, label }: { value: string; label: string }) {
  return (
    <div className="min-w-0 flex-1 px-1">
      <p className="text-[22px] font-bold leading-7 tracking-[-0.02em] text-app-text tabular-nums">
        {value}
      </p>
      <p className="mt-0.5 text-[10.5px] font-semibold leading-tight text-app-text-secondary">
        {label}
      </p>
    </div>
  );
}

function Divider() {
  return <div className="w-px self-stretch bg-app-border-subtle" />;
}

function Shell({
  children,
  className,
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div
      className={clsx(
        "rounded-2xl border border-app-border bg-app-surface p-4 shadow-sm",
        className,
      )}
    >
      {children}
    </div>
  );
}

export default function SchedulingSummaryCard({
  owner,
  pillar,
  onShare,
  insightsHref = "/app/scheduling/insights",
  className,
}: SummaryCardProps) {
  const tk = pillarTokens(pillar);
  const [data, setData] = useState<BookingsSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      const summary = await api.scheduling.getBookingsSummary(owner);
      setData(summary);
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  }, [owner]);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) {
    return (
      <Shell className={className}>
        <div className="mb-3.5 h-3 w-24 animate-pulse rounded bg-app-surface-sunken" />
        <div className="flex items-stretch gap-2.5">
          {[0, 1, 2, 3].map((i) => (
            <div key={i} className="flex flex-1 flex-col gap-1.5">
              <div className="h-6 w-2/3 animate-pulse rounded bg-app-surface-sunken" />
              <div className="h-2.5 w-full animate-pulse rounded bg-app-surface-sunken" />
            </div>
          ))}
        </div>
      </Shell>
    );
  }

  if (error || !data) {
    return (
      <Shell className={className}>
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-app-surface-sunken text-app-text-secondary">
            <CloudOff className="h-5 w-5" aria-hidden />
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-[13.5px] font-semibold text-app-text">
              Couldn’t load your numbers
            </p>
            <p className="mt-0.5 text-xs text-app-text-secondary">
              Check your connection and try again.
            </p>
          </div>
          <button
            type="button"
            onClick={() => void load()}
            className="inline-flex shrink-0 items-center gap-1.5 rounded-full border border-app-border bg-app-surface px-3.5 py-2 text-xs font-semibold text-app-text-strong hover:bg-app-hover"
          >
            <RotateCcw className="h-3.5 w-3.5" aria-hidden />
            Retry
          </button>
        </div>
      </Shell>
    );
  }

  const total = num(data.totalThisMonth);
  const upcoming = num(data.upcomingCount);
  const pending = num(data.pendingCount);
  const isEmpty = total === 0 && upcoming === 0 && pending === 0;

  if (isEmpty) {
    return (
      <Shell className={className}>
        <p
          className={clsx(
            "mb-3.5 text-[11px] font-bold uppercase tracking-[0.08em]",
            tk.text,
          )}
        >
          This month
        </p>
        <div className="flex items-center gap-3">
          <div
            className={clsx(
              "flex h-11 w-11 shrink-0 items-center justify-center rounded-xl",
              tk.bgSoft,
              tk.text,
            )}
          >
            <CalendarClock className="h-[22px] w-[22px]" aria-hidden />
          </div>
          <div className="min-w-0">
            <p className="text-[15px] font-bold text-app-text">
              No bookings yet
            </p>
            <p className="mt-0.5 text-[12.5px] text-app-text-secondary">
              Share your link to get your first one.
            </p>
          </div>
        </div>
        {onShare && (
          <button
            type="button"
            onClick={onShare}
            className={clsx(
              "mt-3.5 flex h-11 w-full items-center justify-center gap-2 rounded-xl text-[13.5px] font-bold shadow-sm",
              tk.bg,
              tk.textOn,
            )}
          >
            <Share2 className="h-4 w-4" strokeWidth={2.2} aria-hidden />
            Share booking link
          </button>
        )}
      </Shell>
    );
  }

  const noShowPct = Math.round(num(data.noShowRate) * 100);

  return (
    <Shell className={className}>
      <p
        className={clsx(
          "mb-3.5 text-[11px] font-bold uppercase tracking-[0.08em]",
          tk.text,
        )}
      >
        This month
      </p>
      <div className="flex items-stretch gap-2.5">
        <StatCell value={String(total)} label="Bookings" />
        <Divider />
        <StatCell value={String(upcoming)} label="Upcoming" />
        <Divider />
        <StatCell value={String(pending)} label="Pending" />
        <Divider />
        <StatCell value={`${noShowPct}%`} label="No-show rate" />
      </div>
      <div className="mt-3 flex justify-end">
        <Link
          href={insightsHref}
          className={clsx(
            "inline-flex items-center gap-0.5 text-[12.5px] font-semibold",
            tk.text,
          )}
        >
          See insights
          <span aria-hidden>›</span>
        </Link>
      </div>
    </Shell>
  );
}
