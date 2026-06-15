"use client";

// E11 — Send a Nudge. A message composer opened from a booking row ("Nudge") or
// a group roster ("Message all"). Sends a reminder via POST /bookings/:id/nudge
// (optional message <= 1000 chars; we cap the composer at 280 for a short
// reminder). Group mode shows audience chips that size the recipient count and
// disable the send when nobody is in the chosen group.

import { useMemo, useState } from "react";
import { Bell, FileText, Mail, Send } from "lucide-react";
import * as api from "@pantopus/api";
import type { BookingAttendee, SchedulingOwnerRef } from "@pantopus/types";
import BottomSheet from "@/components/ui/BottomSheet";
import { toast } from "@/components/ui/toast-store";
import { decodeError } from "@/components/scheduling/decodeError";
import { FilterChip, InlineError, SectionOverline, TextArea } from "./ui";
import {
  NUDGE_AUDIENCES,
  NUDGE_LIMIT,
  type NudgeAudience,
  audienceCount,
  canSendNudge,
  isOverLimit,
} from "./messageTemplates";

export interface NudgeTarget {
  id: string;
  title: string;
  subtitle?: string;
  inviteeName?: string | null;
  /** Provided for group events → enables audience chips + counts. */
  attendees?: BookingAttendee[];
}

export default function NudgeSheet({
  open,
  onClose,
  booking,
  owner,
  onSent,
}: {
  open: boolean;
  onClose: () => void;
  booking: NudgeTarget | null;
  owner: SchedulingOwnerRef;
  onSent?: () => void;
}) {
  const [text, setText] = useState("");
  const [audience, setAudience] = useState<NudgeAudience>("all");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const attendees = booking?.attendees;
  const isGroup = (attendees?.length ?? 0) > 1;

  const recipientCount = useMemo(() => {
    if (!booking) return 0;
    if (isGroup && attendees) return audienceCount(audience, attendees);
    return booking.inviteeName ? 1 : 1; // single invitee
  }, [booking, isGroup, attendees, audience]);

  if (!open || !booking) return null;

  const over = isOverLimit(text);
  const sendable = canSendNudge(text, recipientCount);
  const ctaLabel = isGroup
    ? `Send to ${recipientCount}`
    : booking.inviteeName
      ? `Send to ${booking.inviteeName.split(/\s+/)[0]}`
      : "Send reminder";

  const reset = () => {
    setText("");
    setAudience("all");
    setError(null);
  };

  const submit = async () => {
    setSubmitting(true);
    setError(null);
    try {
      await api.scheduling.nudgeBooking(booking.id, text.trim(), owner);
      toast.success(
        isGroup ? `Reminder sent to ${recipientCount}.` : "Reminder sent.",
      );
      onSent?.();
      reset();
      onClose();
    } catch (err) {
      setError(decodeError(err).message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <BottomSheet
      open={open}
      onClose={() => {
        if (!submitting) onClose();
      }}
      footer={
        <button
          type="button"
          onClick={submit}
          disabled={!sendable || submitting}
          className="flex w-full items-center justify-center gap-2 rounded-lg bg-primary-600 px-4 py-3 text-sm font-semibold text-white transition hover:bg-primary-700 disabled:cursor-not-allowed disabled:bg-app-surface-sunken disabled:text-app-text-muted"
        >
          <Send className="h-4 w-4" aria-hidden />
          {submitting ? "Sending…" : ctaLabel}
        </button>
      }
    >
      <div className="px-1">
        <div className="mb-4">
          <h3 className="text-base font-bold text-app-text">
            {isGroup ? "Message attendees" : "Send a nudge"}
          </h3>
          {booking.subtitle && (
            <p className="mt-1 text-xs text-app-text-muted">
              {booking.subtitle}
            </p>
          )}
        </div>

        <div className="relative mb-4">
          <button
            type="button"
            onClick={() =>
              setText(
                "Quick reminder about your upcoming booking — see you then.",
              )
            }
            className="mb-2 inline-flex h-7 items-center gap-1.5 rounded-full border border-app-border bg-app-surface px-3 text-[11px] font-semibold text-primary-600"
          >
            <FileText className="h-3 w-3" aria-hidden />
            Use a template
          </button>
          <TextArea
            value={text}
            invalid={over}
            maxLength={NUDGE_LIMIT * 2}
            onChange={(e) => setText(e.target.value)}
            placeholder="Write a short reminder…"
            aria-label="Reminder message"
          />
          <span
            className={`pointer-events-none absolute bottom-2.5 right-3 text-[10px] font-semibold tabular-nums ${
              over ? "text-app-error" : "text-app-text-muted"
            }`}
          >
            {text.length}/{NUDGE_LIMIT}
          </span>
        </div>
        {over && (
          <p className="mb-4 text-xs font-medium text-app-error">
            Shorten your message — it’s over {NUDGE_LIMIT} characters.
          </p>
        )}

        {isGroup && (
          <div className="mb-4">
            <SectionOverline className="mb-2">Audience</SectionOverline>
            <div className="flex flex-wrap gap-2">
              {NUDGE_AUDIENCES.map((a) => (
                <FilterChip
                  key={a.id}
                  label={a.label}
                  count={audienceCount(a.id, attendees ?? [])}
                  tone="neutral"
                  active={audience === a.id}
                  onClick={() => setAudience(a.id)}
                />
              ))}
            </div>
            {recipientCount === 0 && (
              <p className="mt-2 rounded-lg bg-app-surface-sunken px-3 py-2 text-xs font-medium text-app-text-muted">
                No one to message in this group.
              </p>
            )}
          </div>
        )}

        <div className="space-y-2">
          {[
            { label: "Push", icon: Bell },
            { label: "Email", icon: Mail },
          ].map(({ label, icon: Icon }) => (
            <div
              key={label}
              className="flex items-center gap-3 rounded-xl border border-app-border bg-app-surface px-3 py-2.5"
            >
              <Icon className="h-4 w-4 text-app-text-secondary" aria-hidden />
              <span className="flex-1 text-sm font-medium text-app-text">
                {label}
              </span>
              <span className="text-[11px] font-semibold uppercase text-app-text-muted">
                Auto
              </span>
            </div>
          ))}
        </div>

        {error && (
          <div className="mt-4">
            <InlineError message={error} />
          </div>
        )}

        <p className="mt-3 text-[11px] text-app-text-muted">
          Sent through the invitee’s preferred channel (push or email).
        </p>
      </div>
    </BottomSheet>
  );
}
