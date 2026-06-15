"use client";

// D11 — My bookings (customer/booker-side). A signed-in user's outgoing
// bookings across every host (distinct from the host inbox + from my-packages).
// Segmented Upcoming/Past, grouped under relative overlines, each row tinted by
// the host's pillar. Authed via SchedulingOwner (personal customer). Ships
// loading skeleton / empty / loaded / error+Retry.

import { useEffect, useMemo, useState } from "react";
import { Calendar, AlertCircle } from "lucide-react";
import clsx from "clsx";
import type { Booking } from "@pantopus/types";
import { scheduling } from "@pantopus/api";
import {
  BookingStatusPill,
  decodeError,
  pillarTokens,
  pillarForOwner,
  useSchedulingOwner,
} from "@/components/scheduling";
import EmptyState from "@/components/ui/EmptyState";
import ErrorState from "@/components/ui/ErrorState";
import {
  formatDay,
  formatTime,
  tzAbbrev,
  timeGroup,
  isPastBooking,
  viewerTimezone,
  type BookingTimeGroup,
} from "./edgeUtils";

type Tab = "upcoming" | "past";

const UPCOMING_ORDER: BookingTimeGroup[] = [
  "Today",
  "This week",
  "Next week",
  "Later",
];
const PAST_ORDER: BookingTimeGroup[] = ["This month", "Earlier"];

function Row({ booking, tz }: { booking: Booking; tz: string }) {
  const pillar = pillarForOwner(booking.owner_type);
  const tk = pillarTokens(pillar);
  const past = isPastBooking(booking);
  return (
    <div
      className={clsx(
        "flex items-center gap-3 rounded-2xl border border-app-border bg-app-surface p-3 shadow-sm",
        past && "opacity-70",
      )}
    >
      <span
        className={clsx(
          "relative flex h-10 w-10 shrink-0 items-center justify-center rounded-full",
          tk.bgSoft,
        )}
        aria-hidden
      >
        <Calendar className={clsx("h-5 w-5", tk.text)} />
        <span
          className={clsx(
            "absolute -bottom-0.5 -right-0.5 h-3 w-3 rounded-full border-2 border-app-surface",
            tk.bg,
          )}
        />
      </span>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-bold text-app-text">
          {formatDay(booking.start_at, tz)}
        </p>
        <p className="mt-0.5 truncate text-xs text-app-text-muted tabular-nums">
          {formatTime(booking.start_at, tz)}
          {booking.end_at ? ` – ${formatTime(booking.end_at, tz)}` : ""} ·{" "}
          {tzAbbrev(tz)}
        </p>
      </div>
      <BookingStatusPill status={booking.status} />
    </div>
  );
}

function GroupedRows({
  bookings,
  order,
  past,
  tz,
}: {
  bookings: Booking[];
  order: BookingTimeGroup[];
  past: boolean;
  tz: string;
}) {
  const grouped = useMemo(() => {
    const map = new Map<BookingTimeGroup, Booking[]>();
    for (const b of bookings) {
      const g = timeGroup(b.start_at, past);
      const arr = map.get(g) ?? [];
      arr.push(b);
      map.set(g, arr);
    }
    return map;
  }, [bookings, past]);

  return (
    <div className="space-y-5">
      {order
        .filter((g) => grouped.has(g))
        .map((g) => (
          <div key={g}>
            <p className="mb-2 px-1 text-[11px] font-bold uppercase tracking-wider text-app-text-muted">
              {g}
            </p>
            <div className="space-y-2.5">
              {(grouped.get(g) ?? []).map((b) => (
                <Row key={b.id} booking={b} tz={tz} />
              ))}
            </div>
          </div>
        ))}
    </div>
  );
}

function SkeletonRow() {
  return (
    <div className="flex items-center gap-3 rounded-2xl border border-app-border bg-app-surface p-3 shadow-sm">
      <div className="h-10 w-10 shrink-0 animate-pulse rounded-full bg-app-surface-muted" />
      <div className="flex-1 space-y-2">
        <div className="h-3 w-1/2 animate-pulse rounded bg-app-surface-muted" />
        <div className="h-2.5 w-2/3 animate-pulse rounded bg-app-surface-muted" />
      </div>
      <div className="h-5 w-16 animate-pulse rounded-full bg-app-surface-muted" />
    </div>
  );
}

export default function MyBookingsList() {
  const owner = useSchedulingOwner();
  const tz = useMemo(() => viewerTimezone(), []);
  const [tab, setTab] = useState<Tab>("upcoming");
  const [bookings, setBookings] = useState<Booking[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setBookings(null);
    setError(null);
    scheduling
      .getMyBookings(owner)
      .then((res) => {
        if (cancelled) return;
        setBookings(res.bookings ?? []);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(decodeError(err).message);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [owner.ownerType, owner.ownerId, owner.homeId, reloadKey]);

  const { upcoming, past } = useMemo(() => {
    const list = bookings ?? [];
    const up = list
      .filter((b) => !isPastBooking(b))
      .sort((a, b) => a.start_at.localeCompare(b.start_at));
    const pa = list
      .filter((b) => isPastBooking(b))
      .sort((a, b) => b.start_at.localeCompare(a.start_at));
    return { upcoming: up, past: pa };
  }, [bookings]);

  const active = tab === "upcoming" ? upcoming : past;

  return (
    <div>
      {/* Segmented control */}
      <div className="mb-5 flex gap-1 rounded-xl bg-app-surface-sunken p-1">
        {(["upcoming", "past"] as Tab[]).map((t) => (
          <button
            key={t}
            type="button"
            onClick={() => setTab(t)}
            aria-pressed={tab === t}
            className={clsx(
              "flex-1 rounded-lg px-3 py-2 text-sm font-semibold capitalize transition-colors",
              tab === t
                ? "bg-app-surface text-app-text shadow-sm"
                : "text-app-text-muted hover:text-app-text",
            )}
          >
            {t}
          </button>
        ))}
      </div>

      {error ? (
        <ErrorState
          message={error}
          onRetry={() => setReloadKey((k) => k + 1)}
        />
      ) : bookings === null ? (
        <div className="space-y-2.5">
          <div className="mb-2 h-2.5 w-20 animate-pulse rounded bg-app-surface-muted" />
          <SkeletonRow />
          <SkeletonRow />
          <SkeletonRow />
        </div>
      ) : active.length === 0 ? (
        tab === "upcoming" ? (
          <EmptyState
            icon={Calendar}
            title="You haven’t booked anything yet"
            description="Bookings you make show up here — everything in one place."
          />
        ) : (
          <EmptyState
            icon={Calendar}
            title="No past bookings"
            description="Bookings you’ve completed or that have passed will show up here."
          />
        )
      ) : (
        <GroupedRows
          bookings={active}
          order={tab === "upcoming" ? UPCOMING_ORDER : PAST_ORDER}
          past={tab === "past"}
          tz={tz}
        />
      )}

      {bookings !== null && !error && active.length > 0 && (
        <p className="mt-6 flex items-center justify-center gap-1.5 text-xs text-app-text-muted">
          <AlertCircle className="h-3 w-3" aria-hidden />
          Times shown in {tzAbbrev(tz)}.
        </p>
      )}
    </div>
  );
}
