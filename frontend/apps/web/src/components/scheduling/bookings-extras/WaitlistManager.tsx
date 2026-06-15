"use client";

// E13 — Waitlist Management (host). The waiting list for a chosen event type
// (GET /event-types/:id/waitlist), each row promotable (POST /waitlist/:id/promote,
// which notifies the invitee). Promote is confirmed before it fires.

import { ArrowUp, Users } from "lucide-react";
import clsx from "clsx";
import type { WaitlistEntry } from "@pantopus/types";
import type { Pillar } from "@/components/scheduling/pillarTokens";
import { pillarTokens } from "@/components/scheduling/pillarTokens";
import { Avatar, SectionOverline } from "./ui";
import { fmtDate } from "./format";

export default function WaitlistManager({
  pillar,
  eventTypeName,
  waitlist,
  promotingId,
  onPromote,
}: {
  pillar: Pillar;
  eventTypeName: string;
  waitlist: WaitlistEntry[];
  promotingId: string | null;
  onPromote: (entry: WaitlistEntry) => void;
}) {
  const tk = pillarTokens(pillar);
  const waiting = waitlist.filter((w) => w.status === "waiting");

  if (waiting.length === 0) {
    return (
      <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-app-border bg-app-surface px-6 py-14 text-center">
        <span
          className={clsx(
            "flex h-14 w-14 items-center justify-center rounded-full",
            tk.bgSoft,
            tk.text,
          )}
        >
          <Users className="h-6 w-6" aria-hidden />
        </span>
        <p className="text-sm font-semibold text-app-text">
          No one’s waiting for {eventTypeName}
        </p>
        <p className="max-w-xs text-xs text-app-text-muted">
          When this event type is fully booked, invitees can join the waitlist
          and they’ll appear here.
        </p>
      </div>
    );
  }

  return (
    <div>
      <SectionOverline className="mb-2">
        {waiting.length} waiting
      </SectionOverline>
      <ul className="space-y-2">
        {waiting.map((w, i) => {
          const busy = promotingId === w.id;
          return (
            <li
              key={w.id}
              className="rounded-xl border border-app-border bg-app-surface p-3 shadow-sm"
            >
              <div className="flex items-center gap-3">
                <Avatar name={w.invitee_name} pillar={pillar} size={36} />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-bold text-app-text">
                    {w.invitee_name || w.invitee_email || "Guest"}
                  </p>
                  <p className="truncate text-[11px] text-app-text-muted">
                    #{i + 1} · joined {fmtDate(w.created_at)}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => onPromote(w)}
                  disabled={busy}
                  className={clsx(
                    "inline-flex items-center gap-1.5 rounded-lg px-3 py-2 text-xs font-bold transition disabled:opacity-60",
                    tk.bgSoft,
                    tk.text,
                  )}
                >
                  <ArrowUp className="h-3.5 w-3.5" aria-hidden />
                  {busy ? "Promoting…" : "Promote"}
                </button>
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
