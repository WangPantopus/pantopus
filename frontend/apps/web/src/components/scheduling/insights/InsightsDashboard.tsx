"use client";

// W17 · H9 — Insights Dashboard. The reporting home: period-scoped KPIs +
// booking-volume trend + top event types, plus a live snapshot (upcoming /
// pending / next booking) and quick links into the detail reports. Read-only;
// every number derives from GET /bookings/summary, GET /bookings (range), the
// event-type list, and GET /bookings/insights/no-shows.

import { useMemo } from "react";
import Link from "next/link";
import {
  ArrowRight,
  CalendarClock,
  CheckCircle2,
  Inbox,
  TrendingUp,
  UserX,
  Users,
} from "lucide-react";
import * as api from "@pantopus/api";
import type {
  Booking,
  BookingsSummary,
  EventType,
  NoShowInsights,
} from "@pantopus/types";
import { useSchedulingOwner } from "@/components/scheduling/SchedulingOwnerProvider";
import {
  pillarForOwner,
  pillarTokens,
} from "@/components/scheduling/pillarTokens";
import { useReport } from "./useReport";
import { useInsightsFilters } from "./useInsightsFilters";
import {
  aggregateByEventType,
  summarizeRange,
  volumeSeries,
} from "./aggregate";
import { bookingListParams, bookingRange, insightsDays } from "./filters";
import {
  formatCount,
  formatDateTimeShort,
  formatRangeLabel,
  formatRate,
} from "./format";
import { isBusinessOwner } from "./gating";
import {
  BarList,
  Card,
  ColumnSpark,
  EmptyReport,
  InlineRetry,
  KpiGrid,
  KpiTile,
  ReportSkeleton,
  type BarDatum,
} from "./ui";

interface DashboardData {
  summary: BookingsSummary;
  bookings: Booking[];
  eventTypes: EventType[];
  noShow: NoShowInsights | null;
}

const INSIGHTS = "/app/scheduling/insights";

export default function InsightsDashboard() {
  const owner = useSchedulingOwner();
  const pillar = pillarForOwner(owner.ownerType);
  const tk = pillarTokens(pillar);
  const { filters, query } = useInsightsFilters();
  const ownerKey = `${owner.ownerType}:${owner.ownerId ?? owner.homeId ?? ""}`;
  const suffix = query ? `?${query}` : "";

  const { phase, data, reload } = useReport<DashboardData>(async () => {
    const [summary, bookingsRes, etRes] = await Promise.all([
      api.scheduling.getBookingsSummary(owner),
      api.scheduling.listBookings(bookingListParams(filters), owner),
      api.scheduling
        .listEventTypes(owner)
        .catch(() => ({ eventTypes: [] as EventType[] })),
    ]);
    let noShow: NoShowInsights | null = null;
    try {
      noShow = await api.scheduling.getNoShowInsights(
        insightsDays(filters),
        owner,
      );
    } catch {
      noShow = null;
    }
    return {
      summary,
      bookings: bookingsRes.bookings ?? [],
      eventTypes: etRes.eventTypes ?? [],
      noShow,
    };
  }, [query, ownerKey]);

  const view = useMemo(() => {
    if (!data) return null;
    const range = bookingRange(filters);
    const summaryRange = summarizeRange(data.bookings);
    const byType = aggregateByEventType(data.bookings, data.eventTypes);
    const series = volumeSeries(
      data.bookings,
      range.from,
      range.to,
      filters.tz,
    );
    return { range, summaryRange, byType, series };
  }, [data, filters]);

  if (phase === "loading") return <ReportSkeleton />;
  if (phase === "error" || !data || !view)
    return (
      <InlineRetry
        message="We couldn't load your insights. Check your connection and try again."
        onRetry={reload}
      />
    );

  const { summaryRange, byType, series, range } = view;
  const noShowRate = data.noShow?.noShowRate ?? summaryRange.noShowRate;
  const noShowCount = data.noShow?.noShowCount ?? summaryRange.noShow;

  const liveTotal =
    (data.summary.upcomingCount ?? 0) + (data.summary.pendingCount ?? 0);

  if (summaryRange.total === 0 && liveTotal === 0) {
    return (
      <EmptyReport
        icon={TrendingUp}
        title="No data for this period yet"
        body="Once you start taking bookings, your volume, outcomes and no-show trends will show up here."
        pillar={pillar}
      />
    );
  }

  const topTypes: BarDatum[] = byType.slice(0, 5).map((t) => ({
    key: t.eventTypeId,
    label: t.name,
    value: t.total,
    display: formatCount(t.total),
    dotColor: t.color,
    caption:
      t.total > 0
        ? `${formatCount(t.completed)} completed · ${formatRate(t.noShowRate)} no-show`
        : undefined,
  }));

  const next = data.summary.nextBooking;

  return (
    <div className="space-y-4">
      <KpiGrid>
        <KpiTile
          value={formatCount(summaryRange.total)}
          label="Bookings"
          hint="in this period"
        />
        <KpiTile
          value={formatCount(summaryRange.completed)}
          label="Completed"
          hint={`${formatRate(summaryRange.completionRate)} completion`}
          tone="success"
        />
        <KpiTile
          value={formatCount(summaryRange.cancelled)}
          label="Cancelled"
          hint={`${formatRate(summaryRange.cancellationRate)} of bookings`}
          tone="warning"
        />
        <KpiTile
          value={formatRate(noShowRate)}
          label="No-show rate"
          hint={`${formatCount(noShowCount)} no-shows`}
          tone="error"
        />
      </KpiGrid>

      {/* Live snapshot */}
      <Card title="Right now" icon={Inbox} accentClass={tk.text}>
        <div className="flex flex-wrap items-center gap-x-8 gap-y-3">
          <div>
            <p className="text-[22px] font-bold tabular-nums text-app-text">
              {formatCount(data.summary.upcomingCount ?? 0)}
            </p>
            <p className="text-[12px] font-semibold text-app-text-secondary">
              Upcoming
            </p>
          </div>
          <div>
            <p className="text-[22px] font-bold tabular-nums text-app-text">
              {formatCount(data.summary.pendingCount ?? 0)}
            </p>
            <p className="text-[12px] font-semibold text-app-text-secondary">
              Pending
            </p>
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-[10px] font-semibold uppercase tracking-wide text-app-text-muted">
              Next booking
            </p>
            {next?.start_at ? (
              <p className="truncate text-[13.5px] font-semibold text-app-text">
                {formatDateTimeShort(next.start_at, filters.tz)}
                {next.invitee_name ? (
                  <span className="text-app-text-secondary">
                    {" · "}
                    {next.invitee_name}
                  </span>
                ) : null}
              </p>
            ) : (
              <p className="text-[13.5px] text-app-text-muted">
                Nothing scheduled
              </p>
            )}
          </div>
        </div>
      </Card>

      {/* Volume trend */}
      <Card
        title="Booking volume"
        icon={TrendingUp}
        accentClass={tk.text}
        action={
          <span className="text-[11px] font-medium text-app-text-muted">
            {formatRangeLabel(range.from, range.to)}
          </span>
        }
      >
        <ColumnSpark
          values={series.buckets.map((b) => b.count)}
          pillar={pillar}
        />
        <div className="mt-2 flex items-center justify-between text-[11px] text-app-text-muted">
          <span>{formatCount(summaryRange.total)} total</span>
          <span>
            {series.bucketDays === 1
              ? "per day"
              : `per ${series.bucketDays} days`}
          </span>
        </div>
      </Card>

      {/* Top event types */}
      <Card
        title="Top event types"
        icon={CalendarClock}
        accentClass={tk.text}
        action={
          <Link
            href={`${INSIGHTS}/event-types${suffix}`}
            className={`inline-flex items-center gap-0.5 text-[12.5px] font-semibold ${tk.text}`}
          >
            Full report
            <ArrowRight className="h-3.5 w-3.5" aria-hidden />
          </Link>
        }
      >
        {topTypes.length ? (
          <BarList data={topTypes} pillar={pillar} />
        ) : (
          <p className="py-4 text-center text-sm text-app-text-muted">
            No bookings in this period.
          </p>
        )}
      </Card>

      {/* Report links */}
      <div className="grid gap-3 sm:grid-cols-2">
        <Link
          href={`${INSIGHTS}/no-shows${suffix}`}
          className="group flex items-center gap-3 rounded-2xl border border-app-border bg-app-surface p-4 shadow-sm hover:bg-app-hover"
        >
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-app-error-bg text-app-error">
            <UserX className="h-5 w-5" aria-hidden />
          </span>
          <span className="min-w-0 flex-1">
            <span className="block text-sm font-semibold text-app-text">
              No-shows & cancellations
            </span>
            <span className="block text-xs text-app-text-secondary">
              {formatRate(noShowRate)} no-show ·{" "}
              {formatRate(summaryRange.cancellationRate)} cancelled
            </span>
          </span>
          <ArrowRight
            className="h-4 w-4 shrink-0 text-app-text-muted group-hover:text-app-text"
            aria-hidden
          />
        </Link>

        {isBusinessOwner(owner) ? (
          <Link
            href={`${INSIGHTS}/team${suffix}`}
            className="group flex items-center gap-3 rounded-2xl border border-app-border bg-app-surface p-4 shadow-sm hover:bg-app-hover"
          >
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-app-business-bg text-app-business">
              <Users className="h-5 w-5" aria-hidden />
            </span>
            <span className="min-w-0 flex-1">
              <span className="block text-sm font-semibold text-app-text">
                Team performance
              </span>
              <span className="block text-xs text-app-text-secondary">
                Bookings, revenue & no-shows by member
              </span>
            </span>
            <ArrowRight
              className="h-4 w-4 shrink-0 text-app-text-muted group-hover:text-app-text"
              aria-hidden
            />
          </Link>
        ) : (
          <Link
            href={`${INSIGHTS}/event-types${suffix}`}
            className="group flex items-center gap-3 rounded-2xl border border-app-border bg-app-surface p-4 shadow-sm hover:bg-app-hover"
          >
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-app-info-bg text-app-info">
              <CheckCircle2 className="h-5 w-5" aria-hidden />
            </span>
            <span className="min-w-0 flex-1">
              <span className="block text-sm font-semibold text-app-text">
                Per-event-type performance
              </span>
              <span className="block text-xs text-app-text-secondary">
                Volume, completion & no-show by type
              </span>
            </span>
            <ArrowRight
              className="h-4 w-4 shrink-0 text-app-text-muted group-hover:text-app-text"
              aria-hidden
            />
          </Link>
        )}
      </div>
    </div>
  );
}
