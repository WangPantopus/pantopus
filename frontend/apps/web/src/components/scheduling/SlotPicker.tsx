"use client";

// The canonical date + time slot picker. A month calendar and the selected
// day's times stack in one scroll (not a wizard). Slots are fetched CLIENT-SIDE
// (no-store via the axios client) for both public (slug + eventTypeSlug) and
// manage (manageToken) flows; host flows inject a `fetchSlots` override
// (bookings/:id/available-slots, find-a-time, etc.). Every state is handled:
// loading skeleton, day-with-slots (grouped Morning/Afternoon), fully-booked
// day, and no-availability-in-month — an empty result is a calm, expected
// outcome, never an error. Available days are solid, unavailable muted, today
// carries a pillar ring, the selected day is a filled pillar circle.

import { useCallback, useEffect, useMemo, useState } from "react";
import { ChevronLeft, ChevronRight, Globe } from "lucide-react";
import clsx from "clsx";
import type { BookingSlot } from "@pantopus/types";
import { publicBooking } from "@pantopus/api";
import ErrorState from "@/components/ui/ErrorState";
import { decodeError } from "./decodeError";
import { pillarTokens, type Pillar } from "./pillarTokens";
import TimezoneSelector, {
  detectTimezone,
  zoneLabel,
} from "./TimezoneSelector";

const MONTH_NAMES = [
  "January",
  "February",
  "March",
  "April",
  "May",
  "June",
  "July",
  "August",
  "September",
  "October",
  "November",
  "December",
];
const WEEKDAY_LABELS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

export interface SlotFetchRange {
  from: string;
  to: string;
  tz: string;
}

interface SlotPickerProps {
  slug?: string;
  eventTypeSlug?: string;
  manageToken?: string;
  /** Override the slot fetcher (host flows). Receives an ISO date range + tz. */
  fetchSlots?: (range: SlotFetchRange) => Promise<BookingSlot[]>;
  tz?: string;
  onTzChange?: (tz: string) => void;
  onPick: (slot: BookingSlot) => void;
  /** Currently selected slot start (ISO) for highlight. */
  selected?: string | null;
  pillar?: Pillar;
  /** Disable picking (e.g. past the reschedule cutoff). */
  disabled?: boolean;
  className?: string;
}

function pad(n: number): string {
  return String(n).padStart(2, "0");
}

function monthRange(
  year: number,
  month: number,
): { first: string; last: string } {
  const lastDay = new Date(year, month + 1, 0).getDate();
  return {
    first: `${year}-${pad(month + 1)}-01`,
    last: `${year}-${pad(month + 1)}-${pad(lastDay)}`,
  };
}

function todayKeyInTz(tz: string): string {
  try {
    return new Intl.DateTimeFormat("en-CA", {
      timeZone: tz,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).format(new Date());
  } catch {
    return new Date().toISOString().slice(0, 10);
  }
}

function slotDayKey(slot: BookingSlot): string {
  return (slot.startLocal || slot.start).slice(0, 10);
}

function slotParts(slot: BookingSlot): { hour: number; label: string } {
  const s = slot.startLocal || slot.start;
  const m = s.match(/T(\d{2}):(\d{2})/);
  if (!m) return { hour: 0, label: s };
  const h = parseInt(m[1], 10);
  const min = m[2];
  const ampm = h >= 12 ? "PM" : "AM";
  const hour12 = h % 12 === 0 ? 12 : h % 12;
  return { hour: h, label: `${hour12}:${min} ${ampm}` };
}

// Human, date-aware label from an ISO date key (parsed as a local civil date so
// negative-offset zones don't drift to the previous day).
function humanDate(dateKey: string, opts: Intl.DateTimeFormatOptions): string {
  const [y, m, d] = dateKey.split("-").map((n) => parseInt(n, 10));
  if (!y || !m || !d) return dateKey;
  return new Date(y, m - 1, d).toLocaleDateString("en-US", opts);
}

// H14 a11y: the slot button's full accessible name — "Tue, Jun 16, 3:00 PM,
// available" — so the time is never announced without its date or availability.
function slotAriaLabel(slot: BookingSlot): string {
  const date = humanDate(slotDayKey(slot), {
    weekday: "short",
    month: "short",
    day: "numeric",
  });
  return `${date}, ${slotParts(slot).label}, available`;
}

// H14 a11y: pillar-tinted keyboard focus ring (focus-visible → no change for
// mouse users). Literal classes so Tailwind's JIT generates them.
const FOCUS_RING: Record<Pillar, string> = {
  personal: "focus-visible:ring-app-personal",
  home: "focus-visible:ring-app-home",
  business: "focus-visible:ring-app-business",
};
const FOCUS_BASE =
  "focus:outline-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-1";

function SlotGroup({
  title,
  slots,
  onPick,
  selected,
  disabled,
  tk,
  pillar,
}: {
  title: string;
  slots: BookingSlot[];
  onPick: (s: BookingSlot) => void;
  selected?: string | null;
  disabled?: boolean;
  tk: ReturnType<typeof pillarTokens>;
  pillar: Pillar;
}) {
  if (slots.length === 0) return null;
  return (
    <div role="group" aria-label={`${title} times`}>
      <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-app-text-muted">
        {title}
      </p>
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
        {slots.map((slot) => {
          const isSel = selected === slot.start;
          return (
            <button
              key={`${slot.start}-${slot.end}`}
              type="button"
              disabled={disabled}
              onClick={() => onPick(slot)}
              aria-pressed={isSel}
              aria-label={slotAriaLabel(slot)}
              className={clsx(
                "rounded-lg border px-3 py-2.5 text-sm font-medium transition-colors",
                FOCUS_BASE,
                FOCUS_RING[pillar],
                disabled && "cursor-not-allowed opacity-50",
                isSel
                  ? clsx(tk.bg, tk.textOn, "border-transparent")
                  : "border-app-border bg-app-surface text-app-text hover:bg-app-hover",
              )}
            >
              {slotParts(slot).label}
            </button>
          );
        })}
      </div>
    </div>
  );
}

export default function SlotPicker({
  slug,
  eventTypeSlug,
  manageToken,
  fetchSlots,
  tz: tzProp,
  onTzChange,
  onPick,
  selected,
  pillar = "personal",
  disabled = false,
  className,
}: SlotPickerProps) {
  const tk = pillarTokens(pillar);
  const [tzInternal, setTzInternal] = useState<string>(
    () => tzProp || detectTimezone(),
  );
  const tz = tzProp || tzInternal;
  const [tzOpen, setTzOpen] = useState(false);

  const now = new Date();
  const [cursor, setCursor] = useState<{ year: number; month: number }>({
    year: now.getFullYear(),
    month: now.getMonth(),
  });

  const [slotsByDay, setSlotsByDay] = useState<Record<string, BookingSlot[]>>(
    {},
  );
  const [selectedDay, setSelectedDay] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  const loadSlots = useCallback(
    async (range: SlotFetchRange): Promise<BookingSlot[]> => {
      if (fetchSlots) return fetchSlots(range);
      if (slug && eventTypeSlug) {
        const res = await publicBooking.getPublicSlots(
          slug,
          eventTypeSlug,
          range,
        );
        return res.slots ?? [];
      }
      if (manageToken) {
        const res = await publicBooking.getManageSlots(manageToken, range);
        return res.slots ?? [];
      }
      return [];
    },
    [fetchSlots, slug, eventTypeSlug, manageToken],
  );

  useEffect(() => {
    let cancelled = false;
    const { first, last } = monthRange(cursor.year, cursor.month);
    const today = todayKeyInTz(tz);
    const from = first < today ? today : first;
    setLoading(true);
    setError(null);
    loadSlots({ from, to: last, tz })
      .then((slots) => {
        if (cancelled) return;
        const byDay: Record<string, BookingSlot[]> = {};
        for (const s of slots) {
          const k = slotDayKey(s);
          (byDay[k] ||= []).push(s);
        }
        setSlotsByDay(byDay);
        setSelectedDay((prev) => {
          if (prev && byDay[prev]?.length) return prev;
          const firstAvail = Object.keys(byDay).sort()[0];
          return firstAvail ?? null;
        });
        setLoading(false);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(decodeError(err).message);
        setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [cursor, tz, loadSlots, reloadKey]);

  const handleTz = (next: string) => {
    if (!tzProp) setTzInternal(next);
    onTzChange?.(next);
  };

  const goMonth = (delta: number) => {
    setCursor((c) => {
      const d = new Date(c.year, c.month + delta, 1);
      return { year: d.getFullYear(), month: d.getMonth() };
    });
  };

  const todayKey = todayKeyInTz(tz);

  // Build the 6-week month grid.
  const cells = useMemo(() => {
    const firstWeekday = new Date(cursor.year, cursor.month, 1).getDay();
    const daysInMonth = new Date(cursor.year, cursor.month + 1, 0).getDate();
    const out: Array<{ key: string; day: number; inMonth: boolean }> = [];
    for (let i = 0; i < firstWeekday; i++)
      out.push({ key: `pad-${i}`, day: 0, inMonth: false });
    for (let d = 1; d <= daysInMonth; d++) {
      out.push({
        key: `${cursor.year}-${pad(cursor.month + 1)}-${pad(d)}`,
        day: d,
        inMonth: true,
      });
    }
    while (out.length % 7 !== 0)
      out.push({ key: `tail-${out.length}`, day: 0, inMonth: false });
    return out;
  }, [cursor]);

  const monthHasSlots = Object.keys(slotsByDay).length > 0;
  const daySlots = selectedDay ? (slotsByDay[selectedDay] ?? []) : [];
  const morning = daySlots.filter((s) => slotParts(s).hour < 12);
  const afternoon = daySlots.filter((s) => slotParts(s).hour >= 12);

  const jumpToNextAvailable = () => {
    const next = Object.keys(slotsByDay)
      .sort()
      .find((k) => k > (selectedDay ?? ""));
    if (next) setSelectedDay(next);
    else goMonth(1);
  };

  return (
    <div className={clsx("space-y-5", className)}>
      {/* Timezone chip */}
      <button
        type="button"
        onClick={() => setTzOpen(true)}
        aria-haspopup="dialog"
        aria-expanded={tzOpen}
        aria-label={`Time zone: ${zoneLabel(tz)}. Change time zone`}
        className={clsx(
          "inline-flex items-center gap-2 rounded-full border border-app-border bg-app-surface px-3 py-1.5 text-xs font-medium text-app-text hover:bg-app-hover",
          FOCUS_BASE,
          FOCUS_RING[pillar],
        )}
      >
        <Globe className="h-3.5 w-3.5 text-app-text-muted" aria-hidden />
        {zoneLabel(tz)}
      </button>

      {/* Month header */}
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-app-text-strong">
          {MONTH_NAMES[cursor.month]} {cursor.year}
        </h3>
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={() => goMonth(-1)}
            aria-label="Previous month"
            className="flex h-8 w-8 items-center justify-center rounded-lg border border-app-border bg-app-surface text-app-text hover:bg-app-hover"
          >
            <ChevronLeft className="h-4 w-4" aria-hidden />
          </button>
          <button
            type="button"
            onClick={() => goMonth(1)}
            aria-label="Next month"
            className="flex h-8 w-8 items-center justify-center rounded-lg border border-app-border bg-app-surface text-app-text hover:bg-app-hover"
          >
            <ChevronRight className="h-4 w-4" aria-hidden />
          </button>
        </div>
      </div>

      {/* Calendar grid */}
      <div>
        <div className="mb-1 grid grid-cols-7 gap-1">
          {WEEKDAY_LABELS.map((w) => (
            <div
              key={w}
              className="py-1 text-center text-[11px] font-semibold text-app-text-muted"
            >
              {w}
            </div>
          ))}
        </div>
        <div className="grid grid-cols-7 gap-1">
          {cells.map((cell) => {
            if (!cell.inMonth) return <div key={cell.key} />;
            const available = (slotsByDay[cell.key]?.length ?? 0) > 0;
            const isToday = cell.key === todayKey;
            const isSelected = cell.key === selectedDay;
            const isPast = cell.key < todayKey;
            return (
              <button
                key={cell.key}
                type="button"
                disabled={!available || disabled}
                onClick={() => setSelectedDay(cell.key)}
                aria-pressed={isSelected}
                aria-label={`${humanDate(cell.key, {
                  weekday: "long",
                  month: "long",
                  day: "numeric",
                })}${isToday ? ", today" : ""}, ${
                  available ? "available" : "no times available"
                }`}
                className={clsx(
                  "flex h-10 items-center justify-center rounded-full text-sm transition-colors",
                  FOCUS_BASE,
                  FOCUS_RING[pillar],
                  isSelected && clsx(tk.bg, tk.textOn, "font-semibold"),
                  !isSelected &&
                    available &&
                    "font-medium text-app-text hover:bg-app-hover",
                  !available &&
                    clsx(
                      "cursor-default",
                      isPast
                        ? "text-app-text-muted/40"
                        : "text-app-text-muted/60",
                    ),
                  !isSelected && isToday && clsx("ring-1 ring-inset", tk.ring),
                )}
              >
                {cell.day}
              </button>
            );
          })}
        </div>
      </div>

      {/* Slots region */}
      <div className="min-h-[120px]">
        {error ? (
          <ErrorState
            message={error}
            onRetry={() => setReloadKey((k) => k + 1)}
          />
        ) : loading ? (
          <div
            className="space-y-2"
            aria-label="Loading times"
            aria-busy="true"
          >
            <div className="h-3 w-20 animate-pulse rounded bg-app-surface-muted" />
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
              {Array.from({ length: 6 }).map((_, i) => (
                <div
                  key={i}
                  className="h-10 animate-pulse rounded-lg bg-app-surface-muted"
                />
              ))}
            </div>
          </div>
        ) : !monthHasSlots ? (
          <div className="rounded-xl border border-app-border bg-app-surface px-4 py-8 text-center">
            <p className="text-sm font-medium text-app-text">
              No open times in {MONTH_NAMES[cursor.month]}
            </p>
            <button
              type="button"
              onClick={() => goMonth(1)}
              className={clsx(
                "mt-2 text-sm font-medium hover:underline",
                tk.text,
              )}
            >
              See {MONTH_NAMES[(cursor.month + 1) % 12]}
            </button>
          </div>
        ) : daySlots.length === 0 ? (
          <div className="rounded-xl border border-app-border bg-app-surface px-4 py-8 text-center">
            <p className="text-sm font-medium text-app-text">
              No times left this day
            </p>
            <button
              type="button"
              onClick={jumpToNextAvailable}
              className={clsx(
                "mt-2 text-sm font-medium hover:underline",
                tk.text,
              )}
            >
              See next available
            </button>
          </div>
        ) : (
          <div className="space-y-4">
            <SlotGroup
              title="Morning"
              slots={morning}
              onPick={onPick}
              selected={selected}
              disabled={disabled}
              tk={tk}
              pillar={pillar}
            />
            <SlotGroup
              title="Afternoon"
              slots={afternoon}
              onPick={onPick}
              selected={selected}
              disabled={disabled}
              tk={tk}
              pillar={pillar}
            />
          </div>
        )}
      </div>

      <TimezoneSelector
        open={tzOpen}
        onClose={() => setTzOpen(false)}
        value={tz}
        onSelect={handleTz}
        pillar={pillar}
      />
    </div>
  );
}
