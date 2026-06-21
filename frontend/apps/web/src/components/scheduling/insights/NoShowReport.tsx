"use client";

// W17 · H11 — No-Show & Cancellation Report. No-show analytics from
// GET /bookings/insights/no-shows (rate, by event type, by host, recent),
// alongside cancellation stats computed from GET /bookings over the range.
// Read-only.

import { useMemo } from "react";
import Link from "next/link";
import { ArrowRight, CalendarX2, PartyPopper, ShieldCheck } from "lucide-react";
import * as api from "@pantopus/api";
import type { Booking, NoShowInsights } from "@pantopus/types";
import { useSchedulingOwner } from "@/components/scheduling/SchedulingOwnerProvider";
import { pillarForOwner } from "@/components/scheduling/pillarTokens";
import BookingStatusPill from "@/components/scheduling/BookingStatusPill";
import { useReport } from "./useReport";
import { useInsightsFilters } from "./useInsightsFilters";
import { summarizeRange } from "./aggregate";
import { bookingListParams, insightsDays } from "./filters";
import {
  formatCount,
  formatDateTimeShort,
  formatRate,
  initials,
} from "./format";
import {
  BarList,
  Card,
  DonutGauge,
  EmptyReport,
  InlineRetry,
  KpiGrid,
  KpiTile,
  NoticeCard,
  ReportSkeleton,
  type BarDatum,
} from "./ui";

interface Data {
  bookings: Booking[];
  noShow: NoShowInsights;
}

export default function NoShowReport() {
  const owner = useSchedulingOwner();
  const pillar = pillarForOwner(owner.ownerType);
  const { filters, query } = useInsightsFilters();
  const ownerKey = `${owner.ownerType}:${owner.ownerId ?? owner.homeId ?? ""}`;

  const { phase, data, reload } = useReport<Data>(async () => {
    const [bookingsRes, noShow] = await Promise.all([
      api.scheduling.listBookings(bookingListParams(filters), owner),
      api.scheduling.getNoShowInsights(insightsDays(filters), owner),
    ]);
    return { bookings: bookingsRes.bookings ?? [], noShow };
  }, [query, ownerKey]);

  const summary = useMemo(
    () => (data ? summarizeRange(data.bookings) : null),
    [data],
  );

  if (phase === "loading") return <ReportSkeleton kpis={4} />;
  if (phase === "error" || !data || !summary)
    return (
      <InlineRetry
        message="We couldn't load the no-show report. Try again."
        onRetry={reload}
      />
    );

  const { noShow } = data;
  const noShowCount = noShow.noShowCount ?? summary.noShow;

  if (summary.total === 0)
    return (
      <EmptyReport
        icon={CalendarX2}
        title="No bookings in this period"
        body="When you have completed bookings, your no-show and cancellation rates will appear here."
        pillar={pillar}
      />
    );

  if (noShowCount === 0 && summary.cancelled === 0)
    return (
      <NoticeCard
        icon={PartyPopper}
        title="No no-shows or cancellations"
        body="Every booking in this period was kept. Nice work — your attendance rate is 100%."
        tone="success"
      />
    );

  const byEventType: BarDatum[] = noShow.byEventType.map((r) => ({
    key: r.event_type_id,
    label: r.name || "Untitled event",
    value: r.count,
    display: formatCount(r.count),
    caption: `${formatRate(r.rate)} no-show rate`,
    tone: "error",
  }));

  const byHost: BarDatum[] = noShow.byHost.map((r) => ({
    key: r.user_id,
    label: r.name || "Unknown",
    value: r.count,
    display: formatCount(r.count),
    caption: `${formatRate(r.rate)} no-show rate`,
    tone: "error",
  }));

  return (
    <div className="space-y-4">
      {/* Rate donuts */}
      <Card>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <DonutGauge
            rate={noShow.noShowRate ?? summary.noShowRate}
            tone="error"
            caption={`${formatCount(noShowCount)} no-shows of completed bookings`}
          />
          <DonutGauge
            rate={summary.cancellationRate}
            tone="warning"
            caption={`${formatCount(summary.cancelled)} cancelled / declined of ${formatCount(
              summary.total,
            )}`}
          />
        </div>
      </Card>

      <KpiGrid>
        <KpiTile
          value={formatCount(noShowCount)}
          label="No-shows"
          tone="error"
        />
        <KpiTile
          value={formatCount(summary.cancelled)}
          label="Cancellations"
          tone="warning"
        />
        <KpiTile
          value={formatCount(summary.completed)}
          label="Completed"
          tone="success"
        />
        <KpiTile value={formatCount(summary.total)} label="Total bookings" />
      </KpiGrid>

      {byEventType.length > 0 && (
        <Card title="No-shows by event type" icon={CalendarX2}>
          <BarList data={byEventType} pillar={pillar} />
        </Card>
      )}

      {byHost.length > 0 && (
        <Card title="No-shows by host" icon={CalendarX2}>
          <BarList data={byHost} pillar={pillar} />
        </Card>
      )}

      {noShow.recent.length > 0 && (
        <Card title="Recent no-shows">
          <ul className="divide-y divide-app-border-subtle">
            {noShow.recent.map((r) => (
              <li key={r.booking_id} className="flex items-center gap-3 py-2.5">
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-app-error-bg text-[11px] font-bold text-app-error">
                  {initials(r.invitee_name)}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-semibold text-app-text">
                    {r.invitee_name || "Guest"}
                  </span>
                  <span className="block truncate text-xs text-app-text-muted">
                    {formatDateTimeShort(r.scheduled_at, filters.tz)}
                  </span>
                </span>
                <BookingStatusPill status="no_show" />
              </li>
            ))}
          </ul>
        </Card>
      )}

      {/* Policy callout — mirrors iOS/Android "Reduce no-shows" section */}
      <div className="flex items-start gap-4 rounded-2xl border border-app-border bg-app-surface p-4 shadow-sm">
        <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-app-warning-bg text-app-warning">
          <ShieldCheck className="h-5 w-5" aria-hidden />
        </span>
        <div className="min-w-0 flex-1">
          <p className="mb-0.5 text-sm font-semibold text-app-text">
            Reduce no-shows
          </p>
          <p className="mb-3 text-xs leading-relaxed text-app-text-secondary">
            A cancellation policy lets you charge a fee or hold a deposit when
            invitees cancel late or don&apos;t show up.
          </p>
          <Link
            href="/app/scheduling/payments/policy"
            className="inline-flex items-center gap-1 text-xs font-semibold text-app-info hover:underline"
          >
            Set a policy
            <ArrowRight className="h-3.5 w-3.5" aria-hidden />
          </Link>
        </div>
      </div>
    </div>
  );
}
