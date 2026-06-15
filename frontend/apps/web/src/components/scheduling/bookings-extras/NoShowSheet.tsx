"use client";

// E6 — Mark No-Show. A confirm modal opened from a booking row/detail. The
// no-show action is only offered once the booking's end time has passed; before
// then the primary is disabled with the reason (mirroring the backend's
// 409 NOT_APPLICABLE_YET). Marks the whole booking no_show via
// POST /bookings/:id/no-show (there is no per-attendee no-show endpoint, so
// group events are marked as a whole and we say so).

import { useState } from "react";
import { UserX } from "lucide-react";
import * as api from "@pantopus/api";
import type { Booking, SchedulingOwnerRef } from "@pantopus/types";
import BottomSheet from "@/components/ui/BottomSheet";
import { toast } from "@/components/ui/toast-store";
import { decodeError } from "@/components/scheduling/decodeError";
import type { Pillar } from "@/components/scheduling/pillarTokens";
import { IconDisc, InlineError } from "./ui";
import { canMarkNoShow, noShowBlockedReason } from "./noShow";
import { initials } from "./format";

export interface NoShowTarget {
  id: string;
  status: Booking["status"];
  start_at: string;
  end_at: string;
  invitee_name: string | null;
  /** > 1 ⇒ a group event (the whole booking is marked). */
  attendeeCount?: number;
}

export default function NoShowSheet({
  open,
  onClose,
  booking,
  owner,
  pillar,
  onDone,
}: {
  open: boolean;
  onClose: () => void;
  booking: NoShowTarget | null;
  owner: SchedulingOwnerRef;
  pillar: Pillar;
  onDone?: (updated: Booking) => void;
}) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!open || !booking) return null;

  const blocked = noShowBlockedReason(booking);
  const allowed = canMarkNoShow(booking);
  const isGroup = (booking.attendeeCount ?? 1) > 1;

  const submit = async () => {
    setSubmitting(true);
    setError(null);
    try {
      const res = await api.scheduling.noShowBooking(booking.id, owner);
      toast.success("Marked as no-show.");
      onDone?.(res.booking);
      onClose();
    } catch (err) {
      const d = decodeError(err);
      setError(
        d.kind === "error" && d.code === "NOT_APPLICABLE_YET"
          ? "You can mark a no-show after the booking's end time."
          : d.message,
      );
    } finally {
      setSubmitting(false);
    }
  };

  void pillar; // destructive action stays semantic-red regardless of pillar

  return (
    <BottomSheet
      open={open}
      onClose={() => {
        if (!submitting) onClose();
      }}
      footer={
        <div className="flex gap-3">
          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            className="flex-1 rounded-lg border border-app-border bg-app-surface px-4 py-2.5 text-sm font-semibold text-app-text-strong transition hover:bg-app-hover disabled:opacity-50"
          >
            Keep open
          </button>
          <button
            type="button"
            onClick={submit}
            disabled={submitting || !allowed}
            className="flex flex-1 items-center justify-center gap-2 rounded-lg bg-app-error px-4 py-2.5 text-sm font-semibold text-white transition hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {submitting ? (
              <svg
                className="h-4 w-4 animate-spin"
                viewBox="0 0 24 24"
                aria-hidden
              >
                <circle
                  className="opacity-25"
                  cx="12"
                  cy="12"
                  r="10"
                  stroke="currentColor"
                  strokeWidth="4"
                  fill="none"
                />
                <path
                  className="opacity-75"
                  fill="currentColor"
                  d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
                />
              </svg>
            ) : (
              "Mark no-show"
            )}
          </button>
        </div>
      }
    >
      <div className="px-1 pb-2 text-center">
        <div className="mb-3 flex justify-center">
          <IconDisc icon={UserX} tone="error" />
        </div>
        <h3 className="text-base font-bold text-app-text">Mark as no-show?</h3>
        <p className="mx-auto mt-2 max-w-xs text-sm leading-relaxed text-app-text-secondary">
          {isGroup
            ? `This closes the whole "${booking.invitee_name ?? "group"}" booking. You can still message attendees afterward.`
            : "This closes the booking. You can still message the invitee or send a rebook link afterward."}
        </p>

        {booking.invitee_name && (
          <div className="mx-auto mt-4 flex max-w-xs items-center gap-3 rounded-xl border border-app-border bg-app-surface px-3 py-2.5 text-left">
            <span className="flex h-8 w-8 items-center justify-center rounded-full bg-app-surface-sunken text-xs font-bold text-app-text-secondary">
              {initials(booking.invitee_name)}
            </span>
            <span className="min-w-0 text-sm font-semibold text-app-text">
              {booking.invitee_name}
            </span>
          </div>
        )}

        {blocked && (
          <p className="mx-auto mt-4 max-w-xs rounded-lg bg-app-warning-bg px-3 py-2 text-xs font-medium text-app-warning">
            {blocked}
          </p>
        )}

        {error && (
          <div className="mx-auto mt-4 max-w-xs text-left">
            <InlineError message={error} />
          </div>
        )}
      </div>
    </BottomSheet>
  );
}
