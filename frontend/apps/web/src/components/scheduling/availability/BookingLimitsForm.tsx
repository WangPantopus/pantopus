"use client";

// B7 — Booking limits & notice rules (THIN surface). These caps persist on the
// EVENT TYPE, not the schedule — the backend has no schedule-level limits
// endpoint (see reference/calendarly-backend-api.md). So W3 keeps this a
// read-only summary of the platform defaults with a clear hand-off to the event
// type editor (W2), where the real per-type fields live. Faithful to the
// design's row idiom, but explicitly non-persisting here.

import Link from "next/link";
import { ArrowUpRight, Info } from "lucide-react";
import { ValueRow } from "./primitives";

const DEFAULTS: Array<{ label: string; value: string; caption: string }> = [
  {
    label: "Minimum notice",
    value: "None",
    caption: "Can't be booked inside this window.",
  },
  {
    label: "Book up to",
    value: "60 days",
    caption: "How far ahead people can book.",
  },
  {
    label: "Max per day",
    value: "No limit",
    caption: "Most bookings you'll take in a day.",
  },
  {
    label: "Per-person limit",
    value: "No limit",
    caption: "How many one person can hold at once.",
  },
  {
    label: "Start times",
    value: "Every 15 min",
    caption: "Where bookings can start within the hour.",
  },
];

export default function BookingLimitsForm() {
  return (
    <div className="space-y-3">
      <div className="flex items-start gap-3 rounded-2xl border border-app-info-light bg-app-info-bg p-3.5">
        <Info className="mt-0.5 h-4 w-4 shrink-0 text-app-info" aria-hidden />
        <div className="min-w-0 flex-1">
          <p className="text-[12.5px] font-bold text-app-text">
            Limits are set per event type
          </p>
          <p className="mt-1 text-[11.5px] leading-relaxed text-app-text-secondary">
            Notice, booking window, daily caps and start-time granularity apply
            to individual event types. Open an event type to fine-tune these.
            The values below are the defaults applied to new event types.
          </p>
          <Link
            href="/app/scheduling/event-types"
            className="mt-2.5 inline-flex items-center gap-1.5 text-[12px] font-bold text-app-personal hover:underline"
          >
            <ArrowUpRight className="h-3.5 w-3.5" aria-hidden /> Manage on event
            types
          </Link>
        </div>
      </div>

      {DEFAULTS.map((d) => (
        <ValueRow
          key={d.label}
          label={d.label}
          value={d.value}
          caption={d.caption}
        />
      ))}
    </div>
  );
}
