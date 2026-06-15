// C5 — public booking footer. "Powered by Pantopus" wordmark with the calendar
// glyph, mirroring the design. Presentational + server-safe.

import Link from "next/link";
import { CalendarClock } from "lucide-react";

export default function PublicBookingFooter() {
  return (
    <footer className="flex flex-col items-center gap-2.5 px-4 pb-8 pt-6">
      <Link
        href="/"
        className="inline-flex items-center gap-1.5 text-app-text-muted hover:text-app-text-secondary"
      >
        <CalendarClock className="h-3 w-3" aria-hidden />
        <span className="text-[11px] font-semibold tracking-wide">
          Powered by Pantopus
        </span>
      </Link>
    </footer>
  );
}
