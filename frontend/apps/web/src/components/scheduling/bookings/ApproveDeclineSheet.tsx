"use client";

// W8 · E3 — Approve / Decline request sheet (local, no global route). Renders
// for approval-required bookings. Review mode shows the requester + slot +
// intake preview with Approve (primary) / Decline (ghost). Decline expands to
// reason chips + note. Errors surface inline and re-enable the actions.

import { useEffect, useState } from "react";
import { AlertCircle, Calendar, Check, ClipboardList, X } from "lucide-react";
import * as api from "@pantopus/api";
import type { Booking, SchedulingOwnerRef } from "@pantopus/types";
import BottomSheet from "@/components/ui/BottomSheet";
import { decodeError } from "@/components/scheduling/decodeError";
import {
  pillarTokens,
  type Pillar,
} from "@/components/scheduling/pillarTokens";
import { Avatar, Banner, ReasonChips } from "./primitives";
import { durationLabel, formatRange, inviteeDisplay, viewerTz } from "./format";

const DECLINE_REASONS = [
  "Time doesn’t work",
  "Fully booked",
  "Not a fit",
  "Other",
];

export default function ApproveDeclineSheet({
  open,
  onClose,
  booking,
  eventName,
  owner,
  pillar = "personal",
  initialMode = "review",
  onDone,
}: {
  open: boolean;
  onClose: () => void;
  booking: Booking;
  eventName: string;
  owner: SchedulingOwnerRef;
  pillar?: Pillar;
  initialMode?: "review" | "decline";
  onDone: () => void;
}) {
  const tk = pillarTokens(pillar);
  const tz = viewerTz();
  const [mode, setMode] = useState<"review" | "decline">(initialMode);
  const [reason, setReason] = useState<string | null>(null);
  const [note, setNote] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      setMode(initialMode);
      setReason(null);
      setNote("");
      setError(null);
      setSubmitting(false);
    }
  }, [open, initialMode]);

  const intakeCount = booking.intake_answers
    ? Object.keys(booking.intake_answers).length
    : 0;

  const approve = async () => {
    setSubmitting(true);
    setError(null);
    try {
      await api.scheduling.approveBooking(booking.id, owner);
      onDone();
      onClose();
    } catch (err) {
      setError(decodeError(err).message);
      setSubmitting(false);
    }
  };

  const decline = async () => {
    setSubmitting(true);
    setError(null);
    const combined = [reason, note.trim()].filter(Boolean).join(" — ");
    try {
      await api.scheduling.declineBooking(
        booking.id,
        combined || undefined,
        owner,
      );
      onDone();
      onClose();
    } catch (err) {
      setError(decodeError(err).message);
      setSubmitting(false);
    }
  };

  return (
    <BottomSheet
      open={open}
      onClose={() => !submitting && onClose()}
      title={mode === "decline" ? "Decline request" : "Review request"}
    >
      <div className="space-y-4">
        {/* Requester */}
        <div className="flex items-center gap-3 rounded-2xl border border-app-border bg-app-surface-raised p-3">
          <Avatar pillar={pillar} name={booking.invitee_name} />
          <div className="min-w-0">
            <div className="truncate text-sm font-bold text-app-text">
              {inviteeDisplay(booking.invitee_name)}
            </div>
            <div className="truncate text-xs text-app-text-muted">
              {booking.invitee_email || "Verified requester"}
            </div>
          </div>
        </div>

        {/* Slot line */}
        <div className="flex items-center gap-3 rounded-2xl border border-app-border bg-app-surface p-3">
          <span
            className={`flex h-9 w-9 items-center justify-center rounded-lg ${tk.bgSoft}`}
          >
            <Calendar className={`h-[18px] w-[18px] ${tk.text}`} aria-hidden />
          </span>
          <div className="min-w-0">
            <div className="text-[13px] font-bold text-app-text">
              {formatRange(booking.start_at, booking.end_at, tz)}
            </div>
            <div className="text-xs text-app-text-muted">
              {eventName}
              {durationLabel(booking.start_at, booking.end_at) &&
                ` · ${durationLabel(booking.start_at, booking.end_at)}`}
            </div>
          </div>
        </div>

        {mode === "review" ? (
          <>
            {intakeCount > 0 && (
              <div className="flex items-center gap-2.5 rounded-xl border border-app-border bg-app-surface px-3 py-2.5">
                <ClipboardList
                  className="h-4 w-4 text-app-text-secondary"
                  aria-hidden
                />
                <span className="flex-1 text-[13px] font-semibold text-app-text">
                  Intake answers
                </span>
                <span className="text-xs text-app-text-muted">
                  {intakeCount} {intakeCount === 1 ? "answer" : "answers"}
                </span>
              </div>
            )}
            <textarea
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="Add a note (optional)"
              rows={2}
              className="w-full resize-none rounded-lg border border-app-border bg-app-surface-sunken px-3 py-2.5 text-[13px] text-app-text placeholder:text-app-text-muted focus:border-app-border-strong focus:outline-none"
            />
            {error && (
              <Banner tone="error" icon={AlertCircle}>
                {error}
              </Banner>
            )}
            <div className="space-y-2.5">
              <button
                type="button"
                disabled={submitting}
                onClick={approve}
                className={`inline-flex h-12 w-full items-center justify-center gap-2 rounded-xl text-sm font-bold transition disabled:opacity-70 ${tk.bg} ${tk.textOn}`}
              >
                {submitting ? (
                  <Spinner />
                ) : (
                  <Check className="h-[18px] w-[18px]" aria-hidden />
                )}
                {submitting ? "Approving" : "Approve"}
              </button>
              <button
                type="button"
                disabled={submitting}
                onClick={() => setMode("decline")}
                className="h-11 w-full rounded-xl text-sm font-bold text-app-error transition hover:bg-app-error-bg disabled:opacity-50"
              >
                Decline
              </button>
            </div>
          </>
        ) : (
          <>
            <div>
              <p className="mb-2 text-[11px] font-bold uppercase tracking-wider text-app-text-muted">
                Reason
              </p>
              <ReasonChips
                options={DECLINE_REASONS}
                value={reason}
                onChange={setReason}
                tone="error"
              />
            </div>
            <textarea
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="Add a note for the requester (optional)"
              rows={2}
              className="w-full resize-none rounded-lg border border-app-border bg-app-surface-sunken px-3 py-2.5 text-[13px] text-app-text placeholder:text-app-text-muted focus:border-app-border-strong focus:outline-none"
            />
            {error && (
              <Banner tone="error" icon={AlertCircle}>
                {error}
              </Banner>
            )}
            <div className="space-y-2.5">
              <button
                type="button"
                disabled={submitting}
                onClick={decline}
                className="inline-flex h-12 w-full items-center justify-center gap-2 rounded-xl bg-app-error text-sm font-bold text-white transition disabled:opacity-70"
              >
                {submitting ? (
                  <Spinner />
                ) : (
                  <X className="h-[18px] w-[18px]" aria-hidden />
                )}
                {submitting ? "Declining" : "Decline request"}
              </button>
              <button
                type="button"
                disabled={submitting}
                onClick={() => setMode("review")}
                className="h-11 w-full rounded-xl border border-app-border text-sm font-bold text-app-text-secondary transition hover:bg-app-hover disabled:opacity-50"
              >
                Back
              </button>
            </div>
          </>
        )}
      </div>
    </BottomSheet>
  );
}

function Spinner() {
  return (
    <span
      className="h-4 w-4 animate-spin rounded-full border-2 border-white/40 border-t-white"
      aria-hidden
    />
  );
}
