"use client";

// W8 · E5 — Cancel & refund sheet (local, destructive). Reason chips + note +
// notify. The refund affordance only shows behind webFeatureFlags.schedulingPaid
// when the booking was paid (else cancel-only). Surfaces the response's
// refund_issued, and the guarded states: ALREADY_CANCELLED (read-only),
// PAST_DEADLINE (policy-blocked), REFUND_FAILED (retry).

import { useEffect, useState } from "react";
import {
  AlertCircle,
  CircleSlash,
  Receipt,
  RotateCw,
  XCircle,
} from "lucide-react";
import * as api from "@pantopus/api";
import type { Booking, SchedulingOwnerRef } from "@pantopus/types";
import BottomSheet from "@/components/ui/BottomSheet";
import { toast } from "@/components/ui/toast-store";
import { decodeError } from "@/components/scheduling/decodeError";
import type { Pillar } from "@/components/scheduling/pillarTokens";
import { Banner, ReasonChips } from "./primitives";
import { formatWhen, inviteeDisplay, viewerTz } from "./format";

const CANCEL_REASONS = [
  "Changed plans",
  "Emergency",
  "Found someone else",
  "Other",
];

export default function CancelRefundSheet({
  open,
  onClose,
  booking,
  eventName,
  owner,
  paid,
  onDone,
}: {
  open: boolean;
  onClose: () => void;
  booking: Booking;
  eventName: string;
  owner: SchedulingOwnerRef;
  pillar?: Pillar;
  paid: boolean;
  onDone: () => void;
}) {
  const tz = viewerTz();
  const [reason, setReason] = useState<string | null>(null);
  const [note, setNote] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [refundFailed, setRefundFailed] = useState(false);
  const [alreadyCancelled, setAlreadyCancelled] = useState(false);

  const refundable = paid && !!booking.payment_id;

  useEffect(() => {
    if (open) {
      setReason(null);
      setNote("");
      setError(null);
      setRefundFailed(false);
      setAlreadyCancelled(false);
      setSubmitting(false);
    }
  }, [open]);

  const confirmLabel = refundable ? "Cancel & refund" : "Cancel booking";

  const submit = async () => {
    setSubmitting(true);
    setError(null);
    setRefundFailed(false);
    const combined =
      [reason === "Other" ? null : reason, note.trim()]
        .filter(Boolean)
        .join(" — ") ||
      reason ||
      undefined;
    try {
      const res = await api.scheduling.cancelBooking(
        booking.id,
        combined,
        owner,
      );
      toast.success(
        res.booking?.refund_issued
          ? "Booking cancelled · refund issued"
          : "Booking cancelled",
      );
      onDone();
      onClose();
    } catch (err) {
      const d = decodeError(err);
      if (d.kind === "error" && d.code === "ALREADY_CANCELLED") {
        setAlreadyCancelled(true);
      } else if (d.kind === "error" && d.code === "REFUND_FAILED") {
        setRefundFailed(true);
        setError(
          "The booking was cancelled but the refund couldn't be processed.",
        );
      } else if (d.kind === "error" && d.code === "PAST_DEADLINE") {
        setError(
          "This booking is past the cancellation cutoff — you can still message the invitee.",
        );
      } else {
        setError(d.message);
      }
      setSubmitting(false);
    }
  };

  return (
    <BottomSheet
      open={open}
      onClose={() => !submitting && onClose()}
      title="Cancel booking"
    >
      {alreadyCancelled ? (
        <div className="flex flex-col items-center gap-4 px-6 py-8 text-center">
          <span className="flex h-16 w-16 items-center justify-center rounded-full bg-app-surface-sunken text-app-text-muted">
            <CircleSlash className="h-7 w-7" aria-hidden />
          </span>
          <div>
            <h3 className="text-base font-bold text-app-text">
              Already cancelled
            </h3>
            <p className="mt-1.5 max-w-xs text-sm text-app-text-secondary">
              This booking has already been cancelled.
            </p>
          </div>
          <button
            type="button"
            onClick={() => {
              onDone();
              onClose();
            }}
            className="h-11 w-full rounded-xl border border-app-border text-sm font-bold text-app-text transition hover:bg-app-hover"
          >
            Done
          </button>
        </div>
      ) : (
        <div className="space-y-4">
          <div>
            <h3 className="text-base font-bold text-app-text">
              Cancel this booking?
            </h3>
            <p className="mt-1 text-xs text-app-text-muted">
              {eventName} · {inviteeDisplay(booking.invitee_name)} ·{" "}
              {formatWhen(booking.start_at, tz)}
            </p>
          </div>

          <div>
            <p className="mb-2 text-[11px] font-bold uppercase tracking-wider text-app-text-muted">
              Reason
            </p>
            <ReasonChips
              options={CANCEL_REASONS}
              value={reason}
              onChange={setReason}
              tone="error"
            />
          </div>

          <textarea
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder={
              reason === "Other"
                ? "Tell the invitee what happened"
                : "Note to the invitee (optional)"
            }
            rows={2}
            className="w-full resize-none rounded-lg border border-app-border bg-app-surface-sunken px-3 py-2.5 text-[13px] text-app-text placeholder:text-app-text-muted focus:border-app-border-strong focus:outline-none"
          />

          {refundable && (
            <div className="rounded-2xl border border-app-border bg-app-surface p-3">
              <div className="mb-1.5 flex items-center gap-1.5">
                <Receipt
                  className="h-3.5 w-3.5 text-app-text-muted"
                  aria-hidden
                />
                <span className="text-[11px] font-bold uppercase tracking-wider text-app-text-muted">
                  Refund
                </span>
              </div>
              <p className="text-xs text-app-text-secondary">
                This booking was paid. A refund is issued per the cancellation
                policy when you cancel.
              </p>
            </div>
          )}

          {error && (
            <Banner tone="error" icon={AlertCircle}>
              {error}
            </Banner>
          )}

          <button
            type="button"
            disabled={submitting}
            onClick={submit}
            className="inline-flex h-12 w-full items-center justify-center gap-2 rounded-xl bg-app-error text-sm font-bold text-white transition disabled:opacity-70"
          >
            {submitting ? (
              <span
                className="h-4 w-4 animate-spin rounded-full border-2 border-white/40 border-t-white"
                aria-hidden
              />
            ) : refundFailed ? (
              <RotateCw className="h-[18px] w-[18px]" aria-hidden />
            ) : (
              <XCircle className="h-[18px] w-[18px]" aria-hidden />
            )}
            {submitting ? "Cancelling" : refundFailed ? "Retry" : confirmLabel}
          </button>
        </div>
      )}
    </BottomSheet>
  );
}
