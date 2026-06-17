"use client";

// E7 — Post-Meeting Follow-up. Opened on a past booking. Pick an outcome to
// start from a plainspoken template, edit the message, and send it to the
// invitee via POST /bookings/:id/nudge. (There's no "private note" persistence
// endpoint, so the sheet stays focused on the message it can actually deliver.)

import { useState } from "react";
import { Link2, RotateCw, Send } from "lucide-react";
import * as api from "@pantopus/api";
import type { SchedulingOwnerRef } from "@pantopus/types";
import BottomSheet from "@/components/ui/BottomSheet";
import { toast } from "@/components/ui/toast-store";
import { decodeError } from "@/components/scheduling/decodeError";
import type { Pillar } from "@/components/scheduling/pillarTokens";
import { FilterChip, InlineError, SectionOverline, TextArea } from "./ui";
import {
  FOLLOWUP_OUTCOMES,
  type FollowUpOutcome,
  followUpTemplate,
  isOverLimit,
} from "./messageTemplates";

export interface FollowUpTarget {
  id: string;
  title: string;
  subtitle?: string;
  inviteeName?: string | null;
}

export default function FollowUpSheet({
  open,
  onClose,
  booking,
  owner,
  pillar = "personal",
  onSent,
}: {
  open: boolean;
  onClose: () => void;
  booking: FollowUpTarget | null;
  owner: SchedulingOwnerRef;
  pillar?: Pillar;
  onSent?: () => void;
}) {
  const [outcome, setOutcome] = useState<FollowUpOutcome | null>(null);
  const [text, setText] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  void pillar;
  if (!open || !booking) return null;

  const pickOutcome = (o: FollowUpOutcome) => {
    setOutcome(o);
    // Only overwrite when the message is empty or still a template.
    const isTemplate = FOLLOWUP_OUTCOMES.some(
      (x) => followUpTemplate(x.id, booking.inviteeName) === text,
    );
    if (!text.trim() || isTemplate) {
      setText(followUpTemplate(o, booking.inviteeName));
    }
  };

  const over = isOverLimit(text);
  const sendable = text.trim().length > 0 && !over;

  const submit = async () => {
    setSubmitting(true);
    setError(null);
    try {
      await api.scheduling.nudgeBooking(booking.id, text.trim(), owner);
      toast.success("Follow-up sent.");
      onSent?.();
      onClose();
      setOutcome(null);
      setText("");
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
          {error ? (
            <RotateCw className="h-4 w-4" aria-hidden />
          ) : (
            <Send className="h-4 w-4" aria-hidden />
          )}
          {submitting ? "Sending…" : error ? "Try again" : "Send follow-up"}
        </button>
      }
    >
      <div className="px-1">
        <div className="mb-4">
          <h3 className="text-base font-bold text-app-text">Follow up</h3>
          {booking.subtitle && (
            <p className="mt-1 text-xs text-app-text-muted">
              {booking.subtitle}
            </p>
          )}
        </div>

        <div className="mb-4">
          <SectionOverline className="mb-2">Outcome</SectionOverline>
          <div className="flex flex-wrap gap-2">
            {FOLLOWUP_OUTCOMES.map((o) => (
              <FilterChip
                key={o.id}
                label={o.label}
                tone="neutral"
                active={outcome === o.id}
                onClick={() => pickOutcome(o.id)}
              />
            ))}
          </div>
        </div>

        <div className="mb-3">
          <SectionOverline className="mb-2">
            Message to {booking.inviteeName?.split(/\s+/)[0] ?? "invitee"}
          </SectionOverline>
          <TextArea
            value={text}
            invalid={over}
            onChange={(e) => setText(e.target.value)}
            placeholder="Write a message, or pick an outcome above to start from a template."
            aria-label="Follow-up message"
          />
          <button
            type="button"
            onClick={() =>
              setText((t) =>
                t.includes("rebook")
                  ? t
                  : `${t}${t ? "\n\n" : ""}Here's a link to grab another time.`,
              )
            }
            className="mt-2 inline-flex h-7 items-center gap-1.5 rounded-full border border-app-border bg-app-surface px-3 text-[11px] font-semibold text-primary-600"
          >
            <Link2 className="h-3 w-3" aria-hidden />
            Add a rebook link
          </button>
        </div>

        {error && <InlineError message={error} />}
      </div>
    </BottomSheet>
  );
}
