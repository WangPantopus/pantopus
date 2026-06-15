// Status pills for bookings + page/link states, themed with semantic + neutral
// tokens. Used across host inbox, manage, and detail surfaces.

import clsx from "clsx";
import type { BookingStatus } from "@pantopus/types";

export type StatusPillValue =
  | BookingStatus
  | "paused"
  | "secret"
  | "expired"
  | "unavailable";

const CONFIG: Record<StatusPillValue, { label: string; cls: string }> = {
  confirmed: { label: "Confirmed", cls: "bg-app-success-bg text-app-success" },
  pending: { label: "Pending", cls: "bg-app-warning-bg text-app-warning" },
  completed: { label: "Completed", cls: "bg-app-info-bg text-app-info" },
  rescheduled: { label: "Rescheduled", cls: "bg-app-info-bg text-app-info" },
  declined: { label: "Declined", cls: "bg-app-error-bg text-app-error" },
  cancelled: { label: "Cancelled", cls: "bg-app-error-bg text-app-error" },
  no_show: { label: "No-show", cls: "bg-app-error-bg text-app-error" },
  paused: { label: "Paused", cls: "bg-app-surface-muted text-app-text-muted" },
  secret: { label: "Private", cls: "bg-app-surface-muted text-app-text-muted" },
  expired: {
    label: "Expired",
    cls: "bg-app-surface-muted text-app-text-muted",
  },
  unavailable: {
    label: "Unavailable",
    cls: "bg-app-surface-muted text-app-text-muted",
  },
};

const FALLBACK = { label: "", cls: "bg-app-surface-muted text-app-text-muted" };

export default function BookingStatusPill({
  status,
  className,
}: {
  status: StatusPillValue | string;
  className?: string;
}) {
  const cfg = CONFIG[status as StatusPillValue] ?? {
    ...FALLBACK,
    label: String(status),
  };
  return (
    <span
      className={clsx(
        "inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold",
        cfg.cls,
        className,
      )}
    >
      <span
        className="h-1.5 w-1.5 rounded-full bg-current opacity-70"
        aria-hidden
      />
      {cfg.label}
    </span>
  );
}
