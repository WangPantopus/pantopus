"use client";

// W2 — Event Types. B8 Connected calendars. OAuth sync is deferred server-side:
// GET /connected-calendars returns an empty list and POST .../connect returns
// 501 NOT_AVAILABLE. So this surface leads with a calm "coming soon" hero and
// offers provider rows whose Connect action surfaces the coming-soon message
// (never a dead end). If the read ever returns connected accounts, they render.

import { useCallback, useEffect, useState } from "react";
import clsx from "clsx";
import {
  Calendar,
  CalendarDays,
  CalendarRange,
  CalendarSync,
  Check,
  RefreshCw,
  type LucideIcon,
} from "lucide-react";
import * as api from "@pantopus/api";
import type { ConnectedCalendar, SchedulingOwnerRef } from "@pantopus/types";
import { toast } from "@/components/ui/toast-store";
import { decodeError } from "@/components/scheduling/decodeError";
import ErrorState from "@/components/ui/ErrorState";
import { ShimmerBlock } from "@/components/ui/Shimmer";

interface Provider {
  key: string;
  name: string;
  icon: LucideIcon;
}

const PROVIDERS: Provider[] = [
  { key: "google", name: "Google Calendar", icon: CalendarDays },
  { key: "apple", name: "Apple Calendar", icon: Calendar },
  { key: "outlook", name: "Outlook", icon: CalendarRange },
];

type Phase = "loading" | "error" | "ready";

export default function ConnectComingSoon({
  owner,
}: {
  owner: SchedulingOwnerRef;
}) {
  const [phase, setPhase] = useState<Phase>("loading");
  const [calendars, setCalendars] = useState<ConnectedCalendar[]>([]);
  const [connecting, setConnecting] = useState<string | null>(null);

  const load = useCallback(() => {
    let alive = true;
    setPhase("loading");
    api.scheduling
      .getConnectedCalendars(owner)
      .then((res) => {
        if (!alive) return;
        setCalendars(res.calendars ?? []);
        setPhase("ready");
      })
      .catch(() => {
        if (alive) setPhase("error");
      });
    return () => {
      alive = false;
    };
  }, [owner]);

  useEffect(() => load(), [load]);

  const handleConnect = async (providerKey: string) => {
    setConnecting(providerKey);
    try {
      await api.scheduling.connectCalendar(owner);
      // If the backend ever starts handing back a real flow, reflect it.
      toast.success("Calendar connected.");
      load();
    } catch (err) {
      const decoded = decodeError(err);
      // 501 NOT_AVAILABLE is the expected, first-class "coming soon" response.
      toast.info(decoded.message || "Calendar sync is coming soon.");
    } finally {
      setConnecting(null);
    }
  };

  if (phase === "loading") {
    return (
      <div className="flex flex-col gap-3">
        <ShimmerBlock className="h-44 rounded-2xl" />
        <ShimmerBlock className="h-16 rounded-2xl" />
        <ShimmerBlock className="h-16 rounded-2xl" />
      </div>
    );
  }

  if (phase === "error") {
    return (
      <ErrorState
        message="We couldn't load your connected calendars."
        onRetry={load}
      />
    );
  }

  const hasConnected = calendars.length > 0;

  return (
    <div className="flex flex-col gap-3">
      {hasConnected ? (
        calendars.map((c) => <ConnectedRow key={c.id} calendar={c} />)
      ) : (
        <ComingSoonHero />
      )}

      <p className="mt-1 px-0.5 text-[11px] font-bold uppercase tracking-[0.08em] text-app-text-muted">
        Providers
      </p>
      <p className="px-0.5 text-xs leading-snug text-app-text-secondary">
        Connect a calendar to check for conflicts and add bookings
        automatically.
      </p>

      {PROVIDERS.map((p) => (
        <ConnectRow
          key={p.key}
          provider={p}
          connecting={connecting === p.key}
          disabled={connecting != null}
          onConnect={() => handleConnect(p.key)}
        />
      ))}
    </div>
  );
}

function ProviderTile({
  icon: Icon,
  muted,
}: {
  icon: LucideIcon;
  muted?: boolean;
}) {
  return (
    <span
      className={clsx(
        "flex h-10 w-10 shrink-0 items-center justify-center rounded-[10px] border border-app-border bg-app-surface shadow-sm",
        muted ? "text-app-text-muted" : "text-app-text-secondary",
      )}
    >
      <Icon className="h-5 w-5" strokeWidth={2} aria-hidden />
    </span>
  );
}

function ComingSoonHero() {
  return (
    <div className="flex flex-col items-center rounded-2xl border border-app-border bg-app-surface p-6 text-center shadow-sm">
      <span className="mb-3.5 flex h-14 w-14 items-center justify-center rounded-2xl bg-primary-50 text-primary-600">
        <CalendarSync className="h-7 w-7" strokeWidth={1.9} aria-hidden />
      </span>
      <h2 className="text-base font-bold text-app-text">
        Calendar sync is coming soon
      </h2>
      <p className="mx-auto mt-1.5 max-w-xs text-[12.5px] leading-relaxed text-app-text-secondary">
        We&apos;ll let you know when you can connect Google, Apple, and Outlook
        to check for conflicts.
      </p>
      <div className="mt-5 flex gap-3.5">
        {PROVIDERS.map((p) => (
          <span key={p.key} className="opacity-50">
            <ProviderTile icon={p.icon} muted />
          </span>
        ))}
      </div>
    </div>
  );
}

function ConnectRow({
  provider,
  connecting,
  disabled,
  onConnect,
}: {
  provider: Provider;
  connecting: boolean;
  disabled: boolean;
  onConnect: () => void;
}) {
  return (
    <div className="flex items-center gap-3 rounded-2xl border border-app-border bg-app-surface p-3 shadow-sm">
      <ProviderTile icon={provider.icon} />
      <div className="min-w-0 flex-1">
        <p className="truncate text-[13px] font-semibold text-app-text">
          {provider.name}
        </p>
        <p className="mt-0.5 text-[11px] text-app-text-secondary">
          {connecting ? "Opening…" : "Not connected"}
        </p>
      </div>
      <button
        type="button"
        onClick={onConnect}
        disabled={disabled}
        className="h-8 shrink-0 rounded-lg bg-primary-600 px-3.5 text-[12.5px] font-bold text-white transition hover:bg-primary-700 disabled:opacity-60"
      >
        {connecting ? "…" : "Connect"}
      </button>
    </div>
  );
}

function ConnectedRow({ calendar }: { calendar: ConnectedCalendar }) {
  const synced = calendar.status === "synced" || calendar.status === "active";
  return (
    <div className="flex flex-col gap-2.5 rounded-2xl border border-app-border bg-app-surface p-3 shadow-sm">
      <div className="flex items-center gap-3">
        <ProviderTile icon={CalendarDays} />
        <div className="min-w-0 flex-1">
          <p className="truncate text-[13px] font-semibold capitalize text-app-text">
            {calendar.provider}
          </p>
          {calendar.external_account && (
            <p className="mt-0.5 truncate text-[11px] text-app-text-secondary">
              {calendar.external_account}
            </p>
          )}
        </div>
        <span
          className={clsx(
            "inline-flex shrink-0 items-center gap-1 rounded-full px-2 py-1 text-[9.5px] font-bold",
            synced
              ? "bg-app-success-bg text-app-success"
              : "bg-app-warning-bg text-app-warning",
          )}
        >
          <Check className="h-2.5 w-2.5" strokeWidth={2.6} aria-hidden />
          {synced ? "Synced" : calendar.status}
        </span>
      </div>
      {calendar.last_synced_at && (
        <div className="flex items-center gap-1.5 border-t border-app-border pt-2 text-[10.5px] text-app-text-secondary">
          <RefreshCw className="h-3 w-3" aria-hidden />
          Last synced {new Date(calendar.last_synced_at).toLocaleString()}
        </div>
      )}
    </div>
  );
}
