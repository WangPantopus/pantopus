"use client";

// D10 — Invitee reschedule via the manage token. Reads the booking + the
// backend-computed `actions`; if the policy forbids it (window closed / not
// online) it shows the policy-blocked card with a fallback (never a dead end).
// Otherwise it wraps the W0 SlotPicker (manageToken mode → fresh availability,
// current slot excluded), confirms via rescheduleByToken, surfaces 409
// alternatives, and lands on a confirmed state with Add-to-Calendar.

import { useEffect, useMemo, useState, type ReactNode } from "react";
import Link from "next/link";
import {
  ChevronLeft,
  ArrowDown,
  Calendar,
  CalendarClock,
  CalendarCheck,
  MessageCircle,
  CheckCircle2,
} from "lucide-react";
import clsx from "clsx";
import type {
  BookingManageView,
  BookingSlot,
  SlotConflict,
} from "@pantopus/types";
import { publicBooking } from "@pantopus/api";
import {
  buildBookingManageAppUrl,
  buildBookingManagePath,
} from "@pantopus/utils";
import {
  SlotPicker,
  decodeError,
  asSlotConflict,
  pillarForOwner,
  type Pillar,
} from "@/components/scheduling";
import OpenInAppButton from "@/components/public-share/OpenInAppButton";
import ErrorState from "@/components/ui/ErrorState";
import BookingSummaryCard from "./BookingSummaryCard";
import ConflictView from "./ConflictView";
import StateRouter from "./StateRouter";
import AddToCalendarPanel from "./AddToCalendarPanel";
import {
  PolicyCard,
  deriveReschedulePolicy,
  reschedulePolicyCopy,
} from "./CutoffPolicyBlocked";
import { formatRange, hostName, viewerTimezone } from "./edgeUtils";

function FlowShell({ children }: { children: ReactNode }) {
  return (
    <div className="mx-auto w-full max-w-md">
      <div className="flex h-12 items-center border-b border-app-border bg-app-surface px-2">
        <Link
          href="/"
          aria-label="Back"
          className="flex h-9 w-9 items-center justify-center rounded-lg text-app-text hover:bg-app-hover"
        >
          <ChevronLeft className="h-5 w-5" aria-hidden />
        </Link>
        <h1 className="flex-1 text-center text-[15px] font-semibold text-app-text">
          Reschedule
        </h1>
        <span className="h-9 w-9" aria-hidden />
      </div>
      <div className="space-y-4 px-4 py-4">{children}</div>
    </div>
  );
}

export default function RescheduleFlow({ token }: { token: string }) {
  const [view, setView] = useState<BookingManageView | null>(null);
  const [loadError, setLoadError] = useState<{
    notFound: boolean;
    message: string;
  } | null>(null);
  const [tz, setTz] = useState<string>(() => viewerTimezone());
  const [picked, setPicked] = useState<BookingSlot | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [conflict, setConflict] = useState<SlotConflict | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [done, setDone] = useState<BookingSlot | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setView(null);
    setLoadError(null);
    publicBooking
      .getBookingByToken(token)
      .then((res) => {
        if (!cancelled) setView(res);
      })
      .catch((err) => {
        if (cancelled) return;
        const d = decodeError(err);
        setLoadError({
          notFound: d.kind === "not_found" || d.kind === "expired",
          message: d.message,
        });
      });
    return () => {
      cancelled = true;
    };
  }, [token, reloadKey]);

  const pillar: Pillar = useMemo(
    () =>
      pillarForOwner(
        view?.page?.owner_type ?? view?.booking.owner_type ?? null,
      ),
    [view],
  );

  if (loadError) {
    if (loadError.notFound)
      return <StateRouter state="expired" message={loadError.message} />;
    return (
      <FlowShell>
        <ErrorState
          message={loadError.message}
          onRetry={() => setReloadKey((k) => k + 1)}
        />
      </FlowShell>
    );
  }

  if (!view) {
    return (
      <FlowShell>
        <div className="h-28 animate-pulse rounded-2xl bg-app-surface-muted" />
        <div className="h-64 animate-pulse rounded-2xl bg-app-surface-muted" />
      </FlowShell>
    );
  }

  const { booking, actions, eventType, page } = view;
  const host = hostName(page?.title);

  // Terminal booking statuses → no reschedule.
  if (
    booking.status === "cancelled" ||
    booking.status === "declined" ||
    booking.status === "no_show"
  ) {
    return (
      <StateRouter
        state="cancelled"
        pillar={pillar}
        bookAgainHref={buildBookingManagePath(token)}
      />
    );
  }

  // Success state.
  if (done) {
    return (
      <FlowShell>
        <div className="py-4 text-center">
          <span className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-app-success-bg">
            <CheckCircle2 className="h-8 w-8 text-app-success" aria-hidden />
          </span>
          <h2 className="text-xl font-bold text-app-text-strong">
            Your time was changed
          </h2>
          <p className="mx-auto mt-2 max-w-xs text-sm text-app-text-muted">
            You’re now booked for {formatRange(done.start, done.end, tz)}. We’ve
            let {host} know.
          </p>
        </div>
        <AddToCalendarPanel
          event={{
            title: eventType?.name || "Booking",
            start: done.start,
            end: done.end,
          }}
          tz={tz}
          icsUrl={publicBooking.getIcsUrl(token)}
        />
        <Link
          href={buildBookingManagePath(token)}
          className="block w-full rounded-xl border border-app-border bg-app-surface px-4 py-3 text-center text-sm font-bold text-app-text hover:bg-app-hover"
        >
          View your booking
        </Link>
      </FlowShell>
    );
  }

  const policy = deriveReschedulePolicy(actions);

  // Policy-blocked → note card + fallbacks.
  if (policy.kind !== "open") {
    const copy = reschedulePolicyCopy(policy, tz);
    return (
      <FlowShell>
        <BookingSummaryCard
          booking={booking}
          eventType={eventType}
          page={page}
          tz={tz}
          pillar={pillar}
        />
        {copy && (
          <PolicyCard
            tone={copy.tone}
            icon={copy.icon}
            title={copy.title}
            body={copy.body}
            still={copy.still}
          />
        )}
        <div className="space-y-2.5">
          <Link
            href={buildBookingManagePath(token)}
            className="block w-full rounded-xl border border-app-border bg-app-surface px-4 py-3 text-center text-sm font-bold text-app-text hover:bg-app-hover"
          >
            Keep my booking
          </Link>
          <OpenInAppButton
            appUrl={buildBookingManageAppUrl(token)}
            className={clsx(
              "flex w-full items-center justify-center gap-2 rounded-xl border px-4 py-3 text-sm font-bold",
              "border-app-personal/40 bg-app-surface text-app-personal hover:bg-app-hover",
            )}
          >
            <MessageCircle className="h-4 w-4" aria-hidden />
            Message host in the app
          </OpenInAppButton>
        </div>
      </FlowShell>
    );
  }

  const submit = async () => {
    if (!picked) return;
    setSubmitting(true);
    setActionError(null);
    setConflict(null);
    try {
      await publicBooking.rescheduleByToken(token, { start_at: picked.start });
      setDone(picked);
    } catch (err) {
      const decoded = decodeError(err);
      const c = asSlotConflict(decoded);
      if (c) {
        setConflict(c);
        setPicked(null);
      } else {
        setActionError(decoded.message);
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <FlowShell>
      <BookingSummaryCard
        booking={booking}
        eventType={eventType}
        page={page}
        tz={tz}
        pillar={pillar}
      />

      {/* current → new */}
      <div>
        <div className="flex items-center gap-2 rounded-xl bg-app-surface-sunken px-3 py-2.5">
          <Calendar
            className="h-4 w-4 shrink-0 text-app-text-muted"
            aria-hidden
          />
          <span className="text-[13px] text-app-text-muted line-through">
            {formatRange(booking.start_at, booking.end_at, tz)}
          </span>
        </div>
        <div className="flex justify-center py-1">
          <ArrowDown className="h-4 w-4 text-app-text-muted" aria-hidden />
        </div>
        <div
          className={clsx(
            "flex items-center gap-2 rounded-xl border px-3 py-2.5",
            picked
              ? "border-app-personal bg-app-personal-bg"
              : "border-dashed border-app-border-strong bg-app-surface",
          )}
        >
          <CalendarClock
            className={clsx(
              "h-4 w-4 shrink-0",
              picked ? "text-app-personal" : "text-app-text-muted",
            )}
            aria-hidden
          />
          <span
            className={clsx(
              "text-[13px] font-semibold",
              picked ? "text-app-text" : "text-app-text-muted",
            )}
          >
            {picked
              ? formatRange(picked.start, picked.end, tz)
              : "Pick a new time"}
          </span>
        </div>
      </div>

      {conflict && (
        <ConflictView
          conflict={conflict}
          pillar={pillar}
          detailsSaved={false}
          onPick={(slot) => {
            setConflict(null);
            setPicked(slot);
          }}
          onPickAnother={() => setConflict(null)}
        />
      )}

      {actionError && (
        <div className="rounded-xl border border-app-error-light bg-app-error-bg p-3 text-xs font-semibold text-app-error">
          {actionError}
        </div>
      )}

      <SlotPicker
        manageToken={token}
        pillar={pillar}
        tz={tz}
        onTzChange={setTz}
        onPick={(slot) => {
          setConflict(null);
          setPicked(slot);
        }}
        selected={picked?.start ?? null}
      />

      <button
        type="button"
        onClick={submit}
        disabled={!picked || submitting}
        className="flex w-full items-center justify-center gap-2 rounded-xl bg-app-personal px-4 py-3 text-sm font-bold text-white disabled:opacity-50"
      >
        <CalendarCheck className="h-4 w-4" aria-hidden />
        {submitting ? "Saving…" : "Confirm new time"}
      </button>
    </FlowShell>
  );
}
