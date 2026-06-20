"use client";

// W17 · H12 — Team Performance (business-only). Per-member bookings and
// no-show rate from GET /bookings/insights/team. Aligned to iOS/Android:
// two-option sort (Bookings / No-show rate), metric strip showing bookings +
// no-show rate per member. No KPI aggregate tiles (absent on native).
// Round-robin balance card deferred — API does not return balance data.
// For non-business owners (or a BUSINESS_ONLY response) we show a notice.

import { useMemo, useState } from "react";
import { Building2, Users } from "lucide-react";
import * as api from "@pantopus/api";
import type { TeamInsights } from "@pantopus/types";
import { useSchedulingOwner } from "@/components/scheduling/SchedulingOwnerProvider";
import { pillarForOwner } from "@/components/scheduling/pillarTokens";
import { useReport } from "./useReport";
import { useInsightsFilters } from "./useInsightsFilters";
import { insightsDays } from "./filters";
import { formatCount, formatRate } from "./format";
import { isBusinessOnly, isBusinessOwner } from "./gating";
import {
  Avatar,
  Card,
  EmptyReport,
  InlineRetry,
  NoticeCard,
  ReportSkeleton,
} from "./ui";

// Two-option sort, mirroring iOS/Android (bookings / no-show only — revenue
// sort removed since it diverged from native without a design spec).
type SortKey = "bookings" | "no_show";

const SORTS: ReadonlyArray<{ id: SortKey; label: string }> = [
  { id: "bookings", label: "Bookings" },
  { id: "no_show", label: "No-show rate" },
];

export default function TeamPerformance() {
  const owner = useSchedulingOwner();
  const pillar = pillarForOwner(owner.ownerType);
  const { filters, query } = useInsightsFilters();
  const ownerKey = `${owner.ownerType}:${owner.ownerId ?? owner.homeId ?? ""}`;
  const business = isBusinessOwner(owner);
  const [sort, setSort] = useState<SortKey>("bookings");

  const { phase, data, error, reload } =
    useReport<TeamInsights | null>(async () => {
      if (!business) return null;
      return api.scheduling.getTeamInsights(insightsDays(filters), owner);
    }, [query, ownerKey, business]);

  const members = useMemo(() => {
    const list = data?.teamMembers ? [...data.teamMembers] : [];
    if (sort === "no_show") list.sort((a, b) => b.noShowRate - a.noShowRate);
    else list.sort((a, b) => b.bookingsCount - a.bookingsCount);
    return list;
  }, [data, sort]);

  if (!business)
    return (
      <NoticeCard
        icon={Building2}
        title="Team performance is for business accounts"
        body="Switch to a business profile to see per-member bookings, revenue and no-show rates for your team."
        tone="info"
      />
    );

  if (phase === "loading") return <ReportSkeleton kpis={0} />;
  if (phase === "error") {
    if (error && isBusinessOnly(error))
      return (
        <NoticeCard
          icon={Building2}
          title="Team performance is for business accounts"
          body="This report is only available for business owners with a team."
          tone="info"
        />
      );
    return (
      <InlineRetry
        message="We couldn't load team performance. Try again."
        onRetry={reload}
      />
    );
  }
  if (!data)
    return (
      <InlineRetry
        message="We couldn't load team performance. Try again."
        onRetry={reload}
      />
    );

  if (members.length === 0)
    return (
      <EmptyReport
        icon={Users}
        title="No team activity yet"
        body="Once your team members start taking bookings in this period, their performance will show here."
        pillar={pillar}
      />
    );

  const maxBookings = members.reduce((m, x) => Math.max(m, x.bookingsCount), 0);

  return (
    <div className="space-y-4">
      <Card
        title="By member"
        icon={Users}
        action={
          <div className="flex gap-0.5 rounded-[10px] bg-app-surface-sunken p-0.5">
            {SORTS.map((s) => {
              const on = s.id === sort;
              return (
                <button
                  key={s.id}
                  type="button"
                  onClick={() => setSort(s.id)}
                  aria-pressed={on}
                  className={`rounded-md px-2.5 py-1 text-[11px] font-semibold transition ${
                    on
                      ? "bg-app-surface text-app-text-strong shadow-sm"
                      : "text-app-text-secondary hover:text-app-text"
                  }`}
                >
                  {s.label}
                </button>
              );
            })}
          </div>
        }
      >
        <ul className="divide-y divide-app-border-subtle">
          {members.map((m) => {
            const pct =
              maxBookings > 0
                ? Math.round((m.bookingsCount / maxBookings) * 100)
                : 0;
            return (
              <li key={m.user_id} className="py-3">
                <div className="flex items-center gap-3">
                  <Avatar name={m.name} pillar={pillar} />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-semibold text-app-text">
                      {m.name || "Unknown"}
                    </span>
                    <span className="block text-xs text-app-text-muted">
                      {formatCount(m.bookingsCount)}{" "}
                      {m.bookingsCount === 1 ? "booking" : "bookings"} ·{" "}
                      {formatRate(m.noShowRate)} no-show
                    </span>
                  </span>
                  <span className="shrink-0 text-right">
                    <span className="text-[17px] font-bold tabular-nums text-app-text">
                      {formatCount(m.bookingsCount)}
                    </span>
                    <span className="block text-[10px] font-semibold uppercase tracking-wide text-app-text-muted">
                      bookings
                    </span>
                  </span>
                </div>
                <div className="mt-2 h-2 overflow-hidden rounded-full bg-app-surface-sunken">
                  <div
                    className="h-full rounded-full bg-app-business"
                    style={{
                      width: `${Math.max(pct, m.bookingsCount > 0 ? 4 : 0)}%`,
                    }}
                  />
                </div>
              </li>
            );
          })}
        </ul>
      </Card>
    </div>
  );
}
