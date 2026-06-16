"use client";

// W17 · H10 — Per-Event-Type Performance. Bookings in the range, grouped by
// event type and joined with the event-type list for names/colors. Volume,
// completion, cancellations, no-show rate and average duration per type, with a
// sort control. Read-only; derives from GET /bookings (range) + event types.

import { useMemo, useState } from "react";
import { CalendarClock } from "lucide-react";
import * as api from "@pantopus/api";
import type { Booking, EventType } from "@pantopus/types";
import { useSchedulingOwner } from "@/components/scheduling/SchedulingOwnerProvider";
import {
  pillarForOwner,
  pillarTokens,
} from "@/components/scheduling/pillarTokens";
import { useReport } from "./useReport";
import { useInsightsFilters } from "./useInsightsFilters";
import { aggregateByEventType, type EventTypeAgg } from "./aggregate";
import { bookingListParams } from "./filters";
import { formatCount, formatDurationMin, formatRate } from "./format";
import { Card, EmptyReport, InlineRetry, ReportSkeleton } from "./ui";

interface Data {
  bookings: Booking[];
  eventTypes: EventType[];
}

type SortKey = "volume" | "no_show" | "completion";

const SORTS: ReadonlyArray<{ id: SortKey; label: string }> = [
  { id: "volume", label: "Volume" },
  { id: "completion", label: "Completion" },
  { id: "no_show", label: "No-show rate" },
];

function completionRate(t: EventTypeAgg): number {
  const settled = t.completed + t.noShow;
  return settled ? t.completed / settled : 0;
}

function MiniStat({
  value,
  label,
  tone,
}: {
  value: string;
  label: string;
  tone?: "error" | "warning" | "success";
}) {
  const cls =
    tone === "error"
      ? "text-app-error"
      : tone === "warning"
        ? "text-app-warning"
        : tone === "success"
          ? "text-app-success"
          : "text-app-text";
  return (
    <div>
      <p className={`text-[15px] font-bold tabular-nums ${cls}`}>{value}</p>
      <p className="text-[10.5px] font-semibold uppercase tracking-wide text-app-text-muted">
        {label}
      </p>
    </div>
  );
}

export default function EventTypePerformance() {
  const owner = useSchedulingOwner();
  const pillar = pillarForOwner(owner.ownerType);
  const tk = pillarTokens(pillar);
  const { filters, query } = useInsightsFilters();
  const ownerKey = `${owner.ownerType}:${owner.ownerId ?? owner.homeId ?? ""}`;
  const [sort, setSort] = useState<SortKey>("volume");

  const { phase, data, reload } = useReport<Data>(async () => {
    const [bookingsRes, etRes] = await Promise.all([
      api.scheduling.listBookings(bookingListParams(filters), owner),
      api.scheduling
        .listEventTypes(owner)
        .catch(() => ({ eventTypes: [] as EventType[] })),
    ]);
    return {
      bookings: bookingsRes.bookings ?? [],
      eventTypes: etRes.eventTypes ?? [],
    };
  }, [query, ownerKey]);

  const rows = useMemo(() => {
    if (!data) return [];
    const agg = aggregateByEventType(data.bookings, data.eventTypes);
    const sorted = [...agg];
    if (sort === "no_show")
      sorted.sort((a, b) => b.noShowRate - a.noShowRate || b.total - a.total);
    else if (sort === "completion")
      sorted.sort(
        (a, b) => completionRate(b) - completionRate(a) || b.total - a.total,
      );
    return sorted;
  }, [data, sort]);

  if (phase === "loading") return <ReportSkeleton kpis={0} />;
  if (phase === "error" || !data)
    return (
      <InlineRetry
        message="We couldn't load per-event-type performance. Try again."
        onRetry={reload}
      />
    );

  if (rows.length === 0)
    return (
      <EmptyReport
        icon={CalendarClock}
        title="No bookings in this period"
        body="Widen the period or pick a different event type to see how each one performs."
        pillar={pillar}
      />
    );

  const maxTotal = rows.reduce((m, r) => Math.max(m, r.total), 0);

  return (
    <div className="space-y-4">
      {/* Sort control */}
      <div className="flex items-center gap-2">
        <span className="text-[11px] font-semibold uppercase tracking-wide text-app-text-muted">
          Sort by
        </span>
        <div className="flex gap-0.5 rounded-[10px] bg-app-surface-sunken p-1">
          {SORTS.map((s) => {
            const on = s.id === sort;
            return (
              <button
                key={s.id}
                type="button"
                onClick={() => setSort(s.id)}
                aria-pressed={on}
                className={`rounded-md px-3 py-1.5 text-xs font-semibold transition ${
                  on
                    ? `bg-app-surface shadow-sm ${tk.text}`
                    : "text-app-text-secondary hover:text-app-text"
                }`}
              >
                {s.label}
              </button>
            );
          })}
        </div>
      </div>

      <div className="space-y-3">
        {rows.map((t) => {
          const pct = maxTotal > 0 ? Math.round((t.total / maxTotal) * 100) : 0;
          return (
            <Card key={t.eventTypeId}>
              <div className="mb-3 flex items-center justify-between gap-3">
                <span className="flex min-w-0 items-center gap-2">
                  <span
                    className="h-3 w-3 shrink-0 rounded-full"
                    style={{
                      backgroundColor: t.color || "var(--app-text-muted)",
                    }}
                    aria-hidden
                  />
                  <span className="truncate text-[15px] font-bold text-app-text">
                    {t.name}
                  </span>
                </span>
                <span className="shrink-0 text-right">
                  <span className="text-[18px] font-bold tabular-nums text-app-text">
                    {formatCount(t.total)}
                  </span>
                  <span className="ml-1 text-[11px] font-medium text-app-text-muted">
                    bookings
                  </span>
                </span>
              </div>

              <div className="mb-3 h-2 overflow-hidden rounded-full bg-app-surface-sunken">
                <div
                  className={`h-full rounded-full ${tk.bg}`}
                  style={{ width: `${Math.max(pct, t.total > 0 ? 4 : 0)}%` }}
                />
              </div>

              <div className="grid grid-cols-4 gap-2">
                <MiniStat
                  value={formatCount(t.completed)}
                  label="Completed"
                  tone="success"
                />
                <MiniStat
                  value={formatCount(t.cancelled)}
                  label="Cancelled"
                  tone="warning"
                />
                <MiniStat
                  value={formatRate(t.noShowRate)}
                  label="No-show"
                  tone="error"
                />
                <MiniStat
                  value={formatDurationMin(t.avgDurationMin)}
                  label="Avg length"
                />
              </div>
            </Card>
          );
        })}
      </div>
    </div>
  );
}
