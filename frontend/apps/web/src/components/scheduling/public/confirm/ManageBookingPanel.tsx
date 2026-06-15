// W6 · D4 — Manage your booking (token-authed, signed-out). Loads from the
// manage token. Shows a status pill (W0), the A09.4 summary card, and the
// reschedule / cancel ENTRY points — the actual cutoff/reschedule UI is D10/W7,
// so these LINK to /booking/:token/reschedule and /cancel (owned by W7; they
// 404 until W7 merges, which is expected during parallel build). Actions are
// enabled from the server-computed `actions` (cutoff/policy). Reuses W0
// AddToCalendar + CancellationPolicy. Token states: confirmed · past ·
// cancelled · window-closed · expired/invalid · loading.

"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  CalendarClock,
  Check,
  ChevronRight,
  Link2Off,
  Lock,
  Mail,
  RotateCcw,
  XCircle,
  type LucideIcon,
} from "lucide-react";
import clsx from "clsx";
import { publicBooking } from "@pantopus/api";
import type { BookingManageView } from "@pantopus/types";
import {
  buildBookingManagePath,
  buildBookingPagePath,
  APP_WEB_URL,
} from "@pantopus/utils";
import {
  AddToCalendar,
  BookingStatusPill,
  CancellationPolicy,
  decodeError,
  detectTimezone,
  pillarForOwner,
} from "@/components/scheduling";
import { ShimmerBlock } from "@/components/ui/Shimmer";
import BookingSummaryCard from "./BookingSummaryCard";
import { formatSlotRange, isPastBooking } from "./confirmUtils";

function Overline({ children }: { children: React.ReactNode }) {
  return (
    <p className="mb-2.5 text-[11px] font-semibold uppercase tracking-[0.08em] text-app-text-secondary">
      {children}
    </p>
  );
}

function ActionRow({
  icon: Icon,
  label,
  sub,
  href,
  tone = "neutral",
  disabled,
}: {
  icon: LucideIcon;
  label: string;
  sub: string;
  href?: string;
  tone?: "neutral" | "error";
  disabled?: boolean;
}) {
  const isErr = tone === "error";
  const body = (
    <>
      <span
        className={clsx(
          "flex h-8 w-8 shrink-0 items-center justify-center rounded-lg",
          disabled
            ? "bg-app-surface-sunken text-app-text-muted"
            : isErr
              ? "bg-app-error-bg text-app-error"
              : "bg-app-personal-bg text-app-personal",
        )}
      >
        <Icon className="h-4 w-4" aria-hidden />
      </span>
      <span className="min-w-0 flex-1">
        <span
          className={clsx(
            "block text-[13px] font-bold",
            disabled
              ? "text-app-text-muted"
              : isErr
                ? "text-app-error"
                : "text-app-text",
          )}
        >
          {label}
        </span>
        <span className="mt-0.5 block text-[10.5px] leading-[14px] text-app-text-secondary">
          {sub}
        </span>
      </span>
      {!disabled && (
        <ChevronRight
          className="h-[15px] w-[15px] shrink-0 text-app-text-muted"
          aria-hidden
        />
      )}
    </>
  );

  const base = clsx(
    "flex w-full items-center gap-3 rounded-xl border bg-app-surface px-3 py-3 text-left",
    disabled
      ? "cursor-not-allowed border-app-border opacity-75"
      : isErr
        ? "border-app-error-light shadow-sm hover:bg-app-hover"
        : "border-app-border-strong shadow-sm hover:bg-app-hover",
  );

  if (disabled || !href) {
    return (
      <div className={base} aria-disabled>
        {body}
      </div>
    );
  }
  return (
    <Link href={href} className={base}>
      {body}
    </Link>
  );
}

function PastBadge() {
  return (
    <span className="inline-flex items-center gap-1.5 self-start rounded-full bg-app-surface-muted px-2.5 py-1 text-xs font-semibold text-app-text-muted">
      <span
        className="h-1.5 w-1.5 rounded-full bg-current opacity-70"
        aria-hidden
      />
      Past
    </span>
  );
}

function ManageSkeleton() {
  return (
    <div className="mx-auto w-full max-w-md space-y-3.5 px-4 py-5">
      <ShimmerBlock className="h-6 w-24 rounded-full" />
      <ShimmerBlock className="h-44 w-full rounded-2xl" />
      <ShimmerBlock className="h-3 w-16 rounded" />
      <ShimmerBlock className="h-14 w-full rounded-xl" />
      <ShimmerBlock className="h-14 w-full rounded-xl" />
    </div>
  );
}

function ExpiredState() {
  return (
    <div className="mx-auto flex min-h-[60vh] w-full max-w-md flex-col items-center justify-center gap-5 px-6 text-center">
      <div className="relative flex h-24 w-24 items-center justify-center">
        <div className="absolute inset-0 rounded-full bg-app-warning-bg opacity-60" />
        <div className="relative flex h-[74px] w-[74px] items-center justify-center rounded-full border-2 border-app-warning-light bg-app-warning-bg text-app-warning">
          <Link2Off className="h-8 w-8" strokeWidth={1.9} aria-hidden />
        </div>
      </div>
      <div>
        <h1 className="text-xl font-bold tracking-tight text-app-text">
          This link has expired
        </h1>
        <p className="mx-auto mt-2 max-w-[15rem] text-[12.5px] leading-[18px] text-app-text-strong">
          For your security, manage links expire after a while. Check your
          latest confirmation email for a fresh link.
        </p>
      </div>
      <a
        href={APP_WEB_URL || "/"}
        className="flex h-11 w-full max-w-xs items-center justify-center gap-2 rounded-xl bg-app-personal text-[14px] font-bold text-white shadow-sm"
      >
        <Mail className="h-4 w-4" aria-hidden />
        Go to Pantopus
      </a>
    </div>
  );
}

export default function ManageBookingPanel({ token }: { token: string }) {
  const [view, setView] = useState<BookingManageView | null>(null);
  const [loading, setLoading] = useState(true);
  const [invalid, setInvalid] = useState(false);

  useEffect(() => {
    let active = true;
    (async () => {
      setLoading(true);
      setInvalid(false);
      try {
        const res = await publicBooking.getBookingByToken(token);
        if (active) setView(res);
      } catch (err) {
        const decoded = decodeError(err);
        if (active) {
          if (decoded.kind === "not_found" || decoded.kind === "expired")
            setInvalid(true);
          else setInvalid(true);
        }
      } finally {
        if (active) setLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [token]);

  if (loading) return <ManageSkeleton />;
  if (invalid || !view) return <ExpiredState />;

  const { booking, eventType, page, actions } = view;
  const pillar = pillarForOwner(page?.owner_type ?? booking.owner_type);
  const hostName = page?.title || "your host";
  const eventName = eventType?.name || "Your booking";
  const tz = booking.invitee_timezone || page?.timezone || detectTimezone();
  const status = booking.status;
  const isCancelled = status === "cancelled" || status === "declined";
  const isPast = isPastBooking(status, booking.end_at);
  const isActive = !isCancelled && !isPast;
  const managePath = buildBookingManagePath(token);

  const canReschedule =
    isActive && actions.can_reschedule && actions.invitee_reschedule_allowed;
  const canCancel =
    isActive && actions.can_cancel && actions.invitee_cancel_allowed;
  const windowClosed = isActive && !canReschedule && !canCancel;

  const icsEvent = {
    title: eventName,
    start: booking.start_at,
    end: booking.end_at,
    description: `Booking with ${hostName} — ${formatSlotRange(booking.start_at, booking.end_at, tz)}`,
  };

  return (
    <div className="mx-auto w-full max-w-md">
      <div className="flex h-12 items-center justify-center border-b border-app-border bg-app-surface px-2">
        <h1 className="text-[15px] font-semibold text-app-text">
          Your booking
        </h1>
      </div>

      <div className="flex flex-col gap-3.5 px-4 py-4">
        {isPast && !isCancelled ? (
          <PastBadge />
        ) : (
          <BookingStatusPill status={status} className="self-start" />
        )}

        <BookingSummaryCard
          hostName={hostName}
          eventName={eventName}
          pillar={pillar}
          startISO={booking.start_at}
          endISO={booking.end_at}
          tz={tz}
          locationMode={eventType?.location_mode}
          locationDetail={eventType?.location_detail}
          inviteeName={booking.invitee_name}
          dimmed={isPast || isCancelled}
          struck={isCancelled}
        />

        {isCancelled ? (
          <div className="flex items-start gap-2.5 rounded-xl border border-app-error-light bg-app-error-bg px-3.5 py-3">
            <XCircle
              className="mt-0.5 h-4 w-4 shrink-0 text-app-error"
              aria-hidden
            />
            <div className="min-w-0">
              <p className="text-[12px] font-bold text-app-error">
                This booking was cancelled
              </p>
              <p className="mt-0.5 text-[11px] leading-[15px] text-app-error">
                The slot was released. Nothing further is owed.
              </p>
              {page?.slug && (
                <Link
                  href={buildBookingPagePath(page.slug)}
                  className="mt-2 inline-flex items-center gap-1.5 text-[12px] font-bold text-app-personal"
                >
                  <RotateCcw className="h-3 w-3" aria-hidden />
                  Book again
                </Link>
              )}
            </div>
          </div>
        ) : isPast ? (
          <div className="flex items-center gap-2 px-1">
            <Check
              className="h-3.5 w-3.5 shrink-0 text-app-text-secondary"
              aria-hidden
            />
            <span className="text-[11.5px] text-app-text-secondary">
              This booking has already happened.
            </span>
          </div>
        ) : (
          <section>
            <Overline>Manage</Overline>
            <div className="space-y-2.5">
              <ActionRow
                icon={CalendarClock}
                label="Reschedule"
                sub="Pick a new time that works for you."
                href={canReschedule ? `${managePath}/reschedule` : undefined}
                disabled={!canReschedule}
              />
              <ActionRow
                icon={XCircle}
                label="Cancel booking"
                sub="Cancelling frees the slot for someone else."
                tone="error"
                href={canCancel ? `${managePath}/cancel` : undefined}
                disabled={!canCancel}
              />
              {windowClosed && (
                <div className="flex items-start gap-2 px-0.5">
                  <Lock
                    className="mt-0.5 h-3.5 w-3.5 shrink-0 text-app-warning"
                    aria-hidden
                  />
                  <span className="text-[11px] font-medium leading-[15px] text-app-warning">
                    Too late to change online — contact {hostName} to reschedule
                    or cancel.
                  </span>
                </div>
              )}
            </div>
          </section>
        )}

        {!isCancelled && (
          <section>
            <Overline>Add to your calendar</Overline>
            <div className="rounded-2xl border border-app-border bg-app-surface px-2 py-1">
              <AddToCalendar
                event={icsEvent}
                icsUrl={publicBooking.getIcsUrl(token)}
              />
            </div>
          </section>
        )}

        {isActive && page?.cancellation_policy && (
          <CancellationPolicy policy={page.cancellation_policy} />
        )}
      </div>
    </div>
  );
}
