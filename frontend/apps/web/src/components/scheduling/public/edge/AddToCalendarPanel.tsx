"use client";

// D8 — Add to Calendar. Wraps the W0 AddToCalendar provider rows with the
// event-recap chip + caption from the design. Render inline, or pass to a
// <BottomSheet> to present as a sheet. Public manage surfaces pass the .ics
// endpoint (publicBooking.getIcsUrl(token)); host surfaces let it build inline.

import { CalendarPlus } from "lucide-react";
import clsx from "clsx";
import {
  AddToCalendar,
  type CalendarEventInput,
} from "@/components/scheduling";
import { formatRange, tzAbbrev } from "./edgeUtils";

export default function AddToCalendarPanel({
  event,
  tz,
  icsUrl,
  className,
}: {
  event: CalendarEventInput;
  tz?: string;
  /** Public manage surfaces pass the token .ics endpoint. */
  icsUrl?: string;
  className?: string;
}) {
  const recap = [
    event.title,
    formatRange(event.start, event.end, tz),
    tz ? tzAbbrev(tz) : null,
  ]
    .filter(Boolean)
    .join(" · ");

  return (
    <div className={clsx("space-y-3", className)}>
      <div className="flex items-center gap-2.5 rounded-xl border border-app-border bg-app-surface-muted px-3 py-2.5">
        <CalendarPlus
          className="h-4 w-4 shrink-0 text-app-text-secondary"
          aria-hidden
        />
        <p className="min-w-0 truncate text-xs font-medium text-app-text">
          {recap}
        </p>
      </div>
      <div className="rounded-xl border border-app-border bg-app-surface px-2">
        <AddToCalendar event={event} icsUrl={icsUrl} />
      </div>
      <p className="px-1 text-xs text-app-text-muted">
        We’ll add the event with the join link and a reminder.
      </p>
    </div>
  );
}
