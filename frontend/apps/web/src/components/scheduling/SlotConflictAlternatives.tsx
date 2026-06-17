"use client";

// The 409-conflict presenter — the most important scheduling error, designed
// to never dead-end. Leads with a calm amber block ("That time was just
// taken"), then re-fetched nearest-open slot rows for one-tap re-pick, plus a
// "Pick another time" ghost. When fully booked (no alternatives), offers a
// waitlist CTA. A re-fetching state shows shimmer rows.

import { AlertTriangle, ChevronRight } from "lucide-react";
import clsx from "clsx";
import type { BookingSlot, SlotConflict } from "@pantopus/types";
import { ShimmerLine } from "@/components/ui/Shimmer";
import { pillarTokens, type Pillar } from "./pillarTokens";

// H14 a11y: pillar-tinted keyboard focus ring (focus-visible → no visual change
// for mouse users). Literal classes so Tailwind's JIT picks them up.
const FOCUS_RING: Record<Pillar, string> = {
  personal: "focus-visible:ring-app-personal",
  home: "focus-visible:ring-app-home",
  business: "focus-visible:ring-app-business",
};
const FOCUS_BASE =
  "focus:outline-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-1";

function formatSlot(slot: BookingSlot): { time: string; date: string } {
  const d = new Date(slot.startLocal || slot.start);
  if (Number.isNaN(d.getTime())) return { time: slot.start, date: "" };
  return {
    time: d.toLocaleTimeString("en-US", { hour: "numeric", minute: "2-digit" }),
    date: d.toLocaleDateString("en-US", {
      weekday: "short",
      month: "short",
      day: "numeric",
    }),
  };
}

interface SlotConflictAlternativesProps {
  conflict: SlotConflict;
  onPick: (slot: BookingSlot) => void;
  onPickAnother?: () => void;
  onJoinWaitlist?: () => void;
  /** True while the nearest-open times are being re-fetched. */
  loading?: boolean;
  pillar?: Pillar;
  className?: string;
}

export default function SlotConflictAlternatives({
  conflict,
  onPick,
  onPickAnother,
  onJoinWaitlist,
  loading = false,
  pillar = "personal",
  className,
}: SlotConflictAlternativesProps) {
  const tk = pillarTokens(pillar);
  const alternatives = conflict.alternatives ?? [];

  return (
    <div
      className={clsx(
        "rounded-2xl border border-app-warning/40 bg-app-warning-bg/60 p-4",
        className,
      )}
    >
      <div className="flex items-start gap-3" role="alert">
        <span className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-app-warning-bg">
          <AlertTriangle className="h-4 w-4 text-app-warning" aria-hidden />
        </span>
        <div className="min-w-0">
          <p className="text-sm font-semibold text-app-text-strong">
            That time was just taken
          </p>
          <p className="mt-0.5 text-xs text-app-text-muted">
            {conflict.message ||
              "Your details are saved — pick one of the nearest open times below."}
          </p>
        </div>
      </div>

      <div className="mt-4 space-y-2">
        {loading ? (
          <>
            {[0, 1, 2].map((i) => (
              <div
                key={i}
                className="flex items-center justify-between rounded-xl border border-app-border bg-app-surface px-3 py-3"
              >
                <ShimmerLine width="w-40" />
                <ShimmerLine width="w-5" />
              </div>
            ))}
            <p className="pt-1 text-center text-xs text-app-text-muted">
              Checking live availability…
            </p>
          </>
        ) : alternatives.length > 0 ? (
          alternatives.map((slot) => {
            const { time, date } = formatSlot(slot);
            return (
              <button
                key={`${slot.start}-${slot.end}`}
                type="button"
                onClick={() => onPick(slot)}
                aria-label={`Book ${time}${date ? `, ${date}` : ""}`}
                className={clsx(
                  "flex w-full items-center justify-between gap-3 rounded-xl border border-app-border bg-app-surface px-3 py-3 text-left hover:bg-app-hover",
                  FOCUS_BASE,
                  FOCUS_RING[pillar],
                )}
              >
                <span>
                  <span className="block text-sm font-semibold text-app-text">
                    {time}
                  </span>
                  {date && (
                    <span className="block text-xs text-app-text-muted">
                      {date}
                    </span>
                  )}
                </span>
                <ChevronRight
                  className={clsx("h-4 w-4 shrink-0", tk.text)}
                  aria-hidden
                />
              </button>
            );
          })
        ) : (
          <div className="rounded-xl border border-dashed border-app-border bg-app-surface px-4 py-6 text-center">
            <p className="text-sm font-medium text-app-text">
              This day is fully booked
            </p>
            <p className="mt-1 text-xs text-app-text-muted">
              Join the waitlist and we’ll let you know when a spot opens.
            </p>
            {onJoinWaitlist && (
              <button
                type="button"
                onClick={onJoinWaitlist}
                className={clsx(
                  "mt-3 rounded-lg px-4 py-2 text-sm font-semibold",
                  tk.bg,
                  tk.textOn,
                )}
              >
                Join the waitlist
              </button>
            )}
          </div>
        )}
      </div>

      {onPickAnother && (
        <button
          type="button"
          onClick={onPickAnother}
          className="mt-3 w-full rounded-lg border border-app-border bg-app-surface px-4 py-2 text-sm font-medium text-app-text hover:bg-app-hover"
        >
          Pick another time
        </button>
      )}
    </div>
  );
}
