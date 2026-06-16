"use client";

// G5 — Business Scheduling Settings. The "Booking" settings hub for the active
// business: Confirmation (auto-confirm vs approve + approval window),
// Scheduling (notice / horizon / buffers / timezone), Policy, Notifications and
// Payments — plus the Team-availability link and the Assignment surface the W2
// editor links here for. Defaults persist to the booking page (timezone +
// cancellation_policy are columns; the rest rides in branding); notify toggles
// persist to notification-preferences. Business violet accents; sky controls.

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import clsx from "clsx";
import {
  AlertTriangle,
  Bell,
  CalendarCheck,
  CalendarClock,
  CalendarRange,
  ChevronRight,
  Clock,
  CreditCard,
  GitCommitHorizontal,
  Globe,
  Hourglass,
  Lock,
  Shield,
  UserCheck,
  Users,
} from "lucide-react";
import * as api from "@pantopus/api";
import type {
  BookingPage,
  NotificationPreferences,
  PaymentsStatus,
} from "@pantopus/types";
import { webFeatureFlags } from "@/lib/featureFlags";
import { PillarThemeProvider } from "@/components/scheduling/PillarThemeProvider";
import TimezoneSelector, {
  zoneLabel,
} from "@/components/scheduling/TimezoneSelector";
import { decodeError } from "@/components/scheduling/decodeError";
import ErrorState from "@/components/ui/ErrorState";
import { toast } from "@/components/ui/toast-store";
import {
  AccentOverline,
  BusinessSwitcher,
  Card,
  Chevron,
  Chip,
  Group,
  IconDisc,
  Note,
  Segmented,
  SettingRow,
  Skeleton,
  Stepper,
  Toggle,
} from "./ui";
import { useBusinessOwner } from "./owner";
import { rosterFromSeats, type TeamMemberView } from "./members";
import AssignmentSection from "./AssignmentSection";
import {
  type ConfirmationMode,
  type SchedulingDefaults,
  approvalWindowLabel,
  brandingPatch,
  bufferLabel,
  cancellationLabel,
  confirmationNote,
  durationLabel,
  horizonLabel,
  minNoticeLabel,
  notifyMember,
  notifyOwner,
  prefsWith,
  readDefaults,
} from "./settings";

const NOTICE_OPTIONS = [60, 120, 240, 720, 1440, 2880];

function ExpandRow({
  icon,
  label,
  value,
  open,
  onToggle,
  children,
}: {
  icon: typeof Clock;
  label: string;
  value: string;
  open: boolean;
  onToggle: () => void;
  children: React.ReactNode;
}) {
  return (
    <div className="border-b border-app-border last:border-b-0">
      <button
        type="button"
        onClick={onToggle}
        aria-expanded={open}
        className="flex w-full items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-app-hover"
      >
        <IconDisc icon={icon} />
        <div className="min-w-0 flex-1">
          <p className="truncate text-[15px] font-medium text-app-text">
            {label}
          </p>
          <p className="mt-0.5 truncate text-xs text-app-text-secondary">
            {value}
          </p>
        </div>
        <ChevronRight
          className={clsx(
            "h-4 w-4 shrink-0 text-app-text-muted transition-transform",
            open && "rotate-90",
          )}
          aria-hidden
        />
      </button>
      {open && <div className="bg-app-surface-muted px-4 py-3">{children}</div>}
    </div>
  );
}

export default function BusinessSettings() {
  const biz = useBusinessOwner();
  const owner = biz.owner;

  const [page, setPage] = useState<BookingPage | null>(null);
  const [prefs, setPrefs] = useState<NotificationPreferences | null>(null);
  const [payments, setPayments] = useState<PaymentsStatus | null>(null);
  const [roster, setRoster] = useState<TeamMemberView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [tzOpen, setTzOpen] = useState(false);
  const [expanded, setExpanded] = useState<string | null>(null);
  const [draft, setDraft] = useState<SchedulingDefaults | null>(null);

  const paid = webFeatureFlags.schedulingPaid;

  const load = useCallback(async () => {
    if (!owner?.ownerId) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [pageRes, prefsRes] = await Promise.all([
        api.scheduling.getBookingPage(owner),
        api.scheduling
          .getNotificationPreferences(owner)
          .catch(() => ({ prefs: {} })),
      ]);
      setPage(pageRes.page);
      setPrefs(prefsRes.prefs ?? {});
      setDraft(readDefaults(pageRes.page));
      // Roster + payments are non-blocking enrichments.
      api.businessSeats
        .getBusinessSeats(owner.ownerId)
        .then((r) => setRoster(rosterFromSeats(r.seats)))
        .catch(() => setRoster([]));
      if (paid) {
        api.scheduling
          .getPaymentsStatus(owner)
          .then(setPayments)
          .catch(() => setPayments(null));
      }
    } catch (err) {
      setError(decodeError(err).message);
    } finally {
      setLoading(false);
    }
  }, [owner, paid]);

  useEffect(() => {
    void load();
  }, [load]);

  // Debounced persistence of the branding-backed defaults.
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const persistDefaults = useCallback(
    (next: SchedulingDefaults) => {
      if (!owner) return;
      if (timer.current) clearTimeout(timer.current);
      timer.current = setTimeout(async () => {
        try {
          const { page: updated } = await api.scheduling.updateBookingPage(
            {
              branding: brandingPatch(page, {
                confirmation: next.confirmation,
                approvalWindowHours: next.approvalWindowHours,
                minNoticeMin: next.minNoticeMin,
                maxHorizonDays: next.maxHorizonDays,
                bufferBeforeMin: next.bufferBeforeMin,
                bufferAfterMin: next.bufferAfterMin,
              }),
            },
            owner,
          );
          setPage(updated);
          toast.success("Saved", 1500);
        } catch (err) {
          toast.error(decodeError(err).message || "Couldn’t save");
        }
      }, 550);
    },
    [owner, page],
  );

  const editDefaults = useCallback(
    (patch: Partial<SchedulingDefaults>) => {
      setDraft((d) => {
        const next = { ...(d ?? readDefaults(page)), ...patch };
        persistDefaults(next);
        return next;
      });
    },
    [page, persistDefaults],
  );

  const patchTimezone = useCallback(
    async (tz: string) => {
      if (!owner) return;
      try {
        const { page: updated } = await api.scheduling.updateBookingPage(
          { timezone: tz },
          owner,
        );
        setPage(updated);
        toast.success("Timezone updated", 1500);
      } catch (err) {
        toast.error(decodeError(err).message || "Couldn’t update timezone");
      }
    },
    [owner],
  );

  const patchPrefs = useCallback(
    async (patch: Partial<{ notifyOwner: boolean; notifyMember: boolean }>) => {
      if (!owner) return;
      const next = prefsWith(prefs, patch);
      setPrefs(next); // optimistic
      try {
        const { prefs: saved } =
          await api.scheduling.updateNotificationPreferences(next, owner);
        setPrefs(saved);
      } catch (err) {
        setPrefs(prefs); // revert
        toast.error(decodeError(err).message || "Couldn’t save notifications");
      }
    },
    [owner, prefs],
  );

  // ── States ────────────────────────────────────────────────────────────────

  if (biz.loading || (loading && owner)) {
    return <SettingsSkeleton switcher={biz.options.length > 1} />;
  }

  if (biz.unavailable || !owner) {
    return (
      <div className="mx-auto max-w-2xl">
        <Header
          options={biz.options}
          activeId={biz.active?.id ?? null}
          onSwitch={biz.setActiveId}
        />
        <Card>
          <div className="flex flex-col items-center gap-2 px-6 py-12 text-center">
            <span className="flex h-14 w-14 items-center justify-center rounded-2xl bg-app-business-bg text-app-business">
              <Users className="h-6 w-6" aria-hidden />
            </span>
            <p className="text-base font-semibold text-app-text">
              No business yet
            </p>
            <p className="max-w-xs text-sm text-app-text-secondary">
              Business scheduling is available once you own or join a business
              on Pantopus.
            </p>
          </div>
        </Card>
      </div>
    );
  }

  if (error || !page || !draft) {
    return (
      <div className="mx-auto max-w-2xl">
        <Header
          options={biz.options}
          activeId={biz.active?.id ?? null}
          onSwitch={biz.setActiveId}
        />
        <ErrorState
          message={error ?? "Couldn’t load settings."}
          onRetry={() => void load()}
        />
      </div>
    );
  }

  const approve = draft.confirmation === "approve";

  return (
    <PillarThemeProvider pillar="business">
      <div className="mx-auto max-w-2xl">
        <Header
          options={biz.options}
          activeId={biz.active?.id ?? null}
          onSwitch={biz.setActiveId}
        />

        <p className="px-1 pb-1 text-xs leading-snug text-app-text-secondary">
          Defaults flow into each service — change them per service anytime.
        </p>

        <div className="space-y-1">
          {/* Team */}
          <Group title="Team">
            <SettingRow
              icon={CalendarClock}
              label="Team booking availability"
              sub="Who’s bookable and their hours"
              href="/app/scheduling/business/team-availability"
              iconTone="business"
            />
            <SettingRow
              icon={Users}
              label="Members & seats"
              sub={`${roster.length || "—"} on the team`}
              href="/app/scheduling/business/team-availability"
              last
            />
          </Group>

          {/* Assignment (mounts G1/G2) */}
          <AssignmentSection owner={owner} roster={roster} />

          {/* Confirmation */}
          <section>
            <AccentOverline className="pb-2 pt-4">Confirmation</AccentOverline>
            <Card>
              <div className="px-4 py-3.5">
                <div className="mb-2 flex items-center gap-2.5">
                  <IconDisc icon={CalendarCheck} tone="business" />
                  <p className="text-[15px] font-medium text-app-text">
                    New bookings
                  </p>
                </div>
                <Segmented<ConfirmationMode>
                  value={draft.confirmation}
                  onChange={(id) => editDefaults({ confirmation: id })}
                  options={[
                    { id: "auto", label: "Auto-confirm" },
                    { id: "approve", label: "Approve each request" },
                  ]}
                />
                <p className="mt-2 px-0.5 text-[11px] leading-tight text-app-text-secondary">
                  {confirmationNote(draft.confirmation)}
                </p>
              </div>
              {approve && (
                <ExpandRow
                  icon={Hourglass}
                  label="Approval window"
                  value={approvalWindowLabel(draft.approvalWindowHours)}
                  open={expanded === "approval"}
                  onToggle={() =>
                    setExpanded(expanded === "approval" ? null : "approval")
                  }
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs text-app-text-secondary">
                      Hours to respond
                    </span>
                    <Stepper
                      value={draft.approvalWindowHours}
                      onChange={(n) => editDefaults({ approvalWindowHours: n })}
                      min={1}
                      max={168}
                      suffix="h"
                      accent
                      ariaLabel="Approval window hours"
                    />
                  </div>
                </ExpandRow>
              )}
            </Card>
          </section>

          {/* Scheduling */}
          <section>
            <AccentOverline className="pb-2 pt-4">Scheduling</AccentOverline>
            <Card>
              <ExpandRow
                icon={Clock}
                label="Minimum notice"
                value={minNoticeLabel(draft.minNoticeMin)}
                open={expanded === "notice"}
                onToggle={() =>
                  setExpanded(expanded === "notice" ? null : "notice")
                }
              >
                <div className="flex flex-wrap gap-2">
                  {NOTICE_OPTIONS.map((m) => {
                    const on = draft.minNoticeMin === m;
                    return (
                      <button
                        key={m}
                        type="button"
                        onClick={() => editDefaults({ minNoticeMin: m })}
                        className={clsx(
                          "rounded-full border px-3 py-1.5 text-xs font-semibold transition-colors",
                          on
                            ? "border-app-business bg-app-business-bg text-app-business"
                            : "border-app-border bg-app-surface text-app-text-secondary hover:bg-app-hover",
                        )}
                      >
                        {durationLabel(m)}
                      </button>
                    );
                  })}
                </div>
              </ExpandRow>
              <ExpandRow
                icon={CalendarRange}
                label="Booking horizon"
                value={horizonLabel(draft.maxHorizonDays)}
                open={expanded === "horizon"}
                onToggle={() =>
                  setExpanded(expanded === "horizon" ? null : "horizon")
                }
              >
                <div className="flex items-center justify-between">
                  <span className="text-xs text-app-text-secondary">
                    Days bookable in advance
                  </span>
                  <Stepper
                    value={draft.maxHorizonDays}
                    onChange={(n) => editDefaults({ maxHorizonDays: n })}
                    min={1}
                    max={365}
                    step={5}
                    accent
                    ariaLabel="Booking horizon days"
                  />
                </div>
              </ExpandRow>
              <ExpandRow
                icon={GitCommitHorizontal}
                label="Buffers"
                value={bufferLabel(draft.bufferBeforeMin, draft.bufferAfterMin)}
                open={expanded === "buffers"}
                onToggle={() =>
                  setExpanded(expanded === "buffers" ? null : "buffers")
                }
              >
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="text-xs text-app-text-secondary">
                      Before each booking
                    </span>
                    <Stepper
                      value={draft.bufferBeforeMin}
                      onChange={(n) => editDefaults({ bufferBeforeMin: n })}
                      min={0}
                      max={120}
                      step={5}
                      suffix="m"
                      accent
                      ariaLabel="Buffer before minutes"
                    />
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-xs text-app-text-secondary">
                      After each booking
                    </span>
                    <Stepper
                      value={draft.bufferAfterMin}
                      onChange={(n) => editDefaults({ bufferAfterMin: n })}
                      min={0}
                      max={120}
                      step={5}
                      suffix="m"
                      accent
                      ariaLabel="Buffer after minutes"
                    />
                  </div>
                </div>
              </ExpandRow>
              <SettingRow
                icon={Globe}
                label="Time zone"
                sub={page.timezone ? zoneLabel(page.timezone) : "Not set"}
                onClick={() => setTzOpen(true)}
                last
                trailing={
                  <div className="flex items-center gap-1.5">
                    {page.timezone && (
                      <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-app-business-bg text-app-business">
                        <Lock className="h-3.5 w-3.5" aria-hidden />
                      </span>
                    )}
                    <Chevron />
                  </div>
                }
              />
            </Card>
          </section>

          {/* Policy */}
          <section>
            <AccentOverline className="pb-2 pt-4">Policy</AccentOverline>
            <Card>
              <SettingRow
                icon={Shield}
                label="Cancellation & no-show policy"
                sub={cancellationLabel(page.cancellation_policy) ?? undefined}
                href="/app/scheduling/payments/policy"
                last
                trailing={
                  cancellationLabel(page.cancellation_policy) ? (
                    <Chevron />
                  ) : (
                    <div className="flex items-center gap-1.5">
                      <Chip tone="warning">Set up</Chip>
                      <Chevron />
                    </div>
                  )
                }
              />
            </Card>
          </section>

          {/* Notifications */}
          <Group title="Notifications">
            <SettingRow
              icon={Bell}
              label="Notify the owner"
              trailing={
                <Toggle
                  on={notifyOwner(prefs)}
                  onChange={(v) => void patchPrefs({ notifyOwner: v })}
                  label="Notify the owner"
                />
              }
            />
            <SettingRow
              icon={UserCheck}
              label="Notify the assigned member"
              last
              trailing={
                <Toggle
                  on={notifyMember(prefs)}
                  onChange={(v) => void patchPrefs({ notifyMember: v })}
                  label="Notify the assigned member"
                />
              }
            />
          </Group>

          {/* Payments (paid flag) */}
          {paid && (
            <section>
              <AccentOverline className="pb-2 pt-4">Payments</AccentOverline>
              <Card>
                <SettingRow
                  icon={CreditCard}
                  iconTone="stripe"
                  label="Stripe payments"
                  sub={
                    payments?.connected
                      ? payments.payouts_enabled
                        ? "Connected · payouts enabled"
                        : "Connected · finish setup"
                      : "Not connected"
                  }
                  href="/app/scheduling/payments"
                  last
                  trailing={
                    payments?.connected ? (
                      <div className="flex items-center gap-1.5">
                        <Chip tone="success" icon={CalendarCheck}>
                          Connected
                        </Chip>
                        <Chevron />
                      </div>
                    ) : (
                      <Link
                        href="/app/scheduling/payments"
                        className="rounded-full bg-primary-600 px-3 py-1.5 text-xs font-bold text-white hover:bg-primary-700"
                      >
                        Connect
                      </Link>
                    )
                  }
                />
              </Card>
              {payments && !payments.connected && (
                <div className="pt-2">
                  <Note tone="warning" icon={AlertTriangle}>
                    Connect payments to charge for services.
                  </Note>
                </div>
              )}
            </section>
          )}
        </div>

        <p className="mt-6 px-1 font-mono text-[11px] text-app-text-muted">
          {page.slug} · business #{owner.ownerId?.slice(0, 8)}
        </p>

        <TimezoneSelector
          open={tzOpen}
          onClose={() => setTzOpen(false)}
          value={page.timezone ?? undefined}
          onSelect={(z) => {
            setTzOpen(false);
            void patchTimezone(z);
          }}
          pillar="business"
        />
      </div>
    </PillarThemeProvider>
  );
}

function Header({
  options,
  activeId,
  onSwitch,
}: {
  options: { id: string; name: string }[];
  activeId: string | null;
  onSwitch: (id: string) => void;
}) {
  return (
    <div className="mb-3 flex items-start justify-between gap-3">
      <div>
        <p className="text-xs font-bold uppercase tracking-wider text-app-business">
          Business
        </p>
        <h1 className="text-xl font-bold text-app-text">Booking settings</h1>
      </div>
      <BusinessSwitcher
        options={options}
        activeId={activeId}
        onChange={onSwitch}
      />
    </div>
  );
}

function SettingsSkeleton({ switcher }: { switcher: boolean }) {
  return (
    <div className="mx-auto max-w-2xl" aria-hidden>
      <div className="mb-3 flex items-start justify-between">
        <div>
          <Skeleton className="h-3 w-16" />
          <Skeleton className="mt-2 h-6 w-40" />
        </div>
        {switcher && <Skeleton className="h-7 w-28 rounded-full" />}
      </div>
      <div className="space-y-4">
        <Skeleton className="h-28 w-full rounded-2xl" />
        <Skeleton className="h-44 w-full rounded-2xl" />
        <Skeleton className="h-40 w-full rounded-2xl" />
        <Skeleton className="h-24 w-full rounded-2xl" />
      </div>
    </div>
  );
}
