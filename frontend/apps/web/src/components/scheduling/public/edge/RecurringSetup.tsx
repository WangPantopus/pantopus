"use client";

// D12 — Recurring / multi-session setup. One decision lays down many linked
// bookings ("every Tuesday for 6 weeks"), distinct from a package credit wallet.
// Authed (personal customer) via SchedulingOwner → POST /scheduling/bookings/
// recurring. Reads the event-type context from query params (event-type id +
// base start time), computes the occurrence list client-side, and confirms the
// whole series in one go; a 409 on any session surfaces a calm conflict banner.

import { useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import {
  Repeat,
  Clock,
  CalendarCheck,
  AlertCircle,
  ArrowRight,
  Minus,
  Plus,
  CheckCircle2,
} from "lucide-react";
import clsx from "clsx";
import type { SlotConflict } from "@pantopus/types";
import { scheduling } from "@pantopus/api";
import {
  decodeError,
  asSlotConflict,
  SlotConflictAlternatives,
  useSchedulingOwner,
} from "@/components/scheduling";
import EmptyState from "@/components/ui/EmptyState";
import {
  formatDay,
  formatTime,
  money,
  durationLabel,
  viewerTimezone,
  tzAbbrev,
} from "./edgeUtils";

type Interval = "weekly" | "biweekly" | "monthly";

const INTERVAL_LABEL: Record<Interval, string> = {
  weekly: "Weekly",
  biweekly: "Every 2 weeks",
  monthly: "Monthly",
};

const WEEKDAYS = ["S", "M", "T", "W", "T", "F", "S"];

function addInterval(d: Date, interval: Interval, steps: number): Date {
  const next = new Date(d);
  if (interval === "monthly") next.setMonth(next.getMonth() + steps);
  else
    next.setDate(next.getDate() + steps * (interval === "biweekly" ? 14 : 7));
  return next;
}

export default function RecurringSetup() {
  const owner = useSchedulingOwner();
  const searchParams = useSearchParams();
  const params = useMemo(
    () => searchParams ?? new URLSearchParams(),
    [searchParams],
  );
  const tz = useMemo(() => params.get("tz") || viewerTimezone(), [params]);

  const eventTypeId = params.get("eventTypeId") || params.get("event_type_id");
  const eventName = params.get("eventName") || "your event";
  const hostName = params.get("host") || null;
  const durationMin = Number(params.get("duration")) || 30;
  const priceCents = Number(params.get("price")) || 0;
  const currency = params.get("currency") || "USD";

  // Base start: provided ?start, else the next round hour a day out.
  const baseStart = useMemo(() => {
    const fromQuery = params.get("start");
    if (fromQuery) {
      const d = new Date(fromQuery);
      if (!Number.isNaN(d.getTime())) return d;
    }
    const d = new Date();
    d.setDate(d.getDate() + 1);
    d.setHours(14, 0, 0, 0);
    return d;
  }, [params]);

  const [interval, setInterval] = useState<Interval>("weekly");
  const [count, setCount] = useState(6);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [conflict, setConflict] = useState<SlotConflict | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [doneCount, setDoneCount] = useState<number | null>(null);

  const occurrences = useMemo(() => {
    return Array.from({ length: count }, (_, i) =>
      addInterval(baseStart, interval, i),
    );
  }, [baseStart, interval, count]);

  const total = priceCents > 0 ? priceCents * count : 0;
  const rangeLabel =
    occurrences.length > 0
      ? `${formatDay(occurrences[0].toISOString(), tz)} – ${formatDay(occurrences[occurrences.length - 1].toISOString(), tz)}`
      : "";

  const confirm = async () => {
    if (!eventTypeId) return;
    setSubmitting(true);
    setConflict(null);
    setError(null);
    try {
      const res = await scheduling.createRecurringBookings(
        {
          event_type_id: eventTypeId,
          sessions: occurrences.map((d) => d.toISOString()),
          invitee_timezone: tz,
        },
        owner,
      );
      setDoneCount(res.bookings?.length ?? count);
    } catch (err) {
      const decoded = decodeError(err);
      const c = asSlotConflict(decoded);
      if (c) setConflict(c);
      else setError(decoded.message);
    } finally {
      setSubmitting(false);
    }
  };

  // No event-type context → honest, actionable empty state.
  if (!eventTypeId) {
    return (
      <EmptyState
        icon={Repeat}
        title="Start a series from an event type"
        description="Open an event type that allows recurrence and choose “Book a series” to set up repeating sessions here."
      />
    );
  }

  if (doneCount != null) {
    return (
      <div className="mx-auto max-w-md py-10 text-center">
        <span className="mx-auto mb-5 flex h-16 w-16 items-center justify-center rounded-full bg-app-success-bg">
          <CheckCircle2 className="h-8 w-8 text-app-success" aria-hidden />
        </span>
        <h1 className="text-xl font-bold text-app-text-strong">
          {doneCount} {doneCount === 1 ? "session" : "sessions"} booked
        </h1>
        <p className="mx-auto mt-2 max-w-xs text-sm text-app-text-muted">
          Your series is set. We’ll send a confirmation for each session.
        </p>
        <Link
          href="/app/scheduling/my-bookings"
          className="mt-6 inline-flex items-center gap-2 rounded-xl bg-app-personal px-4 py-2.5 text-sm font-bold text-white"
        >
          View my bookings
          <ArrowRight className="h-4 w-4" aria-hidden />
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-md space-y-4 pb-28">
      <p className="px-1 text-sm text-app-text-secondary">
        Book the whole series in one go. We’ll use the same time each week and
        flag any that’s taken.
      </p>

      {/* Recurrence controls */}
      <div className="space-y-4 rounded-2xl border border-app-border bg-app-surface p-3.5 shadow-sm">
        <div>
          <p className="mb-1.5 text-xs font-semibold text-app-text-secondary">
            Repeats
          </p>
          <div className="grid grid-cols-3 gap-2">
            {(Object.keys(INTERVAL_LABEL) as Interval[]).map((iv) => (
              <button
                key={iv}
                type="button"
                onClick={() => setInterval(iv)}
                aria-pressed={interval === iv}
                className={clsx(
                  "flex items-center justify-center gap-1.5 rounded-lg border px-2 py-2 text-xs font-semibold",
                  interval === iv
                    ? "border-app-personal bg-app-personal-bg text-app-personal"
                    : "border-app-border bg-app-surface text-app-text-secondary hover:bg-app-hover",
                )}
              >
                {iv === interval && (
                  <Repeat className="h-3.5 w-3.5" aria-hidden />
                )}
                {INTERVAL_LABEL[iv]}
              </button>
            ))}
          </div>
        </div>

        <div>
          <p className="mb-1.5 text-xs font-semibold text-app-text-secondary">
            On
          </p>
          <div className="flex gap-1.5">
            {WEEKDAYS.map((d, i) => {
              const on = i === baseStart.getDay();
              return (
                <span
                  key={i}
                  className={clsx(
                    "flex h-8 flex-1 items-center justify-center rounded-lg border text-xs font-bold",
                    on
                      ? "border-app-personal bg-app-personal text-white"
                      : "border-app-border bg-app-surface text-app-text-muted",
                  )}
                  aria-hidden
                >
                  {d}
                </span>
              );
            })}
          </div>
        </div>

        <div className="flex items-end justify-between gap-4">
          <div>
            <p className="mb-1.5 text-xs font-semibold text-app-text-secondary">
              Time
            </p>
            <span className="inline-flex items-center gap-1.5 rounded-full bg-app-personal-bg px-3 py-1.5 text-xs font-bold text-app-personal">
              <Clock className="h-3.5 w-3.5" aria-hidden />
              {formatTime(baseStart.toISOString(), tz)}
            </span>
          </div>
          <div>
            <p className="mb-1.5 text-right text-xs font-semibold text-app-text-secondary">
              How many
            </p>
            <div className="inline-flex items-center overflow-hidden rounded-lg border border-app-border">
              <button
                type="button"
                aria-label="Fewer sessions"
                onClick={() => setCount((c) => Math.max(1, c - 1))}
                className="flex h-9 w-9 items-center justify-center border-r border-app-border text-app-text-secondary hover:bg-app-hover"
              >
                <Minus className="h-3.5 w-3.5" aria-hidden />
              </button>
              <button
                type="button"
                onClick={() => setPickerOpen((o) => !o)}
                className="flex h-9 min-w-9 items-center justify-center px-2 text-sm font-bold tabular-nums text-app-text"
              >
                {count}
              </button>
              <button
                type="button"
                aria-label="More sessions"
                onClick={() => setCount((c) => Math.min(52, c + 1))}
                className="flex h-9 w-9 items-center justify-center border-l border-app-border text-app-personal hover:bg-app-hover"
              >
                <Plus className="h-3.5 w-3.5" aria-hidden />
              </button>
            </div>
          </div>
        </div>

        {pickerOpen && (
          <div className="space-y-2 rounded-xl bg-app-surface-sunken p-3">
            <div className="flex gap-2">
              {[4, 6, 8, 12].map((n) => (
                <button
                  key={n}
                  type="button"
                  onClick={() => setCount(n)}
                  className={clsx(
                    "flex-1 rounded-lg border py-2 text-sm font-bold tabular-nums",
                    count === n
                      ? "border-app-personal bg-app-personal text-white"
                      : "border-app-border bg-app-surface text-app-text-secondary",
                  )}
                >
                  {n}
                </button>
              ))}
            </div>
            <p className="text-xs text-app-text-muted">
              We’ll find {formatTime(baseStart.toISOString(), tz)} each time and
              flag any that’s taken.
            </p>
          </div>
        )}
      </div>

      {conflict && (
        <SlotConflictAlternatives
          conflict={conflict}
          pillar="personal"
          onPick={() => {
            // A session clashed. Clearing lets the booker adjust the count /
            // pattern and re-confirm against fresh availability.
            setConflict(null);
            setError(null);
          }}
          onPickAnother={() => setConflict(null)}
        />
      )}

      {error && (
        <div className="flex items-center gap-2 rounded-xl border border-app-error-light bg-app-error-bg p-3">
          <AlertCircle
            className="h-4 w-4 shrink-0 text-app-error"
            aria-hidden
          />
          <p className="text-xs font-semibold text-app-error">{error}</p>
        </div>
      )}

      {/* Occurrence list */}
      <div>
        <p className="mb-2 px-1 text-[11px] font-bold uppercase tracking-wider text-app-text-muted">
          {count} {count === 1 ? "session" : "sessions"}
        </p>
        <div className="space-y-2">
          {occurrences.map((d, i) => (
            <div
              key={i}
              className="flex items-center gap-3 rounded-xl border border-app-border bg-app-surface px-3 py-2.5 shadow-sm"
            >
              <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-app-success-bg text-app-success">
                <CalendarCheck className="h-4 w-4" aria-hidden />
              </span>
              <div className="min-w-0 flex-1">
                <p className="truncate text-[13px] font-bold text-app-text">
                  {formatDay(d.toISOString(), tz)}
                </p>
                <p className="mt-0.5 text-[11px] text-app-text-muted tabular-nums">
                  {formatTime(d.toISOString(), tz)} ·{" "}
                  {durationLabel(durationMin)}
                </p>
              </div>
              <span className="inline-flex items-center gap-1 rounded-full bg-app-success-bg px-2 py-0.5 text-[10px] font-bold uppercase text-app-success">
                Open
              </span>
            </div>
          ))}
        </div>
      </div>

      {/* Summary chip */}
      <div className="flex items-center gap-3 rounded-xl border border-app-personal/30 bg-app-personal-bg p-3">
        <Repeat className="h-4 w-4 shrink-0 text-app-personal" aria-hidden />
        <div className="min-w-0 flex-1">
          <p className="truncate text-xs font-bold text-app-personal">
            {count} sessions · {formatTime(baseStart.toISOString(), tz)}
          </p>
          <p className="mt-0.5 truncate text-[11px] text-app-text-muted tabular-nums">
            {rangeLabel}
            {total > 0 ? ` · ${money(total, currency)} total` : ""}
          </p>
        </div>
      </div>

      {/* Sticky confirm */}
      <div className="fixed inset-x-0 bottom-0 z-10 border-t border-app-border bg-app-surface/95 px-4 py-3 backdrop-blur">
        <div className="mx-auto max-w-md">
          <button
            type="button"
            onClick={confirm}
            disabled={submitting}
            className="flex w-full items-center justify-center gap-2 rounded-xl bg-app-personal px-4 py-3 text-sm font-bold text-white disabled:opacity-60"
          >
            {submitting
              ? "Booking…"
              : `Book ${count} ${count === 1 ? "session" : "sessions"}`}
            {!submitting && <ArrowRight className="h-4 w-4" aria-hidden />}
          </button>
          <p className="mt-1.5 text-center text-[11px] text-app-text-muted">
            {eventName}
            {hostName ? ` · with ${hostName}` : ""} · times in {tzAbbrev(tz)}
          </p>
        </div>
      </div>
    </div>
  );
}
