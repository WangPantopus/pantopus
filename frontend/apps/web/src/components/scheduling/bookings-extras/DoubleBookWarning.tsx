"use client";

// E10 — Double-Book Warning. Surfaced when a manual/on-behalf create returns a
// 409 SLOT_CONFLICT. Per the wiring contract it is never a dead end: it leads
// with a calm warning and the nearest open times (the W0 SlotConflictAlternatives
// presenter) for a one-tap re-pick, plus "Pick another time" to reopen the
// picker. (The backend has no force-book, so "book anyway" can't be honored —
// re-picking an open time is the safe, real path.)

import { CalendarClock } from "lucide-react";
import type { BookingSlot, SlotConflict } from "@pantopus/types";
import BottomSheet from "@/components/ui/BottomSheet";
import SlotConflictAlternatives from "@/components/scheduling/SlotConflictAlternatives";
import type { Pillar } from "@/components/scheduling/pillarTokens";
import { IconDisc } from "./ui";

export default function DoubleBookWarning({
  open,
  onClose,
  conflict,
  pillar = "personal",
  onPick,
  onPickAnother,
  loadingAlternatives = false,
}: {
  open: boolean;
  onClose: () => void;
  conflict: SlotConflict | null;
  pillar?: Pillar;
  onPick: (slot: BookingSlot) => void;
  onPickAnother?: () => void;
  loadingAlternatives?: boolean;
}) {
  if (!open || !conflict) return null;

  return (
    <BottomSheet
      open={open}
      onClose={onClose}
      footer={
        <button
          type="button"
          onClick={onClose}
          className="w-full rounded-lg border border-app-border bg-app-surface px-4 py-2.5 text-sm font-semibold text-app-text-strong transition hover:bg-app-hover"
        >
          Cancel
        </button>
      }
    >
      <div className="px-1">
        <div className="mb-3 flex justify-center">
          <IconDisc icon={CalendarClock} tone="warning" />
        </div>
        <h3 className="text-center text-base font-bold text-app-text">
          This time overlaps
        </h3>
        <p className="mx-auto mt-2 max-w-xs text-center text-sm leading-relaxed text-app-text-secondary">
          {conflict.message ||
            "Another booking already holds this slot. Pick one of the nearest open times below."}
        </p>

        <div className="mt-4">
          <SlotConflictAlternatives
            conflict={conflict}
            pillar={pillar}
            loading={loadingAlternatives}
            onPick={onPick}
            onPickAnother={onPickAnother}
          />
        </div>
      </div>
    </BottomSheet>
  );
}
